# ADR-0002: Device serial number source

| | |
|---|---|
| Status | Proposed (waiting for the DEV-012 spike on the iMin device) |
| Date | 2026-10-23 |
| Related | FR-002, A-02, R-3, OQ-01, DEP-4, PRD §12.3, NFR-002, DEV-012, DEV-071, TC-005–TC-007, technical-design §9.1, §11.1, §16 |

## Context
- Activation sends the kiosk's serial number (SN) to E1, and the backend matches it against the device registered in the dashboard (FR-002, D-14). The SN must equal the one on the device label or in system settings (AC1) and must not change after a data clear, a reinstall or a reboot (AC2).
- The exact iMin model, Android version and SN API are still open (OQ-01). Nobody has run code on the unit yet.
- Since Android 10, `Build.getSerial()` works only for a device owner (or profile owner) that also holds `READ_PHONE_STATE`, and otherwise throws `SecurityException`. `READ_PHONE_STATE` is not in PRD §12.3 (R-3, A-02). The app becomes device owner only once `KioskDeviceAdminReceiver` exists (DEV-071).
- **The iMin device SDK resolves.** `com.github.iminsoftware:IminDeviceLibrary:3.0.0` (`libs.imin.device`) downloads from JitPack. Its SN API is `com.device.manager.sdk.tiramusu.biz.SystemPropManager.getSn()`. Its bytecode shows it is a thin wrapper:
  - on API ≥ 29 it returns the `ro.serialno` system property, or `persist.sys.imin.sn` when that is empty;
  - below API 29 it returns `Build.SERIAL`;
  - it reads properties by reflection on `android.os.SystemProperties.get(String)`.

  The AAR also brings Gson at runtime, `Log.d` calls and a manifest with `allowBackup="true"` and `targetSdkVersion 21`.
- Since Android 8, SELinux usually stops an ordinary app from reading `ro.serialno`, and the read returns an empty string. On iMin ROMs `persist.sys.imin.sn` is presumably readable, which would explain why the SDK falls back to it. The spike confirms this.
- Tech-design §16 also asks DEV-012 to check on the device that `screenOrientation="portrait"` is honoured. Android 16+ may ignore it on screens with sw ≥ 600 dp.

## Decision
**Proposed:** `IminDeviceIdentityProvider` (`:core:hardware`) reads the SN without the iMin SDK. It tries these sources in order and keeps the first plausible value (not blank, not `unknown`/`null`, not all zeros):

