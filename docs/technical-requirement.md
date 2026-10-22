# Hitachi Kiosk — Technical Requirements

| | |
|---|---|
| Document version | 1.0 (2026-10-22) |
| Answers | *What must the system satisfy technically, and how is each requirement measured?* |
| Derived from | [`product-requirement.md`](product-requirement.md) (NFR-, FR-, BR-, D-, A-) and [`api/api-spec.md`](api/api-spec.md) (E1–E14) |
| Satisfied by | [`technical-design.md`](technical-design.md) (how), [`architecture.md`](architecture.md) (structure), [`technology-stack.md`](technology-stack.md) (with what) |
| Verified by | [`testing-plan.md`](testing-plan.md) (TC-) |

## 0. How to read this document

- Each requirement has an ID `TR-<AREA>-nn`. A **Source** column traces it to the PRD or the API spec. A **Verification** column names the test case (TC-) or tool that proves it.
- This document **makes the PRD measurable**. It never changes a PRD value. If a number here differs from the PRD, the PRD wins and this document is wrong. Rows marked **(derived)** are not stated in the PRD in so many words: they are the technical consequence of a PRD requirement, and the Source column says which one.
- Normative words: **must** = required for 1.0.0; **should** = expected, and a deviation needs an ADR.
- "Target device" means the iMin kiosk in OQ-01. Performance numbers are measured on it unless the row says otherwise. Emulator numbers are trends only (testing-plan §2.3).

| Area | Prefix | Section |
|---|---|---|
| Platform and compatibility | PLT | §1 |
| Display and layout | DSP | §2 |
| Performance | PRF | §3 |
| Reliability and recovery | REL | §4 |
| Security | SEC | §5 |
| Privacy and logging | PRV | §6 |
| Network and backend integration | NET | §7 |
| Payment and hardware integration | INT | §8 |
| Data and persistence | DAT | §9 |
| Accessibility and motion | ACC | §10 |
| Localization and formatting | L10N | §11 |
| Maintainability and quality gates | MNT | §12 |
| Distribution and operations | DST | §13 |

---

## 1. Platform and compatibility

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-PLT-01 | `minSdk` must be 30. `compileSdk` and `targetSdk` must be the latest stable API level at the 0.1.0 start and at least 35. On 2026-10-22 that is **API 37** (Android 17). | NFR-001, D-04 | Build config review; TC-140 |
| TR-PLT-02 | The app must be written in Kotlin with a Jetpack Compose UI. Material 3 may be used only as a foundation under the custom design system. No XML layouts and no Fragments. | NFR-001, D-24 | Code review; Lint |
| TR-PLT-03 | The app must build and pass every automated test on an API 30 emulator and at the target device's API level. | NFR-001 | TC-140 |
| TR-PLT-04 | The app must work on API 30–36 emulators and on the target iMin device. **(derived)** Because `targetSdk` is 37 (TR-PLT-01), an API 37 emulator is added to the matrix. | NFR-016, NFR-001 | TC-159, DEV-092 |
| TR-PLT-05 | The app must be a single Activity, locked to portrait, with `FLAG_KEEP_SCREEN_ON` while in lock task. | NFR-002, FR-029 | TC-126, manifest review |
| TR-PLT-06 | The app must run as **device owner** in lock-task mode. When it is not device owner, it must fall back to screen pinning and show a warning on Settings home. | FR-029, D-22 | TC-126–TC-128 |
| TR-PLT-07 | **(derived, from D-22 MDM/sideload distribution)** The app must not depend on Google Play Services or the Play Store, because the kiosk may ship without them. | D-22, PRD §3.2 | Dependency audit (TC-149) |
| TR-PLT-08 | **(derived)** A clean checkout must build with JDK 21, the Android SDK and the committed Gradle wrapper alone. Dependencies resolve only from Google Maven, Maven Central, the Gradle Plugin Portal and JitPack (restricted to the iMin group). | NFR-011, AC-0.1-1 | Clean build on a fresh machine |

