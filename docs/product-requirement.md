# Hitachi Kiosk — Product Requirements Document

| | |
|---|---|
| Product | Hitachi Kiosk (Android self-ordering kiosk app) |
| Document version | 1.2 (2026-10-22), see the [revision history](#revision-history) |
| Target release | `1.0.0` |
| Companion documents | [`technical-requirement.md`](technical-requirement.md), [`technical-design.md`](technical-design.md), [`technology-stack.md`](technology-stack.md), [`architecture.md`](architecture.md), [`dev-plan.md`](dev-plan.md), [`testing-plan.md`](testing-plan.md) |
| Design source of truth | [`ui-design/`](ui-design/) — the final light design "Linen & Paprika": 20 screen artboards, 33 state boards (`S_<Screen>_<scenario>`), the `Photo` component, `canvas.json` and `README.md`. It is mirrored from https://claude.ai/artifact/Lv6W9bttmG8fH4ho4CkWMg |

## Revision history
| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-10-22 | First complete PRD. |
| 1.1 | 2026-10-22 | Aligned with the final "Linen & Paprika" design: English-only UI in 1.0 [D-29] (FR-008 deferred); Budget Pas renamed **Budget Plan**, with SCR-19 now designed as a 2-step flow plus a Customize sheet, exact-match handling and in-1.0 swap rebalancing (FR-032, OQ-14 closed); Menu promo carousel and the Checkout scan line, step list and "Step 3 of 3" indicator removed [D-30]; OrderType has no photos; Item, Combo, Payment, Checkout, Complete and Idle states documented from the 33 state boards; SCR-09, SCR-13 and the new SCR-20 (Change PIN) are now designed; "At counter" is no longer drawn on Payment or Checkout. |
| 1.2 | 2026-10-22 | File renamed from `prd.md` to `product-requirement.md`. Added the companion technical documents. No CI in 1.0 [D-31]: NFR-011 static analysis and coverage are enforced by the local `./gradlew verify` gate. |

## 0. How to read this document

Every statement in this PRD carries one of three provenance markers where it matters:

| Marker | Meaning |
|---|---|
| **[V]** Verified | Read directly from the design files in `ui-design/` or from the repository. |
| **[D-nn]** Decision | Explicit product-owner decision taken in the requirements interview (see §14 Decision log). |
| **[A-nn]** Assumption | Agent assumption that is not yet confirmed. Listed in §15 with the risk if wrong. |

Open questions that block or could change a requirement are listed in §16 as `OQ-nn`.

A screen being visible in the design is **not** treated as proof that its business logic, data source or backend behavior is defined. Where the design shows only hard-coded prototype data (prices, counts, order numbers), this PRD defines where the real value comes from.

Design measurements are in **design pixels (dpx)** on a 1080 × 1920 canvas. §11.2 defines how dpx map to Android `dp`/`sp`.

---

## 1. Product overview

### 1.1 Summary
Hitachi Kiosk is a portrait, touch-only, self-ordering app for restaurants. It runs on dedicated iMin Android kiosk hardware [D-05]. Hitachi provides it to many merchants as a service [D-03]. A customer walks up, chooses dine-in or takeaway, browses the merchant's menu, customizes items and combos, and pays by QRIS or card. They then receive an order number and a printed receipt, while a kitchen ticket prints for staff. Staff set up and maintain each kiosk through a PIN-protected settings area.

### 1.2 Vision
Let any restaurant on the Hitachi Kiosk platform put an attractive, fast self-ordering kiosk in front of customers. It should need no on-device configuration beyond registering the device's serial number in the Hitachi dashboard.

### 1.3 Objectives for 1.0.0
| ID | Objective | Measure |
|---|---|---|
| OBJ-1 | A customer can complete an order and pay without staff help. | ≥ 95 % of pilot orders reach the Complete screen without staff intervention (pilot observation, 0.9.0). |
| OBJ-2 | Ordering is fast. | Median time from "Tap to order" to payment start ≤ 90 s for a 3-item order in moderated pilot tests. |
| OBJ-3 | Totals and payments are always correct. | 0 pricing discrepancies between kiosk-displayed total and backend-validated total in the 0.9.0 pilot; 0 orders marked paid without gateway confirmation. |
| OBJ-4 | Kiosks are self-sufficient on the floor. | 72 h soak test without crash, ANR, or memory growth beyond NFR-004 limits. |
| OBJ-5 | The UI matches the approved design. | All screens pass the design-fidelity review in testing-plan §6.9. |

### 1.4 Problems solved
- Queues at the counter during peak hours; customers can order in parallel.
- Order errors from verbal ordering; customers select and confirm themselves.
- Ordering clarity: plain-English copy, large type and strong visual cues. More languages can be added after 1.0 [D-29].
- Manual payment handling; QRIS and card payment integrated into the order flow [D-12].

---

## 2. Users

| Persona | Description | Needs |
|---|---|---|
| **Customer** | Walk-in diner, any age, may be a first-time kiosk user. Uses the kiosk standing, for 1–3 minutes. | Big readable targets, clear prices including tax, quick path to pay, confidence the payment worked, an order number. |
| **Store staff** | Cashier/crew at the merchant. Not technical. | Unlock settings with a PIN, refill paper, test the printer/terminal, check connectivity, sync the menu, recover a stuck kiosk. |
| **Merchant admin** (backend, out of app scope) | Owner/manager using the Hitachi dashboard. | Registers kiosk serial numbers, maintains menu, prices, promos, tax, staff PIN. Not a user of the Android app. |
| **Hitachi field technician** | Installs and provisions the device. | Install the APK via MDM/sideload, make it device owner, verify activation. |

---

## 3. Scope

### 3.1 In scope for 1.0.0 (MVP)
1. Splash, device activation by serial number (SN), merchant selection and binding [D-03, D-14].
2. Single-merchant kiosk mode [D-07].
3. Idle/attract screen with hidden staff entry.
4. Order type (dine-in / takeaway).
5. Menu browsing: categories, Popular, the "Plan by budget" entry row, product grid, quick add.
6. Item detail customization (meal upgrade, ingredient levels, extras, quantity).
7. Combos (combo choice, side, drink, size).
8. Upsell ("Suggest add-ons") step before payment; the screen is designed (SCR-09) [D-16].
9. Cart / order review with quantity edit and removal.
10. Pricing engine: subtotal, promo, PB1 tax, rounding, all configured in the backend [D-19].
11. Order submission to the backend; the backend assigns the order number [D-21].
12. Payment by QRIS and by card on a separate EDC terminal, via the company's own gateway [D-12, D-13].
13. Payment processing, failure, retry and method switch states.
14. Completion screen with order number and auto-return.
15. Printing: customer receipt and kitchen ticket on printers that are built-in (iMin SDK), USB or Bluetooth, with role assignment [D-17, D-18].
16. Inactivity timeout with a "Still there?" warning [D-15].
17. Staff settings: PIN gate synced from the backend with lockout, plus Printer, Payment, Kiosk behaviour, and Device & network screens [D-20].
18. Menu sync and offline behaviour: browse while offline, but checkout is blocked [D-11].
19. Android lock-task kiosk lockdown as device owner [D-20, D-22].
20. English-only UI with every string externalised, so languages can be added later [D-29].
21. Accessibility options: reduce motion, larger text (+15 %), sound effects.
22. **Budget Plan** group meal planner: budget + adults + children → a recommended complete meal plan with a closest-under and a closest-over option, or an exact match when one exists [D-26].

### 3.2 Explicit non-goals for 1.0.0
| Item | Reason / when |
|---|---|
| Food-court mode (several merchants per kiosk) | Deferred post-1.0. When built: one merchant per order, chosen per order [D-07, D-08]. |
| "Pay at counter" payment method | Not offered in 1.0. The design no longer draws the payment tile or the failure-screen button, and the toggle that is still drawn in Payment settings is hidden in the build [D-12a]. |
| Runtime language switching and a Bahasa Indonesia UI | Deferred post-1.0. The design has no language control. Strings stay in resource files so a language can be added later [D-29]. |
| Dark theme | The local dark copy is not a baseline. Light design only [D-02]. |
| Crash reporting / analytics / remote logs | No telemetry in 1.0. Local logs only [D-23]. |
| Customer accounts, loyalty, vouchers typed by the customer | Not in the design. |
| Kitchen display system, order-ready calling screen | The backend/merchant handles these; the kiosk prints only [D-10]. |
| Merchant dashboard and backend implementation | Built outside this Android plan. The app defines the contract it needs (§10) [D-06]. |
| Google Play distribution | Distributed as a signed APK via MDM/sideload [D-22]. |
| Tablet / landscape / phone layouts | Portrait 1080 × 1920 kiosks only (NFR-002). |

### 3.3 MVP and 1.0.0 definition
`1.0.0` is complete when every functional requirement marked **MVP** in §6 is implemented and passes its acceptance criteria, against the **real** backend, gateway and EDC terminal on the target iMin device [D-06]. NFR targets in §9 must also be met, and the release checklist in testing-plan §9 must be signed off.

---

## 4. Screen inventory

All screens are 1080 × 1920 dpx portrait, background `#FAF8F5`, font Schibsted Grotesk [V].

| Screen ID | Design file | Name | Purpose | Related FR |
|---|---|---|---|---|
| SCR-01 | `Main.dc.html` | Splash | Brand animation while the app initializes | FR-001 |
| SCR-02 | `Login.dc.html` | Sign in | Activate the device by SN | FR-002, FR-003 |
| SCR-03 | `Merchants.dc.html` | Choose a restaurant | Bind the kiosk to a merchant | FR-004 |
| SCR-04 | `Idle.dc.html` | Idle / attract | Promo slideshow, "Tap to order", hidden staff entry | FR-006, FR-024 |
| SCR-05 | `OrderType.dc.html` | Dine in / takeaway | Choose order type (text cards, logo mark, no photos) | FR-007 |
| SCR-06 | `Menu.dc.html` | Menu | Browse categories and quick-add products | FR-009 |
| SCR-07 | `Combo.dc.html` | Combos | Configure a combo meal | FR-011 |
| SCR-08 | `ItemDetail.dc.html` | Item | Customize a product | FR-010 |
| SCR-09 | `Upsell.dc.html` | Upsell ("Anything on the side?") | Suggest add-ons before payment | FR-014 |
| SCR-10 | `Payment.dc.html` | Your order | Review cart, see totals, choose payment method | FR-012, FR-013, FR-015 |
| SCR-11 | `Checkout.dc.html` | Checkout (waiting / processing / failed) | Pay by QRIS or card | FR-016…FR-019 |
| SCR-12 | `Complete.dc.html` | Transaction complete | Order number, print confirmation, auto-return | FR-020, FR-021 |
| SCR-13 | `StillThere.dc.html` | "Still there?" overlay | Inactivity warning over the blurred current screen | FR-023 |
| SCR-14 | `Settings.dc.html` | Staff PIN + Settings home | PIN gate and settings index | FR-024 |
| SCR-15 | `SettingsPrinter.dc.html` | Receipt printer | Printer selection, roles, receipt options | FR-021, FR-022 |
| SCR-16 | `SettingsPayment.dc.html` | Payment | Methods, terminal, QRIS, tax/rounding info | FR-025 |
| SCR-17 | `SettingsKiosk.dc.html` | Kiosk behaviour | Ordering, screen, accessibility | FR-026 |
| SCR-18 | `SettingsDevice.dc.html` | Device & network | Network, menu sync, device, PIN, restart | FR-027 |
| SCR-19 | `BudgetPas.dc.html` | Budget Plan | Plan meals for a group within a budget: Step 1 "Your table", Step 2 "Your plan", Customize sheet | FR-032 |
| SCR-20 | `ChangePin.dc.html` | Change PIN | Three-step staff PIN change, reached from Device & network | FR-027 |
| — | `Photo.dc.html` | Photo slot component | Image container with tone placeholder when no image | NFR-013 |

**State boards.** `S_<Screen>_<scenario>.dc.html` shows one screen in one state by importing the real screen with its `scenario` prop (for example `S_Checkout_expired` is `Checkout` with `scenario="expired"`). The 33 boards cover the states in §7. Each screen's valid `scenario` values are listed in its `data-props`. `ui-design/README.md` describes the folder.

### 4.1 Contradictions between design and decisions (resolved)
| # | Design shows [V] | Resolution |
|---|---|---|
| C1 | A light published version and a dark local version exist | The light version is canonical [D-02]. The dark files are not kept. |
| C2 | The "Pay at counter" toggle in Payment settings (the payment tile and failure button are no longer drawn) | Hidden in the 1.0 build [D-12a]. |
| C3 | Printer settings describe one "TP-80 · USB · 80 mm" printer | Replaced by a printer list with connection type (built-in / USB / Bluetooth) and role assignment [D-17, D-18]. Hero text shows the selected receipt printer. |
| C4 | Kitchen ticket "Send to the kitchen printer when paid" | Kept. The kitchen ticket prints on whichever configured printer has the Kitchen role, which may be the same printer as the receipt [D-17]. |
| C5 | Cart badge hard-coded to `4`; Checkout amount hard-coded `Rp 172.700`; order number `A247`; "Kiosk 3"; QR pattern pseudo-random | All are real data: cart state, backend totals, backend order number, kiosk display name from the backend, and the gateway QR payload. |
| C6 | Checkout has a "Prototype only · simulate the result" bar with Approve/Decline | Not shipped. It exists only in debug builds as a developer tool (FR-019). |
| C7 | Login header pill "Version 1.0"; Settings footer "Hitachi Kiosk 1.0.0" | Shows the real `versionName`. |
| C8 | Menu header pill reads "Dine in · Change" with no language tag | Shows the selected order type ("Dine in" or "Takeaway"), followed by "Change". |
| C9 | Idle timeout "cancels the order" with no warning | A warning overlay comes first [D-15]. |
| C10 | "Suggest add-ons" toggle and the Upsell screen | In 1.0, built to `Upsell.dc.html` [D-16]. |
| C11 | Photo slots carry placeholder captions such as "Promo photo 1 · 1080 × 1920" and "Photo" | Design-only labels. Release builds show real images or the plain tone (NFR-013). |
| C12 | Checkout still contains the prototype Approve/Decline bar and an unused instruction-step array in its script | The bar is debug-only (C6). The step list is not part of the design and is not built [D-30]. |

---

## 5. Navigation and user journeys

### 5.1 Navigation map
```
App start
 └─ SCR-01 Splash ──(init complete)──┬─ not activated ─────────────► SCR-02 Sign in
                                     ├─ activated, no merchant ────► SCR-03 Choose a restaurant
                                     └─ activated + merchant bound ─► SCR-04 Idle

SCR-02 Sign in ──(activation OK)──► SCR-03 ──(pick live merchant)──► SCR-04 Idle
SCR-03 ──(Log out)──► SCR-02

SCR-04 Idle ──(tap anywhere)──► SCR-05 Order type ──(pick)──► SCR-06 Menu
SCR-04 Idle ──(hold bottom-right 2 s)──► SCR-14 Staff PIN ──► Settings home ──► SCR-15…18
                                                         └─(Exit)──► SCR-04
SCR-18 Device & network ──(Change PIN)──► SCR-20 Change PIN ──(PIN updated)──► SCR-18

SCR-06 Menu ─┬─(product card)──► SCR-08 Item ──(Keep ordering)──► SCR-06
             ├─(Plan by budget row)──► SCR-19 Budget Plan: Step 1 ──(See plans)──► Step 2 ──(Add plan to order)──► SCR-10
             │                                  (Back ──► Step 1, then SCR-06)
             ├─(Meals pill / combo promo)──► SCR-07 Combo ──(Menu)──► SCR-06
             ├─(Change order type)──► SCR-05 (cart kept)
             ├─(Cancel)──► SCR-04 (cart cleared)
             └─(View order)──► [SCR-09 Upsell, if enabled and not yet shown] ──► SCR-10 Your order
SCR-07/SCR-08 ──(My order / View order)──► SCR-10
SCR-10 ──(Add more)──► SCR-06
SCR-10 ──(Pay Rp …)──► SCR-11 Checkout
SCR-11 ──(Back to order)──► SCR-10 (payment cancelled)
SCR-11 ──(paid)──► SCR-12 Complete ──(Done / 20 s)──► SCR-04
Any customer screen ──(inactivity)──► SCR-13 overlay ──(no response)──► SCR-04
```

### 5.2 Journey J1 — Happy path, dine-in, QRIS
1. Idle shows a promo slideshow. Customer taps anywhere.
2. Order type: customer taps "Dine in". The card fills with the accent color, a check pops, and after 950 ms the app navigates to Menu [V].
3. Menu: customer taps "+" on "Crispy Chicken (2 pc)". A thumbnail flies to the cart dock, the button turns into a check for 1.4 s, and the dock count and total roll [V].
4. Customer taps "Choose ›" on "Smoky Double Burger" (it has a required size). On the Item screen they confirm the size, switch on "Make it a meal", expand "Customize" to set Smoked cheddar to Extra, expand "Add extras" to add Fried egg, set qty 2, and tap "Add to order". The dock shows "Keep ordering" / "View order" [V].
5. If upsell is enabled, the upsell step appears (FR-014). The customer adds or skips.
6. Your order: the customer reviews lines, changes a quantity, and sees Subtotal, the Promo line when eligible, PB1 10 %, and Total. QRIS is pre-selected. They tap "Pay Rp X". The dark summary shows the Total with a "Details" toggle for the full breakdown.
7. Checkout: the app creates the order and the QRIS payment ("Creating your payment…" while it waits). It shows the QR code with a "Code expires in m:ss" ring. The customer scans and pays in their e-wallet.
8. The gateway confirms and the screen shows "Confirming payment" → Complete: "Payment successful", "Thank you!", order number, and dine-in instructions. The receipt and kitchen ticket print.
9. After 20 s, or a tap on "Done", the kiosk returns to Idle with the cart cleared.

### 5.3 Journey J2 — Card payment declined, switch to QRIS
1. Steps 1–6 as J1, but the customer selects Card.
2. Checkout shows "Tap, insert or swipe on the terminal below". The kiosk has sent the amount to the EDC terminal.
3. The terminal reports declined. The Failed state shows "Your bank declined the card. Try again or use another card." and "You have not been charged · your order is saved".
4. The customer taps "Pay by QRIS instead", which cancels the card attempt and creates a QRIS payment for the same order. The flow then continues as J1 step 7.

### 5.4 Journey J3 — QRIS expires
QR countdown reaches 0:00 without confirmation. The kiosk cancels the payment with the gateway and shows Failed with "The QR code expired before we received your payment." "Try again" requests a **fresh** QR for the same order.

### 5.5 Journey J4 — Abandoned order
The customer leaves mid-menu. After the configured idle timeout (default 60 s), the "Still there?" overlay shows a 15 s countdown. With no tap, the cart is cleared and the kiosk returns to Idle. This never happens on Checkout while a payment is pending, or on Complete (FR-023).

### 5.6 Journey J5 — First-time setup
A technician installs the APK as device owner and launches it. Splash leads to Sign in, and "Log in" sends the device SN. The backend recognizes the SN registered in the dashboard and returns the merchant list. Staff choose "Urban Grill", and the kiosk enters lock-task mode on Idle.

### 5.7 Journey J6 — Unregistered device
"Log in" sends the SN, and the backend replies "not registered". Sign in shows "This kiosk isn't registered yet" with the SN in large text, asks staff to ask their administrator to register it, and offers "Try again" [A-01].

### 5.8 Journey J7 — Staff maintenance
On Idle, staff press and hold the bottom-right 160 × 160 dpx corner for 2 s. The 4-digit PIN pad appears, and staff enter the PIN to reach Settings home. They open Receipt printer and tap "Feed paper", then "Exit" to return to Idle.

### 5.9 Journey J8 — Network lost
The kiosk loses connectivity while a customer is on the Menu. Browsing continues from the cache. Tapping "Pay" (or arriving on Your order) shows an "Ordering is paused — reconnecting" state, with the Pay button disabled. Connectivity returns and the button re-enables automatically (FR-028).

### 5.10 Journey J9 — Family plans by budget (Budget Plan)
1. A family of 2 adults and 1 child picks Dine in, then taps the slim dark "Plan by budget" row at the top of the Menu.
2. SCR-19 Step 1, "Feed the whole table": they tap the Rp 150k chip, set Adults 2 and Children 1, and tap "See plans".
3. Step 2, "Two plans for you": the hero card shows the best fit, "Just under budget · Rp 149.600 · Rp 400 left". A "Just under | Just over" control switches it to "Just over budget · Rp 150.700 · +Rp 700 over". They expand "What's included" to see meals per person (the child's meal comes from kid-friendly items) and "Price details" for the final breakdown including PB1.
4. They tap "Customize", swap one adult's drink, and the sheet re-balances sides and desserts so the plan stays as close to the budget as possible. They tap "Done".
5. They tap "Add plan to order · Rp X". The cart is filled and they land on Your order (SCR-10) with every line editable, then continue as J1 step 6.
6. Exact-price variant: 1 adult and 1 child with a Rp 99.000 budget get a single hero card titled "A perfect fit", badge "Exact match", "Perfect fit! Rp 99.000 · Rp 0 left". No over/under pair is shown.

---

## 6. Functional requirements

Priority: **MVP** = required for 1.0.0. **Post-1.0** = specified for later, not built in 1.0.0.

### FR-001 Splash and startup routing — MVP
- **Description:** Show the animated brand splash while initializing, then route by activation state.
- **Rationale:** Masks startup work. A kiosk must recover to the correct screen after a reboot.
- **Behavior:** The splash animations from `Main.dc.html` play: mark-in 900 ms @150 ms, dot pop 600 ms @700 ms, ring 1400 ms @900 ms, title rise, progress bar fill 2200 ms @1600 ms. "Preparing your kiosk" shows at the bottom [V]. During the splash the app loads the stored activation, merchant binding and cached config, and starts a background sync if bound. Navigation happens when **both** the minimum display time (3.9 s [V]) has elapsed **and** initialization has completed. Route: not activated → SCR-02. Activated without merchant → SCR-03. Bound → SCR-04.
- **Inputs:** Stored device token, merchant binding, cached config.
- **Outputs:** Navigation to the target screen.
- **Rules:** The splash never waits on the network for more than 10 s. On timeout it routes using cached state.
- **Errors/edge cases:** Corrupt local storage → treat as not activated and log locally. Process death on splash → splash restarts.
- **Acceptance criteria:**
  - AC1: On a cold start with a bound merchant and a warm cache, Idle appears within 3.9–5.0 s of the splash's first frame.
  - AC2: With no network and a bound merchant, Idle appears within 10 s using cached data.
  - AC3: With reduced motion on, animations complete instantly (≤ 1 frame) but the routing rules still apply.
- **Screens/flows:** SCR-01; J5.

### FR-002 Device serial number acquisition — MVP
- **Description:** Obtain a stable hardware serial number (SN) that identifies the kiosk to the backend [D-14].
- **Rationale:** Activation relies on matching the SN against the device registered in the dashboard.
- **Behavior:** `DeviceIdentityProvider` returns the SN. The primary source is the iMin SDK/system API. If that is unavailable, it falls back to `Build.getSerial()`, which is permitted because the app is device owner [A-02]. The SN is cached after the first successful read.
- **Rules:** The SN must be identical across app reinstalls and reboots. The SN is not a secret but is not shown to customers.
- **Errors:** SN unavailable → Sign in shows the error panel "Can't read this device's serial number" / "Restart the kiosk. If it keeps happening, contact your administrator." and the Log in button is disabled.
- **Acceptance criteria:**
  - AC1: On the target iMin device the SN returned equals the SN printed on the device label / shown in system settings.
  - AC2: The value is unchanged after app data clear, reinstall and reboot.
  - AC3: The fake provider used in tests can simulate success, unavailable, and slow (5 s) reads.
- **Dependencies:** OQ-01 (exact model and SN API).

### FR-003 Device activation ("Log in") — MVP
- **Description:** "Log in" on SCR-02 sends the SN to the backend, which matches it to a registered device and returns a device token and the merchant list [D-14].
- **Behavior:** Tapping "Log in" changes the label to "Signing in…" with a spinner, and repeat taps are ignored [V]. The app calls `POST /v1/devices/activate` with SN, app version and device model. On success it stores `deviceToken`, `deviceId` and `kioskDisplayName` securely (NFR-005) and navigates to SCR-03.
- **Inputs:** SN, versionName, Build.MODEL.
- **Outputs:** Device token, device ID, kiosk display name (e.g. "Kiosk 3"), kiosk code (e.g. "KSK-0003"), merchants.
- **Rules:** The token is long-lived and refreshed per §10. No user credentials are entered or stored on the device.
- **Errors:**
  - Not registered (HTTP 404 / `DEVICE_NOT_REGISTERED`): show the panel "This kiosk isn't registered yet" / "Its serial number is not linked to a merchant account. Ask your administrator to register it, then try again." with the SN ("Serial number HK-…") and a "Try again" button [A-01].
  - Device disabled (`DEVICE_DISABLED`): show "This kiosk has been disabled. Contact your administrator."
  - Network error / timeout of 15 s: show "Can't reach Hitachi Kiosk" / "The connection timed out. Check the network cable or Wi-Fi, then try again." with "Try again".
- **Acceptance criteria:**
  - AC1: A registered SN moves from SCR-02 to SCR-03 within 3 s on a normal network.
  - AC2: Each error case shows its message, and the button becomes tappable again within 300 ms of the error.
  - AC3: Double-tapping "Log in" issues exactly one network request.
  - AC4: After activation, killing and restarting the app does not show SCR-02.
- **Screens/flows:** SCR-02; J5, J6.

### FR-004 Merchant selection and binding — MVP
- **Description:** Staff choose the merchant this kiosk serves [V, D-07].
- **Behavior:** SCR-03 shows "Step 1 of 2 · Set up this kiosk" and "Choose a restaurant". Live merchants appear as large cards with photo, logo, name, cuisine and an "Open now" pulse badge. Merchants with status `coming_soon` appear under "Coming soon" as compact disabled rows (the design shows two) with initial, name, type and a "Soon" pill [V]. States: while loading, "Loading restaurants…" with skeleton cards; no merchants, "No restaurants yet" / "This account isn't linked to any merchant. Ask your administrator to add one, then refresh." with "Refresh list"; a closed merchant shows a "Closed · opens {time}" badge and a disabled card; bind or menu-download failure shows "This kiosk could not be linked to {merchant}. Check the connection and retry." or "The menu for {merchant} did not finish downloading. Check the connection and retry." with "Retry" and "Log out". Tapping a live card calls `PUT /v1/devices/{id}/binding`, downloads config and menu (FR-028), then navigates to Idle and enters lock-task (FR-029). "Log out" (header) clears the device token and binding and returns to SCR-02.
- **Rules:** Exactly one merchant is bound at a time. Coming-soon rows are not tappable (`aria-disabled` [V]). The "Open now" badge reflects backend `isOpen`. A closed merchant shows "Closed · opens {time}" and cannot be bound, because its card is disabled in the design [A-03].
- **Edge cases:** Zero live merchants → show "No restaurants yet" with the refresh message above. Binding succeeds but the menu download fails → stay on SCR-03 with a retry message and do not enter Idle with an empty menu.
- **Acceptance criteria:**
  - AC1: Binding a live merchant shows Idle with that merchant's name and accent within 10 s on a normal network (menu ≤ 200 items, images loading lazily).
  - AC2: Coming-soon rows ignore taps and are announced as disabled by TalkBack.
  - AC3: Log out returns to SCR-02, and a restart shows SCR-02.
  - AC4: A menu download failure leaves the kiosk on SCR-03 with a working retry.
- **Note:** Re-binding from Settings is not designed. See OQ-07.

### FR-005 Kiosk operating mode — MVP (single) / Post-1.0 (food court)
- **Description:** A per-device operating mode [D-07].
- **Behavior 1.0:** Single-merchant mode only. The mode value is stored and read from config so that food-court mode can be added without migration.
- **Post-1.0:** In food-court mode a merchant picker appears after "Tap to order". One merchant per order, with one payment and one ticket per order [D-08].
- **Acceptance criteria:** AC1: The config contains `mode = SINGLE`, and no code path in 1.0 exposes a merchant picker to customers.

### FR-006 Idle / attract screen — MVP
- **Behavior [V]:**
  - Full-screen slideshow of merchant promo images (from the backend). Each slide shows for 7 s with a 1.4 s crossfade and a slow Ken Burns zoom of 8 s.
  - The top shows the merchant name chip and segmented progress dots.
  - The bottom sheet reads "Hungry?" and "Order here and skip the queue.", with the accent "Tap to order" button with pulsing ring, and the helper line "Touch the screen to start your order".
  - Tapping anywhere goes to SCR-05.
  - A transparent 160 × 160 dpx button in the bottom-right corner opens the staff PIN after a continuous 2 s press. Releasing or leaving early cancels it [V].
- **Rules:**
  - When there are no promo images, show the tone placeholder (NFR-013).
  - Entering Idle clears any cart and releases payment resources.
  - When any hardware or network issue exists (FR-031), a small pulsing red dot shows on the staff corner (design: `scenario="issues"`) [A-31].
  - The slideshow pauses when the screen is not visible.
- **Acceptance criteria:**
  - AC1: Slides advance every 7 s ± 100 ms.
  - AC2: A tap anywhere except the staff corner opens SCR-05 within 300 ms.
  - AC3: Holding the corner 2 s opens SCR-14, while a 1.9 s hold does not.
  - AC4: A tap on the staff corner shorter than 2 s does **not** start an order [A-04].
  - AC5: Returning to Idle always shows an empty cart.
  - AC6: With a printer or network fault the red dot is visible, and it disappears once all statuses are healthy.

### FR-007 Order type selection — MVP
- **Behavior [V]:**
  - The screen has no photos [D-30]: the brand header, a large logo mark (ink tile with the accent dot), "Welcome to {merchant}" and the question "Where are you eating today?". The footer reads "Powered by Hitachi Kiosk".
  - Two full-width text cards, each with an icon tile, the title and a one-line description: "Dine in" ("Served on a tray. We'll call your number.") and "Takeaway" ("Packed in a sealed bag, ready to go.").
  - On tap, the selected card fills with the accent (600 ms circular reveal) and a check pops in. The other card dims to 45 %. After 950 ms the app navigates to Menu.
  - Further taps are ignored once a card is picked.
- **Rules:**
  - Each card shows only if enabled in Kiosk behaviour (FR-026). If only one is enabled, the app still shows SCR-05 with that single card, the question "Ready when you are" and a note such as "Takeaway isn't offered at this restaurant right now." [A-05].
  - At least one must be enabled (FR-026).
  - The chosen type is stored in the order draft and shown in the Menu header pill ("Dine in · Change"), the Your-order header, the Complete instructions and the receipt.
  - "Change" from Menu returns here with the cart kept.
- **Acceptance criteria:**
  - AC1: Picking a card navigates to Menu 950 ms ± 100 ms later.
  - AC2: A second tap within the 950 ms has no effect.
  - AC3: With Takeaway disabled, only Dine in is shown, with the note above.
  - AC4: The Menu header reflects the chosen type.

### FR-008 Language selection and localization — Post-1.0 (deferred) [D-29]
- **Description:** 1.0 ships an English-only customer and staff UI. The design has no language control.
- **Behavior in 1.0:**
  - There is no EN/ID control on SCR-05, no language tag in the Menu header pill, and no "Default language" row in Kiosk behaviour (SCR-17).
  - Every customer and staff string is English. The receipt and kitchen ticket are English.
  - Menu names, descriptions and categories are read in English from the backend. The API may still carry other languages (§10.3), and the app ignores them in 1.0.
  - All strings live in `values/strings.xml`, so a language can be added without code changes (NFR-008).
- **Post-1.0 (when a language is added):** a per-order language switch on SCR-05, a reset to the default at the end of each order, a default-language setting, and `values-<lang>` resources. A native-speaker review of every string, including receipts and errors, is part of that work.
- **Acceptance criteria:**
  - AC1: No customer-visible string is hard-coded (lint, NFR-008).
  - AC2: No language control exists anywhere in a release build.

### FR-009 Menu browsing — MVP
- **Behavior [V]:**
  - **Header:** brand lockup, a pill reading "Dine in · Change" (or "Takeaway · Change"), and "Cancel", which returns to Idle and clears the cart.
  - **Greeting:** time-of-day greeting ("Good morning/afternoon/evening" [A-07]) and the title "What are you having?".
  - **Category pills:** a horizontal scroll that includes "Popular" (featured list) and the backend categories. A "Meals" pill with an ↗ icon links to the Combo screen. The active pill is dark (#171513). Changing category scrolls the grid to the top and replays the heading and card entrance animations.
  - **"Plan by budget" row (only on Popular):** a slim 128 dpx dark row with a people icon, "Plan by budget", "Set a budget and group size — we plan the meal" and an arrow button. It opens SCR-19 and shows only when merchant config `budgetPlanner.enabled` is true (FR-032). The promo carousel from earlier designs is removed from the Menu in 1.0 [D-30].
  - **Grid:** 2 columns. Each card shows the photo, an optional badge ("New", "Best seller" in accent; "Spicy", "Share", "Local" in ink), name, price and a round add button. Calories are shown on the Item screen, not on the grid. Tapping the card opens SCR-08. The round "+" button quick-adds one unit with default options. Products that need a choice show a "Choose ›" pill instead of "+" and open SCR-08.
  - **Quick add:** the "+" turns into an accent check with a ring burst for 1.4 s. The thumbnail flies to the cart dock (760 ms). The dock count bumps and the total rolls.
  - **Cart dock:** up to 3 most-recent thumbnails, "N items", total, and "View order →". A linen fade above the dock stops grid cards from sitting under it.
  - **States:** offline shows an amber banner "Ordering is paused — reconnecting…" and disables add buttons and "View order". An empty category shows "Nothing here right now", "Everything in this category is sold out. Try another one — the full menu is just above." and a "See popular items" button. Reaching the 20-per-item limit shows the toast "Maximum 20 per item" and dims that card's add button.
- **Rules:**
  - Products that require a choice (a required modifier group) cannot be quick-added. They show "Choose ›" and open SCR-08 instead [A-08].
  - Unavailable (sold-out) items are not shown [A-09, OQ-08].
  - Section heading shows "Popular now" for Popular, otherwise the category label, plus "N items".
  - With an empty cart, the dock shows "0 items · Rp 0" and "View order" opens SCR-10's empty state [V].
- **Acceptance criteria:**
  - AC1: Category switch renders the new grid in ≤ 300 ms (NFR-003).
  - AC2: Quick add increments the existing line if identical, otherwise adds a line, and the dock total equals the sum of line totals.
  - AC3: While offline, add buttons and "View order" are disabled and the banner is visible.
  - AC4: Cancel clears the cart and shows Idle.
  - AC5: Badge colors match the design and the grid shows no calorie line.

### FR-010 Item detail and customization — MVP
- **Behavior [V]:**
  - Hero photo (560 dpx tall) with a sheet sliding up over it. "Menu" back pill and "My order" pill with count badge.
  - Info chips: badge, kcal, and protein/allergen text such as "Beef · Contains gluten, dairy". Then name, price and description.
  - **"Choose a size"** (required group, shown first): "Regular · Two patties" and "Large · Three patties · +Rp 6.000". It carries a "Required" tag that becomes "Required · done" once chosen.
  - **"Make it a meal"** is a single switch row with a photo and "Adds fries & a drink · +Rp 18.000". It is off by default.
  - **"Customize"** is a collapsed row with a summary ("No changes", or the changed ingredients). Expanding it shows, per ingredient, a 3-segment control None / Regular / Extra. The indicator is white for None, ink for Regular, and accent for Extra. An Extra may cost extra (e.g. "Smoked cheddar — Extra +Rp 5.000").
  - **"Add extras"** is a collapsed row ("None added") that expands to a multi-select list with a price each.
  - **Bottom dock:** quantity stepper (min 1, max 20; "−" is disabled at 1) and "Add to order · Rp X". The price rolls when options change.
  - **After add:** the dock swaps to a check plus "Keep ordering" / "View order". The thumbnail flies to the cart pill and the badge bumps.
- **Data model:** All of these sections come from backend modifier groups (§10.3):
  - A meal upgrade is a single-choice group.
  - Ingredient levels are level-type modifiers with per-level price deltas.
  - Extras are a multi-choice group with optional min/max.
  - The sections a product shows depend on its groups. A product with no groups shows only info and quantity.
- **Rules:** Unit price = base + Σ selected option deltas. Line total = unit × qty. Default selections come from the backend, and the defaults are what quick-add uses. Two lines are "identical" when product and every option match exactly. Adding an identical line merges quantities, capped at 20 [A-10].
- **Edge cases:**
  - Adding would exceed qty 20 on a merged line: cap at 20 and show "Maximum 20 per item".
  - A required group without a selection: the group shows "Required" and the dock button is disabled and reads "Choose a size to continue".
  - The product sells out while open: a "No longer available" banner ("This item just sold out. Pick something else from the menu."), a dimmed hero, and a disabled button.
- **Acceptance criteria:**
  - AC1: For the design sample (Smoky Double, meal, cheddar Extra, Fried egg, qty 1), the price shown is Rp 52.000 + 18.000 + 5.000 + 6.000 = **Rp 81.000**.
  - AC2: The stepper stops at 1 and 20.
  - AC3: After "Add to order", the cart contains exactly one new or merged line with the selected options and its description string (e.g. "Fries · Cola · Extra cheddar · No onion").
  - AC4: Re-entering the screen starts from the defaults (meal off, ingredients on Regular, base price, e.g. Rp 52.000).

### FR-011 Combos — MVP
- **Behavior [V]:**
  - Header: "Menu" back pill and "My order" pill. Subtitle "A main, a side and a drink, for less", title "Combos".
  - A radio list of combos showing photo, name, includes text, price and "Save Rp X" in green.
  - "Side" and "Drink" single-select sections, each showing 3 option cards ("Included" or "+Rp X") and a "More sides (n)" / "More drinks (n)" button that reveals the rest, with "Show fewer". A selected option that would be hidden stays visible. Each section carries a "Required" tag until chosen, and unavailable options are greyed with "Sold out".
  - "Size" segmented: Regular / Large +Rp 6.000.
  - Dock: summary text "{combo} · {side} · {drink} · {size}", rolling total, and "Add combo". While a side or drink is missing the button is disabled and reads "Choose a side", "Choose a drink" or "Pick side & drink". This becomes "✓ Added" for 1.8 s and the cart badge bumps.
- **Rules:**
  - Combo price = combo base + (side upcharge + drink upcharge + large upcharge) × multiplier.
  - The multiplier is 2 for combos marked as serving 2 (e.g. "Feast for Two"), otherwise 1 [V].
  - "Save" = separate price − combo base price (backend-supplied `separatePrice`).
  - Option upcharge labels show the multiplied amount [V].
  - Defaults: first combo, first side, first drink, Regular [V].
- **Acceptance criteria:**
  - AC1: Cheeseburger Meal + Cheese fries + Iced latte + Large = 56.000 + 8.000 + 4.000 + 6.000 = **Rp 74.000**.
  - AC2: Feast for Two + Nuggets + Large = 109.000 + (4.000 + 6.000) × 2 = **Rp 129.000**, and the labels show "+Rp 8.000" and "Large +Rp 12.000".
  - AC3: Each "Add combo" adds one combo line, and a double tap during "Added" adds nothing.

### FR-012 Cart / order review — MVP
- **Behavior [V]:**
  - Header: "Add more" (→ Menu) and an order type chip. When the cart came from Budget Plan, a tag row "Budget Plan · {adults} adults · {children} child · Rp {budget}" sits above the lines [A-40].
  - Progress "Step 2 of 3 · Review & pay", title "Your order", "N items".
  - Lines (scrollable, fade mask): photo, name, options description, line total, and a quantity pill. "−" shows a trash icon at qty 1 and removes the line with a slide-out (460 ms). "+" increments.
  - Empty state: "Your tray is empty" / "Add something tasty to get started." / "Browse the menu".
- **Rules:**
  - The cart lives in memory for the current order only. It survives navigation and configuration change, and is cleared on Idle.
  - Process death mid-order restarts at Idle with an empty cart, except during payment (FR-016) [A-11].
  - Maximum 30 lines and 20 per line [A-10].
- **Acceptance criteria:**
  - AC1: "+" / "−" update the line total and all summary values within one frame (NFR-003).
  - AC2: Removing the last line shows the empty state, and the Pay button is replaced by the disabled "Add an item to pay".
  - AC3: The item count label pluralizes correctly ("1 item" / "N items").

### FR-013 Pricing engine — MVP
- **Description:** Deterministic computation of order totals on the kiosk from backend config, re-validated by the backend [D-19].
- **Rules:**
  - All amounts are integer Rupiah (IDR). No floating point anywhere in money math.
  - `subtotal` = Σ line totals.
  - `discount` = the best applicable promo from config. 1.0 supports the **threshold discount** type: if subtotal ≥ threshold, subtract a fixed amount (design: Rp 10.000 off when subtotal ≥ Rp 100.000 [V]). Only one promo applies per order [A-12].
  - `tax` = round_half_up((subtotal − discount) × taxRate), with taxRate from config (design: PB1 10 % [V]). Tax is exclusive (added on top) [V: "added at checkout"].
  - `total` = subtotal − discount + tax, then rounded **up** to the configured rounding step (None / Rp 100 / Rp 500 [V]) [A-13]. Any rounding adjustment is shown as its own line "Rounding" when non-zero [A-13].
  - When subtotal > 0 and below the threshold, show "Add Rp X more to get Rp 10.000 off" as a banner with a progress bar on SCR-10 [V].
  - Display format: `Rp` + space + thousands separated by `.`, no decimals (e.g. `Rp 172.700`) [V].
- **Acceptance criteria:**
  - AC1: The design sample (75.000 + 38.000 + 22.000 + 2 × 16.000 = 167.000) gives discount 10.000, tax 15.700 and total **Rp 172.700**.
  - AC2: Subtotal 99.999 gives no discount and shows "Add Rp 1 more…".
  - AC3: Rounding Rp 500 on total 172.700 gives 173.000 with a "Rounding Rp 300" line.
  - AC4: The backend-validated total equals the kiosk total in 100 % of contract tests. A mismatch follows FR-016's mismatch handling.

### FR-014 Upsell ("Suggest add-ons") — MVP
- **Description:** Before payment, offer sides and desserts [V toggle text: "Offer sides and desserts before payment"] [D-16].
- **Behavior [V, `Upsell.dc.html`]:** When enabled (FR-026) and the cart has no item from the configured upsell categories, the first "View order" tap of an order shows SCR-09. The eyebrow reads "Before you pay", the title "Anything on the side?" and the subtitle "A side or something sweet goes well with your order." A strip shows the current cart ("Your order", item count, total). A 2 × 2 grid lists up to 4 suggestions from the backend `upsell` list (default: Sides and Desserts categories), each with photo, name, note, price and a quick-add button. The dock holds "No thanks" and "Continue · Rp X" (→ SCR-10). It is shown at most once per order.
- **Empty state:** with nothing to suggest, SCR-09 shows "You're all set" / "Your order already has everything it needs. Review it and pay when you're ready." with a single "Review order" button. Better: the app skips SCR-09 entirely when there are no suggestions, and the state exists as a fallback.
- **Rules:** Suggestions exclude items already in the cart.
- **Acceptance criteria:**
  - AC1: With the toggle off, SCR-09 is never shown.
  - AC2: With it on, it shows once per order and never again after "No thanks".
  - AC3: Quick-added suggestions appear in the cart with default options, and the "Continue" total updates.
  - AC4: With no suggestions the fallback state appears (or the screen is skipped) and never blocks payment.

### FR-015 Payment method selection — MVP
- **Behavior [V]:**
  - The order lines scroll in the main area (about 4 rows visible). Banners and the Budget Plan tag scroll with them.
  - The dark summary panel shows the Total in large type and a "Details" toggle. Collapsed by default, "Details" expands Subtotal, the Promo line ("Promo · Rp 10.000 off over Rp 100.000"), "Restaurant tax (PB1 10 %)" and, when non-zero, "Rounding".
  - "How would you like to pay?" has two tiles side by side: QRIS ("Any e-wallet") and Card ("Debit or credit"). A selected tile inverts and shows a check. There is no third tile in 1.0.
  - The CTA reads "Pay Rp X".
  - Blocking states (Pay disabled, with its reason as the button label):
    - Prices changed: a banner "Prices have been updated. Please review your order." with "Review"; changed lines show "Price updated · was Rp X". Label "Review your order to continue".
    - Item unavailable: the line reads "No longer available" with a "Remove" action. Label "Remove the unavailable item to pay".
    - Offline: a banner "Ordering is paused — reconnecting…". Label "Pay unavailable while offline".
    - No payment method: "No payment methods right now" / "Please ask a team member for help." Label "Pay unavailable".
    - Empty tray: "Your tray is empty" / "Add something tasty to get started." with "Back to menu". Label "Add an item to pay".
- **Rules:**
  - Visible methods = methods enabled in Payment settings (FR-025) ∩ methods allowed by merchant config. "At counter" is never shown in 1.0 [D-12a].
  - The default selection is the first visible method (QRIS).
  - If no method is available, show "No payment methods right now" / "Please ask a team member for help." and disable Pay [A-14].
  - When offline, Pay is disabled with "Ordering is paused — reconnecting…" (FR-028).
- **Acceptance criteria:**
  - AC1: With Card disabled in settings, only the QRIS tile is shown (full width).
  - AC2: The Pay label always equals the formatted total.
  - AC3: No "At counter" tile is ever rendered in a release build.

### FR-016 Order submission — MVP
- **Description:** Create the order on the backend before payment.
- **Behavior:** Tapping "Pay" calls `POST /v1/orders` with an `Idempotency-Key` (UUID per order attempt). The body holds lines with product and option IDs, quantities, order type, kiosk-computed totals, and the menu version. The backend re-prices the order. If its total equals the kiosk total, it returns `orderId`, `orderNumber` and `totals`, and the app proceeds to payment (FR-017 / FR-018).
- **Rules:**
  - Retrying with the same key never creates a second order.
  - The order is created once per checkout. Switching method or retrying payment reuses it.
  - "Back to order" before payment completion cancels the payment and marks the order abandoned via `POST /v1/orders/{id}/cancel`. Editing the cart afterwards creates a new order with a new key.
- **Errors:**
  - **Price mismatch (`PRICE_CHANGED`):** the app refreshes config and menu, re-prices the cart, and shows "Prices have been updated. Please review your order." on SCR-10 (banner, "Review" button, Pay disabled until reviewed).
  - **Item unavailable (`ITEM_UNAVAILABLE`):** the affected lines are marked "No longer available" with a "Remove" action and must be removed before paying.
  - **Network failure:** retry with the same key up to 3 times (1 s, 2 s, 4 s backoff), then show "Can't reach the restaurant system. Please try again." with a "Try again" button.
- **Process death:** The pending `orderId` / `paymentId` is persisted. On restart, if a payment was pending, the app queries its status. If paid, it shows Complete and prints. If failed or expired, it cancels the order and shows Idle [A-11].
- **Acceptance criteria:**
  - AC1: 10 rapid Pay taps produce one order.
  - AC2: A forced 5xx followed by success produces one order.
  - AC3: A mismatch shows the updated totals and never charges the old amount.
  - AC4: Killing the app during a pending, successfully paid QRIS payment leads to Complete and a printed receipt on relaunch.

### FR-017 QRIS payment — MVP
- **Behavior [V]:**
  - Checkout shows "Amount to pay", the 112 dpx total and "Secure payment".
  - A method switcher (QRIS / Debit / credit card) and a "Waiting for your payment" status with a pulsing dot.
  - A QRIS card with "{merchant} · {kiosk name}", the QR (480 dpx) with the merchant mark in the center. There is no scan-line animation [D-30].
  - A countdown ring with "Code expires in m:ss".
  - There is no instruction list under the QR and no "Step 3 of 3" indicator in the header [D-30]. "Back to order" (outline) sits above the debug-only prototype bar.
  - While the payment is being created: "Creating your payment…" with "Setting up your QR code. This only takes a moment."
- **Process:** The app calls `POST /v1/orders/{id}/payments {method: QRIS}` and receives `paymentId`, `qrString` and `expiresAt`. It renders the QR from `qrString` with error correction level M or higher. It polls `GET …/payments/{pid}` every 2 s, or uses push if the backend supports it.
- **Expiry:** The expiry comes from Payment settings (3 / 5 / 10 min, default 5 min [V]). The countdown uses `expiresAt` from the server, not the local clock.
- **On status:**
  - `PAID` → Processing state ("Confirming payment", "Waiting for your bank to confirm. Please keep this screen open.") for at least 800 ms → Complete.
  - `FAILED` / `CANCELLED` → Failed state, title "Payment cancelled" ("The payment was cancelled in your app. Try again with a fresh code, or choose another method.").
  - Expiry → cancel the payment, then Failed, title "QR code expired", with the expired copy [V].
- **Rules:**
  - The QR is never shown for an amount different from the backend order total.
  - The idle timeout is suspended while a QR is shown (FR-023).
- **Acceptance criteria:**
  - AC1: The QR decodes to exactly the `qrString` from the API.
  - AC2: The countdown matches `expiresAt` ± 1 s.
  - AC3: A `PAID` status leads to Complete within 3 s.
  - AC4: Expiry shows the expired failure, and "Try again" yields a new `paymentId` and QR.
  - AC5: After a paid status, no more polling occurs.

### FR-018 Card payment on EDC terminal — MVP
- **Description:** Card payments are taken on a separate EDC terminal next to the kiosk, through the company gateway [D-13].
- **Behavior [V]:** Checkout card view: "Tap, insert or swipe on the terminal below", "Visa, Mastercard and GPN accepted · contactless works too", and a terminal illustration with the amount and an animated card tap and arrow. The kiosk calls `POST …/payments {method: CARD}` and then sends the amount and reference to the terminal through the `CardTerminal` interface. The transport (USB/LAN/cloud) is pending OQ-03. When a result arrives: approved → the backend confirms → Processing ("Keep your card on the terminal until it beeps.") → Complete. Declined → Failed, title "Card declined" ("Your bank declined the card. Try again or use another card."). While the payment is created: "Creating your payment…" with "Getting the card terminal ready."
- **Rules:**
  - The kiosk app never sees card numbers, PIN or track data.
  - A payment is "paid" only when the **backend** reports `PAID`. The terminal result alone is not enough [A-15].
  - Terminal not responding for 90 s → cancel on the terminal, then Failed, title "Terminal not responding", with "The card terminal didn't respond. Try again or pay with QRIS." [A-16].
  - The terminal reference and TID are stored on the payment.
- **Acceptance criteria:**
  - AC1: With the fake terminal, approved, declined and timeout produce Complete, the declined failure and the timeout failure respectively.
  - AC2: The amount sent to the terminal equals the backend order total.
  - AC3: Cancel from "Back to order" sends a cancel to the terminal.

### FR-019 Payment failure and recovery — MVP
- **Behavior [V]:**
  - A Failed state with an animated X: "Payment didn't go through" or a reason-specific title ("QR code expired", "Payment cancelled", "Card declined", "Terminal not responding"), a reason, and the green reassurance "You have not been charged · your order is saved".
  - Buttons: "Pay by card instead" / "Pay by QRIS instead" (outline, shown only if the other method is enabled), "Cancel order" (soft, cancels the order and returns to Idle) and "Try again" (accent, full width).
  - Offline failure: title "Ordering is paused", the text "Ordering is paused — reconnecting…" and "Try again" disabled with the label "Reconnecting…". It re-enables when connectivity returns.
  - Status unknown: "Checking your payment…".
  - There is no "Pay at the counter" button in the design or the build [D-12a].
  - "Back to order" on the waiting state returns to SCR-10.
- **Rules:**
  - "You have not been charged" shows only when the backend status is a terminal non-paid state (`FAILED` / `EXPIRED` / `CANCELLED`).
  - If the status is unknown, e.g. the network dropped, show "Checking your payment…" and keep polling up to 60 s before showing a failure [A-17].
  - Debug builds keep the design's "Prototype only · simulate the result" bar to force outcomes against the fake gateway. Release builds must not contain it.
- **Acceptance criteria:**
  - AC1: Every failure reason in the design maps to a gateway/terminal status.
  - AC2: An unknown status never shows "not charged".
  - AC3: The release APK contains no simulate bar (string absence check).

### FR-020 Order completion — MVP
- **Behavior [V]:**
  - An accent flood transition, then "Payment successful" with a badge, drawn check, ring waves and confetti.
  - "Thank you!" and "Rp X paid with {QRIS|Card}".
  - A ticket card: "Your order number" in 240 dpx digits with the first character in accent.
  - Order-type instructions. Dine in: "Take your receipt and watch the screen. We'll call your number when your tray is ready." Takeaway reads "Takeaway · Pick up at the counter when your number is called." [A-18].
  - A printer illustration animating the receipt out.
  - A countdown ring "Back to the start in Ns" (20 s) and "Done".
- **Rules:** The order number is the backend `orderNumber` [D-21]. Printing starts on entry (FR-021). Done or the timer → Idle, clearing the order.
- **Acceptance criteria:**
  - AC1: The order number equals the backend value.
  - AC2: Auto-return after 20 s ± 1 s.
  - AC3: Done returns immediately.
  - AC4: A print failure does not block this screen. A card replaces the printer animation: "Receipt couldn’t print" / "Please show this screen at the counter.", and the dine-in line becomes "Remember your number and watch the screen. We’ll call it when your tray is ready." [A-19].

### FR-021 Receipt and kitchen ticket printing — MVP
- **Description:** Print a customer receipt and a kitchen ticket after successful payment [D-17].
- **Behavior:** After `PAID`, the app prints:
  - the customer receipt to the printer with the Receipt role, if "Customer receipt" is on;
  - the kitchen ticket to the printer with the Kitchen role, if "Kitchen ticket" is on.
  - If the same printer has both roles, it prints both, with a cut between.
- **Receipt content [A-20]:** merchant logo (if "Logo on receipt" is on) and name; outlet address/NPWP if provided by config; kiosk name; date/time; order number (large); order type; lines with options and amounts; subtotal, discount, tax, rounding, total; payment method and reference; the footer text from settings ("Thank you for dining with us!" default [V]). Language is English in 1.0 [D-29].
- **Kitchen ticket content [A-20]:** large order number, order type, time, and lines with options and quantities. No prices.
- **Formatting:** paper width 58/80 mm and density Light/Normal/Dark from settings [V].
- **Errors:** Printer offline, out of paper or failing → retry once, show the Complete-screen fallback message, and flag the status in Printer settings (red status, "Last error"). The order stays paid. The kitchen relies on the backend order [D-10].
- **Acceptance criteria:**
  - AC1: A paid order prints one receipt and one kitchen ticket within 5 s of Complete on each supported connection type.
  - AC2: A disabled toggle prints nothing for that document.
  - AC3: 58 mm layout wraps without truncating amounts.
  - AC4: Out-of-paper is surfaced in Settings and on Complete.

### FR-022 Printer management — MVP
- **Description:** Staff choose and configure printers [D-17, D-18].
- **Behavior:** SCR-15 shows:
  - **Printers:** configured printers, each with connection type (Built-in / USB / Bluetooth), name, status, and role toggles Receipt and Kitchen.
  - **Add printer:** built-in is auto-detected via the iMin SDK. USB lists attached devices and requests USB permission. Bluetooth lists **paired** printers [A-21].
  - **Status [V]:** paper roll meter (where the printer reports it; "Replace below 15 %", red below 15 %), "Last printed", "Test page" (Printing… → Printed), "Feed paper" (Feeding… → Done).
  - **Receipt [V]:** Customer receipt, Kitchen ticket, Logo on receipt toggles, and Receipt footer text input.
  - **Paper [V]:** paper width 58/80 mm, print density.
- **Rules:**
  - Exactly one printer may hold each role. Assigning a role moves it from any other printer.
  - The Receipt role is required when "Customer receipt" is on, and the Kitchen role when "Kitchen ticket" is on. Otherwise show a warning on Settings home.
  - Settings persist locally per device.
- **Acceptance criteria:**
  - AC1: Each connection type can be added, tested and assigned.
  - AC2: Reassigning a role updates both printers' toggles.
  - AC3: The settings survive restart.
  - AC4: Bluetooth permission denial shows an explanation and a "Grant" action (NFR-005).

### FR-023 Inactivity timeout — MVP
- **Behavior:** On customer screens SCR-05…SCR-10, no touch for the configured timeout (30 / 60 / 120 s, default 60 s [V]) shows the "Still there?" overlay (SCR-13). It sits over the blurred current screen and reads "Still there?" / "Your order will be cancelled in N s. Tap anywhere to keep ordering." with a countdown ring, "Continue ordering" and "Start over" (clears the cart and returns to Idle) [D-15]. In the last 5 s the ring pulses. A tap anywhere on the overlay continues. At 0 the cart is cleared → Idle.
- **Rules:**
  - Never active on SCR-11 while a payment is pending or processing, on SCR-12 (it has its own timer), or in staff settings.
  - The settings area has its own 120 s timeout back to Idle [A-22].
  - On SCR-05 with an empty cart, the timeout returns directly to Idle without the overlay [A-23].
- **Acceptance criteria:**
  - AC1: Overlay at timeout ± 1 s.
  - AC2: A tap dismisses it and resets the timer.
  - AC3: Ignoring it returns to Idle at +15 s with an empty cart.
  - AC4: No overlay during a pending QR.

### FR-024 Staff access and settings home — MVP
- **Behavior [V]:**
  - A hidden 2 s corner hold on Idle opens SCR-14: "Staff only", "Staff access", "Enter the 4-digit staff PIN", 4 dots and a 3×4 keypad (1–9, blank, 0, ⌫), plus "Cancel" → Idle. Entering 4 digits auto-submits.
  - **Settings home:** "Kiosk settings", "{merchant} · {kiosk name}", "All systems ready" status, and four section rows (title, summary, status) for Printer, Payment, Kiosk behaviour, Device & network.
  - Header: "Lock" (back to the PIN) and "Exit" (→ Idle).
  - Footer: "Hitachi Kiosk {version} · Changes apply immediately".
- **Rules:**
  - The PIN is set in the dashboard and delivered as a salted hash in config. Verification happens on the device against the hash, so it works offline [D-20].
  - 5 consecutive wrong PINs → locked out for 5 min with a countdown. The lockout survives restart [D-20].
  - A wrong PIN shakes the dots and clears them.
  - Section statuses are live: printer Ready / Out of paper / Offline; payment Connected / Terminal offline; network Online / Offline.
  - "All systems ready" shows only when all are healthy. Otherwise it reads "N issues need attention".
- **Acceptance criteria:**
  - AC1: The correct PIN opens Settings home.
  - AC2: The 5th wrong attempt starts a 5 min lockout that persists across process death.
  - AC3: Exit returns to Idle.
  - AC4: The status row reflects a disconnected printer within 5 s.

### FR-025 Payment settings — MVP
- **Behavior [V]:**
  - Accepted methods: QRIS and Card terminal toggles. The Pay-at-counter toggle is hidden in 1.0.
  - Card terminal: Status ("Connected by USB" etc.), Terminal ID, and "Test terminal" ("Sends a Rp 0 check to the terminal"; Testing… → Terminal OK / failure message).
  - QRIS: Merchant ID (masked, e.g. "ID10 •••• 4721") and "QR code expires after" 3 / 5 / 10 min.
  - Prices & tax: "Restaurant tax (PB1)" (read-only from config) and "Round total to" None / Rp 100 / Rp 500.
- **Rules:**
  - Toggles can only *restrict* methods allowed by merchant config [A-24].
  - At least one method must stay on. Turning off the last one is refused with a message.
  - Rounding and QR expiry are local per device in 1.0 [A-25].
- **Acceptance criteria:**
  - AC1: Toggling Card off removes the Card tile on SCR-10 and the card option on SCR-11.
  - AC2: The last method cannot be turned off.
  - AC3: The expiry change applies to the next QR.

### FR-026 Kiosk behaviour settings — MVP
- **Behavior [V]:**
  - Ordering: Dine in, Takeaway, Suggest add-ons toggles.
  - Screen: "Return to idle after" 30 / 60 / 120 s ("No touch for this long cancels the order"), Brightness Low / Medium / High.
  - Accessibility: Sound effects, Reduce motion ("Simpler transitions for everyone"), Larger text ("Increase menu text size by 15 %").
- **Rules:**
  - At least one of Dine in / Takeaway stays on.
  - Brightness sets the app window brightness (Low 0.3, Medium 0.6, High 1.0) [A-26].
  - Reduce motion forces all animations to ≤ 1 frame, mirroring the design's `.rm` class [V]. The system setting "Remove animations" also triggers it.
  - Larger text scales customer-facing text by 1.15 without breaking layout (NFR-007).
  - All values persist locally and apply immediately.
- **Acceptance criteria:**
  - AC1: Each control changes behaviour on the next customer screen without restart.
  - AC2: The last order type cannot be disabled.
  - AC3: Larger text on Menu, Item, Combo and Your order has no clipped or overlapping text (screenshot test).

### FR-027 Device & network settings — MVP
- **Behavior [V]:**
  - Network: Wi-Fi SSID (or "Ethernet"), Signal strength meter, IP address, and "Connection test" ("Checks the store server and payment gateway" → "All reachable" or per-endpoint failure).
  - Menu sync: Last synced, Auto-sync toggle ("every 15 minutes"), "Sync now" (Syncing… → Up to date / error).
  - Device: Kiosk ID (backend kiosk code), App version, "Change PIN", and "Restart app" ("Takes about 20 seconds").
- **Rules:**
  - "Change PIN" asks for the current PIN, then the new PIN twice. It calls `PUT /v1/devices/{id}/staff-pin` and requires online [D-20]. SCR-20 (`ChangePin.dc.html`) has three steps: "Enter current PIN", "Enter new PIN" ("Choose 4 digits staff will remember") and "Confirm new PIN" ("Enter the new PIN once more"). A mismatch shakes the dots and shows "PINs didn’t match. Enter the new PIN again." Success shows "PIN updated" / "Use the new PIN next time you open settings." with "Back to settings". The screen notes "Requires an internet connection".
  - "Restart app" restarts the process and relaunches to Splash while staying in lock-task.
  - Reading the SSID requires location permission on Android 11+. If it is not granted, show "Wi-Fi name hidden" [A-27].
- **Acceptance criteria:**
  - AC1: Connection test reports backend and gateway reachability separately.
  - AC2: Sync now updates "Last synced".
  - AC3: Change PIN offline shows "Connect to the internet to change the PIN".
  - AC4: Restart returns to Idle in ≤ 20 s.

### FR-028 Menu sync and offline behaviour — MVP
- **Behavior:**
  - On binding and at startup, fetch merchant config and menu. With Auto-sync on, a periodic sync runs every 15 min [V], plus a sync on reconnect.
  - Sync is versioned (`menuVersion`, `If-None-Match`/ETag) and replaces the cache atomically.
  - Images are cached on disk and pre-fetched after sync.
  - While offline, a slim banner on customer screens reads "Ordering is paused — reconnecting…" [A-28]. Browsing works from the cache. Pay (SCR-10) and order submission are disabled.
  - When connectivity returns, the banner hides and actions re-enable automatically [D-11].
- **Rules:**
  - A sync never mutates a cart in progress. Prices are re-validated at FR-016.
  - A sync that arrives while a customer is ordering is applied when the order ends [A-29].
  - Connectivity = validated internet **and** backend health check OK.
- **Acceptance criteria:**
  - AC1: Airplane mode on Menu shows the banner within 5 s and disables Pay.
  - AC2: Restoring the network re-enables Pay within 10 s.
  - AC3: A menu price change on the backend is visible after Sync now.
  - AC4: Cold start offline with a cache shows the full menu with images.

### FR-029 Kiosk lockdown (lock task) — MVP
- **Behavior:**
  - The app is provisioned as **device owner** [D-22], allow-lists itself for lock task, and starts lock task after merchant binding.
  - Status bar, navigation and notifications are hidden or blocked.
  - The app is set as the persistent home activity and launches on boot.
  - Leaving lock task requires the staff PIN, via Settings → Device → "Exit kiosk mode" [A-30].
- **Rules:**
  - The screen stays on while in lock task (`FLAG_KEEP_SCREEN_ON`).
  - Volume/power key behaviour follows the device defaults.
  - If the app is not device owner (development devices), it falls back to screen pinning with a warning on Settings home.
- **Acceptance criteria:**
  - AC1: After a reboot the app is in the foreground within 60 s without user action.
  - AC2: Home, Back and Recents gestures cannot leave the app.
  - AC3: "Exit kiosk mode" requires the PIN.

### FR-030 Sound effects — MVP
- **Behavior:** When "Sound effects" is on [V], short sounds play for tap-to-add, payment success and payment failure. Sounds use the media stream at the system volume.
- **Acceptance criteria:**
  - AC1: Toggle off → no sounds.
  - AC2: Each sound is ≤ 1 s and does not block the UI.

### FR-031 Staff-facing error and status surfacing — MVP
- **Behavior:** Hardware and network faults that affect customers are visible to staff: the status rows (FR-024), a pulsing red dot on the Idle staff corner when any issue exists [A-31, V], and the reason text in each settings page. Local logs keep the last 7 days, rotated at 10 MB, with no customer data (NFR-006).
- **Acceptance criteria:**
  - AC1: Unplugging the receipt printer shows "Offline" on Settings home within 5 s.
  - AC2: The log directory never exceeds 10 MB.

### FR-032 Budget Plan group meal planner — MVP
- **Description:** The customer enters a budget and the number of adults and children. The kiosk recommends a complete meal plan whose **final** total lands exactly on the budget when possible, otherwise closest under it, with a closest-over alternative [D-26]. The UI starts simple (budget and people) and offers advanced options only on demand.
- **Rationale:** Self-order kiosks usually push upsell. Planning a fair meal for a whole group within a stated budget is rare, fits Indonesian family and group dining, and is the launch differentiator.
- **Entry:** a slim dark "Plan by budget" row at the top of the Popular view of the Menu (SCR-06), shown when merchant config `budgetPlanner.enabled` is true. It opens SCR-19 (`BudgetPas.dc.html`).
- **Step 1 — "Your table"** (eyebrow "Budget Plan · Step 1 of 2", title "Feed the whole table", subtitle "Set a budget and who's eating. We'll build complete meals that fit."):
  - Budget card: "Your budget", the amount in large type, the caption "Final total, tax included · exact to the nearest Rp 100", chips from config `quickBudgets` (default Rp 50k / 100k / 150k / 200k) and an "Other" chip that opens a number pad ("Enter your budget", "Exact to the nearest Rp 100. Plans match your final total, tax included.", "Use this budget"). The amount is a multiple of Rp 100.
  - People card: Adults 1–10 ("12+ years") and Children 0–10 ("Under 12") with 96 dpx steppers. Adults + children ≤ config `maxGroupSize` (default 10).
  - Dock: "{n} people · budget" with the amount, and "See plans" (→ Step 2). Disabled when the budget is empty or Rp 0.
  - Group above `maxGroupSize`: a card "Group too large" / "Please order in smaller groups" / "Budget Plan plans up to 10 people at a time. Split your table into smaller groups and build one plan for each." with "Set group to 10". The dock button reads "Reduce group" and is disabled.
- **Step 2 — "Your plan"** (eyebrow "Budget Plan · Step 2 of 2"). The title is "Two plans for you" when both an under and an over plan exist, "A perfect fit" for an exact match, and "Your plan" otherwise.
  - **Hero plan card** with a tag, title, the final total, a delta badge and "{n} people · {k} items · tax included":

    | Case | Tag / title | Badge |
    |---|---|---|
    | Exact | "Exact match" / "Right on your budget" (green ring) | "Perfect fit! Rp X · Rp 0 left" |
    | Under | "Best fit" / "Just under budget" | "Rp X left" |
    | Over | "Slightly above budget" / "Just over budget" | "+Rp X over" |
    | Budget below the cheapest plan | "Need Rp X more" / "Closest complete plan" | "Rp X more needed" |
    | Budget ≥ most generous plan | "More than enough" / "Everyone eats well" | "Your budget covers more than enough" |

  - A segmented control "Just under | Just over" switches the hero card. It shows only when both plans exist, and never for an exact match.
  - Collapsed rows: "What's included · N items" (items grouped per person — "Adult 1", "Adult 2", "Child 1 · Main · kid-friendly" — plus shared items) and "Price details · Rp X" (Subtotal, Promo, PB1, Rounding, Total).
  - **"Customize"** (row "Swap items · sides · tolerance") opens the bottom sheet "Customize your plan", whose header shows the live total. It holds: the toggles "Include sides" and "Include desserts"; the control "Strict | Up to +10 %" under "Allow a plan slightly above your budget"; the plan's items, each with "Swap"; "Reset to suggested"; and "Done". The sheet notes "Sides and desserts rebalance automatically to land as close to your budget as possible." Strict shows "Strict budget: only plans at or under your budget are shown."
  - Dock: "{n} people · {k} items · {badge}", the total, and "Add plan to order" (it becomes "Added to order"). "Back" returns to Step 1, and from Step 1 to the Menu.
- **Plan rules (BR-13 – BR-15):**
  - Every adult gets exactly 1 main + 1 drink. A combo (main + side + drink) counts as one adult's main and drink. A combo with `serves` = 2 covers two adults.
  - Every child gets exactly 1 main with `kidFriendly = true` + 1 drink. If the merchant has no kid-friendly main, the cheapest available main is used and the plan notes "Kids' menu not available" [D-28].
  - Sides and desserts are added only while they bring the total closer to the target, at most one per person per plan.
  - A plan never contains more than one main per person.
  - Products with `servesAdults` > 1 (e.g. Family Bucket) are not used as plan mains in 1.0. Group sharing comes from combos with `serves` > 1.
  - Sold-out items (`available = false`) are excluded.
- **Exact match:** If any candidate's final total equals the budget exactly, that plan is the recommendation and no over/under pair is shown. The planner searches combinations (mains, drinks, combos, and at most one side or dessert per person) for an exact total, pricing every candidate with the PricingEngine, so a promo threshold or rounding can make a plan land exactly on the budget.
- **Customize behavior:** "Swap" replaces an item with the next alternative in the same role. Sides and desserts are then re-picked so the plan lands on an exact total when one exists, otherwise on the nearest total under the budget (the over plan: the nearest total above it, within the cap). "Strict" hides the over plan. "Up to +10 %" allows it, capped at `overCapPercent` (default 10 [D-27]). "Reset to suggested" restores the original plan.
- **Pricing:** every candidate is priced with the same `PricingEngine` as checkout (FR-013). A plan whose raw subtotal crosses a promo threshold can therefore land **under** the budget.
- **Ranking:** among candidates with the same final total, prefer, in order:
  1. The higher subtotal (more food for the same price).
  2. Variety (fewest repeated mains).
  3. Merchant `plannerWeight`.
  4. Featured items.
- **Edge cases:**
  - Budget below the cheapest complete plan → show that cheapest plan as "Need Rp X more" with "This is the lowest-priced complete meal for your group. Raise the budget or adjust the group."
  - Budget ≥ the most generous allowed plan → show that plan as "More than enough" with "This is our most generous plan for your group, and it still fits."
  - No plan within the over cap → only the under plan is shown, with "No plan fits within 10 % over your budget, so only the under-budget plan is shown."
  - A menu sync during the order does not change plans until the order ends (FR-028).
- **Performance:** both plans, including the exact-match search and a Customize re-balance, are computed on-device in ≤ 100 ms on the target iMin device for the design sample menu (23 products + 4 combos) and the maximum group of 10.
- **Acceptance criteria:**
  - AC1: Sample menu, 2 adults + 1 child, budget Rp 150.000 → under plan total **Rp 149.600**, over plan total **Rp 150.700** (cap Rp 165.000). Both plans cover all three people under BR-13/BR-14.
  - AC2: Sample menu, 1 adult + 1 child, budget Rp 99.000 → an **exact match** at **Rp 99.000**. It has subtotal **Rp 100.000**: it uses the promo threshold and wins the tie against a Rp 90.000 subtotal plan with the same total. Step 2 shows one hero card titled "A perfect fit" with "Perfect fit! Rp 99.000 · Rp 0 left", no over/under control.
  - AC3: 2 adults + 1 child, budget Rp 100.000 → one plan, total Rp 116.600 (the cheapest complete plan), with "Need Rp 16.600 more".
  - AC4: A menu with no kid-friendly main gives the child the cheapest main and shows the "Kids' menu not available" note. Otherwise only kid-friendly mains are chosen for children.
  - AC5: Tapping "Add plan to order" makes the cart lines and totals on SCR-10 equal the plan exactly.
  - AC6: Plan computation ≤ 100 ms on the target device (benchmark).
  - AC7: With `budgetPlanner.enabled = false` the Menu shows no "Plan by budget" row.
  - AC8: Whenever an exact-total plan exists for the budget and group, the planner returns it (property test over the sample menu, budgets Rp 50.000–Rp 300.000 in Rp 100 steps).
  - AC9: After a swap in Customize, the plan total is either exact, or the nearest achievable total on the plan's side of the budget, and never beyond the over cap.
  - AC10: "Strict" hides the over plan and the "Just under | Just over" control. "Up to +10 %" restores them when an over plan exists.
  - AC11: A group of 11 or more shows "Please order in smaller groups", no plans, and a disabled dock button.
- **Screens/flows:** SCR-06, SCR-19, SCR-10; J9.

---

## 7. Screen states and interaction details

| Screen | Loading | Empty | Success | Failure | Offline |
|---|---|---|---|---|---|
| SCR-01 Splash | Animations + init | — | Route | Corrupt storage → SCR-02 | Cached route |
| SCR-02 Sign in | "Signing in…" spinner, button disabled | — | → SCR-03 | Not registered (SN shown, "Try again"), serial unreadable (button disabled), network timeout, device disabled (FR-003) | "Can't reach Hitachi Kiosk" + "Try again" |
| SCR-03 Merchants | "Loading restaurants…" skeleton cards [A-32, V] | "No restaurants yet" + "Refresh list" | → Idle | Bind or menu-download failure + "Retry" / "Log out"; closed merchant shown disabled | Failure panel + retry |
| SCR-04 Idle | Tone placeholder while images load | Tone placeholder | — | Red dot on the staff corner when a fault exists | Works from cache |
| SCR-05 Order type | — | — | Card fills, → Menu | Single-option variant with note | Unaffected |
| SCR-06 Menu | Tone placeholders per image | "Nothing here right now" + "See popular items" [V] | Quick-add feedback | "Maximum 20 per item" toast; required-option items show "Choose ›" | Amber banner "Ordering is paused — reconnecting…"; browse OK; add and View order disabled |
| SCR-07 Combo | — | — | "Added" state | Required side/drink missing → disabled CTA ("Pick side & drink"); sold-out options | Browse OK |
| SCR-08 Item | — | — | "Added" state | Required size missing → disabled CTA ("Choose a size to continue"); sold out → "No longer available"; "Maximum 20 per item" | Browse OK |
| SCR-09 Upsell | — | "You're all set" fallback | → SCR-10 | — | Browse OK |
| SCR-10 Your order | — | "Your tray is empty" / "Back to menu" [V] | — | PRICE_CHANGED banner + "Review"; ITEM_UNAVAILABLE "No longer available" + "Remove"; no methods "No payment methods right now"; promo nudge banner | Banner, Pay disabled ("Pay unavailable while offline") |
| SCR-11 Checkout | "Creating your payment…" before the QR or terminal prompt [A-34, V] | — | Processing → Complete | Failed variants: QR code expired, Payment cancelled, Card declined, Terminal not responding (FR-019) | "Ordering is paused" failure, "Reconnecting…"; checking status "Checking your payment…" |
| SCR-12 Complete | — | — | Ticket (dine-in or takeaway copy) | "Receipt couldn't print" card | Unaffected (already paid) |
| SCR-13 Still there? | — | — | Continue → previous screen | Countdown ends → Idle; "Start over" → Idle | Same |
| SCR-19 Budget Plan | Computing (≤ 100 ms, no spinner) | Budget empty: "See plans" disabled | Exact / under / over hero card | "Need Rp X more" plan; "More than enough"; group too large "Please order in smaller groups" | Works from cache; checkout gated by FR-028 |
| SCR-14–18, 20 Settings | Action busy labels [V] | No printers: "Add a printer" | Done labels [V]; "PIN updated" | Inline error per row; "PINs didn’t match" | Offline badges; PIN change blocked ("Requires an internet connection") |

### 7.1 Interaction primitives [V]
- **Press feedback:** `.press` scale 0.96 (0.97 on large cards) over 140 ms, easing `cubic-bezier(.2,0,0,1)`. Cards use `.lift` 0.985 over 220 ms.
- **Segmented controls:** a sliding white indicator with spring easing `cubic-bezier(.3,1.15,.4,1)`, 500–550 ms.
- **Number changes:** prices and quantities "roll" (translateY 60 % → 0 with fade, 380–480 ms, `cubic-bezier(.16,1,.3,1)`). Direction-aware for qty up/down.
- **Entrance:** titles rise (translateY 105 % → 0, 800–1000 ms). Content fades up 24 dpx (600–700 ms) with 50–90 ms stagger.
- **Docks:** dock-in translateY 140 % → 0 over 800 ms @300 ms.
- **Reduced motion:** every animation and transition collapses to ≤ 1 frame with no delay [V].

Complete per-screen motion inventory: §11.4.

---

## 8. Business rules summary

| ID | Rule | Source |
|---|---|---|
| BR-01 | All money is integer IDR. Format `Rp 1.234.567`. | [V] |
| BR-02 | Tax PB1 is exclusive, computed on subtotal after discount, rate from config (10 % in design). | [V, D-19] |
| BR-03 | One threshold promo per order. | [V, A-12] |
| BR-04 | Rounding is applied to the final total, upward, to the configured step. | [V, A-13] |
| BR-05 | Max 20 per line, 30 lines per order. | [V qty 20, A-10] |
| BR-06 | The order number comes from the backend: a letter plus 3 digits, reset daily per merchant. | [V format, D-21] |
| BR-07 | An order is paid only on backend `PAID`. | [A-15] |
| BR-08 | One order per checkout. Payment retries reuse it. Cart edits after "Back to order" create a new one. | FR-016 |
| BR-09 | The cart resets at the end of every order. | FR-006 |
| BR-10 | Checkout requires connectivity. Browsing does not. | [D-11] |
| BR-11 | At least one order type and one payment method stay enabled. | FR-025, FR-026 |
| BR-12 | One printer per role. | FR-022 |
| BR-13 | Budget Plan: each adult gets 1 main + 1 drink; a combo covers its `serves` adults. | FR-032, D-26 |
| BR-14 | Budget Plan: each child gets 1 kid-friendly main + 1 drink, falling back to the cheapest main. | FR-032, D-28 |
| BR-15 | Budget Plan: plans are compared on the final total (after promo, tax, rounding). "Over" plans are capped at the merchant's `overCapPercent` (default 10 %). An exact total always wins. | FR-032, D-27 |

---

## 9. Non-functional requirements

| ID | Category | Requirement | Acceptance |
|---|---|---|---|
| **NFR-001** | Platform | `minSdk 30` [D-04], `compileSdk`/`targetSdk` = latest stable at 0.1.0 start (≥ 35). Primary target: the iMin kiosk the merchant deploys, believed to run Android 14+ [D-05, OQ-01]. Kotlin, Jetpack Compose (Material 3 used only as a foundation; visual design is custom) [D-24]. | Builds and passes all tests on API 30 and on the target device's API level. |
| **NFR-002** | Display | Portrait locked, designed for 1080 × 1920 px. All layouts are expressed in design px and converted with a single scale factor `s = screenWidthDp / 1080` (§11.2), so 1080-wide screens of any density render identically. Supported range: 1080 × 1920 to 1200 × 1920 portrait. Other aspect ratios letterbox vertically [A-35]. | Screenshot tests at 1080 × 1920 @ 160, 240 and 320 dpi match references. |
| **NFR-003** | Performance | Cold start to first splash frame ≤ 1.0 s. Splash → Idle ≤ 5.0 s warm cache. Category switch ≤ 300 ms. Customer UI at 60 fps with ≤ 1 % janky frames (P90 frame time ≤ 16 ms) on the target device during the scripted journey. Tap feedback ≤ 100 ms. | Macrobenchmark + JankStats reports (testing-plan §6.10). |
| **NFR-004** | Reliability | 72 h soak (scripted orders every 3 min, idle in between): 0 crashes, 0 ANRs, Java heap growth ≤ 20 MB from hour 1 to 72, no leaked Activities (LeakCanary in debug). Recovery from process death without manual action (FR-016). | Soak report in 0.9.0. |
| **NFR-005** | Security | Device token in Android Keystore-backed encrypted storage. HTTPS only (TLS 1.2+), cleartext disabled via network security config. Certificate pinning to the backend's public-key set with a backup pin [A-36]. Staff PIN stored only as a salted hash from the backend, compared in constant time. No card data handled (EDC). Release builds are R8-minified, `debuggable=false`, and contain no debug simulate tools. Runtime permissions (Bluetooth connect/scan, USB, location for SSID) are requested only in staff settings with rationale. Exported components are minimal. | Security checklist (testing-plan §6.12) passes. |
| **NFR-006** | Privacy | The app collects no customer personal data. There is no telemetry or third-party analytics/crash SDK [D-23]. Local logs exclude payment references beyond the last 4 characters, never contain the PIN, and are rotated (FR-031). | Log review in 0.9.0. Dependency audit shows no analytics SDKs. |
| **NFR-007** | Accessibility | Touch targets ≥ 88 dpx (≈ 48 dp at scale). All primary targets in the design are already 88–168 dpx [V]. Text contrast ≥ 4.5:1 (≥ 3:1 for text ≥ 24 dpx bold). TalkBack labels mirror the design's aria labels, e.g. "Add {item}", "Your order, N items", "Staff settings, press and hold". Reduce motion and Larger text (+15 %) per FR-026. No information conveyed by color alone (selected states also show a check or indicator) [V]. | Accessibility Scanner shows no errors on SCR-02…SCR-18. Manual TalkBack walkthrough of J1. |
| **NFR-008** | Localization | The UI is English-only in 1.0 [D-29]. All UI strings still live in `values/strings.xml`, with no hard-coded user-visible strings (lint `HardcodedText` / Compose check), so a language can be added later. Plurals use `plurals` resources. Currency formatting uses the custom IDR formatter (BR-01), not the locale default. | Lint passes. Pseudo-locale run shows no clipped text. |
| **NFR-009** | Motion fidelity | Durations, delays and easing curves match §11.4 within ± 10 %. Reduced motion collapses all of them. | Motion review checklist per screen (testing-plan §6.9). |
| **NFR-010** | Network | Timeouts: connect 10 s, read 15 s. Polling every 2 s. Retries with exponential backoff for idempotent calls only. All network calls off the main thread. Behaviour under offline as in FR-028. | Fault-injection tests (MockWebServer). |
| **NFR-011** | Maintainability | Module structure per §12. Domain logic (pricing, cart, order state machines) is pure Kotlin with ≥ 90 % line coverage. Data layer ≥ 70 %. Static analysis (Android Lint, ktlint/detekt) with zero errors in the `./gradlew verify` gate (no CI in 1.0, D-31). Public interfaces are documented. | `verify` report per milestone (`docs/test-reports/`). |
| **NFR-012** | Testability | Every external dependency sits behind an interface with a fake: backend API, payment gateway, card terminal, printers, device identity, clock, connectivity. A debug "Developer options" menu (debug builds only) lets testers switch fakes and force outcomes. | All J1–J8 runnable on an emulator with fakes. |
| **NFR-013** | Images | Images load asynchronously with memory and disk cache. Placeholder = the design's tone color for the category/slot [V Photo component]. A decode failure shows the placeholder. Max image cache 200 MB [A-37]. | Offline cold start shows cached images. |
| **NFR-014** | Distribution | Signed release APK (v2+v3 signature) installed via MDM/sideload [D-22]. `versionCode = MAJOR*10000 + MINOR*100 + PATCH`. In-place upgrade preserves activation, binding and settings. | Upgrade test from the previous milestone build. |
| **NFR-015** | Power/thermal | Screen always on in kiosk mode. Idle slideshow GPU work bounded (no more than 1 animated layer at a time). Device surface temperature stays within the manufacturer's spec during the soak. | Soak observation. |
| **NFR-016** | Compatibility | Works on API 30–36 emulators and on the target iMin device. Bluetooth printers via classic SPP (ESC/POS). USB printers via USB host bulk transfer (ESC/POS). Built-in via iMin SDK. | Device matrix (testing-plan §2.3). |
| **NFR-017** | Time | Business timestamps (order time, expiry) come from the server. The local clock is used only for UI timers. | Clock-skew test (device time +10 min) still shows correct QR expiry. |

---

## 10. Technical requirements: backend contract (proposed)

No backend exists yet [D-06]. The app **defines** the contract below, which the backend team implements. A real backend must be live before 0.9.0 [D-06].

**The authoritative request/response contract is [`api/api-spec.md`](api/api-spec.md)**: full JSON examples, error codes, enums and mock scenarios, written so a mock server can be built first. The table below summarizes it. Where field names differ, `api-spec.md` wins. In 0.1.0, DEV-006 derives a machine-readable `docs/api/openapi.yaml` from it.

### 10.1 Conventions
- Base URL per environment (`dev`, `staging`, `prod`) is a build config.
- Auth: `Authorization: Bearer <deviceToken>` on all calls after activation. A 401 means the token is revoked: clear activation and go to SCR-02.
- Errors use the JSON envelope `{ "code": "PRICE_CHANGED", "message": "...", "details": {...} }`.
- Money is integer IDR. Time is ISO-8601 UTC.
- Mutating calls accept an `Idempotency-Key` header.

### 10.2 Endpoints
| # | Method & path | Purpose | Used by |
|---|---|---|---|
| E1 | `POST /v1/devices/activate` `{serialNumber, model, appVersion}` → `{deviceId, deviceToken, kioskCode, kioskName, merchants[]}` | SN activation | FR-003 |
| E2 | `GET /v1/devices/{id}/merchants` → `[{id, name, cuisine, logoUrl, photoUrl, status: live\|coming_soon, isOpen}]` | Merchant list | FR-004 |
| E3 | `PUT /v1/devices/{id}/binding` `{merchantId}` | Bind kiosk | FR-004 |
| E4 | `DELETE /v1/devices/{id}/binding` | Log out / unbind | FR-004 |
| E5 | `GET /v1/merchants/{id}/config` (ETag) → `{mode, accentColor, brandName, logoUrl, taxRate, taxLabel, roundingStepsAllowed, promos[], paymentMethods[], qrisMerchantIdMasked, staffPin: {hash, salt, algo}, receipt: {address, npwp}, upsell: {categoryIds[]}, budgetPlanner: {enabled, overCapPercent, maxGroupSize, quickBudgets[]}, idlePromos[]}` | Merchant config | FR-006, FR-013, FR-015, FR-024, FR-032 |
| E6 | `GET /v1/merchants/{id}/menu` (ETag) → `{menuVersion, categories[], products[], modifierGroups[], combos[], featured[], promoBanners[]}` | Menu | FR-009–FR-011, FR-028 |
| E7 | `POST /v1/orders` (Idempotency-Key) → `{orderId, orderNumber, totals}` or `PRICE_CHANGED` / `ITEM_UNAVAILABLE` | Create order | FR-016 |
| E8 | `POST /v1/orders/{id}/cancel` | Abandon order | FR-016 |
| E9 | `POST /v1/orders/{id}/payments` `{method: QRIS\|CARD}` (Idempotency-Key) → QRIS `{paymentId, qrString, expiresAt}`; CARD `{paymentId, amount, terminalRef}` | Start payment | FR-017, FR-018 |
| E10 | `GET /v1/orders/{id}/payments/{pid}` → `{status: PENDING\|PAID\|FAILED\|EXPIRED\|CANCELLED, reason?}` | Poll status | FR-017–FR-019 |
| E11 | `POST /v1/orders/{id}/payments/{pid}/cancel` | Cancel payment | FR-017–FR-019 |
| E12 | `POST /v1/orders/{id}/payments/{pid}/terminal-result` `{approved, terminalRef, tid, rawCode}` | Relay EDC result if the terminal reports to the kiosk (depends on OQ-03) | FR-018 |
| E13 | `PUT /v1/devices/{id}/staff-pin` `{currentPinProof, newPin}` | Change PIN | FR-027 |
| E14 | `GET /v1/health` and gateway health | Connection test | FR-027, FR-028 |

### 10.3 Menu data model (minimum)
- In 1.0 the app reads only the `en` value of every `{en,id}` field and ignores other languages [D-29]. The `promoBanners` and `idlePromos` fields: `idlePromos` feeds the Idle slideshow, and `promoBanners` is not rendered on the Menu in 1.0 [D-30].
- **Category** `{id, name{en,id}, sort, tone}`
- **Product** `{id, categoryId, name{en,id}, description{en,id}, price, kcal?, badge?{type,label{en,id}}, tags{en,id}?, imageUrl?, available, modifierGroupIds[], role: MAIN|SIDE|DRINK|DESSERT, kidFriendly, servesAdults, shareable, plannerWeight?}`. The last five fields drive Budget Plan (FR-032).
- **ModifierGroup** `{id, name{en,id}, type: SINGLE|MULTI|LEVEL, required, min, max, options[]}`
  - **Option** `{id, name{en,id}, priceDelta, default, imageUrl?}`
  - For LEVEL groups, each option has levels `[None, Regular, Extra]` with per-level `priceDelta`.
- **Combo** `{id, name{en,id}, shortName, includes{en,id}, price, separatePrice, serves (multiplier), slots: [{type: SIDE|DRINK, optionIds[], upcharges}], sizeUpcharge}`
- **Promo** `{id, type: THRESHOLD_FIXED, threshold, amount, label{en,id}}`

---

## 11. UI and design-system requirements

### 11.1 Tokens [V]
| Token | Value | Use |
|---|---|---|
| `bg` | `#FAF8F5` | Screen background |
| `ink` | `#171513` | Primary text, dark docks, selected pills |
| `muted` | `#6F6961` | Secondary text |
| `line` | `#E9E4DC` | Outline pills, progress tracks |
| `surfaceAlt` | `#F1EDE7` | Pills, segmented tracks, chips |
| `cardBorder` | `#EFE9E0` | Card outlines |
| `radioOff` | `#D9D2C7` | Unselected radio/check borders |
| `success` / `successBg` | `#2F7A50` / `#EAF3EC`, `#E6F0E9` | Open now, Save, Secure payment, not-charged note, toggles on |
| `accent` (default) | `#C8552B` (options `#2F5D4A`, `#3A4C7A`, `#171513`) | Per merchant from config `accentColor` [A-38] |
| `dockMuted` | `#B9B2A8`, `#B5AEA4` | Secondary text on dark docks |
| `dockTile` / `dockTileBorder` | `#211E1B` / `#2E2B27` | Payment method tiles |
| `dockDisabled` | `#26231F` / `#8F887E` | "Add an item to pay" |
| `surface` | `#FFFFFF` | Cards, rows, sheets |
| Type | Schibsted Grotesk 400/500/600/700/800 (bundled font resource, SIL OFL) | All text |
| Type scale (dpx / weight) | display 112/800, title 80/700, section 44/700, item 36/600, body 30/400, button 34/700, meta 26/500. Nothing below 24. | Headlines are tight (−0.02 to −0.035 em) |
| Radii | 999 (pills), 48 (docks), 40 (cards), 32/28/24 (smaller) | — |
| Layout | 72 dpx side margins (936 content width); header top 48, height 96; 8-pt spacing; touch targets ≥ 96 dpx; one accent action per screen, in the bottom dock | Elevations: flat, and floating (`0 24 48 rgba(23,21,19,.10)`) for docks and overlays |

### 11.2 Units
1 dpx = `screenWidthDp / 1080` dp. Text uses the same factor in `sp`, multiplied by 1.15 when Larger text is on. A Compose `DesignUnit` helper provides `Int.dpx` and `Int.spx`. This mirrors the design at any density (NFR-002).

### 11.3 Components (shared)
Pill button, Primary CTA (accent, with arrow tile), Dark dock, Segmented control (spring indicator), Radio card, Check row, Option card (with "Required" tag), Switch row, Collapsible row with summary, Bottom sheet, Banner (offline, price changed, promo nudge), Toast, Slim entry row ("Plan by budget"), Plan hero card with delta badge, Quantity stepper (dock and pill variants), Product card, Category pill, Photo slot (tone placeholder), Rolling number text, Status chip, Settings row types (toggle, segmented, action with busy/done, info, input, meter), PIN pad, Progress dots, Countdown ring, Overlay with blurred backdrop.

Removed from earlier designs: the Menu promo carousel, the language segmented control, photo cards and hero on Order type, the QR scan line and the Checkout instruction steps [D-30].

### 11.4 Motion inventory [V]
| Screen | Key animations (duration @ delay, easing) |
|---|---|
| Splash | markIn 900 @150 (.16,1,.3,1); dotIn 600 @700 (.3,1.15,.4,1); ringOut 1400 @900; title rise 900 @900/1000; tagline up 800 @1350; bar fill 2200 @1600 (.65,0,.35,1); auto-advance 3.9 s |
| Sign in | markIn 800 @80; breathe 3.2 s loop; title rise 900 @160/240; dock 800 @300; spinner 800 linear |
| Merchants | title rise 900; card up 700 @220; soon rows stagger 60 ms from 360; open-now pulse 1.8 s loop; arrow nudge 1.8 s loop |
| Idle | slide crossfade 1400; Ken Burns 8 s; progress 7 s linear; sheet up 900 @100; title rise 1000 @250; CTA ring 2.8 s loop; tap icon 1.8 s loop |
| Order type | logo tile 1100 @100; cards in 900 @360/440; selection fill 600; check pop 500 @150; ring 800 @200; icon lift 600 @120; advance 950 |
| Menu | pills in 600 stagger 50; cards 700 stagger 60; fly-to-cart 760 (.55,0,.75,.2); dock bump 460 @650; total roll 480 @650 |
| Item | hero scale 1200; sheet up 800 @120; sections fade-up 650 @560/640/720; qty roll 340; price roll 420; fly 820; swap-in 480 |
| Combo | list fade-up stagger 60 from 200; radio dot 420; check 380; size indicator 550 spring; total roll 460; added state 1.8 s |
| Your order | rows in 700 stagger 70; remove 460; panel up 900 @300; totals roll 420; method tiles 700 stagger 70 from 500 |
| Checkout | QR in 900 @220; countdown ring 1 s steps; card tap 2.6 s loop; processing spinners 1.1 s / 1.9 s; fail pop 560 + shake 520 @500 |
| Complete | flood 1500; badge 700 @900; check draw 560 @1250; ring waves 1600 @1050/1300; confetti 1500 ~@1150; number digits 600 stagger 90 from 1700; receipt print 1800 @2100; return ring 20 s |
| Budget Plan | step swap crossfade + 24 dpx slide; hero card rise 700; collapsible rows 400; Customize sheet up 620 (.16,1,.3,1); delta badge pop |
| Still there? | scrim in 600; countdown ring 1 s steps; last-5-s pulse |
| Settings | keypad stagger 30 from 260; dot pop 360; action busy 1.5 s → done → idle 3.8 s |

---

## 12. Android architecture

Greenfield project [V: no code]. Decisions [D-24]: Kotlin, Jetpack Compose, single Activity, MVVM with unidirectional state (`StateFlow<UiState>`), coroutines/Flow, Hilt DI, Room (cache + pending order), DataStore (settings) and Secure DataStore (token: DataStore encrypted with Tink AEAD, keyset wrapped by an Android Keystore key), Retrofit + OkHttp + kotlinx.serialization, Coil (images), WorkManager (periodic sync), Navigation Compose.

### 12.1 Modules
Sized for one developer. Features live as packages inside `:app` rather than separate modules, which avoids build overhead while keeping the boundaries that matter testable.

| Module | Responsibility | Depends on |
|---|---|---|
| `:app` | Activity, navigation graph, feature packages (`splash`, `activation`, `merchant`, `idle`, `ordertype`, `menu`, `item`, `combo`, `upsell`, `cart`, `checkout`, `complete`, `settings`), ViewModels, DI wiring, lock-task | all |
| `:core:designsystem` | Tokens, `DesignUnit`, typography, motion specs, shared components (§11.3), reduce-motion/large-text CompositionLocals | — |
| `:core:domain` | Pure Kotlin: models, `PricingEngine`, `Cart`, `OrderSession` and `PaymentStateMachine`, repository interfaces, `Clock` | — |
| `:core:data` | Retrofit API, DTO↔domain mapping, Room DB, DataStore, sync worker, repositories, connectivity monitor | `:core:domain` |
| `:core:hardware` | `DeviceIdentityProvider`, `PrinterManager` + drivers (iMin, USB ESC/POS, BT ESC/POS), receipt/ticket renderer, `CardTerminal` interface + impls, fakes | `:core:domain` |
| `:core:testing` | Fakes and test fixtures shared by unit/UI tests | `:core:domain` |

### 12.2 Key state machines
- **OrderSession:** `Idle → TypeChosen → Building → Reviewing → Submitting → Paying → Paid → Completed`, with `Cancelled` / `TimedOut` from any customer state before `Paying`.
- **Payment:** `Creating → AwaitingCustomer(QR|Terminal) → Processing → Paid | Failed(reason) | Unknown(polling)`. `Failed → Creating` on retry or switch.

### 12.3 Permissions
`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` (Android 12+), the `BLUETOOTH` / `BLUETOOTH_ADMIN` legacy pair (API 30), `ACCESS_FINE_LOCATION` (SSID, optional), USB host feature (not required), `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` only if a print service needs it [A-39]. Device-owner admin receiver for lock task.

---

## 13. Dependencies, constraints and risks

### 13.1 Dependencies
| ID | Dependency | Needed by | Owner |
|---|---|---|---|
| DEP-1 | Backend implementing §10 | Mock from 0.1.0. Real by 0.9.0 | Backend team [D-06] |
| DEP-2 | Company payment gateway API + sandbox (QRIS, card) | Spec by 0.5.0. Sandbox by 0.9.0 | Company gateway team [D-12] |
| DEP-3 | EDC terminal model, transport and protocol | Spec by 0.5.0 | Company / bank [D-13, OQ-03] |
| DEP-4 | Target iMin device (≥ 1 unit) and SDK (printer, SN) | 0.2.0 (SN), 0.6.0 (printer) | Hitachi [OQ-01] |
| DEP-5 | Upsell screen design | Delivered in design 1.1 (`Upsell.dc.html`) | Product owner [OQ-05, closed] |
| DEP-6 | Real menu content + images for one pilot merchant | 0.9.0 | Merchant |
| DEP-7 | USB and Bluetooth ESC/POS test printers (58 and 80 mm) | 0.6.0 | Hitachi |
| DEP-8 | Menu items tagged with `role` and `kidFriendly` (dashboard field + merchant data) | Mock from 0.4.0; real by 0.9.0 | Backend team + merchant |

### 13.2 Constraints
- One Android developer, about 3 months to 1.0.0 [D-25].
- No backend or gateway spec at kickoff [D-06, D-13a].
- Distribution via MDM/sideload as device owner [D-22].

### 13.3 Risks
| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-1 | Gateway/EDC specs arrive late, blocking real payment | High | Blocks 1.0.0 | Fakes behind `PaymentGateway` / `CardTerminal` interfaces from 0.5.0. Escalate if no spec by 0.5.0 exit. Card can ship in 1.0.1 only by explicit PO decision. |
| R-2 | Backend not live by 0.9.0 | Medium | Blocks 1.0.0 | Contract-first OpenAPI in 0.1.0, contract tests the backend can run, weekly sync. |
| R-3 | SN not readable without the iMin SDK or privileged permission | Medium | Blocks activation | Spike in 0.2.0 on real hardware (DEV-012). Fallback: `Build.getSerial()` as device owner. |
| R-4 | iMin device OS is older than expected | Low | minSdk issue | minSdk 30 chosen for margin [D-04]. |
| R-5 | Bluetooth printer variety (non-standard ESC/POS) | Medium | Print failures | Support the ESC/POS baseline. Certify a named list of printers. |
| R-6 | Animation-heavy UI janks on kiosk SoC | Medium | Poor UX | Macrobenchmark from 0.3.0. Reduce-motion escape hatch. Profile on device in 0.8.0. |
| R-7 | Scope (≈20 screens including Budget Plan + 3 hardware integrations) for one developer in about 3 months; the estimate is ~14.75 weeks | High | Date slip | Strict milestone exit criteria. Defer list (§3.2). Weekly burn-down. Budget Plan's Customize sheet (swap with rebalancing) can move to 1.0.1 if the date is fixed. |
| R-8 | Upsell design not delivered | Closed | — | The design was delivered in 1.1 (`Upsell.dc.html`). |
| R-9 | Merchants tag menu roles or kid-friendly items poorly, so Budget Plan plans look wrong | Medium | Bad first impression of the differentiator | Dashboard validation (every product needs a role). Kiosk falls back per BR-14. Per-merchant `budgetPlanner.enabled` lets the feature stay off until the data is ready. |

---

## 14. Decision log (interview, 2026-10-22)

| ID | Decision |
|---|---|
| D-01 | Deliverables: PRD, dev plan and testing plan only. No app code in this task. |
| D-02 | The published light design is the visual source of truth. It is stored in `docs/ui-design/`. |
| D-03 | Business model: Hitachi offers the kiosk to many merchants (SaaS). |
| D-04 | `minSdk 30` (Android 11). |
| D-05 | Hardware: an iMin kiosk. Exact model unknown, believed to run Android 14+. |
| D-06 | No backend exists. The app defines the API contract. A real backend must be live by 1.0.0, built outside the Android plan. |
| D-07 | Operating mode is a per-device setting. 1.0.0 ships single-merchant mode only. |
| D-08 | Food-court mode (post-1.0) uses one merchant per order. |
| D-09 | Languages: English and Bahasa Indonesia, fully translated. **Superseded by D-29: English only in 1.0.** |
| D-10 | Kitchen gets orders through the backend. The kiosk prints the customer receipt with the order number. |
| D-11 | Offline: browse the cached menu, but checkout is blocked until reconnected. No offline order queue. |
| D-12 | QRIS and Card must work in 1.0.0, through the company's own payment gateway. |
| D-12a | "At counter" is hidden in 1.0.0. |
| D-13 | Card payments are taken on a separate EDC terminal. |
| D-13a | The gateway API/SDK is not documented yet. |
| D-14 | Login sends the device SN to the backend, which matches it to a device registered in the dashboard. |
| D-15 | Inactivity: warn ("Still there?") first, then cancel. |
| D-16 | Upsell is included in 1.0.0. The screen is now designed (`Upsell.dc.html`). |
| D-17 | Kitchen ticket kept, printed on kiosk-connected printers. |
| D-18 | Printer selection: built-in (iMin SDK), USB and Bluetooth. Each printer can be assigned Receipt and/or Kitchen. |
| D-19 | Tax, rounding and promos are configured in the backend. The kiosk computes totals and the backend re-validates. |
| D-20 | Staff PIN comes from the backend (synced hash), with lockout after 5 tries. The app runs in Android lock-task mode. |
| D-21 | Order numbers are generated by the backend with a daily reset. |
| D-22 | Distribution: signed APK via MDM/sideload, app as device owner. |
| D-23 | No crash reporting or telemetry in 1.0.0. |
| D-24 | Stack: Kotlin + Jetpack Compose. |
| D-25 | Team/timeline: solo developer, about 3 months. |
| D-26 | Budget Plan (formerly "Budget Pas") group meal planner (budget + adults + children → closest under / closest over) ships in 1.0.0 as the differentiator. |
| D-27 | The "just over" plan is capped by a merchant-set percentage, default 10 %. |
| D-28 | Children's meals use only kid-tagged items, falling back to the cheapest main when none exist. |
| D-29 | The UI is English-only in 1.0.0. Runtime language switching and Bahasa Indonesia are deferred post-1.0. Strings stay externalised so a language can be added later. Supersedes the localization part of D-09. (Product owner, design review, 2026-10-22.) |
| D-30 | Design simplifications in the final "Linen & Paprika" design: the Menu promo carousel is removed, Order type has no photos, Checkout has no scan-line animation, no instruction steps and no "Step 3 of 3" indicator, and Budget Plan starts simple (budget and people) with advanced options behind "Customize". Budget Plan also gains an exact-match result. (Product owner, design review, 2026-10-22.) |
| D-31 | No CI service in 1.0. Every check a CI job would run is in one local Gradle task, `./gradlew verify`, which must pass before merging to `main` and at each milestone exit. Instrumented tests run on local emulators at the same points. CI can be added later by running the same task. (Product owner, 2026-10-22.) |

## 15. Assumptions (unconfirmed)

| ID | Assumption | Risk if wrong |
|---|---|---|
| A-01 | An unregistered SN shows an error with the SN visible and "Try again". | Copy/UX change only. |
| A-02 | As device owner, `Build.getSerial()` is a valid SN fallback. | Activation blocked (R-3). |
| A-03 | A closed merchant is shown disabled ("Closed · opens {time}") and cannot be bound, as in the design. | Minor. |
| A-04 | A short tap on the hidden staff corner does nothing. | A customer tap there is ignored. Minor. |
| A-05 | With one order type enabled, SCR-05 still shows (one card). | Could skip the screen instead. Minor. |
| A-06 | Superseded by D-29: staff settings are English. | — |
| A-07 | Greeting by time of day (05–11 morning, 11–17 afternoon, 17–05 evening). | Copy only. |
| A-08 | Products with required choices open Item detail on "+". | UX. |
| A-09 | Unavailable items are hidden. | UX (OQ-08). |
| A-10 | Limits: 20 per line, 30 lines. | Config change. |
| A-11 | Process death mid-order resets to Idle, except during payment, which recovers. | UX. |
| A-12 | One promo per order, threshold-fixed type only. | Pricing scope. |
| A-13 | Rounding is upward and shown as a line. | Pricing correctness; confirm with merchant/accounting. |
| A-14 | No available payment method → "No payment methods right now / Please ask a team member for help." | Copy. |
| A-15 | Paid only on backend `PAID`, not on the terminal result alone. | Payment integrity. |
| A-16 | Card terminal timeout is 90 s. | Tunable. |
| A-17 | Unknown payment status polls up to 60 s. | Tunable. |
| A-18 | Takeaway completion copy: "Takeaway · Pick up at the counter when your number is called." (from the design). | Copy. |
| A-19 | Print failure shows fallback text on Complete. | Copy. |
| A-20 | Receipt and kitchen ticket contents as listed in FR-021. | Legal content (OQ-11). |
| A-21 | Bluetooth lists only already-paired printers (pairing in system settings). | UX. |
| A-22 | Settings auto-exit after 120 s of inactivity. | Minor. |
| A-23 | Empty-cart timeout on SCR-05 returns without the overlay. | Minor. |
| A-24 | Device payment toggles only restrict merchant-allowed methods. | Config semantics. |
| A-25 | Rounding and QR expiry are per-device local settings. | Could move to backend. |
| A-26 | Brightness = window brightness levels 0.3 / 0.6 / 1.0. | Could need a system setting. |
| A-27 | SSID shown only with location permission. | Minor. |
| A-28 | Offline banner copy "Ordering is paused — reconnecting…". | Copy. |
| A-29 | Syncs apply between orders. | Freshness. |
| A-30 | "Exit kiosk mode" lives in Device & network settings (not designed). | Design gap. |
| A-31 | Red dot on the Idle staff corner when issues exist. | Designed (`scenario="issues"`); may be unwanted. |
| A-32 | Skeleton cards while merchants load. | Designed. |
| A-33 | Empty category copy ("Nothing here right now"). | Designed. |
| A-34 | "Creating your payment…" interim state. | Designed. |
| A-35 | Non-16:9 screens letterbox. | Visual. |
| A-36 | Certificate pinning is acceptable to the backend/infra team. | Operational. |
| A-37 | 200 MB image cache. | Storage. |
| A-38 | Accent color comes from merchant config. | Branding. |
| A-39 | No foreground service is needed for printing. | Platform. |
| A-40 | The Budget Plan tag row on Your order ("Budget Plan · 2 adults · 1 child · Rp 150.000") shows while the cart still matches the plan, and is cleared as soon as a line is edited. | Cosmetic. |
| A-41 | The kid-menu fallback note on a plan reads "Kids' menu not available" (the design has no UI for it). | Copy. |

## 16. Open questions

| ID | Question | Blocks | Needed by |
|---|---|---|---|
| OQ-01 | Exact iMin model, Android version, SN API, printer SDK version? | FR-002, FR-021/022 | 0.2.0 |
| OQ-02 | Gateway API specification and sandbox access? | FR-017/018 (real) | 0.5.0 spec / 0.9.0 sandbox |
| OQ-03 | EDC terminal model, transport (USB/LAN/cloud) and protocol; does the result reach the backend directly or via the kiosk? | FR-018 | 0.5.0 |
| OQ-04 | Backend team, timeline and staging URL? | DEP-1 | 0.5.0 |
| OQ-05 | **Closed (1.1):** Upsell screen design — delivered as `Upsell.dc.html`. | — | — |
| OQ-06 | **Closed (1.1):** "Still there?" overlay — delivered as `StillThere.dc.html`. | — | — |
| OQ-07 | How do staff change or rebind the merchant after setup (no design path from Settings)? | FR-004 | 0.7.0 |
| OQ-08 | Should sold-out items show as disabled ("Sold out") instead of hidden? | FR-009 | 0.3.0 |
| OQ-09 | What does the order-number letter mean (kiosk, order type, day)? | Display only | 0.5.0 |
| OQ-10 | Which MDM and device-owner provisioning method (QR provisioning, `dpm` via adb, iMin tool)? | FR-029, release | 0.7.0 |
| OQ-11 | Legal receipt requirements (NPWP, tax invoice wording) for Indonesia. | FR-021 | 0.6.0 |
| OQ-12 | Should brightness control system brightness? | FR-026 | 0.7.0 |
| OQ-13 | **Closed (1.1):** Change-PIN UI — delivered as `ChangePin.dc.html` (SCR-20). | — | — |
| OQ-14 | **Closed (1.1):** Budget Plan screen (SCR-19) and Menu entry row — delivered as `BudgetPas.dc.html` and the "Plan by budget" row. | — | — |

## 17. Requirement traceability

Development tasks: `dev-plan.md`. Test cases: `testing-plan.md`. The full matrix is in testing-plan §8.

| Req | Version introduced | DEV tasks | Test cases |
|---|---|---|---|
| FR-001 | 0.2.0 | DEV-010, DEV-011 | TC-001–TC-004 |
| FR-002 | 0.2.0 | DEV-012 | TC-005–TC-007 |
| FR-003 | 0.2.0 | DEV-013, DEV-014 | TC-008–TC-013 |
| FR-004 | 0.2.0 | DEV-015, DEV-016 | TC-014–TC-018 |
| FR-005 | 0.2.0 | DEV-017 | TC-019 |
| FR-006 | 0.3.0 | DEV-024 | TC-020–TC-024 |
| FR-007 | 0.3.0 | DEV-025 | TC-025–TC-028 |
| FR-008 | Post-1.0 (deferred, D-29) | DEV-026, DEV-041, DEV-080 (language parts deferred) | TC-029–TC-032 (deferred) |
| FR-009 | 0.3.0 | DEV-027, DEV-028 | TC-033–TC-039 |
| FR-010 | 0.3.0 | DEV-028, DEV-029 | TC-040–TC-045 |
| FR-011 | 0.3.0 | DEV-030 | TC-046–TC-049 |
| FR-012 | 0.4.0 | DEV-036, DEV-037, DEV-041 | TC-050–TC-054 |
| FR-013 | 0.4.0 | DEV-035, DEV-038, DEV-046, DEV-097 | TC-055–TC-061 (+TC-071) |
| FR-014 | 0.4.0 | DEV-039 | TC-062–TC-064 |
| FR-015 | 0.4.0 | DEV-038 | TC-065–TC-067 |
| FR-016 | 0.5.0 | DEV-045, DEV-046, DEV-052 | TC-068–TC-073 |
| FR-017 | 0.5.0, 0.9.0 | DEV-047, DEV-048, DEV-090 | TC-074–TC-079 |
| FR-018 | 0.5.0, 0.9.0 | DEV-049, DEV-091 | TC-080–TC-083, TC-181 |
| FR-019 | 0.5.0 | DEV-050 | TC-084–TC-087 |
| FR-020 | 0.6.0 | DEV-059, DEV-060 | TC-088–TC-090 |
| FR-021 | 0.6.0 | DEV-057, DEV-058, DEV-059 | TC-091–TC-095 |
| FR-022 | 0.6.0 | DEV-055, DEV-056, DEV-061 | TC-096–TC-100 |
| FR-023 | 0.4.0 | DEV-040, DEV-041 | TC-101–TC-104 |
| FR-024 | 0.7.0 | DEV-065, DEV-066, DEV-067 | TC-105–TC-109 |
| FR-025 | 0.7.0 | DEV-068 | TC-110–TC-112 |
| FR-026 | 0.7.0 | DEV-003, DEV-069, DEV-085 | TC-113–TC-116 |
| FR-027 | 0.7.0 | DEV-070 | TC-117–TC-120 |
| FR-028 | 0.3.0, 0.5.0 | DEV-021, DEV-022, DEV-023, DEV-051 | TC-121–TC-125 |
| FR-029 | 0.7.0 | DEV-071, DEV-101 | TC-126–TC-128 |
| FR-030 | 0.7.0 | DEV-072 | TC-129 |
| FR-031 | 0.7.0 | DEV-067, DEV-073 | TC-130–TC-131 |
| FR-032 | 0.4.0 | DEV-042, DEV-043, DEV-044 | TC-161–TC-168 |
| NFR-001 | 0.1.0 | DEV-001 | TC-140 |
| NFR-002 | 0.1.0 | DEV-003, DEV-084 | TC-141 |
| NFR-003 | 0.3.0, 0.8.0 | DEV-031, DEV-082 | TC-142–TC-144 |
| NFR-004 | 0.9.0 | DEV-093 | TC-145 |
| NFR-005 | 0.2.0, 0.9.0 | DEV-007, DEV-014, DEV-094 | TC-146–TC-148 |
| NFR-006 | 0.7.0, 0.9.0 | DEV-073, DEV-094 | TC-149 |
| NFR-007 | 0.8.0 | DEV-004, DEV-081 | TC-150–TC-151 |
| NFR-008 | 0.8.0 | DEV-080 | TC-152 |
| NFR-009 | 0.8.0 | DEV-004, DEV-083, DEV-084 | TC-153 |
| NFR-010 | 0.2.0, 0.5.0 | DEV-007, DEV-051 | TC-154 |
| NFR-011 | 0.1.0 (ongoing) | DEV-001, DEV-002, DEV-008 | TC-155 |
| NFR-012 | 0.1.0 (ongoing) | DEV-005, DEV-009 | TC-156 |
| NFR-013 | 0.3.0 | DEV-023 | TC-157 |
| NFR-014 | 0.9.0, 1.0.0 | DEV-001, DEV-095, DEV-100 | TC-158 |
| NFR-015 | 0.9.0 | DEV-093 | TC-145 |
| NFR-016 | 0.6.0, 0.9.0 | DEV-056, DEV-092 | TC-159 |
| NFR-017 | 0.5.0 | DEV-047 | TC-160 |
