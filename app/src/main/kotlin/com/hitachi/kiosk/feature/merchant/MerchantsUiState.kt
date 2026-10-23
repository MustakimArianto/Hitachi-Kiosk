package com.hitachi.kiosk.feature.merchant

import androidx.compose.runtime.Immutable

/**
 * SCR-03 Choose a restaurant (FR-004; `Merchants.dc.html` and its `S_Merchants_*` boards). The screen draws exactly
 * this; the ViewModel computes every flag.
 *
 * @property content the body below the title.
 * @property logOutEnabled false while a Log out is in progress.
 */
@Immutable
data class MerchantsUiState(
    val content: MerchantsContent = MerchantsContent.Loading,
    val logOutEnabled: Boolean = true,
)

/** The body states of SCR-03 (PRD §7). */
@Immutable
sealed interface MerchantsContent {
    /** "Loading restaurants…" with skeleton cards (`S_Merchants_skeleton`, A-32). */
    data object Loading : MerchantsContent

    /** "No restaurants yet" with "Refresh list" (`S_Merchants_empty`). */
    data object Empty : MerchantsContent

    /**
     * The list: live merchants as large cards, then every coming-soon merchant as a compact disabled row.
     *
     * @property bindingMerchantId the live card being bound; while set, every card ignores taps and that card's arrow
     *   tile shows a spinner.
     */
    data class Loaded(
        val live: List<MerchantCardUi>,
        val comingSoon: List<ComingSoonRowUi>,
        val bindingMerchantId: String? = null,
    ) : MerchantsContent

    /** The failure panel with Retry and Log out (`S_Merchants_bindFailed`, scenario `menuDownloadFailed`). */
    data class Failed(
        val reason: MerchantsFailure,
        val merchantName: String? = null,
    ) : MerchantsContent
}

/** Which failure copy the panel shows (FR-004). */
enum class MerchantsFailure {
    /** E3 failed: "Couldn't connect this kiosk" / "This kiosk could not be linked to {merchant}…". */
    BIND,

    /** Config or menu download failed: "Couldn't download the menu" / "The menu for {merchant} did not finish…". */
    MENU_DOWNLOAD,

    /** E2 failed: "Couldn't load restaurants" / "Check the connection and retry." (FR-004). */
    LOAD,
}

/**
 * A live merchant card. A closed one is drawn disabled with "Closed · opens {[opensAt]}" (`S_Merchants_closed`), or
 * "Closed" when [opensAt] is unknown.
 *
 * @property opensAt the opening time already formatted in device time ("10:00"), only when closed.
 */
@Immutable
data class MerchantCardUi(
    val id: String,
    val name: String,
    val cuisine: String,
    val photoUrl: String?,
    val logoUrl: String?,
    val isOpen: Boolean,
    val opensAt: String? = null,
)

/** A coming-soon merchant: initial, name, type and a "Soon" pill; never tappable (FR-004 AC2). */
@Immutable
data class ComingSoonRowUi(
    val id: String,
    val name: String,
    val initial: String,
    val cuisine: String,
)

/** What the Merchants screen can ask for. */
sealed interface MerchantsEvent {
    /** Tap on a live, open card. Closed cards and coming-soon rows send nothing. */
    data class Select(
        val merchantId: String,
    ) : MerchantsEvent

    /** "Retry" or "Refresh list": shows the skeleton, then reloads the list (TC-179). */
    data object Retry : MerchantsEvent

    /** "Log out" in the header or on the failure panel. */
    data object LogOut : MerchantsEvent
}

/** One-off effects of SCR-03. */
sealed interface MerchantsEffect {
    data object NavigateToIdle : MerchantsEffect

    data object NavigateToSignIn : MerchantsEffect
}
