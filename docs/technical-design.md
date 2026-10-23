# Hitachi Kiosk — Technical Design

| | |
|---|---|
| Document version | 1.1 (2026-10-23) — updated with the 0.1.0 implementation (build, verify gate, debug bindings, fixtures) |
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
├── benchmark/                  :benchmark     (from 0.3.0)
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
| `:core:domain` | `backend/BackendApi` (E1–E14 in domain types), `payment/PaymentGateway` (E9–E12, delegates to `BackendApi`), `model/` (`Rupiah`, `Product`, `ModifierGroup`, `Combo`, `Category`, `MerchantConfig`, `KioskSettings`, `CartLine`, `Totals`, `PendingOrder`, `PaymentStatus`), `pricing/PricingEngine`, `cart/Cart`, `planner/BudgetPlanner`, `order/OrderSession`, `order/OrderSessionHolder` (interface), `payment/PaymentStateMachine`, `auth/StaffPinVerifier`, `format/IdrFormatter`, `repository/*Repository` (interfaces), `device/` (`CardTerminal`, `PrinterManager`, `PrinterDriver`, `DeviceIdentityProvider`, `ConnectivityMonitor`), `time/Clock`, `id/IdGenerator`, `image/ImagePrefetcher`, `error/DomainError`, `Outcome`, `retryWithBackoff`, `log/KioskLog` (facade + `Redactor`, pure Kotlin) |
| `:core:data` | `api/KioskApi` (internal), `api/interceptor/*`, `api/ApiErrorMapper`, `api/ApiConfig`, `api/DeviceCredentialsProvider`, `api/UnauthorizedHandler`, `backend/DefaultBackendApi`, `backend/BackendPaymentGateway`, `di/NetworkModule`, `dto/*Dto`, `mapper/*`, `db/KioskDatabase`, `db/dao/*`, `db/entity/*Entity`, `prefs/DeviceStore`, `prefs/SettingsStore`, `prefs/SecureTokenStore` (implements the `TokenStore` port), `repository/Default*Repository`, `sync/CatalogSyncRepository`, `sync/CatalogSyncWorker`, `net/DefaultConnectivityMonitor`, `time/ServerClockOffset` |
| `:core:hardware` | `identity/IminDeviceIdentityProvider`, `identity/SerialCache` (own DataStore file), `printer/DefaultPrinterManager`, `printer/escpos/EscPosEncoder`, `printer/render/ReceiptRenderer`, `printer/render/KitchenTicketRenderer`, `printer/driver/{Imin,UsbEscPos,BtEscPos}Driver`, `terminal/*CardTerminal` (per ADR-0003) |
| `:core:designsystem` | `theme/KioskTheme` (+ `KioskColors`, `KioskType`, `KioskShapes`, `KioskSpacing`), `unit/DesignUnit`, `motion/KioskMotion`, `LocalReduceMotion`, `LocalLargeText`, `LocalAccent`, `component/*` (PRD §11.3). `PhotoSlot` draws the tone placeholder and takes the image as a composable slot, so this module depends on Compose only (architecture §3); `:app` fills the slot with Coil's `AsyncImage`. |
| `:core:testing` | `fake/Fake*` (one per domain interface) built on `fake/Script`, `fake/FakeScenario` (api-spec §4), `fake/TestClock`, `fixture/Fixtures` (loads `fixtures/*.json`), `fixture/SampleData` (the same examples as domain objects), `rule/MainDispatcherRule` |
| `:app` | `KioskApp`, `MainActivity`, `navigation/` (`Routes.kt`, `KioskNavHost`, `StartupRouter`), `kiosk/` (`KioskModeController`, `KioskDeviceAdminReceiver`, `BootReceiver`, `BrightnessController`), `inactivity/InactivityController`, `sound/SoundPlayer`, `log/TimberLogSink`, `log/RotatingFileTree`, `image/CoilImagePrefetcher`, `devtools/` (`DeveloperMenu`, `BindingOverrides`), `di/*Module` (including `BackendModule`), `feature/*`; `src/debug` (package `com.hitachi.kiosk.debug`): `DeveloperOptions*`, `DeveloperSettingsStore`, `SwitchableBackendApi`, `MockServerInterceptor` |

`Clock` and `IdGenerator` are domain interfaces (TR-INT-10, TR-INT-11). `SystemClock` returns `kotlin.time.Instant` and a monotonic `elapsedRealtime` for UI timers. `UuidIdGenerator` produces the `Idempotency-Key` values (UUID v4), so tests can script keys.

