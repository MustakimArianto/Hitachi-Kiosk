# Hitachi Kiosk — Technology Stack

| | |
|---|---|
| Document version | 1.1 (2026-10-23) — DEV-001 trial-build results, Espresso pin, release check on build-tools |
| Answers | *What technologies does the project use, at which version, and why?* |
| Requirements | [`product-requirement.md`](product-requirement.md) (FR-/NFR-/D-) and [`technical-requirement.md`](technical-requirement.md) (TR-) |
| Design | [`architecture.md`](architecture.md) (structure), [`technical-design.md`](technical-design.md) (implementation), [ADR-0001](adr/0001-architecture.md) (stack decision) |
| Versions verified | 2026-10-22, against Maven Central, Google Maven, the Gradle Plugin Portal, npm and the official release notes |

This document lists every technology the 1.0 app, its build and its tests use. The stack itself was decided in D-24 and [ADR-0001](adr/0001-architecture.md). This document pins the versions and records the purpose of each item. If it disagrees with the PRD, `architecture.md` or an ADR, those documents win and this one is corrected.

**Version policy.** Every version below is the latest **stable** release on 2026-10-22. No alpha, beta or RC versions are used. Once DEV-001 creates `gradle/libs.versions.toml`, that file is the single source of truth for versions (conventions §7), and this document is updated in the same PR whenever a version changes. Items marked **⚠ verify in DEV-001** are compatibility points that could not be confirmed without a trial build. Each one names a fallback.

---

## 1. Stack at a glance

| Layer | Choice | Decided by |
|---|---|---|
| Language | Kotlin 2.4 (JVM target 17) | D-24 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 as a foundation only, custom "Linen & Paprika" design system | D-24, D-02, PRD §11 |
| Architecture | Single Activity, MVVM + unidirectional state (`StateFlow<UiState>`), multi-module (`:app` + 5 `:core:*` + `:benchmark`) | ADR-0001, architecture §3–4 |
| Navigation | Navigation Compose with type-safe `@Serializable` routes | architecture §11 |
| DI | Hilt (Dagger) with KSP | ADR-0001, architecture §10 |
| Async | Kotlin coroutines + Flow | architecture §13.4 |
| Networking | Retrofit 3 + OkHttp 5 + kotlinx.serialization | ADR-0001, architecture §7 |
| Local data | Room (cache, pending order), Preferences DataStore (settings), Secure DataStore = DataStore + Tink AEAD (device token) | ADR-0001, architecture §6 |
| Background work | WorkManager (periodic catalog sync) | architecture §8 |
| Images | Coil 3 (memory + 200 MB disk cache) | NFR-013, A-37 |
| QR codes | ZXing core | DEV-048 |
| Hardware | iMin SDK (serial number, built-in printer), Android USB host and Bluetooth Classic SPP for ESC/POS printers, `CardTerminal` adapter (OQ-03) | D-18, FR-002, FR-018, FR-022 |
| Kiosk mode | Android Device Policy (device owner + lock task) | D-22, FR-029 |
| Logging | Timber behind the `KioskLog` facade, rotating local files | FR-031, D-23 |
| Build | Gradle 9.8 (Kotlin DSL) + AGP 9.4 + version catalog + `build-logic` convention plugins | DEV-001 |
| Quality | Android Lint, detekt, ktlint, Kover, Dependency Analysis plugin | NFR-011 |
| Tests | JUnit 4, Truth, Turbine, coroutines-test, kotest-property, MockWebServer 3, Robolectric, Roborazzi, Compose UI test, UI Automator, Macrobenchmark | testing-plan §1.3 |
| API contract | Markdown spec → generated OpenAPI 3.0.3, Redocly CLI lint, Prism mock server | D-06, DEV-006 |
| Verification gate | Local `./gradlew verify` (CI is deferred, D-31) | NFR-011 |

---

## 2. Toolchain

