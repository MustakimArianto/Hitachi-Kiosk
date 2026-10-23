package com.hitachi.kiosk.feature.merchant

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.MerchantStatus
import com.hitachi.kiosk.core.domain.repository.BindResult
import com.hitachi.kiosk.core.testing.fake.FakeMerchantRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

/** SCR-03 behaviour (FR-004; TC-014–TC-018, TC-179). */
class MerchantsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val merchants = FakeMerchantRepository()
    private val jakarta = ZoneId.of("Asia/Jakarta")

    private fun viewModel() = MerchantsViewModel(merchants, jakarta)

    private val urbanGrillCard =
        MerchantCardUi(
            id = SampleData.MERCHANT_ID,
            name = "Urban Grill",
            cuisine = "Burgers & grill",
            photoUrl = SampleData.urbanGrill.photoUrl,
            logoUrl = SampleData.urbanGrill.logoUrl,
            isOpen = true,
        )

    @Test
    fun `the list shows the live card and every coming-soon merchant`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            assertThat(vm.state.value.content).isEqualTo(MerchantsContent.Loading)

            advanceUntilIdle()

            val content = vm.state.value.content as MerchantsContent.Loaded
            assertThat(content.live).containsExactly(urbanGrillCard)
            assertThat(content.comingSoon.map { it.name })
                .containsExactly("Bella Pasta", "Sakura Ramen", "Taco Loco", "Fresh Bowl")
                .inOrder()
            assertThat(
                content.comingSoon.first(),
            ).isEqualTo(ComingSoonRowUi("mer_bellapasta", "Bella Pasta", "B", "Italian kitchen"))
        }

    @Test
    fun `a closed merchant shows its opening time in device time`() =
        runTest(main.dispatcher) {
            merchants.merchantsScript.succeed(SampleData.merchantsWithClosed)
            val vm = viewModel()
            advanceUntilIdle()

            val card = (vm.state.value.content as MerchantsContent.Loaded).live.single()
            assertThat(card.isOpen).isFalse()
            assertThat(card.opensAt).isEqualTo("10:00")
        }

    @Test
    fun `a closed merchant without an opening time shows no time`() =
        runTest(main.dispatcher) {
            merchants.merchantsScript.succeed(listOf(SampleData.urbanGrill.copy(isOpen = false)))
            val vm = viewModel()
            advanceUntilIdle()

            assertThat((vm.state.value.content as MerchantsContent.Loaded).live.single().opensAt).isNull()
        }

    @Test
    fun `no live merchant shows No restaurants yet`() =
        runTest(main.dispatcher) {
            merchants.merchantsScript.succeed(SampleData.merchants.filter { it.status == MerchantStatus.COMING_SOON })
            val vm = viewModel()
            advanceUntilIdle()
            assertThat(vm.state.value.content).isEqualTo(MerchantsContent.Empty)

            merchants.merchantsScript.succeed(emptyList())
            vm.onEvent(MerchantsEvent.Retry)
            advanceUntilIdle()
            assertThat(vm.state.value.content).isEqualTo(MerchantsContent.Empty)
        }

    @Test
    fun `a failed list shows the load failure, and Retry shows the skeleton then the list`() =
        runTest(main.dispatcher) {
            merchants.merchantsScript.enqueue(Outcome.Failure(DomainError.Network()))
            val vm = viewModel()
            advanceUntilIdle()
            assertThat(vm.state.value.content).isEqualTo(MerchantsContent.Failed(MerchantsFailure.LOAD))

            vm.onEvent(MerchantsEvent.Retry)
            assertThat(vm.state.value.content).isEqualTo(MerchantsContent.Loading)
            advanceUntilIdle()
            assertThat(vm.state.value.content).isInstanceOf(MerchantsContent.Loaded::class.java)
        }

    @Test
    fun `tapping a live card binds it and goes to Idle`() =
        runTest(main.dispatcher) {
            merchants.bindDelay = 1.seconds
            val vm = viewModel()
            advanceUntilIdle()

            vm.onEvent(MerchantsEvent.Select(SampleData.MERCHANT_ID))
            runCurrent()
            assertThat(
                (vm.state.value.content as MerchantsContent.Loaded).bindingMerchantId,
            ).isEqualTo(SampleData.MERCHANT_ID)

            assertThat(vm.effect.first()).isEqualTo(MerchantsEffect.NavigateToIdle)
            assertThat(merchants.bound).containsExactly(SampleData.urbanGrill)
        }

    @Test
    fun `taps while binding, on closed cards and on coming-soon rows are ignored`() =
        runTest(main.dispatcher) {
            merchants.merchantsScript.succeed(SampleData.merchantsWithClosed)
            val vm = viewModel()
            advanceUntilIdle()

            vm.onEvent(MerchantsEvent.Select(SampleData.MERCHANT_ID))
            vm.onEvent(MerchantsEvent.Select("mer_bellapasta"))
            vm.onEvent(MerchantsEvent.Select("mer_unknown"))
            advanceUntilIdle()
            assertThat(merchants.bound).isEmpty()

            merchants.merchantsScript.succeed(SampleData.merchants)
            merchants.bindDelay = 1.seconds
            vm.onEvent(MerchantsEvent.Retry)
            advanceUntilIdle()
            vm.onEvent(MerchantsEvent.Select(SampleData.MERCHANT_ID))
            vm.onEvent(MerchantsEvent.Select(SampleData.MERCHANT_ID))
            vm.onEvent(MerchantsEvent.Retry)
            advanceUntilIdle()
            assertThat(merchants.bound).hasSize(1)
        }

    @Test
    fun `a bind failure and a menu download failure name the merchant`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            merchants.bindResult = BindResult.BindFailed(DomainError.Network())
            vm.onEvent(MerchantsEvent.Select(SampleData.MERCHANT_ID))
            advanceUntilIdle()
            assertThat(vm.state.value.content).isEqualTo(MerchantsContent.Failed(MerchantsFailure.BIND, "Urban Grill"))

            vm.onEvent(MerchantsEvent.Retry)
            advanceUntilIdle()
            merchants.bindResult = BindResult.MenuDownloadFailed(DomainError.Server("SERVER_ERROR", 503))
            vm.onEvent(MerchantsEvent.Select(SampleData.MERCHANT_ID))
            advanceUntilIdle()
            assertThat(vm.state.value.content)
                .isEqualTo(MerchantsContent.Failed(MerchantsFailure.MENU_DOWNLOAD, "Urban Grill"))
        }

    @Test
    fun `a 401 shows no panel because the app returns to Sign in`() =
        runTest(main.dispatcher) {
            merchants.merchantsScript.fail(DomainError.Unauthorized)
            val vm = viewModel()
            advanceUntilIdle()

            assertThat(vm.state.value.content).isEqualTo(MerchantsContent.Loading)
        }

    @Test
    fun `Log out logs out once and goes to Sign in`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.onEvent(MerchantsEvent.LogOut)
            assertThat(vm.state.value.logOutEnabled).isFalse()
            vm.onEvent(MerchantsEvent.LogOut)

            assertThat(vm.effect.first()).isEqualTo(MerchantsEffect.NavigateToSignIn)
            assertThat(merchants.logOutCount).isEqualTo(1)
        }
}
