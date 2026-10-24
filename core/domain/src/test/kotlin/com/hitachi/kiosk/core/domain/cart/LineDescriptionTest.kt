package com.hitachi.kiosk.core.domain.cart

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.Rupiah
import org.junit.Test

class LineDescriptionTest {
    private val wording =
        object : LineWording {
            override val separator = " · "

            override fun option(name: String) = name

            override fun levelChange(
                name: String,
                level: Level,
            ) = when (level) {
                Level.NONE -> "No ${name.lowercase()}"
                Level.REGULAR -> "Regular ${name.lowercase()}"
                Level.EXTRA -> "Extra ${name.lowercase()}"
            }

            override fun size(size: ComboSize) = if (size == ComboSize.LARGE) "Large" else "Regular"
        }

    @Test
    fun `the design's line reads Fries, Cola, Extra cheddar, No onion (DEV-036)`() {
        val choices =
            listOf(
                LineChoice.Option(LocalizedText(en = "Fries")),
                LineChoice.Option(LocalizedText(en = "Cola")),
                LineChoice.LevelChange(LocalizedText(en = "Cheddar"), Level.EXTRA),
                LineChoice.LevelChange(LocalizedText(en = "Onion"), Level.NONE),
            )

        assertThat(describe(choices, wording)).isEqualTo("Fries · Cola · Extra cheddar · No onion")
    }

    @Test
    fun `a combo line names its side, drink and size, and a plain line says nothing`() {
        val combo =
            CartLine(
                lineId = "l1",
                item = CartItem.ProductItem("x", emptyList()),
                quantity = 1,
                unitPrice = Rupiah(1),
                choices = listOf(LineChoice.Option(LocalizedText(id = "Kentang")), LineChoice.Size(ComboSize.LARGE)),
            )

        assertThat(combo.describe(wording)).isEqualTo("Kentang · Large")
        assertThat(combo.copy(choices = emptyList()).describe(wording)).isEmpty()
        assertThat(describe(listOf(LineChoice.LevelChange(LocalizedText(en = "Sauce"), Level.REGULAR)), wording))
            .isEqualTo("Regular sauce")
        assertThat(describe(listOf(LineChoice.Size(ComboSize.REGULAR)), wording)).isEqualTo("Regular")
    }
}
