# To do after 1.0.0

Open items from milestones 0.2.0–0.4.0 that were not done when the milestone was implemented. For the 0.2.0 and 0.3.0 items the PO decided on 2026-10-24 to run them **after the 1.0.0 implementation**; the 0.4.0 items in the first section still wait for that decision. Each item names the task or test case it belongs to, so its result goes back to that place in the docs.

## Needs the PO (0.4.0 exit criteria, not yet deferred)

These belong to the 0.4.0 exit criteria and manual testing requirements ([dev-plan 0.4.0](docs/dev-plan.md)). They need the PO, so they were not done when 0.4.0 was implemented. The PO decides whether they run now or join the after-1.0.0 list.

- [ ] **Review of the built Upsell and Budget Plan screens** against `Upsell.dc.html` and `BudgetPas.dc.html` (exit criterion). The goldens `Upsell*.png`, `S_Upsell_noSuggestions.png`, `BudgetPas_*.png` and `S_BudgetPas_*.png` in `app/src/test/screenshots/` are the starting point; the deviations are listed in `docs/test-reports/0.4.0.md`.
- [ ] **Pricing spot check**: 20 carts from the design samples in a spreadsheet against the app (manual testing requirement; `docs/pricing-cases.md` is the automated part).
- [ ] **Plan sanity review**: 10 budget and group combinations on SCR-19 (manual testing requirement; `docs/budget-plan-cases.md` is the automated part).

## Needs the iMin device

- [ ] **DEV-012 serial-number spike** (FR-002, TC-005, TC-006). Run the procedure in [ADR-0002](docs/adr/0002-device-serial.md) on the iMin kiosk with the debug build (Developer options → Serial number diagnostics). Fill the ADR's results table, decide the source order and whether `READ_PHONE_STATE` is requested from the PO (A-02, R-3), then set the ADR to Accepted. Also record the window orientation and size the spike screen reports (technical-design §16).
- [ ] **TC-125: offline cold start with cached images** (FR-028 AC4, NFR-013). Use a menu whose products, combos and idle promos have real image URLs. Sync once, turn the network off, restart the app, and check that Idle and the Menu show the cached images.
- [ ] **TC-146 on the device** (NFR-005). Repeat the 0.2.0 emulator check with `run-as` on the iMin unit: `secure.pb` holds no plain token.

## Needs more emulators or test data

- [ ] **Instrumented suite on API 30 and API 34/35** (NFR-001, NFR-016, testing-plan §2.3). Create Kiosk-30 and Kiosk-34 with `scripts/create-avds.sh` and run `./gradlew connectedDebugAndroidTest` on each. Only API 37 has run so far. On API 30 this also checks the inactivity overlay's scrim fallback (no blur below API 31, DEV-040).
- [ ] **TC-157, filling the image cache** (NFR-013, A-37). The 200 MB cap and the placeholder on a failed image are covered. Still to do: load enough images to reach the cap and check that the cache never grows past 200 MB.

## Evidence

- [ ] **J5 and J6 screen recordings** (testing-plan §7, 0.2.0 evidence). Record first-time setup and the unregistered-device journey on the device and add them to `docs/test-reports/0.2.0.md`.

## When an item is done

Tick it here, record the result in the matching test report (`docs/test-reports/0.2.0.md`, `0.3.0.md` or `0.4.0.md`) and, for DEV-012, in ADR-0002 and the dev-plan 0.2.0 status. Delete this file when every item is ticked.
