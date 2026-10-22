# Hitachi Kiosk — Development Plan (0.1.0 → 1.0.0)

| | |
|---|---|
| Document version | 1.0 (2026-10-22) |
| Requirements | [`prd.md`](prd.md) (FR-xxx, NFR-xxx, D-xx, A-xx, OQ-xx) |
| Tests | [`testing-plan.md`](testing-plan.md) (TC-xxx) |
| Team | One Android developer (with AI coding agents) [D-25] |
| Horizon | ~13 weeks to `1.0.0` |

## 1. Starting point

Verified repository state on 2026-10-22:
- `hitachi-kiosk/` contains only `docs/ui-design/`, which holds 18 design artboards plus `canvas.json`.
- It has no Android project, no Gradle build, no source code and no tests.
- It is not yet a git repository. Git is initialized when these documents are committed.

Everything below is therefore greenfield. No existing code is changed or migrated.

## 2. Planning principles

1. **Contract-first.** The backend does not exist [D-06]. 0.1.0 publishes the API contract and every feature is built against fakes. The real backend, gateway and EDC are wired in at 0.9.0.
2. **Risk early, polish late.** Unknown hardware (SN, printers) gets spikes in the milestone that first needs it. Pricing and payment state logic land before UI polish.
3. **Each milestone is testable on its own.** Every version ships an installable debug APK whose new journeys run end-to-end on an emulator with fakes.
4. **Tests ship with features.** No milestone exits with untested new behavior. Testing-plan §7 lists the tests per version.
5. **Design fidelity is built in.** Every screen is built from the design tokens and motion specs as it is created. 0.8.0 is a fidelity *audit*, not the first time animations are added.

## 3. Milestone overview

| Version | Milestone | Weeks (est.) | Main requirements | External dependency at exit |
|---|---|---|---|---|
| 0.1.0 | Foundation | 1 | NFR-001/002/011/012, contract | — |
| 0.2.0 | Device activation & setup | 1 | FR-001–005 | iMin device for SN spike (DEP-4) |
| 0.3.0 | Catalog: idle, order type, menu, item, combo | 2 | FR-006–011, FR-028 (sync), NFR-013 | — |
| 0.4.0 | Cart, pricing, upsell & inactivity | 1.5 | FR-012–015, FR-023 | Upsell design (DEP-5) |
| 0.5.0 | Order submission & payments (fakes) | 2 | FR-016–019, FR-028 (offline gate), NFR-017 | Gateway + EDC specs (DEP-2/3) |
| 0.6.0 | Printing & completion | 1.5 | FR-020–022, NFR-016 | Test printers (DEP-7) |
| 0.7.0 | Staff settings & kiosk lockdown | 1.5 | FR-024–027, FR-029–031 | MDM/provisioning answer (OQ-10) |
| 0.8.0 | Localization, accessibility, motion & performance | 1 | FR-008, NFR-003/007/008/009 | — |
| 0.9.0 | Release candidate: real integrations & pilot | 1.5 | FR-017/018 real, NFR-004/005/006/014/015 | Real backend, gateway sandbox, EDC (DEP-1/2/3), pilot content (DEP-6) |
| 1.0.0 | Release | 0.5 | All MVP | Sign-off |

Total ≈ 13.5 weeks. The extra half week over the 3-month target is visible on purpose. If the date is fixed, the first cuts (with PO approval) are: Bluetooth printers → 1.0.1, and upsell visuals limited to design-system components.

### 3.1 Dependency graph
```
0.1.0 ─► 0.2.0 ─► 0.3.0 ─► 0.4.0 ─► 0.5.0 ─► 0.6.0 ─► 0.7.0 ─► 0.8.0 ─► 0.9.0 ─► 1.0.0
                    │                  │         │
                    └─ sync/cache ─────┘         └─ print after PAID needs payment state
0.7.0 lock task needs 0.2.0 binding; 0.9.0 needs DEP-1/2/3 delivered.
```

---

## Version 0.1.0 — Foundation

### Objective
Create a buildable, testable Android project with the architecture, design system foundation, API contract and fakes that every later milestone builds on. Nothing customer-facing ships yet, but the empty app launches into a navigation skeleton showing every screen as a labelled placeholder.

### Scope
**Included:** Gradle project, module structure (PRD §12.1), version catalog, CI, static analysis, design tokens, `DesignUnit` scaling, typography with bundled Schibsted Grotesk, motion spec constants, reduced-motion plumbing, navigation skeleton, API contract (OpenAPI) with a mock server, fake implementations of every external interface, debug developer menu shell.
**Excluded:** Any real screen UI beyond placeholders, networking against a real server, hardware drivers.

### TODO checklist

**Build & architecture**
- [ ] **DEV-001** Create the Android project `hitachi-kiosk` at the repo root (NFR-001, NFR-011).
  - Gradle Kotlin DSL, `libs.versions.toml`, `minSdk 30`, `compileSdk`/`targetSdk` latest stable (≥ 35), Kotlin + Compose BOM.
  - applicationId `com.hitachi.kiosk` [A: confirm with PO], `versionName 0.1.0`, `versionCode 100` (NFR-014 scheme).
  - Portrait-locked single Activity with `windowSoftInputMode` set for the settings inputs.
  - *Verify:* `./gradlew assembleDebug` succeeds, and the APK installs on an API 30 and an API 35 emulator.
- [ ] **DEV-002** Create modules `:core:designsystem`, `:core:domain` (pure Kotlin/JVM), `:core:data`, `:core:hardware`, `:core:testing` with the dependency rules in PRD §12.1. Add Hilt to `:app`, `:core:data` and `:core:hardware`.
  - *Verify:* `:core:domain` has no Android dependency (a Gradle JVM module), and a module dependency check (Gradle `dependencies` report) matches §12.1.