| Tool | Version | Purpose | Notes |
|---|---|---|---|
| JDK (Gradle daemon) | 21 (LTS) recommended, e.g. Eclipse Temurin 21; any JDK ≥ 17 works | Runs Gradle, AGP, KSP and the JVM tests | AGP 9.4 needs JDK ≥ 17. No Gradle Java toolchain is configured: every module compiles with the daemon JDK to JVM 17 bytecode (`jvmTarget = 17`, `sourceCompatibility = 17`). 0.1.0 was verified with JDK 17.0.20. |
| Gradle wrapper | **9.8.2** | Build system | AGP 9.4 needs Gradle ≥ 9.6.0. The wrapper is committed, with the distribution checksum in `gradle-wrapper.properties`. |
| Android Gradle Plugin | **9.4.1** | Android build | Supports API 37. AGP 9 compiles Kotlin with its built-in Kotlin support, so Android modules apply no separate `org.jetbrains.kotlin.android` plugin. Verified in DEV-001: built-in Kotlin works with KSP, Hilt, Compose, serialization, detekt, ktlint, Kover and Roborazzi. |
| Android SDK Build Tools | 36.0.0 (minimum required by AGP 9.4) | `aapt2`, `dexdump`, `apksigner` | `aapt2` and `dexdump` run the release-APK checks (TR-SEC-07), so the gate needs no command-line tools package. |
| Android SDK platform | API **37** (Android 17) | `compileSdk` and `targetSdk` | Latest stable at the 0.1.0 start (NFR-001). `minSdk 30` (D-04). |
| Android Studio | Rabbit 1 (2026.2.1) | IDE | Any version that supports AGP 9.4 is fine. |
| Kotlin | **2.4.21** | Language and compiler | Pin it explicitly. The Plugin Portal "latest" points to a 2.5 beta. |
| Kotlin Compose compiler plugin | **2.4.21** (`org.jetbrains.kotlin.plugin.compose`) | Compose compiler | Always the same version as Kotlin. |
| Kotlin serialization plugin | **2.4.21** (`org.jetbrains.kotlin.plugin.serialization`) | `@Serializable` DTOs and navigation routes | Same version as Kotlin. |
| KSP | **2.3.12** (`com.google.devtools.ksp`) | Annotation processing for Hilt and Room | KSP2 has its own version line and is no longer tied to the Kotlin version. Verified in DEV-001 with Kotlin 2.4.21 and Hilt 2.60.1. |
| Python | 3.9+ | `docs/api/tools/` (`gen_openapi.py`: spec → `openapi.yaml`; `extract_fixtures.py`: spec → test fixtures; shared parser `spec_examples.py`) | Standard library only. Run by `verify`. |
| Node.js | 22 LTS | Runs `npx` for Redocly and Prism | Contract tooling only. Not part of the app. |
| Git | 2.40+ | Version control, optional `pre-push` hook (§9) | Conventional Commits (conventions §9). |

---

## 3. Application libraries

