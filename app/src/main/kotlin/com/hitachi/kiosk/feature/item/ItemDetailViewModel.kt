package com.hitachi.kiosk.feature.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hitachi.kiosk.core.designsystem.component.RollDirection
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartLimit
import com.hitachi.kiosk.core.domain.cart.ProductCustomization
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.ModifierGroup
import com.hitachi.kiosk.core.domain.model.ModifierType
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.isSatisfiedByDefaults
import com.hitachi.kiosk.core.domain.model.product
import com.hitachi.kiosk.core.domain.model.toneOf
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.pricing.MenuPricing
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import com.hitachi.kiosk.navigation.ItemDetailRoute as ItemDetailDestination

/** How long "Maximum 20 per item" stays up (`ItemDetail.dc.html`: 2400 ms). */
internal const val MAX_TOAST_MILLIS = 2_400L

/**
 * SCR-08 Item detail (FR-010, DEV-029). Starts from the product's defaults every time it opens (FR-010 AC4), prices
 * the choices with the domain rules, and adds one line, merged and capped at 20, to the order session.
 *
 * Section order (decided for 0.3.0): `SINGLE` groups that need a choice first, then two-option `SINGLE` groups with a
 * free default as switch rows, then the other `SINGLE` groups, then `LEVEL` groups, then `MULTI` groups; product
 * order within each.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val catalogs: CatalogRepository,
        private val orders: OrderSessionHolder,
    ) : ViewModel() {
        private val productId = savedStateHandle.toRoute<ItemDetailDestination>().productId
        private val model = MutableStateFlow(ItemModel())

        private val cartCount: Flow<Int> =
            orders.current.flatMapLatest { session ->
                session?.state?.map { it.cart.itemCount } ?: flowOf(0)
            }

        val state: StateFlow<ItemDetailUiState> =
            combine(model, cartCount) { current, count -> current.toUiState(count) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ItemDetailUiState())

        private val effects = Channel<ItemDetailEffect>(Channel.BUFFERED)
        val effect: Flow<ItemDetailEffect> = effects.receiveAsFlow()

        private var toastJob: Job? = null

        init {
            viewModelScope.launch {
                // The catalog does not change during an order (A-29), so the first value is the one to sell from.
                val menu = catalogs.catalog.first()?.menu
                val product = menu?.product(productId)
                model.update {
                    it.copy(
                        loaded = true,
                        menu = menu,
                        product = product,
                        custom = product?.let { found -> menu?.let { ProductCustomization.defaults(found, it) } },
                    )
                }
            }
        }

        fun onEvent(event: ItemDetailEvent) {
            when (event) {
                is ItemDetailEvent.SelectOption -> customize { it.select(event.groupId, event.optionId) }
                is ItemDetailEvent.SetLevel -> customize { it.setLevel(event.groupId, event.optionId, event.level) }
                is ItemDetailEvent.ToggleExpanded -> toggleExpanded(event.groupId)
                ItemDetailEvent.IncreaseQuantity -> increase()
                ItemDetailEvent.DecreaseQuantity -> decrease()
                ItemDetailEvent.AddToOrder -> add()
                ItemDetailEvent.BackToMenu -> effects.trySend(ItemDetailEffect.NavigateToMenu)
                ItemDetailEvent.ViewOrder -> effects.trySend(ItemDetailEffect.NavigateToYourOrder)
            }
        }

        private fun customize(change: (ProductCustomization) -> ProductCustomization) {
            model.update { current -> current.custom?.let { current.copy(custom = change(it)) } ?: current }
        }

        private fun toggleExpanded(groupId: String) {
            model.update {
                it.copy(expanded = if (groupId in it.expanded) it.expanded - groupId else it.expanded + groupId)
            }
        }

        private fun increase() {
            if (model.value.quantity >= ItemDetailUiState.MAX_QUANTITY) {
                showMaxToast()
                return
            }
            model.update { it.copy(quantity = it.quantity + 1, quantityRoll = RollDirection.UP) }
        }

        private fun decrease() {
            model.update {
                if (it.quantity <= ItemDetailUiState.MIN_QUANTITY) {
                    it
                } else {
                    it.copy(quantity = it.quantity - 1, quantityRoll = RollDirection.DOWN)
                }
            }
        }

        private fun add() {
            val current = model.value
            val custom = current.custom ?: return
            if (current.added || !current.canAdd) return
            val session = orders.current.value
            if (session == null) {
                KioskLog.w("item_add_without_order", "productId" to productId)
                return
            }
            when (val result = session.add(custom.toCartItem(), current.quantity, custom.unitPrice, custom.choices())) {
                is CartAddResult.Added -> {
                    model.update { it.copy(added = true, cartBumps = it.cartBumps + 1) }
                    // FR-010 edge case, TC-045: the merged line was cut to 20.
                    if (result.capped) showMaxToast()
                }

                is CartAddResult.LimitReached -> {
                    when (result.limit) {
                        CartLimit.MAX_PER_LINE -> {
                            showMaxToast()
                        }

                        CartLimit.MAX_LINES, CartLimit.ORDER_LOCKED -> {
                            KioskLog.w("item_add_refused", "productId" to productId, "limit" to result.limit.name)
                        }
                    }
                }
            }
        }

        private fun showMaxToast() {
            toastJob?.cancel()
            model.update { it.copy(showMaxToast = true) }
            toastJob =
                viewModelScope.launch {
                    delay(MAX_TOAST_MILLIS)
                    model.update { it.copy(showMaxToast = false) }
                }
        }
    }

/** What the ViewModel holds; [toUiState] turns it into what the screen draws. */
private data class ItemModel(
    val loaded: Boolean = false,
    val menu: Menu? = null,
    val product: Product? = null,
    val custom: ProductCustomization? = null,
    val quantity: Int = ItemDetailUiState.MIN_QUANTITY,
    val quantityRoll: RollDirection = RollDirection.UP,
    val expanded: Set<String> = emptySet(),
    val added: Boolean = false,
    val showMaxToast: Boolean = false,
    val cartBumps: Int = 0,
) {
    /** A missing product counts as sold out (`S_ItemDetail_soldOut`). */
    val soldOut: Boolean get() = product?.available != true

    val canAdd: Boolean get() = !soldOut && custom?.isComplete == true

    fun toUiState(cartCount: Int): ItemDetailUiState {
        val custom = custom
        val unitPrice = custom?.unitPrice ?: product?.price ?: Rupiah.Zero
        return ItemDetailUiState(
            loaded = loaded,
            product = product?.let { it.toUi(menu?.toneOf(it)) },
            soldOut = soldOut,
            sections = custom?.let { sectionsOf(it, expanded) }.orEmpty(),
            quantity = quantity,
            quantityRoll = quantityRoll,
            total = IdrFormatter.format(unitPrice * quantity),
            cta = cta(),
            dock = if (added) ItemDock.ADDED else ItemDock.EDITING,
            showMaxToast = showMaxToast,
            cartCount = cartCount,
            cartBumps = cartBumps,
        )
    }

    private fun cta(): ItemCta {
        if (soldOut) return ItemCta.Unavailable
        val missing = custom?.missingRequiredGroups?.firstOrNull() ?: return ItemCta.Add
        val name = missing.name.resolve()
        val prompt = name.startsWith(CHOOSE_PREFIX, ignoreCase = true)
        return ItemCta.ChooseFirst(groupName = if (prompt) name else name.lowercase(Locale.ENGLISH), prompt = prompt)
    }
}

