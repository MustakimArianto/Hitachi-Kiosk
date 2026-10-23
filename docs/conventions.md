# Hitachi Kiosk — Coding Conventions

How code is written and how changes land. [`architecture.md`](architecture.md) covers structure. This file covers style and workflow. Lint (ktlint/detekt, Android Lint) enforces formatting, so it is not repeated here.

## 1. Packages

```
com.hitachi.kiosk                      :app (MainActivity, KioskApp, navigation/, di/, devtools/, kiosk/)
com.hitachi.kiosk.debug                :app src/debug only (Developer options, switchable wrappers)
com.hitachi.kiosk.feature.<feature>    :app feature packages (menu, cart, checkout, …)
com.hitachi.kiosk.core.designsystem    :core:designsystem (theme/, unit/, motion/, component/)
com.hitachi.kiosk.core.domain          :core:domain (model/, error/, backend/, pricing/, cart/, planner/, order/, payment/, repository/, device/, time/, id/, log/)
com.hitachi.kiosk.core.data            :core:data (api/, backend/, dto/, mapper/, db/, prefs/, repository/, sync/, di/)
com.hitachi.kiosk.core.hardware        :core:hardware (identity/, printer/, terminal/)
com.hitachi.kiosk.core.testing         :core:testing (fake/, fixture/, rule/)
```

A feature package contains only `XxxRoute.kt`, `XxxScreen.kt`, `XxxViewModel.kt`, `XxxUiState.kt` (state, events, effects) and private composables. It never imports another `feature.*` package.

## 2. Naming

| Kind | Pattern | Example |
|---|---|---|
| Screen composable (stateless) | `XxxScreen(state, onEvent)` | `MenuScreen` |
| Route composable (collects VM) | `XxxRoute` | `MenuRoute` |
| ViewModel | `XxxViewModel` | `CheckoutViewModel` |
| UI state / events / effects | `XxxUiState` (data class), `XxxEvent`, `XxxEffect` (sealed interfaces) | `CheckoutEvent.TryAgain` |
| Navigation route | `@Serializable data object/class XxxRoute`, kept in `navigation/Routes.kt` | `ItemDetailRoute(productId)` |
| Domain interface / implementation | `XxxRepository` / `DefaultXxxRepository` | `OrderRepository` |
| Device interface / implementation | role name / tech name | `CardTerminal` / `IngenicoCardTerminal` |
| Fake | `FakeXxx` in `:core:testing` | `FakeCardTerminal` |
| DTO / Room entity | `XxxDto` / `XxxEntity` | `MenuDto`, `ProductEntity` |
| Mapper | extension `XxxDto.toDomain()` / `Xxx.toEntity()` | |
| Hilt module | `XxxModule` | `NetworkModule` |
| Test | `XxxTest` (unit), `XxxUiTest` (instrumented), `XxxScreenshotTest` | |
| Test method | backticked sentence: `` `PRICE_CHANGED reprices cart and returns to review`() `` | |
| String resource | `screen_element_purpose`, snake_case | `checkout_qr_expires_in` |

## 3. UI state pattern

- One `StateFlow<XxxUiState>` per ViewModel, built with `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` or a private `MutableStateFlow`.
- `fun onEvent(event: XxxEvent)` is the only public entry point from the UI.
- One-off effects (navigate, play sound, show toast) go through `Channel<XxxEffect>(Channel.BUFFERED).receiveAsFlow()` and are collected in the Route with `LaunchedEffect`.
- `UiState` holds display-ready values: formatted strings come from resources in the composable, but derived values (totals, enabled flags) are computed in the ViewModel or domain.
- Composables never call repositories, never launch coroutines for business work, and never read `Clock` directly.

## 4. Domain rules

- **Money is `Long` IDR** (`typealias Rupiah = Long` or a `@JvmInline value class Rupiah(val value: Long)`). Never use `Double`/`Float` for money. Format only via `IdrFormatter` (BR-01).
- **Time**: business times are `kotlin.time.Instant` (standard library; kotlinx-datetime 0.7+ removed its own `Instant`) from the server. UI timers use the injected `Clock`. Never call `System.currentTimeMillis()` in domain or ViewModels.
- **Errors**: return `Outcome<T>` (success or `DomainError`). Throw only for programmer errors. Never let `IOException`/`HttpException` escape `:core:data`.
- **Immutability**: domain models are `data class`es with `val`s and immutable collections. State machines expose `StateFlow` and change state only inside their own methods.
- Public domain APIs have KDoc that cites the requirement they implement (`/** FR-013 … */`) (NFR-011).

