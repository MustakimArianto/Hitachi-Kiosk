package com.hitachi.kiosk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.devtools.DeveloperMenu
import com.hitachi.kiosk.devtools.DeveloperMenuTapCounter
import com.hitachi.kiosk.navigation.KioskRoot
import com.hitachi.kiosk.ui.rememberSystemReduceMotion
import dagger.hilt.android.AndroidEntryPoint
import java.util.Optional
import javax.inject.Inject

/** The single Activity (TR-PLT-05): portrait, edge-to-edge, linen system splash handing over to SCR-01. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var developerMenu: Optional<DeveloperMenu>

    private val developerTaps = DeveloperMenuTapCounter()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KioskTheme(reduceMotion = rememberSystemReduceMotion()) {
                KioskRoot(onBrandMarkClick = ::onBrandMarkClick)
            }
        }
    }

    private fun onBrandMarkClick() {
        if (developerTaps.onTap()) developerMenu.ifPresent { it.open(this) }
    }
}
