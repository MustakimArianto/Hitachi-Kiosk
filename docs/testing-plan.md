# Hitachi Kiosk — Testing Plan

| | |
|---|---|
| Document version | 1.8 (2026-10-25) — 0.6.0 as built: the PO removed printing and settings work from 0.6.0 [D-36], so TC-091–TC-095, TC-099 and TC-100 are removed from 0.6.0 and not scheduled (OQ-19 asks the PO whether printing is built later or dropped from 1.0), TC-096–TC-098 and TC-159's printer part wait for that answer, and TC-088–TC-090 stay (Complete has no print-failed card); TC-198–TC-200 and TC-167/TC-170/TC-171 follow the built Budget Plan and the design ("Minimum Rp 1.000" button label, "More than enough" in the hero title, one "Main + drink" row per person); TC-201–TC-205 name the cart-limit message, the Menu pill, Edit plan, the combo side rule and the meal card as built; 1.7: 0.6.0 rework (Upsell is a strip on Your order, so TC-062–TC-064 and TC-172 are retired and TC-194–TC-197 and TC-205 replace them [D-33]; TC-198–TC-204 cover the Budget Plan improvements [D-35]); 1.6: 0.5.0: TC-185–TC-193 cover the stale payment state, the pending-payment guard, the terminal cancel, the bounded Processing, the amount guards, the unvalidated-network grace, the recovery start and rules, and the Room migration; TC-073 stays partly manual; the 0.5.0 manual checks are listed as not run in `test-reports/0.5.0.md`. 1.5: 0.4.0: the pricing and Budget Plan case tables are checked by tests (TC-060, TC-161–TC-165); Upsell shows once per order with the no-suggestions fallback (TC-172, PO); TC-104's staff-settings timeout runs with the PIN gate in 0.7.0. 1.4: 0.3.0 changes; benchmark TCs move to 1.0.0 |
| Requirements | [`product-requirement.md`](product-requirement.md) |
| Development roadmap | [`dev-plan.md`](dev-plan.md) |
| API contract | [`api/api-spec.md`](api/api-spec.md) |
| Technical requirements | [`technical-requirement.md`](technical-requirement.md) (TR-), with the TC that verifies each |

A feature is **not** done because it compiles or looks right. It is done when its PRD acceptance criteria pass, through the test cases mapped below, with the evidence listed for its milestone.

---

## 1. Strategy

### 1.1 Objectives
1. Prove every MVP functional requirement (FR-001…FR-032) meets its acceptance criteria.
2. Guarantee money correctness: pricing, order creation and payment state, with zero tolerance for wrong charges.
3. Prove kiosk robustness: lifecycle, process death, connectivity loss, hardware faults, 72 h unattended operation.
4. Prove fidelity to the approved light design (layout, copy, motion, and every state board) and to accessibility and string-externalisation rules. The UI is English only in 1.0 (D-29).
5. Catch regressions on every change through automation in the local `./gradlew verify` gate (no CI in 1.0, D-31).

### 1.2 Scope
**In:** The Android app (all modules), its behaviour against the API contract (mock, staging, prod), its integration with the iMin device, printers and the EDC terminal.
**Out:** Testing of the backend itself, the payment gateway's internal behaviour, and the merchant dashboard. These are owned by other teams; we test only our contract against them.

### 1.3 Test levels and responsibilities

| Level | What | Tools (justification) | Where it runs | Owner |
|---|---|---|---|---|
| Unit | `:core:domain` pricing, cart, state machines, formatters; ViewModels; repositories with fakes | JUnit 4, kotlinx-coroutines-test, Turbine (Flow assertions), Truth, kotest-property (property tests TC-061 and FR-032 AC8, used inside JUnit 4) | JVM, `verify` before every merge | Developer |
| Integration (data) | Retrofit + serialization against recorded API examples; Room DAOs; DataStore; WorkManager | MockWebServer (contract fixtures copied from `api-spec.md`), Room in-memory, `WorkManagerTestInitHelper` | JVM/Robolectric or emulator, `verify` | Developer |
| Contract | App fixtures vs `openapi.yaml`, and the app's client against the real staging backend | Generated contract test suite (0.9.0) | `verify` + staging | Developer + backend team |
| UI (component/screen) | Compose screens per state, navigation, input handling | `androidx.compose.ui.test` with fakes via Hilt test modules | Local emulator, before every merge | Developer |
| Screenshot | Visual regression of every screen and state (the 20 screen artboards and the 33 `S_*` state boards in `docs/ui-design/`), larger text | Roborazzi (JVM, fast enough to run in every `verify`; Paparazzi acceptable) | JVM, `verify` | Developer |
| End-to-end | Journeys J1–J8 across screens with fakes or the mock server | Compose UI test + UI Automator (system dialogs, lock task, kill process) | Emulator nightly; device per milestone | Developer |
| Hardware | SN, printers (built-in/USB/BT), EDC terminal, lock task, boot | Manual scripts with photo/video evidence; UI Automator where possible | iMin device | Developer / tester |
| Performance | Startup, jank, memory | Macrobenchmark, JankStats, Android Studio profiler, `dumpsys meminfo` | iMin device (authoritative), emulator (trend) | Developer |
| Accessibility | Labels, targets, contrast, TalkBack | Accessibility checks in Compose tests (`enableAccessibilityChecks`), Accessibility Scanner, manual TalkBack | Emulator + device | Developer |
| Exploratory / pilot | Real customers at one merchant | Observation sheet | Pilot site (0.9.0) | PO + developer |

No new test dependency may be added beyond those listed without noting the reason in the PR.

### 1.4 Manual vs automated
- **Automate:** everything deterministic. That covers domain logic, data, UI states, navigation, screenshots, and the fake-driven payment and failure paths.
- **Manual (scripted, with evidence):** physical printing (only if printing is built, OQ-19), real EDC and card handling, real QRIS scans with e-wallets, lock-task escape attempts, reboot behaviour, motion feel versus design, PO copy review, and the pilot.
- **Target:** ≥ 80 % of the TCs below run automatically (in `verify` or the local instrumented suite) by 0.8.0.

### 1.5 Fakes and debug tooling
Every external dependency has a scriptable fake (dev-plan DEV-005). The debug-only Developer options (DEV-009) switch between fake and mock-server modes and select scenarios. Instrumented tests inject the backend, serial-number and connectivity fakes through `FakeBindingsTestModule` (technical-design §14). The mock server supports the scenarios in `api-spec.md` §4 (`X-Mock-Scenario`). The debug-only Checkout "simulate" bar (FR-019) forces payment outcomes, and the debug `SwitchableCardTerminal` (Developer options → Card terminal) picks the fake terminal's answer. Release builds contain none of these (TC-148).

---

## 2. Environments

### 2.1 Backend environments
| Env | Used from | Purpose |
|---|---|---|
| In-app fakes | 0.1.0 | Unit/UI tests, offline development |
| Mock server (PO-built from `api-spec.md`) | 0.1.0+ | E2E against HTTP, scenario testing |
| Staging backend + gateway sandbox | 0.9.0 | Contract, integration, soak, pilot rehearsal |
| Production | 1.0.0 | Final regression with real low-value transactions |

### 2.2 Test data
- **Menu and config:** the design sample data (`api-spec.md` §3 E5/E6, §4) is the canonical fixture. Fixtures live in `:core:testing/src/main/resources/fixtures/` and are byte-identical to the spec examples.
- **Pricing table:** `docs/pricing-cases.md` (0.4.0) holds ≥ 20 carts with expected subtotal, discount, tax, rounding and total; `PricingCasesTest` checks every row (TC-060).
- **Budget Plan table:** `docs/budget-plan-cases.md` (0.4.0) holds the planner cases on the sample menu, computed by an independent exhaustive search; `BudgetPlanCasesTest` checks every row (TC-161–TC-165).
- **Staff PIN:** `1234` in mock/fakes. Never a real merchant PIN in tests or logs.
- **Devices:** SN `IMN24K0003118` (registered) and `IMN24K9999999` (unregistered) in the mock.
- **Payments:** gateway sandbox test cards and QRIS test wallets (DEP-2) in 0.9.0. Production: one real QRIS and one real card transaction of the lowest menu price, refunded (DEV-102).

### 2.3 Device and OS matrix
| Target | API | Display | Used for | From |
|---|---|---|---|---|
| Emulator "Kiosk-30" | 30 | 1080×1920, 320 dpi | minSdk floor, instrumented suite | 0.1.0 |
| Emulator "Kiosk-34" | 34 | 1080×1920, 240 dpi | Instrumented suite | 0.1.0 |
| Emulator "Kiosk-35/36/37" | 35, 36, 37 (37 = `targetSdk`, TR-PLT-04) | 1080×1920, 160 dpi | Compatibility, latest target | 0.3.0 |
| **iMin target kiosk** (model per OQ-01) | device (≈ 34+) | device native | Hardware, performance, soak, pilot: **authoritative** | 0.2.0 (SN spike) |
| Printers | — | — | iMin built-in; 2 USB ESC/POS (58 mm + 80 mm); 2 Bluetooth ESC/POS | Not scheduled: printing is not built in 0.6.0 and the PO decides whether it is built later or dropped from 1.0 (D-36, OQ-19) |
| EDC terminal | — | — | Model per OQ-03 | 0.9.0 |

0.1.0 ran its instrumented suite on a Pixel 10 AVD (API 37, 1080 × 2424, letterboxed) because no API 30/34 image was installed; `scripts/create-avds.sh` creates Kiosk-30 and Kiosk-34. JVM screenshot tests run on Robolectric SDK 36 (technical-design §16).

Orientation is locked portrait (NFR-002). Densities 160/240/320 prove the `DesignUnit` scaling. Unsupported aspect ratios are checked once for letterboxing (TC-141).

---

## 3. Defect severity and handling

| Severity | Definition | Examples | Rule |
|---|---|---|---|
| **P1 Blocker** | Money wrong, data loss, crash in the customer flow, kiosk escape, or no way to order | Wrong total charged; order marked paid without `PAID`; duplicate order; lock-task escape; crash on Menu | Blocks milestone exit and release. Fix immediately. |
| **P2 Critical** | A major feature broken without a reasonable workaround | Card flow fails; receipt never prints; idle timeout cancels during payment; a Checkout state with no designed screen (for example expired QR) | Blocks milestone exit and release. |
| **P3 Major** | Feature impaired, workaround exists, or a clear deviation from design/AC | Wrong animation timing; staff status not refreshing; minor copy mismatch on a key screen | May exit a milestone if tracked with a target version. Must be 0 open on checkout/payment screens at 1.0.0. |
| **P4 Minor** | Cosmetic, low impact | 2 dpx misalignment in settings | Track. Does not block. |