## 2. Display and layout

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-DSP-01 | All layout sizes must be expressed in design pixels and converted with one scale factor `s = screenWidthDp / 1080` (`Int.dpx`, `Int.spx`). Raw `dp`/`sp` may appear only inside `:core:designsystem`. | NFR-002, PRD §11.2 | DEV-003 unit test; detekt rule / review |
| TR-DSP-02 | Supported displays: 1080 × 1920 up to 1200 × 1920 portrait. Other aspect ratios must letterbox vertically. | NFR-002, A-35 | TC-141 |
| TR-DSP-03 | Screenshots at 1080 × 1920 and 160, 240 and 320 dpi must render identical layouts. | NFR-002 | TC-141, TC-180 |
| TR-DSP-04 | Larger text must scale customer-facing text by 1.15 with no clipped or overlapping text on Menu, Item, Combo and Your order. | FR-026 AC3, NFR-007 | TC-116, TC-180 larger-text variants |
| TR-DSP-05 | Light theme only. No dark theme resources. | D-02, PRD §3.2 | Resource review |

## 3. Performance

Rows verified by a benchmark TC (TC-142, TC-143, TC-144, TC-168) are measured on the target device with a release-like build (R8 on, baseline profile installed). The other rows are asserted by UI or end-to-end tests with fakes on the emulator, and spot-checked on the target device at 0.9.0.

| ID | Requirement | Target | Source | Verification |
|---|---|---|---|---|
| TR-PRF-01 | Cold start to the first splash frame | ≤ 1.0 s | NFR-003 | TC-142 (Macrobenchmark, ×10, median) |
| TR-PRF-02 | Splash to Idle with a bound merchant and a warm cache | 3.9–5.0 s | NFR-003, FR-001 AC1 | TC-142 |
| TR-PRF-03 | Splash to Idle when offline, using the cache | ≤ 10 s | FR-001 AC2 | TC-003 |
| TR-PRF-04 | Category switch renders the new grid | ≤ 300 ms | NFR-003, FR-009 AC1 | TC-034, TC-144 |
| TR-PRF-05 | Frame rate on customer screens during the scripted J1 journey | 60 fps, ≤ 1 % janky frames, P90 frame time ≤ 16 ms | NFR-003 | TC-143 (Macrobenchmark `FrameTimingMetric`, plus JankStats in the benchmark variant) |
| TR-PRF-06 | Tap feedback (press state visible) | ≤ 100 ms | NFR-003 | TC-144 |
| TR-PRF-07 | Cart quantity change updates the line and all summary values | within one frame | FR-012 AC1 | TC-050 |
| TR-PRF-08 | Budget Plan: both plans, the exact-match search, and a Customize rebalance for the 10-person maximum on the sample menu (23 products + 4 combos) | ≤ 100 ms | FR-032 AC6 | TC-168 (benchmark) |
| TR-PRF-09 | Activation round trip on a normal network | ≤ 3 s | FR-003 AC1 | TC-008 |
| TR-PRF-10 | Merchant binding to Idle (menu ≤ 200 items, images lazy) | ≤ 10 s | FR-004 AC1 | TC-016 |
| TR-PRF-11 | Backend `PAID` seen → Complete screen | ≤ 3 s | FR-017 AC3 | TC-075 |
| TR-PRF-12 | Complete screen → receipt and kitchen ticket printed | ≤ 5 s per connection type | FR-021 AC1 | TC-091 |
| TR-PRF-13 | Hardware or network status change visible in settings | ≤ 5 s | FR-024 AC4, FR-031 AC1 | TC-130 |
| TR-PRF-14 | Offline detected → banner and Pay disabled | ≤ 5 s | FR-028 AC1 | TC-122 |
| TR-PRF-15 | Network restored → Pay re-enabled | ≤ 10 s | FR-028 AC2 | TC-123 |
| TR-PRF-16 | "Restart app" → Idle | ≤ 20 s | FR-027 AC4 | TC-120 |
| TR-PRF-17 | Device boot → app in the foreground with no user action | ≤ 60 s | FR-029 AC1 | TC-127 |
| TR-PRF-18 | Error state → button tappable again | ≤ 300 ms | FR-003 AC2 | TC-010 |
| TR-PRF-19 | **(derived)** The release APK must ship a baseline profile covering startup and the Idle → Menu → Item journey, installed through ProfileInstaller on sideloaded builds. | — | NFR-003, NFR-014 | DEV-031, APK inspection |
| TR-PRF-20 | Idle slideshow: at most one animated layer at a time | — | NFR-015 | Layout Inspector review, soak |

