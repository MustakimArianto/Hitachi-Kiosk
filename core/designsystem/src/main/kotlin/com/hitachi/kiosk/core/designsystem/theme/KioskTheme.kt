package com.hitachi.kiosk.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.hitachi.kiosk.core.designsystem.motion.LocalReduceMotion
import com.hitachi.kiosk.core.designsystem.unit.LocalDesignScale
import com.hitachi.kiosk.core.designsystem.unit.LocalLargeText

/**
 * The "Linen & Paprika" theme (PRD §11, D-02, DEV-004). Light only (TR-DSP-05).
 *
 * Provides [LocalKioskColors], [LocalKioskTypography], [LocalKioskShapes], [LocalKioskSpacing], [LocalAccent],
 * [LocalLargeText] and [LocalReduceMotion], and sets up Material 3 underneath with the same palette (Material is
 * the foundation only; feature code reads [KioskTheme], architecture §12). Put a
 * [com.hitachi.kiosk.core.designsystem.unit.DesignFrame] directly inside it so sizes follow the screen width.
 *
 * @param accent merchant accent from config `accentColor` (A-38), one of [KioskPalette.AccentOptions].
 * @param largeText the staff "Larger text" setting: text is 1.15× (FR-026, TR-DSP-04).
 * @param reduceMotion the reduce-motion setting OR the system "Remove animations" setting (TR-ACC-05).
 */
@Composable
fun KioskTheme(
    accent: Color = KioskPalette.AccentPaprika,
    largeText: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = remember(accent) { KioskColors(accent = accent) }
    CompositionLocalProvider(
        LocalKioskColors provides colors,
        LocalAccent provides accent,
        LocalLargeText provides largeText,
        LocalReduceMotion provides reduceMotion,
    ) {
        ProvideScaledTokens(scale = LocalDesignScale.current, content = content)
    }
}

/**
 * Re-provides the scale-dependent tokens (type, shapes, spacing and the Material theme) for [scale]. Used by
 * [KioskTheme] and by `DesignFrame`, which changes the scale below the theme.
 */
@Composable
internal fun ProvideScaledTokens(
    scale: Float,
    content: @Composable () -> Unit,
) {
    val colors = LocalKioskColors.current
    val largeText = LocalLargeText.current
    val typography = remember(scale, largeText) { kioskTypography(scale, largeText) }
    val shapes = remember(scale) { kioskShapes(scale) }
    val spacing = remember(scale) { kioskSpacing(scale) }
    val colorScheme = remember(colors) { colors.toMaterialColorScheme() }
    val materialTypography = remember(typography) { typography.toMaterialTypography() }
    val materialShapes = remember(shapes) { shapes.toMaterialShapes() }
    CompositionLocalProvider(
        LocalDesignScale provides scale,
        LocalKioskTypography provides typography,
        LocalKioskShapes provides shapes,
        LocalKioskSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = materialTypography,
            shapes = materialShapes,
            content = content,
        )
    }
}

/** Access to the current [KioskTheme] tokens (architecture §12, DEV-004). */
object KioskTheme {
    /** Color roles, including the merchant accent (PRD §11.1). */
    val colors: KioskColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKioskColors.current

    /** Type scale, scaled to the screen and the Larger text setting (PRD §11.1–11.2). */
    val typography: KioskTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalKioskTypography.current

    /** Corner shapes (PRD §11.1). */
    val shapes: KioskShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalKioskShapes.current

    /** Layout tokens: margins, header, grid, touch target (PRD §11.1). */
    val spacing: KioskSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalKioskSpacing.current

    /** Elevations: the floating shadow for docks and overlays (PRD §11.1). */
    val elevation: KioskElevation
        @Composable
        @ReadOnlyComposable
        get() = kioskElevation(LocalDesignScale.current, LocalKioskColors.current)
}

/** Maps the Kiosk palette onto Material 3 roles so Material foundation components match (technical-design §10.1). */
internal fun KioskColors.toMaterialColorScheme(): ColorScheme =
    lightColorScheme(
        primary = accent,
        onPrimary = surface,
        secondary = ink,
        onSecondary = surface,
        tertiary = success,
        onTertiary = surface,
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = muted,
        surfaceContainer = surface,
        surfaceContainerLow = surface,
        surfaceContainerHigh = surface,
        outline = line,
        outlineVariant = cardBorder,
    )

private fun KioskTypography.toMaterialTypography(): Typography =
    Typography(
        displayLarge = display,
        headlineLarge = title,
        headlineMedium = section,
        titleLarge = section,
        titleMedium = item,
        bodyLarge = body,
        bodyMedium = body,
        labelLarge = button,
        labelMedium = meta,
        bodySmall = meta,
    )

private fun KioskShapes.toMaterialShapes(): Shapes =
    Shapes(
        extraSmall = small,
        small = small,
        medium = medium,
        large = card,
        extraLarge = dock,
    )
