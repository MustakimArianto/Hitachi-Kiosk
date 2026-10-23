# Hitachi Kiosk

Android self-order kiosk app for iMin kiosks (Kotlin, Jetpack Compose, `minSdk 30`, portrait 1080 × 1920). Customers order and pay by QRIS or card (EDC); staff configure printers, payment and kiosk behaviour. It runs in lock-task mode as device owner.

**Status: `0.2.0` Device activation & setup.** On top of the 0.1.0 foundation, the kiosk now goes from first launch to "bound to a merchant": the animated Splash with startup routing, Sign in (activation by serial number, token in encrypted storage) and Choose a restaurant (bind, Log out). Idle and the screens after it are still labelled placeholders until their milestones ([`docs/dev-plan.md`](docs/dev-plan.md)). The serial-number source still needs its on-device spike ([ADR-0002](docs/adr/0002-device-serial.md)).

| Start here | |
|---|---|
| Agent and contributor guide | [`CLAUDE.md`](CLAUDE.md) |
| What the product does | [`docs/product-requirement.md`](docs/product-requirement.md) |
| How it is built | [`docs/architecture.md`](docs/architecture.md), [`docs/technical-design.md`](docs/technical-design.md), [ADR-0001](docs/adr/0001-architecture.md) |
| Libraries and versions | [`docs/technology-stack.md`](docs/technology-stack.md), [`gradle/libs.versions.toml`](gradle/libs.versions.toml) |
| Backend contract | [`docs/api/api-spec.md`](docs/api/api-spec.md) → generated [`docs/api/openapi.yaml`](docs/api/openapi.yaml) |
| Plan and tests | [`docs/dev-plan.md`](docs/dev-plan.md), [`docs/testing-plan.md`](docs/testing-plan.md) |
| Design | [`docs/ui-design/README.md`](docs/ui-design/README.md) |

## Prerequisites

- JDK 17 or newer to run Gradle (JDK 21 recommended; all bytecode targets Java 17). Set `JAVA_HOME` or use the JDK bundled with Android Studio.
- Android SDK with platform **API 37** and build-tools **36.0.0** (`ANDROID_HOME`, or `sdk.dir` in `local.properties`).
- Python 3.9+ (standard library only) for the API contract tools.
- Node.js 22+ for `npx` (Redocly lint, Prism mock server). Used by `./gradlew verify` and for the mock server only.

Everything else comes through the committed Gradle wrapper (Gradle 9.8.2).

## Build and run

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. A release build (`./gradlew assembleRelease`) is unsigned locally unless `KIOSK_KEYSTORE_PATH`, `KIOSK_KEYSTORE_PASSWORD`, `KIOSK_KEY_ALIAS` and `KIOSK_KEY_PASSWORD` are set (or the matching `kiosk.keystore.*` keys in `local.properties`). Signing material is never committed.

**Backend environment.** `-Pkiosk.env=dev|staging|prod` selects the backend URL. By default debug builds use `dev` and release builds use `prod`; a release build with `dev` fails. Certificate pins come from `-Pkiosk.pins.<env>=pin1,pin2` or `KIOSK_PINS_<ENV>` and are never committed (technical-design §2.3).

## Modules

| Module | Holds |
|---|---|
| `:app` | `MainActivity`, navigation graph, `StartupRouter`, the feature screens (`splash`, `activation`, `merchant`) and the remaining placeholders, Hilt wiring; `src/debug`: Developer options |
| `:core:designsystem` | "Linen & Paprika" tokens, `DesignUnit` (`Int.dpx` / `Int.spx`), `DesignFrame`, Schibsted Grotesk, motion specs, `LocalReduceMotion` |
| `:core:domain` | Pure Kotlin/JVM: models for E1–E14, `Outcome` / `DomainError`, `retryWithBackoff`, ports (`BackendApi`, `PaymentGateway`, `CardTerminal`, `PrinterDriver`, `DeviceIdentityProvider`, `ConnectivityMonitor`, `Clock`, `IdGenerator`), `KioskLog` |
| `:core:data` | Retrofit `KioskApi`, DTOs and mappers, interceptors, error mapping, `DefaultBackendApi`; the device state (`DeviceSession`, encrypted `secure.pb`, `device.preferences_pb`) and the activation, merchant and catalog repositories |
| `:core:hardware` | Device identity (`IminDeviceIdentityProvider` + serial cache); printers and card terminal from 0.5.0/0.6.0 |
| `:core:testing` | Scriptable fakes for every port, `SampleData`, API fixtures, test rules. Test and debug builds only. |
| `build-logic` | Convention plugins `kiosk.*` and the `verify` gate |