## 4. Reliability and recovery

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-REL-01 | 72 h soak (scripted order every 3 min, idle in between): 0 crashes, 0 ANRs, Java heap growth ≤ 20 MB from hour 1 to hour 72, no leaked Activities (LeakCanary in debug). | NFR-004, OBJ-4 | TC-145 |
| TR-REL-02 | After process death during a payment, the app must recover with no manual action, following the `pending_order` table in architecture §5.3. A paid order must reach Complete and print. | FR-016, A-11 | TC-073 |
| TR-REL-03 | Any number of Pay taps, and a 5xx followed by a retry, must produce **exactly one** backend order per checkout (`Idempotency-Key` reused on retry). | FR-016 AC1–AC2, BR-08 | TC-069, TC-070 |
| TR-REL-04 | Double taps on "Log in", "Add combo" and order-type cards must produce exactly one effect. | FR-003 AC3, FR-007 AC2, FR-011 AC3 | TC-012, TC-026, TC-049 |
| TR-REL-05 | **(derived)** No disk or network I/O on the main thread. Debug builds enable `StrictMode` (thread and VM policies) with log penalties, and the debug instrumented journey suite (J1–J8) must show no violations from app code. The soak itself runs a release build (TC-149), where StrictMode is off. | NFR-010, NFR-004 | StrictMode log review in the soak |
| TR-REL-06 | Corrupt or undecryptable local storage must be treated as "not activated" and logged locally. The app must never crash on it. | FR-001, architecture §6 | TC-004 |
| TR-REL-07 | A failed or malformed menu sync must keep the previous cache intact. A sync must never change a cart in progress. | FR-028, DEV-021/022 | DAO and sync integration tests |
| TR-REL-08 | A print failure must never block Complete or change the order's paid status. One retry, then a fallback message. | FR-020 AC4, FR-021 | TC-095 |
| TR-REL-09 | In-place upgrade from the previous milestone build must keep activation, binding and settings, and **(derived, from FR-016 recovery)** any pending order. | NFR-014 | TC-158 |

## 5. Security

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-SEC-01 | The device token must be stored encrypted with AES-256-GCM (Tink AEAD) in a DataStore file, with the keyset wrapped by a non-exportable Android Keystore key. It must never be logged or stored in plain text. | NFR-005, ADR-0001 | TC-146 |
| TR-SEC-02 | All backend traffic must use HTTPS with TLS 1.2 or later. Cleartext must be disabled by `network_security_config.xml`. The only exception is the mock host in debug builds. | NFR-005, api-spec §2.1 | TC-147, manifest and config review |
| TR-SEC-03 | `staging` and `prod` builds must pin the backend's public keys (SPKI SHA-256) with a primary and a backup pin. Pins must not be committed in plain text alongside secrets. They come from Gradle properties per environment. | NFR-005, A-36 | TC-147 (MITM proxy test fails to connect) |
| TR-SEC-04 | The staff PIN must be checked on the device against the synced salted hash (`PBKDF2WithHmacSHA256`, iterations from config) using a constant-time comparison. The plain PIN must never be stored, logged, or sent except in E13. `PLAIN_MOCK_ONLY` hashes must be rejected in release builds. | FR-024, NFR-005, api-spec E5/E13 | TC-105, TC-149 |
| TR-SEC-05 | Five consecutive wrong PINs must lock staff access for 5 min. The lockout must survive process death and reboot. | FR-024 AC2, D-20 | TC-106, TC-107 |
| TR-SEC-06 | The app must never see card numbers, PINs or track data. The card is handled by the EDC terminal only. | FR-018, NFR-005 | Code review, TC-083 |
| TR-SEC-07 | Release builds must be R8-minified with resource shrinking, `debuggable = false`, signed v2 + v3, and must contain no class from `src/debug`, no fake, no switchable wrapper and no simulate-bar string. | NFR-005, FR-019 AC3, NFR-014 | TC-087, TC-148 (`scripts/check-release-apk.sh`) |
| TR-SEC-08 | The app's own exported components must be limited to the launcher and home `MainActivity`, the `DeviceAdminReceiver` (guarded by `BIND_DEVICE_ADMIN`) and the boot receiver. Components merged from AndroidX libraries (WorkManager, ProfileInstaller) must keep their permission guards and appear on an explicit allow-list. | NFR-005 | TC-148 (manifest check) |
| TR-SEC-09 | Permissions must be limited to PRD §12.3. Runtime permissions (Bluetooth connect/scan, location for the SSID) must be requested only inside staff settings, with a rationale. | NFR-005, PRD §12.3, FR-022 AC4 | TC-098, TC-148 |
| TR-SEC-10 | **(derived)** `android:allowBackup="false"` and no data-extraction rules. Backups would copy settings without the Keystore key that decrypts the token, so a restored device would hold unusable secrets. | NFR-005, architecture §6 | Manifest check |
| TR-SEC-11 | The signing keystore and its passwords must never be committed. They come from environment variables or `local.properties`. | conventions §8, DEV-095 | Repository review |
| TR-SEC-12 | A payment is paid **only** when the backend reports `PAID`. A terminal approval alone must never complete an order. | A-15, BR-07, FR-018 | TC-080–TC-083 |

