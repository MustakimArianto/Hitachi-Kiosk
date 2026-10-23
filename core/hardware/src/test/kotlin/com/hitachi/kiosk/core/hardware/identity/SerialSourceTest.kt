package com.hitachi.kiosk.core.hardware.identity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SerialSourceTest {
    @Test
    fun `plausible serials are non-blank, not placeholders and not all zeros`() {
        assertThat(isPlausibleSerial("IMN24K0003118")).isTrue()
        assertThat(isPlausibleSerial("  IMN24K0003118 ")).isTrue()
        listOf("", "   ", "unknown", "UNKNOWN", "null", "0", "0000000000").forEach {
            assertThat(isPlausibleSerial(it)).isFalse()
        }
    }

    @Test
    fun `a system property source reports the raw value, blank or the failure`() {
        val properties = mapOf("persist.sys.imin.sn" to "IMN24K0003118", "ro.serialno" to "")

        assertThat(SystemPropertySerialSource("persist.sys.imin.sn", properties::get).read())
            .isEqualTo(SerialReading.Value("IMN24K0003118"))
        assertThat(SystemPropertySerialSource("ro.serialno", properties::get).read()).isEqualTo(SerialReading.Blank)
        assertThat(SystemPropertySerialSource("ro.sn", properties::get).read()).isEqualTo(SerialReading.Blank)
        assertThat(SystemPropertySerialSource("ro.sn") { throw ClassNotFoundException("hidden") }.read())
            .isEqualTo(SerialReading.Failed("ClassNotFoundException", "hidden"))
        assertThat(SystemPropertySerialSource("ro.sn") { throw SecurityException("denied") }.read())
            .isEqualTo(SerialReading.Failed("SecurityException", "denied"))
        assertThat(SystemPropertySerialSource("ro.sn").name).isEqualTo("prop:ro.sn")
    }

    @Test
    fun `the reflective reader fails softly where the hidden class is missing`() {
        // The JVM test classpath has only the public android.jar, so android.os.SystemProperties is absent.
        assertThat(SystemPropertySerialSource("ro.serialno").read()).isInstanceOf(SerialReading.Failed::class.java)
    }

    @Test
    fun `the candidate keys start with the ones the iMin SDK reads`() {
        assertThat(SERIAL_PROPERTY_KEYS.take(2)).containsExactly("ro.serialno", "persist.sys.imin.sn").inOrder()
        assertThat(SERIAL_PROPERTY_KEYS).containsNoDuplicates()
    }

    @Test
    fun `Build getSerial is not called without READ_PHONE_STATE`() {
        var calls = 0
        val source = BuildSerialSource(permissionGranted = { false }, readSerial = { "IMN24K0003118".also { calls++ } })

        assertThat(source.read()).isEqualTo(SerialReading.Unavailable(BuildSerialSource.PERMISSION_MISSING))
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `Build getSerial reports the value, blank or a SecurityException when not device owner`() {
        assertThat(BuildSerialSource({ true }, { "IMN24K0003118" }).read())
            .isEqualTo(SerialReading.Value("IMN24K0003118"))
        assertThat(BuildSerialSource({ true }, { null }).read()).isEqualTo(SerialReading.Blank)
        assertThat(BuildSerialSource({ true }, { throw SecurityException("not owner") }).read())
            .isEqualTo(SerialReading.Failed("SecurityException", "not owner"))
    }
}
