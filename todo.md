# To do after 1.0.0

Open items from milestones 0.2.0–0.5.0 that were not done when the milestone was implemented. For the 0.2.0 and 0.3.0 items the PO decided on 2026-10-24 to run them **after the 1.0.0 implementation**; the 0.4.0 and 0.5.0 items in the first two sections still wait for that decision. Each item names the task or test case it belongs to, so its result goes back to that place in the docs.

## Needs the PO (0.4.0 exit criteria, not yet deferred)

These belong to the 0.4.0 exit criteria and manual testing requirements ([dev-plan 0.4.0](docs/dev-plan.md)). They need the PO, so they were not done when 0.4.0 was implemented. The PO decides whether they run now or join the after-1.0.0 list.

- [ ] **Review of the built Upsell and Budget Plan screens** against `Upsell.dc.html` and `BudgetPas.dc.html` (exit criterion). *2026-10-25: the Upsell screen is replaced by the Your order strip in 0.6.0 [D-33], so review the Budget Plan screens and the strip together at DEV-113 and drop the Upsell screen part.* The goldens `Upsell*.png`, `S_Upsell_noSuggestions.png`, `BudgetPas_*.png` and `S_BudgetPas_*.png` in `app/src/test/screenshots/` are the starting point; the deviations are listed in `docs/test-reports/0.4.0.md`.
- [ ] **Pricing spot check**: 20 carts from the design samples in a spreadsheet against the app (manual testing requirement; `docs/pricing-cases.md` is the automated part).
- [ ] **Plan sanity review**: 10 budget and group combinations on SCR-19 (manual testing requirement; `docs/budget-plan-cases.md` is the automated part).

## Needs the PO (0.5.0 exit criteria, not yet deferred)

These belong to the 0.5.0 exit criteria and risks ([dev-plan 0.5.0](docs/dev-plan.md)). They need the PO or the gateway and EDC teams, so they were not done when 0.5.0 was implemented.

- [ ] **Acknowledge risk R-1** (exit criterion, DEV-053). The gateway and EDC specs (DEP-2, DEP-3, OQ-02, OQ-03) had not arrived at the 0.5.0 exit, so ADR-0003 is not written and 0.9.0 is at risk. The PO acknowledges that, or decides to ship card in 1.0.1 (PRD R-1).
- [ ] **Chase the gateway and EDC specs** (DEP-2, DEP-3). ADR-0003 (`docs/adr/0003-payment-integration.md`) is the first step of DEV-091; until then release has `UnavailableCardTerminal` and the fake.
- [ ] **Review of the built Checkout** against `Checkout.dc.html` and the `S_Checkout_*` boards. The goldens `Checkout*.png`, `S_Checkout_*.png` and `QrCard*.png` in `app/src/test/screenshots/` are the starting point; the deviations are in `docs/test-reports/0.5.0.md` and `docs/ui-design/README.md`. Confirm the copy the design has no board for (A-45): the card "Payment cancelled" text and the "Your order is saved" note.
- [ ] **Confirm the recovery decision** (D-32). After a process death a payment that is still `PENDING` is cancelled and not resumed, because the QR string is not stored; `PROCESSING` is polled for up to 60 s first. The lead chose this when the PO said "you decide what is best". Tell the PO, and record any change in architecture §5.3.

## Manual runs of 0.5.0 (not run)

