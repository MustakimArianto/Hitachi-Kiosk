package com.hitachi.kiosk.debug

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.testing.fixture.SampleData
import org.junit.Test
import java.io.File

class DemoImagesTest {
    private val assets = File("src/debug/assets/demo")
    private val menu = DemoImages.apply(SampleData.menu)
    private val config = DemoImages.apply(SampleData.config)

    private fun imageUrls(): List<String> =
        menu.products.mapNotNull { it.imageUrl } +
            menu.combos.mapNotNull { it.imageUrl } +
            (menu.comboSlots.sides + menu.comboSlots.drinks).mapNotNull { it.imageUrl } +
            listOfNotNull(config.logoUrl, config.heroPhotoUrl) +
            config.idlePromoImageUrls.filterNotNull()

    @Test
    fun `every demo image url points at a bundled asset`() {
        val urls = imageUrls()
        assertThat(urls).isNotEmpty()
        urls.forEach { url ->
            assertThat(url).startsWith("file:///android_asset/demo/")
            assertThat(File(assets, url.substringAfterLast('/')).isFile).isTrue()
        }
    }

    @Test
    fun `the sample sides drinks and combos all get a photo`() {
        assertThat(menu.combos.map { it.imageUrl }).doesNotContain(null)
        assertThat(menu.comboSlots.drinks.map { it.imageUrl }).doesNotContain(null)
        assertThat(config.idlePromoImageUrls).hasSize(3)
    }

    @Test
    fun `a real backend url is kept`() {
        val real = SampleData.menu.products.first().copy(imageUrl = "https://cdn.merchant.test/a.jpg")
        val result = DemoImages.apply(SampleData.menu.copy(products = listOf(real)))
        assertThat(result.products.single().imageUrl).isEqualTo("https://cdn.merchant.test/a.jpg")
    }
}