1. system property `ro.serialno` (the SDK's first read);
2. system property `persist.sys.imin.sn` (the SDK's fallback);
3. the candidate properties `ro.boot.serialno`, `ro.imin.serialno`, `persist.sys.sn`, `ro.sn`;
4. `Build.getSerial()`, only when `READ_PHONE_STATE` is granted. Otherwise the source reports `unavailable` and does not call the API. A `SecurityException` (not device owner) becomes a failed reading.

Properties are read by reflection on `SystemProperties.get` (`SystemPropertySerialSource`). The keys sit in one list, `SERIAL_PROPERTY_KEYS`, which the provider and the debug spike share. Each source is a `SerialSource` returning a `SerialReading` (`Value`, `Blank`, `Unavailable`, `Failed`). That way the chain, the validation and the caching are tested on the JVM.

**Cache** (technical-design §9.1): `SerialCache` stores the first value read under `serialNumber` in `:core:hardware`'s own `identity.preferences_pb`. Every `identify()` reads the sources again:
- if the live value differs from the cached one, the live value wins, the cache is updated and `serial_mismatch` is logged;
- if no source answers but a value is cached, the cached value is returned and `serial_from_cache` is logged (AC2);
- with neither, the call fails with `DomainError.Hardware("identity", "serial_unavailable")`.

**`READ_PHONE_STATE`** is declared only in `app/src/debug/AndroidManifest.xml`, so the spike can test step 4. The release manifest leaves it out. In release, step 4 always reports `unavailable`.

**Question for the PO** (A-02, R-3): if the spike shows that no system property gives the label SN, keeping the `Build.getSerial()` fallback needs `READ_PHONE_STATE` in the release manifest, granted by the device owner with `setPermissionGrantState` after DEV-071. Is adding that permission to PRD §12.3 approved?

## Alternatives considered
| Option | Why not |
|---|---|
| Depend on `IminDeviceLibrary` and call `SystemPropManager.getSn()` | It reads the same two properties by the same reflection. The dependency would add Gson, vendor `Log.d` output and a manifest whose `allowBackup="true"` has to be overridden in the merge. Nothing would be gained. Revisit if a future SDK version reads the SN through a privileged iMin service. |
| `Build.getSerial()` as the primary source | Needs device owner (DEV-071) and a permission the PRD does not list. It stays as the last fallback, pending the PO. |
| `Settings.Secure.ANDROID_ID` | Not the label SN. It changes after a factory reset and differs per signing key. It cannot meet AC1. |
| Ask staff to type the SN | Typing errors, and the backend could not trust the value. Out of scope for FR-002. |

## Spike procedure (DEV-012, run on the iMin device)
What can be checked now, without device owner: every system-property source, the `Build.getSerial()` path up to the `SecurityException`, the cache behaviour (TC-005, TC-006) and the orientation. What needs device owner: a successful `Build.getSerial()`. `adb shell dpm set-device-owner com.hitachi.kiosk/...` is not possible yet, because the app has no `DeviceAdminReceiver` until DEV-071. Repeat the step 4 checks after DEV-071.

1. Build and install the debug APK: `./gradlew :app:installDebug` (or `adb install -r app/build/outputs/apk/debug/app-debug.apk`).
2. Optional, for the `Build.getSerial()` path: `adb shell pm grant com.hitachi.kiosk android.permission.READ_PHONE_STATE`. This works on the debug build because only its manifest declares the permission. Without device owner, expect `<SecurityException: …>` on that row.
3. Start the app and tap the splash mark 5 times. Developer options opens.
4. Scroll to **Serial number diagnostics (DEV-012)**. Each row shows one source's raw result: a quoted value, `<blank>`, `<unavailable: …>` or `<ExceptionType: message>`. A value marked `(implausible)` was rejected. The section also shows the production provider's result, the cache before and after the read, `Build.SERIAL`, the device facts and the window facts. The **Serial number source** choice above it does not affect this section.
5. Get the results off the device: **Copy results**, **Share results**, or `adb logcat -d | grep serial_spike`, which logs one line per row.
6. **TC-005:** compare the provider's value with the label on the device and with *Settings › About › Status › Serial number*. Note which source rows hold the label SN.
7. **TC-006:**
   - Tap **Clear serial cache**. The section reads again, and *SerialCache.read() before* shows `<none>`. The SN must be the same.
   - `adb shell pm clear com.hitachi.kiosk`, then reopen Developer options. The SN must be the same.
   - `adb uninstall com.hitachi.kiosk`, then reinstall. The SN must be the same.
   - `adb reboot`, then reopen Developer options. The SN must be the same.
8. **Orientation (tech-design §16):** with the kiosk in its normal mounting, check that *Configuration.orientation* is `portrait`, *Activity.requestedOrientation* is `1` and the window is 1080 × 1920 px (or 1200 × 1920). Note *smallestScreenWidthDp*: at 600 or more, Android 16+ may ignore the portrait request. If the device can rotate, rotate it and read the section again.
9. Fill in the table below and send it back with a photo of the device label.

### Results
| Item | Value |
|---|---|
| Device model (label) / `Build.MODEL` / `Build.MANUFACTURER` | |
| `Build.DISPLAY` (ROM build) | |
| Android version / `SDK_INT` | |
| SN on the device label | |
| SN in system settings | |
| `IminDeviceIdentityProvider.identify()` | |
| `prop:ro.serialno` | |
| `prop:persist.sys.imin.sn` | |
| `prop:ro.boot.serialno` | |
| `prop:ro.imin.serialno` | |
| `prop:persist.sys.sn` | |
| `prop:ro.sn` | |
| `Build.getSerial()` without the permission | |
| `Build.getSerial()` with `pm grant` (not device owner) | |
| `Build.getSerial()` as device owner (after DEV-071) | |
| `Build.SERIAL` | |
| `isDeviceOwnerApp` | |
| TC-006: same SN after cache clear / `pm clear` / reinstall / reboot | |
| Orientation, `requestedOrientation`, window px, `smallestScreenWidthDp` | |
| Orientation after rotating the device (if it rotates) | |

### What the results decide
- The final source order: drop properties that are always blank on the device, and move the one that holds the label SN first.
- Whether the `Build.getSerial()` fallback and `READ_PHONE_STATE` stay. They are dropped if a property gives the label SN. Otherwise they go to the PO (question above), and technical-design §11.1 and PRD §12.3 are updated.
- Whether `libs.imin.device` stays in the version catalog. It is removed if this ADR is accepted as written.
- Whether the app must handle a landscape window: if so, write a new ADR (NFR-002, TR-PLT-05).
- Then set the status to **Accepted**.

## Consequences
- Positive: no vendor dependency for the SN. The same code and key list run in production and in the spike. Every source is testable on the JVM. The cache keeps the SN stable when the sources fail.
- Negative / cost: reflection on a hidden API. If a future Android release blocks `SystemProperties.get` for apps, every property row shows a `Failed` reading, and only the cache and `Build.getSerial()` remain. The spike cannot test `Build.getSerial()` as device owner until DEV-071.
- Follow-ups: run the spike and fill in **Results**. Update architecture §9, technical-design §9.1, §11.1 and §16, technology-stack (`libs.imin.device`) and the dev-plan DEV-012 checkbox. Get the PO's answer on `READ_PHONE_STATE`.