/** A group name that already asks for the choice ("Choose a size") is used as is in the CTA (FR-010 edge case). */
private const val CHOOSE_PREFIX = "Choose "

private fun Product.toUi(tone: String?) =
    ItemProductUi(
        name = name.resolve(),
        price = IdrFormatter.format(price),
        description = description?.resolve()?.takeIf { it.isNotBlank() },
        badge = badge?.label?.resolve()?.takeIf { it.isNotBlank() },
        kcal = kcal,
        tags = tags?.resolve()?.takeIf { it.isNotBlank() },
        imageUrl = imageUrl,
        tone = tone,
    )

/** The sections in the screen order described on [ItemDetailViewModel]. */
private fun sectionsOf(
    custom: ProductCustomization,
    expanded: Set<String>,
): List<ItemSectionUi> {
    val single = custom.groups.filter { it.type == ModifierType.SINGLE }
    val needChoice = single.filter { !it.isSatisfiedByDefaults() }
    val switches = single.filter { it !in needChoice && it.isSwitchShaped() }
    val otherSingle = single - needChoice.toSet() - switches.toSet()
    return needChoice.map { it.toChoice(custom, required = true) } +
        switches.map { it.toSwitch(custom) } +
        otherSingle.map { it.toChoice(custom, required = false) } +
        custom.groups.filter { it.type == ModifierType.LEVEL }.map { it.toLevels(custom, it.id in expanded) } +
        custom.groups.filter { it.type == ModifierType.MULTI }.map { it.toExtras(custom, it.id in expanded) }
}