The modules column follows [architecture §3](architecture.md#3-modules). "BOM" means the version is managed by the Compose BOM.

### 3.1 Kotlin and coroutines

| Library | Artifact | Version | Purpose | Modules |
|---|---|---|---|---|
| Kotlin stdlib | `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.21 | Language runtime. `kotlin.time.Instant`, `kotlin.time.Duration` and `kotlin.time.Clock` model time. | all |
| kotlinx.coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core`, `-android` | **1.11.0** | Suspend functions, `StateFlow`, polling loops, injected dispatchers | all (`-android` in Android modules) |
| kotlinx.serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | **1.12.0** | JSON DTOs (`ignoreUnknownKeys`, `explicitNulls = false`) and type-safe navigation routes | `:core:data`, `:app` |
| kotlinx-datetime | `org.jetbrains.kotlinx:kotlinx-datetime` | **0.8.0** | Time zones and local date/time for display (receipt timestamps, greeting by time of day, A-07). Since 0.7 the instant type is `kotlin.time.Instant` from the standard library. The old `kotlinx.datetime.Instant` is not used. | `:core:domain`, `:app` |

### 3.2 UI (Compose and AndroidX)

| Library | Artifact | Version | Purpose | Modules |
|---|---|---|---|---|
| Compose BOM | `androidx.compose:compose-bom` | **2026.09.00** | Aligns every Compose artifact (UI 1.12.1, Material 3 1.4.0) | `:app`, `:core:designsystem` |
| Compose UI, Foundation, Animation | `androidx.compose.ui:ui`, `androidx.compose.foundation:foundation`, `androidx.compose.animation:animation` | BOM | Layout, gestures, the motion inventory (PRD §11.4) | `:app`, `:core:designsystem` |
| Material 3 | `androidx.compose.material3:material3` | BOM (1.4.0) | **Foundation only**: ripple-free surfaces, text fields and `ModalBottomSheet`. Feature code reads `KioskTheme.*`, never `MaterialTheme.*` (architecture §12). | `:core:designsystem` |
| Compose tooling | `androidx.compose.ui:ui-tooling-preview` (impl), `ui-tooling` (debug only) | BOM | `@Preview` for every screen state (they feed the Roborazzi tests) | `:app`, `:core:designsystem` |
| Activity Compose | `androidx.activity:activity-compose` | **1.13.0** | `ComponentActivity.setContent`, edge-to-edge | `:app` |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | **2.11.0** | `collectAsStateWithLifecycle`, `ViewModel`, `viewModelScope` | `:app` |
| Navigation Compose | `androidx.navigation:navigation-compose` | **2.10.2** | `NavHost` with type-safe `@Serializable` routes (architecture §11) | `:app` |
| Core KTX | `androidx.core:core-ktx` | **1.19.1** | Kotlin extensions for platform APIs (`WindowCompat`, `ContextCompat`) | `:app`, `:core:hardware` |
| Core SplashScreen | `androidx.core:core-splashscreen` | **1.2.0** | Gives API 30 the same system-splash behaviour as API 31+, so the linen system splash hands straight to the Compose brand splash (SCR-01) without a flash | `:app` |
| Schibsted Grotesk | bundled `res/font` (TTF, weights 400–800) | from Google Fonts, SIL OFL 1.1 | The only typeface (PRD §11.1). Bundled, not a downloadable font, so it works offline. License kept in `licenses/`. | `:core:designsystem` |

### 3.3 Dependency injection

| Library | Artifact | Version | Purpose | Modules |
|---|---|---|---|---|
| Hilt | `com.google.dagger:hilt-android`, `hilt-android-compiler` (KSP), plugin `com.google.dagger.hilt.android` | **2.60.1** | Compile-time DI graph (architecture §10) | `:app`, `:core:data`, `:core:hardware` |
| Hilt Navigation Compose | `androidx.hilt:hilt-navigation-compose` | **1.4.0** | `hiltViewModel()` per navigation destination | `:app` |
| Hilt Work | `androidx.hilt:hilt-work`, `androidx.hilt:hilt-compiler` (KSP) | **1.4.0** | `@HiltWorker` for the catalog sync `CoroutineWorker` | `:core:data` |
| Hilt testing | `com.google.dagger:hilt-android-testing` | 2.60.1 | `@HiltAndroidTest`, `@TestInstallIn` with fakes | `:app` (androidTest) |

`:core:domain` has no DI library. Its classes use plain constructors (architecture §10).

### 3.4 Networking

| Library | Artifact | Version | Purpose | Modules |
|---|---|---|---|---|
| Retrofit | `com.squareup.retrofit2:retrofit` | **3.0.0** | `KioskApi` interface mirroring E1–E14 | `:core:data` |
| Retrofit kotlinx converter | `com.squareup.retrofit2:converter-kotlinx-serialization` | **3.0.0** | JSON (de)serialization through kotlinx.serialization | `:core:data` |
| OkHttp | `com.squareup.okhttp3:okhttp` | **5.5.1** | HTTP client: timeouts, interceptors, `CertificatePinner`, TLS | `:core:data`; `:app` (debug: the mock-server interceptor) |
| OkHttp logging interceptor | `com.squareup.okhttp3:logging-interceptor` | 5.5.1 | Request logging, **debug only**, behind the `KioskLog` redaction | `:core:data` (debug) |

### 3.5 Persistence and background work

| Library | Artifact | Version | Purpose | Modules |
|---|---|---|---|---|
| Room | `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (KSP), plugin `androidx.room` | **2.8.5** | Config, menu and `pending_order` tables. Atomic menu replacement. Exported schemas (`core/data/schemas/`) for migrations (architecture §6) | `:core:data` |
| DataStore Preferences | `androidx.datastore:datastore-preferences` | **1.3.0** | `device.preferences_pb`, `settings.preferences_pb`, PIN lockout state; the debug-only Developer options choices (`developer_options`) | `:core:data`, `:app` (debug) |
| DataStore + Tink | `androidx.datastore:datastore`, `androidx.datastore:datastore-tink` | **1.3.0** | Secure DataStore `secure.pb`: `AeadSerializer` encrypts each write with AES-256-GCM (ADR-0001, NFR-005) | `:core:data` |
| Tink | `com.google.crypto.tink:tink-android` | **1.24.0** | `Aead` primitive, `AndroidKeysetManager` with an `android-keystore://` master key | `:core:data` |
| WorkManager | `androidx.work:work-runtime-ktx` | **2.12.0** | 15-minute periodic catalog sync with a `CONNECTED` constraint (FR-028) | `:core:data` |

### 3.6 Images and QR

| Library | Artifact | Version | Purpose | Modules |
|---|---|---|---|---|
| Coil 3 | `io.coil-kt.coil3:coil-compose` | **3.6.3** | Async image loading (`AsyncImage`) placed in the `PhotoSlot` component's image slot, which shows the tone placeholder (NFR-013). `:core:designsystem` stays Compose-only (architecture §3). | `:app` |
| Coil network | `io.coil-kt.coil3:coil-network-okhttp` | **3.6.3** | Shares the app's OkHttp stack. 200 MB disk cache (A-37) and prefetch after sync (through the domain `ImagePrefetcher` port) | `:app` |
| ZXing core | `com.google.zxing:core` | **3.5.4** | Encodes `qrString` into a `BitMatrix` that Compose draws (FR-017, DEV-048). Also decodes the bitmap in TC-079. No `zxing-android-embedded`, because nothing is scanned with the camera. | `:app` (encode), tests (decode) |

### 3.7 Hardware and platform integrations

| Integration | Technology | Version | Purpose | Modules |
|---|---|---|---|---|
| iMin printer | `com.github.iminsoftware:IminPrinterLibrary` (JitPack) | V2.0.0.19 (newest tag) | Built-in printer driver and paper status (`IminPrinterDriver`, FR-022) | `:core:hardware` |
| iMin device | `com.github.iminsoftware:IminDeviceLibrary` (JitPack) | 3.0.0 (newest tag) | Serial number on iMin devices (`DeviceIdentityProvider`, FR-002) | `:core:hardware` |
| USB printers | Android USB host API (`android.hardware.usb.UsbManager`) | platform | ESC/POS over bulk transfer (NFR-016) | `:core:hardware` |
| Bluetooth printers | Android Bluetooth Classic (`BluetoothSocket`, SPP UUID `00001101-0000-1000-8000-00805F9B34FB`) | platform | ESC/POS to paired printers (A-21) | `:core:hardware` |
| ESC/POS encoding | In-house `EscPosEncoder` (pure Kotlin) | n/a | Text styles, alignment, raster logo, feed, cut. Tested against golden byte files (DEV-055). | `:core:hardware` |
| Card terminal (EDC) | `CardTerminal` adapter; vendor SDK or protocol per OQ-03 | TBD (ADR-0003, 0.5.0) | Sends the amount and reference to the EDC terminal and receives the result (FR-018) | `:core:hardware` |
| Kiosk lockdown | `DevicePolicyManager`, `DeviceAdminReceiver`, lock task | platform | Device owner, lock task, persistent home, keyguard and status bar disabled (FR-029) | `:app` |
| Sound effects | `android.media.SoundPool` | platform | Three short sounds (FR-030) | `:app` |
| Cryptography for the staff PIN | `javax.crypto` (`PBKDF2WithHmacSHA256`), `java.security.MessageDigest.isEqual` | JDK | Offline PIN check against the synced hash in constant time (FR-024, NFR-005) | `:core:domain` |

**iMin SDK sourcing.** The iMin libraries come from JitPack, not Maven Central. The JitPack repository is declared with a content filter limited to the `com.github.iminsoftware` group, so no other dependency can resolve from it. The iMin model and Android version are still open (OQ-01), and so is which library that model needs. The exact artifact and version are confirmed in the DEV-012 spike (ADR-0002) and the DEV-056 spike (ADR-0004). If the vendor ships an AAR instead, it is committed under `core/hardware/libs/` with its version in the file name and recorded in that ADR.

### 3.8 Logging and diagnostics

| Library | Artifact | Version | Purpose | Modules |
|---|---|---|---|---|
| Timber | `com.jakewharton.timber:timber` | **5.0.1** | Log backend behind the pure-Kotlin `KioskLog` facade (in `:core:domain`). A `RotatingFileTree` writes files capped at 10 MB / 7 days, and a `DebugTree` writes to Logcat in debug builds (FR-031, DEV-073). | `:app` |
| LeakCanary | `com.squareup.leakcanary:leakcanary-android` | **2.14** | Detects leaked Activities. `debugImplementation` only (NFR-004). | `:app` (debug) |
| JankStats | `androidx.metrics:metrics-performance` | **1.0.0** | Logs frame timing in debug builds and in the benchmark build variant during the scripted J1 journey (NFR-003, TC-143). Never active in release. | `:app`, `:benchmark` |
| Profile installer | `androidx.profileinstaller:profileinstaller` | **1.4.1** | Installs the baseline profile on sideloaded APKs (NFR-003, NFR-014) | `:app` |

---

## 4. Test libraries

Rules for test dependencies are in [testing-plan §1.3](testing-plan.md#13-test-levels-and-responsibilities). Fakes are preferred over mocks, so there is **no mocking library**.

| Library | Artifact | Version | Purpose | Where |
|---|---|---|---|---|
| JUnit 4 | `junit:junit` | **4.13.2** | Test runner for every JVM and instrumented test. Robolectric, the Compose UI test and Macrobenchmark all run on JUnit 4. | all |
| Truth | `com.google.truth:truth` | **1.4.5** | Assertions | all |
| kotlinx-coroutines-test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | `runTest`, virtual time for the polling, timers and timeouts | JVM tests |
| Turbine | `app.cash.turbine:turbine` | **1.2.1** | `Flow` and `StateFlow` assertions | JVM tests |
| kotest-property | `io.kotest:kotest-property` | **6.2.6** | Property tests with generators and shrinking (TC-061 pricing over 1,000 random carts; FR-032 AC8 exact-match coverage). Used standalone (`checkAll`) inside JUnit 4 tests; the Kotest runner is not used. Not used until the first property test (0.4.0); check there that it runs in a JUnit 4 test. Fallback: the latest 5.x release. | `:core:domain` |
| MockWebServer 3 | `com.squareup.okhttp3:mockwebserver3`, `mockwebserver3-junit4` | 5.5.1 | HTTP contract and fault-injection tests that serve the fixtures from `api-spec.md` (NFR-010, DEV-006). `mockwebserver3-junit4` provides the JUnit 4 rule. | `:core:data` |
| Room testing | `androidx.room:room-testing` | 2.8.5 | `MigrationTestHelper`, in-memory database | `:core:data` |
| WorkManager testing | `androidx.work:work-testing` | 2.12.0 | `WorkManagerTestInitHelper`, `TestDriver` (DEV-023) | `:core:data` |
| Robolectric | `org.robolectric:robolectric` | **4.17** | Android framework on the JVM for Room, DataStore and Roborazzi tests. DEV-001 result: 4.17 starts the API 37 image, but Compose draws nothing there in NATIVE graphics mode, so the screenshot modules pin `sdk=36` in `src/test/resources/robolectric.properties`. API 37 is covered by the instrumented tests. The unit-test JVM gets `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` (set in build-logic). | `:core:data`, `:app`, `:core:designsystem` |
| Roborazzi | `io.github.takahirom.roborazzi:roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`, plugin `io.github.takahirom.roborazzi` | **1.77.0** | JVM screenshot tests at 1080 × 1920 and 160/240/320 dpi, one per `S_<Screen>_<scenario>` board (TC-180) | `:app`, `:core:designsystem` |
| AndroidX Test | `androidx.test:core`, `androidx.test:runner` (**1.7.0**), `androidx.test.ext:junit` (**1.3.0**) | as listed | Instrumented test runner (a custom `HiltTestRunner`) | `:app` (androidTest) |
| Espresso | `androidx.test.espresso:espresso-core` | **3.7.0** | Not used directly: a dependency **constraint** in `kiosk.compose`. The Compose test rule drives Espresso, and the 3.5.0 it brings transitively calls `InputManager.getInstance()`, which API 37 removed, so every Compose test failed on API 37 without it. | test and androidTest of Compose modules |
| Compose UI test | `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest` (debug) | BOM (1.12.1) | Screen and journey tests with `enableAccessibilityChecks()` (NFR-007) | `:app` (androidTest) |
| UI Automator | `androidx.test.uiautomator:uiautomator` | **2.4.0** | System dialogs, lock task, process kill, 72 h soak script (TC-145) | `:app` (androidTest), `:benchmark` |
| Macrobenchmark | `androidx.benchmark:benchmark-macro-junit4`, plugin `androidx.baselineprofile` | **1.5.0** | Cold start, journey jank and the Budget Plan benchmark. Generates the baseline profile (DEV-031, DEV-082). | `:benchmark` |
| Shared fakes and fixtures | `:core:testing` (project module) | n/a | `FakeXxx` for every external interface; fixtures that are byte-identical to `api-spec.md` (NFR-012) | test source sets, `debug` source set |

---

## 5. Build, quality and contract tooling

| Tool | Artifact / plugin ID | Version | Purpose |
|---|---|---|---|
| Version catalog | `gradle/libs.versions.toml` | n/a | The only place versions are declared (conventions §7) |
| Convention plugins | `build-logic/` included build | n/a | `kiosk.android.application`, `kiosk.android.library`, `kiosk.jvm.library`, `kiosk.compose`, `kiosk.hilt`, `kiosk.quality`, `kiosk.verify` (root only): shared SDK levels, JVM target, lint, detekt, ktlint, Kover and dependency-analysis setup, and the `verify` gate (technical-design §2) |
| Android Lint | AGP built-in | AGP 9.4.1 | Includes `HardcodedText`, missing translation and accessibility checks (NFR-008). `warningsAsErrors` for the Compose and accessibility categories. |
| detekt | `io.gitlab.arturbosch.detekt` | **1.23.8** | Static analysis (NFR-011). detekt 2.0 is still alpha, so it is not used. Verified in DEV-001: 1.23.8 analyses the Kotlin 2.4 sources without type resolution (the plain `detekt` task). compose-rules reads `allowedCompositionLocals` but does not declare it, so `config/detekt/detekt.yml` excludes that key from config validation. |
| Compose rules for detekt | `io.nlopez.compose.rules:detekt` | **0.6.8** | Compose-specific checks (modifier order, unstable parameters, ViewModel injection) |
| ktlint | `org.jlleitschuh.gradle.ktlint` plugin **14.2.0** running ktlint **1.8.0** | as listed | Formatting (`ktlint_official` style plus `.editorconfig`) |
| Kover | `org.jetbrains.kotlinx.kover` | **0.9.11** | Coverage. `koverVerify` enforces `:core:domain` ≥ 90 % and `:core:data` ≥ 70 % line coverage (NFR-011) |
| Dependency Analysis | `com.autonomousapps.dependency-analysis` | **3.19.2** | Flags unused and wrongly scoped dependencies. Also catches module-boundary leaks such as `:core:hardware` → `:core:data` (architecture §3). Artifact families used through one alias (Compose, Hilt, AndroidX Test, …) are declared as bundles in the root `build.gradle.kts`. |
| Versions report | `com.github.ben-manes.versions` | **0.66.0** | `./gradlew dependencyUpdates` lists newer **stable** versions (non-stable versions filtered out) at each milestone start |
| OpenAPI generator | `docs/api/tools/gen_openapi.py` | in repo | `api-spec.md` → `openapi.yaml`. The yaml is never edited by hand. |
| Redocly CLI | `@redocly/cli` (npx) | **2.69.0** | `redocly lint docs/api/openapi.yaml` |
| Prism | `@stoplight/prism-cli` (npx) | **5.16.0** | Mock server on port 4010 (`api-spec.md` §4 scenarios) |
| `aapt2`, `dexdump` | Android SDK build-tools | 36.0.0 | `scripts/check-release-apk.sh`: verifies the release APK (every `classes*.dex`) and the R8 mapping have no `com.hitachi.kiosk.debug`, `Fake*` or `Switchable*` classes, no simulate-bar strings, no `debuggable`, `allowBackup="false"` and only allow-listed exported components (FR-019 AC3, TC-087, TC-148) |

**Verification gate.** There is no CI in 1.0 (D-31). Instead, `./gradlew verify` (defined in `build-logic`) runs the same checks a CI job would:
1. `lintDebug`, `detekt` and `ktlintCheck` in every module
2. `testDebugUnitTest` (Android modules), `test` (the JVM modules `:core:domain` and `:core:testing`), `verifyRoborazziDebug` and the Kover verification of `:core:domain` (`koverVerify`) and `:core:data` (`koverVerifyDebug`)
3. `buildHealth` (Dependency Analysis, applied to every module by `kiosk.quality`)
4. `assembleRelease` followed by `scripts/check-release-apk.sh` (`:app:checkReleaseApk`)
5. `scripts/check-openapi.sh`, which regenerates `openapi.yaml`, fails on a diff and runs Redocly lint
6. `checkApiFixtures`, which fails when the test fixtures differ from the `api-spec.md` examples

`connectedDebugAndroidTest` (instrumented tests) runs separately on a local emulator, at least before each merge to `main` and at every milestone exit. An optional `scripts/git-hooks/pre-push` hook runs `verify`. If CI is adopted later, it runs the same `verify` task; no checks change.

---

## 6. Explicitly excluded

| Not used | Why |
|---|---|
| Firebase (Crashlytics, Analytics, Performance), Sentry, Bugsnag, any ad SDK | D-23, NFR-006: no telemetry or third-party crash reporting in 1.0 |
| Google Play Services (location, ML Kit, Play Integrity, in-app updates) | Distributed by MDM/sideload (D-22). The kiosk may not have Play Services. |
| `androidx.security:security-crypto` (`EncryptedSharedPreferences`) | Deprecated. Replaced by DataStore + Tink (ADR-0001). |
| AppCompat, XML Views, Fragments | Compose-only UI (D-24). AppCompat is only needed for the future locale switch, which is out of 1.0 (D-29). |
| Mocking libraries (MockK, Mockito) | Scriptable fakes in `:core:testing` are the testing model (NFR-012). Adding one needs a PR note (testing-plan §1.3). |
| KAPT | Hilt and Room both use KSP. KAPT is slower and in maintenance. |
| Paging, Accompanist, Lottie | Not needed: menus are ≤ 200 items in memory, the APIs Accompanist provided are now in Compose itself, and all animation is native Compose per PRD §11.4. |
| `zxing-android-embedded`, CameraX | The kiosk shows QR codes and never scans them. |
| Third-party ESC/POS libraries | The command set needed is small, and a pure-Kotlin encoder can be golden-tested without hardware (architecture §9). |

---

## 7. Alternatives considered

Library-level alternatives that were checked against the current ecosystem (2026-10-22). Architecture-level alternatives (Koin, Ktor, MVI libraries, feature modules) are in [ADR-0001](adr/0001-architecture.md).

| Option | Status on 2026-10-22 | Decision |
|---|---|---|
| Navigation 3 (`androidx.navigation3` 1.2.0) | Stable | **Not adopted in 1.0.** architecture §11 already specifies Navigation Compose type-safe routes, nested graphs and an overlay host, and Navigation 2.10 supports all of them. Navigation 3 changes the model (the app owns the back stack). Adopting it needs a new ADR. |
| Room 3 (`androidx.room3` 3.0.3) | Stable | **Not adopted in 1.0.** It changes the package (`androidx.room3`), requires a `SQLiteDriver` and renames the annotations. Room 2.8 is still maintained and covers every need in architecture §6. Revisit after 1.0, with an ADR. |
| detekt 2.0 (`dev.detekt`) | Alpha | Rejected until it is stable (see §5). |
| LeakCanary 3 | Alpha | 2.14 is the stable line. |
| Paparazzi | Stable | Roborazzi was chosen in testing-plan §1.3: it runs on Robolectric, which the data-layer tests already use. |
| Kotest framework (runner, assertions) | Stable | Only `kotest-property` is used, so JUnit 4 stays the single runner. |
| `org.jmailen.kotlinter` (ktlint wrapper) | Stable | Equivalent. `jlleitschuh` ktlint-gradle is more widely used with AGP. |
| Ktor client | Stable | Rejected in ADR-0001 (MockWebServer, pinning and interceptor maturity). |

---

## 8. `libs.versions.toml` starting point

DEV-001 creates the catalog from this excerpt. Only the `[versions]` section is shown. Library and plugin aliases follow `<group>-<name>` naming.

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.21"
ksp = "2.3.12"
coroutines = "1.11.0"
serialization = "1.12.0"
datetime = "0.8.0"
compose-bom = "2026.09.00"
activity = "1.13.0"
lifecycle = "2.11.0"
navigation = "2.10.2"
core-ktx = "1.19.1"
core-splashscreen = "1.2.0"
hilt = "2.60.1"
androidx-hilt = "1.4.0"
retrofit = "3.0.0"
okhttp = "5.5.1"
room = "2.8.5"
datastore = "1.3.0"
tink = "1.24.0"
work = "2.12.0"
coil = "3.6.3"
zxing = "3.5.4"
timber = "5.0.1"
leakcanary = "2.14"
jankstats = "1.0.0"
profileinstaller = "1.4.1"
imin-printer = "V2.0.0.19"   # confirm in DEV-056 / ADR-0004
imin-device = "3.0.0"        # confirm in DEV-012 / ADR-0002
junit4 = "4.13.2"
truth = "1.4.5"
turbine = "1.2.1"
kotest = "6.2.6"
robolectric = "4.17"
roborazzi = "1.77.0"
androidx-test = "1.7.0"
androidx-test-ext = "1.3.0"
uiautomator = "2.4.0"
benchmark = "1.5.0"
detekt = "1.23.8"
compose-rules = "0.6.8"
ktlint-gradle = "14.2.0"
ktlint = "1.8.0"
kover = "0.9.11"
dependency-analysis = "3.19.2"
versions-plugin = "0.66.0"
```

---

## 9. Keeping the stack current

- At each milestone start, run `./gradlew dependencyUpdates` and take stable patch and minor updates in a `chore(deps)` PR, after `verify` and the instrumented tests pass.
- A major-version change (for example Room 3, Navigation 3, detekt 2, a new AGP major) needs an ADR.
- Each new library needs a line in the PR description saying why (conventions §7), a row in this document, and a check against the excluded list (§6).
- After 0.9.0, only patch updates and security fixes go in until 1.0.0 ships.
