# Hitachi Kiosk — Technical Design

| | |
|---|---|
| Document version | 1.5 (2026-10-24) — updated with the 0.5.0 implementation (order submission, the payment machine, QR, Checkout, process-death recovery, the connectivity engine, Room version 2, the debug card terminal and demo images); 1.4: 0.4.0 |
| Answers | *How will the system satisfy the requirements?* |
| Requirements | [`product-requirement.md`](product-requirement.md) (what the product does), [`technical-requirement.md`](technical-requirement.md) (TR-, what it must satisfy) |
| Structure | [`architecture.md`](architecture.md) and [`adr/`](adr/): modules, layering, state machines, persistence map |
| Stack | [`technology-stack.md`](technology-stack.md): libraries and versions |
| Contract | [`api/api-spec.md`](api/api-spec.md) (E1–E14) |

## 0. Scope and relationship to `architecture.md`

`architecture.md` defines the **structure**: modules and their rules, layering, the `OrderSession` and `PaymentStateMachine` diagrams, the persistence map, and the networking, hardware and navigation principles. This document does not repeat it. It adds the **implementation layer** a developer needs to build each DEV task: build setup, concrete classes, data schemas, call sequences, algorithms, configuration values, and how each technical requirement (TR-) is met.

Precedence: the PRD wins over everything. `technical-requirement.md` wins over this document. If this document contradicts `architecture.md` or an Accepted ADR, those win, and this document is corrected. A design choice that changes the architecture needs an ADR first.

Decisions taken in this document carry an ID `TD-nn`. They are collected in §15.

---

## 1. Repository layout

```
hitachi-kiosk/
├── app/                        :app
│   └── src/{main,debug,release,test,androidTest}/
├── core/
│   ├── designsystem/           :core:designsystem
│   ├── domain/                 :core:domain   (JVM)
│   ├── data/                   :core:data     (+ schemas/ for Room)
│   ├── hardware/               :core:hardware (+ libs/ only if iMin ships an AAR)
│   └── testing/                :core:testing  (+ src/main/resources/fixtures/)
├── benchmark/                  :benchmark     (from 1.0.0, DEV-031)
├── build-logic/                included build: convention plugins
├── gradle/libs.versions.toml   versions (technology-stack.md §8)
├── scripts/                    check-release-apk.sh, check-openapi.sh, git-hooks/pre-push
├── licenses/                   Schibsted Grotesk OFL, third-party notices
└── docs/                       this documentation set
```

---

## 2. Build system

### 2.1 Gradle setup
- Gradle 9.8 with the Kotlin DSL, `settings.gradle.kts` with `includeBuild("build-logic")`, and typesafe project accessors.
- Repositories are declared once in `settings.gradle.kts` (`dependencyResolutionManagement`, `FAIL_ON_PROJECT_REPOS`): `google()`, `mavenCentral()`, and JitPack with `content { includeGroup("com.github.iminsoftware") }` (TR-PLT-08).
- `gradle.properties`: configuration cache on, build cache on, `org.gradle.parallel=true`, `android.nonTransitiveRClass=true`, and the `kiosk.version.*` properties (§2.4).

### 2.2 Convention plugins (`build-logic`) — TD-01

| Plugin | Applies | Configures |
|---|---|---|
| `kiosk.android.application` | `com.android.application` | `compileSdk 37`, `targetSdk 37`, `minSdk 30`, JVM target 17, versioning (§2.4), environments (§2.3), R8 for release, packaging excludes |
| `kiosk.android.library` | `com.android.library` | SDK levels, JVM target 17, `consumerProguardFiles`, no `BuildConfig` unless asked for |
| `kiosk.jvm.library` | `org.jetbrains.kotlin.jvm` | Bytecode 17, compiled with the Gradle daemon JDK (no toolchain). Used by `:core:domain`, so `android.*` cannot compile there (TR-MNT-01), and by `:core:testing`, whose fakes depend on the domain only. |
| `kiosk.compose` | `org.jetbrains.kotlin.plugin.compose` | Compose BOM, compiler reports and metrics in `build/compose-reports` behind `-Pkiosk.composeReports=true`, stability config `config/compose/stability.conf`, and an Espresso 3.7.0 constraint for the Compose test rule (API 37, technology-stack §4) |
| `kiosk.hilt` | `com.google.devtools.ksp`, `com.google.dagger.hilt.android` | Hilt runtime and KSP compiler |
| `kiosk.quality` | detekt, ktlint, Kover, Dependency Analysis | Rule sets (`config/detekt/detekt.yml`, `.editorconfig`, `config/lint/lint.xml`; no baselines), Kover exclusions for generated and Hilt wiring code, and the per-module `kioskCheck` task. Modules call `kioskCoverage(minLinePercent)`: `:core:domain` 90, `:core:data` 70 (TR-MNT-02) |
| `kiosk.verify` | root project only | The `verify` task (§2.6), `checkOpenApi`, `extractApiFixtures`, `checkApiFixtures` and the aggregated Kover report |

Android modules use AGP 9's built-in Kotlin compilation, so no separate Kotlin Android plugin is applied (technology-stack §2).

### 2.3 Build types and environments — TD-02
Architecture §7 requires a flavor-less `dev | staging | prod` environment. It is implemented as a Gradle property, not product flavors:

| Build | Default `kiosk.env` | Override | Cleartext | Pinning |
|---|---|---|---|---|
| `debug` | `dev` | `-Pkiosk.env=staging`; at runtime, Developer options can switch to the mock host or a custom URL | Mock host only (`10.0.2.2`, `localhost`) through `src/debug/res/xml/network_security_config.xml` | Off for `dev` and the mock host; on when built with `kiosk.env=staging` |
| `release` | `prod` | `-Pkiosk.env=staging` for 0.9.0 staging builds | Never | On, staging or prod pin set |

`kiosk.env` sets `BuildConfig.API_BASE_URL` (from `kiosk.baseUrl.<env>` in `gradle.properties`), `BuildConfig.KIOSK_ENV` and `BuildConfig.CERT_PINS`, read from `kiosk.pins.<env>` or `KIOSK_PINS_<ENV>` (TR-SEC-03). Debug builds also get `BuildConfig.MOCK_BASE_URL` for Developer options. A release build with `kiosk.env=dev` fails before it builds (`checkReleaseEnvironment`). The pins are not known yet (A-36, DEV-094); until then a build without pins has no pinning, and DEV-094 makes a missing pin set fail a `staging` or `prod` build.

### 2.4 Versioning (TR-DST-02)
`kiosk.version.major/minor/patch` in `gradle.properties` produce `versionName = "M.m.p"` and `versionCode = M*10000 + m*100 + p` in `kiosk.android.application`. They are bumped in one place (conventions §10).

### 2.5 Release build (TR-SEC-07, TR-DST-01)
- `isMinifyEnabled = true`, `isShrinkResources = true`, R8 full mode (the AGP 9 default), `isDebuggable = false`.
- Signing from the environment variables `KIOSK_KEYSTORE_PATH`, `KIOSK_KEYSTORE_PASSWORD`, `KIOSK_KEY_ALIAS`, `KIOSK_KEY_PASSWORD`, with a fallback to `local.properties`. v2 and v3 signing on, v1 off (`minSdk 30` does not need it).
- Keep rules: kotlinx.serialization (generated serializers for DTOs and routes), Retrofit interfaces, Tink (shipped consumer rules), iMin SDK (per ADR-0004), and `@Keep` on the `DeviceAdminReceiver`.
- `scripts/check-release-apk.sh <apk-dir> <r8-mapping> <sdk-dir> <build-tools-version> <stamp>` (run by `:app:checkReleaseApk`) uses the build-tools `dexdump` and `aapt2` to check:
  - no class under `com.hitachi.kiosk.debug` and no `com.hitachi.kiosk…Fake*` or `Switchable*` class, in every `classes*.dex` and in the R8 mapping (R8 renames classes, the mapping keeps the original names);
  - no "Prototype only" string;
  - `debuggable` is absent or false, and `allowBackup="false"`;
  - the exported components match the allow-list in §11.1.

### 2.6 Verification gate (TR-MNT-06) — TD-03
No CI in 1.0 (D-31). `build-logic` registers a root `verify` task that depends on:

1. every module's `kioskCheck`: `lintDebug` (Android), `detekt`, `ktlintCheck`, `testDebugUnitTest` (Android) or `test` (JVM), `verifyRoborazziDebug` (modules with screenshot tests) and the Kover verification where `kioskCoverage` is set
2. `buildHealth` (Dependency Analysis, every module)
3. `:app:checkReleaseApk`, which builds `assembleRelease` and runs the script above
4. `checkOpenApi` (an `Exec` task running `scripts/check-openapi.sh`: regenerate `openapi.yaml` into a temporary file, `diff` it with `docs/api/openapi.yaml`, then `npx @redocly/cli@2.69.0 lint`)
5. `checkApiFixtures` (re-extracts the fixtures from `api-spec.md`, §14, and fails on a diff)

`connectedDebugAndroidTest` stays a separate step, because it needs a running emulator (Kiosk-30 and Kiosk-34 AVDs, testing-plan §2.3; `scripts/create-avds.sh` creates them). `scripts/git-hooks/pre-push` runs `./gradlew verify`. It is installed with `git config core.hooksPath scripts/git-hooks`, documented in the README. If CI is adopted later, a job calls the same tasks.

---

## 3. Module internals

The package layout follows conventions §1. The table names the main types per module so DEV tasks can be estimated and reviewed against it.

| Module | Main types |
|---|---|
| `:core:domain` | `backend/BackendApi` (E1–E14 in domain types), `payment/PaymentGateway` (E9–E12, delegates to `BackendApi`), `model/` (`Rupiah`, `Product`, `ModifierGroup`, `Combo`, `Category`, `MerchantConfig`, `KioskSettings` + `visiblePaymentMethods`, `Totals`, `PendingOrder`, `PaymentStatus`), `model/Catalog` (config + menu), `model/MenuLookup` (menu queries, `needsChoice`), `pricing/MenuPricing` (item and combo unit prices, 0.3.0), `pricing/PricingEngine` (+ `PricingRules`, `PromoHint`, 0.4.0), `cart/Cart` (+ `CartLine`, `CartItem`, `LineChoice`, `CartAddResult`, `CartAddition`, `CartAddAllResult`), `cart/LineDescription` (`LineWording`, `describe`), `cart/ProductCustomization`, `cart/ComboCustomization`, `cart/Upsell`, `planner/BudgetPlanner` (+ `planner/BudgetPlan`: `PlanRequest`, `PlanOptions`, `PlanResult`, `BudgetPlan`, `PlanPick`), `order/OrderSession` (+ `OrderPhase`, `OrderState`, `PlanTag`, `ReviewIssues`, `Submission`), `order/OrderSessionHolder` (interface) + `DefaultOrderSessionHolder`, `order/SubmitOrder` (+ `SubmitResult`), `order/CartRepricer`, `order/RecoverPendingOrder` (+ `RecoveryOutcome`, `RecoveredPaidOrder`), `payment/PaymentStateMachine` (+ `PaymentAttempt`, `PaymentPolicy`, `PaymentUiState`, `PaymentExit`), `auth/StaffPinVerifier`, `format/IdrFormatter`, `repository/*Repository` (interfaces: `ActivationRepository`, `MerchantRepository` + `BindResult`, `CatalogSyncRepository`, `CatalogRepository`, `SettingsRepository`, `OrderRepository`), `setup/ActivateKiosk`, `model/DeviceState`, `device/` (`CardTerminal`, `PrinterManager`, `PrinterDriver`, `DeviceIdentityProvider`, `ConnectivityMonitor`), `time/Clock`, `time/ServerClockOffset`, `time/DayPart` (A-07), `id/IdGenerator`, `image/ImagePrefetcher`, `error/DomainError`, `Outcome`, `retryWithBackoff`, `log/KioskLog` (facade + `Redactor`, pure Kotlin) |
| `:core:data` | `api/KioskApi` (internal), `api/interceptor/*`, `api/ApiErrorMapper`, `api/ApiConfig`, `api/DeviceCredentialsProvider` (+ `InMemoryDeviceCredentialsStore`), `api/UnauthorizedHandler`, `backend/DefaultBackendApi`, `backend/BackendPaymentGateway`, `di/NetworkModule`, `di/DataStoreModule`, `di/RepositoryModule`, `di/DatabaseModule`, `dto/*Dto`, `mapper/*` (including `CatalogEntityMappers` and the config `toDto` of TD-13), `db/KioskDatabase`, `db/Migrations`, `db/dao/CatalogDao`, `db/dao/PendingOrderDao`, `db/entity/*Entity`, `prefs/DeviceStore`, `prefs/SettingsStore`, `prefs/SecureTokenStore` (implements `prefs/TokenStore`, internal to the module), `prefs/KeystoreAead`, `repository/DeviceSession`, `repository/SessionInvalidator`, `repository/Default*Repository` (including `DefaultCatalogRepository`, the Room cache with `CatalogStore` / `CatalogCache`, `DefaultSettingsRepository` over `prefs/SettingsStore`, and `DefaultOrderRepository` over `repository/PendingOrderStore`), `sync/DefaultCatalogSyncRepository`, `sync/CatalogSyncWorker`, `sync/CatalogSyncScheduler` (+ `WorkManagerCatalogSyncScheduler`), `net/ConnectivityEngine`, `net/DefaultConnectivityMonitor`, `api/NetworkErrorSignal` |
| `:core:hardware` | `identity/IminDeviceIdentityProvider`, `identity/SerialSource` (`SystemPropertySerialSource`, `BuildSerialSource`), `identity/SerialCache` (own DataStore file), `di/HardwareModule`, `printer/DefaultPrinterManager`, `printer/escpos/EscPosEncoder`, `printer/render/ReceiptRenderer`, `printer/render/KitchenTicketRenderer`, `printer/driver/{Imin,UsbEscPos,BtEscPos}Driver`, `terminal/UnavailableCardTerminal` (release until the adapter), then `terminal/*CardTerminal` (per ADR-0003) |
| `:core:designsystem` | `theme/KioskTheme` (+ `KioskColors`, `KioskType`, `KioskShapes`, `KioskSpacing`), `unit/DesignUnit`, `motion/KioskMotion`, `LocalReduceMotion`, `LocalLargeText`, `LocalAccent`, `component/*` (PRD §11.3; from 0.3.0 also `OfflineBanner`, `KioskToast`, `RollingText`; from 0.5.0 `QrCard`, `ExpiryRing`, `QrCenterMark`). `PhotoSlot` draws the tone placeholder and takes the image as a composable slot, so this module depends on Compose only (architecture §3); `:app` fills the slot with Coil's `AsyncImage` (`ui/MenuPhoto`). |
| `:core:testing` | `fake/Fake*` (one per domain interface, including `FakeCatalogRepository`, `FakeCatalogSyncRepository`, `FakeConnectivityMonitor`, `FakeImagePrefetcher`, `FakeSettingsRepository`, `FakeOrderRepository`) built on `fake/Script`, `fake/FakeScenario` (api-spec §4), `fake/TestClock`, `fake/VirtualTimeClock` (a `Clock` that follows the virtual time of a test dispatcher), `fixture/Fixtures` (loads `fixtures/*.json`), `fixture/SampleData` (the same examples as domain objects), `rule/MainDispatcherRule` |
| `:app` | `KioskApp`, `MainActivity`, `navigation/` (`Routes.kt`, `KioskNavHost`, `KioskRoot`, `StartupRouter`, `SessionViewModel`, `BrandingViewModel`), `sync/CatalogSyncCoordinator`, `recovery/PendingOrderRecoveryCoordinator`, `ui/` (`MenuPhoto`, `parseHexColor`, `rememberSystemReduceMotion`, `ResourceLineWording`), `ui/qr/` (`QrCode`, `QrEncoder`), `kiosk/` (`KioskModeController`, `KioskDeviceAdminReceiver`, `BootReceiver`, `BrightnessController`), `inactivity/` (`InactivityController`, `InactivityViewModel`, `StillThereOverlay`), `sound/SoundPlayer`, `log/TimberLogSink`, `log/RotatingFileTree`, `image/CoilImagePrefetcher`, `image/kioskImageLoader`, `devtools/` (`DeveloperMenu`, `BindingOverrides`, `CheckoutDebugTools`), `di/*Module` (including `BackendModule`, `HardwareBindingsModule`, `SetupModule`, `SessionModule`, `OrderModule`, `PaymentModule`, `RecoveryModule`), `feature/*`; `src/debug` (package `com.hitachi.kiosk.debug`): `DeveloperOptions*`, `DeveloperSettingsStore`, `SwitchableBackendApi`, `SwitchableDeviceIdentityProvider`, `SwitchableCardTerminal`, `CheckoutSimulator`, `DemoImages`, `SerialDiagnostics`, `MockServerInterceptor` |

