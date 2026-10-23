package com.hitachi.kiosk.core.hardware.identity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * `Build.getSerial()` (FR-002, A-02). Since Android 10 it needs the caller to be device owner **and** hold
 * `READ_PHONE_STATE`, which is not in PRD §12.3 (R-3, ADR-0002). Without the permission this source reports
 * [SerialReading.Unavailable] and never calls the API; a [SecurityException] (not device owner) becomes
 * [SerialReading.Failed].
 */
class BuildSerialSource(
    private val permissionGranted: () -> Boolean,
    private val readSerial: () -> String?,
) : SerialSource {
    override val name: String = "Build.getSerial()"

    override fun read(): SerialReading {
        if (!permissionGranted()) return SerialReading.Unavailable(PERMISSION_MISSING)
        return try {
            readSerial().toSerialReading()
        } catch (e: SecurityException) {
            SerialReading.Failed.of(e)
        }
    }

    companion object {
        /** [SerialReading.Unavailable] reason when `READ_PHONE_STATE` is not granted. */
        const val PERMISSION_MISSING = "read_phone_state_not_granted"

        /** The source backed by the platform API. */
        fun forDevice(context: Context): BuildSerialSource =
            BuildSerialSource(
                permissionGranted = {
                    context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                        PackageManager.PERMISSION_GRANTED
                },
                readSerial = ::platformSerial,
            )
    }
}

// The permission is checked at runtime by BuildSerialSource and is declared only in the debug manifest until the PO
// decides on it (ADR-0002). The hardware identifier is the point: activation matches it against the dashboard (FR-002).
@SuppressLint("MissingPermission", "HardwareIds")
private fun platformSerial(): String? = Build.getSerial()
