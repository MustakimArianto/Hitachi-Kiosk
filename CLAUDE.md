# Hitachi Kiosk — agent guide

Android self-order kiosk app (Kotlin, Jetpack Compose, `minSdk 30`, portrait 1080 × 1920) for iMin kiosks. Customers order and pay by QRIS or card (EDC). Staff configure printers, payment and kiosk behavior. It runs in lock-task mode as device owner. Built by one developer with AI agents, milestone by milestone (`0.1.0` → `1.0.0`).

## Sources of truth (higher wins on conflict)
1. [`docs/product-requirement.md`](docs/product-requirement.md): requirements (FR-/NFR-/BR-), decisions (D-), assumptions (A-), open questions (OQ-)
2. [`docs/api/api-spec.md`](docs/api/api-spec.md): backend contract E1–E14. `openapi.yaml` is generated from it.
3. [`docs/technical-requirement.md`](docs/technical-requirement.md): measurable technical requirements (TR-) derived from the PRD
4. [`docs/architecture.md`](docs/architecture.md) and [`docs/adr/`](docs/adr/): how the app is structured; [`docs/technical-design.md`](docs/technical-design.md): how it is implemented (TD- decisions). Architecture and ADRs win over the technical design.
5. [`docs/technology-stack.md`](docs/technology-stack.md): libraries, tools and pinned versions (until `gradle/libs.versions.toml` exists, then both are kept in sync)
6. [`docs/conventions.md`](docs/conventions.md): naming, patterns, git, definition of done
7. [`docs/dev-plan.md`](docs/dev-plan.md) (DEV- tasks) and [`docs/testing-plan.md`](docs/testing-plan.md) (TC- cases)
8. [`docs/ui-design/*.dc.html`](docs/ui-design/): the final "Linen & Paprika" design, the visual source of truth for layout, copy and motion (D-02). `S_<Screen>_<scenario>.dc.html` are the state boards (errors, offline, empty, blocked) and are the visual spec for every non-default state. Start with [`docs/ui-design/README.md`](docs/ui-design/README.md).

If the docs conflict, or a requirement is ambiguous, stop and ask. Don't guess. Never invent requirements. When an open question (OQ-) blocks you, build against the interface and fake, and say so.

## Hard rules
- **Module boundaries** (architecture §3): `:core:domain` is pure Kotlin/JVM, so no `android.*`, Retrofit, Room or Hilt. Feature packages in `:app` never import each other. DTOs and Room entities never leave `:core:data`.
- **Business logic lives in `:core:domain`** (pricing, cart, Budget Plan, order/payment state machines), not in ViewModels or composables.
- **Every external system goes behind a domain interface with a scriptable fake** in `:core:testing` (NFR-012).
- **Money is integer IDR (`Long`)**, never floating point. Business timestamps come from the server, and UI timers use the injected `Clock`.
- **Paid means backend `PAID`**, never a terminal result alone (A-15). Never show "not charged" for an unknown status (FR-019).
- **Debug-only tools live in `src/debug/`** and must not be referenced from release.
- **No analytics, crash-reporting or ad SDKs** (D-23). No PIN, full payment references or customer data in logs.
- **No hardcoded UI strings**: every string goes in `values/strings.xml`. The 1.0 UI is English only (D-29), so there is no `values-in/` and no language switch. Layout uses `dpx`/`spx`, not raw `dp`/`sp`. Colors and motion come from the design system tokens, and animations respect `LocalReduceMotion`.
- **API changes**: edit `docs/api/api-spec.md` first, then regenerate the yaml (command below). Never hand-edit `openapi.yaml`.

## Workflow per DEV task
1. Read the DEV task in `dev-plan.md`, its FRs in `product-requirement.md` and its TCs in `testing-plan.md`.
2. Write the tests first for domain/data logic, then implement.
3. Run the checks below until they're green. There is no CI in 1.0 (D-31): `./gradlew verify` is the gate before every merge to `main`.
4. Meet the definition of done in `conventions.md` §11, including ticking the DEV checkbox. Record any decision as an ADR (`docs/adr/0000-template.md`).
5. Commit with Conventional Commits, scope = module/feature, and cite the ID: `feat(cart): merge identical lines (DEV-036)`.

