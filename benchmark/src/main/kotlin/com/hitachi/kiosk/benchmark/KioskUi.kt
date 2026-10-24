package com.hitachi.kiosk.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The app under test: the `benchmarkRelease` / `nonMinifiedRelease` build of `:app`. */
internal const val TARGET_PACKAGE = "com.hitachi.kiosk"

/**
 * Everything the benchmarks know about the screens: UI Automator selectors built from the design copy
 * (the `.dc.html` boards in `docs/ui-design`) and the accessibility descriptions, kept in this one file so they can be adjusted when
 * a screen changes. Each [Target] tries its selectors in order: the accessibility description first, the visible
 * text second.
 */
internal object KioskUi {
    /** One element to wait for and tap, found by the first selector that matches. */
    class Target(
        val name: String,
        vararg val selectors: BySelector,
    )

    // Setup (0.2.0 screens): Login.dc.html and Merchants.dc.html.
    val logIn = Target("Sign in 'Log in' button", By.text("Log in"))
    val urbanGrill =
        Target("'Urban Grill' merchant card", By.desc("Urban Grill, open"), By.textStartsWith("Urban Grill"))

    // J1 journey (0.3.0 screens).
    val idle = Target("Idle 'Tap to order'", By.desc("Tap to order"), By.text("Tap to order"))
    val dineIn = Target("Order type 'Dine in'", By.text("Dine in"), By.desc("Dine in"))
    val burgers = Target("Menu category pill 'Burgers'", By.text("Burgers"), By.desc("Burgers"))
    val smokyDoubleBurger =
        Target(
            "Menu product 'Smoky Double Burger'",
            By.desc("Choose options for Smoky Double Burger"),
            By.text("Smoky Double Burger"),
        )
    val itemDetail = Target("Item detail screen", By.desc("Increase quantity"))
    val menu = Target("Menu screen", By.desc("Categories"))
    val backToMenu = Target("Item detail 'Menu' back pill", By.text("Menu"), By.desc("Menu"))

    private const val SETUP_TIMEOUT_MS = 60_000L
    private const val SCREEN_TIMEOUT_MS = 15_000L
    private const val STEP_TIMEOUT_MS = 5_000L
    private const val POLL_MS = 100L

    /**
     * From a cold launch to Idle. On a fresh install the fakes sign in (E1) and bind Urban Grill (E3, E5, E6); the
     * binding is stored, so later launches go straight from Splash to Idle.
     */
    fun MacrobenchmarkScope.launchToIdle() {
        startActivityAndWait()
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < SETUP_TIMEOUT_MS) {
            // Sign in and Merchants animate out after a tap, so a button that is already gone is not an error here.
            when (val screen = device.firstOf(listOf(idle, logIn, urbanGrill), SCREEN_TIMEOUT_MS)) {
                idle -> return
                null -> break
                else -> screen.selectors.firstNotNullOfOrNull(device::findObject)?.click()
            }
            device.waitForIdle()
        }
        error("DEV-031: Idle not reached from Splash/Sign in/Merchants. ${hint(idle)}")
    }

    /** J1 up to the item: Idle → Order type "Dine in" → Menu → "Burgers" → "Smoky Double Burger" → back to Menu. */
    fun MacrobenchmarkScope.idleToItemAndBack() {
        device.tap(idle)
        device.tap(dineIn)
        device.tap(burgers)
        device.tap(smokyDoubleBurger)
        // Wait for the item screen and for the Menu to leave: during the transition both are on screen.
        device.require(itemDetail)
        device.awaitGone(menu)
        device.tap(backToMenu)
        device.require(menu)
    }

    private fun UiDevice.tap(target: Target) {
        require(target).click()
        waitForIdle()
    }

    private fun UiDevice.awaitGone(target: Target) {
        target.selectors.forEach { selector -> wait(Until.gone(selector), STEP_TIMEOUT_MS) }
    }

    private fun UiDevice.require(target: Target): UiObject2 =
        find(target, STEP_TIMEOUT_MS) ?: error("DEV-031: ${target.name} not found. ${hint(target)}")

    private fun UiDevice.find(
        target: Target,
        timeoutMs: Long,
    ): UiObject2? =
        firstOf(listOf(target), timeoutMs)?.let { found ->
            found.selectors.firstNotNullOfOrNull(::findObject)
        }

    /** The first of [targets] on screen within [timeoutMs], or null. */
    private fun UiDevice.firstOf(
        targets: List<Target>,
        timeoutMs: Long,
    ): Target? {
        val start = System.currentTimeMillis()
        do {
            targets.firstOrNull { target -> target.selectors.any(::hasObject) }?.let { return it }
            Thread.sleep(POLL_MS)
        } while (System.currentTimeMillis() - start < timeoutMs)
        return null
    }

    private fun hint(target: Target): String =
        "Tried ${target.selectors.joinToString()}. Update the selectors in benchmark/src/main/kotlin/" +
            "com/hitachi/kiosk/benchmark/KioskUi.kt if the screen copy changed."
}
