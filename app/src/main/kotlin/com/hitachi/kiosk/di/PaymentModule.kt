package com.hitachi.kiosk.di

import com.hitachi.kiosk.core.domain.device.CardTerminal
import com.hitachi.kiosk.core.domain.device.ConnectivityMonitor
import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.payment.PaymentGateway
import com.hitachi.kiosk.core.domain.payment.PaymentStateMachine
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.core.hardware.terminal.UnavailableCardTerminal
import com.hitachi.kiosk.devtools.BindingOverrides
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import java.util.Optional
import javax.inject.Singleton

/**
 * The payment of an order (architecture §5.2, §10, DEV-047): the [PaymentStateMachine] in the application scope, so
 * leaving or recreating Checkout never stops it, and the [CardTerminal] it drives.
 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {
    /**
     * FR-018: the EDC terminal. No real adapter exists before 0.9.0 (OQ-03, ADR-0003), so the release binding is
     * [UnavailableCardTerminal], which fails every sale. Debug builds wrap it through [BindingOverrides].
     */
    @Provides
    @Singleton
    fun cardTerminal(
        real: UnavailableCardTerminal,
        overrides: Optional<BindingOverrides>,
    ): CardTerminal = overrides.map { it.cardTerminal(real) }.orElse(real)

    @Provides
    @Singleton
    @Suppress("LongParameterList") // Wiring: one parameter per port of the machine.
    fun paymentStateMachine(
        @ApplicationScope scope: CoroutineScope,
        clock: Clock,
        ids: IdGenerator,
        gateway: PaymentGateway,
        terminal: CardTerminal,
        orders: OrderRepository,
        connectivity: ConnectivityMonitor,
        sessions: OrderSessionHolder,
    ): PaymentStateMachine =
        PaymentStateMachine(
            scope = scope,
            clock = clock,
            ids = ids,
            gateway = gateway,
            terminal = terminal,
            orders = orders,
            connectivity = connectivity,
            sessions = sessions,
        )
}
