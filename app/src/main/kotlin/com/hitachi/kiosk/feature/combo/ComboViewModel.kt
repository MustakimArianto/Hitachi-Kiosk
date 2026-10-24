package com.hitachi.kiosk.feature.combo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.ComboCustomization
import com.hitachi.kiosk.core.domain.cart.ComboMissing
import com.hitachi.kiosk.core.domain.format.IdrFormatter
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.ComboSlotOption
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.availableCombos
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
import javax.inject.Inject
import com.hitachi.kiosk.navigation.ComboRoute as ComboDestination

/** Side and Drink show three cards until "More sides (n)" / "More drinks (n)" is tapped (FR-011). */
internal const val VISIBLE_SLOT_OPTIONS = 3

/**
 * SCR-07 Combo (FR-011, DEV-030). Starts from the FR-011 defaults (the route's combo or the first one, the first
 * available side and drink, Regular), prices with the domain rules, and adds one combo line per "Add combo". The
 * Added state lasts 1.8 s; taps during it add nothing (TC-049), and any change of option ends it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ComboViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val catalogs: CatalogRepository,
        private val orders: OrderSessionHolder,
    ) : ViewModel() {
        private val comboId = savedStateHandle.toRoute<ComboDestination>().comboId
        private val model = MutableStateFlow(ComboModel())

        private val cartCount: Flow<Int> =
            orders.current.flatMapLatest { session ->
                session?.state?.map { it.cart.itemCount } ?: flowOf(0)
            }

        val state: StateFlow<ComboUiState> =
            combine(model, cartCount) { current, count -> current.toUiState(count) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ComboUiState())

        private val effects = Channel<ComboEffect>(Channel.BUFFERED)
        val effect: Flow<ComboEffect> = effects.receiveAsFlow()

        private var addedJob: Job? = null

        init {
            viewModelScope.launch {
                // The catalog does not change during an order (A-29).
                val menu = catalogs.catalog.first()?.menu
                model.update {
                    it.copy(
                        loaded = true,
                        menu = menu,
                        custom =
                            menu?.let { m ->
                                ComboCustomization.defaults(m, comboId)
                            },
                    )
                }
            }
        }

        fun onEvent(event: ComboEvent) {
            when (event) {
                is ComboEvent.SelectCombo -> {
                    selectCombo(event.comboId)
                }

                is ComboEvent.SelectSide -> {
                    customize { it.selectSide(event.sideId) }
                }

                is ComboEvent.SelectDrink -> {
                    customize { it.selectDrink(event.drinkId) }
                }

                is ComboEvent.SelectLarge -> {
                    customize {
                        it.selectSize(
                            if (event.large) ComboSize.LARGE else ComboSize.REGULAR,
                        )
                    }
                }

                ComboEvent.ToggleMoreSides -> {
                    model.update { it.copy(moreSides = !it.moreSides) }
                }

                ComboEvent.ToggleMoreDrinks -> {
                    model.update { it.copy(moreDrinks = !it.moreDrinks) }
                }

                ComboEvent.AddCombo -> {
                    add()
                }

                ComboEvent.BackToMenu -> {
                    effects.trySend(ComboEffect.NavigateToMenu)
                }

                ComboEvent.ViewOrder -> {
                    effects.trySend(ComboEffect.NavigateToYourOrder)
                }
            }
        }

        private fun selectCombo(id: String) {
            val combo =
                model.value.menu
                    ?.availableCombos()
                    ?.firstOrNull { it.id == id } ?: return
            customize { it.selectCombo(combo) }
        }

        /** Applies a change; a real change ends the Added state, as in the design. */
        private fun customize(change: (ComboCustomization) -> ComboCustomization) {
            val current = model.value.custom ?: return
            val next = change(current)
            if (next == current) return
            addedJob?.cancel()
            model.update { it.copy(custom = next, added = false) }
        }

        private fun add() {
            val current = model.value
            if (current.added) return
            val custom = current.custom
            val item = custom?.toCartItem() ?: return
            val session = orders.current.value
            val result = session?.add(item, 1, custom.unitPrice, custom.choices())
            when (result) {
                is CartAddResult.Added -> {
                    model.update { it.copy(added = true, cartBumps = it.cartBumps + 1) }
                    addedJob =
                        viewModelScope.launch {
                            delay(KioskMotion.Combo.ADDED_STATE_MILLIS.toLong())
                            model.update { it.copy(added = false) }
                        }
                }

                is CartAddResult.LimitReached -> {
                    KioskLog.w("combo_add_limit", "limit" to result.limit)
                }

                null -> {
                    KioskLog.w("combo_add_without_order", "comboId" to custom.combo.id)
                }
            }
        }
    }