Every defect records: build, device, steps, expected/actual, evidence (screenshot/video/logcat), and linked FR/TC.

---

## 4. Test case conventions

- IDs `TC-001…TC-205` are stable. New cases append after TC-205 and are never renumbered. A retired case keeps its row, marked *Retired*, so references stay valid.
- **Type:** U = unit, I = integration, C = contract, UI = Compose UI test, S = screenshot, E2E, M = manual, D = manual on the iMin device/hardware, B = benchmark.
- **Pri:** H / M / L (test priority, distinct from defect severity).
- **Auto:** Y = automated (`verify` or the local instrumented suite); P = partly automated; N = manual.
- "Fakes" means the in-app fakes with the given scenario. "Mock" means the mock server scenario from `api-spec.md` §4.

---

## 5. Test cases

### 5.1 Startup, activation and merchant (FR-001 – FR-005)

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-001 | FR-001 | Route: not activated | Fresh install | Launch | Splash animates; SCR-02 Sign in appears at 3.9–5.0 s | UI | H | Y |
| TC-002 | FR-001 | Route: activated, no merchant / bound | Fakes: token stored; binding absent, then present | Launch twice (once per state) | SCR-03, then SCR-04 | UI | H | Y |
| TC-003 | FR-001 | Offline start with cache | Bound, cache present, network off | Launch | Idle within 10 s from cached data | UI | H | Y |
| TC-004 | FR-001 | Reduced motion and corrupt storage | (a) Reduce motion on; (b) corrupt DataStore file | Launch | (a) No animation frames but routing unchanged; (b) routes to SCR-02 and logs a local error, no crash | UI | M | Y |
| TC-005 | FR-002 | SN matches device label | iMin device | Read SN in Developer options | Equals the label / system-settings serial | D | H | N |
| TC-006 | FR-002 | SN stable | iMin device | Clear data, reinstall, reboot; read SN each time | Identical value | D | H | N |
| TC-007 | FR-002 | SN unavailable / slow | Fake: unavailable; slow 5 s | Open Sign in | Unavailable → message and Log in disabled; slow → button busy, then enabled | UI | M | Y |
| TC-008 | FR-003 | Activation success | Mock: default | Tap Log in | "Signing in…" spinner; SCR-03 within 3 s | E2E | H | Y |
| TC-009 | FR-003 | Not registered | Mock `activate_unregistered` | Tap Log in | Error message with the SN shown; Try again works | UI | H | Y |
| TC-010 | FR-003 | Disabled device | Mock `activate_disabled` | Tap Log in | Disabled message; button re-enabled ≤ 300 ms | UI | M | Y |
| TC-011 | FR-003 | Network timeout | Mock `slow_network` (> 15 s) | Tap Log in | Network message after timeout; button re-enabled | I | M | Y |
| TC-012 | FR-003 | Double tap | Mock default | Double-tap Log in quickly | Exactly one E1 request recorded | UI | H | Y |
| TC-013 | FR-003 | Activation persists; 401 revokes | Activated | Kill and restart; then make the mock return 401 on E5 | Restart skips SCR-02; 401 → SCR-02 and token cleared | E2E | H | Y |
| TC-014 | FR-004 | Merchant list renders | Mock E2 sample | Open SCR-03 | 1 live card ("Urban Grill", "Burgers & grill", "Open now"); a compact "Coming soon" row with its initial for each of the 4 coming-soon merchants, the list scrolling where needed; matches screenshot | S | M | Y |
| TC-015 | FR-004 | Coming soon not tappable | — | Tap "Bella Pasta"; TalkBack focus it | No navigation; announced as disabled | UI | M | Y |
| TC-016 | FR-004 | Bind live merchant | Mock default | Tap Urban Grill | E3 called, config + menu fetched, Idle shows "Urban Grill" with the accent within 10 s | E2E | H | Y |
| TC-017 | FR-004 | Menu download fails after bind | Mock: E6 → 503 | Tap Urban Grill | Stays on SCR-03 with error and retry; retry succeeds → Idle | E2E | H | Y |
| TC-018 | FR-004 | Log out / empty list | (a) Bound; (b) E2 returns [] | (a) Log out, restart; (b) open SCR-03 | (a) SCR-02 after restart; (b) "No restaurants yet" with "This account isn't linked to any merchant…" (`S_Merchants_empty`) | UI | M | Y |
| TC-019 | FR-005 | Single mode only | Config `mode: SINGLE` | Walk J1 | No customer merchant picker anywhere | U/UI | L | Y |
| TC-182 | FR-003 | Other sign-in failure | Fakes: E1 → 400 `VALIDATION_ERROR`, then 503, then an unreadable answer | Tap Log in each time | "Couldn't sign in" with "Try again"; the button is tappable again | U/UI | M | Y |
| TC-183 | FR-004 | Merchant list fails to load | Fakes: E2 → network error (after its 1/2/4 s retries) | Open SCR-03, then Retry with E2 healthy | "Couldn't load restaurants" with Retry and Log out; Retry shows the skeleton, then the list | U/UI | M | Y |
| TC-184 | FR-003, FR-004 | Merchant pre-assigned in the dashboard | Fakes: E1 `boundMerchantId: mer_urbangrill`; then the same with Urban Grill closed | Tap Log in | Open: E3, E5, E6 run and Idle shows without SCR-03; closed: SCR-03 with the closed card | U | M | Y |

### 5.2 Idle, order type, language (FR-006 – FR-008)

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-020 | FR-006 | Slideshow timing | 3 idle promos | Observe 25 s (test clock) | Advances every 7 s ± 100 ms; progress dots fill linearly; crossfade 1.4 s | UI | M | Y |
| TC-021 | FR-006 | Tap to order | Idle | Tap centre | SCR-05 within 300 ms | UI | H | Y |
| TC-022 | FR-006 | Staff hold boundaries | Idle | Hold the corner 1.9 s; then 2.0 s; then a short tap | 1.9 s: nothing; 2.0 s: SCR-14; short tap: no order started | UI | H | Y |
| TC-023 | FR-006 | Idle resets the session | Cart with 3 items, order type Takeaway | Cancel from Menu | Idle; then start a new order → cart empty, order type not preselected | E2E | H | Y |
| TC-024 | FR-006 | No promo images | `idlePromos: []` | Open Idle | Tone placeholder; no crash; "Tap to order" works | UI | L | Y |
| TC-025 | FR-007 | Select dine in | — | Tap Dine in | Accent fill, check pop, other card at 45 %; Menu at 950 ± 100 ms; header "Dine in" | UI | H | Y |
| TC-026 | FR-007 | Double selection ignored | — | Tap Dine in then Takeaway within 500 ms | Dine in kept; one navigation | UI | H | Y |
| TC-027 | FR-007 | Only one type enabled | Takeaway off (FR-026) | Open SCR-05 | Only Dine in shown | UI | M | Y |
| TC-028 | FR-007 | Change type keeps cart | Cart 2 items, Dine in | Menu → Change → Takeaway | Menu header "Takeaway"; cart still 2 items | E2E | M | Y |
| TC-029 | FR-008 | *Retired* — the EN/ID segmented control no longer exists on SCR-05 (language switching deferred, D-29). Replaced by TC-174. | — | — | — | — | — | — | — |
| TC-030 | FR-008 | *Retired* — no per-order language. Cart and order type persistence is covered by TC-028 and TC-139. | — | — | — | — | — | — | — |
| TC-031 | FR-008 | *Retired* — no language to reset. Session reset to Idle is covered by TC-023 and TC-103. | — | — | — | — | — | — | — |
| TC-032 | NFR-008 | String externalisation | Lint | Build; scan the code | Lint `HardcodedText` = 0; every customer and staff string is in `values/strings.xml`; no `values-in` exists; a product with only `en` text shows it, and one with only `id` falls back to `id` (never blank) | U/I | H | Y |

### 5.3 Menu, item, combo (FR-009 – FR-011)

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-033 | FR-009 | Menu default view | Sample menu | Open Menu | "Popular" active; slim "Plan by budget" row (and no promo carousel); "Popular now · 4 items" (b2, c1, r1, d1); matches screenshot | S | H | Y |
| TC-034 | FR-009 | Category switch | — | Tap "Chicken" | Grid shows c1–c4, heading "Chicken · 4 items", scroll at top, rendered ≤ 300 ms | UI | H | Y |
| TC-035 | FR-009 | Meals pill | — | Tap "Meals ↗" | Combo screen | UI | M | Y |
| TC-036 | FR-009 | *Retired* — the Menu promo carousel was removed from the design | — | — | — | — | — | — | — |
| TC-037 | FR-009 | Badges, kcal, price format | Sample menu | View Burgers | "New" in accent on b2, "Spicy" in ink on b3; "Rp 52.000"; no kcal line on grid cards (Item detail shows the "780 kcal" tag) | S | M | Y |
| TC-038 | FR-009 | Quick add + merge | Empty cart | Tap + on c1 twice; + on d1 | Check state 1.4 s; fly animation; dock "3 items · Rp 92.000"; c1 one line qty 2 | UI | H | Y |
| TC-039 | FR-009 | Required choice blocks quick add; Cancel | Product with a required group that has no default (b2, c4, r1, d2) | Tap the "Choose ›" pill on it; then Cancel | Opens Item detail instead of adding (no "+" button on that card); Cancel → Idle with empty cart | UI | M | Y |
| TC-040 | FR-010 | Item price calculation | b2 (size Regular) | Turn on "Make it a meal"; expand Customize and set cheddar Extra; expand Add extras and tick Fried egg; qty 1 | CTA "Add to order · Rp 81.000"; closed rows show a summary again when collapsed | UI/U | H | Y |
| TC-041 | FR-010 | Stepper limits | — | Tap − at 1; tap + 25 times | Stays 1 (− disabled style); stops at 20 | UI | H | Y |
| TC-042 | FR-010 | Level indicator colors | — | Expand Customize; set Onion None, Pickles Regular, Cheddar Extra | Indicator white / ink / accent; collapsed summary reads the changes | S | M | Y |
| TC-043 | FR-010 | Add to order → added state | — | Add | Dock swaps to ✓, "Keep ordering", "View order"; cart badge bumps; line description "Fries · Cola · Extra cheddar · No onion" style | UI | H | Y |
| TC-044 | FR-010 | Required group unselected | Product with required group, no default (`S_ItemDetail_requiredMissing`) | Open | "Required" tag; CTA disabled and reads "Choose a size to continue"; group highlighted | UI | M | Y |
| TC-045 | FR-010 | Merge cap | Line b2 (same options) qty 18 | Add qty 5 identical | Line becomes 20; "Maximum 20 per item" shown | U/UI | M | Y |
| TC-046 | FR-011 | Combo defaults | — | Open Combo | Cheeseburger Meal, Fries, Cola, Regular selected; total Rp 56.000; "Save Rp 14.000"; Side and Drink show 3 options each with "More sides (1)" / "More drinks (1)" | UI | H | Y |
| TC-047 | FR-011 | Combo with upcharges | — | Cheese fries, Iced latte, Large | Total Rp 74.000; summary "Cheeseburger Meal · Cheese fries · Iced latte · Large" | U/UI | H | Y |
| TC-048 | FR-011 | Feast multiplier | — | Feast for Two, Nuggets, Large | Total Rp 129.000; labels "+Rp 8.000" (nuggets), "Large +Rp 12.000" | U/UI | H | Y |
| TC-049 | FR-011 | Add combo once | — | Tap Add combo twice quickly | One combo line; "Added" for 1.8 s, then back to "Add combo" | UI | M | Y |