---

## 4. Application startup and dependency injection

### 4.1 `KioskApp` (`@HiltAndroidApp`)
In `onCreate`, in this order, all off the main thread except the first two:
1. `KioskLog.install(TimberLogSink)`: plants the `RotatingFileTree`, plus a `DebugTree` in debug builds (§12).
2. Debug only: `StrictMode` thread and VM policies with `detectAll()` and `penaltyLog()` (TR-REL-05).
3. WorkManager is initialised on demand through `Configuration.Provider` with `HiltWorkerFactory`. The default initializer is removed in the manifest.
4. Coil: `SingletonImageLoader.Factory` builds the `ImageLoader` with the shared `OkHttpClient` (without the auth interceptor), a 200 MB disk cache in `cacheDir/images`, and a memory cache of 20 % of app memory (TR-DAT-05).
5. Periodic sync is enqueued (§7.2).

### 4.2 Startup routing (FR-001)
`MainActivity` installs the AndroidX `SplashScreen`, with an exit animation set to *none* and a linen background, so the system splash hands straight to the Compose `SplashRoute` (SCR-01). `StartupRouter` runs three things in parallel under `@ApplicationScope`:
- a `delay(3_900)` minimum display time;
- the local load (token, binding, cached config);
- the pending-order check (architecture §5.3).

A background E5/E6 sync starts in the same scope but does not block. Routing waits for the minimum time **and** the local load, with a hard cap of 10 s (`withTimeoutOrNull`), then replaces the back stack (TR-PRF-02, TR-PRF-03). Any failure to read the token is treated as "not activated" (TR-REL-06).

### 4.3 Hilt modules
As in architecture §10. One addition: `CoroutinesModule` provides the dispatchers and `@ApplicationScope CoroutineScope = CoroutineScope(SupervisorJob() + @DefaultDispatcher)`.

**Debug overrides (TD-11).** Hilt cannot replace a production binding outside tests, so `src/main` declares two optional bindings with `@BindsOptionalOf` in `BackendModule`: `BindingOverrides` and `DeveloperMenu`. `BackendModule` binds each port to its real implementation, wrapped by `BindingOverrides` when present. Only `src/debug` (`DeveloperOptionsModule`) provides them, so the release bindings are exactly those of `src/main` and release has no `src/release` override. In 0.1.0 the debug override wraps `BackendApi` in `SwitchableBackendApi`, which sends each call to the real client or to a `FakeBackendApi` with the selected `FakeScenario`; `PaymentGateway` delegates to `BackendApi`, so it follows the same switch. Later milestones add `CardTerminal`, `PrinterManager` and `DeviceIdentityProvider` to `BindingOverrides` in the same way. Switching happens at domain interfaces only, so no DTO or `KioskApi` type leaves `:core:data` (architecture §3). The "mock server" mode is not a fake: it keeps the real client, and a debug-only interceptor (contributed to the `@NetworkInterceptors` set) redirects it to Prism and adds the scenario's `Prefer` header.

---

## 5. Networking implementation

### 5.1 Client (TR-NET-03, TR-NET-05, TR-SEC-02, TR-SEC-03)
`NetworkModule` builds a single `OkHttpClient`:
- `connectTimeout(10 s)`, `readTimeout(15 s)`, `writeTimeout(15 s)`, `callTimeout(30 s)`.
- Interceptors, in the architecture §7 order:
  1. `IdempotencyKeyInterceptor`: copies the key from the `@Tag key: RequestIdempotencyKey` request tag (a data-module wrapper of the domain `IdempotencyKey`) into the header. It never generates a key.
  2. `AuthInterceptor`: adds `Authorization` on every call except the `@Unauthenticated` ones (E1, E14), and `X-Device-Id` on every call once activated, E14 included (api-spec §2.2). It reads a `DeviceCredentialsProvider`; 0.1.0 binds an in-memory store that DEV-014 replaces with the Secure DataStore `TokenStore` and `DeviceStore`.
  3. `HeadersInterceptor`: `X-App-Version`, `Accept-Language: en`.
  4. Extra interceptors from the `@NetworkInterceptors` multibound set (empty in release; the debug build adds the mock-server redirect).
  5. Debug only (from DEV-073): `HttpLoggingInterceptor(level = BODY)` through a `KioskLog` logger that applies the same `Redactor` rules (§12) to headers and JSON bodies: `Authorization`, `deviceToken`, `currentPin`, `currentPinProof`, `newPin` dropped; `qrString`, `reference`, `terminalRef`, `approvalCode` and `paymentId` truncated.
