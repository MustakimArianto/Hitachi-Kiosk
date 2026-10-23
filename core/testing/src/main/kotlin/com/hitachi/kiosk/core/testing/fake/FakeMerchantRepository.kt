package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.BoundMerchant
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.Merchant
import com.hitachi.kiosk.core.domain.repository.BindResult
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * A scriptable [MerchantRepository] (NFR-012). [merchantsScript] answers E2. [bindResult] answers [bind] after
 * [bindDelay]; by default it binds the requested merchant. Calls are recorded in [bound] and [logOutCount].
 */
class FakeMerchantRepository : MerchantRepository {
    val merchantsScript = Script<List<Merchant>>(Outcome.Success(SampleData.merchants))

    /** Answer for the next and later [bind] calls; `null` binds the requested merchant. */
    var bindResult: BindResult? = null

    var bindDelay: Duration = Duration.ZERO

    private val recorded = mutableListOf<Merchant>()

    /** Merchants passed to [bind], in order. */
    val bound: List<Merchant> get() = synchronized(recorded) { recorded.toList() }

    var logOutCount: Int = 0
        private set

    override suspend fun merchants(): Outcome<List<Merchant>> = merchantsScript.next()

    override suspend fun bind(merchant: Merchant): BindResult {
        synchronized(recorded) { recorded += merchant }
        delay(bindDelay)
        return bindResult
            ?: BindResult.Bound(SampleData.boundState.copy(merchant = BoundMerchant(merchant.id, merchant.name)))
    }

    override suspend fun logOut() {
        logOutCount++
    }

    /** A bind result for [state], for tests that need a specific binding. */
    fun bindsTo(state: DeviceState.Bound) {
        bindResult = BindResult.Bound(state)
    }
}