### 5.4 Cart, pricing, upsell, method selection (FR-012 – FR-015)

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-050 | FR-012 | Increment/decrement | Design sample cart | + on Fries, − on Lychee tea | Line totals and summary update in the same frame; roll animation | UI | H | Y |
| TC-051 | FR-012 | Remove at qty 1 | Line qty 1 | Tap trash | Slides out (460 ms); totals update | UI | H | Y |
| TC-052 | FR-012 | Limits | 30 lines | Add a 31st distinct item | Refused with message; cart unchanged | U | M | Y |
| TC-053 | FR-012 | Empty state | Remove all lines | — | "Your tray is empty", "Add something tasty to get started.", "Back to menu"; Pay disabled (`S_Payment_emptyTray`) | UI | H | Y |
| TC-054 | FR-012 | Pluralization | 1 item, then 2 | View header (EN) | "1 item"/"2 items" | UI | L | Y |
| TC-055 | FR-013 | Design sample totals | 75.000 + 38.000 + 22.000 + 2×16.000 | Compute | Subtotal 167.000, discount 10.000, tax 15.700, total **172.700** | U | H | Y |
| TC-056 | FR-013 | Threshold boundary | Subtotal 99.999 / 100.000 | Compute | 99.999: no discount, hint "Add Rp 1 more…"; 100.000: discount 10.000 | U | H | Y |
| TC-057 | FR-013 | Tax rounding half-up | (subtotal − discount) = 12.345 | Compute | Tax 1.235 (1.234,5 → half-up) | U | H | Y |
| TC-058 | FR-013 | Rounding steps | Total 172.700 | Steps 0/100/500 | 172.700 / 172.700 / 173.000 with "Rounding Rp 300" | U | H | Y |
| TC-059 | FR-013 | IDR formatting | 0, 8.000, 1.234.567 | Format | "Rp 0", "Rp 8.000", "Rp 1.234.567" | U | H | Y |
| TC-060 | FR-013 | Pricing table | `docs/pricing-cases.md` (≥ 20 carts) | Run parametrised test | All expected values match | U | H | Y |
| TC-061 | FR-013 | Properties | Random carts | Property test (1,000 runs, kotest-property) | Total ≥ 0; integer only; total ≥ subtotal − discount; with a rounding step > 0, total is a multiple of the step and 0 ≤ rounding < step; tax follows the half-up rule. (The total is not monotonic in quantity: crossing the promo threshold can lower it.) | U | M | Y |
| TC-062 | FR-014 | *Retired in 0.6.0 (SCR-09 replaced by the strip, D-33; see TC-194).* Upsell off | Toggle off | View order | SCR-10 directly | UI | M | Y |
| TC-063 | FR-014 | *Retired in 0.6.0 (SCR-09 replaced by the strip, D-33; see TC-195, TC-196).* Upsell once per order | Toggle on, cart without sides/desserts | View order → No thanks → Add more → View order | Upsell (2 × 2 suggestions, "No thanks", "Continue · Rp X") shown the first time only | UI | H | Y |
| TC-064 | FR-014 | *Retired in 0.6.0 (see TC-196).* Upsell quick add | Toggle on | Add "Caramel Sundae" from upsell, Continue | Sundae line in cart with defaults; items already in cart not suggested | UI | M | Y |
| TC-065 | FR-015 | Methods shown | Card disabled in settings | Open SCR-10 | Only the QRIS tile (the Card tile is hidden); never an "At counter" tile | UI | H | Y |
| TC-066 | FR-015 | Pay label | Any cart | Change qty | Pay label always equals the formatted total | UI | H | Y |
| TC-067 | FR-015 | No method available | All methods unavailable (config) | Open SCR-10 | "No payment methods right now" / "Please ask a team member for help." and Pay disabled (`S_Payment_noMethods`) | UI | M | Y |

### 5.4b Budget Plan group meal planner (FR-032)

Test data: the tagged sample menu from `api-spec.md` §4 (kid-friendly mains b1, c3, r2), with promo Rp 10.000 off ≥ Rp 100.000, PB1 10 %, rounding none, and `overCapPercent` 10. Expected values below were computed by exhaustive search over that menu.

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-161 | FR-032 | Under/over bracket | 2 adults + 1 child, budget Rp 150.000 | Run planner | Under total **Rp 149.600** (badge "Rp 400 left"); over total **Rp 150.700** (badge "+Rp 700 over") ≤ cap Rp 165.000; each adult has 1 main + 1 drink (or a combo); the child has a kid-friendly main + drink | U | H | Y |
| TC-162 | FR-032 | Promo-threshold twist and exact match | 1 adult + 1 child, budget Rp 99.000 | Run planner; open SCR-19 Step 2 | Total **Rp 99.000** with subtotal **Rp 100.000** (promo applied) is chosen over the Rp 90.000-subtotal plan with the same total; because it equals the budget it is the single "Exact match" result (see TC-169) | U/UI | H | Y |
| TC-163 | FR-032 | Infeasible budget | 2 adults + 1 child, budget Rp 100.000 | Run planner; open SCR-19 | One plan, total **Rp 116.600**, badge "Need Rp 16.600 more" on the closest complete plan; no Just over option (`S_BudgetPas_tooLow`) | U/UI | H | Y |
| TC-164 | FR-032 | Large budget and over cap | (a) 1 adult, budget Rp 1.000.000; (b) any case where the nearest over plan is > cap | Run planner | (a) never more than 1 main per person; badge "More than enough" (`S_BudgetPas_moreThanEnough`); (b) the Just over option is hidden | U | M | Y |
| TC-165 | FR-032 | Children's meals | (a) sample menu; (b) menu with no `kidFriendly` main | 1 adult + 2 children, budget Rp 120.000 | (a) both children get only b1, c3 or r2; (b) children get the cheapest main and the kid-menu fallback note defined in PRD FR-032 (the design boards do not show it) | U | H | Y |
| TC-166 | FR-032 | Group limits and inputs | `maxGroupSize` 10 | Set 8 adults + 3 children; set adults 0; clear budget | 11 people → "Please order in smaller groups" with "Set group to 10", no plans, dock disabled (`S_BudgetPas_group`); adults minimum 1; empty budget → "See plans" disabled | UI | M | Y |
| TC-167 | FR-032 | Use plan, swap, toggle | Mock default; then `budgetPlanner.enabled=false` | Open via the Menu "Plan by budget" row; Step 1 → "See plans"; pick the under plan; tap Swap on one drink and pick a row; "Add plan to order"; then disable in config and reopen Menu | Cart lines and totals on SCR-10 equal the plan and show the "Budget Plan plan" tag and, once, the green "Plan added" pill (2.6 s); the swapped plan's total is the picker row's total (TC-200); with the toggle off, no pill and no entry row. *Re-recorded in 0.6.0: the Customize sheet and the Strict control are gone (replaced by the Step 2 options and the swap sheet, DEV-107–DEV-109, TC-198–TC-200).* | UI | H | Y |
| TC-168 | FR-032 | Performance | iMin device; 10 adults, sample menu, budget Rp 600.000 | Benchmark 50 runs | P95 ≤ 100 ms | B | M | Y |
| TC-169 | FR-032 | Exact-match hero card | 1 adult + 1 child, budget Rp 99.000 | Open SCR-19, Step 1 → "See plans" | One hero card with the "Exact match" badge and "Perfect fit! Rp 99.000 · Rp 0 left"; no Just under / Just over control; dock "Add plan to order · Rp 99.000" (`S_BudgetPas_exact`) | UI/S | H | Y |
| TC-170 | FR-032 | Options, swap and Reset (was: Customize sheet and Strict) | 2 adults + 1 child, budget Rp 150.000 | On Step 2 tap Swap on Adult 1's meal and pick a row; switch "Add desserts" off; switch "Allow a little over budget" off; tap "Reset to suggested" | The picker header shows the plan total; a swap keeps the shared extras and the price change is the real one; the chips remove all sides or desserts of that kind; with the switch off only the plan within the budget shows; Reset restores the suggested plan. The planner behaviour (rebalancing `swap`, golden cases) is unchanged. *Re-recorded in 0.6.0: the Customize sheet and the Strict control are gone.* | U/UI | H | Y |
| TC-171 | FR-032 | Two-step flow and inputs | Sample menu | Step 1: tap chip Rp 100k, then "Other" and type 123456; adults 2, children 1; "See plans"; Back | Amount shows to the nearest Rp 100 ("Final total, tax included · exact to the nearest Rp 100"); Step 2 title "Your plan"; "What's included" is open and "Price details" is collapsed by default; Back returns to Step 1 with the budget and people kept *Re-recorded in 0.6.0: "What's included" is open by default.* | UI | M | Y |

### 5.4c Ordering UX of 0.6.0: Upsell strip and Budget Plan improvements (FR-014, FR-032)