- [ ] **TC-073 on a device** (FR-016 AC4, TR-REL-02). Pay by QRIS with the mock scenario `qris_paid_after_5s`, run `adb shell am kill` at 2 s, relaunch, and check that the app reaches Complete with the right order number. Run it once with "Don't keep activities" on (testing-plan §6.4). Also kill during a `PENDING` payment and check that it ends on Idle with the order cancelled.
- [ ] **Airplane-mode runs** (TC-085, TC-122, TC-123, TC-137, testing-plan §6.5): toggle airplane mode during each Checkout state and on Your order. Check "Checking your payment…" without "not charged", the banner within 5 s and Pay back within 10 s.
- [ ] **J1–J3 recordings with the fakes** (testing-plan §7, 0.5.0 evidence): happy path, card declined then QRIS, QR expiry then retry. Add them to `docs/test-reports/0.5.0.md`.
- [ ] **FR-013 AC4 against the Prism mock** (`npx @stoplight/prism-cli@5.16.0 mock docs/api/openapi.yaml --port 4010`, Developer options → Mock server). Pay with the design sample cart (total Rp 172.700, the mock's canned total) and check that Checkout opens; any other cart must end as "Payment didn't go through", because the kiosk refuses an order whose total differs from the one it sent.
- [ ] **FR-019 AC3 on a release build on the device** (the string absence is checked by `scripts/check-release-apk.sh` in `verify`): install the release APK and check that Checkout has no simulate bar.

## Needs the iMin device

- [ ] **DEV-012 serial-number spike** (FR-002, TC-005, TC-006). Run the procedure in [ADR-0002](docs/adr/0002-device-serial.md) on the iMin kiosk with the debug build (Developer options → Serial number diagnostics). Fill the ADR's results table, decide the source order and whether `READ_PHONE_STATE` is requested from the PO (A-02, R-3), then set the ADR to Accepted. Also record the window orientation and size the spike screen reports (technical-design §16).
- [ ] **TC-125: offline cold start with cached images** (FR-028 AC4, NFR-013). Use a menu whose products, combos and idle promos have real image URLs. Sync once, turn the network off, restart the app, and check that Idle and the Menu show the cached images.
- [ ] **TC-146 on the device** (NFR-005). Repeat the 0.2.0 emulator check with `run-as` on the iMin unit: `secure.pb` holds no plain token.

## Needs more emulators or test data

- [ ] **Instrumented suite on API 30 and API 34/35** (NFR-001, NFR-016, testing-plan §2.3). Create Kiosk-30 and Kiosk-34 with `scripts/create-avds.sh` and run `./gradlew connectedDebugAndroidTest` on each. Only API 37 has run so far, including the 0.5.0 `CheckoutUiTest`. On API 30 this also checks the inactivity overlay's scrim fallback (no blur below API 31, DEV-040).
- [ ] **TC-157, filling the image cache** (NFR-013, A-37). The 200 MB cap and the placeholder on a failed image are covered. Still to do: load enough images to reach the cap and check that the cache never grows past 200 MB.

## For 0.6.0 (the Complete screen)

- [ ] **Take over the payment hand-off.** The 0.5.0 Checkout calls `PaymentStateMachine.acknowledgePaid()` as it leaves for the Complete placeholder. The 0.6.0 Complete screen should take the paid details first and acknowledge itself, and it should take an order that a recovery found paid from `PendingOrderRecoveryCoordinator.recoveredPaid` (`consumeRecoveredPaid()`; totals and payment details, no order lines). Consider clearing the pending order only after Complete is shown, so a kill in between still leaves a receipt (a known limit, technical-design §8.6).

## For 0.6.0 (the rework of 2026-10-25: printing with fakes, Upsell strip, Budget Plan UX)

- [ ] **Sync the updated design files into `docs/ui-design/`** and add the new boards (DEV-104). The files in the repo were last changed on 2026-10-22, and the published canvas (link in `docs/ui-design/README.md`) is in another organization that the dev session could not open. Needed boards: the Your order strip and its states, Budget Plan Step 1 and Step 2 changes, the swap picker, the cart-limit message, the Menu header pill, and the printer boards. The PO signs them off before DEV-106–DEV-112 start.
- [ ] **Decide OQ-15** (Budget Plan extras cap: the combo side rule and the design's cap of 4) and **OQ-16** (no preselected budget chip). Recommended answers are in the PRD.
- [ ] **Decide OQ-17** with the backend: an optional `purpose` on modifier groups that marks "Make it a meal". Without it, DEV-114 and the "Make it a meal" strip card do not ship, and the gap check only recognises combo lines. **OQ-18** (an optional `source` on E7 order lines to measure attach rate without analytics) can wait until after 1.0.0.
- [ ] **Confirm A-47**: the receipt for an order that a recovery found paid has no order lines (0.5.0 known limit). The alternative is storing the lines, which changes the 0.5.0 recovery rules.
- [ ] **Printing on real printers moved to 0.9.0 (D-34, DEV-056):** the iMin, USB and Bluetooth drivers, ADR-0004 (Proposed until then), TC-096–TC-098 and the real runs of TC-091, TC-093, TC-095 and TC-159, the photographed output, and `docs/printers.md`. They need DEP-4 and DEP-7.
- [ ] **P2 tasks of 0.6.0 that did not ship** go to 1.0.1 (the swap picker DEV-109, "Edit plan" DEV-111, the "Make it a meal" card DEV-114, the promo caption). List them here when 0.6.0 closes.

## Evidence

- [ ] **J5 and J6 screen recordings** (testing-plan §7, 0.2.0 evidence). Record first-time setup and the unregistered-device journey on the device and add them to `docs/test-reports/0.2.0.md`.

## When an item is done

Tick it here, record the result in the matching test report (`docs/test-reports/0.2.0.md`, `0.3.0.md`, `0.4.0.md` or `0.5.0.md`) and, for DEV-012, in ADR-0002 and the dev-plan 0.2.0 status. Delete this file when every item is ticked.