## 6. Privacy and logging

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-PRV-01 | No analytics, crash-reporting, telemetry or ad SDKs, directly or transitively. | D-23, NFR-006 | Dependency audit (TC-149) |
| TR-PRV-02 | The app must collect no customer personal data. | NFR-006 | Review |
| TR-PRV-03 | Logs must go through `KioskLog`. Logs must never contain the PIN, must truncate payment references to the last 4 characters, and must contain no customer data. | NFR-006, FR-031 | TC-149, redaction unit tests |
| TR-PRV-04 | Local logs must rotate: the log directory never exceeds 10 MB and keeps at most 7 days. | FR-031 AC2 | TC-131 |
| TR-PRV-05 | HTTP body logging is allowed only in debug builds, and it goes through the same redaction. | architecture §7 | TC-148 |

## 7. Network and backend integration

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-NET-01 | The app must implement endpoints E1–E14 exactly as specified in `api-spec.md`. `openapi.yaml` is generated from it and never edited by hand. | D-06, PRD §10 | DEV-006 contract tests |
| TR-NET-02 | Test fixtures must be byte-identical to the JSON examples in `api-spec.md`. A spec change that breaks parsing must fail the verification gate. | DEV-006, testing-plan §2.2 | Contract test suite |
| TR-NET-03 | Timeouts: connect 10 s, read 15 s. | NFR-010 | TC-154 |
| TR-NET-04 | Retries (up to 3 retries with 1 s, 2 s, 4 s backoff, so at most 4 attempts) are allowed only for GETs and for mutations that carry an `Idempotency-Key`. The key comes from the caller and is reused on every retry. | NFR-010, FR-016 | TC-070, TC-154 |
| TR-NET-05 | Every request must carry `X-App-Version` and `Accept-Language: en`. Authenticated calls (all except E1 and E14) must carry `Authorization: Bearer <token>`. Every call after activation, E14 included, must carry `X-Device-Id`. | api-spec §2.2, D-29 | Interceptor unit tests |
| TR-NET-06 | HTTP 401 on any authenticated call must clear the token and the binding and route to SCR-02. | PRD §10.1, FR-003 | TC-013 |
| TR-NET-07 | Error bodies `{code, message, details}` must be mapped to `DomainError`. Exceptions must never cross a module boundary. | api-spec §2.4, architecture §13.3 | Mapper unit tests |
| TR-NET-08 | Unknown JSON fields must be ignored, and absent optional fields must not fail parsing. | api-spec, architecture §7 | Contract tests with extra fields |
| TR-NET-09 | E5 and E6 must send `If-None-Match`. A 304 must keep the cached copy. | FR-028 | TC-121 |
| TR-NET-10 | Payment status must be polled every 2 s while a payment is pending. Polling must stop on any terminal state. | FR-017, NFR-010 | TC-075, TC-077 |
| TR-NET-11 | Connectivity = validated internet **and** a successful E14 health probe whose `paymentGateway` component is `OK` (otherwise checkout is unavailable, as when offline). While offline, E14 is probed on every network change and every 30 s. | FR-028, api-spec §1.1 | TC-122, TC-123 |
| TR-NET-12 | The base URL is a build setting per environment (`dev`, `staging`, `prod`). Only debug builds may override it at runtime (Developer options). | PRD §10.1, architecture §7 | Build config review, TC-148 |

