package com.hitachi.kiosk.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.domain.cart.LineWording
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.Level
import java.util.Locale

/**
 * The [LineWording] of the app, from string resources (NFR-008, DEV-036): " · " between parts, "No onion" and
 * "Extra smoked cheddar" for level changes (the ingredient lowercased, as on Item detail), "Regular" / "Large" for a
 * combo size. Used by every screen that shows a cart line's options, for example Your order (FR-012).
 */
class ResourceLineWording(
    private val resources: Resources,
) : LineWording {
    override val separator: String = resources.getString(R.string.customize_summary_separator)

    override fun option(name: String): String = name

    override fun levelChange(
        name: String,
        level: Level,
    ): String =
        resources.getString(
            when (level) {
                Level.NONE -> R.string.item_level_change_none
                Level.REGULAR -> R.string.item_level_change_regular
                Level.EXTRA -> R.string.item_level_change_extra
            },
            name.lowercase(Locale.ENGLISH),
        )

    override fun size(size: ComboSize): String =
        resources.getString(
            when (size) {
                ComboSize.REGULAR -> R.string.combo_size_regular
                ComboSize.LARGE -> R.string.combo_size_large
            },
        )
}

/** A [ResourceLineWording] of the current context's resources, kept across recompositions. */
@Composable
fun rememberLineWording(): LineWording {
    val resources = LocalResources.current
    // Keyed on the configuration too: the same Resources object is updated in place on a configuration change.
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { ResourceLineWording(resources) }
}