Rules (enforced by build and review): `:core:domain` has no Android, Retrofit, Room or Hilt; feature packages never import each other; DTOs stay in `:core:data`. See [architecture §3](docs/architecture.md#3-modules).

## Fakes, mock server and Developer options

Every external system sits behind a domain interface with a scriptable fake in `:core:testing` (NFR-012). In a **debug** build, tap the brand mark on the Splash screen **5 times** to open Developer options:

- **Backend**: *In-app fakes* (default, no network), *Mock server* (Prism on the host at `http://10.0.2.2:4010/`), or *Real backend* (the build's environment).
- **Scenario**: the mock scenarios of [api-spec §4](docs/api/api-spec.md#4-mock-data-checklist) (`activate_unregistered`, `order_price_changed`, `card_declined`, …). Fakes apply all of them; the mock server applies those Prism can express through a `Prefer` header.
- **Serial number source**: *Fake* (default, `IMN24K0003118`, so emulators can sign in), *Fake: unavailable*, *Fake: slow (5 s)*, or *Real device*.
- **Serial number diagnostics**: what every serial-number source returns on this device, the cached value, device-owner and permission state, and the window size and orientation, with Read again, Clear serial cache, Copy and Share. This is the DEV-012 spike; the procedure is in [ADR-0002](docs/adr/0002-device-serial.md). Each row is also logged: `adb logcat -d | grep serial_spike`.
- **Build** information: version, build type, environment and backend URL.

Release builds contain none of this (`scripts/check-release-apk.sh`).

## First run (journey J5)

With the debug build on the in-app fakes (the default):

1. Launch the app. The Splash animation runs for 3.9 s, then routes by the stored state: Sign in on a fresh install.
2. **Sign in** reads the serial number, then *Log in* activates the kiosk (E1). The device token is stored encrypted (Tink + Android Keystore); nothing else is entered.
3. **Choose a restaurant** lists the merchants (E2). Tap *Urban Grill*: the kiosk binds it (E3), downloads its config and menu (E5, E6) and opens Idle (still a placeholder in 0.2.0).
4. Restart the app: a bound kiosk goes straight to Idle. *Log out* on Choose a restaurant forgets the activation and returns to Sign in.

Other first-run states come from the Developer options scenario: `activate_unregistered` (journey J6: "This kiosk isn't registered yet" with the serial number and *Try again*), `activate_disabled`, `slow_network`, `merchants_empty`, `merchant_closed` and `menu_download_failed`. The serial-number source setting covers "Can't read this device's serial number".

Start the mock server from the repository root:

```bash
npx @stoplight/prism-cli@5.16.0 mock docs/api/openapi.yaml --port 4010
```

## API contract

`docs/api/api-spec.md` is the source of truth. Edit it first, then regenerate the OpenAPI file and the test fixtures:

```bash
python3 docs/api/tools/gen_openapi.py docs/api/api-spec.md docs/api/openapi.yaml
```

```bash
./gradlew extractApiFixtures
```

Never edit `openapi.yaml` or `core/testing/src/main/resources/fixtures/*.json` by hand; `verify` fails when either differs from the spec.

**Contract owner (DEP-1):** the PO builds the mock and the backend from `api-spec.md` (since 2026-10-23). Contract changes go through `api-spec.md` first and are agreed with the PO. Until that mock is up, the app runs on its in-app fakes or on Prism.

## Verification gate

There is no CI in 1.0 (D-31). Before every merge to `main`:

```bash
./gradlew verify
```

It runs Android Lint, detekt, ktlint, the unit and Roborazzi screenshot tests, Kover coverage (`:core:domain` ≥ 90 %, `:core:data` ≥ 70 %), `buildHealth`, the release APK check and the API contract checks (technical-design §2.6). Useful pieces:

```bash
./gradlew ktlintFormat
```

```bash
./gradlew recordRoborazziDebug
```

```bash
./gradlew koverHtmlReport
```

Optional pre-push hook that runs `verify`:

```bash
git config core.hooksPath scripts/git-hooks
```

### Instrumented tests

```bash
./gradlew connectedDebugAndroidTest
```

They need a running emulator. The testing plan uses **Kiosk-30** (API 30, 1080 × 1920, 320 dpi) and **Kiosk-34** (API 34, 1080 × 1920, 240 dpi); create them with `scripts/create-avds.sh` (needs the SDK command-line tools). Any API 30+ emulator runs the suite.

## Conventions

Naming, UI state pattern, coroutines, dependencies and the definition of done are in [`docs/conventions.md`](docs/conventions.md). Commits follow Conventional Commits with the DEV/FR/TC ID, for example `feat(cart): merge identical lines (DEV-036)`.

## Licenses

Third-party assets bundled in the app are listed in [`licenses/`](licenses/) (Schibsted Grotesk, SIL Open Font License 1.1).
