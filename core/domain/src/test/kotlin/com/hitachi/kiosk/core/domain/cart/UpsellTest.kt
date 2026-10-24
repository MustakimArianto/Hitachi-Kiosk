package com.hitachi.kiosk.core.domain.cart

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.order.OrderPhase
import com.hitachi.kiosk.core.domain.order.OrderState
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test

/** FR-014, DEV-039 (TC-062–TC-064, TC-172). */
class UpsellTest {
    private val menu = SampleData.menu
    private val config = SampleData.config.upsell

    private fun cartOf(vararg productIds: String): Cart =
        productIds.fold(Cart()) { cart, id ->
            (cart.add(CartItem.ProductItem(id, emptyList()), 1, Rupiah(1), emptyList()) as CartAddResult.Added).cart
        }

    @Test
    fun `four sides and desserts are suggested in menu order (FR-014)`() {
        assertThat(Upsell.suggestions(menu, config, Cart()).map { it.id })
            .containsExactly("s1", "s2", "s3", "s4")
            .inOrder()
    }

    @Test
    fun `items already in the cart are not suggested (TC-064)`() {
        assertThat(Upsell.suggestions(menu, config, cartOf("s1", "s2")).map { it.id })
            .containsExactly("s3", "s4", "e1", "e2")
            .inOrder()
    }

    @Test
    fun `nothing is left when every candidate is in the cart (TC-172)`() {
        val all =
            menu.products
                .filter { it.categoryId in config.categoryIds }
                .map { it.id }
                .toTypedArray()

        assertThat(Upsell.suggestions(menu, config, cartOf(*all))).isEmpty()
        assertThat(Upsell.suggestions(menu, config.copy(maxSuggestions = 0), Cart())).isEmpty()
        assertThat(Upsell.suggestions(menu, config.copy(maxSuggestions = 9), Cart())).hasSize(Upsell.MAX_SUGGESTIONS)
    }

    @Test
    fun `the upsell is offered once per order, only with the toggle on (FR-014 AC1, AC2)`() {
        val order = OrderState(OrderType.DINE_IN, OrderPhase.BUILDING, Cart(), "menu-118", "cfg-42")

        assertThat(Upsell.shouldOffer(KioskSettings(), order)).isTrue()
        assertThat(Upsell.shouldOffer(KioskSettings(upsellEnabled = false), order)).isFalse()
        assertThat(Upsell.shouldOffer(KioskSettings(), order.copy(upsellShown = true))).isFalse()
    }
}