## 8. Payment and hardware integration

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-INT-01 | The kiosk must never call the payment gateway directly. QRIS payloads and payment status come through the backend (E9–E11). | architecture §2, D-12 | Code review |
| TR-INT-02 | The QR must be rendered from `qrString` with error correction level M or higher, and must decode back to exactly `qrString`, including with the center mark drawn. | FR-017 AC1 | TC-079 |
| TR-INT-03 | The QR countdown must use server `expiresAt` adjusted by the server/device clock offset from the E9 `Date` header, accurate to ± 1 s even with the device clock skewed by +10 min. | FR-017 AC2, NFR-017 | TC-074, TC-160 |
| TR-INT-04 | The amount on the QR and the amount sent to the terminal must equal the backend order total. | FR-017, FR-018 AC2 | TC-074, TC-080 |
| TR-INT-05 | Card terminal: the app must cancel on the terminal after 90 s with no response, and on "Back to order". The transport and protocol are decided in ADR-0003 (OQ-03) behind the `CardTerminal` interface. | FR-018, A-16 | TC-082, TC-181 |
| TR-INT-06 | Unknown payment status: show "Checking your payment…" and poll for up to 60 s. "You have not been charged" must only appear after a backend terminal non-paid status. | FR-019, A-17 | TC-085 |
| TR-INT-07 | Serial number: from the iMin SDK, falling back to `Build.getSerial()` as device owner. Must be identical across reinstall, data clear and reboot. | FR-002, A-02 | TC-005–TC-007 |
| TR-INT-08 | Printers: built-in through the iMin SDK, USB through USB host bulk transfer, Bluetooth through Classic SPP to paired devices, all speaking ESC/POS. 58 mm and 80 mm paper, density Light, Normal and Dark. | FR-021, FR-022, NFR-016, A-21 | TC-096–TC-099 |
| TR-INT-09 | Exactly one printer per role (Receipt, Kitchen). The receipt and ticket print within TR-PRF-12. The 58 mm layout wraps without truncating amounts. | FR-021 AC3, FR-022, BR-12 | TC-093, TC-099 |
| TR-INT-10 | Every external system (backend, gateway via backend, card terminal, printers, device identity, clock, connectivity) must sit behind a domain interface with a scriptable fake in `:core:testing`. | NFR-012 | TC-156 |
| TR-INT-11 | Business timestamps (order time, expiry) come from the server. The device clock drives UI timers only, through the injected `Clock`. | NFR-017 | TC-160 |

## 9. Data and persistence

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-DAT-01 | All money is integer IDR (`Long`). No floating point anywhere in money math or money storage. | BR-01, FR-013 | Pricing tests (TC-055–TC-061), detekt rule / review |
| TR-DAT-02 | Pricing must be deterministic and must match the backend re-validation in 100 % of contract tests. Property test: 1,000 random carts. | FR-013 AC4, OBJ-3 | TC-060, TC-061 |
| TR-DAT-03 | The menu cache must be replaced atomically (one Room transaction) per `menuVersion`. | FR-028, DEV-021 | DAO tests |
| TR-DAT-04 | From 1.0.0 onward, Room schema changes need an explicit `Migration`, an exported schema committed under `core/data/schemas/`, and a migration test. Before 1.0.0 a destructive migration is allowed for cache tables only, never for `pending_order` or DataStore. | NFR-014, architecture §6 | `MigrationTestHelper` tests |
| TR-DAT-05 | The image disk cache is capped at 200 MB. A cold start with no network must show cached images. | NFR-013, A-37 | TC-125, TC-157 |
| TR-DAT-06 | The cart is in memory only, survives navigation and configuration change, and is cleared on Idle. Only the pending order/payment is persisted. | FR-012, A-11 | TC-023, TC-139 |
| TR-DAT-07 | Device settings, printer registry and PIN lockout state persist locally and apply immediately without restart. | FR-022 AC3, FR-026 AC1 | TC-100, TC-115 |

## 10. Accessibility and motion

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-ACC-01 | Touch targets ≥ 88 dpx (≈ 48 dp at scale). | NFR-007 | Compose accessibility checks, TC-150 |
| TR-ACC-02 | Text contrast ≥ 4.5:1, or ≥ 3:1 for bold text ≥ 24 dpx. | NFR-007 | Accessibility Scanner, TC-150 |
| TR-ACC-03 | Every interactive element has a content description matching the design's aria label. | NFR-007 | TC-151 (TalkBack walkthrough of J1) |
| TR-ACC-04 | No information conveyed by color alone. | NFR-007 | Design-fidelity review |
| TR-ACC-05 | Reduce motion (setting **or** the system "Remove animations" setting) must collapse every animation to ≤ 1 frame with no delay. Routing rules still apply. | FR-026, FR-001 AC3, NFR-009 | TC-004, TC-115, TC-153 |
| TR-ACC-06 | Animation durations, delays and easing must match PRD §11.4 within ± 10 %. | NFR-009 | TC-153 |
| TR-ACC-07 | Sounds are ≤ 1 s each, play on the media stream, never block the UI, and are silent when the setting is off. | FR-030 | TC-129 |

