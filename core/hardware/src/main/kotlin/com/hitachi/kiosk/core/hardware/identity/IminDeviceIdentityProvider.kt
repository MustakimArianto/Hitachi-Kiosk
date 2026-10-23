package com.hitachi.kiosk.core.hardware.identity

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.device.DeviceIdentityProvider
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.log.KioskLog
import javax.inject.Inject
import javax.inject.Singleton

/** Model and Android version reported with the serial number (FR-002, E1). */
data class DeviceBuild(
    val model: String?,
    val osVersion: String?,
)

/**
 * Reads the kiosk's serial number on the iMin device (FR-002, technical-design §9.1, ADR-0002).
 *
 * Every call tries [SerialSources] in order and takes the first plausible value ([isPlausibleSerial]). The value is
 * cached in [SerialCache]; when it differs from the cached one the live value wins and the change is logged. When no
 * source answers but a value is cached, the cached value is returned so the SN stays stable (FR-002 AC2). With
 * neither, the call fails with `DomainError.Hardware("identity", "serial_unavailable")`.
 */
@Singleton
class IminDeviceIdentityProvider
    @Inject
    constructor(
        private val sources: SerialSources,
        private val cache: SerialCache,
        private val build: DeviceBuild,
    ) : DeviceIdentityProvider {
        override suspend fun identify(): Outcome<DeviceIdentity> {
            val live = readLive()
            val cached = cache.read()
            val serial =
                when {
                    live != null -> {
                        remember(live, cached)
                        live.serial
                    }

                    cached != null -> {
                        KioskLog.w("serial_from_cache", "sources" to sources.all.size)
                        cached
                    }

                    else -> {
                        KioskLog.w("serial_unavailable", "sources" to sources.all.size)
                        return Outcome.Failure(DomainError.Hardware(device = DEVICE, reason = SERIAL_UNAVAILABLE))
                    }
                }
            return Outcome.Success(
                DeviceIdentity(serialNumber = serial, model = build.model, osVersion = build.osVersion),
            )
        }

        private fun readLive(): LiveSerial? {
            for (source in sources.all) {
                val reading = source.read()
                if (reading is SerialReading.Value && isPlausibleSerial(reading.value)) {
                    return LiveSerial(source = source.name, serial = reading.value.trim())
                }
                KioskLog.d("serial_source_skipped", "source" to source.name, "reading" to reading.kind())
            }
            return null
        }

        private suspend fun remember(
            live: LiveSerial,
            cached: String?,
        ) {
            when (cached) {
                live.serial -> return
                null -> KioskLog.i("serial_cached", "source" to live.source)
                else -> KioskLog.w("serial_mismatch", "source" to live.source)
            }
            cache.write(live.serial)
        }

        private data class LiveSerial(
            val source: String,
            val serial: String,
        )

        companion object {
            /** [DomainError.Hardware.device] for identity failures (architecture §9). */
            const val DEVICE = "identity"

            /** [DomainError.Hardware.reason] when no serial number can be read (FR-002 errors). */
            const val SERIAL_UNAVAILABLE = "serial_unavailable"
        }
    }

private fun SerialReading.kind(): String =
    when (this) {
        is SerialReading.Value -> "implausible"
        SerialReading.Blank -> "blank"
        is SerialReading.Unavailable -> reason
        is SerialReading.Failed -> type
    }