Decisions: D-33 (the strip replaces SCR-09), D-35 (Budget Plan, fewer decisions). Boards (synced as design v26, built as `S_Payment_upsell*`, `S_BudgetPas_*`, `S_Menu_drinksTab`, `S_Menu_planOff`; the PO's sign-off, DEV-104, is open). All P2 tasks shipped in 0.6.0, so every row applies.

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-194 | FR-014 | Strip shown, toggle | Toggle on, then off; order with one main | Open Your order from the Menu; disable "Suggest add-ons" in settings; reopen | With the toggle on, "Goes well with your order" shows up to 3 cards and the first is a drink; with it off there is no strip, and there is no extra screen or tap in either case; the strip sits below the lines and is hidden while a Pay blocker (offline, price change, unavailable item, no method) or the empty tray shows | UI | H | Y |
| TC-195 | FR-014 | Gap rules and exclusions | Sample menu | Carts: (a) a main only; (b) a main and a drink; (c) a combo only; (d) a main, side, drink and dessert; (e) a main and a sold-out candidate | (a) drink, side, dessert; (b) side, dessert; (c) dessert only (a combo covers side and drink); (d) nothing, no strip; inside a gap the order is `plannerWeight`, featured, menu order; in-cart, sold-out and needs-a-choice items are never suggested | U | H | Y |
| TC-196 | FR-014 | Add and undo | Strip visible | Tap "+" on a drink card; tap "−" to 0; tap "+" twice | The line appears with default options and the total updates; the card shows "− 1 +" and returns to "+" at 0; a card the customer added keeps its slot while the other cards re-rank from the order (the design freezes the strip; the built app re-ranks it, a recorded deviation) | UI | H | Y |
| TC-197 | FR-014 | Budget-limited, every path, never blocking | An order from a Budget Plan with Rp 400 left; orders opened from Menu, Item and Combo | Open Your order from each | No suggestion costs more than Rp 400, the strip is hidden when none fits, and when cards show they carry "Fits your budget" (AC5); the same strip appears from every path (AC6); Pay is never blocked (AC4) | UI/U | H | Y |
| TC-198 | FR-032 | Step 1 defaults, caption, keypad | Sample menu | Open SCR-19; choose Rp 150k with 3 people; open "Other" and type 500; reopen "Other" | Opens with 2 adults, 0 children and no budget, "See plans" disabled and the dock "Choose a budget to continue"; caption "About Rp 50.000 per person"; the keypad shows thousand separators and "Minimum Rp 1.000" under the amount for 500, and the button reads "Minimum Rp 1.000" and is disabled (design `S_BudgetPas_keypadMin`); reopening shows the current amount (AC12) | UI/S | H | Y |
| TC-199 | FR-032 | Step 2 cards and options | 2 adults + 1 child, Rp 150.000 | "See plans"; select the second card; use the chips and the "Allow a little over budget" switch; use a budget that gives "More than enough" | Two visible cards (Rp 149.600 selected, Rp 150.700); "What's included" open with one row per person, each with Swap; the switch off shows one plan (AC10); the chips remove sides or desserts; for "More than enough" the hero title reads "Our menu tops out at Rp X for your group, which is under your budget." with the badge "Rp X under your budget" (AC13) | UI/S | H | Y |
| TC-200 | FR-032 | Swap picker (P2, built) | 2 adults + 1 child, Rp 150.000, combos on the menu | Tap Swap on Adult 1's row; pick an alternative; tap Swap on the shared side | One "Main + drink" row per person; the sheet lists the current choice first, then the alternatives with the price change and "Total Rp X", mixing combos and main-plus-drink pairs, and none goes below the current total or past the budget or the cap; shared extras are kept on a swap; the plan total and notes update (AC14) | U/UI | M | Y |
| TC-201 | FR-032 | Plan does not fit the cart | A cart with 29 lines | Add a plan of 3 lines | "Your order has too many items to add this plan" and the dock button "Review order" (it opens Your order); the cart is unchanged (AC15, `S_BudgetPas_cartFull`) | UI | M | Y |
| TC-202 | FR-032 | Entry on every tab | `budgetPlanner.enabled` true, then false | On the Menu open Drinks and Desserts and tap the header pill; open the empty Your order | The "Plan by budget" pill is in the Menu category bar on every tab and opens SCR-19; the empty Your order offers the entry; with the flag off neither shows (AC7, AC16; `S_Menu_drinksTab`, `S_Menu_planOff`, `S_Payment_emptyTrayNoPlan`) | UI | H | Y |
| TC-203 | FR-032 | Edit plan (P2, built) | A plan in the cart, plus a unit of one of its items the customer added before | On Your order tap "Edit plan" on the plan row; change the budget and add; separately edit a plan line by hand | Step 2 opens (`BudgetPlanRoute(edit)`) with the saved budget and group; only the plan's own units go out and the new plan comes in whole or not at all, so the other lines and the customer's earlier units stay; the dock keeps "Add plan to order" / "Added to order" (A-50); after a hand edit the tag is gone (A-40) | UI/U | M | Y |
| TC-204 | FR-032 | Combo diners and sides (OQ-15 resolved) | A menu with cheap combos (the sample menu's combos are not cheap enough to be chosen) | 2 adults with a budget that selects a combo for both | A combo diner has no extra side (sides allowance is people minus combo diners); desserts are unaffected; the "cap at min(people, 4)" half of OQ-15 is not applied, so the golden cases in `docs/budget-plan-cases.md` (one extra per person) and the planner and pricing suites still pass unchanged | U | M | Y |
| TC-205 | FR-014 | "Make it a meal" card (P2, built; OQ-17 resolved as `purpose` = `MEAL_UPGRADE`) | A main with the meal option off; the API marks the optional modifier group with `purpose` `MEAL_UPGRADE`; plan orders are never upgraded | Tap the "Make it a meal" strip card; tap "−" on it | One unit of the line switches its option on and its price rises by the option's `priceDelta` (`Cart.switchOne`); the line then counts as a meal (no side or drink suggested); "−" switches the unit back; without the marker there is no meal card; a plan order shows "Fits your budget" and no meal card (`S_Payment_upsellMeal`, `_upsellMealOn`, `_upsellBudget`) | UI/U | M | Y |

### 5.5 Order submission and payment (FR-016 – FR-019)

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-068 | FR-016 | Create order | Mock default | Tap Pay | E7 sent with `Idempotency-Key`, the lines and `clientTotals`; Checkout opens | I/E2E | H | Y |
| TC-069 | FR-016 | Rapid taps | — | Tap Pay 10× within 1 s | One E7 request, one order | UI | H | Y |
| TC-070 | FR-016 | Retry with same key | Mock `server_error_once` | Tap Pay | 503 then 201; both requests carry the same key; one order | I | H | Y |
| TC-071 | FR-016 | PRICE_CHANGED | Mock `order_price_changed` | Tap Pay | Back to SCR-10 with "Prices have been updated…"; totals updated; no payment started | E2E | H | Y |
| TC-072 | FR-016 | ITEM_UNAVAILABLE | Mock `order_item_unavailable` | Tap Pay | c1 line marked "No longer available"; Pay blocked until removed | E2E | H | Y |
| TC-073 | FR-016 | Process death during payment | Mock `qris_paid_after_5s`. Run once with "Don't keep activities" on | Pay; at 2 s `adb shell am kill`; relaunch | App queries E10 → Complete with correct number and the paid totals (a recovered order has no lines and no order type, so Complete's instruction line is neutral, A-49; nothing prints). The pending order is cleared only after Complete is shown, so a kill between Checkout and Complete also finds the paid order (TD-24). The logic is automated (TC-191, TC-192, `CompleteViewModelTest`); the real kill on a device is manual | E2E/D | H | P |
| TC-074 | FR-017 | QRIS display | Mock default | Reach Checkout | QR card with the logo mark, "Urban Grill · Kiosk 3", the order total as the amount (design sample with Rp 500 rounding: Rp 173.000), "Code expires in 5:00" with the expiry ring; **no scan line, no 1-2-3 step list and no "Step 3 of 3" indicator** | UI/S | H | Y |
| TC-075 | FR-017 | QRIS paid | Mock `qris_paid_after_5s` | Wait | "Confirming payment" ≥ 800 ms → Complete within 3 s of PAID; polling stops | E2E | H | Y |
| TC-076 | FR-017 | QRIS cancelled in app | Mock `qris_cancelled` | Wait | Failed: "The payment was cancelled in your app…"; "not charged" note | E2E | H | Y |
| TC-077 | FR-017 | QRIS expiry | Mock `qris_expire_fast` | Wait 20 s | E11 sent; Failed with expired copy; Try again → new paymentId and new QR | E2E | H | Y |
| TC-078 | FR-017 | Idle suspended during QR | Idle timeout 30 s | Leave Checkout untouched 60 s | No "Still there?" overlay | UI | H | Y |
| TC-079 | FR-017 | QR correctness | `qrString` sample | Render the QR composable with the merchant centre mark, decode the bitmap (ZXing) | Decoded text equals `qrString` | U | H | Y |
| TC-080 | FR-018 | Card approved | Fake terminal approve | Choose Card, Pay | Terminal receives the order total (172.700 or the rounded total) + ref; Processing → Complete only after backend PAID | E2E | H | Y |
| TC-081 | FR-018 | Card declined | Mock `card_declined` / fake decline | Pay | Failed "Your bank declined the card…" | E2E | H | Y |
| TC-082 | FR-018 | Terminal timeout | Fake no response | Wait 90 s (test clock) | Cancel sent to terminal; failure "The card terminal didn't respond…" | U/UI | H | Y |
| TC-083 | FR-018 | Real EDC | Sandbox test cards, real terminal | Approve, decline, cancel mid-tap | Results match; no card data in app logs | D | H | N |
| TC-181 | FR-018 | Card cancelled from "Back to order" | Fake terminal waiting for a card | Tap "Back to order" | `cancel()` sent to the terminal, then E11 + E8; SCR-10 with the cart intact (FR-018 AC3) | E2E | H | Y |
| TC-084 | FR-019 | Switch method | Failed (card) | Tap "Pay by QRIS instead" | Card payment cancelled; QRIS payment created for the **same** orderId | E2E | H | Y |
| TC-085 | FR-019 | Unknown status | Network drop right after PAID at gateway | Pay; kill network 10 s; restore | "Checking your payment…" (no "not charged"); resolves to Complete | E2E | H | P |
| TC-086 | FR-019 | Back to order | Pending QR | Tap "Back to order" | E11 + E8 sent; SCR-10 with cart intact; next Pay creates a new order (new key) | E2E | H | Y |
| TC-087 | FR-019 | No counter button / no simulate bar in release | Release APK | Inspect Failed state; `scripts/check-release-apk.sh` strings check | No "Pay at the counter" button; no "Prototype only" string or class | UI/U | H | Y |
| TC-185 | FR-016, FR-019 | Stale payment state of an earlier order | Fakes; an order that ended outside the machine (401 wipe, staff reset) while the machine shows `Failed` or a card sale is running | End the order outside the machine; start a new order and tap Pay | The machine goes Idle when the session ends: a running card sale is cancelled, nothing is sent to the backend, the pending order is kept, and `Paid` waits for the Complete hand-off. Checkout clears a stale `Failed` or `Paid` before it submits, so the new order is not stuck behind it | U | H | Y |
| TC-186 | FR-016 | A pending payment is never overwritten | Pending order that has a `paymentId` (a cancel that failed, a deferred recovery) | Tap Pay for a new order; then settle the pending payment and tap Pay again | The first Pay sends no E7: Checkout shows "Payment didn't go through" with "Can't reach the restaurant system…" and Try again, Back to order and Cancel order. After the pending payment is settled, a new order is created. A pending order with no payment is replaced | U/I | H | Y |
| TC-187 | FR-018 | Terminal cancelled when the backend ends the payment | Fake terminal waiting for a card; scripted E10 | E10 answers `FAILED`, `EXPIRED`, `CANCELLED` or `PAID` during the sale; separately a QR payment ends the same way | `cancel()` is sent to the terminal once, so it cannot charge twice; an E12 answer that is a final status needs no second cancel; a QR payment never touches the terminal | U | H | Y |
| TC-188 | FR-019 | "Confirming payment" is bounded | Test clock. (a) An approval whose E12 failed and E10 stays `PENDING`; (b) a QR whose E10 stays `PROCESSING`; (c) `PAID` at 59 s | Wait 60 s | (a) and (b): Failed without the "not charged" note, reason "network" when E10 stopped answering; Try again cancels the payment first. (c) Paid | U | H | Y |
| TC-189 | FR-016, FR-017, FR-018 | Amount guards | Fakes: E7 answers another total; E9 answers another amount (QR and card) | Tap Pay; then start a payment | E7 total differs: E8 is sent, nothing is paid, the unreachable failure shows. E9 amount differs: no QR or terminal prompt, E11 and E8 are sent, generic failure, and if the cancel fails the pending order stays for recovery. The matching amount is accepted | U | H | Y |
| TC-191 | FR-016 | Recovery starts without Splash | A bound kiosk with a pending paid order; Android restores the saved back stack to a screen after Splash | Start the process | `KioskApp.start()` loads the device state and recovers (the pending order is cleared, the app goes to Complete from Idle); Idle ignores "tap to order" while a run asks the backend and the gate always lifts; `StartupRouter` asking again changes nothing; a kiosk that is not bound is never recovered. The real kill on a device is TC-073 | U | H | Y |
| TC-192 | FR-016 | Recovery of the other stored states and of failures | Pending orders: no payment; `FAILED`; `PENDING`; `PROCESSING` that becomes `PAID`, a non-paid status or stays `PROCESSING`; E8, E10 or E11 failing | Run the recovery | As the table in architecture §5.3: `PENDING` → E11 then E8; `PROCESSING` is polled every 2 s for 60 s, then treated like `PENDING`; a 409 on E11 re-reads E10 and `PAID` wins; a failure leaves the order pending and the next ONLINE (or the 30 s timer while online) retries it; a customer order in progress is never disturbed; a second run finds nothing | U/I | H | Y |

### 5.6 Completion and printing (FR-020 – FR-022)

**0.6.0 scope (D-36, PO, 2026-10-25):** only Complete (TC-088–TC-090) is built and tested. Printing and the Printer settings screen are not part of 0.6.0, so TC-091–TC-095, TC-099 and TC-100 are removed from 0.6.0 and **not scheduled**; whether printing is built later or dropped from 1.0 is OQ-19 (PO). TC-096–TC-098 wait for the same answer. Complete shows a decorative receipt-printer illustration that is a demo visual only (PO: "only a demo, the receipt UI is still there"); nothing prints, there is no print-failed card (`S_Complete_printFailed` is not built) and FR-020 AC4 does not apply.

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-088 | FR-020 | Complete content | Paid order A247, QRIS, Dine in | Arrive | "Payment successful", "Thank you!", "Rp {total} paid with QRIS" (the design sample shows Rp 173.000 with Rp 500 rounding), "A247" with "A" in accent, the Dine in instruction line (copy to be confirmed by the PO, A-49), the 20 s countdown ring "Back to the start in Ns" and Done; the paid payment is handed over (acknowledged) when Complete is shown, not by Checkout (TD-24); an order recovered as paid shows the totals and a neutral instruction line (A-49) | UI/S | H | Y |
| TC-089 | FR-020 | Auto return / Done | — | Wait; separately tap Done | Idle at 20 ± 1 s; Done immediate; session cleared before Idle appears | UI | H | Y |
| TC-090 | FR-020 | Takeaway copy | Takeaway order (`S_Complete_takeaway`) | Arrive | "Pick up at the counter when your number is called." [A-18] | UI | L | Y |
| TC-091 | FR-021 | *Removed from 0.6.0 by the PO (D-36), not scheduled (OQ-19).* Receipt + kitchen ticket | Two printers with roles (as written when printing was planned) | Complete a paid order | One receipt and one kitchen ticket within 5 s | I/D | H | N |
| TC-092 | FR-021 | *Removed from 0.6.0 by the PO (D-36), not scheduled (OQ-19).* Kitchen ticket content | Fake printer capture | Paid order | Large number, type, time, lines + options + qty, **no prices** (golden text) | U | H | N |
| TC-093 | FR-021 | *Removed from 0.6.0 by the PO (D-36), not scheduled (OQ-19).* Receipt layouts | 58 mm and 80 mm | Render | Matches golden files; no truncated amounts; footer text; logo when enabled | U/D | H | N |
| TC-094 | FR-021 | *Removed from 0.6.0 by the PO (D-36), not scheduled (OQ-19).* Toggles off | Customer receipt off | Paid order | No receipt printed; kitchen ticket still printed | I | M | N |
| TC-095 | FR-021 | *Removed from 0.6.0 by the PO (D-36), not scheduled (OQ-19).* Print failure | Printer unplugged / out of paper | Paid order | Complete still shown with the card "Receipt couldn't print — Please show this screen at the counter." (`S_Complete_printFailed`); status "Offline"/"Out of paper" in settings; order remains paid | I/D | H | N |
| TC-096 | FR-022 | *Not scheduled: waits for the printing decision (D-36, OQ-19; DEV-056).* Built-in printer | iMin device | Add built-in, test page, feed | Detected; prints; paper % shown | D | H | N |
| TC-097 | FR-022 | *Not scheduled: waits for the printing decision (D-36, OQ-19; DEV-056).* USB printer | USB ESC/POS printer | Add USB (grant permission), test page | Prints; status online | D | H | N |
| TC-098 | FR-022 | *Not scheduled: waits for the printing decision (D-36, OQ-19; DEV-056).* Bluetooth printer + permission | Paired BT printer | Add BT; deny permission once, then grant | Denial → rationale + "Grant"; after grant prints | D | H | N |
| TC-099 | FR-022 | *Removed from 0.6.0 by the PO (D-36), not scheduled (OQ-19).* Role reassignment (fakes) | Printers P1 (Receipt), P2 | Assign Receipt to P2 | P1 loses Receipt; P2 has it; persists after restart | U/UI | H | N |
| TC-100 | FR-022 | *Removed from 0.6.0 by the PO (D-36), not scheduled (OQ-19).* Printer settings screen (against `FakePrinterManager`; one screenshot per printer board) | — | Open SCR-15 | Meter red below 15 %; Test page "Printing…" → "Printed"; footer input saved; width/density persisted | UI/S | M | N |

### 5.7 Inactivity (FR-023)

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-101 | FR-023 | Overlay at timeout | Timeout 60 s, on Menu with items | No touch 60 s (test clock) | "Still there?" with 15 s ring at 60 ± 1 s | UI | H | Y |
| TC-102 | FR-023 | Continue | Overlay shown | Tap | Overlay gone; timer reset; cart intact | UI | H | Y |
| TC-103 | FR-023 | Cancel on expiry | Overlay shown | Wait 15 s | Idle; cart empty | UI | H | Y |
| TC-104 | FR-023 | Excluded screens | Checkout pending, Complete, Settings | Wait beyond timeout | No overlay on any of them (Settings exits after 120 s). The overlay part runs from 0.4.0; the Settings 120 s exit runs with the PIN gate (DEV-066, 0.7.0) | UI | H | Y |

### 5.8 Staff settings and kiosk (FR-024 – FR-031)

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-105 | FR-024 | Correct PIN | PIN 1234 | Hold corner 2 s, type 1234 | Settings home | UI | H | Y |
| TC-106 | FR-024 | Wrong PIN | — | Type 1111 | Dots shake and clear; attempt counted | UI | H | Y |
| TC-107 | FR-024 | Lockout persists | — | 5 wrong; kill app; relaunch; open PIN | Locked with countdown; still locked after restart; unlocks after 5 min (test clock) | UI | H | Y |
| TC-108 | FR-024 | Lock / Exit / Cancel | Settings home | Tap Lock; then Cancel; reopen, Exit | Lock → PIN pad; Cancel → Idle; Exit → Idle | UI | M | Y |
| TC-109 | FR-024 | Settings home status | All healthy | Open | "All systems ready"; four rows; version footer equals `versionName` | UI/S | M | Y |
| TC-110 | FR-025 | Toggle card off | — | Turn Card off; start an order | No Card tile on SCR-10, no card option on Checkout | E2E | H | Y |
| TC-111 | FR-025 | Last method guard | Only QRIS on | Turn QRIS off | Refused with message | UI | H | Y |
| TC-112 | FR-025 | QR expiry + rounding + terminal test | — | Expiry 3 min; rounding Rp 500; Run test | Next QR shows 3:00; totals rounded; "Terminal OK" (fake) | UI | M | Y |
| TC-113 | FR-026 | Order type guard | Takeaway off | Turn Dine in off | Refused | UI | H | Y |
| TC-114 | FR-026 | Idle timeout | Set 30 s | Start order | Overlay at 30 s; the Kiosk behaviour screen has no language row | UI | M | Y |
| TC-115 | FR-026 | Reduce motion / brightness / sound | Toggle each | Start order | Animations ≤ 1 frame; window brightness changes; no sounds | UI/M | M | P |
| TC-116 | FR-026 | Larger text | Larger text on | Screenshot Menu, Item, Combo, Your order, Checkout | Text 1.15×; no clipping/overlap | S | H | Y |
| TC-117 | FR-027 | Connection test | Mock `gateway_down` | Run test | Backend reachable; gateway failure reported separately | UI | M | Y |
| TC-118 | FR-027 | Sync now | Mock menu-119 with a price change | Sync now | "Syncing…" → "Up to date"; Last synced updated; new price visible | E2E | M | Y |
| TC-119 | FR-027 | Change PIN | Online / offline | Change 1234 → 5678 | Online: E13, new PIN works; offline: "Connect to the internet…" | UI | M | Y |
| TC-120 | FR-027 | Restart app | Device in lock task | Restart app | Back to Idle ≤ 20 s, still locked | D | M | N |
| TC-121 | FR-028 | Sync on bind/startup with ETag | Mock with ETag | Start twice | 2nd start E5/E6 return 304; cache unchanged | I | H | Y |
| TC-122 | FR-028 | Offline banner | On Menu | Airplane mode on | Banner within 5 s; browsing works; Pay disabled | E2E | H | Y |
| TC-123 | FR-028 | Reconnect | Offline on SCR-10 | Airplane mode off | Banner hidden and Pay enabled within 10 s | E2E | H | Y |
| TC-124 | FR-028 | Sync deferred mid-order | Cart open | Trigger periodic sync with new prices | Cart prices unchanged until the order ends; applied before next order | I | M | Y |
| TC-125 | FR-028 | Offline cold start with images | Synced once | Network off, reboot app | Menu with cached images | D | H | N |
| TC-126 | FR-029 | Lock task escape | Device owner, bound | Try Home, Back, Recents, status bar pull, power-menu apps | Cannot leave app | D | H | N |
| TC-127 | FR-029 | Boot launch | — | Reboot device | App in foreground within 60 s, in lock task | D | H | N |
| TC-128 | FR-029 | Exit kiosk mode | — | Settings → Exit kiosk mode | PIN required; then lock task ends | D | M | N |
| TC-129 | FR-030 | Sound effects | Sound on / off | Quick add, pay success, pay fail | Sounds ≤ 1 s when on; none when off | M | L | N |
| TC-130 | FR-031 | Fault surfacing | Receipt printer connected | Unplug | Settings home "1 issue needs attention" within 5 s; red dot on Idle corner (matches `S_Idle_issues`) | D | M | N |
| TC-131 | FR-031 | Log rotation & redaction | Generate 15 MB of logs | Inspect log dir | ≤ 10 MB; no PIN; payment refs show last 4 only | I | M | Y |
| TC-190 | FR-028 | Unvalidated network and E14 states | Connectivity engine with virtual time; fakes | (a) Connect to a captive portal (connected, not validated); (b) E14 `components.paymentGateway` `DEGRADED` or `DOWN`; (c) a real call fails with a network error while online; (d) the first probe on a fresh network fails or hangs | (a) CHECKING for 4 s, then OFFLINE, so the banner is up within 5 s and the network is not probed; (b) OFFLINE; (c) E14 is probed at once; (d) Pay is back within 10 s. Retries wait 2 s, 4 s and then 30 s. CHECKING never disables Pay or Add | U | H | Y |

### 5.9 End-to-end journeys and Android lifecycle

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-132 | J1 (FR-006…FR-021) | Happy path dine-in QRIS | Mock default | Run PRD §5.2 | Complete with A-number (no printing in 0.6.0, D-36) | E2E | H | Y |
| TC-133 | J2 | Card declined → QRIS | Mock `card_declined` then QRIS paid | PRD §5.3 | Same orderId; Complete | E2E | H | Y |
| TC-134 | J3 | QR expiry → retry | Mock `qris_expire_fast` | PRD §5.4 | New QR; then paid | E2E | H | Y |
| TC-135 | J4 | Abandoned order | Timeout 30 s | PRD §5.5 | Idle, empty cart | E2E | H | Y |
| TC-136 | J7 | Staff maintenance | — | PRD §5.8 | Feed executes; Exit to Idle | E2E | M | P |
| TC-137 | J8 | Network lost while ordering | — | PRD §5.9 | Banner, Pay blocked, auto-recovery | E2E | H | Y |
| TC-138 | Lifecycle | Configuration changes | On Item with options; on Checkout pending | Change system font scale, locale, dark mode, display size via `adb` | State retained (options, cart, payment); app stays light-themed; no crash | UI | H | Y |
| TC-139 | Lifecycle | Process death outside payment | Cart on Menu | `am kill`, relaunch | Splash → Idle, empty cart (A-11); no crash; settings untouched | E2E | M | Y |

### 5.10 Non-functional (NFR-001 – NFR-017)

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-140 | NFR-001 | API floor/target | Emulators 30, 34, 35, 36, 37; target iMin device (from 0.2.0) | Run instrumented suite | All pass | UI | H | Y |
| TC-141 | NFR-002 | Density scaling + letterbox | 1080×1920 at 160/240/320 dpi; one 1200×1920 and one 1080×2340 | Screenshot Idle, Menu, Your order | Identical layout across densities; supported range fills; other ratio letterboxed | S | H | Y |
| TC-142 | NFR-003 | Startup | iMin device | Macrobenchmark cold start ×10 | First frame ≤ 1.0 s (median); Idle ≤ 5.0 s | B | H | Y |
| TC-143 | NFR-003 | Jank | iMin device | Scripted J1 with JankStats | ≤ 1 % janky frames; P90 ≤ 16 ms | B | H | Y |
| TC-144 | NFR-003 | Interaction latency | iMin device | Category switch, quick add | ≤ 300 ms render; tap feedback ≤ 100 ms | B | M | P |
| TC-145 | NFR-004, NFR-015 | 72 h soak | iMin device, staging | UI Automator order every 3 min for 72 h | 0 crashes, 0 ANRs; heap growth ≤ 20 MB; temperature within spec; log dir ≤ 10 MB | D | H | P |
| TC-146 | NFR-005 | Token storage | Debug build | `run-as` inspect files | Token not in plain text | D | H | N |
| TC-147 | NFR-005 | TLS / pinning / cleartext | Proxy with self-signed cert; HTTP URL | Run app through proxy | Connections refused (pinning); cleartext blocked in release | D | H | N |
| TC-148 | NFR-005 | Release hardening | Release APK | `scripts/check-release-apk.sh` (dex, R8 mapping, manifest), manifest review | `debuggable=false`; R8 on; no Developer options/simulate classes; exported components minimal; only listed permissions | U | H | Y |
| TC-149 | NFR-006 | Privacy | Release build after a soak | Review logs; dependency list | No customer data, no PIN, payment refs masked; no analytics/crash SDKs | M | H | P |
| TC-150 | NFR-007 | Automated accessibility checks | — | Compose tests with accessibility checks on every screen | No errors (labels, target ≥ 88 dpx, contrast) | UI | H | Y |
| TC-151 | NFR-007 | TalkBack walkthrough | iMin device or emulator | Complete J1 with TalkBack only | All controls reachable and labelled as in the design aria labels | M | M | N |
| TC-152 | NFR-008 | String integrity | — | Lint; pseudo-locale (en-XA) screenshots; PO copy review | 0 hardcoded strings; no clipping when text grows 30 %; English copy reviewed | U/S/M | H | P |
| TC-153 | NFR-009 | Motion fidelity | Screen recordings vs design prototype | Compare per PRD §11.4 checklist | All timings within ± 10 %; reduce motion collapses all | M | M | N |
| TC-154 | NFR-010 | Network fault injection | MockWebServer | Timeouts, 5xx, malformed JSON, slow body | Correct timeouts (10/15 s); retries only on idempotent calls; errors mapped; no crash | I | H | Y |
| TC-155 | NFR-011 | Static analysis & coverage | `./gradlew verify` | Run lint, detekt/ktlint, Kover | 0 errors; `:core:domain` ≥ 90 %, `:core:data` ≥ 70 % | U | M | Y |
| TC-156 | NFR-012 | Fakes complete | — | Run J1–J8 on emulator with fakes only | All pass without network | E2E | M | Y |
| TC-157 | NFR-013 | Image placeholders & cache | Broken image URL; cache 200 MB cap | Load menu; fill cache | Tone placeholder on failure; cache never > 200 MB | I | M | Y |
| TC-158 | NFR-014 | Upgrade install | Previous milestone APK installed + activated | Install new signed APK over it | Activation, binding, printers, settings preserved; `versionCode` per scheme | D | H | N |
| TC-159 | NFR-016 | Compatibility matrix | §2.3 matrix | Run regression subset per target | Pass on all targets; printer list certified only if printing is built (OQ-19) | D/UI | H | P |
| TC-160 | NFR-017 | Clock skew | Device clock +10 min | Start QRIS payment | Countdown still correct (server-based) | UI | H | Y |
| TC-193 | NFR-014, FR-016 | Room migrations 1 → 2 and 2 → 3 (0.6.0: `modifier_group.purpose`, TD-25) | Exported schemas `1.json`, `2.json` and `3.json` | Migrate a version-1 database that holds catalog rows; open the migrated file with the real database; store a pending order and reopen | `pending_order` exists and is empty, the catalog rows are kept (no new download), the migrated file passes Room's schema check, the pending order survives reopening, a version-2 cache gets `purpose` = NULL (a plain group) and keeps its rows, so "Make it a meal" shows only after a menu refresh, and `1 → 2` and `2 → 3` are the only registered migrations (no destructive fallback) | I | H | Y |

### 5.11 Design states (the `S_*` state boards)

Each case below uses the state board named in the Scenario column as its visual reference. A state board is the real screen rendered with a `scenario` value, so the screenshot test and the Compose test drive the same state in the app.

| ID | Req | Scenario | Preconditions / data | Steps | Expected | Type | Pri | Auto |
|---|---|---|---|---|---|---|---|---|
| TC-172 | FR-014 | *Retired in 0.6.0 (the strip is simply hidden; see TC-194, TC-197).* Upsell with nothing to suggest (`S_Upsell_noSuggestions`) | Cart already holds a side, a drink and a dessert; toggle on | View order | "Nothing more to suggest" with "Your order already has everything it needs…" and one "Review order" button → SCR-10 | UI/S | M | Y |
| TC-173 | FR-013, FR-015 | Summary panel Details | Design sample cart | Open SCR-10; tap "Details"; tap again | Collapsed: Total and the two method tiles (QRIS, Card) only. Expanded: Subtotal, Promo, PB1, Rounding lines and the order list shrinks to fit; Pay label equals the total in both states | UI | M | Y |
| TC-174 | FR-007 | Order type is text and logo only (`S_OrderType_singleOption`) | (a) Both types on; (b) Takeaway off | Open SCR-05 | (a) Brand mark, "Welcome to Urban Grill", "Where are you eating today?", two full-width cards with an icon, title and description; no photo and no language control anywhere. (b) One card and the note "Takeaway isn't offered at this restaurant right now." | UI/S | H | Y |
| TC-175 | FR-009, FR-028 | Menu states (`S_Menu_emptyCategory`, `S_Menu_offline`) | (a) Category with every item sold out; (b) airplane mode; (c) sold-out items; (d) item at 20 | (a) Open it; (b) go offline; (c) open the category; (d) tap + | (a) "Nothing here right now" and "See popular items" returns to Popular; (b) banner "Ordering is paused — reconnecting…", add buttons and "View order" disabled; (c) sold-out items are not shown; (d) toast "Maximum 20 per item", quantity stays 20 | UI/S | H | Y |
| TC-176 | FR-010, FR-011 | Item and Combo states (`S_ItemDetail_requiredMissing`, `S_ItemDetail_soldOut`, `S_Combo_requiredMissing`, `S_Combo_soldOut`) | Fakes | (a) Open an item with a required size and add nothing; (b) item that sells out while open; (c) combo with no side/drink; (d) sold-out combo option | (a) CTA disabled "Choose a size to continue"; (b) "No longer available" banner, dimmed hero, CTA disabled; (c) CTA reads "Pick side & drink" (then "Choose a side" / "Choose a drink"); (d) the option is greyed and cannot be selected | UI/S | H | Y |
| TC-177 | FR-012, FR-015, FR-016, FR-028 | Review blocking states (`S_Payment_priceChanged`, `S_Payment_itemUnavailable`, `S_Payment_offline`, `S_Payment_noMethods`, `S_Payment_emptyTray`, `S_Payment_promoNudge`) | Fakes / mock scenarios | Trigger each state | Each shows its banner or panel as designed; Pay is disabled with the reason in every blocking state; "Review" and "Remove" clear price-changed and unavailable; the promo nudge shows "Add Rp X more to get Rp 10.000 off" with a progress bar and does not block Pay | UI/S | H | Y |
| TC-178 | FR-019 | Checkout failure states (`S_Checkout_creating`, `S_Checkout_expired`, `S_Checkout_cancelled`, `S_Checkout_declined`, `S_Checkout_terminalTimeout`, `S_Checkout_offline`) | Mock / fakes | Reach each state; tap "Try again", the other-method button and "Cancel order" | "Creating your payment…" shows before the QR; each failure shows its own title and copy and the three actions; "Try again" creates a fresh payment; "Cancel order" → Idle; offline disables Try again and labels it "Reconnecting…"; no "Pay at the counter" button | UI/S | H | Y |
| TC-179 | FR-003, FR-004 | Sign-in and merchant states (`S_Login_notRegistered`, `S_Login_networkTimeout`, `S_Merchants_skeleton`, `S_Merchants_empty`, `S_Merchants_bindFailed`, `S_Merchants_closed`) | Mock scenarios | Trigger each state | Login errors show the designed copy with "Try again" (serial number shown when not registered; Log in disabled while busy or when the SN is unreadable); Merchants shows "Loading restaurants…" skeletons, "No restaurants yet", a bind failure with Retry (Retry shows the skeleton, then the list), a closed card "Closed · opens 10:00" (mock `merchant_closed`, E2 `opensAt`) that is not tappable, or "Closed" when `opensAt` is missing | UI/S | H | Y |
| TC-180 | NFR-002, NFR-009 | State-board screenshot matrix | `verifyRoborazziDebug` in `verify` | Render every `S_*` board (33) and every screen artboard (20) at 1080×1920 in the app and compare with the design | Every board has a reference image reviewed against `docs/ui-design/`; any board without a screenshot test fails the check; larger-text variants exist for the money screens | S | H | Y |

---

## 6. Android-specific validation guidance

### 6.1 Unit (business logic)
Pricing, cart, combo/item price, the OrderSession and Payment state machines, PIN hashing and lockout, the IDR formatter, LocalizedText fallback, and the receipt/kitchen renderers (golden text). Use the virtual time (`TestScope`) for every timer.

### 6.2 Repository / data integration
MockWebServer with fixtures equal to `api-spec.md`: activation, merchants, config/menu ETag, order idempotency, the payment lifecycle, error envelopes. Room: atomic menu replace and pending-payment persistence. DataStore: settings and lockout persistence.

### 6.3 UI and navigation
Every route in PRD §5.1 is covered by a navigation test (DEV-010a). Every state in the PRD §7 table has a Compose test and a screenshot, and every `S_*` state board in `docs/ui-design/` is the reference image for its screenshot test (TC-172–TC-180).

### 6.4 Lifecycle, configuration and process death
TC-013, TC-073, TC-107, TC-138, TC-139, TC-191 and TC-192. Run with "Don't keep activities" enabled once per milestone from 0.4.0.

### 6.5 Connectivity transitions
TC-003, TC-011, TC-085, TC-122–TC-125, TC-137, TC-154 and TC-190. Test Wi-Fi off, airplane mode, captive portal (validated network false), and a backend that is up while the gateway is down.

### 6.6 Permissions
Bluetooth connect/scan deny and grant (TC-098), USB permission (TC-097), and location for SSID (FR-027, "Wi-Fi name hidden" when denied). No runtime permission is requested on customer screens (checked in TC-132).

### 6.7 Screen size, density, orientation
TC-141. Orientation is locked, so rotation tests only confirm the lock holds (TC-138 includes `adb shell settings put system user_rotation 1`).

### 6.8 Accessibility and string externalisation
TC-032, TC-116 and TC-150–TC-152 (accessibility and string externalisation; there is no language switching in 1.0).

### 6.9 Design fidelity and animation
For every screen, a side-by-side review of the screenshot test reference against the design artboard in `ui-design/`, checking copy, spacing (± 4 dpx) and colors (exact tokens). A motion checklist per PRD §11.4 (TC-153). Rendering smoothness via TC-143.

### 6.10 Performance, memory, battery
TC-142–TC-145. Memory via `dumpsys meminfo` at hours 1, 24, 48 and 72 of the soak. Battery is not applicable (mains-powered kiosk). Thermal is observed instead (NFR-015).

### 6.11 Hardware
SN (TC-005/006), printers (TC-091–TC-100, removed from 0.6.0 and not scheduled until the PO answers OQ-19 [D-36]), EDC (TC-083), lock task and boot (TC-126–TC-128). Evidence is photos of printouts and screen recordings.

### 6.12 Security and privacy checklist (TC-146–TC-149)
- [ ] Token in encrypted storage; never logged.
- [ ] HTTPS only; pinning active with a backup pin; cleartext only in debug for the mock host.
- [ ] PIN hash constant-time compare; lockout persisted; PIN never logged or sent except in E13.
- [ ] No card data in the app (EDC only).
- [ ] Release: R8 on, not debuggable, no debug tools, minimal exported components.
- [ ] Permissions limited to PRD §12.3.
- [ ] Dependency audit: no analytics or crash SDKs (D-23); licenses recorded (font OFL).

---

## 7. Version-specific testing

Each milestone runs: (a) its new TCs, (b) the full automated regression of all earlier milestones, (c) its manual/device checks. Evidence goes in `docs/test-reports/<version>.md` (`verify` and instrumented-test reports, screenshots, device photos/videos, benchmark numbers).

| Version | Features to validate | Required TCs (new) | Regression | Blocking defects | Required evidence | Exit |
|---|---|---|---|---|---|---|
| **0.1.0** | Build, modules, design units/tokens, navigation skeleton, network skeleton, fakes | TC-140, TC-141 (skeleton screen), TC-154 (partial), TC-155, TC-156 (fakes compile) + navigation edge walk | — | Build or `verify` failure; scaling wrong | `verify` report; emulator screenshots of placeholders | dev-plan 0.1.0 exit |
| **0.2.0** | Splash, routing, SN, activation, merchants | TC-001–TC-019, TC-146, TC-179, TC-182–TC-184 | 0.1.0 | Wrong routing; duplicate activation; token in plain text | `verify` report; SN spike notes (ADR-0002) with device photo; J5/J6 recordings | All pass; SN source decided |
| **0.3.0** | Idle, order type, menu, item, combo, sync/cache, images | TC-020–TC-049 (TC-029–TC-031 and TC-036 retired), TC-174–TC-176, TC-121, TC-124, TC-125, TC-157 | 0.1–0.2 | Wrong item/combo price; crash in browse; menu not cached | Screenshot diffs vs design; `docs/test-reports/0.3.0.md` | All pass |
| **0.4.0** | Cart, pricing, Budget Plan, review, methods, upsell, inactivity | TC-050–TC-067, TC-101–TC-104, TC-135, TC-139, TC-161–TC-167, TC-169–TC-173, TC-177 | 0.1–0.3 | Any pricing mismatch; overlay during payment; a Budget Plan plan above budget shown as "under" or as an exact match, or a person left without a meal | `docs/pricing-cases.md`; `docs/budget-plan-cases.md`; coverage report (domain ≥ 90 %) | All pass |
| **0.5.0** | Order creation, QRIS, card (fake), failures, offline gate, recovery, clock skew | TC-068–TC-087 (TC-083 deferred to 0.9.0), TC-122, TC-123, TC-132–TC-134, TC-137, TC-138, TC-154, TC-160, TC-178, TC-181, TC-185–TC-193 | 0.1–0.4 | Duplicate order; "not charged" shown on unknown status; paid without PAID | Recordings of J1–J3 with fakes; mock-scenario run log | All pass; gateway/EDC spec status recorded |
| **0.6.0** | Complete (with a demo receipt visual, nothing prints), Upsell strip incl. "Make it a meal", Budget Plan improvements (Step 1, Step 2, swap picker, Edit plan, Menu pill, hand-off), Room version 3 | TC-088–TC-090, TC-194–TC-205 (all P2 rows are built); TC-091–TC-095, TC-099, TC-100 removed by the PO [D-36] | 0.1–0.5 incl. the payment suite and J1 up to Complete (no print capture) | The strip blocks Pay; a Budget Plan order is upsold past its budget or meal-upgraded; Complete ends without clearing the session; the paid payment is lost between Checkout and Complete | `docs/test-reports/0.6.0.md`; screenshots of Complete, the strip boards and the Budget Plan boards; the PO's sign-off of the boards (DEV-104) and review (DEV-113) | All listed TCs pass; no printing in the release build is expected |
| **0.7.0** | PIN, settings, lock task, sound, logging | TC-105–TC-131, TC-136 | 0.1–0.6 + J1–J8 | Lock-task escape; PIN bypass; lockout not persisted | Device videos of escape attempts and reboot; provisioning run log | All pass on iMin device |
| **0.8.0** | String lint, a11y, motion, on-device profiling, larger text, screenshots | TC-032, TC-116, TC-150–TC-153, TC-180 | Full suite | Hardcoded strings on customer screens; perf below NFR-003; a11y errors; a state board without a screenshot test | Perf report; a11y report; PO copy sign-off; motion checklist | Targets met or accepted |
| **0.9.0** | Real backend/gateway/EDC, matrix, soak, security, release build, pilot | TC-083, TC-096–TC-098 (only if printing is built, OQ-19), TC-145, TC-147–TC-149, TC-158, TC-159 + **all MVP TCs re-run on device against staging** | Full suite on device | Any P1/P2; contract mismatch; soak failure | Contract run vs staging; soak report; security checklist; pilot report | Release checklist complete except sign-off |
| **1.0.0** | Performance benchmarks (DEV-031), release build against prod | TC-142–TC-144, TC-168 on the iMin device; full regression (automated + manual priority H) on the signed APK; DEV-102 real transactions | Full | Any P1/P2; any P3 on checkout/payment screens | Signed test report; APK checksum; release checklist signed | Rollout approved |

---

## 8. Requirement coverage matrix

| Req | DEV tasks (dev-plan) | Test cases | Version |
|---|---|---|---|
| FR-001 | DEV-010, DEV-011 | TC-001–TC-004 | 0.2.0 |
| FR-002 | DEV-012 | TC-005–TC-007 | 0.2.0 |
| FR-003 | DEV-013, DEV-014 | TC-008–TC-013, TC-179, TC-182, TC-184 | 0.2.0 |
| FR-004 | DEV-015, DEV-016 | TC-014–TC-018, TC-179, TC-183, TC-184 | 0.2.0 |
| FR-005 | DEV-017 | TC-019 | 0.2.0 |
| FR-006 | DEV-024 | TC-020–TC-024 | 0.3.0 |
| FR-007 | DEV-025 | TC-025–TC-028, TC-174 | 0.3.0 |
| FR-008 | DEV-026 (removed) | TC-029–TC-031 retired (D-29: language switching deferred past 1.0); TC-032 now covers NFR-008 | — |
| FR-009 | DEV-027, DEV-028 | TC-033–TC-035, TC-037–TC-039 (TC-036 retired), TC-175 | 0.3.0 |
| FR-010 | DEV-028, DEV-029 | TC-040–TC-045, TC-176 | 0.3.0 |
| FR-011 | DEV-030 | TC-046–TC-049, TC-176 | 0.3.0 |
| FR-012 | DEV-036, DEV-037, DEV-037a, DEV-041 | TC-050–TC-054, TC-177 | 0.4.0 |
| FR-013 | DEV-035, DEV-038, DEV-046, DEV-097 | TC-055–TC-061, TC-173 (+TC-071) | 0.4.0 / 0.5.0 / 0.9.0 |
| FR-014 | DEV-039 (replaced), DEV-105, DEV-106, DEV-114 | TC-194–TC-197, TC-205 (TC-062–TC-064, TC-172 retired) | 0.4.0 / 0.6.0 |
| FR-015 | DEV-037a, DEV-038 | TC-065–TC-067, TC-173, TC-177 | 0.4.0 |
| FR-016 | DEV-045, DEV-046, DEV-052 | TC-068–TC-073, TC-185, TC-186, TC-189, TC-191, TC-192 | 0.5.0 |
| FR-017 | DEV-047, DEV-048, DEV-090 | TC-074–TC-079, TC-189 | 0.5.0 / 0.9.0 |
| FR-018 | DEV-049, DEV-091 | TC-080–TC-083, TC-181, TC-187, TC-189 | 0.5.0 / 0.9.0 |
| FR-019 | DEV-050 | TC-084–TC-087, TC-178, TC-185, TC-187, TC-188 | 0.5.0 |
| FR-020 | DEV-060 (DEV-059 removed from 0.6.0) | TC-088–TC-090 | 0.6.0 |
| FR-021 | DEV-057, DEV-058, DEV-059 (removed from 0.6.0 by the PO, not scheduled; OQ-19) | TC-091–TC-095 (not scheduled) | OQ-19 |
| FR-022 | DEV-055, DEV-061, DEV-056 (not scheduled; OQ-19) | TC-096–TC-100 (not scheduled) | OQ-19 |
| FR-023 | DEV-040, DEV-041 | TC-101–TC-104 | 0.4.0 |
| FR-024 | DEV-065, DEV-066, DEV-067 | TC-105–TC-109 | 0.7.0 |
| FR-025 | DEV-068 | TC-110–TC-112 | 0.7.0 |
| FR-026 | DEV-003, DEV-069, DEV-085 | TC-113–TC-116 | 0.7.0 / 0.8.0 |
| FR-027 | DEV-070 | TC-117–TC-120 | 0.7.0 |
| FR-028 | DEV-021, DEV-022, DEV-023, DEV-051 | TC-121–TC-125, TC-175, TC-190 | 0.3.0 / 0.5.0 |
| FR-029 | DEV-071, DEV-101 | TC-126–TC-128 | 0.7.0 |
| FR-030 | DEV-072 | TC-129 | 0.7.0 |
| FR-031 | DEV-067, DEV-073 | TC-130–TC-131 | 0.7.0 |
| FR-032 | DEV-042, DEV-043, DEV-043a, DEV-044, DEV-031, DEV-107–DEV-112 | TC-161–TC-171, TC-198–TC-204 | 0.4.0 / 0.6.0 / 1.0.0 |
| NFR-001 | DEV-001 | TC-140 | 0.1.0 |
| NFR-002 | DEV-003, DEV-084 | TC-141, TC-180 | 0.1.0 / 0.8.0 |
| NFR-003 | DEV-031, DEV-082 | TC-142–TC-144 | 0.8.0 / 1.0.0 |
| NFR-004 | DEV-093 | TC-145 | 0.9.0 |
| NFR-005 | DEV-007, DEV-014, DEV-094 | TC-146–TC-148 | 0.2.0 / 0.9.0 |
| NFR-006 | DEV-073, DEV-094 | TC-149 | 0.7.0 / 0.9.0 |
| NFR-007 | DEV-004, DEV-081 | TC-150–TC-151 | 0.8.0 |
| NFR-008 | DEV-080 | TC-032, TC-152 | 0.3.0 / 0.8.0 |
| NFR-009 | DEV-004, DEV-083, DEV-084 | TC-153, TC-180 | 0.8.0 |
| NFR-010 | DEV-007, DEV-051 | TC-154 | 0.1.0 / 0.5.0 |
| NFR-011 | DEV-001, DEV-002, DEV-008 | TC-155 | 0.1.0 |
| NFR-012 | DEV-005, DEV-009 | TC-156 | 0.1.0 |
| NFR-013 | DEV-023 | TC-157 | 0.3.0 |
| NFR-014 | DEV-001, DEV-095, DEV-100 | TC-158, TC-193 | 0.5.0 / 0.9.0 / 1.0.0 |
| NFR-015 | DEV-093 | TC-145 | 0.9.0 |
| NFR-016 | DEV-056 (not scheduled, OQ-19), DEV-092 | TC-159 | 0.9.0 |
| NFR-017 | DEV-047 | TC-160 | 0.5.0 |

Journeys J1–J8 are covered by TC-132–TC-137 plus TC-008/TC-009 (J5/J6). Every MVP FR and every NFR has at least one DEV task and at least one TC. FR-008 is no longer part of 1.0, so its three cases are retired rather than deleted.

---

## 9. Release readiness

### 9.1 Release-blocking conditions
1. Any open P1 or P2 defect.
2. Any open P3 defect on SCR-10, SCR-11 or SCR-12 (money screens).
3. Any MVP FR acceptance criterion not verified on the signed release build.
4. Any NFR target missed without a written PO waiver.
5. Contract tests against prod/staging failing.
6. Security checklist (§6.12) incomplete.

### 9.2 Regression requirements
- All automated TCs green on the release commit (`verify` and instrumented-test reports recorded).
- All priority-H manual/device TCs executed on the signed APK on the iMin device within 48 h before release.
- J1–J3 executed against **prod** with real low-value transactions, then refunded (DEV-102).

### 9.3 Final 1.0.0 checklist
- [ ] `versionName 1.0.0`, `versionCode 10000`, signed with the production key (key never in the repo).
- [ ] APK SHA-256 recorded; git tag `v1.0.0`.
- [ ] Coverage matrix §8 fully green with evidence links.
- [ ] Pilot report shows OBJ-1 ≥ 95 % and OBJ-3 with zero discrepancies; OBJ-2 recorded.
- [ ] 72 h soak report passed (TC-145) on the release candidate code.
- [ ] Printers certified list published (`docs/printers.md`).
- [ ] Provisioning, runbook and rollback docs reviewed by a technician who did not write them.
- [ ] English copy (screens, receipts, errors) signed off by the PO.
- [ ] No telemetry SDKs; privacy review signed.
- [ ] MDM rollout plan: pilot kiosks → 48 h watch → remaining kiosks; rollback APK (0.9.x) staged.
- [ ] Sign-off: developer ____ / product owner ____ / date ____.