## 11. Localization and formatting

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-L10N-01 | English-only UI in 1.0. All user-visible strings live in `values/strings.xml`, with `<plurals>` where needed. No `values-in/`, no language switch. | D-29, NFR-008, FR-008 | Lint `HardcodedText`, TC-152 |
| TR-L10N-02 | Money is formatted only by `IdrFormatter`: `Rp` + space + `.` thousands separators, no decimals (`Rp 172.700`). | BR-01, NFR-008 | Formatter unit tests |
| TR-L10N-03 | Menu text resolves `en` and falls back to `id` when `en` is missing. | api-spec §2.3 | Mapper unit tests |
| TR-L10N-04 | A pseudo-locale run shows no clipped text. | NFR-008 | TC-152 |

## 12. Maintainability and quality gates

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-MNT-01 | Module structure and dependency rules per architecture §3. `:core:domain` is a JVM-only Gradle module (no `android.*`, Retrofit, Room or Hilt). Feature packages do not import each other. DTOs and entities stay in `:core:data`. | NFR-011, ADR-0001 | Dependency Analysis `buildHealth`, review |
| TR-MNT-02 | Line coverage: `:core:domain` ≥ 90 %, `:core:data` ≥ 70 %. | NFR-011 | `koverVerify`, TC-155 |
| TR-MNT-03 | Zero errors from Android Lint, detekt and ktlint. | NFR-011 | TC-155 |
| TR-MNT-04 | Public domain APIs have KDoc citing the requirement they implement. | NFR-011, conventions §4 | Review |
| TR-MNT-05 | Versions are declared only in `gradle/libs.versions.toml` and listed in `technology-stack.md`. Only stable releases. | conventions §7 | Review, `dependencyUpdates` |
| TR-MNT-06 | **Verification gate (no CI in 1.0, D-31):** `./gradlew verify` must pass before every merge to `main` and at every milestone exit. Instrumented tests (`connectedDebugAndroidTest`) must pass on the API 30 and API 34 emulators at the same points. Evidence goes in `docs/test-reports/<version>.md`. | NFR-011, D-31 | TC-155, milestone reports |
| TR-MNT-07 | A debug-only Developer options screen lets testers switch fakes and force outcomes. Every journey J1–J8 must be runnable on an emulator with fakes. | NFR-012 | TC-156 |
| TR-MNT-08 | Screenshot tests exist for every screen artboard (20) and every `S_*` state board (33). | NFR-002, NFR-009 | TC-180 |

## 13. Distribution and operations

| ID | Requirement | Source | Verification |
|---|---|---|---|
| TR-DST-01 | Distributed as a signed release APK (v2 + v3 signatures) through MDM or sideload. | NFR-014, D-22 | TC-158 |
| TR-DST-02 | `versionName` = milestone. `versionCode = MAJOR*10000 + MINOR*100 + PATCH`. | NFR-014 | Build config review |
| TR-DST-03 | Provisioning as device owner is documented in `docs/provisioning.md` (adb `dpm set-device-owner` for development; the MDM path per OQ-10). | FR-029, OQ-10 | DEV-101 |
| TR-DST-04 | The screen stays on in kiosk mode, and the device surface temperature stays within the manufacturer's spec during the soak. | NFR-015 | TC-145 |

---

## 14. Open items that affect these requirements

| Item | Affects | Resolution |
|---|---|---|
| OQ-01 iMin model, Android version, SN and printer SDK | TR-PLT-04, TR-INT-07, TR-INT-08, all TR-PRF targets | ADR-0002 (0.2.0), ADR-0004 (0.6.0) |
| OQ-03 EDC transport and result path | TR-INT-05 | ADR-0003 (0.5.0) |
| A-36 Pinning acceptable to infra | TR-SEC-03 | Confirm in DEV-094 |
| The `Build.getSerial()` fallback (A-02) needs `READ_PHONE_STATE`, which is not in PRD §12.3 | TR-INT-07, TR-SEC-09 | ADR-0002 (DEV-012). Drop the fallback if the iMin SDK works, or have the PO add the permission. |
| FR-003 says the device token is "refreshed per §10", but PRD §10 and api-spec v1 define no refresh endpoint (E1 returns `tokenExpiresAt: null`) | TR-NET-06, TR-SEC-01 | Until the backend team defines one, the token is long-lived and a 401 sends the kiosk back to SCR-02. Agree before 0.9.0 (DEP-1). |
| NFR-016 lists API 30–36, while TR-PLT-01 makes the target API 37 | TR-PLT-04 | The API 37 emulator is added here as a derived requirement. Ask the PO to extend NFR-016 to API 37 in the next PRD revision. |