`Clock` and `IdGenerator` are domain interfaces (TR-INT-10, TR-INT-11). `SystemClock` returns `kotlin.time.Instant` and a monotonic `elapsedRealtime` for UI timers. `UuidIdGenerator` produces the `Idempotency-Key` values (UUID v4), so tests can script keys.

---

## 4. Application startup and dependency injection

### 4.1 `KioskApp` (`@HiltAndroidApp`)
In `onCreate`, in this order, all off the main thread except the first two:
1. `KioskLog.install(TimberLogSink)`: plants the `RotatingFileTree`, plus a `DebugTree` in debug builds (§12).
2. Debug only: `StrictMode` thread and VM policies with `detectAll()` and `penaltyLog()` (TR-REL-05).
3. WorkManager is initialised on demand through `Configuration.Provider` with `HiltWorkerFactory`. The default initializer is removed in the manifest.
4. Coil: `SingletonImageLoader.Factory` builds the `ImageLoader` (`image/kioskImageLoader`) with a plain `OkHttpClient` (image URLs are public CDN links, so the backend client and its device token are never used), a 200 MB disk cache in `cacheDir/images`, and a memory cache of 20 % of app memory (TR-DAT-05, TC-157).
5. `CatalogSyncCoordinator.start()` schedules the periodic sync and starts the reconnect and order-end triggers in `@ApplicationScope` (§7.2).
6. `PendingOrderRecoveryCoordinator.start()` starts its triggers, loads the stored device state itself and, for a bound kiosk, begins the pending-order recovery (§8.6). It does not wait for Splash (0.5.0).

### 4.2 Startup routing (FR-001)
`MainActivity` installs the AndroidX `SplashScreen`, with an exit animation set to *none* and a linen background, so the system splash hands straight to the Compose `SplashRoute` (SCR-01). `SplashViewModel` calls `StartupRouter.route()` once, which runs in parallel:
- a `delay(3_900)` minimum display time;
- the local load, `ActivationRepository.load()` (token, device record and binding);
- for a bound kiosk, the request to start the pending-order recovery (`StartupRecovery.begin()` → `PendingOrderRecoveryCoordinator.recoverAtStartup()`, DEV-052). It returns at once, and in the same process it does nothing because `KioskApp.start()` already asked (§8.6, architecture §5.3).

When the stored state is bound, a background E5/E6 sync starts in `@ApplicationScope` but does not block. Routing waits for the minimum time **and** the local load, with a hard cap of 10 s on the load (`withTimeoutOrNull`; on timeout it routes with the state known so far), then replaces the back stack: `NotActivated` → SCR-02, `Activated` → SCR-03, `Bound` → SCR-04 (TR-PRF-02, TR-PRF-03). Any failure to read the token is treated as "not activated" (TR-REL-06).

**Device state (DEV-014).** `DeviceSession` (`:core:data`, `@Singleton`) owns the encrypted token (`TokenStore`), the device record (`DeviceStore`), the in-memory `DeviceCredentials` that `AuthInterceptor` reads without I/O, and `StateFlow<DeviceState>`. Writes are serialized by a `Mutex`. A token without a device record (or the reverse) is wiped. `SessionInvalidator` (the `UnauthorizedHandler`, a `suspend` call made by `ApiErrorMapper` before it returns `Unauthorized`) wipes under `NonCancellable`. `SessionViewModel` at the app root turns the drop to `NotActivated` into a back-stack replace to SCR-02 from any screen except Splash and Sign in.

**Setup flow (DEV-013–DEV-017).** `ActivateKiosk` (domain) runs E1 through `ActivationRepository`; when E1 returns a `boundMerchantId` that is live and open, it binds it at once, so a dashboard pre-assignment skips SCR-03 (api-spec E1). `MerchantRepository.bind` runs E3 (one idempotency key per attempt, retried), then `CatalogSyncRepository.sync` (E5 + E6, each retried), and stores the binding with the config's `mode` only when both succeed, so a restart never opens Idle without a menu (FR-004). Log out calls E4 once, then wipes locally whatever it answered.

### 4.3 Hilt modules
As in architecture §10. One addition: `CoroutinesModule` provides the dispatchers and `@ApplicationScope CoroutineScope = CoroutineScope(SupervisorJob() + @DefaultDispatcher)`.

**Debug overrides (TD-11).** Hilt cannot replace a production binding outside tests, so `src/main` declares three optional bindings with `@BindsOptionalOf` in `BackendModule`: `BindingOverrides`, `DeveloperMenu` and, since 0.5.0, `CheckoutDebugTools` (the Checkout simulate bar). `BackendModule` and `HardwareBindingsModule` bind each port to its real implementation, wrapped by `BindingOverrides` when present. Only `src/debug` (`DeveloperOptionsModule`) provides them, so the release bindings are exactly those of `src/main` and release has no `src/release` override. The debug override wraps `BackendApi` in `SwitchableBackendApi`, which sends each call to the real client or to a `FakeBackendApi` with the selected `FakeScenario`; `PaymentGateway` delegates to `BackendApi`, so it follows the same switch. Since 0.2.0 it also wraps `DeviceIdentityProvider` in `SwitchableDeviceIdentityProvider` (real device, or the FR-002 fakes: success, unavailable, slow 5 s; the fake is the debug default so emulators work). Since 0.5.0 `BindingOverrides.cardTerminal(real)` (default: keep the real one) wraps `CardTerminal` in `SwitchableCardTerminal`, which sends each sale, following the Developer options "Card terminal" section at the time of the sale, to the release terminal, to a manual fake that waits for the Checkout simulate bar (the default), or to a `FakeCardTerminal` that answers by itself (approve, decline, cancelled at the terminal, error, no answer). The simulate bar (`CheckoutSimulator`, "Prototype only · simulate the result") forces results against the fake backend and the manual terminal and draws nothing against a real backend (`SwitchableBackendApi.fakeOrNull()`). Later milestones add `PrinterManager` in the same way. Instrumented tests replace `DeveloperOptionsModule` with a `@TestInstallIn` module whose `BindingOverrides` return scriptable fakes. Switching happens at domain interfaces only, so no DTO or `KioskApi` type leaves `:core:data` (architecture §3). The "mock server" mode is not a fake: it keeps the real client, and a debug-only interceptor (contributed to the `@NetworkInterceptors` set) redirects it to Prism and adds the scenario's `Prefer` header.

**Demo images (debug only).** `DemoImages` gives the sample "Urban Grill" catalog photos, so Idle, Menu, Item and Combo can be checked with pictures. 25 WebP files (20 product photos, the fries-and-cola photo of "Make it a meal", 3 idle slides and the logo; the hero reuses a product photo) live in `app/src/debug/assets/demo/`, made from the full-size originals by `scripts/demo-images/make_demo_images.py` (a 2:1 canvas with the subject at 82 % of the height for the products, a 9:16 canvas for the idle slides; why and how: `ui-design/photo-guidelines.md` §4), and Coil loads them as `file:///android_asset/demo/…`. `SwitchableBackendApi` applies it to the config and menu of the fake backend and of the Prism mock, replacing only a missing URL or the `cdn.example` placeholder of api-spec §4; a real backend URL is never replaced. Products r3 and r4 (Ayam Geprek Bowl, Black Pepper Beef Bowl) have no photo and show the placeholder. The full-size originals stay untracked in `Assets/` (gitignored). Release builds contain none of it (`com.hitachi.kiosk.debug` is rejected by the release check).

---

## 5. Networking implementation

### 5.1 Client (TR-NET-03, TR-NET-05, TR-SEC-02, TR-SEC-03)
`NetworkModule` builds a single `OkHttpClient`:
- `connectTimeout(10 s)`, `readTimeout(15 s)`, `writeTimeout(15 s)`, `callTimeout(30 s)`.
- Interceptors, in the architecture §7 order:
  1. `IdempotencyKeyInterceptor`: copies the key from the `@Tag key: RequestIdempotencyKey` request tag (a data-module wrapper of the domain `IdempotencyKey`) into the header. It never generates a key.
  2. `AuthInterceptor`: adds `Authorization` on every call except the `@Unauthenticated` ones (E1, E14), and `X-Device-Id` on every call once activated, E14 included (api-spec §2.2). It reads a `DeviceCredentialsProvider`: the `InMemoryDeviceCredentialsStore` that `DeviceSession` fills from the Secure DataStore `TokenStore` and the `DeviceStore` (DEV-014).
  3. `HeadersInterceptor`: `X-App-Version`, `Accept-Language: en`.
  4. Extra interceptors from the `@NetworkInterceptors` multibound set (empty in release; the debug build adds the mock-server redirect).
  5. Debug only (from DEV-073): `HttpLoggingInterceptor(level = BODY)` through a `KioskLog` logger that applies the same `Redactor` rules (§12) to headers and JSON bodies: `Authorization`, `deviceToken`, `currentPin`, `currentPinProof`, `newPin` dropped; `qrString`, `reference`, `terminalRef`, `approvalCode` and `paymentId` truncated.
