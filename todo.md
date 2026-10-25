# To do after 1.0.0

Open items from milestones 0.2.0–0.6.0 that were not done when the milestone was implemented. For the 0.2.0 and 0.3.0 items the PO decided on 2026-10-24 to run them **after the 1.0.0 implementation**; the 0.4.0, 0.5.0 and 0.6.0 items in the "Needs the PO" sections still wait for that decision. Each item names the task or test case it belongs to, so its result goes back to that place in the docs.

## Needs the PO (0.4.0 exit criteria, not yet deferred)

These belong to the 0.4.0 exit criteria and manual testing requirements ([dev-plan 0.4.0](docs/dev-plan.md)). They need the PO, so they were not done when 0.4.0 was implemented. The PO decides whether they run now or join the after-1.0.0 list.

- [ ] **Review of the built Upsell and Budget Plan screens** against `Upsell.dc.html` and `BudgetPas.dc.html` (exit criterion). *2026-10-25: the Upsell screen is gone (0.6.0, D-33), so this review became DEV-113 below and covers the strip and the reworked Budget Plan.* The 0.4.0 deviations are listed in `docs/test-reports/0.4.0.md`.
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

- [ ] **Instrumented suite on API 30 and API 34/35** (NFR-001, NFR-016, testing-plan §2.3). Create Kiosk-30 and Kiosk-34 with `scripts/create-avds.sh` and run `./gradlew connectedDebugAndroidTest` on each. Only API 37 has run so far, including the 0.5.0 `CheckoutUiTest`; the 0.6.0 edits to the instrumented tests have not run at all. On API 30 this also checks the inactivity overlay's scrim fallback (no blur below API 31, DEV-040).
- [ ] **TC-157, filling the image cache** (NFR-013, A-37). The 200 MB cap and the placeholder on a failed image are covered. Still to do: load enough images to reach the cap and check that the cache never grows past 200 MB.

## Needs the PO (0.6.0 exit criteria, not yet deferred)

These belong to the 0.6.0 exit criteria ([dev-plan 0.6.0](docs/dev-plan.md)). The instrumented tests of 0.6.0 were not run (no emulator), so the first item also needs one.

- [ ] **DEV-113: review the built strip and Budget Plan** against the synced boards (`S_Payment_upsell*`, `S_Payment_plan*`, `S_BudgetPas_*`, `S_Menu_drinksTab`, `S_Menu_planOff`), and do the plan sanity review of 10 budget and group combinations (it replaces the 0.4.0 item above; add the notes to `docs/test-reports/0.6.0.md`). Also look at Complete against `Complete.dc.html` and `S_Complete_takeaway`.
- [ ] **DEV-104: sign off the boards.** Design v26 is synced in `docs/ui-design/` and the app is built from it; the PO's sign-off is not recorded yet.
- [ ] **Decide OQ-19: printing.** D-36 took all printing and the Printer settings screen out of 0.6.0 and nothing was rescheduled. Build it later (DEV-055, 057, 058, 059, 061 then DEV-056; TC-091–TC-100; FR-021, FR-022, SCR-15) or drop it from 1.0 (then also settle the Receipt printer rows of Settings in FR-024 and FR-027, TC-130, DEP-4, DEP-7, R-5, and the `imin-printer` entry in the version catalog notes).
- [ ] **Confirm the new copy** (A-49): the Complete dine-in line (`complete_info_dine_in` keeps the design wording "Take your receipt…" as a demo while nothing prints; change it if OQ-19 drops printing), the neutral line for a recovered order and "Back to the start in Ns". And A-50: the Budget Plan edit-mode dock copy. And the design deviation: the built strip re-ranks while the design freezes it.
- [ ] **Confirm the meal marker with the backend team**: E6 `purpose` = `MEAL_UPGRADE` (OQ-17, resolved on our side). A menu cached before the upgrade shows no "Make it a meal" card until the next menu refresh.
- [ ] **OQ-18** (an optional `source` on E7 order lines to measure attach rate without analytics) can wait until after 1.0.0.

## Manual and device runs of 0.6.0 (not run)

- [ ] **Instrumented suite on an emulator.** `CheckoutUiTest`, `OrderingUiTest` and `NavigationEdgeWalkUiTest` were edited for the strip, the swap sheet, Edit plan and Complete, and compile only. Run `./gradlew connectedDebugAndroidTest` (Kiosk-34 or API 37) and record the result in `docs/test-reports/0.6.0.md`.
- [ ] **TC-073 and TC-205 on a device**: kill the app after Checkout shows paid and before Complete is shown, and check that the next start still reaches Complete (the pending order is now cleared only after Complete is shown); and check the "Make it a meal" card after a menu refresh on a device that had the old cache.

## For 1.0.1

- [ ] The strip's promo caption ("Adds Rp X off", P2 of DEV-105), the only P2 item of 0.6.0 that did not ship.

## Evidence

- [ ] **J5 and J6 screen recordings** (testing-plan §7, 0.2.0 evidence). Record first-time setup and the unregistered-device journey on the device and add them to `docs/test-reports/0.2.0.md`.

## When an item is done

Tick it here, record the result in the matching test report (`docs/test-reports/0.2.0.md`, `0.3.0.md`, `0.4.0.md`, `0.5.0.md` or `0.6.0.md`) and, for DEV-012, in ADR-0002 and the dev-plan 0.2.0 status. Delete this file when every item is ticked.
