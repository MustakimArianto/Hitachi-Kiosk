package com.hitachi.kiosk.feature.activation

import androidx.compose.runtime.Immutable

/**
 * SCR-02 Sign in (FR-002, FR-003; `Login.dc.html`). The screen draws exactly this; the ViewModel computes every
 * flag.
 *
 * @property versionName the real `versionName` for the header pill (PRD §4.1 C7).
 * @property button the label of the Log in button.
 * @property buttonEnabled false while signing in, while the serial number is being read, and when it cannot be read.
 * @property busy shows the spinner in the button's arrow tile.
 * @property error the error panel, or `null` for the intro text.
 */
@Immutable
data class SignInUiState(
    val versionName: String,
    val button: SignInButton = SignInButton.LOG_IN,
    val buttonEnabled: Boolean = false,
    val busy: Boolean = true,
    val error: SignInError? = null,
)

/** "Log in", "Signing in…" or "Try again" (`Login.dc.html`). */
enum class SignInButton { LOG_IN, SIGNING_IN, TRY_AGAIN }

/** The error panel variants of FR-002 and FR-003. Each has its own title and body. */
@Immutable
sealed interface SignInError {
    /** HTTP 404 `DEVICE_NOT_REGISTERED`: shows [serialNumber] large (`S_Login_notRegistered`, A-01). */
    data class NotRegistered(
        val serialNumber: String,
    ) : SignInError

    /** HTTP 403 `DEVICE_DISABLED`: "This kiosk has been disabled. Contact your administrator." */
    data object Disabled : SignInError

    /** No connection or a timeout: "Can't reach Hitachi Kiosk" (`S_Login_networkTimeout`). */
    data object Network : SignInError

    /** No serial number: "Can't read this device's serial number"; Log in stays disabled (FR-002). */
    data object SerialUnreadable : SignInError

    /** Any other failure: "Couldn't sign in" (FR-003). */
    data object Generic : SignInError
}

/** What the Sign in screen can ask for. */
sealed interface SignInEvent {
    /** Log in or Try again. Ignored while busy (FR-003 AC3). */
    data object LogIn : SignInEvent
}

/** One-off effects of Sign in. */
sealed interface SignInEffect {
    /** Activated, no merchant yet → SCR-03. */
    data object NavigateToMerchants : SignInEffect

    /** A pre-assigned merchant was bound → SCR-04 (api-spec E1 `boundMerchantId`). */
    data object NavigateToIdle : SignInEffect
}
