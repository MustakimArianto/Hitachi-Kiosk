# Hitachi Kiosk — agent guide

Android self-order kiosk app (Kotlin, Jetpack Compose, `minSdk 30`, portrait 1080 × 1920) for iMin kiosks. Customers order and pay by QRIS or card (EDC). Staff configure printers, payment and kiosk behavior. It runs in lock-task mode as device owner. Built by one developer with AI agents, milestone by milestone (`0.1.0` → `1.0.0`).

## Sources of truth (higher wins on conflict)
1. [`docs/prd.md`](docs/prd.md): requirements (FR-/NFR-/BR-), decisions (D-), assumptions (A-), open questions (OQ-)
2. [`docs/api/api-spec.md`](docs/api/api-spec.md): backend contract E1–E14. `openapi.yaml` is generated from it.
3. [`docs/architecture.md`](docs/architecture.md) and [`docs/adr/`](docs/adr/): how the app is structured
4. [`docs/conventions.md`](docs/conventions.md): naming, patterns, git, definition of done
5. [`docs/dev-plan.md`](docs/dev-plan.md) (DEV- tasks) and [`docs/testing-plan.md`](docs/testing-plan.md) (TC- cases)
6. [`docs/ui-design/*.dc.html`](docs/ui-design/): the final "Linen & Paprika" design, the visual source of truth for layout, copy and motion (D-02). `S_<Screen>_<scenario>.dc.html` are the state boards (errors, offline, empty, blocked) and are the visual spec for every non-default state. Start with [`docs/ui-design/README.md`](docs/ui-design/README.md).

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
1. Read the DEV task in `dev-plan.md`, its FRs in `prd.md` and its TCs in `testing-plan.md`.
2. Write the tests first for domain/data logic, then implement.
3. Run the checks below until they're green.
4. Meet the definition of done in `conventions.md` §11, including ticking the DEV checkbox. Record any decision as an ADR (`docs/adr/0000-template.md`).
5. Commit with Conventional Commits, scope = module/feature, and cite the ID: `feat(cart): merge identical lines (DEV-036)`.

Coverage targets: `:core:domain` ≥ 90 %, `:core:data` ≥ 70 %.

## Commands
```bash
# API contract
python3 docs/api/tools/gen_openapi.py docs/api/api-spec.md docs/api/openapi.yaml
npx @stoplight/prism-cli mock docs/api/openapi.yaml --port 4010

# Android (available once DEV-001 lands; update this list if task names change)
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint detekt ktlintCheck
./gradlew verifyRoborazziDebug        # screenshot tests
./gradlew connectedDebugAndroidTest   # instrumented, needs an emulator
./gradlew koverHtmlReport
```

## Repo state
As of 2026-10-22 the repo has docs only. The Android project is created by DEV-001/002 (milestone 0.1.0).