Coverage targets: `:core:domain` ≥ 90 %, `:core:data` ≥ 70 %.

## Commands
```bash
# API contract
python3 docs/api/tools/gen_openapi.py docs/api/api-spec.md docs/api/openapi.yaml
npx @stoplight/prism-cli@5.16.0 mock docs/api/openapi.yaml --port 4010

python3 docs/api/tools/extract_fixtures.py docs/api/api-spec.md core/testing/src/main/resources/fixtures   # or ./gradlew extractApiFixtures

# Android (update this list if task names change)
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint detekt ktlintCheck
./gradlew verifyRoborazziDebug        # screenshot tests (record new goldens with recordRoborazziDebug)
./gradlew connectedDebugAndroidTest   # instrumented, needs an emulator (scripts/create-avds.sh makes Kiosk-30/34)
./gradlew koverHtmlReport
./gradlew verify                      # the full local gate (lint, tests, coverage, screenshots, release APK check, OpenAPI check)
```

## Repo state
As of 2026-10-25 milestones 0.1.0 (Foundation) to 0.6.0 (Complete, Upsell strip and Budget Plan) are implemented: the multi-module project, `build-logic` and the `verify` gate, the design system, domain models and ports for E1–E14 with fakes, networking, debug Developer options, the setup screens, the cached and synced catalog, the ordering screens (Menu, Item, Combo, Your order with its "Goes well with your order" suggestion strip, Budget Plan with its swap sheet and "Edit plan", inactivity overlay), order submission (`SubmitOrder`, idempotent E7, price-change handling), the `PaymentStateMachine` (QRIS, card via the `CardTerminal` fake, every failure), Checkout, the ZXing QR card, the E14 connectivity engine, pending-order recovery after a process death (Room version 3: `modifier_group.purpose` marks "Make it a meal", api-spec E6) and the Complete screen, which takes over the payment hand-off from Checkout (the pending order is forgotten only once Complete is shown) and reads a recovered paid order through `RecoveredPaidSource`. In debug only: the Checkout simulate bar, a switchable card terminal and 27 demo images. **Nothing prints**: the PO removed printing and settings work from 0.6.0 (D-36), so DEV-055/057/058/059/061 are removed and not scheduled, the receipt printer on Complete is a demo animation, and OQ-19 asks whether to build printing later or drop it from 1.0. Release has no EDC adapter (`UnavailableCardTerminal` fails every sale until 0.9.0). Gateway/EDC specs have not arrived, so ADR-0003 is not written and risk R-1 awaits the PO. DEV-012's on-device serial-number spike is pending ([ADR-0002](docs/adr/0002-device-serial.md) is Proposed). The 0.6.0 instrumented tests were edited but not run (no emulator), and DEV-113 (PO review) and the DEV-104 board sign-off are open. Open checks from 0.2.0–0.6.0 are in `todo.md`: the 0.2.0 and 0.3.0 ones run after the 1.0.0 implementation, and the 0.4.0–0.6.0 ones (PO reviews, R-1, OQ-19, the manual runs) wait for the PO. Status and open items are in `docs/dev-plan.md` (the per-milestone status notes) and `docs/test-reports/`. `README.md` explains how to build and run.

Build notes: run Gradle with a JDK ≥ 17 (`JAVA_HOME`); bytecode is Java 17. JVM screenshot tests run on Robolectric SDK 36 (`src/test/resources/robolectric.properties`); `:app` unit tests run on a plain `Application`, not the Hilt `KioskApp`. Debug-only code lives in package `com.hitachi.kiosk.debug` and is reached from `src/main` only through the optional `DeveloperMenu` / `BindingOverrides` / `CheckoutDebugTools` bindings.
