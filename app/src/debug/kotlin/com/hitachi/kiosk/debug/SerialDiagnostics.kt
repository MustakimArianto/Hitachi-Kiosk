package com.hitachi.kiosk.debug

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.hardware.identity.IminDeviceIdentityProvider
import com.hitachi.kiosk.core.hardware.identity.SerialCache
import com.hitachi.kiosk.core.hardware.identity.SerialReading
import com.hitachi.kiosk.core.hardware.identity.SerialSources
import com.hitachi.kiosk.core.hardware.identity.isPlausibleSerial
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * One diagnostics row. [key] is the API, property or class that was asked, and [value] its raw answer: both are
 * technical identifiers for the ADR-0002 results table, not UI copy.
 */
@Immutable
data class DiagnosticRow(
    val key: String,
    val value: String,
)

/** The DEV-012 serial-number spike results (ADR-0002), in display order. */
@Immutable
data class SerialDiagnostics(
    val rows: List<DiagnosticRow>,
) {
    /** Plain text for the clipboard, the share sheet and logcat. */
    fun report(): String = rows.joinToString(separator = "\n") { "${it.key}: ${it.value}" }
}

/** Window facts from the Activity, to check that `screenOrientation="portrait"` is honoured (technical-design §16). */
data class WindowFacts(
    val orientation: String,
    val requestedOrientation: Int,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
)

/**
 * Debug only: runs every serial-number source of the production chain, then the production provider itself, and
 * collects the device facts ADR-0002 asks for (DEV-012, TC-005, TC-006). Each row is also logged as `serial_spike`,
 * so `adb logcat` can capture the results.
 */
class SerialDiagnosticsCollector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sources: SerialSources,
        private val cache: SerialCache,
        private val provider: IminDeviceIdentityProvider,
    ) {
        /** Reads everything once. The cache is read first, so it shows what was stored before this read. */
        suspend fun collect(window: WindowFacts): SerialDiagnostics {
            val cached = cache.read()
            val readings = sources.all.map { it.name to it.read() }
            val identity = provider.identify()
            val rows =
                buildList {
                    add(DiagnosticRow("IminDeviceIdentityProvider.identify()", identity.describe()))
                    add(DiagnosticRow("SerialCache.read() before", cached?.quoted() ?: NONE))
                    add(DiagnosticRow("SerialCache.read() after", cache.read()?.quoted() ?: NONE))
                    readings.forEach { (name, reading) -> add(DiagnosticRow(name, reading.describe())) }
                    add(DiagnosticRow("Build.SERIAL (deprecated)", deprecatedBuildSerial().quoted()))
                    addAll(deviceRows())
                    addAll(window.rows())
                }
            rows.forEach { KioskLog.i("serial_spike", "key" to it.key, "value" to it.value) }
            return SerialDiagnostics(rows)
        }

        /** Empties the serial cache (TC-006). */
        suspend fun clearCache() {
            cache.clear()
            KioskLog.i("serial_spike_cache_cleared")
        }

        private fun deviceRows(): List<DiagnosticRow> {
            val policy = context.getSystemService(DevicePolicyManager::class.java)
            val phoneState = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            return listOf(
                DiagnosticRow("Build.MANUFACTURER", Build.MANUFACTURER),
                DiagnosticRow("Build.BRAND", Build.BRAND),
                DiagnosticRow("Build.MODEL", Build.MODEL),
                DiagnosticRow("Build.DEVICE", Build.DEVICE),
                DiagnosticRow("Build.DISPLAY", Build.DISPLAY),
                DiagnosticRow("Build.VERSION.RELEASE", Build.VERSION.RELEASE),
                DiagnosticRow("Build.VERSION.SDK_INT", Build.VERSION.SDK_INT.toString()),
                DiagnosticRow(
                    "DevicePolicyManager.isDeviceOwnerApp",
                    (policy?.isDeviceOwnerApp(context.packageName) == true).toString(),
                ),
                DiagnosticRow(
                    "READ_PHONE_STATE granted",
                    (phoneState == PackageManager.PERMISSION_GRANTED).toString(),
                ),
            )
        }

        private companion object {
            const val NONE = "<none>"
        }
    }

private fun WindowFacts.rows(): List<DiagnosticRow> =
    listOf(
        DiagnosticRow("Configuration.orientation", orientation),
        DiagnosticRow("Activity.requestedOrientation", requestedOrientation.toString()),
        DiagnosticRow("Configuration.screenWidthDp x screenHeightDp", "$widthDp x $heightDp"),
        DiagnosticRow("Configuration.smallestScreenWidthDp", smallestWidthDp.toString()),
        DiagnosticRow("WindowMetrics.bounds px", "$widthPx x $heightPx"),
        DiagnosticRow("Configuration.densityDpi", densityDpi.toString()),
    )

private fun Outcome<DeviceIdentity>.describe(): String =
    when (this) {
        is Outcome.Success -> "${value.serialNumber.quoted()} model=${value.model} os=${value.osVersion}"
        is Outcome.Failure -> error.toString()
    }

private fun SerialReading.describe(): String =
    when (this) {
        is SerialReading.Value -> if (isPlausibleSerial(value)) value.quoted() else "${value.quoted()} (implausible)"
        SerialReading.Blank -> "<blank>"
        is SerialReading.Unavailable -> "<unavailable: $reason>"
        is SerialReading.Failed -> "<$type: $message>"
    }

private fun String.quoted(): String = "\"$this\""

// Diagnostics only: targetSdk >= 26 makes this "unknown", which is the point of showing it next to the real sources.
@Suppress("DEPRECATION")
@SuppressLint("HardwareIds")
private fun deprecatedBuildSerial(): String = Build.SERIAL