- `connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))`: TLS 1.2 and 1.3 only (TR-SEC-02). The debug mock host adds `CLEARTEXT`.
- `CertificatePinner` for the API host when `BuildConfig.CERT_PINS` is not empty (`staging`/`prod`, TR-SEC-03; see §2.3).
- The `OkHttpClient` and `Retrofit` are qualified `@KioskBackend`, so another client (for example Coil's) can never pick up the one that sends the device token. `KioskApi`, the DTOs and the `Json` instance are `internal` to `:core:data`.
- A `401` is handled in `ApiErrorMapper`, not with an OkHttp `Authenticator`. api-spec v1 defines no refresh endpoint (E1 returns `tokenExpiresAt: null`), so the app has no refresh flow. FR-003 says the token is "refreshed per §10", but §10 defines no mechanism; this is listed as an open item in technical-requirement §14. `SessionInvalidator` clears the token and binding; the device state becomes `NotActivated` and the root routes to SCR-02 (§4.2, TR-NET-06).

Retrofit: `Retrofit.Builder().addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))` with `Json { ignoreUnknownKeys = true; explicitNulls = false }` (TR-NET-08, architecture §7). Unknown enum values are not coerced silently: mappers handle them explicitly (for example a product with no `role` defaults to MAIN and logs a warning). Every `KioskApi` method is `suspend` and returns `Response<T>`, so headers (`ETag`, `Date`) and status codes are readable.

### 5.2 Error mapping (TR-NET-07) — TD-04
`ApiErrorMapper` turns a transport outcome into `Outcome<T>`. The `DomainError` cases are the ones in architecture §13.3.

| Transport outcome | `DomainError` | Notes |
|---|---|---|
| `IOException`, `SocketTimeoutException`, no route | `Network` | Retried per §5.3 first |
| HTTP 401 `TOKEN_INVALID` | `Unauthorized` | Also triggers `SessionInvalidator` |
| HTTP 409 `PRICE_CHANGED` | `PriceChanged(details)` | `details` carries `menuVersion`, `configVersion` and `serverTotals` (api-spec E7) |
| HTTP 409 `ITEM_UNAVAILABLE` | `ItemUnavailable(ids)` | |
| Other HTTP 409 (`MERCHANT_NOT_LIVE` on E3, `MERCHANT_CLOSED` on E7, `ORDER_ALREADY_PAID` on E8, `ORDER_NOT_AWAITING_PAYMENT` on E9, `PAYMENT_NOT_CANCELLABLE` on E11, `PIN_INCORRECT` on E13) | `Conflict` | Each is unique within its endpoint, so the caller knows which one it is |
| HTTP 403 `DEVICE_DISABLED`, 404 `DEVICE_NOT_REGISTERED`, 404 `NOT_FOUND`, 400 `VALIDATION_ERROR` (bad request body only), 422, 429, 5xx | `Server(code)` | `code` = the envelope `code` string, or `HTTP_<status>` when there is no envelope. ViewModels map the code to the PRD copy. |
| Body that fails to parse | `Unknown` | Logged with the endpoint ID, never the body |

### 5.3 Retry and idempotency per endpoint (TR-NET-04, TR-REL-03)
Retries are written in the repositories with `retryWithBackoff(delays = [1 s, 2 s, 4 s], retryOn = { it is Network || it is Server(5xx/429) })`, so they are visible and testable (architecture §7).

| Endpoint | Idempotency-Key | Retried | Key lifetime |
|---|---|---|---|
| E1 activate | — | No (user taps "Try again") | — |
| E2, E5, E6, E14 (GET) | — | Yes | — |
| E10 payment status (GET) | — | Not by `BackendPaymentGateway`: the state machine polls every 2 s and treats a failed poll as "no answer yet" (§8.4). The recovery reads it with the 1/2/4 s backoff (§8.6). | — |
| E3 bind | Yes | Yes | Per bind attempt |
| E4 unbind | — | No | — |
| E7 create order | **Required** | Yes, in `DefaultOrderRepository` | Held in `OrderState.submission` (the body and its key), set by `OrderSession.submit(...)` as Reviewing becomes Submitting, and reused for every retry of the **same body**, also after a network failure returned to Reviewing. A new key whenever the body changes (cart edit, re-pricing after `PRICE_CHANGED`) or after "Back to order" cancelled the previous order, which forgets it (BR-08, TC-086). On success the key is saved in `pending_order` with the `orderId`. |
| E8 cancel order | Yes | Yes, in `DefaultOrderRepository` | One key from `IdGenerator` per cancel call, reused by its retries. The pending order is cleared only when the answer is `CANCELLED`; `409 ORDER_ALREADY_PAID` is returned as it is (not mapped to a network error, not retried) and keeps the row, so the caller goes to Complete. |
| E9 start payment | **Required** | Yes, in `BackendPaymentGateway` | One key per payment attempt. "Try again" and "switch method" create a new key for the same `orderId`. |
| E11 cancel payment | Yes | Yes, in `BackendPaymentGateway` | Per payment and cancel reason: the machine repeats the key for a retry of the same reason and makes a new one when the reason changes (another cancel is another request). The recovery makes its own key per run. |
| E12 terminal result | — | No automatic retry (it carries no key). If it fails, the state machine goes to `Checking` (the earlier `Unknown`) and keeps polling E10 for 60 s; ADR-0003 decides whether E12 needs a key (api-spec first). | — (only if ADR-0003 uses E12) |
| E13 change PIN | Yes | No (user-driven) | Per attempt |

### 5.4 ETag and server time
- `CatalogSyncRepository` stores `ETag` values beside the Room data and sends them as `If-None-Match`. A `304` keeps the cache (TR-NET-09).
- `ServerClockOffset` (`:core:domain`) is `offset = serverTime − clock.now()`. `DefaultBackendApi` reads the `Date` header of the **E9 answer only** (RFC 1123, whole seconds) and puts it in `StartedPayment.Qris.serverTime`; it is `null` when the header is missing or malformed. The machine measures the offset when the answer arrives, from `serverTime`, or from `createdAt` when there is no header. The QR expiry instant on the device is `expiresAt − offset`, turned into a deadline on the monotonic clock, so the countdown ticks on it and a later wall-clock change does not matter (TR-INT-03). The header has whole seconds, so the countdown can be off by up to 1 s (FR-017 AC2 allows ± 1 s).

---

## 6. Persistence implementation

### 6.1 Room `KioskDatabase` (version 1 at 0.3.0, version 2 since 0.5.0; frozen at 1.0.0)

| Table | Key columns | Written by |
|---|---|---|
| `config` | `merchantId` (PK), `etag`, `json` (the E5 body as wire JSON, `MerchantConfigDto`), `fetchedAtMillis` | Sync |
| `menu_meta` | `merchantId` (PK), `menuVersion`, `etag`, `fetchedAtMillis`, the combos category name and sort, `sizeLargeUpcharge` | Sync |
| `category` | `id` (PK), `position`, `sort`, `nameEn`, `nameId`, `tone` | Sync |
| `product` | `id` (PK), `position`, `categoryId`, `nameEn`, `nameId`, `descriptionEn`, `descriptionId`, `price`, `kcal`, `badgeType`, `badgeLabelEn`, `badgeLabelId`, `tagsEn`, `tagsId`, `imageUrl`, `available`, `role`, `kidFriendly`, `servesAdults`, `shareable`, `plannerWeight` | Sync |
| `product_modifier_group` | (`productId`, `groupId`) PK, `position` | Sync |
| `modifier_group` | `id` (PK), `position`, names, `type`, `required`, `min`, `max` | Sync |
| `modifier_option` | (`groupId`, `id`) PK (option IDs are unique per group only), `position`, names, descriptions, notes, `priceDelta`, `isDefault`, `imageUrl`, `defaultLevel` | Sync |
| `modifier_level` | (`groupId`, `optionId`, `level`) PK, `position`, `priceDelta` | Sync |
| `combo` | `id` (PK), `position`, names, short names, includes, `price`, `separatePrice`, `serves`, `imageUrl`, `tone`, `available` | Sync |
| `combo_slot_option` | (`slot` SIDE/DRINK, `id`) PK, `position`, names, `upcharge`, `imageUrl`, `available` | Sync |
| `featured` | `position` (PK), `productId` | Sync |
| `pending_order` (0.5.0) | `orderId` (PK), `orderNumber`, `paymentId?`, `method?` (the `PaymentMethod` name), `idempotencyKey`, `totalsJson` (the order totals as `TotalsDto` JSON), `createdAt` (the E7 `createdAt`, ISO-8601 text, so no precision is lost) | `OrderRepository` (architecture §5.3): `create` replaces it, `recordPayment` adds the payment, an E8 answer `CANCELLED` and `clearPending` delete it |

Every list keeps the E6 order in a `position` column. `{en, id}` texts are two nullable columns. The idle promo images are part of the config row, so they need no table of their own. The menu tables hold one merchant's catalog at a time.

- **Atomic replace (TR-DAT-03):** `CatalogDao.replaceAll(rows)` is one `@Transaction`. It deletes every catalog table and inserts the `config` row, `menu_meta` and all menu tables, so E5 and E6 land together (architecture §8). An insert failure rolls back and keeps the old catalog (TR-REL-07); `CatalogDao.readAll()` reads all tables in one transaction and returns nothing for an empty or half-written cache. `CatalogDaoTest` checks both on an in-memory database.
- Config is stored as one JSON column because it is read whole and never queried by field. This avoids a schema migration every time a config field is added. The column holds the E5 wire form: `MerchantConfig.toDto()` writes it and the same `MerchantConfigDto.toDomain()` mapper as E5 reads it, so the cache and the network parse config identically (TD-13).
- **Read side:** `DefaultCatalogRepository` (implements the domain `CatalogRepository`, plus the data-internal `CatalogStore` the sync writes through and `CatalogCache` the session wipe clears) loads Room once and keeps the catalog in memory, so a category switch never touches the disk (TR-PRF-04). A cache that cannot be read (SQLite error, JSON or enum that no longer parses) is served as empty and logged (`catalog_cache_unreadable`), never a crash.
- **Wipe:** `DeviceSession.wipe()` (Log out, HTTP 401) also empties the catalog cache and forgets the pending order, so a logged-out kiosk keeps no merchant data (FR-004 AC3). The pending order goes through `PendingOrderCache`, a narrow interface that `PendingOrderStore` extends, so the wipe does not depend on the backend (the 401 handler would make that a cycle).
- **At most one pending order.** `PendingOrderStore.replace` keeps one row. `DefaultOrderRepository.create` refuses a new order with `Conflict("PENDING_PAYMENT_UNRESOLVED")`, and sends no E7, while the stored order has a `paymentId` and another key (§8.6). A row that no longer parses reads as "none" and is logged, never a crash; a save that fails makes `create` report `DomainError.Unknown`, so Pay again resends the same body with the same key.
- Money columns are `INTEGER` (`Long`). There is no `REAL` column anywhere (TR-DAT-01).
- `exportSchema = true` with the `androidx.room` Gradle plugin `schemaDirectory("$projectDir/schemas")`; `core/data/schemas/…/1.json` and `2.json` are committed. Version 2 (0.5.0) adds `pending_order` with `MIGRATION_1_2` (`CREATE TABLE`, the `createSql` of `2.json`; the cache tables are untouched, so an in-place upgrade keeps the cache and needs no download). The blanket `fallbackToDestructiveMigration(dropAllTables = true)` of 0.3.0 is gone: `DatabaseModule` registers `KIOSK_MIGRATIONS` through `addKioskMigrations()` and there is no destructive fallback, not even for the cache tables, so every later schema change needs a real `Migration` (TR-DAT-04, TD-21). `KioskDatabaseMigrationTest` runs `MigrationTestHelper` on Robolectric against the exported schemas (`core/data/build.gradle.kts` adds `schemas/` as unit-test assets).

### 6.2 DataStore files

| File | Type | Keys |
|---|---|---|
| `device.preferences_pb` | Preferences | `deviceId`, `kioskCode`, `kioskName`, `merchantId`, `merchantName`, `kioskMode` (FR-005; written with the binding) |
| `identity.preferences_pb` (owned by `:core:hardware`) | Preferences | `serialNumber` (cache, §9.1) |
| `settings.preferences_pb` | Preferences | `orderTypes`, `idleTimeoutSec`, `brightness`, `soundEnabled`, `reduceMotion`, `largeText`, `upsellEnabled`, `paymentMethods`, `qrExpiryMin`, `roundingStep`, `printers` (JSON list of `PrinterConfig`), `receiptToggles`, `receiptFooter`, `paperWidth`, `density`, `autoSync`, `lastSyncAt` |
| `pin_lockout.preferences_pb` | Preferences | `failedAttempts`, `lockedUntilElapsed`, `lockedUntilWall` (TR-SEC-05) |
| `secure.pb` | Typed DataStore `SecureStore { deviceToken }` | Encrypted (§6.3) |

Each store is exposed as a `Flow` of a domain model (`KioskSettings`, etc.) or read through a repository. From 0.4.0 `SettingsStore`, behind `DefaultSettingsRepository` (the domain `SettingsRepository`), reads `upsellEnabled`, `idleTimeoutSec`, `paymentMethods` and `roundingStep` as `Flow<KioskSettings>`, with the FR-025/FR-026 defaults for a missing or unusable value (Suggest add-ons on, 60 s, every method, the config's rounding step); its `update` is what the 0.7.0 staff screens write through. The other keys arrive with their screens. Writes are `suspend`; DataStore does its I/O on its own background scope. DataStore allows one instance per file and process, so each file's instance is a process-level singleton (a `preferencesDataStore` delegate, or a holder object for `secure.pb`), not a per-Hilt-component object: instrumented tests rebuild the Hilt graph for every test in one process.

### 6.3 Secure DataStore (TR-SEC-01) — TD-05
- Master key: `AndroidKeysetManager.Builder().withSharedPref(context, "kiosk_keyset", "kiosk_keyset_prefs").withKeyTemplate(KeyTemplates.get("AES256_GCM")).withMasterKeyUri("android-keystore://kiosk_master_key").build()` (`prefs/KeystoreAead`). Tink keeps the **wrapped** keyset in that small preferences file. It is built once, at first token access, inside the serializer (`EncryptedSecureStoreSerializer`), so it runs on DataStore's own I/O thread. The token itself is never in SharedPreferences. This is Tink's standard keyset storage and holds only wrapped key material, so it does not reintroduce what ADR-0001 rejected (`EncryptedSharedPreferences` holding the data, read synchronously on the main thread).
- Store: `DataStoreFactory.create(serializer = AeadSerializer(aead, SecureStoreSerializer, associatedData = "kiosk.secure.v1".encodeToByteArray()), produceFile = { context.dataStoreFile("secure.pb") })` using `androidx.datastore:datastore-tink`. `SecureStoreSerializer` is a kotlinx-serialization JSON serializer of `SecureStore`.
- Failures (TR-REL-06): a keyset that cannot be unwrapped (lost Keystore key) is deleted and recreated by `KeystoreAead`; a `secure.pb` that then fails to decrypt is a `CorruptionException` that the DataStore's `ReplaceFileCorruptionHandler` replaces with an empty store; an I/O or crypto exception on read is caught in `SecureTokenStore`, which clears the store. Each path logs `token_store_reset` and reports "no token" → SCR-02 (FR-001). `SecureTokenStoreTest` checks on the JVM that the file never contains the token in plain text; TC-146 repeats it on a device.

---

## 7. Catalog sync and connectivity

### 7.1 `CatalogSyncRepository.sync()` (FR-028)
`DefaultCatalogSyncRepository` (DEV-022):
1. Single flight: a `Mutex` makes concurrent triggers run one after the other.
2. The base copy is the held-back catalog (step 5) or the cached one, but only for the same merchant, so another merchant's cache never lends its ETags.
3. E5 with `If-None-Match`, then E6 with `If-None-Match`, each retried per §5.3. A failure (including a body that does not parse, `DomainError.Unknown`) returns the error and keeps the cache. A 304 with no base copy is treated as a bad answer.
4. Both 304: nothing is written (`catalog_unchanged`). Otherwise the changed part is combined with the base copy, with the ETags of each answer. The DTO → domain mapping already validated it; a product with no `role` defaults to MAIN and is logged as a data warning (DEV-044).
5. If `OrderSessionHolder.isOrderActive`, the new catalog is kept in memory as `pending` and not written (A-29). `applyPending()` writes it once the order has ended; `CatalogSyncCoordinator` calls it whenever the session returns to Idle (§7.2, TD-14). A newer sync during the same order replaces `pending` and sends its ETags.
6. Otherwise it is written to Room at once in one transaction (§6.1). A write that fails rolls back and returns `DomainError.Unknown`.
7. After a successful write, the domain port `ImagePrefetcher.prefetch(urls)` gets every product, option, combo, slot option, idle-promo, logo and hero image. `:app` implements it with Coil (`CoilImagePrefetcher`: `ImageRequest` with `memoryCachePolicy(DISABLED)`, disk only), so `:core:data` does not depend on Coil.
8. `lastSyncAt` and a `SyncState` for Settings → Device & network arrive with the settings store (0.7.0, DEV-070).

`syncNow(merchantId)` (0.5.0) runs the same steps but applies the result at once, also during an order, and also writes a catalog that an earlier sync held back. Only `SubmitOrder` calls it, after `PRICE_CHANGED`: the backend's menu is newer than the one the order started with, so waiting until the order ends (A-29) would trap the customer. It is the one explicit exception to step 5 (TD-14).

The result is the config's operating mode (FR-005), which the bind stores with the binding.

### 7.2 Triggers
- On binding (blocking, with the FR-004 states): `MerchantRepository.bind`.
- On startup (non-blocking): `StartupRouter` (§4.2).
- On "Sync now" (Settings, 0.7.0).
- On the first ONLINE after any OFFLINE: `CatalogSyncCoordinator` (`:app`, `@Singleton`, started by `KioskApp`) syncs the bound merchant. CHECKING sits between OFFLINE and ONLINE, so the trigger is not a direct OFFLINE → ONLINE step.
- After `PRICE_CHANGED` on E7: `SubmitOrder` calls `syncNow` (§7.1).
- Every 15 minutes: `CatalogSyncScheduler.schedulePeriodic()` (`WorkManagerCatalogSyncScheduler`) enqueues `PeriodicWorkRequest<CatalogSyncWorker>(15, MINUTES)` with `Constraints(requiredNetworkType = CONNECTED)`, unique name `catalog-sync`, policy `UPDATE`. The worker is a `@HiltWorker`; WorkManager starts on demand with `HiltWorkerFactory` (§4.1). It loads the device state first when the process was started by WorkManager alone, syncs only a bound kiosk, and asks for a retry only after a network error. From 0.7.0 it is cancelled when Auto-sync is off and re-enqueued when it is turned back on.
- When an order ends: `CatalogSyncCoordinator` calls `applyPending()` (A-29).

### 7.3 `ConnectivityEngine` and `DefaultConnectivityMonitor` (TR-NET-11, DEV-051)
`ConnectivityEngine` (`:core:data` `net/`, pure, tested on the JVM with virtual time) turns the network status and an E14 probe into `StateFlow<Connectivity>`. `DefaultConnectivityMonitor` is the thin Android adapter: it feeds `ConnectivityManager.registerDefaultNetworkCallback` readings and the E14 call (`BackendApi.health()`) into the engine. `:app`'s `HardwareBindingsModule` builds it (it needs the bound `BackendApi`, `NetworkErrorSignal` and `@ApplicationScope`, which `:core:data` cannot name) and binds it through the optional `BindingOverrides` (TD-11), so instrumented tests replace it with `FakeConnectivityMonitor`; debug builds keep the real monitor (testers use airplane mode).

| Network | Last probe | `Connectivity` |
|---|---|---|
| none | any | `OFFLINE` (not probed) |
| connected, not validated | any | `CHECKING`, `OFFLINE` after a 4 s grace (not probed) |
| validated | none yet, or in flight after a network change | `CHECKING` |
| validated | healthy | `ONLINE` |
| validated | unhealthy or timed out | `OFFLINE` |

- **Healthy** means E14 `status` and `components.paymentGateway` are both `OK`. A gateway that is `DEGRADED` or `DOWN` makes checkout unavailable, the same as offline (api-spec E14); the Connection test reports it separately (FR-027).
- **Probes**: once on every network change (a new state or network ID; a signal-strength update does not count). While the probe fails: the first retries wait 2 s and 4 s, then every 30 s. While `ONLINE`: no timer, but at once when a real call (any endpoint except E14) fails with a network error, reported through `NetworkErrorSignal` by `ApiErrorMapper`. A probe that has not answered after 5 s counts as failed.
- **Unvalidated network** (a captive portal): `CHECKING` for 4 s and then `OFFLINE`, so the banner appears within 5 s with the debounce (FR-028 AC1). It is not probed: the platform reports a change when it validates, and that starts the probe of the validated network (AC2: Pay is back within 10 s, also when the first probe fails or hangs).
- **Debounce**: the published state must hold for 1 s, so a flapping network does not flicker the banner (TR-PRF-14 allows 5 s).
- **Consumers block on `OFFLINE` only.** `CHECKING` does not gate Pay or Add. The Menu's offline banner, the disabled add buttons, the Pay gate on Your order, the Checkout "Try again" ("Reconnecting…") and the payment machine's restart all read this one flow.

---

## 8. Order, pricing, Budget Plan and payment

### 8.1 Money and pricing (TR-DAT-01, TR-DAT-02)
- `@JvmInline value class Rupiah(val value: Long)` with checked `plus`, `minus` and `times(Int)` (`Math.addExact`, `Math.multiplyExact`).
- `PricingEngine.price(lines, rules): Totals` (or `price(subtotal, rules)`) is a pure function implementing FR-013. `PricingRules.of(config, settings.roundingStep)` takes the promos and `tax.rateBasisPoints` from config and the device's rounding step; a step that config's `rounding.allowedSteps` does not list, or none, falls back to `rounding.defaultStep` (A-25). Tax is always exclusive in 1.0 (BR-02). The steps, in order:
  1. `subtotal` = Σ line totals.
  2. `discount` = the best `THRESHOLD_FIXED` promo, or 0.
  3. `tax = roundHalfUp((subtotal − discount) × taxRate)`, computed in integers from E5 `tax.rateBasisPoints` (`1000` = 10 %): `(base × bp + 5_000) / 10_000`.
  4. `total` = subtotal − discount + tax, rounded up to `roundingStep`.
  5. `rounding` = the difference.

  There is no floating point at any step. A rounding step of 0 ("None") skips step 4. The best promo is the largest discount the subtotal reaches (one per order, A-12); a discount never exceeds the subtotal.
- `PricingEngine.promoHint(subtotal, rules)` is the "Add Rp X more to get Rp Y off" nudge: the closest threshold not reached yet, only while the subtotal is above zero and no promo applies, with a 0–99 progress value for the banner.
- `docs/pricing-cases.md` (0.4.0) is the golden table: `PricingCasesTest` reads it from `docs/` (the Gradle test task passes the folder and declares the file as an input) and checks every row (TC-060). `kotest-property` checks the invariants over 1,000 random carts (TC-061): `total ≥ 0`; `total ≥ subtotal − discount`; when the step is > 0, `total % step == 0` and `0 ≤ rounding < step`; tax equals the half-up rule. The total is **not** monotonic in quantity, because crossing the promo threshold can lower it (FR-032 AC2 depends on that).

### 8.2 Cart and `OrderSession`
`Cart` is an immutable list of `CartLine(lineId, item, quantity, unitPrice, choices)`, where `item` is `CartItem.ProductItem(productId, selections)` or `CartItem.ComboItem(comboId, ComboSelection)`. Two lines are identical when their items are equal (merge rule, FR-010); a `LEVEL` selection is recorded only when it differs from the default, so equal choices always give equal items. Limits of 20 per line and 30 lines are enforced in `Cart.add` (BR-05): a merge past 20 is capped and flagged so the UI shows "Maximum 20 per item"; a full line or a 31st line returns `LimitReached`. `choices` are the structured parts of the line description (`Option`, `LevelChange`, `Size`); the UI words them from string resources (NFR-008).

Unit prices come from `ProductCustomization` (Item detail and quick add: defaults, `select`, `setLevel`, required-group check, `unitPrice` = base + Σ option deltas, with every `LEVEL` option counted at its chosen or default level) and `ComboCustomization` (Combo: first available side and drink, sold-out options not selectable, `unitPrice` = price + (side + drink + large) × `serves`), both on `MenuPricing` (FR-010, FR-011). A product needs Item detail ("Choose ›") only when a required group has no default (`Menu.needsChoice`, FR-009).

Cart edits (DEV-036): `increment` (capped at 20, `LimitReached(MAX_PER_LINE)` at the cap, `null` for an unknown line), `decrement` (removes at quantity 1) and `remove`. `addAll(additions)` adds every line of a Budget Plan or none (a merge past 20 counts as a limit, because a capped plan would no longer equal the plan). The worded line description ("Fries · Cola · Extra cheddar · No onion") is `describe(choices, wording)`: the domain decides the parts and their order, and `:app`'s `ui/ResourceLineWording` supplies the words from string resources.

**`OrderSession` (DEV-041)** is the machine of architecture §5.1. `OrderSessionHolder` (`DefaultOrderSessionHolder`, a singleton from `SessionModule`) holds the current session or `null` at Idle; `chooseOrderType` starts an order on the current catalog versions or changes the type of the running one and keeps its cart; `endOrder()` returns to Idle from any phase. `OrderSession` exposes `StateFlow<OrderState>` (`orderType`, `phase`, `cart`, the catalog version snapshot, `upsellShown`, `planTag`, `issues`, `submission`) and changes it only by compare-and-set, so a double tap cannot act on a stale cart. Each transition returns `false` and changes nothing when the current phase does not allow it:
- `startBuilding` (TypeChosen/Reviewing → Building), `startReviewing` (Building → Reviewing), `submit` (Reviewing → Submitting, only with a non-empty cart and no blocking review issue; the overload `submit(buildRequest, newKey)` also settles the E7 body and key in the same compare-and-set and returns the `Submission`, or `null` when the tap changes nothing), `submissionFailed` (→ Reviewing), `priceChanged(cart, menuVersion, configVersion, issues)` and `itemsUnavailable(lineIds)` (Submitting → Reviewing with the review issues), `reviewPrices()` and `removeUnavailable()` (clear the issues), `orderCreated` (→ Paying), `backToOrder` (Paying → Reviewing, forgets the submission), `paid`, `complete`, and `cancel` / `timeOut` (only before Submitting).
- The cart can change only in TypeChosen, Building and Reviewing; afterwards `add` returns `LimitReached(ORDER_LOCKED)` (BR-08).
- `addPlan(additions, budget, adults, children)` adds a plan atomically and sets `PlanTag(budget, adults, children, lineIds)`; changing or removing one of those lines, or merging a Menu add into one, clears the tag.
- `markUpsellShown()` sets the once-per-order flag (FR-014).
- **Review issues (DEV-046)**: `ReviewIssues(previousUnitPrices, unavailableLineIds, pricesChanged)` lives in `OrderState`, so it survives the navigation between Checkout and Your order. `isBlocking` (prices not reviewed, or an unavailable line not removed) disables Pay and makes `submit` refuse. `pricesChanged` is a stored flag: a `PRICE_CHANGED` that left every unit price as it was (for example a tax change) still has to be reviewed.
- **Submission (DEV-045)**: `Submission(request, key)` is the last E7 body with the key it was sent with (§5.3). `SubmitOrder` (`order/`) is the use case that combines the session, the catalog, the settings, `CatalogSyncRepository` and `OrderRepository`: it builds the request from the cart (`PricingEngine` with the device rounding step), sends one E7, and then moves the session on. `Created` → `orderCreated()`. `PRICE_CHANGED` → `syncNow(merchantId)`, then `CartRepricer` re-prices every line from the refreshed menu with `MenuPricing` (the same rules as the screens) and records each changed unit price as its previous price; a line whose product, combo, option, side or drink is gone or sold out keeps its price and is flagged unavailable; a failed refresh is `Failed`. `ITEM_UNAVAILABLE` → the lines named by line ID, or by product or combo ID, are flagged. Any other failure returns to Reviewing unchanged. If the backend order total differs from the total the kiosk sent, the order is cancelled with E8 and the result is `Failed` (TD-17). A cancelled or crashed submission never stays in Submitting.

### 8.3 `BudgetPlanner` (FR-032, TR-PRF-08) — TD-06
`BudgetPlanner(menu, config.budgetPlanner, pricingRules)` is pure and deterministic. It is built once per catalog (it precomputes the items a plan may use) and answers `plan(PlanRequest(budget, adults, children, PlanOptions)): PlanResult` and `swap(request, plan, pickIndex, part): BudgetPlan`. The approach mirrors the reference planner in `BudgetPas.dc.html` and meets the 100 ms target without trying every combination:

1. **Slots and items.** Each person is a slot. An adult picks a (main, drink) pair or a combo with `serves` = 1; two adults may share one slot holding a combo with `serves` = 2 (combos with any other `serves` are not planned in 1.0). A child picks a (main, drink) pair from kid-friendly mains and kid-friendly drinks; with no kid-friendly main the child gets the cheapest main and the result carries `kidsMenuUnavailable` ("Kids' menu not available", BR-14, D-28); with no kid-friendly drink any drink is used. Then one optional side or dessert slot per person, shared by the table (the "Shared" group of "What's included"); the Customize toggles remove sides or desserts from these slots. Unavailable items and products with `servesAdults > 1` are excluded. A product keeps its default options, or the first option of a required group without one (A-42); a combo keeps its first available side and drink at Regular size.
2. **Reachable subtotals.** For each number of shared two-person meals (0 … adults / 2), a dynamic program over the slots, from the last to the first, records in a `BitSet` which subtotals the remaining slots can make, in units of the greatest common divisor of the item prices (Rp 1.000 on the sample menu). With 10 people that is a few hundred states per slot.
3. **Final totals.** Every reachable subtotal is priced with `PricingEngine` (promo, tax, rounding) and cached, so the promo threshold and the rounding are exact (BR-15).
4. **Selection.**
   - The under plan is the highest total ≤ budget; the over plan is the lowest total in (budget, budget + budget × `overCapPercent` / 100] (none in Strict).
   - A tie on the total goes to the higher subtotal (FR-032 ranking 1), then to fewer shared meals.
   - Then the edge cases: no total ≤ budget → the cheapest plan as `TOO_LOW` ("Need Rp X more"); the under total equals the budget → one `EXACT` plan (AC2, AC8); the under plan is the most generous plan and nothing is over → one `MORE_THAN_ENOUGH` plan; otherwise `UNDER` plus the `OVER` plan when one exists within the cap.
5. **Build.** The chosen subtotal is rebuilt slot by slot: among the options that keep the rest of the subtotal reachable, a main (or meal) or extra not used yet wins (variety, ranking 2); then the planner's preference order: `plannerWeight` (higher first, ranking 3), featured items in featured order (ranking 4), then menu order. Pairs come before combos at the same preference.
6. **Swap and rebalance.** `swap` replaces the main, drink or one-person meal of a pick with the next one of the same role in preference order (wrapping), keeps everyone's main and drink, and reruns steps 2–5 over the shared extra slots only: the plan lands on the budget when it can, otherwise on the nearest total on its own side (an over plan: the lowest total over the budget within the cap; else the nearest under), never beyond the cap (AC9). Swapping a side or dessert cycles it within its role and does not rebalance. A two-person meal is not swapped (as in the design). The plan's kind follows its new total (exact, under or over).

Results: `PlanResult.GroupTooLarge(maxGroupSize)` above the group limit (AC11), `NoPlan` for a menu without an available one-person main and drink (the Menu hides the entry row then: `BudgetPlanner.canPlan(menu)`), or `Plans(plans, kidsMenuUnavailable)` with the hero plan first. `BudgetPlan.toCartAdditions()` is one unit per planned item; the cart merges identical ones.

Verification (0.4.0): AC1–AC5, AC7, AC10, AC11 as unit tests on the sample menu; `docs/budget-plan-cases.md` is a golden table computed by an independent exhaustive search and checked row by row by `BudgetPlanCasesTest`; AC8 checks every budget from Rp 50.000 to Rp 300.000 in Rp 100 steps against an independent set-convolution oracle; AC9 checks every main and drink swap of both TC-161 plans against all possible extra combinations; a `kotest-property` test over 1,000 random groups and budgets checks that under ≤ budget, budget < over ≤ cap, everyone is covered and nobody has two mains; and a JVM timing test asserts a P95 under 100 ms for 10 adults (about 1.5 ms on a development laptop). The device benchmark is TC-168 in 1.0.0 (DEV-031). If the state count grows beyond the target for a real merchant menu, cap the candidate pairs per person at the 12 cheapest plus the 4 most featured and record that in an ADR.

### 8.4 `PaymentStateMachine` implementation (FR-017–FR-019)
The states and transitions are those in architecture §5.2. `PaymentModule` provides one `@Singleton` built with the application scope, `Clock`, `IdGenerator`, `PaymentGateway`, `CardTerminal`, `OrderRepository`, `ConnectivityMonitor` and `OrderSessionHolder`; `PaymentPolicy` holds the timings (poll 2 s, minimum Processing 800 ms, unknown limit 60 s, terminal timeout 90 s, relay of terminal results on). Implementation notes:
- `start(orderId, method, qrExpirySeconds)` works only at `Idle`, so a double tap cannot start two payments. E9 gets a new key, then the QR or the terminal prompt. The card path calls `CardTerminal.startSale(amount, terminalRef)` with the amount **from the E9 answer**, which equals the backend order total (TR-INT-04). The QR path hands `qrString` to the QR card (§8.5).
- **Amount guard (TD-17):** E9's amount must equal the pending order's total. If not, the payment is cancelled (E11, then E8) and the state is `Failed(GENERIC, notCharged = false)` before any QR or terminal prompt; if the cancel fails the pending order stays for recovery. An E9 answer for the other method than the one asked for also fails without prompting anyone.
- **Claim model:** the polling coroutine and the operations never write at the same time. An operation (`tryAgain`, `switchMethod`, `backToOrder`, `cancelOrder`, `acknowledgePaid`, `reset`) first claims the state under a lock, then cancels the polling coroutine, so a late answer cannot overwrite its result. Operations run in the application scope, so a screen that goes away mid-call does not leave the order half-cancelled.
- **Polling:** every 2 s on the injected `Clock` (the tests use a virtual-time clock) calling E10. It ends on `PAID`, `FAILED`, `EXPIRED` or `CANCELLED` (TR-NET-10). A failed poll (network, 5xx, an answer the kiosk cannot read) moves to `Checking` and keeps polling; with no backend answer for 60 s it ends as `Failed(NETWORK, notCharged = false)`, with **no** "not charged" note (TR-INT-06). `PENDING` again restores what was shown before.
- **QR expiry:** the deadline is `expiresAt` minus the server offset (§5.4) on the monotonic clock. At the deadline the machine calls E11 `EXPIRED_ON_KIOSK`; on 409 it reads E10 first: `PAID` wins, and `PROCESSING` keeps waiting and polling (money may be in flight). Otherwise `Failed(EXPIRED, notCharged = true)`. The deadline is cleared on entering Processing, so a payment in flight cannot expire on the kiosk.
- **Card:** the terminal events are collected in a channel and handled before a deadline, and the wait for the next event is a `select` with a timeout, so an approval that arrives at the timeout instant is not lost. An approval only moves to Processing and, with `relayTerminalResults`, sends E12 (no automatic retry: E12 has no key, and the polling answers). A decline is relayed first (E12). A decline, a cancel at the terminal or a terminal error then reads E10 once (`PAID` still wins) and fails; only a backend terminal non-paid status or an accepted relay claims "not charged". The fixed 90 s timeout (A-16) cancels the terminal and sends E11 `TIMEOUT` → `Failed(TERMINAL_TIMEOUT)` (TR-INT-05). The terminal is also cancelled whenever the backend ends the payment (`PAID`, `FAILED`, `EXPIRED`, `CANCELLED`) while the sale is active, so it cannot charge twice (FR-018 AC3); a QR payment never touches it.
- **Processing** is held for at least 800 ms before `Paid` (FR-017) and is bounded: if E10 does not say `PAID` within `unknownLimit` (60 s) of entering it, the state is `Failed(notCharged = false)` (reason `NETWORK` when no answer arrived meanwhile, else `GENERIC`). "Try again" then cancels the payment first, and `PAID` still wins.
- **Paid** is only backend `PAID` (A-15). The machine calls `OrderSession.paid()` when the state becomes `Paid`. `acknowledgePaid()` clears the pending order and goes `Idle`; Checkout calls it at the hand-over to the 0.5.0 Complete placeholder, and the 0.6.0 Complete screen takes it over and prints from the paid payment.
- **Leaving:** `backToOrder` = terminal cancel, E11 `CUSTOMER_BACK` if the payment may be live, E8, then `OrderSession.backToOrder()`. `cancelOrder` = E11 if the payment may be live, then E8 `PAYMENT_FAILED`. A 409 `ORDER_ALREADY_PAID` or a 200 that is not `CANCELLED` is never taken as "left": E10 decides, `PAID` leads to `Paid`, otherwise the customer sees a failure and the order stays pending. The E11 key is renewed when the cancel reason changes.
- **Reset:** when `OrderSessionHolder.current` becomes `null` (a 401 wipe, an order that ended outside the machine), the machine `reset()`s: polling stops, an active sale is cancelled, nothing is sent to the backend and the pending order is kept, so recovery still settles a payment that may be live; at `Paid` it waits for `acknowledgePaid()`. `CheckoutViewModel.begin()` also clears a stale `Failed` or `Paid` of an earlier order before a new one starts (TC-185).
- E9 saves the payment on the pending order (`recordPayment`), so recovery works (TR-REL-02).
- Logging never carries the QR string or the payment reference: `WaitingForQr` and `Paid` override `toString`, and the log lines carry the state name and reason only (§12).

### 8.5 QR rendering (TR-INT-02) — TD-07
`QrEncoder` (`:app` `ui/qr/`) calls `QRCodeWriter().encode(qrString, QR_CODE, 0, 0, hints)` with `ERROR_CORRECTION = H` and `MARGIN = 4`, so the matrix is one pixel per module and includes the quiet zone. It returns `null` for a blank or too-long string and never logs the content. `QrCode` draws the dark modules as rectangles on a Compose `Canvas` with a **whole number of pixels per module** (`QrLayout`; the leftover widens the quiet zone), so nothing is scaled or blurred; the code is dark ink on white whatever the theme. Level H (≥ the required M) is used because the merchant mark always covers the centre. The mark is limited to ≤ 20 % of the QR width (about 4 % of the area), which stays well inside H's error-correction capacity: on the 480 dpx code it is 96 dpx (the design's 104 dpx would be 21.7 %). `QrCard`, `ExpiryRing` and `QrCenterMark` are components of `:core:designsystem` and take the code as a composable slot, so the module has no QR library (§3); the card's shadow is `ink` at 0.06 alpha through the floating elevation. A string that cannot be encoded calls `onEncodeFailed`, which makes Checkout show a local failure (§10.8). TC-079 (`QrCodeDecodeTest`) renders the composable to a bitmap with the mark, decodes it with ZXing and compares it with `qrString`.

### 8.6 Process-death recovery (DEV-052, TD-16)
`RecoverPendingOrder` (`:core:domain` `order/`) implements the table in architecture §5.3. It reads the pending order through `OrderRepository` and decides from the backend, reached only through `OrderRepository` (E8) and `PaymentGateway` (E10, E11). Outcomes: `NothingPending`, `Cancelled`, `Paid(RecoveredPaidOrder)` (the pending order is already cleared; the order lines are not stored, so a receipt printed from it has the totals and the payment details but no line list) and `Deferred(error)`.
- **Rules:** no payment → E8 `APP_RECOVERY`. E10 `PAID` → clear, Complete. `FAILED`, `EXPIRED`, `CANCELLED` → E8. `PENDING` → E11 `EXPIRED_ON_KIOSK`, then E8 (the QR string is not stored, so Checkout cannot be redrawn; D-32). `PROCESSING` → poll E10 every 2 s for up to 60 s, then treat it like `PENDING`.
- **A 409 on E11, or an answer that is not a cancel, re-reads E10 first** (the same rule as the machine's); a payment that is still open ends as `Deferred`. E8 answering `ORDER_ALREADY_PAID` or a paid order goes to Complete. E10 is read with the 1/2/4 s backoff; E8 and E11 retry inside their repositories.
- **Failures never drop the order:** any call that fails (or an unexpected exception) ends the run as `Deferred` with the pending order untouched. Only a paid order clears the row from here.
- **Coordinator** (`PendingOrderRecoveryCoordinator`, `:app`, `@Singleton`): `start()` (from `KioskApp`) ensures its consumers, loads the stored device state itself and, for a `Bound` kiosk, requests the startup run. `StartupRouter` asks again through `recoverAtStartup()`; only the first request in a process counts. After a real process death Android restores the saved NavHost back stack, so Splash and the router may never run: that is why recovery starts from the application, not from the router (TC-191). `recoverAtStartup()` also starts the consumers, so the gate never closes without a run to open it again (instrumented tests run under `HiltTestApplication`, which never calls `start()`).
- **Retry:** a deferral sets `retryNeeded`. Every ONLINE while it is set (CHECKING sits between OFFLINE and ONLINE, so OFFLINE need not come first) and a timer of 30 s while the kiosk stays online start another run; one timer per run, so it cannot spin. A run that finds nothing pending does nothing, so every trigger is safe. A run waits until no customer order is in progress.
- **Gate:** while a run is asking the backend, `RecoveryGate.blocksNewOrder` is true and Idle ignores "tap to order" (at most a few seconds, up to 60 s for `PROCESSING`; waiting for a trigger is not a run). The gate always lifts, also when the run throws or its scope is cancelled. A new order is never created over an unsettled pending payment: `DefaultOrderRepository.create` refuses it with `PENDING_PAYMENT_UNRESOLVED` and sends no E7, so a paid or processing order is never overwritten; the customer sees the "Can't reach the restaurant system" failure on Checkout with Try again, Back to order and Cancel order (§10.8).
- **Complete:** a recovered paid order is kept in `recoveredPaid` and the state becomes `PaidWaiting`. `KioskRoot` (through `RecoveryViewModel`) navigates to the Complete route once, only when Idle is on screen. The 0.6.0 Complete screen takes the order with `consumeRecoveredPaid()`.
- **Known limits:** the pending order is cleared before Complete is shown, so a kill in between leaves no receipt on the next start. The recovered receipt has no order lines. TC-073's real `adb shell am kill` check has not run (the logic is covered by `RecoverPendingOrderTest`, `ProcessDeathRecoveryIntegrationTest` and `PendingOrderRecoveryCoordinatorTest`).

---

## 9. Hardware implementation

### 9.1 Device identity (TR-INT-07)
`IminDeviceIdentityProvider` tries an ordered list of `SerialSource`s and takes the first plausible value (not blank, not `unknown`/`null`, not all zeros):
1. system properties, read by reflection on `android.os.SystemProperties.get`, in the order of `SERIAL_PROPERTY_KEYS`: `ro.serialno` and `persist.sys.imin.sn` (exactly what the iMin device SDK's `SystemPropManager.getSn()` reads, so the SDK is not a dependency, ADR-0002), then `ro.boot.serialno`, `ro.imin.serialno`, `persist.sys.sn`, `ro.sn`;
2. `Build.getSerial()`. Since Android 10 this needs the caller to be device owner **and** hold `READ_PHONE_STATE`. A device owner can grant that to itself with `setPermissionGrantState`, but `READ_PHONE_STATE` is not in the PRD §12.3 permission list, so the source reports "unavailable" without it and never calls the API. Only the debug manifest declares the permission, for the spike. ADR-0002 decides from the device results whether the fallback and the permission stay (A-02, R-3).

The value is cached by `SerialCache` in `:core:hardware`'s own `identity.preferences_pb`, so the module never touches a `:core:data` store (architecture §3). The cache is a speed-up only: the sources are read at every `identify()`. If the live value differs from the cache, the live value wins and `serial_mismatch` is logged; if no source answers but a value is cached, the cached value is returned (`serial_from_cache`, FR-002 AC2); otherwise the call fails with `DomainError.Hardware("identity", "serial_unavailable")`. The debug Developer options show every source's raw result for the on-device spike (ADR-0002 procedure).

### 9.2 Printing pipeline (FR-021, FR-022) — TD-08
```
Order (domain) ──ReceiptRenderer / KitchenTicketRenderer──► List<PrintCommand>   (pure Kotlin)
            ──EscPosEncoder(paperWidth, density)──► ByteArray                   (pure Kotlin)
            ──PrinterDriver.write(bytes)──► transport (iMin SDK | USB bulk | BT SPP)
```
- `PrintCommand`: a sealed type with `Text(style, align)`, `Row(left, right)`, `Divider`, `RasterImage(bitmap1bpp)`, `Feed(lines)` and `Cut`. Widths: 58 mm = 32 columns, 80 mm = 48 columns (Font A). `Row` wraps the left text and never truncates the right-hand amount (TR-INT-09).
- `EscPosEncoder` covers `ESC @` (init), `ESC a` (align), `ESC E`/`GS !` (bold, size), `GS v 0` (raster logo), `ESC d` (feed), `GS V 66 0` (partial cut), and density through `GS ( K` where supported. It is tested against golden byte files (DEV-055).
- **Drivers:**
  - **USB:** `UsbManager` device list filtered on printer-class interfaces; permission through a `PendingIntent` (`FLAG_IMMUTABLE`); claim the interface; `bulkTransfer` to the OUT endpoint in 4 KB chunks with a 5 s timeout.
  - **Bluetooth:** a paired device from `BluetoothAdapter.bondedDevices`; `createRfcommSocketToServiceRecord(SPP_UUID)`; `cancelDiscovery()` before `connect()`; one socket per job.
  - **iMin:** the SDK print-bytes or bitmap API, plus the SDK paper status (ADR-0004).
- **Status:** the iMin SDK provides paper status. For USB and Bluetooth, the driver sends `DLE EOT 4` (paper sensor) where the printer answers, and otherwise reports only online/offline from the last write. `PrinterManager` polls each configured printer every 2 s while Settings is open or a job is queued, and every 60 s otherwise. USB detach (`ACTION_USB_DEVICE_DETACHED`) and Bluetooth disconnect (`ACTION_ACL_DISCONNECTED`) broadcasts update the status immediately (TR-PRF-13).
- **Job queue:** one `Channel<PrintJob>` per printer, consumed in `@ApplicationScope`. One retry after 2 s. On failure, `PrinterStatus.lastError` is updated, the issue indicator lights (FR-031), and `Complete` shows the fallback card (TR-REL-08).

### 9.3 Card terminal
The interface is `CardTerminal { fun startSale(amount: Rupiah, ref: String): Flow<TerminalEvent>; suspend fun cancel(); suspend fun selfTest(): Outcome<Unit> }` (DEV-049). The release binding is `UnavailableCardTerminal` (`:core:hardware`): every sale fails at once with a terminal error, so the machine shows its generic failure and charges nothing, and `selfTest()` reports a hardware error (FR-025). The real adapter waits on OQ-03 and ADR-0003 (not written: the specs had not arrived at the 0.5.0 exit) and arrives in 0.9.0 (DEV-091). Debug builds wrap the binding in `SwitchableCardTerminal` (§4.3). Whatever the transport (USB serial, LAN or cloud), the adapter only turns vendor events into `TerminalEvent` (`Prompted`, `Approved(ref, tid)`, `Declined(code)`, `Cancelled`, `Error`). If the terminal reports to the kiosk rather than to the gateway, `PaymentStateMachine` relays `Approved` or `Declined` through E12. In every case it still waits for E10 `PAID` (TR-SEC-12).

---

## 10. UI implementation

### 10.1 Design system
- `DesignUnit`: `val Int.dpx: Dp @Composable get() = (this * LocalDesignScale.current).dp`, where `LocalDesignScale = frameWidthDp / 1080f` (the frame below; equal to `screenWidthDp / 1080` inside the supported range, PRD §11.2). `spx` additionally multiplies by `LocalLargeText` (1.15f or 1f) (TR-DSP-01).
- **Frame and letterboxing (TR-DSP-02):** screens in the supported range (1080 × 1920 to 1200 × 1920, an aspect ratio of 9:16 to 10:16) fill the window. Any other ratio is clamped into that range: a taller screen is letterboxed vertically, a wider one is pillarboxed, both centred on a `bg` fill. The design scale is computed from the resulting frame width (`frameWidthDp / 1080`). Screen layouts take their vertical space from weights and scrolling areas, not fixed heights, so the 10:16 end of the range fits.
- `KioskTheme` provides colors, type, shapes and spacing, plus `LocalAccent` from `MerchantConfig.accentColor`. Material 3's `MaterialTheme` is set up underneath with the same palette, so the Material components used as a foundation (text field, bottom sheet) match.
- **Motion:** `KioskMotion` holds the named `CubicBezierEasing` constants and per-screen durations from PRD §11.4. `motionSpec(spec)` returns `snap()` when `LocalReduceMotion.current` is true. `LocalReduceMotion` = the setting **OR** `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`, observed through a `ContentObserver` (TR-ACC-05).
- **Rolling numbers, fly-to-cart, countdown rings:** shared components in `:core:designsystem` built on `Animatable` and `graphicsLayer`, so animated layers do not trigger relayout (TR-PRF-05).

### 10.2 Navigation and overlays
`KioskNavHost` follows architecture §11. The customer flow is a nested graph `CustomerGraph`, and the staff area is `StaffGraph`. The root composable stacks the `NavHost`, then the inactivity overlay (SCR-13), then the offline banner host.

### 10.3 Inactivity (FR-023)
A root `Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); controller.onUserActivity() } } }` sees every touch without consuming it. `InactivityController` (`inactivity/`, using the injected `Clock`'s monotonic time and coroutine `delay`s, so tests drive it with `TestClock` and virtual time) keeps a deadline from the `idleTimeoutSeconds` setting. `KioskRoot` reports every touch, and maps the current back-stack destination to a zone (`inactivityZone()`): Order type; the ordering routes Menu, Combo, Item, Upsell, Your order and Budget Plan (FR-023: SCR-05…SCR-10 and SCR-19, architecture §11); or off (everything else, including Checkout, Complete and staff settings). The timer also stops whenever the order phase can no longer be cancelled (`OrderPhase.isCancellable` is false from `Submitting` on, so `Paying` and `Paid` are covered) and starts again after "Back to order". When the deadline passes it shows the overlay, except on Order type with an empty cart (or no order yet), where it returns straight to Idle (A-23). A touch does not dismiss the overlay; only the overlay's own tap-anywhere area and its two buttons do (Continue resets the timer; Start over calls `OrderSession.cancel()`). At 0 it calls `OrderSession.timeOut()` and the root pops to Idle, whose ViewModel ends the order. The controller is held by `InactivityViewModel` in `viewModelScope`, so it runs on the main thread with the touches that feed it and is not a singleton: the staff area can run its own 120 s instance (A-22, 0.7.0). The overlay (`StillThereOverlay`) blurs the screen under it on API 31+ and uses a darker scrim on API 30, where blur is not available.

### 10.4 Lifecycle and recomposition
- ViewModels collect with `collectAsStateWithLifecycle()`.
- `UiState` classes are `@Immutable` data classes holding read-only `List`s. Compose compiler reports confirm they are stable, so `kotlinx.collections.immutable` is not needed.
- Lists and grids use stable `key`s (product ID). The Menu grid is a plain column of rows rather than a lazy grid: a category holds a handful of products, and the rows of a 2-column grid share one height as in the design.
- Images are requested at their display size (`size(Size(widthPx, heightPx))`) so full-resolution bitmaps are never decoded (TR-PRF-05, TR-REL-01).

### 10.5 Sound and brightness
- `SoundPlayer` loads the three short OGG files into a `SoundPool(maxStreams = 2)` with `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SONIFICATION)` at startup, so they play on the media stream at the system volume (FR-030), and only when the setting is on (TR-ACC-07).
- `BrightnessController` sets `window.attributes.screenBrightness` to 0.3 / 0.6 / 1.0 (A-26).

### 10.6 Customer screens (0.3.0)
- **Idle (DEV-024):** the slideshow advances in a coroutine `delay` loop while the screen is at least STARTED, so it pauses off screen and Compose tests drive it with the main clock (TC-020). The staff corner is a `pointerInput` hold: 2.0 s of continuous press opens the PIN; release, leaving the 160 dpx area or a short tap do nothing (A-04). The Route sends a `Shown` event on every visit, and the ViewModel ends the order (`OrderSessionHolder.endOrder()`), which also lets the coordinator apply a held-back sync (§7.2). Under reduce motion the slides still advance; the crossfade, Ken Burns and loops snap.
- **Order type (DEV-025):** the cards are config `orderTypes` (the FR-026 toggles become one more filter in 0.7.0). The pick goes to `OrderSessionHolder.chooseOrderType` at once; the 950 ms advance is a ViewModel `delay`, kept under reduce motion because FR-007 AC1 is about timing; later taps are ignored.
- **Menu (DEV-027, DEV-028):** the ViewModel combines the catalog, the current order session and connectivity. A category switch filters the in-memory menu (TR-PRF-04). Quick add uses `ProductCustomization.defaults` and `OrderSession.add`; the 1.4 s check and the 2.4 s "Maximum 20 per item" toast are ViewModel timers, so tests use virtual time. Offline (`Connectivity.OFFLINE` only) disables the add buttons, "Choose ›" and "View order", while browsing works. "View order" goes through Upsell the first time in an order when the setting is on (`MenuEffect.OfferUpsell`, §10.7), otherwise to Your order; the "Plan by budget" row also needs `BudgetPlanner.canPlan(menu)`.
- **Item detail (DEV-029):** modifier groups are rendered generically by TD-15. The disabled CTA reads the missing group's name when it starts with "Choose " ("Choose a size to continue"), otherwise "Choose {group name} to continue". The screen stays in the Added state until the customer leaves.
- **Combo (DEV-030):** `ComboCustomization` drives the state; "Added" lasts 1.8 s and ignores a second tap (TC-049).
- **Shared:** the header pills (`BackPill`, `CartPill`) and the selection dot are design-system components; photos are `ui/MenuPhoto` with the category or combo tone. Entrance and feedback timings that PRD §11.4 lists are `KioskMotion` constants; the per-element delays and durations it doesn't list are file-private constants copied from the design CSS, and every curve is a named `KioskEasing` (including `Pop`, `.3, 1.4, .5, 1`). The 30-line cart limit is only logged on these screens, because the design has no copy for it.

### 10.7 Ordering screens (0.4.0)
- **Your order (DEV-037, DEV-037a, DEV-038):** the ViewModel combines the catalog, the order session, the settings and connectivity, and prices the cart with `PricingEngine` on every change, so the line totals, the summary and the "Pay Rp X" label move together (FR-012 AC1, TC-066). Methods are `config.visiblePaymentMethods(settings)`; a selected method that disappears falls back to the first one. One banner shows at a time, by priority: offline, prices changed, item unavailable, then the promo nudge (`PromoHint`); the first three disable Pay with their reason. The price-changed and unavailable states come from `OrderState.issues`, which `SubmitOrder` fills from E7 (DEV-046; §8.2): "Review" calls `reviewPrices()`, and Remove, the trash and the minus clear the flags of the line they remove. "Offline" means `Connectivity.OFFLINE` only; `CHECKING` does not block (§7.3). A removed line leaves the cart and the totals at once and then slides out for 460 ms; the empty tray appears after the last one. Opening the screen calls `startReviewing()`, "Add more" and "Back to menu" call `startBuilding()`. Pay emits the chosen method once per visit and the app navigates to Checkout with it (`launchSingleTop`), where the order is submitted (§10.8).
- **Upsell (DEV-039):** `Upsell.shouldOffer` decides on the Menu; SCR-09 calls `markUpsellShown()` and `startReviewing()` when it opens, and Back to the Menu calls `startBuilding()`. The suggestion list (`Upsell.suggestions`) is fixed when the screen opens, so a card just added keeps its place with its "N added" state. The strip and "Continue" show the cart subtotal, as drawn.
- **Budget Plan (DEV-043, DEV-043a):** one route with an internal step. The ViewModel builds one `BudgetPlanner` per catalog and rounding step and keeps the step, inputs, Just under / Just over choice, swaps, toggles and tolerance (architecture §5.1). Every input change asks for a new plan; planning and swaps run on `@DefaultDispatcher` with the latest input winning, and the plans on screen stay until the new ones arrive, so an open sheet does not flicker. The screen opens with no budget, 1 adult and 0 children (A-43). Toggles and the tolerance recompute the whole plan and clear the edits; "Reset to suggested" is enabled only after a swap. "Add plan to order" calls `OrderSession.addPlan`, shows "Added to order" and opens Your order 1 s later; from then on nothing on the screen can change or add the plan again (only "My order" still acts). The kids' menu note (A-41) is a note line on the hero card.

### 10.8 Checkout (0.5.0)
- **Entry (DEV-045, DEV-049, DEV-050):** `CheckoutRoute(method: String)` carries the payment method by name; a secondary constructor takes a `PaymentMethod` (TD-19). `CheckoutViewModel` reads it, submits the order with `SubmitOrder` and, on `Created`, starts the payment with `machine.start(orderId, method, qrExpirySeconds)`, once. A configuration change keeps the ViewModel, so nothing is submitted twice (TC-138), and a second Pay cannot start a second order (TC-069). If the machine still shows the `Failed` or `Paid` of an earlier order while the session is a new order on Your order, that state is stale and is reset first (TC-185).
- **What it renders** (`CheckoutUiState`, built by the pure `checkoutUiState(...)` from the machine, Checkout's local state, connectivity and the catalog, so the screenshot tests build the boards from the same mapping): "Creating your payment…" (also while E7 runs), the QR card or the card prompt, "Checking your payment…", "Confirming payment" (also while the machine is `Paid` and the screen leaves for Complete), and the failure panels. The amount is the backend order total once the order exists, the kiosk total before. The method switcher shows only when two methods are enabled (FR-025); the QR card has a once-per-second ticker only while a QR is on screen. Copy comes from `strings.xml` (`checkout_*`).
- **Leaving:** "Back to order" = `machine.backToOrder()`; on `Left` it returns to Your order with the cart intact. "Cancel order" = `machine.cancelOrder()`, then it ends the order (`OrderSession.cancel()`, `endOrder()`) and returns to Idle. `PRICE_CHANGED` and `ITEM_UNAVAILABLE` pop back to Your order, where the session holds the issues. Paid: Checkout sends `ToComplete` and calls `machine.acknowledgePaid()`; the 0.5.0 Complete is a placeholder, so the 0.6.0 Complete screen takes `acknowledgePaid()` over (TC-088 on).
- **Local failures (TD-20):** the machine only knows payments, so two failures are Checkout's own. (1) An E7 that failed after its retries, or an unexpected exception while submitting, shows "Payment didn't go through" with "Can't reach the restaurant system. Please try again." and **no** "not charged" note, because no payment exists. "Try again" runs `SubmitOrder` again, which reuses the key for the same body; "Pay by X instead" runs it with the other method; "Back to order" goes to Your order (the session is already there); "Cancel order" ends the order. (2) A `qrString` that cannot be encoded shows the generic failure; "Try again" leaves the payment and the order like "Back to order" and submits a new order. The same panels show while the kiosk is offline with Try again disabled and labelled "Reconnecting…" (`S_Checkout_offline`); the "Ordering is paused" variant has no switch button and shows the "Your order is saved" note only there (A-45).
- **Failure copy:** expired, cancelled (QR: "…cancelled in your app…", card: "…cancelled on the terminal. Try again or choose another method." (A-45)), declined, terminal timeout, unreachable and generic, each with its own title (FR-019 AC1). "You have not been charged · your order is saved" shows only when the machine says `notCharged`.
- **QR life:** the seconds sent to E9 are the QRIS config default (`qrExpirySecondsDefault`) when it is in `qrExpirySecondsAllowed` (or the list is empty), else 300 s, until the 0.7.0 Payment settings (FR-025) exist. The countdown on screen comes from the machine's deadline, rounded up so it never reads 0:00 while alive.
- **Idle timer:** off on Checkout (§10.3). **Debug only:** the simulate bar is drawn by `CheckoutDebugTools` when the debug source set provides it (§4.3); release has an empty `Optional` and none of the bar's text (`scripts/check-release-apk.sh`).
- **Design deviations:** no simulate bar in the QR and card goldens (it is debug-only, and the goldens follow the release rule); the real level-H QR is denser than the design's pseudo QR; the centre mark is 96 dpx, not 104; the switcher thumb shadow bleeds slightly; the entrance and shake animations are approximated. The first three are recorded in [`ui-design/README.md`](ui-design/README.md).

---

## 11. Kiosk mode and platform integration

### 11.1 Manifest (TR-SEC-08, TR-SEC-09, TR-SEC-10)
- `MainActivity`: `exported=true`, `screenOrientation="portrait"`, `launchMode="singleTask"`, `lockTaskMode="if_whitelisted"`. Intent filters for `MAIN/LAUNCHER` and `MAIN/HOME/DEFAULT`.
- `KioskDeviceAdminReceiver`: `exported=true`, `permission="android.permission.BIND_DEVICE_ADMIN"`, `res/xml/device_admin.xml`.
- `BootReceiver`: `exported=true` with `RECEIVE_BOOT_COMPLETED` only. It relaunches `MainActivity` as a safety net, since the persistent home activity normally relaunches it anyway.
- No other exported component. `WorkManager`'s components (`DiagnosticsReceiver`, and `SystemJobService`, which the system binds only with `BIND_JOB_SERVICE`) and `ProfileInstaller`'s receiver are merged with their library defaults (permission-guarded) and listed in the release check allow-list.
- `android:allowBackup="false"`, no `dataExtractionRules`, `usesCleartextTraffic` not set (the network security config governs cleartext).
- Permissions are exactly PRD §12.3:
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `RECEIVE_BOOT_COMPLETED`;
  - `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` (with `usesPermissionFlags="neverForLocation"`);
  - `BLUETOOTH` and `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`;
  - `ACCESS_FINE_LOCATION` (SSID only, requested from Device & network);
  - `<uses-feature android:name="android.hardware.usb.host" android:required="false"/>`;
  - no `FOREGROUND_SERVICE`, because printing runs in `@ApplicationScope` (A-39);
  - `READ_PHONE_STATE` only if ADR-0002 keeps the `Build.getSerial()` fallback and the PO approves it (§9.1). Until then it is declared in the **debug** manifest only, for the DEV-012 spike.

### 11.2 `KioskModeController` (FR-029)
When `dpm.isDeviceOwnerApp(packageName)` is true, after merchant binding:
- `setLockTaskPackages(admin, arrayOf(packageName))`;
- `setLockTaskFeatures(admin, LOCK_TASK_FEATURE_GLOBAL_ACTIONS)`: no home, recents, notifications or status bar, but the power menu keeps its device default (FR-029: "Volume/power key behaviour follows the device defaults");
- `addPersistentPreferredActivity(admin, HOME filter, MainActivity)`;
- `setKeyguardDisabled(admin, true)`;
- `setStatusBarDisabled(admin, true)`;
- then `activity.startLockTask()`.

`MainActivity` sets `FLAG_KEEP_SCREEN_ON` while in lock task (FR-029, architecture §11). No device-wide setting is changed for this.

When the app is not device owner, it calls `startLockTask()` (screen pinning) and Settings home shows the warning.

"Exit kiosk mode" (behind the PIN) calls `stopLockTask()` and clears the persistent preferred activity.

"Restart app" (FR-027) schedules a `PendingIntent` for `MainActivity` with the inexact `AlarmManager.set(ELAPSED_REALTIME, now + 500 ms)`, which needs no exact-alarm permission, and then calls `Process.killProcess(myPid())`. As the persistent home activity, the app is also relaunched by the system. It restarts at the Splash screen, still in lock task (TR-PRF-16).

### 11.3 Provisioning (TR-DST-03)
- Development: `adb shell dpm set-device-owner com.hitachi.kiosk/.kiosk.KioskDeviceAdminReceiver` on a device with no accounts.
- Field: per OQ-10 (QR provisioning, the MDM's own path, or an iMin tool). Documented in `docs/provisioning.md` (DEV-101).

---

## 12. Logging, privacy and diagnostics

- `KioskLog` is a pure-Kotlin facade in `:core:domain` (`log/`), so every module can log without depending on Android or Timber. `:app` installs a `TimberLogSink` at startup. Calls take an event name plus key-value fields, never free text containing user input.
- **Redaction (TR-PRV-03):** inside the facade, before any sink sees a field, `Redactor`:
  - drops keys named `pin`, `currentPin`, `newPin`, `currentPinProof` and `deviceToken`;
  - truncates `paymentId`, `reference`, `terminalRef`, `approvalCode` and `qrString` to the last 4 characters (NFR-006: payment references);
  - drops any `customer*` key.

  Unit tests cover every rule. The payment code logs state names, reasons and counts only: `PaymentUiState.WaitingForQr`, `Paid` and `StartedPayment.Qris` override `toString` so a QR string or payment reference never reaches a log line (NFR-006), and the QR encoder logs the content length, never the content.
- **`RotatingFileTree` (TR-PRV-04):** writes on a single-thread executor to `filesDir/logs/kiosk-YYYYMMDD-N.log`, with files ≤ 2 MB, at most 5 files (10 MB in total), and files older than 7 days deleted at startup and at rotation.
- Release builds log `INFO` and above. Debug builds log everything to Logcat as well.
- Diagnostics for staff come from the status flows (`PrinterStatus`, `Connectivity`, `TerminalStatus`) that feed Settings home and the Idle red dot (FR-031). Nothing is uploaded (TR-PRV-01).

---

## 13. Performance strategy (TR-PRF-*)

| Concern | Technique |
|---|---|
| Startup (TR-PRF-01) | Baseline profile + ProfileInstaller. No synchronous disk work in `Application.onCreate`. Hilt graph kept lazy (`Lazy<>`/`Provider<>` for hardware and sync). Fonts bundled, not downloaded. |
| Jank (TR-PRF-05) | Animations through `graphicsLayer`/`Animatable`. Stable keys in lazy layouts. Images sized to the slot. Compose compiler reports reviewed at 0.8.0 for unstable parameters. One animated layer on Idle (TR-PRF-20). |
| Category switch (TR-PRF-04) | Menu held in memory as domain models after the first load. A category switch only filters an in-memory list. |
| Budget Plan (TR-PRF-08) | DP over price units (§8.3), computed on `@DefaultDispatcher`. |
| Measurement | `:benchmark`: `StartupBenchmark` (cold, ×10), `JourneyBenchmark` (Idle → Menu → Item → Your order with `FrameTimingMetric`), `PlannerBenchmark`. JankStats is enabled in debug and in the benchmark build variant (TC-143), never in release. |

---

## 14. Test infrastructure

| Need | Implementation |
|---|---|
| Fakes (TR-INT-10) | `:core:testing` `Fake*`. Each one takes a script (`Queue<Outcome>`) or a mode enum, plus optional delays driven by the test dispatcher. Added in 0.5.0: `FakeOrderRepository`, `FakeCatalogSyncRepository.syncNow` (with `onSyncNow` to change the catalog the way a refresh would), `FakePaymentGateway` recording `starts`, `cancels` and `relays` and answering sample payments with the order total, and `FakeBackendApi` answering the sample order with the request's total (so the "same total" checks pass for any cart). A Prism mock answers canned totals, so it only works for a cart that equals its canned total (Rp 172.700, the design sample); any other cart ends as the total-differs failure (TD-17). |
| Fixtures (TR-NET-02) | `core/testing/src/main/resources/fixtures/*.json` (31 files, byte-identical to the `api-spec.md` examples), written by `./gradlew extractApiFixtures` (`docs/api/tools/extract_fixtures.py`), which shares its parser `spec_examples.py` with `gen_openapi.py`. The `checkApiFixtures` step of `verify` fails on a diff (§2.6). `SampleData` holds the same examples as domain objects, and the `:core:data` contract tests check that each parsed fixture equals it. |
| Contract and network tests | `mockwebserver3` + `mockwebserver3-junit4` serve the fixtures and inject faults (delays, 5xx, disconnects) (TR-NET-03, TR-NET-04). |
| Room and DataStore | Robolectric with an in-memory Room database (`CatalogDaoTest`, `DefaultCatalogRepositoryTest`, `PendingOrderStoreTest`). The sync is tested on the JVM against an in-memory `CatalogStore`. `MigrationTestHelper` (`room-testing`) runs `KioskDatabaseMigrationTest` against the exported `schemas/` (added as unit-test assets). A DataStore on a temporary folder. |
| WorkManager | `work-testing` (`WorkManagerTestInitHelper`, `TestListenableWorkerBuilder`) on Robolectric for the scheduler and `CatalogSyncWorker`. |
| Flows and time | `runTest` + `StandardTestDispatcher`, `TestClock` implementing the domain `Clock`, Turbine. Code that waits with `delay` and measures with `elapsedRealtime` (the QR countdown, the 60 s unknown window, the 90 s terminal timeout, the 30 s recovery timer, the connectivity engine) is tested with `VirtualTimeClock`, which follows the virtual time of the test dispatcher, so nothing sleeps. |
| Property tests | `kotest-property` `checkAll(iterations = 1_000, …)` inside JUnit 4 `@Test` functions (TC-061, FR-032 AC8). |
| Screenshot tests | Roborazzi on Robolectric (SDK 36, §16), goldens committed under each module's `src/test/screenshots`. A 1080 × 1920 px screen at 160, 240 and 320 dpi is `@Config(qualifiers = "w1080dp-h1920dp-mdpi")`, `"w720dp-h1280dp-hdpi"` and `"w540dp-h960dp-xhdpi"`. One test per `S_*` board and screen artboard, plus larger-text variants (TR-MNT-08). |
| JVM UI tests in `:app` | Robolectric runs them on a plain `android.app.Application` (`robolectric.properties`), not `KioskApp`: unit tests build their classes by hand, and `KioskApp` would build the Hilt graph and start WorkManager and the sync triggers. |
| Instrumented UI | `HiltTestRunner`, `@TestInstallIn` replacing `DeveloperOptionsModule` with `FakeBindingsTestModule`, whose `BindingOverrides` return the scriptable `FakeBackendApi`, `FakeDeviceIdentityProvider`, `FakeConnectivityMonitor` and, since 0.5.0, a `FakeCardTerminal` (`CheckoutUiTest` drives the Checkout journeys through it). `HiltTestRunner` installs a test WorkManager (`HiltTestApplication` is not the WorkManager configuration provider), and `testOptions.animationsDisabled` runs the suite with system animations off, which the app reads as reduce motion, so Idle's chained slide animations let Compose go idle. `createAndroidComposeRule<MainActivity>()` with `enableAccessibilityChecks()` (TR-ACC-01). |
| System-level | UI Automator for lock task, process kill and reboot checks, plus the 72 h soak script (TR-REL-01). |
| Release checks | `scripts/check-release-apk.sh` (§2.5). |

---

## 15. Key technical decisions

| ID | Decision | Rationale | Alternatives | Record |
|---|---|---|---|---|
| TD-01 | Convention plugins in an included `build-logic` build | Shared SDK, JVM and quality settings across 7 modules. One place to change them. | Copy-pasted module build files; `buildSrc` (slower to invalidate) | This document |
| TD-02 | Environment selected by the `kiosk.env` Gradle property, not product flavors | Architecture §7 requires a flavor-less config. Keeps the variant count at 2 (debug, release). | Product flavors `dev/staging/prod` (6 variants) | This document |
| TD-03 | Local `./gradlew verify` gate, no CI in 1.0 | D-31. A single task keeps the CI move trivial later. | GitHub Actions now | D-31 in the PRD |
| TD-04 | Keep architecture §13.3's `DomainError` set; carry the API code in `Server(code)` | No new error types. ViewModels map codes to copy. | One error class per API code | This document |
| TD-05 | `datastore-tink` `AeadSerializer` with a Keystore-wrapped Tink keyset | The official artifact implements ADR-0001's Secure DataStore exactly. | A hand-written AEAD serializer | ADR-0001 (implementation detail) |
| TD-06 | Budget Plan uses dynamic programming over subtotal price units, then final pricing per reachable subtotal | Guarantees the exact match and the closest under/over totals within 100 ms | Exhaustive search with pruning; greedy | DEV-042 (updated to this approach); ADR if it changes |
| TD-07 | QR at error-correction level H with a centre mark ≤ 20 % of the width | The centre mark is always drawn. H still satisfies "≥ M". | Level M with no mark (contradicts the design) | This document |
| TD-08 | Pure-Kotlin print renderer and ESC/POS encoder; drivers only move bytes | Golden tests without hardware. One renderer for three transports. | A vendor ESC/POS library | Architecture §9; ADR-0004 for the drivers |
| TD-09 | Keep Navigation Compose 2.x and Room 2.x in 1.0, although Navigation 3 and Room 3 are stable | Architecture §11 and §6 are written for them. Migration has no 1.0 benefit. | Navigation 3; Room 3 | technology-stack §7 |
| TD-10 | `kotlin.time.Instant` for every business timestamp | kotlinx-datetime 0.7+ removed `kotlinx.datetime.Instant` in favour of the standard library type | Pin an older kotlinx-datetime | conventions §4 |
| TD-11 | Debug builds change bindings only through optional `BindingOverrides` / `DeveloperMenu` bindings declared in `src/main` and provided in `src/debug` | Hilt cannot override production bindings outside tests; `src/main` stays the release graph | A `src/debug` + `src/release` module pair per binding | This document (§4.3) |
| TD-12 | **Superseded by TD-18 (0.5.0).** In 0.3.0 connectivity was the default network's validation state only (no E14 probe, no debounce) | The 0.3.0 offline banner needed a real signal on the device; the health probe belongs with the checkout gate in 0.5.0 (PO, 2026-10-23) | An always-online stub (banner reachable only with fakes) | This document (§7.3) |
| TD-13 | The config row stores the E5 wire JSON, written by `MerchantConfig.toDto()` and read by the E5 mapper | One parser for network and cache; no domain serialization annotations in `:core:domain` | `@Serializable` domain types; a column per config field | This document (§6.1) |
| TD-14 | A catalog held back during an order is applied by `:app`'s `CatalogSyncCoordinator` when `OrderSessionHolder.current` returns to `null` | `:core:data` has no application scope; the trigger list lives in one app-level place (§7.2) | The data layer observing the session in its own scope | This document (§7.1, §7.2) |
| TD-15 | Item detail renders modifier groups generically: required `SINGLE` groups with no default first (option cards, "Required"); a `SINGLE` group of exactly two options whose default costs Rp 0 as one switch row ("Make it a meal"); other `SINGLE` groups as option rows; `LEVEL` groups as a collapsed "Customize" row; `MULTI` groups as a collapsed "Add extras" row | FR-010 asks for generic rendering from the groups; the API has no display hint, and this rule reproduces the design for b2 and gives a sensible layout for every sample product | A display-type field in E6 (contract change) | This document (§10.6), DEV-029 |
| TD-16 | Process-death recovery starts from `KioskApp.start()` and decides per backend status: no payment, `FAILED`/`EXPIRED`/`CANCELLED` → E8; `PAID` → Complete; `PENDING` → E11 then E8 (not resumed); `PROCESSING` → poll 60 s, then as `PENDING`. A failing call leaves the order pending and is retried on every ONLINE and every 30 s while online. New orders wait for a running run and are refused over an unsettled payment | After a real process death Android restores the saved back stack, so Splash never runs. The QR string is deliberately not stored (NFR-006), so Checkout cannot be redrawn. Money may be in flight in `PROCESSING`. A paid or processing order must never be forgotten or overwritten (TR-REL-02). The PO left the choice to the lead (D-32) | Resume Checkout (needs the QR string in storage); cancel `PROCESSING` at once (can lose a paid order); recover from `StartupRouter` only | D-32; this document (§8.6); architecture §5.3 |
| TD-17 | Two amount guards before money moves: the order the backend creates must have the total the kiosk sent (else E8 and `Failed`), and the E9 amount must equal the pending order's total (else E11, E8 and `Failed(GENERIC)`, before any QR or terminal prompt) | "Never pay another amount" (FR-016 AC3, TR-INT-04) must not rest on the backend alone; defence in depth costs one comparison. A Prism mock with canned totals only works for a cart that equals its canned total | Trust the backend total | This document (§8.2, §8.4) |
| TD-18 | `ConnectivityEngine` (pure, `:core:data`) with a thin `DefaultConnectivityMonitor`, built in `:app`: E14 probe (`status` and `paymentGateway` both `OK`), 2 s and 4 s quick retries then 30 s, 5 s probe timeout, immediate re-probe on a real network error, 1 s debounce, and a connected-but-unvalidated network is `CHECKING` for 4 s and then `OFFLINE`. Consumers block on `OFFLINE` only | FR-028 AC1 (banner within 5 s) and AC2 (Pay within 10 s) need the unvalidated case to end and the first probe after a network change to retry quickly. The logic is testable with virtual time. `:core:data` cannot name `@ApplicationScope`, so `:app` builds the monitor. Supersedes TD-12 | A probe on a timer while online; blocking on `CHECKING` | This document (§7.3) |
| TD-19 | `CheckoutRoute` carries the payment method as a `String` name, with a secondary constructor taking `PaymentMethod` | Android Lint `MissingKeepAnnotation` rejects an enum route argument in a minified build, and `:core:domain` (pure JVM) cannot declare `@Keep` | An enum argument with `@Keep` in `:app`; passing the method through the session | This document (§10.8) |
| TD-20 | A failure of order creation (E7 after its retries) and a QR that cannot be drawn are Checkout's own failures, not payment machine states; the first has no "not charged" note | The machine only knows payments, and no payment exists after a failed E7. "Not charged" is only claimed from a backend status (FR-019) | A machine state for order failures; the Your order banner | This document (§10.8); A-45 |
| TD-21 | Room version 2 adds `pending_order` with a real `Migration(1, 2)`; the blanket destructive fallback is removed, also for the cache tables | `pending_order` must survive an upgrade (TR-REL-02, TR-REL-09), and one fallback for everything would drop it too. Keeping one rule is simpler than a per-version fallback | `fallbackToDestructiveMigrationFrom(1)` for the cache only | This document (§6.1) |

---

## 16. Risks in this design

| Risk | Mitigation |
|---|---|
| detekt 1.23.8 may not parse Kotlin 2.4 sources | **Closed in DEV-001**: it does, without type resolution. |
| Robolectric may not support API 37 yet | **DEV-001 result**: Robolectric 4.17 starts API 37, but Compose draws nothing there, so screenshot tests run on SDK 36 (`robolectric.properties`). Instrumented tests cover API 37. Revisit with the next Robolectric release. |
| KSP 2.3.12 with Kotlin 2.4.21 is unconfirmed | **Closed in DEV-001**: it works, with Hilt 2.60.1 and AGP 9's built-in Kotlin. |
| Compose's transitive Espresso 3.5.0 fails on API 37 (`InputManager.getInstance()` was removed) | Constrained to Espresso 3.7.0 in `kiosk.compose` (technology-stack §4). |
| Android 16+ may ignore `screenOrientation="portrait"` on large screens (sw ≥ 600 dp; a 1080 × 1920 kiosk at 240 dpi is 720 dp wide), and `targetSdk 37` removes the opt-out | The kiosk is mounted in portrait and runs in lock task, so the window is portrait anyway. The DEV-012 spike screen reports the window orientation and size on the iMin device; the result goes in ADR-0002, or a new ADR if the app must handle a landscape window (NFR-002, TR-PLT-05). Pending the device run. |
| iMin SDK distribution (JitPack) and APIs vary by model | Spikes DEV-012 and DEV-056 on the real device (OQ-01). Drivers sit behind interfaces. DEV-012 found that `IminDeviceLibrary` 3.0.0 only reads two system properties, so identity does not depend on it (ADR-0002). |
| EDC transport unknown | `CardTerminal` interface and fake (OQ-03, R-1). At the 0.5.0 exit the specs had not arrived, so ADR-0003 is not written, release has `UnavailableCardTerminal` and the PO is asked to acknowledge that 0.9.0 is at risk. |
| A kill between clearing the pending order and showing Complete leaves no receipt on the next start, and a recovered receipt has no order lines | Known limits (§8.6). Clearing after Complete is shown, and storing the lines, are 0.6.0 options when printing is built (DEV-059). |
| `PROCESSING` that never resolves keeps a recovered order pending | It is never cancelled while money may be in flight; after 60 s the recovery tries E11, and a refused cancel leaves it pending and retried on every ONLINE (§8.6). |

---

## 17. Traceability: technical requirements → design

| TR area | Design sections |
|---|---|
| TR-PLT | §2.2, §2.3, §11 |
| TR-DSP | §10.1 |
| TR-PRF | §4.2, §8.3, §9.2, §10, §13 |
| TR-REL | §4.1, §5.3, §6.1, §8.4, §8.6, §9.2, §10.8 |
| TR-SEC | §2.3, §2.5, §5.1, §6.3, §11.1, §12; PIN check: `StaffPinVerifier` in `:core:domain` (`PBKDF2WithHmacSHA256` via `javax.crypto.SecretKeyFactory`, iterations and salt from E5, `MessageDigest.isEqual`; `PLAIN_MOCK_ONLY` accepted only when `BuildConfig.DEBUG`, which `:app` passes in as a flag) |
| TR-PRV | §5.1, §12 |
| TR-NET | §5, §7.3, §14 |
| TR-INT | §8.2, §8.4, §8.5, §9, §10.8 |
| TR-DAT | §6, §8.1 |
| TR-ACC | §10.1, §10.5, §14 |
| TR-L10N | `IdrFormatter` (§3), `strings.xml` only, `en` → `id` fallback in the `:core:data` mappers (§3) |
| TR-MNT | §1, §2.2, §2.6, §3, §14 |
| TR-DST | §2.4, §2.5, §11.3 |
