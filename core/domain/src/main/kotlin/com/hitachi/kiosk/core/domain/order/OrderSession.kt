package com.hitachi.kiosk.core.domain.order

import com.hitachi.kiosk.core.domain.cart.Cart
import com.hitachi.kiosk.core.domain.cart.CartAddAllResult
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartAddition
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.CartLimit
import com.hitachi.kiosk.core.domain.cart.LineChoice
import com.hitachi.kiosk.core.domain.id.IdempotencyKey
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where an order is in the OrderSession machine of architecture §5.1 (PRD §12.2, DEV-041). Idle is the absence of a
 * session ([OrderSessionHolder.current] is `null`).
 */
enum class OrderPhase {
    /** Dine in or Takeaway was picked (FR-007). */
    TYPE_CHOSEN,

    /** The customer is on the Menu, Item, Combo or Budget Plan (FR-009–FR-011, FR-032). */
    BUILDING,

    /** Your order, with its suggestion strip (FR-012–FR-015). */
    REVIEWING,

    /** Pay was tapped and E7 is running (FR-016). */
    SUBMITTING,

    /** The order exists and is being paid (FR-017, FR-018). */
    PAYING,

    /** The backend reported `PAID` (A-15). */
    PAID,

    /** The Complete screen was shown (FR-020). */
    COMPLETED,

    /** "Cancel" or "Start over" (FR-009, FR-023). */
    CANCELLED,

    /** The inactivity overlay ran out (FR-023). */
    TIMED_OUT,
    ;

    /** Whether the customer can still change the cart: before Pay, and back on Your order after a failure (BR-08). */
    val isEditable: Boolean get() = this == TYPE_CHOSEN || this == BUILDING || this == REVIEWING

    /** Whether the order can still be cancelled or time out: every customer state before Submitting (§5.1). */
    val isCancellable: Boolean get() = isEditable
}

/**
 * The "Budget Plan · 2 adults · 1 child · Rp 150.000" row on Your order (FR-012, A-40). [lineIds] are the cart lines
 * the plan created or merged into; editing or removing any of them clears the tag (architecture §5.1).
 *
 * @property quantities the units the plan put into each of [lineIds], so "Edit plan" takes out only what the plan
 *   added and keeps units the customer had of the same item before (DEV-111). Empty for a tag built without it: every
 *   unit of the lines then belongs to the plan.
 */
data class PlanTag(
    val budget: Rupiah,
    val adults: Int,
    val children: Int,
    val lineIds: List<String>,
    val quantities: Map<String, Int> = emptyMap(),
)

/**
 * One order's state (architecture §5.1). [menuVersion] and [configVersion] are the catalog the order started with:
 * a sync that lands mid-order waits until the order ends (FR-028, A-29).
 *
 * @property planTag the Budget Plan that filled the cart, while its lines are untouched (FR-032).
 * @property planAdded a plan was just added or replaced and Your order has not shown "Plan added" for it yet (DEV-112).
 * @property issues what E7 asked the customer to review: re-priced and unavailable lines (FR-016, DEV-046).
 * @property submission the body and `Idempotency-Key` of the last E7 attempt, kept until "Back to order" so a retry
 *   of the same body reuses the key (technical-design §5.3, BR-08).
 */
data class OrderState(
    val orderType: OrderType,
    val phase: OrderPhase,
    val cart: Cart,
    val menuVersion: String,
    val configVersion: String,
    val planTag: PlanTag? = null,
    val planAdded: Boolean = false,
    val issues: ReviewIssues = ReviewIssues(),
    val submission: Submission? = null,
)

/**
 * One customer order, shared by Order type, Menu, Item, Combo, Budget Plan and Your order through
 * [OrderSessionHolder] (architecture §5.1). Every change is atomic (compare-and-set), so a double tap can never act
 * on a stale cart. A transition that the machine does not allow from the current phase changes nothing and returns
 * `false`.
 */
