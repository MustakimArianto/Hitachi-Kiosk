package com.hitachi.kiosk.core.domain.cart

import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.Level

/**
 * The words a line description is built from. The app supplies them from string resources (NFR-008), so the domain
 * decides what is said and in which order, and the UI decides how it reads.
 */
interface LineWording {
    /** Between two parts: " · " in the design. */
    val separator: String

    /** A chosen option or a combo side or drink, usually its name. */
    fun option(name: String): String

    /** An ingredient moved off its default level: "No onion", "Extra smoked cheddar". */
    fun levelChange(
        name: String,
        level: Level,
    ): String

    /** A combo size: "Regular" or "Large". */
    fun size(size: ComboSize): String
}

/**
 * DEV-036: the options line under a cart line on Your order and the Upsell strip, for example
 * "Fries · Cola · Extra cheddar · No onion" (FR-012). Empty when the line has no choices to show.
 */
fun CartLine.describe(wording: LineWording): String = describe(choices, wording)

/** The parts of [choices] in their order, worded by [wording] and joined by its separator (FR-010 AC3, FR-012). */
fun describe(
    choices: List<LineChoice>,
    wording: LineWording,
): String =
    choices.joinToString(wording.separator) { choice ->
        when (choice) {
            is LineChoice.Option -> wording.option(choice.name.resolve())
            is LineChoice.LevelChange -> wording.levelChange(choice.name.resolve(), choice.level)
            is LineChoice.Size -> wording.size(choice.size)
        }
    }