- `connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))`: TLS 1.2 and 1.3 only (TR-SEC-02). The debug mock host adds `CLEARTEXT`.
- `CertificatePinner` for the API host when `BuildConfig.CERT_PINS` is not empty (`staging`/`prod`, TR-SEC-03; see §2.3).
- The `OkHttpClient` and `Retrofit` are qualified `@KioskBackend`, so another client (for example Coil's) can never pick up the one that sends the device token. `KioskApi`, the DTOs and the `Json` instance are `internal` to `:core:data`.
- A `401` is handled in `ApiErrorMapper`, not with an OkHttp `Authenticator`. api-spec v1 defines no refresh endpoint (E1 returns `tokenExpiresAt: null`), so the app has no refresh flow. FR-003 says the token is "refreshed per §10", but §10 defines no mechanism; this is listed as an open item in technical-requirement §14. `SessionInvalidator` clears the token and binding and emits a navigation event to SCR-02 (TR-NET-06).

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
| E2, E5, E6, E10, E14 (GET) | — | Yes | — |
| E3 bind | Yes | Yes | Per bind attempt |
| E4 unbind | — | No | — |
| E7 create order | **Required** | Yes | Held by `OrderSession` from `Submitting` until E7 succeeds, and reused for every retry of the **same body**. A new key whenever the body changes (cart edit, re-pricing after `PRICE_CHANGED`) or after "Back to order" cancelled the previous order (BR-08, TC-086). On success the key is saved in `pending_order` with the `orderId`. |
| E8 cancel order | Yes | Yes | Per order |
| E9 start payment | **Required** | Yes | One key per payment attempt. "Try again" and "switch method" create a new key for the same `orderId`. |
| E11 cancel payment | Yes | Yes | Per payment |
| E12 terminal result | — | No automatic retry (it carries no key). If it fails, the state machine goes to `Unknown` and keeps polling E10 for 60 s; ADR-0003 decides whether E12 needs a key (api-spec first). | — (only if ADR-0003 uses E12) |
| E13 change PIN | Yes | No (user-driven) | Per attempt |

### 5.4 ETag and server time
- `CatalogSyncRepository` stores `ETag` values beside the Room data and sends them as `If-None-Match`. A `304` keeps the cache (TR-NET-09).
- `ServerClockOffset` records `offset = serverDate − clock.now()` from the `Date` header of every successful response, keeping the value from E9. The QR expiry instant on the device is `expiresAt − offset`. The countdown ticks on the monotonic clock (TR-INT-03).

---

## 6. Persistence implementation

### 6.1 Room `KioskDatabase` (version 1 at 0.3.0; frozen at 1.0.0)

| Table | Key columns | Written by |
|---|---|---|
| `config` | `merchantId` (PK), `etag`, `json` (the E5 body, as serialized domain JSON), `fetchedAt` | Sync |
| `menu_meta` | `merchantId` (PK), `menuVersion`, `etag`, `fetchedAt` | Sync |
| `category` | `id`, `sort`, `name_en`, `name_id`, `tone` | Sync |
| `product` | `id`, `categoryId`, `name_en`, `name_id`, `description_en`, `description_id`, `price`, `kcal`, `badgeType`, `imageUrl`, `available`, `role`, `kidFriendly`, `servesAdults`, `shareable`, `plannerWeight` | Sync |
| `modifier_group`, `modifier_option`, `product_modifier_group` (cross ref) | per api-spec E6 | Sync |
| `combo`, `combo_slot`, `combo_slot_option` | per api-spec E6 | Sync |
| `featured`, `idle_promo` | ordering and image URLs | Sync |
| `pending_order` | `orderId` (PK), `orderNumber`, `paymentId?`, `method?`, `idempotencyKey`, `totalsJson`, `createdAt` | `OrderRepository` (architecture §5.3) |

- **Atomic replace (TR-DAT-03):** `CatalogDao.replaceAll(...)` is one `@Transaction`. It writes the `config` row and deletes and inserts all menu tables and `menu_meta`, so E5 and E6 land together (architecture §8). A parse or insert failure rolls back and keeps the old menu (TR-REL-07).
- Config is stored as one JSON column because it is read whole and never queried by field. This avoids a schema migration every time a config field is added.
- Money columns are `INTEGER` (`Long`). There is no `REAL` column anywhere (TR-DAT-01).
- `exportSchema = true` with the `androidx.room` Gradle plugin `schemaDirectory("$projectDir/schemas")`. Before 1.0.0, `fallbackToDestructiveMigrationFrom(...)` is allowed only for versions whose change touched cache tables. `pending_order` changes always get a real `Migration` (TR-DAT-04).

### 6.2 DataStore files

| File | Type | Keys |
|---|---|---|
| `device.preferences_pb` | Preferences | `deviceId`, `kioskCode`, `kioskName`, `merchantId`, `merchantName` |
| `identity.preferences_pb` (owned by `:core:hardware`) | Preferences | `serialNumber` (cache, §9.1) |
| `settings.preferences_pb` | Preferences | `orderTypes`, `idleTimeoutSec`, `brightness`, `soundEnabled`, `reduceMotion`, `largeText`, `upsellEnabled`, `paymentMethods`, `qrExpiryMin`, `roundingStep`, `printers` (JSON list of `PrinterConfig`), `receiptToggles`, `receiptFooter`, `paperWidth`, `density`, `autoSync`, `lastSyncAt` |
| `pin_lockout.preferences_pb` | Preferences | `failedAttempts`, `lockedUntilElapsed`, `lockedUntilWall` (TR-SEC-05) |
| `secure.pb` | Typed DataStore `SecureStore { deviceToken }` | Encrypted (§6.3) |

Each store is exposed as a `Flow` of a domain model (`KioskSettings`, etc.). Writes are `suspend` and run on `@IoDispatcher`.

### 6.3 Secure DataStore (TR-SEC-01) — TD-05
- Master key: `AndroidKeysetManager.Builder().withSharedPref(context, "kiosk_keyset", "kiosk_keyset_prefs").withKeyTemplate(KeyTemplates.get("AES256_GCM")).withMasterKeyUri("android-keystore://kiosk_master_key").build()`. Tink keeps the **wrapped** keyset in that small preferences file. It is read once, at first token access, on `@IoDispatcher`. The token itself is never in SharedPreferences. This is Tink's standard keyset storage and holds only wrapped key material, so it does not reintroduce what ADR-0001 rejected (`EncryptedSharedPreferences` holding the data, read synchronously on the main thread).
- Store: `DataStoreFactory.create(serializer = AeadSerializer(aead, SecureStoreSerializer, associatedData = "kiosk.secure.v1".encodeToByteArray()), produceFile = { context.dataStoreFile("secure.pb") })` using `androidx.datastore:datastore-tink`. `SecureStoreSerializer` is a kotlinx-serialization JSON serializer of `SecureStore`.
- A failure such as `GeneralSecurityException`, a lost Keystore key or a `CorruptionException` is caught in `SecureTokenStore`. It deletes `secure.pb` and the keyset, logs `token_store_reset`, and reports "no token" → SCR-02 (FR-001, TR-REL-06).

---

## 7. Catalog sync and connectivity

### 7.1 `CatalogSyncRepository.sync()` (FR-028)
1. Single flight: a `Mutex` makes concurrent triggers share one run.
2. E5 with `If-None-Match`, then E6 with `If-None-Match`. Each is retried per §5.3.
3. Map DTOs → domain, which validates them. A product with no `role` defaults to MAIN and is logged as a data warning (DEV-044).
4. If `OrderSessionHolder.isOrderActive`, keep the parsed result in memory as `pendingCatalog` and apply it when the session returns to `Idle` (A-29). Otherwise write it to Room at once (§6.1).
5. After a successful write, call the domain port `ImagePrefetcher.prefetch(urls)` for every product and idle-promo image. `:app` implements it with Coil (`ImageRequest` with `memoryCachePolicy(DISABLED)`, disk only), so `:core:data` does not depend on Coil.
6. Update `lastSyncAt`. Emit `SyncState` for Settings → Device & network.

### 7.2 Triggers
- On binding (blocking, with the FR-004 states).
- On startup (non-blocking).
- On "Sync now".
- On a connectivity transition offline → online.
- `PeriodicWorkRequest<CatalogSyncWorker>(15, MINUTES)` with `Constraints(requiredNetworkType = CONNECTED)`, unique name `catalog-sync`, policy `UPDATE`. It is cancelled when Auto-sync is off and re-enqueued when it is turned back on.

### 7.3 `DefaultConnectivityMonitor` (TR-NET-11)
`combine(validatedNetworkFlow, healthFlow)` → `StateFlow<Connectivity>` (`Online`, `Offline`, `Checking`).
- `validatedNetworkFlow` comes from `ConnectivityManager.registerDefaultNetworkCallback`, using `NET_CAPABILITY_VALIDATED`.
- `healthFlow` probes E14 on every network change and every 30 s while not `Online`. While `Online`, a `Network` error on any real call triggers an immediate E14 re-probe.
- E14 counts as healthy only when `status` and `components.paymentGateway` are `OK`. A gateway that is `DEGRADED` or `DOWN` makes checkout unavailable, the same as offline (api-spec E14); the Connection test reports it separately (FR-027).
- `Online` needs both signals. The flow is debounced by 1 s so a flapping network doesn't flicker the banner (TR-PRF-14 allows 5 s).

---

## 8. Order, pricing, Budget Plan and payment

### 8.1 Money and pricing (TR-DAT-01, TR-DAT-02)
- `@JvmInline value class Rupiah(val value: Long)` with checked `plus`, `minus` and `times(Int)` (`Math.addExact`, `Math.multiplyExact`).
- `PricingEngine.price(lines, config, settings): Totals` is a pure function implementing FR-013 in this order:
  1. `subtotal` = Σ line totals.
  2. `discount` = the best `THRESHOLD_FIXED` promo, or 0.
  3. `tax = roundHalfUp((subtotal − discount) × taxRate)`, computed in integers from E5 `tax.rateBasisPoints` (`1000` = 10 %): `(base × bp + 5_000) / 10_000`.
  4. `total` = subtotal − discount + tax, rounded up to `roundingStep`.
  5. `rounding` = the difference.

  There is no floating point at any step. A rounding step of 0 ("None") skips step 4.
- `docs/pricing-cases.md` (0.4.0) is the golden table. `kotest-property` checks the invariants over 1,000 random carts (TC-061): `total ≥ 0`; `total ≥ subtotal − discount`; when the step is > 0, `total % step == 0` and `0 ≤ rounding < step`; tax equals the half-up rule. The total is **not** monotonic in quantity, because crossing the promo threshold can lower it (FR-032 AC2 depends on that).

### 8.2 Cart and `OrderSession`
`Cart` is an immutable list of `CartLine(productId | comboId, optionSelections, qty, unitPrice)`. Two lines are equal when their product and selections are equal (merge rule, FR-010). Limits of 20 per line and 30 lines are enforced in `Cart` (BR-05). `OrderSession` follows the state machine in architecture §5.1. It exposes `StateFlow<OrderState>` and owns:
- the `idempotencyKey` for E7;
- the `PlanTag`;
- the `upsellShown` flag;
- the catalog version snapshot.

### 8.3 `BudgetPlanner` (FR-032, TR-PRF-08) — TD-06
A pure function: `plan(budget, adults, children, menu, config, options): PlanResult`. The approach meets the 100 ms target without trying every combination:

1. **Candidate units.** Per adult: `(main, drink)` pairs, plus combos (`serves = 1` covers one adult, `serves = 2` covers two). Per child: `(kidFriendly main, drink)` pairs. If no main is kid-friendly, the child gets the cheapest main and the result is flagged "Kids' menu not available" (BR-14, D-28). Optional per person: one side or one dessert. Unavailable items and products with `servesAdults > 1` are excluded. Each planned item takes its required groups' default option, or the first option when a group has none (A-42).
2. **Reachable subtotals.** Subtotals are counted in units of the greatest common divisor of all candidate prices (Rp 1.000 on the sample menu, at least Rp 100 in practice). If a merchant's prices make that unit smaller than Rp 100, the state count grows; the cap below handles it. A dynamic program over the people adds each person's unit prices to a set of reachable subtotals. It keeps one best composition per subtotal, chosen by the FR-032 tie-breaks that apply within a subtotal: variety, then `plannerWeight`, then featured. With 10 people and a sample menu, that is at most about 30,000 states.
3. **Final totals.** Each reachable subtotal goes through `PricingEngine` to get its final total (promo, tax, rounding). That makes the threshold promo and the rounding exact rather than approximate (BR-15).
4. **Selection.**
   - If any total equals the budget, pick it (exact match, AC2, AC8).
   - Otherwise pick the closest total ≤ budget and the closest total in (budget, budget × (1 + `overCapPercent`/100)].
   - Whenever several subtotals give the same final total, the higher subtotal wins first (FR-032 ranking rule 1), then the per-subtotal tie-breaks above.
   - Then apply the edge cases: budget below the cheapest plan → "Need Rp X more"; budget at or above the most generous plan → "More than enough"; no plan within the over cap → under plan only.
5. **Customize rebalance.** A swap fixes the swapped person's main and drink and reruns step 2 over the side and dessert choices only. That is the "sides and desserts rebalance" rule in FR-032.

Verification: AC1–AC11 as unit tests on the sample menu, AC8 as a `kotest-property` test over Rp 50.000–300.000 in Rp 100 steps, and AC6 in `:benchmark` (TC-168). If the DP state count grows beyond the target for a real merchant menu, cap the candidate pairs per person at the 12 cheapest plus the 4 most featured and record that in an ADR.

### 8.4 `PaymentStateMachine` implementation (FR-017–FR-019)
The states and transitions are those in architecture §5.2. Implementation notes:
- `start(method)`: E9 with a new key, then `AwaitingCustomer`. The QR path renders the QR (§8.5). The card path calls `CardTerminal.startSale(amount, terminalRef)` with both values from the E9 card response (api-spec E9); the amount equals the backend order total (TR-INT-04).
- **Polling:** a `while (isActive)` loop with `delay(2_000)` (injected `Clock`/test dispatcher) calling E10. It ends on `PAID`, `FAILED`, `EXPIRED` or `CANCELLED` (TR-NET-10). A `Network` error moves to `Unknown`. `Unknown` keeps polling and fails after 60 s with no backend answer, showing the reason "checking" with **no** "not charged" note (TR-INT-06).
- **QR expiry:** when the device-side expiry instant (§5.4) passes, call E11. If E11 returns 409, re-read E10 first, because the payment might be `PAID`. Otherwise go to `Failed(EXPIRED)`.
- **Card timeout:** `withTimeout(90 s)` around the terminal result, then `CardTerminal.cancel()` and E11 → `Failed(TERMINAL_TIMEOUT)` (TR-INT-05).
- `Processing` is held for at least 800 ms before `Paid` (FR-017).
- Each state change updates `pending_order` (`paymentId`, `method`), so recovery works (TR-REL-02).
- The machine runs in `@ApplicationScope`, so a configuration change or a navigation event doesn't cancel it. `CheckoutViewModel` only observes it.

### 8.5 QR rendering (TR-INT-02) — TD-07
`QRCodeWriter().encode(qrString, QR_CODE, size, hints)` with `ERROR_CORRECTION = H` and `MARGIN = 4`. The `BitMatrix` is drawn on a Compose `Canvas` as rectangles, one per module, with no bitmap scaling. Level H (≥ the required M) is used because the merchant mark always covers the centre. The mark is limited to ≤ 20 % of the QR width (about 4 % of the area), which stays well inside H's error-correction capacity. TC-079 renders the composable to a bitmap with the mark, decodes it with ZXing and compares it with `qrString`.

### 8.6 Process-death recovery
This follows the table in architecture §5.3. `StartupRouter` reads `pending_order` before routing. The recovery calls (E10, then E8 if needed) use the same retry policy. If the network is down, the app shows Idle and retries recovery on the next `Online` transition, so a pending order is never silently dropped.

---

## 9. Hardware implementation

### 9.1 Device identity (TR-INT-07)
`IminDeviceIdentityProvider` tries, in order:
1. the iMin device SDK serial API;
2. `Build.getSerial()`. Since Android 10 this needs the caller to be device owner **and** hold `READ_PHONE_STATE`. A device owner can grant that to itself with `setPermissionGrantState`, but `READ_PHONE_STATE` is not in the PRD §12.3 permission list. If the iMin SDK path works (DEV-012), the fallback and the permission are dropped. Otherwise ADR-0002 records the need and the PO approves adding the permission (A-02, R-3).

The first non-blank value is cached by `SerialCache` in `:core:hardware`'s own `identity.preferences_pb`, so the module never touches a `:core:data` store (architecture §3). The cache is a speed-up only: the value is re-read and compared at every activation. The final order and SDK call are recorded in ADR-0002 (DEV-012).

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
The interface is `CardTerminal { fun startSale(amount: Rupiah, ref: String): Flow<TerminalEvent>; suspend fun cancel(); suspend fun selfTest(): Outcome<Unit> }` (DEV-049). The real adapter waits on OQ-03 and ADR-0003. Whatever the transport (USB serial, LAN or cloud), the adapter only turns vendor events into `TerminalEvent` (`Prompted`, `Approved(ref, tid)`, `Declined(code)`, `Cancelled`, `Error`). If the terminal reports to the kiosk rather than to the gateway, `PaymentStateMachine` relays `Approved` or `Declined` through E12. In every case it still waits for E10 `PAID` (TR-SEC-12).

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
A root `Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); controller.onUserActivity() } } }` sees every touch without consuming it. `InactivityController` (in `:app`, using the injected `Clock`) keeps a deadline. It is enabled on Order type, Menu, Combo, Item, Upsell, Payment and Budget Plan (FR-023: SCR-05…SCR-10 and SCR-19, architecture §11), and suspended in `Paying` and `Paid`. When the deadline passes, it shows the overlay, except on Order type with an empty cart, where it returns straight to Idle (A-23). 15 s later it calls `OrderSession.timeOut()`. Staff settings use their own 120 s controller instance.

### 10.4 Lifecycle and recomposition
- ViewModels collect with `collectAsStateWithLifecycle()`.
- `UiState` classes are `@Immutable` data classes holding read-only `List`s. Compose compiler reports confirm they are stable, so `kotlinx.collections.immutable` is not needed.
- Lazy grids use stable `key`s (product ID).
- Images are requested at their display size (`size(Size(widthPx, heightPx))`) so full-resolution bitmaps are never decoded (TR-PRF-05, TR-REL-01).

### 10.5 Sound and brightness
- `SoundPlayer` loads the three short OGG files into a `SoundPool(maxStreams = 2)` with `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SONIFICATION)` at startup, so they play on the media stream at the system volume (FR-030), and only when the setting is on (TR-ACC-07).
- `BrightnessController` sets `window.attributes.screenBrightness` to 0.3 / 0.6 / 1.0 (A-26).

---

## 11. Kiosk mode and platform integration

### 11.1 Manifest (TR-SEC-08, TR-SEC-09, TR-SEC-10)
- `MainActivity`: `exported=true`, `screenOrientation="portrait"`, `launchMode="singleTask"`, `lockTaskMode="if_whitelisted"`. Intent filters for `MAIN/LAUNCHER` and `MAIN/HOME/DEFAULT`.
- `KioskDeviceAdminReceiver`: `exported=true`, `permission="android.permission.BIND_DEVICE_ADMIN"`, `res/xml/device_admin.xml`.
- `BootReceiver`: `exported=true` with `RECEIVE_BOOT_COMPLETED` only. It relaunches `MainActivity` as a safety net, since the persistent home activity normally relaunches it anyway.
- No other exported component. `WorkManager`'s and `ProfileInstaller`'s own receivers are merged with their library defaults (permission-guarded) and listed in the release check allow-list.
- `android:allowBackup="false"`, no `dataExtractionRules`, `usesCleartextTraffic` not set (the network security config governs cleartext).
- Permissions are exactly PRD §12.3:
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `RECEIVE_BOOT_COMPLETED`;
  - `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` (with `usesPermissionFlags="neverForLocation"`);
  - `BLUETOOTH` and `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`;
  - `ACCESS_FINE_LOCATION` (SSID only, requested from Device & network);
  - `<uses-feature android:name="android.hardware.usb.host" android:required="false"/>`;
  - no `FOREGROUND_SERVICE`, because printing runs in `@ApplicationScope` (A-39);
  - `READ_PHONE_STATE` only if ADR-0002 keeps the `Build.getSerial()` fallback and the PO approves it (§9.1).

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

  Unit tests cover every rule.
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
| Fakes (TR-INT-10) | `:core:testing` `Fake*`. Each one takes a script (`Queue<Outcome>`) or a mode enum, plus optional delays driven by the test dispatcher. |
| Fixtures (TR-NET-02) | `core/testing/src/main/resources/fixtures/*.json` (31 files, byte-identical to the `api-spec.md` examples), written by `./gradlew extractApiFixtures` (`docs/api/tools/extract_fixtures.py`), which shares its parser `spec_examples.py` with `gen_openapi.py`. The `checkApiFixtures` step of `verify` fails on a diff (§2.6). `SampleData` holds the same examples as domain objects, and the `:core:data` contract tests check that each parsed fixture equals it. |
| Contract and network tests | `mockwebserver3` + `mockwebserver3-junit4` serve the fixtures and inject faults (delays, 5xx, disconnects) (TR-NET-03, TR-NET-04). |
| Room and DataStore | Robolectric with an in-memory Room database. `MigrationTestHelper` from 1.0.0. A DataStore on a temporary folder. |
| Flows and time | `runTest` + `StandardTestDispatcher`, `TestClock` implementing the domain `Clock`, Turbine. |
| Property tests | `kotest-property` `checkAll(iterations = 1_000, …)` inside JUnit 4 `@Test` functions (TC-061, FR-032 AC8). |
| Screenshot tests | Roborazzi on Robolectric (SDK 36, §16), goldens committed under each module's `src/test/screenshots`. A 1080 × 1920 px screen at 160, 240 and 320 dpi is `@Config(qualifiers = "w1080dp-h1920dp-mdpi")`, `"w720dp-h1280dp-hdpi"` and `"w540dp-h960dp-xhdpi"`. One test per `S_*` board and screen artboard, plus larger-text variants (TR-MNT-08). |
| Instrumented UI | `HiltTestRunner`, `@TestInstallIn` replacing the data and hardware modules with fakes. `createAndroidComposeRule<MainActivity>()` with `enableAccessibilityChecks()` (TR-ACC-01). |
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

---

## 16. Risks in this design

| Risk | Mitigation |
|---|---|
| detekt 1.23.8 may not parse Kotlin 2.4 sources | **Closed in DEV-001**: it does, without type resolution. |
| Robolectric may not support API 37 yet | **DEV-001 result**: Robolectric 4.17 starts API 37, but Compose draws nothing there, so screenshot tests run on SDK 36 (`robolectric.properties`). Instrumented tests cover API 37. Revisit with the next Robolectric release. |
| KSP 2.3.12 with Kotlin 2.4.21 is unconfirmed | **Closed in DEV-001**: it works, with Hilt 2.60.1 and AGP 9's built-in Kotlin. |
| Compose's transitive Espresso 3.5.0 fails on API 37 (`InputManager.getInstance()` was removed) | Constrained to Espresso 3.7.0 in `kiosk.compose` (technology-stack §4). |
| Android 16+ may ignore `screenOrientation="portrait"` on large screens (sw ≥ 600 dp; a 1080 × 1920 kiosk at 240 dpi is 720 dp wide), and `targetSdk 37` removes the opt-out | The kiosk is mounted in portrait and runs in lock task, so the window is portrait anyway. Check on the iMin device in DEV-012 (0.2.0) and record the result in ADR-0002 or a new ADR if the app must handle a landscape window (NFR-002, TR-PLT-05). |
| iMin SDK distribution (JitPack) and APIs vary by model | Spikes DEV-012 and DEV-056 on the real device (OQ-01). Drivers sit behind interfaces. |
| EDC transport unknown | `CardTerminal` interface and fake (OQ-03, R-1). |

---

## 17. Traceability: technical requirements → design

| TR area | Design sections |
|---|---|
| TR-PLT | §2.2, §2.3, §11 |
| TR-DSP | §10.1 |
| TR-PRF | §4.2, §8.3, §9.2, §10, §13 |
| TR-REL | §4.1, §5.3, §6.1, §8.4, §8.6, §9.2 |
| TR-SEC | §2.3, §2.5, §5.1, §6.3, §11.1, §12; PIN check: `StaffPinVerifier` in `:core:domain` (`PBKDF2WithHmacSHA256` via `javax.crypto.SecretKeyFactory`, iterations and salt from E5, `MessageDigest.isEqual`; `PLAIN_MOCK_ONLY` accepted only when `BuildConfig.DEBUG`, which `:app` passes in as a flag) |
| TR-PRV | §5.1, §12 |
| TR-NET | §5, §7.3, §14 |
| TR-INT | §8.4, §8.5, §9 |
| TR-DAT | §6, §8.1 |
| TR-ACC | §10.1, §10.5, §14 |
| TR-L10N | `IdrFormatter` (§3), `strings.xml` only, `en` → `id` fallback in the `:core:data` mappers (§3) |
| TR-MNT | §1, §2.2, §2.6, §3, §14 |
| TR-DST | §2.4, §2.5, §11.3 |