## 5. Compose and design system

- Sizes use `Int.dpx`, text uses `Int.spx`. Raw `.dp`/`.sp` are allowed only inside `:core:designsystem`.
- Colors, type and shapes come from `KioskTheme`. Never write hex literals outside the token file.
- Animations use the named motion specs. Every new animation must respect `LocalReduceMotion`.
- Every user-visible string is a resource in `values/strings.xml`. The 1.0 UI is English only (D-29), so there is no `values-in/` and no language switch. Keep strings externalised so a locale can be added later. Plurals use `<plurals>`.
- Every interactive element has a content description matching the design's aria label (NFR-007).
- Each screen has `@Preview`s for its main states, which feed the Roborazzi screenshot tests. Every `S_<Screen>_<scenario>` board in `docs/ui-design/` has a matching preview and screenshot test (English only).

## 6. Coroutines

- Inject dispatchers (`@IoDispatcher`, `@DefaultDispatcher`). Don't reference `Dispatchers.*` directly outside the DI module.
- No `GlobalScope`. No `runBlocking` outside tests. Long-lived work uses `@ApplicationScope`.
- Polling loops (`PaymentStateMachine`) are cancellable and stop on terminal states.

## 7. Dependencies

- Versions live only in `gradle/libs.versions.toml`, and every library is listed with its purpose in [`technology-stack.md`](technology-stack.md). Only stable releases.
- Every new library needs a line in the PR description saying why. Analytics, crash reporting or ad SDKs are never allowed [D-23].
- Every new external system or hardware access goes behind a domain interface with a scriptable fake in `:core:testing` (NFR-012).

## 8. Debug vs release

- Developer tools (Developer options, simulate bar, fake switching) live only in `src/debug/`. Release must not reference them (`scripts/check-release-apk.sh` in `./gradlew verify`, FR-019 AC3). Debug code lives in package `com.hitachi.kiosk.debug`, which the check rejects in a release APK. `src/main` reaches debug tools only through optional bindings (`DeveloperMenu`, `BindingOverrides`, technical-design §4.3).
- Logging goes through `KioskLog`, never `println`/`Log.*` directly. No PIN, full payment refs or customer data in logs (NFR-006).
- Secrets (signing keystore, pins) never get committed. They come from environment variables or `local.properties`.

## 9. Git workflow

- `main` is always releasable to an emulator: `./gradlew verify` green, instrumented tests green on the local emulators, installable debug APK. There is no CI in 1.0 (D-31).
- Branches: `feat/DEV-0xx-short-name`, `fix/TC-xxx-short-name` or `fix/short-name`, `docs/…`, `chore/…`.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/), matching the existing history: `feat(checkout): poll payment status every 2 s (DEV-047)`. Scope = module or feature package. Reference the DEV/FR/TC ID.
- One DEV task per PR where practical. Squash-merge.
- Tag each milestone `v0.x.0` when its exit criteria pass ([`dev-plan.md`](dev-plan.md)).

## 10. Versioning

`versionName` = milestone (`0.3.0`). `versionCode = MAJOR*10000 + MINOR*100 + PATCH` (NFR-014), so `0.3.0` → `300` and `1.0.0` → `10000`. Both are bumped in one place (`gradle.properties` or the version catalog).

## 11. Definition of done (per DEV task)

1. The behavior matches the cited FR acceptance criteria.
2. Tests from the testing plan for that task exist and pass: unit tests first for domain logic, plus a screenshot or UI test for UI.
3. No new lint, detekt or ktlint findings. Coverage targets hold (domain ≥ 90 %, data ≥ 70 %).
4. Strings are in `values/strings.xml`, not hardcoded. Reduce motion and larger text checked.
5. If the API changed: `api-spec.md` updated first, `openapi.yaml` regenerated.
6. If a decision was made: an ADR, or an update to `architecture.md`.
7. The checkbox is ticked in `dev-plan.md`.
