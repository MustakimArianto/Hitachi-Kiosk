package com.hitachi.kiosk.di

import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.order.RecoverPendingOrder
import com.hitachi.kiosk.core.domain.payment.PaymentGateway
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.recovery.PendingOrderRecoveryCoordinator
import com.hitachi.kiosk.recovery.RecoveryGate
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Process-death recovery (FR-016, DEV-052): the `:core:domain` [RecoverPendingOrder] use case over the order
 * repository and the payment gateway, and the [RecoveryGate] Idle reads, which is the coordinator itself.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecoveryModule {
    @Binds
    abstract fun recoveryGate(coordinator: PendingOrderRecoveryCoordinator): RecoveryGate

    companion object {
        @Provides
        fun recoverPendingOrder(
            orders: OrderRepository,
            gateway: PaymentGateway,
            ids: IdGenerator,
        ): RecoverPendingOrder = RecoverPendingOrder(orders, gateway, ids)
    }
}