- [ ] **DEV-008** CI pipeline (GitHub Actions or the team's CI) (NFR-011).
  - Runs `lint`, `ktlint`/`detekt`, unit tests and `assembleDebug` on every push.
  - Runs instrumented tests on an API 30 and an API 34 emulator nightly or on PRs to `main`.
  - Publishes the test reports and debug APK as artifacts.
  - *Verify:* a deliberately failing test fails CI.

**Design system foundation**
- [ ] **DEV-003** `DesignUnit`: the `Int.dpx` / `Int.spx` extensions computing `screenWidthDp / 1080`, with a large-text multiplier CompositionLocal (NFR-002, PRD §11.2).
  - *Verify:* unit test of the conversion. A screenshot test of a 72-dpx-margin box at 1080×1920 for 160/240/320 dpi shows identical layout.
- [ ] **DEV-004** Tokens: colors (PRD §11.1), radii, spacing, typography (Schibsted Grotesk 400–800 bundled under `res/font`, with its OFL license stored in `licenses/`), and motion constants (easings `Emphasized=(.16,1,.3,1)`, `Press=(.2,0,0,1)`, `Spring=(.3,1.15,.4,1)`, `InOut=(.65,0,.35,1)`, `Fly=(.55,0,.75,.2)`).
  - Add a `LocalReduceMotion` that, when true, turns every spec into `snap()`.
  - *Verify:* a token unit test asserts the hex values. A reduce-motion test asserts specs return `snap()`.

**Contract & fakes**
- [ ] **DEV-006** Derive `docs/api/openapi.yaml` from the existing human-readable contract `docs/api/api-spec.md` (E1–E14, written 2026-10-22, from which the product owner builds the mock server first).
  - Copy the example payloads into the OpenAPI `examples`.
  - Reconcile any difference found while building the mock, updating `api-spec.md` first.
  - *Verify:* the OpenAPI lints clean (e.g. `redocly lint`); the examples validate against their schemas; and the app's MockWebServer fixtures equal the spec's examples.
- [ ] **DEV-007** Networking skeleton in `:core:data` (NFR-010).
  - Retrofit + OkHttp + kotlinx.serialization, error envelope parsing, auth interceptor, timeouts 10 s/15 s, network security config with cleartext disabled.
  - The base URL comes from build config `dev|staging|prod`.
  - *Verify:* MockWebServer tests for the success path, the error envelope, and a timeout.
- [ ] **DEV-005** Domain interfaces and fakes in `:core:testing` (NFR-012): `BackendApi` (all E1–E14), `PaymentGateway` (via backend), `CardTerminal`, `PrinterDriver`, `DeviceIdentityProvider`, `ConnectivityMonitor`, `Clock`. Each fake is scriptable (success, error, delay).
  - *Verify:* fakes compile against the interfaces, and a sample test drives each fake outcome.
- [ ] **DEV-009** A debug-only "Developer options" screen (behind 5 taps on the splash mark, `debug` source set only).
  - Choose fake vs real backend, select fake scenarios, and show the build info.
  - *Verify:* the release APK has no reference to the class (`apkanalyzer` check, scripted in CI).

**Navigation skeleton**
- [ ] **DEV-010a** A Navigation Compose graph with routes for SCR-01…SCR-18 (PRD §4). Each route shows a placeholder with the screen ID, and all edges in PRD §5.1 are wired as debug buttons.
  - *Verify:* a UI test walks every edge in §5.1 on the emulator.

**Docs**
- [ ] **DEV-019** `README.md` (build, run, module map, how to switch fakes) and `docs/adr/0001-architecture.md` capturing D-24 and the module decision.

### Acceptance criteria
- AC-0.1-1: A clean checkout builds debug and release (release unsigned locally) with zero lint errors.
- AC-0.1-2: The app launches on API 30 and API 35 emulators and every placeholder route is reachable.
- AC-0.1-3: `openapi.yaml` covers E1–E14, lints clean, and matches `api-spec.md`. The app runs its first screens against the PO's mock server or the in-app fakes.
- AC-0.1-4: CI runs green with unit and instrumented test stages.

### Testing requirements
- Unit: DesignUnit conversion, token values, reduce-motion specs, network error parsing (TC-140, TC-141, TC-154 partial, TC-155, TC-156).
- UI: navigation edge walk (DEV-010a).
- Manual: install on an emulator at 1080×1920.

### Deliverables
Buildable multi-module project; CI; design-system foundation; `docs/api/openapi.yaml`; fakes; README; ADR-0001.

### Exit criteria
All AC-0.1 pass in CI. The contract has been shared with the backend team (DEP-1) and the share date is noted in the README. There are no open P1/P2 defects.

---

## Version 0.2.0 — Device activation & setup

### Objective
A fresh kiosk goes from first launch to "bound to a merchant". The device identifies itself by SN, activates against the (fake) backend, lets staff choose a merchant, and routes correctly on every restart.

### Scope
**Included:** Splash (full animation), startup routing, SN provider with a real-device spike, Sign in screen and activation, secure token storage, Merchant selection, binding/logout, operating-mode config.
**Excluded:** Lock task (0.7.0), menu download content (only the call and storage stub, completed in 0.3.0), Idle visuals (placeholder until 0.3.0).

### TODO checklist

**Splash & routing**
- [ ] **DEV-010** SCR-01 Splash UI with all animations per PRD §11.4 (FR-001). The version text comes from `BuildConfig`.
  - *Verify:* screenshot at t=0 and t=4 s. A motion check against the design timings.
- [ ] **DEV-011** `StartupRouter`: in parallel, run the min 3.9 s timer and init (load the token, binding and cached config; 10 s cap), then route per FR-001 (FR-001 AC1–AC3).
  - *Verify:* unit tests for the 4 routing cases + network timeout + corrupt storage.

**Identity & activation**
- [ ] **DEV-012** `DeviceIdentityProvider` (FR-002). This is a **spike on real iMin hardware** first (R-3).
  - Try the iMin SDK SN API, a system property, and `Build.getSerial()` as device owner. Document the findings in `docs/adr/0002-device-serial.md`.
  - Implement the chosen source plus the fallback chain and caching.
  - *Verify:* TC-005–TC-007. If no unit is available, record the blocker and keep the fake; the milestone may exit with this as a documented risk only if the PO accepts.
- [ ] **DEV-013** SCR-02 Sign in UI per `Login.dc.html`. Busy state "Signing in…" with spinner, single-flight tap, error panel variants (not registered with SN shown, disabled, network), and "Need help?" text (FR-003).
  - *Verify:* UI tests for each state with fake responses.
- [ ] **DEV-014** `ActivationRepository` (FR-003, NFR-005).
  - Calls E1 and persists `deviceToken` in Keystore-backed EncryptedSharedPreferences, and `deviceId`/`kioskCode`/`kioskName` in DataStore.
  - A 401 interceptor clears activation and routes to SCR-02.
  - *Verify:* unit and integration tests (MockWebServer): success, 404 `DEVICE_NOT_REGISTERED`, `DEVICE_DISABLED`, timeout, and 401 later.

**Merchant**
- [ ] **DEV-015** SCR-03 Merchant selection UI per `Merchants.dc.html` (FR-004).
  - Live card with photo, logo, "Open now"/"Closed" badge and arrow; coming-soon disabled rows; empty state; skeletons [A-32]; and the "Log out" header button.
  - *Verify:* UI tests including the TalkBack disabled semantics on soon rows.
- [ ] **DEV-016** `MerchantRepository`: E2 list, E3 bind (then trigger the config + menu fetch stub that completes in 0.3.0), and E4 unbind on Log out with local data wiped (FR-004 AC1–AC4).
  - *Verify:* integration tests; a failure path that keeps staff on SCR-03 with retry.
- [ ] **DEV-017** Store `mode=SINGLE` from config. Ensure no customer merchant picker exists (FR-005).
  - *Verify:* unit test.

**Testing & docs**
- [ ] **DEV-018** Write the instrumented test for journey J5 with fakes, plus J6. Update the README "first run" section.

### Acceptance criteria
- FR-001 AC1–AC3, FR-002 AC1–AC3 (AC1/AC2 on real hardware if DEP-4 is available), FR-003 AC1–AC4, FR-004 AC1–AC4 and FR-005 AC1 pass.
- The token is not readable in plain text from app storage (checked with `run-as` on a debug build).

### Testing requirements
- Unit: StartupRouter, ActivationRepository, MerchantRepository.
- Integration: MockWebServer flows.
- UI: SCR-01/02/03 states, J5, J6.
- Manual: SN spike on the iMin device.
- TCs: TC-001–TC-019, TC-146.
- Regression: 0.1.0 suite.

### Deliverables
Working activation and binding against fakes; ADR-0002 (device serial); updated contract if the spike changes E1.

### Exit criteria
All 0.2.0 TCs pass. The SN source is decided (or explicitly accepted as a risk by the PO). There are no P1/P2 defects.

---

## Version 0.3.0 — Catalog: idle, order type, menu, item, combo

### Objective
A customer can start an order and browse and customize the full menu with design-fidelity UI, backed by a synced, cached menu that works offline.

### Scope
**Included:** Menu/config domain models, Room cache, versioned sync and periodic WorkManager sync, image caching with tone placeholders, Idle, Order type with the language switch, Menu (categories, promo carousel, grid, quick add, cart dock with count/total only), Item detail, Combo, and the in-memory cart *add* operations (full cart management in 0.4.0), plus the performance baseline.
**Excluded:** Pricing beyond the line totals needed for the dock, the review screen, payment, the inactivity overlay, and the upsell.

### TODO checklist

**Data**
- [ ] **DEV-020** Domain models in `:core:domain` per PRD §10.3: Category, Product, ModifierGroup (SINGLE/MULTI/LEVEL), Option, Combo, Promo, MerchantConfig, LocalizedText with fallback (FR-008 rule).
  - *Verify:* unit tests for LocalizedText fallback and LEVEL price deltas.
- [ ] **DEV-021** Room schema for config and menu with atomic replace by `menuVersion`. Add the migration test scaffold.
  - *Verify:* DAO tests (in-memory Room) for replace atomicity: a failed replace leaves the old version intact.
- [ ] **DEV-022** `CatalogSyncRepository` (FR-028).
  - Calls E5/E6 with ETag, maps DTOs to the domain, and replaces the cache.
  - Triggers at binding, startup and "sync now"; defers application while an order is active [A-29].
  - *Verify:* integration tests: 200, 304, malformed payload (keep the old cache), and mid-order deferral.
- [ ] **DEV-023** A periodic `WorkManager` sync every 15 min with the network constraint, plus Coil with a 200 MB disk cache, a prefetch of menu images after sync, and the `PhotoSlot` component with a tone placeholder (FR-028, NFR-013).
  - *Verify:* WorkManager test (`TestDriver`). Offline cold-start manual check shows cached images.

**Customer screens**
- [ ] **DEV-024** SCR-04 Idle per `Idle.dc.html` (FR-006).
  - Slideshow of 7 s with 1.4 s crossfade, 8 s Ken Burns and progress dots; the bottom sheet with the CTA ring and bilingual subtitle; tap-anywhere → SCR-05.
  - The staff corner exists here but only routes to the 0.7.0 placeholder. Hold detection is 2.0 s and implemented now.
  - Entering Idle clears the order session.
  - *Verify:* TC-020–TC-024.
- [ ] **DEV-025** SCR-05 Order type per `OrderType.dc.html` (FR-007).
  - Cards come from config toggles. The selection animation runs, then navigation after 950 ms; extra taps are ignored.
  - The order type is stored in `OrderSession`.
  - *Verify:* TC-025–TC-028.
- [ ] **DEV-026** Runtime language switching (FR-008).
  - An app-level `LocaleController` applies per-order language via `AppCompatDelegate.setApplicationLocales` or a Compose-local override, chosen in the ADR.
  - Add the EN/ID segmented control and reset to the default at order end.
  - Create the initial `values-in` for all strings existing so far.
  - *Verify:* TC-029–TC-031.
- [ ] **DEV-027** SCR-06 Menu per `Menu.dc.html` (FR-009).
  - Header with the order-type pill and Change/Cancel; time-of-day greeting [A-07].
  - Category pills including Popular and the Meals → Combo link.
  - Promo carousel (5 s, dots, pause on touch, "Order now" target).
  - Section heading with item count; the 2-column grid with badges and kcal; empty-category state [A-33].
  - *Verify:* TC-033–TC-037.
- [ ] **DEV-028** Quick add and cart dock (FR-009).
  - `+` → check state for 1.4 s with ring burst, fly-to-cart animation (760 ms), dock thumbnails (last 3), count bump and total roll.
  - Products with required groups route to Item instead [A-08].
  - The in-memory `Cart.add()` merges identical lines (FR-010 rule).
  - *Verify:* TC-038, TC-039.
- [ ] **DEV-029** SCR-08 Item detail per `ItemDetail.dc.html` (FR-010).
  - Rendered generically from modifier groups: meal radio pair (SINGLE), Customize LEVEL rows with the 3-segment indicator colors, and Extras (MULTI).
  - Stepper 1–20; the price roll; "Add to order" → Added dock swap with Keep ordering / View order.
  - *Verify:* TC-040–TC-045 (including the Rp 81.000 sample).
- [ ] **DEV-030** SCR-07 Combo per `Combo.dc.html` (FR-011).
  - Combo radio list with Save; Side/Drink chips; Size segmented; multiplier logic; summary line; the Add combo → Added state for 1.8 s.
  - *Verify:* TC-046–TC-049 (Rp 74.000 and Rp 129.000 samples).

**Performance**
- [ ] **DEV-031** Add a `:benchmark` macrobenchmark module with a baseline profile for startup and the Idle→Menu→Item journey, and JankStats logging in debug (NFR-003).
  - *Verify:* the benchmark runs on the emulator, and the baseline numbers are recorded in `docs/perf/baseline-0.3.0.md`.

### Acceptance criteria
- FR-006, FR-007, FR-009, FR-010 and FR-011 acceptance criteria pass with fake data mirroring the design samples.
- FR-008 AC2.
- FR-028 AC3 (with the fake backend) and AC4.
- Each screen has a screenshot test approved against the design artboard (side-by-side review recorded).

### Testing requirements
- Unit: models, merge rules, combo/item price calculation.
- Integration: sync/cache.
- UI: Compose tests per screen + screenshot tests.
- Benchmark baseline.
- Manual: visual review vs design at 1080×1920.
- TCs: TC-020–TC-049, TC-121, TC-124, TC-125, TC-142, TC-157.
- Regression: 0.1.0 and 0.2.0 suites.

### Deliverables
A browsable, customizable menu; sync and offline cache; perf baseline doc.

### Exit criteria
All listed TCs pass. The perf baseline is recorded, and any screen > 16 ms P90 frame time on the emulator has a tracked issue. There are no P1/P2 defects.

---

## Version 0.4.0 — Cart, pricing, upsell & inactivity

### Objective
Complete the ordering half of the journey: a correct, tested pricing engine, the order review screen with payment method choice, the upsell step, and the inactivity protection.

### Scope
**Included:** PricingEngine, full cart management, the OrderSession state machine, SCR-10 Your order (totals, methods, empty state), SCR-09 Upsell, the SCR-13 inactivity overlay.
**Excluded:** Order submission and payment (0.5.0), the offline gate on Pay (0.5.0; Pay navigates to the 0.5.0 placeholder).

### TODO checklist
- [ ] **DEV-035** `PricingEngine` in `:core:domain` (FR-013, BR-01–BR-04).
  - Integer IDR, threshold promo, exclusive tax with half-up rounding, upward total rounding with a "Rounding" line, the unlock hint, and an `IdrFormatter`.
  - *Verify:* table-driven unit tests TC-055–TC-061, including the Rp 172.700 sample, plus property-based tests (total ≥ 0, monotonic in qty).
- [ ] **DEV-036** `Cart` domain (FR-012): add/merge, increment, decrement-to-remove, the limits of 20 per line and 30 lines with error results, and the line description builder (e.g. "Fries · Cola · Extra cheddar · No onion").
  - *Verify:* unit tests TC-050–TC-052.
- [ ] **DEV-041** `OrderSession` state machine (PRD §12.2) owning the cart, order type, language and the upsell-shown flag. Every transition to Idle resets it.
  - *Verify:* state-transition unit tests for every edge.
- [ ] **DEV-037** SCR-10 Your order per `Payment.dc.html` (FR-012). Lines with roll animations and slide-out removal, the fade mask, the empty state, step label, item count plural, "Add more" and the order-type chip.
  - *Verify:* TC-053, TC-054.
- [ ] **DEV-038** Summary panel and payment method tiles (FR-013 display, FR-015).
  - Subtotal, the Promo line or unlock hint, the PB1 label from config, Rounding, Total; methods = settings ∩ config with no "At counter"; the Pay label equals the total; the disabled state.
  - *Verify:* TC-065–TC-067.
- [ ] **DEV-039** SCR-09 Upsell (FR-014). Built with design-system components until the OQ-05 design arrives: up to 4 suggestions, quick add, "No thanks"/"Continue", once per order, and the toggle read from settings (default on).
  - *Verify:* TC-062–TC-064. When the design arrives, add DEV-039b to restyle and re-baseline the screenshot.
- [ ] **DEV-040** Inactivity controller (FR-023). A global touch observer on customer routes with the configurable timeout and the SCR-13 overlay ("Still there?", 15 s ring, "Continue ordering").
  - Excluded on Checkout pending/processing, Complete and settings. The empty-cart rule on SCR-05 applies [A-23].
  - *Verify:* TC-101–TC-104 using a test clock.

### Acceptance criteria
FR-012 AC1–AC3, FR-013 AC1–AC3 (AC4 deferred to 0.5.0), FR-014 AC1–AC3, FR-015 AC1–AC3 and FR-023 AC1–AC4 pass.

### Testing requirements
- Unit: pricing (≥ 90 % coverage on `:core:domain`), cart, session.
- UI: review screen, upsell, overlay.
- Manual: pricing spot checks against a spreadsheet of 20 carts produced from the design samples.
- TCs: TC-050–TC-067, TC-101–TC-104.
- Regression: 0.3.0 menu suite.

### Deliverables
A pricing engine with a test table (`docs/pricing-cases.md`, generated from the tests); a complete ordering flow up to Pay.

### Exit criteria
All TCs pass. Domain coverage ≥ 90 %. The PO has reviewed the upsell interim UI, or the design has arrived.

---

## Version 0.5.0 — Order submission & payments (fakes)

### Objective
Implement the full order and payment lifecycle, including every failure, retry, offline and process-death path, against the fake backend, gateway and terminal. Real providers then only need adapters in 0.9.0.

### Scope
**Included:** Order creation with idempotency, price-mismatch/unavailable handling, QRIS flow, card flow via the `CardTerminal` interface, Processing/Failed states, method switch, debug simulate bar, the connectivity gate and offline banner, payment recovery after process death.
**Excluded:** Real gateway/EDC adapters (0.9.0), printing on success (0.6.0; success goes to the 0.6.0 Complete placeholder).

### TODO checklist
- [ ] **DEV-045** `OrderRepository.create()` (FR-016).
  - E7 with a UUID `Idempotency-Key` per checkout attempt, and retries with 1/2/4 s backoff on network errors only.
  - Persists the pending order/payment IDs in Room for recovery. Cancel uses E8.
  - *Verify:* TC-068–TC-070.
- [ ] **DEV-046** Mismatch handling (FR-016): `PRICE_CHANGED` → refresh catalog, re-price, back to SCR-10 with the message; `ITEM_UNAVAILABLE` → flag the lines and block Pay until they are removed.
  - *Verify:* TC-071, TC-072.
- [ ] **DEV-047** `PaymentStateMachine` + QRIS (FR-017, NFR-017).
  - E9 (QRIS) and polling E10 every 2 s; the countdown from server `expiresAt` with the skew-corrected server clock offset from the `Date` header.
  - Expiry → E11 cancel → Failed(expired). Polling stops on a terminal state. The idle timer is suspended.
  - *Verify:* TC-074–TC-078, TC-160.
- [ ] **DEV-048** QR rendering from `qrString` (ZXing core, ECC ≥ M) in the design frame: centered mark, scan line, expiry ring, the 3 steps with the amount.
  - *Verify:* TC-079 (decode the rendered bitmap equals the input).
- [ ] **DEV-049** `CardTerminal` interface (`startSale(amount, ref)`, `cancel()`, result flow), `FakeCardTerminal`, and the card UI per Checkout (terminal illustration, card-tap loop). Add the 90 s timeout [A-16]; success only on backend `PAID` [A-15].
  - *Verify:* TC-080–TC-083.
- [ ] **DEV-050** Processing and Failed states (FR-019).
  - Reason copy mapping; the "not charged" note only on terminal non-paid states; the Unknown state polling for 60 s; "Try again" with a fresh payment; "Pay by X instead" only if enabled.
  - "Back to order" cancels. The "Pay at the counter" button is not rendered.
  - Add the debug-only simulate bar.
  - *Verify:* TC-084–TC-087.
- [ ] **DEV-051** `ConnectivityMonitor` (validated network + E14 health) and an offline banner on customer screens; disable Pay and submission while offline; auto-recover (FR-028, NFR-010).
  - *Verify:* TC-122, TC-123, TC-154.
- [ ] **DEV-052** Process-death recovery (FR-016): on launch with a persisted pending payment, query E10 and route to Complete (paid) or cancel → Idle.
  - *Verify:* TC-073 (using `adb shell am kill` during a pending payment).
- [ ] **DEV-053** Update `openapi.yaml` with anything learned. If the gateway/EDC specs (DEP-2/3) have arrived, write `docs/adr/0003-payment-integration.md` mapping them to the interfaces. Otherwise raise risk R-1 to the PO.

### Acceptance criteria
FR-013 AC4 (contract test against the mock), FR-016 AC1–AC4, FR-017 AC1–AC5, FR-018 AC1–AC3, FR-019 AC1–AC3 (AC3 checked on a release build), FR-028 AC1–AC2 and NFR-017 pass, all with fakes.

### Testing requirements
- Unit: payment state machine (every transition), retry/backoff, clock offset.
- Integration: MockWebServer scripted payment sequences.
- UI: Checkout states.
- Manual: airplane-mode toggling during each state.
- TCs: TC-068–TC-087 (except TC-083, the real-EDC test, which runs in 0.9.0), TC-122, TC-123, TC-132–TC-134, TC-137, TC-138, TC-154, TC-160.
- Regression: the 0.4.0 suite plus J1–J3 end-to-end with fakes.

### Deliverables
A complete payment flow with fakes; ADR-0003 or a written risk escalation.

### Exit criteria
All TCs pass. The gateway/EDC spec status is recorded. If the specs are missing, the PO has acknowledged that 0.9.0 is at risk. There are no P1/P2 defects.

---

## Version 0.6.0 — Printing & completion

### Objective
Paid orders end on the Complete screen with the backend order number, and a receipt and kitchen ticket print on the configured printers: built-in iMin, USB or Bluetooth.

### Scope
**Included:** Printer abstraction and ESC/POS encoder, three drivers, receipt and kitchen renderers (58/80 mm, density, logo, footer), print orchestration after PAID, the Complete screen, the Printer settings screen (reachable via a debug route until 0.7.0 adds the PIN gate).
**Excluded:** The settings PIN/home (0.7.0).

### TODO checklist
- [ ] **DEV-055** `PrinterManager` in `:core:hardware` (FR-022).
  - Covers the printer registry (DataStore), role assignment (one printer per role), status flow (online/offline/paper %/last error), a print job queue with one retry, and an `EscPosEncoder` (text styles, alignment, raster image for the logo, cut, feed).
  - *Verify:* unit tests for the encoder byte output against golden files, and for the role reassignment rule.
- [ ] **DEV-056** Drivers (NFR-016). Write findings in `docs/adr/0004-printers.md`.
  - **iMin built-in:** via the iMin printer SDK, from a spike on the device, using SDK paper status.
  - **USB:** USB host bulk transfer with the permission flow.
  - **Bluetooth:** SPP to paired devices, with `BLUETOOTH_CONNECT`/`SCAN` runtime permission and rationale.
  - *Verify:* TC-096–TC-099 on real printers (DEP-7) and the iMin device (DEP-4).
- [ ] **DEV-057** Receipt renderer (FR-021) with the content per FR-021/A-20 in the order language, 58/80 mm layouts and density mapping.
  - *Verify:* golden-text tests for both widths and both languages; TC-093.
- [ ] **DEV-058** Kitchen ticket renderer (large order number, type, time, lines with options, no prices).
  - *Verify:* golden-text test; TC-092.
- [ ] **DEV-059** Print orchestration: on `PAID`, enqueue the receipt and/or ticket per toggles and roles, without blocking the UI. Failures surface on Complete and in status (FR-020 AC4, FR-021 AC4).
  - *Verify:* TC-091, TC-094, TC-095.
- [ ] **DEV-060** SCR-12 Complete per `Complete.dc.html` (FR-020).
  - Flood, badge, check draw, rings, confetti, digit cascade (first character in accent), order-type instruction copy, printer animation, 20 s ring and Done.
  - Clear the session to Idle on exit.
  - *Verify:* TC-088–TC-090.
- [ ] **DEV-061** SCR-15 Printer settings per `SettingsPrinter.dc.html`, extended with the printer list and add/role UI [D-18].
  - Status meter (red < 15 %), last printed, Test page and Feed actions with busy/done labels, receipt toggles, footer input, paper width and density.
  - *Verify:* TC-100.

### Acceptance criteria
FR-020 AC1–AC4, FR-021 AC1–AC4 and FR-022 AC1–AC4 pass on at least one printer of each connection type. J1 runs end-to-end with fakes, including the printed output.

### Testing requirements
- Unit: encoder, renderers, roles.
- Integration: PrinterManager with fake drivers (offline, paper-out, slow).
- Manual: physical print checks on 58 and 80 mm and the built-in printer, with photographed output stored as evidence.
- TCs: TC-088–TC-100, TC-159 (partial).
- Regression: payment suite.

### Deliverables
Printing subsystem; ADR-0004; the certified printer list (`docs/printers.md`).

### Exit criteria
All TCs pass with physical evidence. Any unsupported printer is documented. There are no P1/P2 defects.

---

## Version 0.7.0 — Staff settings & kiosk lockdown

### Objective
Staff can securely maintain the kiosk on site, and the device behaves like an appliance: locked to the app, auto-starting, and surfacing faults.

### Scope
**Included:** Settings row components, PIN gate with lockout, Settings home with live status, Payment / Kiosk behaviour / Device & network settings, Change PIN, Restart app, lock task as device owner with the boot launch, sound effects, local logging and status surfacing.
**Excluded:** Merchant rebinding from settings (OQ-07, unless answered before this milestone starts; then add DEV-074).

### TODO checklist
- [ ] **DEV-065** Shared settings components in `:core:designsystem`: toggle row (green track), segmented row, action row (idle/busy/done with 1.5 s/3.8 s timings [V]), info row, input row, meter row, section card, hero card, header with Settings/Exit.
  - *Verify:* screenshot tests of each row state.
- [ ] **DEV-066** SCR-14 PIN gate (FR-024).
  - 2 s corner hold entry (already detected in DEV-024), keypad with stagger, dot pop and shake on wrong.
  - Constant-time salted-hash verify against config (algorithm agreed in the contract, e.g. PBKDF2).
  - 5 tries → 5 min lockout persisted in DataStore with a countdown; "Cancel"; and a 120 s auto-exit [A-22].
  - *Verify:* TC-105–TC-108.
- [ ] **DEV-067** Settings home: header with Lock/Exit, "{merchant} · {kiosk name}", aggregate status ("All systems ready" / "N issues need attention"), 4 section rows with live status, and the version footer (FR-024).
  - *Verify:* TC-109, TC-130.
- [ ] **DEV-068** SCR-16 Payment settings (FR-025): method toggles (counter hidden; the last one cannot turn off), terminal status/TID/Test terminal via `CardTerminal`, masked QRIS MID, expiry 3/5/10, PB1 info, rounding.
  - *Verify:* TC-110–TC-112.
- [ ] **DEV-069** SCR-17 Kiosk behaviour (FR-026): order type toggles (last one cannot turn off), upsell, idle timeout, default language, brightness [A-26], sound, reduce motion (also bound to the system animator scale = 0), and larger text (1.15× through `DesignUnit`).
  - *Verify:* TC-113–TC-116.
- [ ] **DEV-070** SCR-18 Device & network (FR-027).
  - SSID/IP/signal (location permission optional), Connection test (backend + gateway separately), Last synced/Auto-sync/Sync now, Kiosk ID, version.
  - Change PIN flow (current → new → confirm, online only, E13, with interim UI for OQ-13), Restart app (process restart via `AlarmManager`/relaunch intent), and "Exit kiosk mode" [A-30].
  - *Verify:* TC-117–TC-120.
- [ ] **DEV-071** Lock task (FR-029).
  - A `DeviceAdminReceiver`; when device owner, call `setLockTaskPackages`, set the persistent preferred home activity and disable the keyguard/status bar.
  - Start lock task after binding; `BOOT_COMPLETED` launch; keep the screen on. Fall back to screen pinning with a warning when not device owner.
  - Document provisioning in `docs/provisioning.md` (adb `dpm set-device-owner` for development; the MDM path per OQ-10).
  - *Verify:* TC-126–TC-128 on the iMin device.
- [ ] **DEV-072** Sound effects (FR-030): 3 short sounds via `SoundPool`, gated by the setting.
  - *Verify:* TC-129.
- [ ] **DEV-073** Local logging (FR-031, NFR-006): Timber to rotating files (7 days / 10 MB), a redaction helper (payment refs → last 4 characters, never the PIN), and a red-dot issue indicator on the Idle staff corner [A-31].
  - *Verify:* TC-131, TC-149 (partial).

### Acceptance criteria
FR-024 AC1–AC4, FR-025 AC1–AC3, FR-026 AC1–AC3, FR-027 AC1–AC4, FR-029 AC1–AC3, FR-030 AC1–AC2 and FR-031 AC1–AC2 pass. Lock task is verified on the iMin device.

### Testing requirements
- Unit: PIN verify/lockout, settings persistence.
- UI: every settings screen.
- Manual on device: lock-task escape attempts, reboot auto-start, permission denial flows.
- TCs: TC-105–TC-131.
- Regression: J1–J8 with fakes.

### Deliverables
Settings area; lock-task kiosk; `docs/provisioning.md`.

### Exit criteria
All TCs pass on the target device. Provisioning is documented and has been run once from scratch. There are no P1/P2 defects.

---

## Version 0.8.0 — Localization, accessibility, motion & performance

### Objective
Audit and finish the cross-cutting qualities so the app is ready for a real-world pilot: complete Bahasa translation, accessibility compliance, motion matching the design, and performance on the target device.

### Scope
**Included:** Translation completion and review, accessibility fixes, the motion fidelity audit, on-device performance tuning, screenshot test suite completion, and the larger-text layout pass.
**Excluded:** New features.

### TODO checklist
- [ ] **DEV-080** Localization (FR-008, NFR-008).
  - Extract any remaining strings; complete `values-in`; add plurals; get a native-speaker review of all ID copy, including receipts and errors.
  - Turn on the pseudo-locale run; set the lint `HardcodedText`/`MissingTranslation` checks to error.
  - *Verify:* TC-032, TC-152.
- [ ] **DEV-081** Accessibility (NFR-007).
  - TalkBack labels matching the design aria labels, a focus order per screen, role semantics (radio, toggle, button), contrast check of every text/background token pair (fix any < 4.5:1 with the PO), and touch targets ≥ 88 dpx.
  - *Verify:* TC-150, TC-151.
- [ ] **DEV-082** Performance on the target device (NFR-003).
  - Run the macrobenchmarks on the iMin device; fix regressions (image sizes, recomposition hot spots, `graphicsLayer` for animated layers); update the baseline profile.
  - *Verify:* TC-142–TC-144, with results in `docs/perf/0.8.0.md`.
- [ ] **DEV-083** Motion fidelity audit (NFR-009): screen-by-screen comparison against PRD §11.4 (screen recordings next to the design prototype), and confirm reduce motion collapses everything.
  - *Verify:* TC-153 checklist signed.
- [ ] **DEV-084** Complete the screenshot test suite (Paparazzi or Roborazzi; pick the one that runs on JVM CI) for every screen and state in PRD §7 at 1080×1920, in both languages and with larger text.
  - *Verify:* CI screenshot job green, and references reviewed against the design.
- [ ] **DEV-085** Larger-text layout fixes (FR-026 AC3).
  - *Verify:* TC-116.

### Acceptance criteria
NFR-003, NFR-007, NFR-008 and NFR-009 acceptance criteria pass on the target device. There are no untranslated strings, and Accessibility Scanner shows zero errors.

### Testing requirements
- Screenshot regression; accessibility scanner + manual TalkBack J1; on-device benchmarks; native-speaker review sign-off.
- TCs: TC-032, TC-116, TC-142–TC-144, TC-150–TC-153.
- Regression: full automated suite.

### Deliverables
Performance report; accessibility report; translation sign-off; complete screenshot suite.

### Exit criteria
All targets are met, or variances are formally accepted by the PO with a tracked issue for 1.0.x. There are no P1/P2 defects.

---

## Version 0.9.0 — Release candidate: real integrations & pilot

### Objective
Replace the fakes with the real backend, company payment gateway and EDC terminal. Prove stability and security, and run a pilot at one merchant on the target device.

### Scope
**Included:** Real API environment wiring and contract verification, the QRIS gateway adapter, the EDC adapter, the device matrix, a 72 h soak, security and privacy review, release signing and build config, pilot operations, and the bug-fix buffer.
**Excluded:** Any feature from the PRD §3.2 non-goals.

**Hard entry dependencies:** DEP-1 (staging backend implementing the contract), DEP-2 (gateway sandbox), DEP-3 (EDC unit + protocol), DEP-6 (pilot menu content). If any is missing at the planned start, the PO decides whether to slip 0.9.0, or (for the card path only) to ship card in 1.0.1 [R-1].

### TODO checklist
- [ ] **DEV-097** Point `staging` at the real backend. Run the contract test suite (generated from `openapi.yaml`) against it, and fix or record any mismatches with the backend team (FR-013 AC4).
  - *Verify:* contract suite green against staging.
- [ ] **DEV-090** QRIS against the gateway sandbox (FR-017): real `qrString`, status updates (polling or push as provided), cancel and expiry. Run the full TC-074–TC-079 set against the sandbox with real e-wallet test apps where the sandbox allows.
- [ ] **DEV-091** EDC adapter implementing `CardTerminal` per ADR-0003 (USB/LAN/cloud per OQ-03): sale, cancel, timeout, result relay (E12 if required), TID display, and the Test terminal action.
  - *Verify:* TC-080–TC-083 with test cards on the real terminal.
- [ ] **DEV-092** Device compatibility matrix (NFR-016) per testing-plan §2.3: emulators API 30/33/34/35/36 and the iMin target, plus 2 USB and 2 Bluetooth printer models.
  - *Verify:* TC-159.
- [ ] **DEV-093** A 72 h soak on the iMin device with a scripted journey via UI Automator every 3 min, against staging. Collect memory, crashes, ANRs, temperature and log size (NFR-004, NFR-015).
  - *Verify:* TC-145.
- [ ] **DEV-094** Security & privacy review (NFR-005, NFR-006).
  - Checklist in testing-plan §6.12: token storage, TLS/pinning (confirm A-36 with infra), exported components, R8, no debug tools in release, log redaction, permissions, and a dependency audit with no analytics SDKs.
  - *Verify:* TC-146–TC-149.
- [ ] **DEV-095** Release build: signing config from secure env vars/CI secrets (keystore never committed), `versionCode` scheme, R8 rules, and a release variant pointed at `prod`. Upgrade-install test from 0.8.0 preserves activation and settings (NFR-014).
  - *Verify:* TC-158.
- [ ] **DEV-096** Pilot at one merchant (≥ 5 trading days): provision per `docs/provisioning.md`, observe OBJ-1/OBJ-2, and record issues. Pilot report in `docs/pilot-report.md`.
- [ ] **DEV-098** Bug-fix buffer: fix all P1/P2 defects found in the pilot and soak. Re-run the affected TCs.

### Acceptance criteria
- FR-017/FR-018 pass against the real sandbox/terminal.
- NFR-004, NFR-005, NFR-006, NFR-014 and NFR-015 pass.
- Pilot meets OBJ-1 to OBJ-3, with OBJ-2 observed.
- Zero open P1/P2 defects.

### Testing requirements
- Full regression (automated + manual) on the target device against staging; real-payment tests with sandbox/test cards; soak; security checklist; pilot observation.
- TCs: all MVP TCs, with emphasis on TC-074–TC-087, TC-145–TC-149, TC-158, TC-159.

### Deliverables
Release-candidate APK `0.9.0`; soak report; security review; pilot report; final contract version.

### Exit criteria
The release checklist (testing-plan §9) is complete except for the final sign-off items. The PO approves going to 1.0.0.

---

## Version 1.0.0 — Release

### Objective
Ship the validated MVP to production kiosks.

### Scope
**Included:** Final regression on the release build against `prod`, version bump, signed APK, release notes, operational documentation, rollout via MDM.
**Excluded:** New work. Anything not done moves to the 1.0.x/1.1 backlog (§4).

### TODO checklist
- [ ] **DEV-102** Final regression on the signed `1.0.0` build against `prod`, with one real low-value QRIS and one real card transaction (refunded through the gateway) per the release checklist.
- [ ] **DEV-100** Set `versionName 1.0.0` and `versionCode 10000`. Produce the signed APK, record its SHA-256 checksum, and tag `v1.0.0` in git. Write release notes in EN and ID.
- [ ] **DEV-101** Ops documentation: `docs/provisioning.md` (final), `docs/runbook.md` (staff troubleshooting: paper, printer offline, terminal offline, network, sync, restart, PIN lockout), and `docs/rollback.md` (MDM rollback to 0.9.x).
- [ ] **DEV-103** Staged MDM rollout: pilot kiosks first, 48 h watch, then the remaining kiosks.

### Acceptance criteria
Every MVP FR's acceptance criteria and every NFR target are verified on the release build (testing-plan §8 matrix fully green). The release checklist is signed by the developer and the PO.

### Testing requirements
Full release regression (testing-plan §7, version 1.0.0) on the signed APK.

### Deliverables
Signed `hitachi-kiosk-1.0.0.apk` + checksum; git tag `v1.0.0`; release notes; runbook; provisioning and rollback docs.

### Exit criteria
Rolled out to pilot kiosks with no P1/P2 issues in 48 h. The full rollout is approved.

---

## 4. Deferred beyond 1.0.0

| Item | PRD ref | Earliest |
|---|---|---|
| Food-court mode (one merchant per order) | FR-005, D-07, D-08 | 1.1.0 |
| Pay at counter | D-12a | 1.1.0 |
| Merchant rebinding from settings (if OQ-07 is not resolved by 0.7.0) | OQ-07 | 1.0.1 |
| Sold-out display variant | OQ-08 | 1.0.x |
| Dark theme | D-02 | Not planned |
| Telemetry / crash reporting | D-23 | 1.1.0 (decision needed) |
| Multiple promos / promo types beyond threshold | A-12 | 1.1.0 |
| Kitchen display / order-ready screen | D-10 | Backend product |

## 5. Task index (DEV → requirement)

| DEV | Version | Requirements |
|---|---|---|
| DEV-001 | 0.1.0 | NFR-001, NFR-011, NFR-014 |
| DEV-002 | 0.1.0 | NFR-011 |
| DEV-003 | 0.1.0 | NFR-002, FR-026 |
| DEV-004 | 0.1.0 | NFR-009, NFR-007 |
| DEV-005 | 0.1.0 | NFR-012 |
| DEV-006 | 0.1.0 | §10, DEP-1 |
| DEV-007 | 0.1.0 | NFR-010, NFR-005 |
| DEV-008 | 0.1.0 | NFR-011 |
| DEV-009 | 0.1.0 | NFR-012 |
| DEV-010a | 0.1.0 | §5.1 |
| DEV-019 | 0.1.0 | NFR-011 |
| DEV-010 | 0.2.0 | FR-001 |
| DEV-011 | 0.2.0 | FR-001 |
| DEV-012 | 0.2.0 | FR-002 |
| DEV-013 | 0.2.0 | FR-003 |
| DEV-014 | 0.2.0 | FR-003, NFR-005 |
| DEV-015 | 0.2.0 | FR-004 |
| DEV-016 | 0.2.0 | FR-004 |
| DEV-017 | 0.2.0 | FR-005 |
| DEV-018 | 0.2.0 | FR-001–FR-004 |
| DEV-020 | 0.3.0 | FR-008, FR-010, FR-011 |
| DEV-021 | 0.3.0 | FR-028 |
| DEV-022 | 0.3.0 | FR-028 |
| DEV-023 | 0.3.0 | FR-028, NFR-013 |
| DEV-024 | 0.3.0 | FR-006 |
| DEV-025 | 0.3.0 | FR-007 |
| DEV-026 | 0.3.0 | FR-008 |
| DEV-027 | 0.3.0 | FR-009 |
| DEV-028 | 0.3.0 | FR-009, FR-010 |
| DEV-029 | 0.3.0 | FR-010 |
| DEV-030 | 0.3.0 | FR-011 |
| DEV-031 | 0.3.0 | NFR-003 |
| DEV-035 | 0.4.0 | FR-013 |
| DEV-036 | 0.4.0 | FR-012 |
| DEV-037 | 0.4.0 | FR-012 |
| DEV-038 | 0.4.0 | FR-013, FR-015 |
| DEV-039 | 0.4.0 | FR-014 |
| DEV-040 | 0.4.0 | FR-023 |
| DEV-041 | 0.4.0 | FR-012, FR-023, FR-008 |
| DEV-045 | 0.5.0 | FR-016 |
| DEV-046 | 0.5.0 | FR-016, FR-013 |
| DEV-047 | 0.5.0 | FR-017, NFR-017 |
| DEV-048 | 0.5.0 | FR-017 |
| DEV-049 | 0.5.0 | FR-018 |
| DEV-050 | 0.5.0 | FR-019 |
| DEV-051 | 0.5.0 | FR-028, NFR-010 |
| DEV-052 | 0.5.0 | FR-016 |
| DEV-053 | 0.5.0 | DEP-2, DEP-3 |
| DEV-055 | 0.6.0 | FR-022 |
| DEV-056 | 0.6.0 | FR-022, NFR-016 |
| DEV-057 | 0.6.0 | FR-021 |
| DEV-058 | 0.6.0 | FR-021 |
| DEV-059 | 0.6.0 | FR-021, FR-020 |
| DEV-060 | 0.6.0 | FR-020 |
| DEV-061 | 0.6.0 | FR-022 |
| DEV-065 | 0.7.0 | FR-024–FR-027 |
| DEV-066 | 0.7.0 | FR-024 |
| DEV-067 | 0.7.0 | FR-024, FR-031 |
| DEV-068 | 0.7.0 | FR-025 |
| DEV-069 | 0.7.0 | FR-026 |
| DEV-070 | 0.7.0 | FR-027 |
| DEV-071 | 0.7.0 | FR-029 |
| DEV-072 | 0.7.0 | FR-030 |
| DEV-073 | 0.7.0 | FR-031, NFR-006 |
| DEV-080 | 0.8.0 | FR-008, NFR-008 |
| DEV-081 | 0.8.0 | NFR-007 |
| DEV-082 | 0.8.0 | NFR-003 |
| DEV-083 | 0.8.0 | NFR-009 |
| DEV-084 | 0.8.0 | NFR-002, NFR-009 |
| DEV-085 | 0.8.0 | FR-026 |
| DEV-090 | 0.9.0 | FR-017 |
| DEV-091 | 0.9.0 | FR-018 |
| DEV-092 | 0.9.0 | NFR-016 |
| DEV-093 | 0.9.0 | NFR-004, NFR-015 |
| DEV-094 | 0.9.0 | NFR-005, NFR-006 |
| DEV-095 | 0.9.0 | NFR-014 |
| DEV-096 | 0.9.0 | OBJ-1–OBJ-3 |
| DEV-097 | 0.9.0 | FR-013, §10 |
| DEV-098 | 0.9.0 | all |
| DEV-100 | 1.0.0 | NFR-014 |
| DEV-101 | 1.0.0 | FR-029, operations |
| DEV-102 | 1.0.0 | all MVP |
| DEV-103 | 1.0.0 | release |
