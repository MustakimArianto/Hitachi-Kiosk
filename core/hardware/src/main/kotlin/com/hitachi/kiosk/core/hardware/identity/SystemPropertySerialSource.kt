package com.hitachi.kiosk.core.hardware.identity

import android.annotation.SuppressLint
import java.lang.reflect.Method

/**
 * System properties that may hold the serial number, in the order they are tried (FR-002, ADR-0002). The first two
 * are exactly what the iMin device SDK reads in `SystemPropManager.getSn()` (IminDeviceLibrary 3.0.0): `ro.serialno`,
 * then `persist.sys.imin.sn` when it is empty. The others are candidates checked by the DEV-012 device spike.
 */
val SERIAL_PROPERTY_KEYS: List<String> =
    listOf(
        "ro.serialno",
        "persist.sys.imin.sn",
        "ro.boot.serialno",
        "ro.imin.serialno",
        "persist.sys.sn",
        "ro.sn",
    )

/**
 * Reads the system property [key] (FR-002). An app usually cannot read `ro.serialno` since Android 8 (SELinux), and
 * the property then reads as empty, which this source reports as [SerialReading.Blank].
 */
class SystemPropertySerialSource(
    val key: String,
    private val readProperty: (String) -> String? = SystemProperties::get,
) : SerialSource {
    override val name: String = "prop:$key"

    // A hidden API read through reflection can fail in many ways at runtime; a source must never crash activation.
    @Suppress("TooGenericExceptionCaught")
    override fun read(): SerialReading =
        try {
            readProperty(key).toSerialReading()
        } catch (e: ReflectiveOperationException) {
            SerialReading.Failed.of(e)
        } catch (e: RuntimeException) {
            SerialReading.Failed.of(e)
        }
}

/** `android.os.SystemProperties.get(String)` through reflection: the class is hidden from the public SDK. */
internal object SystemProperties {
    @SuppressLint("PrivateApi") // No public API exposes vendor properties such as persist.sys.imin.sn (ADR-0002).
    private fun getMethod(): Method = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)

    fun get(key: String): String? = getMethod().invoke(null, key) as String?
}