@Suppress("TooManyFunctions") // One function per edge of the machine and per cart edit.
class OrderSession(
    orderType: OrderType,
    menuVersion: String,
    configVersion: String,
) {
    private val mutableState =
        MutableStateFlow(
            OrderState(
                orderType = orderType,
                phase = OrderPhase.TYPE_CHOSEN,
                cart = Cart(),
                menuVersion = menuVersion,
                configVersion = configVersion,
            ),
        )

    val state: StateFlow<OrderState> = mutableState.asStateFlow()

    /** "Change" on the Menu: a new order type, the cart kept (FR-007, PRD §5.1). */
    fun changeOrderType(orderType: OrderType): Boolean =
        transition({ it.isEditable }) { it.copy(orderType = orderType) }

    /** The Menu is shown: TypeChosen → Building, or Reviewing → Building on "Add more" (§5.1). */
    fun startBuilding(): Boolean =
        transition({ it == OrderPhase.TYPE_CHOSEN || it == OrderPhase.BUILDING || it == OrderPhase.REVIEWING }) {
            it.copy(phase = OrderPhase.BUILDING)
        }

    /** Your order is shown: Building → Reviewing (§5.1). Also accepted on the way back from Submitting. */
    fun startReviewing(): Boolean =
        transition({ it == OrderPhase.BUILDING || it == OrderPhase.REVIEWING }) {
            it.copy(phase = OrderPhase.REVIEWING)
        }

    /** Adds to the cart with the merge and limit rules of [Cart.add] (FR-009 AC2, FR-010, BR-05). */
    fun add(
        item: CartItem,
        quantity: Int,
        unitPrice: Rupiah,
        choices: List<LineChoice>,
    ): CartAddResult {
        while (true) {
            val current = mutableState.value
            if (!current.phase.isEditable) return CartAddResult.LimitReached(CartLimit.ORDER_LOCKED)
            val result = current.cart.add(item, quantity, unitPrice, choices)
            if (result !is CartAddResult.Added) return result
            val next = current.copy(cart = result.cart, planTag = current.planTag.without(result.line.lineId))
            if (mutableState.compareAndSet(current, next)) return result
        }
    }

    /**
     * One unit of [lineId] becomes [item] (the "Make it a meal" card, DEV-114); see [Cart.switchOne]. `null` for an
     * unknown line or no change. Changing a plan line clears the plan tag.
     */
    fun switchOne(
        lineId: String,
        item: CartItem,
        unitPrice: Rupiah,
        choices: List<LineChoice>,
    ): CartAddResult? {
        while (true) {
            val current = mutableState.value
            if (!current.phase.isEditable) return CartAddResult.LimitReached(CartLimit.ORDER_LOCKED)
            val result = current.cart.switchOne(lineId, item, unitPrice, choices)
            if (result !is CartAddResult.Added) return result
            val next =
                current.copy(
                    cart = result.cart,
                    planTag = current.planTag.without(lineId),
                    issues = if (result.cart.line(lineId) == null) current.issues.without(lineId) else current.issues,
                )
            if (mutableState.compareAndSet(current, next)) return result
        }
    }

    /** "+" on Your order (FR-012). `null` for an unknown line. Changing a plan line clears the plan tag. */
    fun increment(lineId: String): CartAddResult? {
        while (true) {
            val current = mutableState.value
            if (!current.phase.isEditable) return CartAddResult.LimitReached(CartLimit.ORDER_LOCKED)
            val result = current.cart.increment(lineId)
            if (result !is CartAddResult.Added) return result
            val next = current.copy(cart = result.cart, planTag = current.planTag.without(lineId))
            if (mutableState.compareAndSet(current, next)) return result
        }
    }

    /** "−" on Your order; at quantity 1 the line is removed (FR-012). Changing a plan line clears the plan tag. */
    fun decrement(lineId: String): Boolean = editLine(lineId) { it.decrement(lineId) }

    /** The trash icon or "Remove" (FR-012, FR-016). Removing a plan line clears the plan tag. */
    fun remove(lineId: String): Boolean = editLine(lineId) { it.remove(lineId) }

    /**
     * "Add plan to order" (FR-032 AC5): every line of the plan or none, tagged with [budget] and the group size for
     * the Your order row (A-40). Marks the plan as just added, for the "Plan added" confirmation (DEV-112).
     */
    fun addPlan(
        additions: List<CartAddition>,
        budget: Rupiah,
        adults: Int,
        children: Int,
    ): CartAddAllResult {
        while (true) {
            val current = mutableState.value
            if (!current.phase.isEditable) return CartAddAllResult.LimitReached(CartLimit.ORDER_LOCKED)
            val result = current.cart.addAll(additions)
            if (result !is CartAddAllResult.Added) return result
            val tag = planTag(current.cart, result, budget, adults, children)
            val next = current.copy(cart = result.cart, planTag = tag, planAdded = true)
            if (mutableState.compareAndSet(current, next)) return result
        }
    }

    /**
     * "Edit plan" then "Add plan to order" (FR-032, DEV-111, TC-203): the units the current plan put into the cart go
     * out and [additions] come in, whole or not at all, so the other lines stay and the cart never holds both plans.
     * The tag becomes the new plan's. Without a plan tag this is [addPlan]. A refusal ([CartAddAllResult.LimitReached])
     * leaves the cart and the tag as they were.
     */
    fun replacePlan(
        additions: List<CartAddition>,
        budget: Rupiah,
        adults: Int,
        children: Int,
    ): CartAddAllResult {
        while (true) {
            val current = mutableState.value
            if (!current.phase.isEditable) return CartAddAllResult.LimitReached(CartLimit.ORDER_LOCKED)
            val old = current.planTag
            val cleared = if (old == null) current.cart else old.takenOutOf(current.cart)
            val result = cleared.addAll(additions)
            if (result !is CartAddAllResult.Added) return result
            val gone =
                current.cart.lines
                    .map { it.lineId }
                    .filter { cleared.line(it) == null }
            val next =
                current.copy(
                    cart = result.cart,
                    planTag = planTag(cleared, result, budget, adults, children),
                    planAdded = true,
                    issues = gone.fold(current.issues) { issues, lineId -> issues.without(lineId) },
                )
            if (mutableState.compareAndSet(current, next)) return result
        }
    }

    /** Your order showed "Plan added" for the last plan, so it is not shown again (DEV-112). `false` when none was due. */
    fun acknowledgePlanAdded(): Boolean = transition({ true }, { it.planAdded }) { it.copy(planAdded = false) }

    /**
     * "Pay" on Your order: Reviewing → Submitting, only with something to pay for and nothing left to review
     * ([ReviewIssues.isBlocking], FR-016). This is the phase change alone; [SubmitOrder] uses the overload below, which
     * also settles the E7 body and key in the same atomic step.
     */
    fun submit(): Boolean =
        transition({ it == OrderPhase.REVIEWING }, { !it.cart.isEmpty() && !it.issues.isBlocking }) {
            it.copy(phase = OrderPhase.SUBMITTING)
        }

    /**
     * "Pay" for E7 (FR-016 AC1): Reviewing → Submitting and the request to send, atomically. [buildRequest] must be
     * pure; it sees the state the transition applies to, so the body always matches the cart that gets locked. The
     * key is the one of the last submission when the body is equal (a retry, also after a network failure returned to
     * Reviewing), otherwise a fresh one from [newKey] (a cart edit, re-pricing after `PRICE_CHANGED`; technical-design
     * §5.3). `null` when the transition is not allowed, so a second tap changes nothing and sends nothing.
     */
    fun submit(
        buildRequest: (OrderState) -> OrderRequest,
        newKey: () -> IdempotencyKey,
    ): Submission? {
        val fresh by lazy(newKey)
        while (true) {
            val current = mutableState.value
            if (current.phase != OrderPhase.REVIEWING || current.cart.isEmpty() || current.issues.isBlocking) {
                return null
            }
            val request = buildRequest(current)
            val key = current.submission?.takeIf { it.request == request }?.key ?: fresh
            val submission = Submission(request, key)
            val next = current.copy(phase = OrderPhase.SUBMITTING, submission = submission)
            if (mutableState.compareAndSet(current, next)) return submission
        }
    }

    /** E7 failed without a verdict on the order (network, 5xx, bad answer): Submitting → Reviewing (FR-016). */
    fun submissionFailed(): Boolean =
        transition({ it == OrderPhase.SUBMITTING }) { it.copy(phase = OrderPhase.REVIEWING) }

    /**
     * E7 answered `PRICE_CHANGED` and the catalog was refreshed (DEV-046): Submitting → Reviewing with the re-priced
     * [cart], the new catalog versions and [issues] to review. The cart is locked while Submitting, so [cart] is the
     * one that was sent, re-priced.
     */
    fun priceChanged(
        cart: Cart,
        menuVersion: String,
        configVersion: String,
        issues: ReviewIssues,
    ): Boolean =
        transition({ it == OrderPhase.SUBMITTING }) {
            it.copy(
                phase = OrderPhase.REVIEWING,
                cart = cart,
                menuVersion = menuVersion,
                configVersion = configVersion,
                issues = issues,
            )
        }

    /**
     * E7 answered `ITEM_UNAVAILABLE` (DEV-046): Submitting → Reviewing with [lineIds] flagged "No longer available".
     * Unknown line IDs are ignored.
     */
    fun itemsUnavailable(lineIds: Set<String>): Boolean =
        transition({ it == OrderPhase.SUBMITTING }) { current ->
            val flagged = lineIds.filterTo(mutableSetOf()) { current.cart.line(it) != null }
            current.copy(
                phase = OrderPhase.REVIEWING,
                issues = current.issues.copy(unavailableLineIds = current.issues.unavailableLineIds + flagged),
            )
        }

    /** "Review" on the price-changed banner: the notes go and Pay is enabled again (FR-016, TC-177). */
    fun reviewPrices(): Boolean = transition({ it.isEditable }) { it.copy(issues = it.issues.reviewed()) }

    /** "Remove it" on the unavailable banner: every flagged line leaves the cart (FR-016). `false` with none flagged. */
    fun removeUnavailable(): Boolean =
        transition({ it.isEditable }, { it.issues.unavailableLineIds.isNotEmpty() }) { current ->
            val gone = current.issues.unavailableLineIds
            current.copy(
                cart = gone.fold(current.cart) { cart, lineId -> cart.remove(lineId) },
                planTag = current.planTag?.takeUnless { tag -> tag.lineIds.any { it in gone } },
                issues = gone.fold(current.issues) { issues, lineId -> issues.without(lineId) },
            )
        }

    /** E7 created the order: Submitting → Paying (FR-016). */
    fun orderCreated(): Boolean = transition({ it == OrderPhase.SUBMITTING }) { it.copy(phase = OrderPhase.PAYING) }

    /**
     * "Back to order" on Checkout after E11 and E8: Paying → Reviewing (FR-019, BR-08). The cancelled order's key is
     * forgotten, so the next Pay creates a new order with a new key.
     */
    fun backToOrder(): Boolean =
        transition({ it == OrderPhase.PAYING }) { it.copy(phase = OrderPhase.REVIEWING, submission = null) }

    /** The backend reported `PAID`: Paying → Paid (A-15). */
    fun paid(): Boolean = transition({ it == OrderPhase.PAYING }) { it.copy(phase = OrderPhase.PAID) }

    /** The Complete screen was shown: Paid → Completed (FR-020). */
    fun complete(): Boolean = transition({ it == OrderPhase.PAID }) { it.copy(phase = OrderPhase.COMPLETED) }

    /** "Cancel" on the Menu or "Start over" on the overlay (FR-009, FR-023): only before Submitting (§5.1). */
    fun cancel(): Boolean = transition({ it.isCancellable }) { it.copy(phase = OrderPhase.CANCELLED) }

    /** The inactivity overlay ran out (FR-023 AC3): only before Submitting (§5.1). */
    fun timeOut(): Boolean = transition({ it.isCancellable }) { it.copy(phase = OrderPhase.TIMED_OUT) }

    private fun editLine(
        lineId: String,
        edit: (Cart) -> Cart,
    ): Boolean =
        transition({ it.isEditable }, { it.cart.line(lineId) != null }) { current ->
            val cart = edit(current.cart)
            current.copy(
                cart = cart,
                planTag = current.planTag.without(lineId),
                issues = if (cart.line(lineId) == null) current.issues.without(lineId) else current.issues,
            )
        }

    /** Applies [change] atomically when the phase passes [allowed] and the state passes [guard]. */
    private inline fun transition(
        allowed: (OrderPhase) -> Boolean,
        guard: (OrderState) -> Boolean = { true },
        change: (OrderState) -> OrderState,
    ): Boolean {
        while (true) {
            val current = mutableState.value
            if (!allowed(current.phase) || !guard(current)) return false
            if (mutableState.compareAndSet(current, change(current))) return true
        }
    }

    private fun planTag(
        before: Cart,
        result: CartAddAllResult.Added,
        budget: Rupiah,
        adults: Int,
        children: Int,
    ): PlanTag {
        val units =
            result.lineIds.associateWith { id ->
                (result.cart.line(id)?.quantity ?: 0) - (before.line(id)?.quantity ?: 0)
            }
        return PlanTag(budget, adults, children, result.lineIds, units)
    }

    /** [cart] without the units this plan added (whole lines when the tag has no quantities). */
    private fun PlanTag.takenOutOf(cart: Cart): Cart =
        lineIds.fold(cart) { acc, lineId ->
            val units = quantities[lineId] ?: Int.MAX_VALUE
            var next = acc
            repeat(minOf(units, acc.line(lineId)?.quantity ?: 0)) { next = next.decrement(lineId) }
            next
        }

    /** The tag, or `null` once one of its lines was edited (architecture §5.1). */
    private fun PlanTag?.without(lineId: String): PlanTag? = this?.takeUnless { lineId in it.lineIds }
}