/** What the ViewModel holds; [toUiState] turns it into what the screen draws. */
private data class ComboModel(
    val loaded: Boolean = false,
    val menu: Menu? = null,
    val custom: ComboCustomization? = null,
    val moreSides: Boolean = false,
    val moreDrinks: Boolean = false,
    val added: Boolean = false,
    val cartBumps: Int = 0,
) {
    fun toUiState(cartCount: Int): ComboUiState =
        comboUiState(
            menu = menu,
            custom = custom,
            moreSides = moreSides,
            moreDrinks = moreDrinks,
            added = added,
            cartCount = cartCount,
            cartBumps = cartBumps,
        ).copy(loaded = loaded)
}

/** The screen state for [custom] on [menu] (FR-011). Also used by the screenshot tests to show missing choices. */
@Suppress("LongParameterList")
internal fun comboUiState(
    menu: Menu?,
    custom: ComboCustomization?,
    moreSides: Boolean = false,
    moreDrinks: Boolean = false,
    added: Boolean = false,
    cartCount: Int = 0,
    cartBumps: Int = 0,
): ComboUiState {
    if (menu == null || custom == null) {
        return ComboUiState(loaded = true, cartCount = cartCount, cartBumps = cartBumps)
    }
    val missing = custom.missing
    return ComboUiState(
        loaded = true,
        combos =
            menu.availableCombos().map { combo ->
                ComboCardUi(
                    id = combo.id,
                    name = combo.name.resolve(),
                    includes = combo.includes?.resolve()?.takeIf { it.isNotBlank() },
                    price = IdrFormatter.format(combo.price),
                    saving = MenuPricing.comboSaving(combo).takeIf { it > Rupiah.Zero }?.let(IdrFormatter::format),
                    imageUrl = combo.imageUrl,
                    tone = combo.tone,
                    selected = combo.id == custom.combo.id,
                )
            },
        sides =
            slotSection(
                options = custom.slots.sides,
                selectedId = custom.side?.id,
                expanded = moreSides,
                custom = custom,
                tone = menu.roleTone(ProductRole.SIDE),
            ),
        drinks =
            slotSection(
                options = custom.slots.drinks,
                selectedId = custom.drink?.id,
                expanded = moreDrinks,
                custom = custom,
                tone = menu.roleTone(ProductRole.DRINK),
            ),
        large = custom.size == ComboSize.LARGE,
        largeUpcharge = IdrFormatter.formatSigned(custom.largeUpcharge),
        summary =
            ComboSummaryUi(
                shortName = (custom.combo.shortName ?: custom.combo.name).resolve(),
                side = custom.side?.name?.resolve(),
                drink = custom.drink?.name?.resolve(),
                large = custom.size == ComboSize.LARGE,
                missing = missing,
            ),
        total = IdrFormatter.format(custom.unitPrice),
        cta =
            when (missing) {
                ComboMissing.SIDE_AND_DRINK -> ComboCta.PICK_SIDE_AND_DRINK
                ComboMissing.SIDE -> ComboCta.CHOOSE_SIDE
                ComboMissing.DRINK -> ComboCta.CHOOSE_DRINK
                null -> if (added) ComboCta.ADDED else ComboCta.ADD
            },
        cartCount = cartCount,
        cartBumps = cartBumps,
    )
}

private fun slotSection(
    options: List<ComboSlotOption>,
    selectedId: String?,
    expanded: Boolean,
    custom: ComboCustomization,
    tone: String?,
) = SlotSectionUi(
    visible =
        options
            .filterIndexed { index, option -> index < VISIBLE_SLOT_OPTIONS || expanded || option.id == selectedId }
            .map { option ->
                SlotOptionUi(
                    id = option.id,
                    name = option.name.resolve(),
                    upcharge = custom.upchargeOf(option).takeIf { it > Rupiah.Zero }?.let(IdrFormatter::formatSigned),
                    imageUrl = option.imageUrl,
                    available = option.available,
                    selected = option.id == selectedId,
                )
            },
    hiddenCount = (options.size - VISIBLE_SLOT_OPTIONS).coerceAtLeast(0),
    expanded = expanded,
    missing = selectedId == null,
    tone = tone,
)

/** The placeholder tone of side or drink photos: the tone of the category those products sit in (NFR-013). */
private fun Menu.roleTone(role: ProductRole): String? = products.firstOrNull { it.role == role }?.let { toneOf(it) }
