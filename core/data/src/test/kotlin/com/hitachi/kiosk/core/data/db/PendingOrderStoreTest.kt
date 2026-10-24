package com.hitachi.kiosk.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.repository.RoomPendingOrderStore
import com.hitachi.kiosk.core.data.repository.SamplePendingOrder
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/** DEV-045: the `pending_order` table round-trips the order and payment on an in-memory database (TR-REL-02). */
@RunWith(RobolectricTestRunner::class)
class PendingOrderStoreTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KioskDatabase::class.java)
            .build()
    private val dao = database.pendingOrderDao()
    private val store = RoomPendingOrderStore(dao)

    @After
    fun close() = database.close()

    @Test
    fun `an empty table reads as none`() =
        runTest {
            assertThat(store.read()).isNull()
        }

    @Test
    fun `an order round-trips with its key, totals and exact creation time`() =
        runTest {
            val order = SamplePendingOrder.copy(createdAt = Instant.parse("2026-10-22T07:15:30.123456789Z"))

            store.replace(order)

            assertThat(store.read()).isEqualTo(order)
        }

    @Test
    fun `a payment is recorded on its order and survives a read`() =
        runTest {
            store.replace(SamplePendingOrder)

            store.updatePayment(SamplePendingOrder.orderId, "pay_Q1x8", PaymentMethod.QRIS)

            assertThat(store.read())
                .isEqualTo(SamplePendingOrder.copy(paymentId = "pay_Q1x8", method = PaymentMethod.QRIS))
        }

    @Test
    fun `a payment for another order changes nothing`() =
        runTest {
            store.replace(SamplePendingOrder)

            store.updatePayment("ord_other", "pay_x", PaymentMethod.CARD)

            assertThat(store.read()).isEqualTo(SamplePendingOrder)
        }

    @Test
    fun `only the newest order is pending`() =
        runTest {
            store.replace(SamplePendingOrder)
            val newer = SamplePendingOrder.copy(orderId = "ord_new", orderNumber = "A248")

            store.replace(newer)

            assertThat(store.read()).isEqualTo(newer)
        }

    @Test
    fun `delete forgets only the named order`() =
        runTest {
            store.replace(SamplePendingOrder)

            store.delete("ord_other")
            assertThat(store.read()).isEqualTo(SamplePendingOrder)

            store.delete(SamplePendingOrder.orderId)
            assertThat(store.read()).isNull()
        }

    @Test
    fun `clear forgets whatever is pending`() =
        runTest {
            store.replace(SamplePendingOrder)

            store.clear()

            assertThat(store.read()).isNull()
        }

    @Test
    fun `totals are stored as integer Rupiah in their wire form`() =
        runTest {
            val huge = SamplePendingOrder.totals.copy(total = Rupiah(Long.MAX_VALUE))
            store.replace(SamplePendingOrder.copy(totals = huge))

            assertThat(dao.read()!!.totalsJson).contains("\"total\":${Long.MAX_VALUE}")
            assertThat(store.read()!!.totals.total).isEqualTo(Rupiah(Long.MAX_VALUE))
        }

    @Test
    fun `a row with an unknown payment method fails to parse instead of guessing`() =
        runTest {
            store.replace(SamplePendingOrder)
            database.openHelper.writableDatabase.execSQL("UPDATE pending_order SET method = 'COUNTER'")

            val error = runCatching { store.read() }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        }
}
