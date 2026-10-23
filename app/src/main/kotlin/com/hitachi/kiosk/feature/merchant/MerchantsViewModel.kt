package com.hitachi.kiosk.feature.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.Merchant
import com.hitachi.kiosk.core.domain.model.MerchantStatus
import com.hitachi.kiosk.core.domain.repository.BindResult
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.time.toJavaInstant

/**
 * SCR-03 Choose a restaurant (FR-004, DEV-015). Loads the list when it opens and on Retry, binds a live and open
 * merchant (one at a time), and logs out. A 401 is not shown here: the session wipe sends the app to Sign in.
 */
@HiltViewModel
class MerchantsViewModel
    @Inject
    constructor(
        private val merchants: MerchantRepository,
        zone: ZoneId,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(MerchantsUiState())
        val state: StateFlow<MerchantsUiState> = mutableState.asStateFlow()

        private val effects = Channel<MerchantsEffect>(Channel.BUFFERED)
        val effect: Flow<MerchantsEffect> = effects.receiveAsFlow()

        private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone)
        private var loaded: List<Merchant> = emptyList()

        init {
            load()
        }

        fun onEvent(event: MerchantsEvent) {
            when (event) {
                is MerchantsEvent.Select -> select(event.merchantId)
                MerchantsEvent.Retry -> load()
                MerchantsEvent.LogOut -> logOut()
            }
        }

        private fun load() {
            if (isBinding()) return
            mutableState.update { it.copy(content = MerchantsContent.Loading) }
            viewModelScope.launch {
                when (val outcome = merchants.merchants()) {
                    is Outcome.Success -> show(outcome.value)
                    is Outcome.Failure -> fail(outcome.error, MerchantsFailure.LOAD, merchantName = null)
                }
            }
        }

        private fun show(list: List<Merchant>) {
            loaded = list
            val live = list.filter { it.status == MerchantStatus.LIVE }
            // FR-004 edge case: no live merchant means "No restaurants yet", even with coming-soon ones.
            val content =
                if (live.isEmpty()) {
                    MerchantsContent.Empty
                } else {
                    MerchantsContent.Loaded(
                        live = live.map { it.toCard() },
                        comingSoon = list.filter { it.status == MerchantStatus.COMING_SOON }.map { it.toRow() },
                    )
                }
            mutableState.update { it.copy(content = content) }
        }

        private fun select(merchantId: String) {
            val content = mutableState.value.content as? MerchantsContent.Loaded ?: return
            if (content.bindingMerchantId != null) return
            // Closed and coming-soon merchants cannot be bound (FR-004 AC2, A-03).
            val merchant = loaded.firstOrNull { it.id == merchantId }?.takeIf { it.isBindable } ?: return
            mutableState.update { it.copy(content = content.copy(bindingMerchantId = merchantId)) }
            viewModelScope.launch {
                when (val result = merchants.bind(merchant)) {
                    is BindResult.Bound -> {
                        effects.send(MerchantsEffect.NavigateToIdle)
                    }

                    is BindResult.BindFailed -> {
                        fail(result.error, MerchantsFailure.BIND, merchant.name)
                    }

                    is BindResult.MenuDownloadFailed -> {
                        fail(
                            result.error,
                            MerchantsFailure.MENU_DOWNLOAD,
                            merchant.name,
                        )
                    }
                }
            }
        }

        private fun fail(
            error: DomainError,
            reason: MerchantsFailure,
            merchantName: String?,
        ) {
            // The session was wiped and the app is already on its way to Sign in (TR-NET-06).
            if (error == DomainError.Unauthorized) return
            mutableState.update { it.copy(content = MerchantsContent.Failed(reason, merchantName)) }
        }

        private fun logOut() {
            if (!mutableState.value.logOutEnabled) return
            mutableState.update { it.copy(logOutEnabled = false) }
            viewModelScope.launch {
                merchants.logOut()
                effects.send(MerchantsEffect.NavigateToSignIn)
            }
        }

        private fun isBinding(): Boolean =
            (mutableState.value.content as? MerchantsContent.Loaded)?.bindingMerchantId != null

        private fun Merchant.toCard() =
            MerchantCardUi(
                id = id,
                name = name,
                cuisine = cuisine?.resolve().orEmpty(),
                photoUrl = photoUrl,
                logoUrl = logoUrl,
                isOpen = isOpen,
                opensAt = opensAt?.takeIf { !isOpen }?.let { timeFormat.format(it.toJavaInstant()) },
            )

        private fun Merchant.toRow() =
            ComingSoonRowUi(
                id = id,
                name = name,
                initial = name.trim().take(1).uppercase(),
                cuisine = cuisine?.resolve().orEmpty(),
            )
    }