/** Two options, one of them the free default: an on/off choice ("Make it a meal", "Less ice", "Large"). */
private fun ModifierGroup.isSwitchShaped(): Boolean =
    options.size == 2 && options.count { it.isDefault } == 1 && options.first { it.isDefault }.priceDelta == Rupiah.Zero

private fun signedOrNull(amount: Rupiah): String? = amount.takeIf { it > Rupiah.Zero }?.let(IdrFormatter::formatSigned)

private fun ModifierGroup.toChoice(
    custom: ProductCustomization,
    required: Boolean,
) = ItemSectionUi.Choice(
    groupId = id,
    title = name.resolve(),
    options =
        options.map { option ->
            ChoiceOptionUi(
                id = option.id,
                name = option.name.resolve(),
                description = option.description?.resolve()?.takeIf { it.isNotBlank() },
                priceDelta = signedOrNull(option.priceDelta),
                selected = custom.isSelected(id, option.id),
            )
        },
    requiredTag =
        when {
            !required -> null
            this in custom.missingRequiredGroups -> RequiredTag.MISSING
            else -> RequiredTag.DONE
        },
)

private fun ModifierGroup.toSwitch(custom: ProductCustomization): ItemSectionUi.Switch {
    val off = options.first { it.isDefault }
    val on = options.first { !it.isDefault }
    return ItemSectionUi.Switch(
        groupId = id,
        title = on.name.resolve(),
        description = on.description?.resolve()?.takeIf { it.isNotBlank() },
        priceDelta = signedOrNull(on.priceDelta),
        imageUrl = on.imageUrl,
        checked = custom.isSelected(id, on.id),
        onOptionId = on.id,
        offOptionId = off.id,
    )
}

private fun ModifierGroup.toLevels(
    custom: ProductCustomization,
    expanded: Boolean,
): ItemSectionUi.Levels {
    val rows =
        options.map { option ->
            LevelRowUi(
                optionId = option.id,
                name = option.name.resolve(),
                note = option.note?.resolve()?.takeIf { it.isNotBlank() },
                level = custom.levelOf(id, option.id) ?: MenuPricing.defaultLevel(option),
            )
        }
    val changes =
        options.zip(rows).mapNotNull { (option, row) ->
            // The summary reads "No onion · Extra smoked cheddar" (`ItemDetail.dc.html`).
            LevelChangeUi(row.name.lowercase(Locale.ENGLISH), row.level)
                .takeIf { row.level != MenuPricing.defaultLevel(option) }
        }
    return ItemSectionUi.Levels(
        groupId = id,
        title = name.resolve(),
        expanded = expanded,
        changes = changes,
        rows = rows,
    )
}

private fun ModifierGroup.toExtras(
    custom: ProductCustomization,
    expanded: Boolean,
): ItemSectionUi.Extras {
    val atMax = custom.isAtMax(id)
    val chosen = options.filter { custom.isSelected(id, it.id) }
    val chosenTotal = chosen.fold(Rupiah.Zero) { sum, option -> sum + option.priceDelta }
    return ItemSectionUi.Extras(
        groupId = id,
        title = name.resolve(),
        expanded = expanded,
        chosenNames = chosen.map { it.name.resolve() },
        chosenTotal = signedOrNull(chosenTotal),
        options =
            options.map { option ->
                val selected = custom.isSelected(id, option.id)
                ExtraOptionUi(
                    id = option.id,
                    name = option.name.resolve(),
                    price = IdrFormatter.formatSigned(option.priceDelta),
                    imageUrl = option.imageUrl,
                    selected = selected,
                    enabled = selected || !atMax,
                )
            },
    )
}
