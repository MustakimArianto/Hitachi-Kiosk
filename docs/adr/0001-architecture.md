# ADR-0001: Stack and module structure

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-22 |
| Related | D-24, D-25, D-29, D-06, NFR-001, NFR-011, NFR-012, PRD §12, DEV-002, DEV-019 |

## Context
- Greenfield Android app for an iMin kiosk, `minSdk 30`, portrait 1080 × 1920, built by one developer with AI coding agents in about 3 months (D-25).
- The UI is animation-heavy with a custom visual design (PRD §11). Material defaults do not match it.
- No backend, gateway or EDC spec exists at kickoff (D-06, D-13a), so every external system must be replaceable by a fake (NFR-012).
- The pricing, Budget Plan and payment logic carry the most risk and need ≥ 90 % unit coverage (NFR-011).

## Decision
**Stack** (D-24): Kotlin, Jetpack Compose (Material 3 as foundation only), single Activity, Navigation Compose with type-safe routes, MVVM with unidirectional state (`StateFlow<UiState>`, events in, one-off effects out), coroutines/Flow, Hilt, Room, DataStore, Retrofit + OkHttp + kotlinx.serialization, Coil, WorkManager.

**Token storage — Secure DataStore**: the device token lives in a dedicated DataStore file (`secure.pb`) whose `Serializer` encrypts every write with a Tink `Aead` (AES-256-GCM). The Tink keyset is itself wrapped by a non-exportable Android Keystore key (`AndroidKeysetManager` with an `android-keystore://` master key). This replaces `EncryptedSharedPreferences`: the `androidx.security:security-crypto` library is deprecated, and SharedPreferences does blocking I/O on the main thread. It meets the same NFR-005 requirement.

**Modules**: `:app`, `:core:designsystem`, `:core:domain` (Gradle JVM module, no Android), `:core:data`, `:core:hardware`, `:core:testing`, plus `:benchmark` (planned for 0.3.0, moved to 1.0.0 by the PO on 2026-10-24). Features are **packages inside `:app`**, not modules. Rules and the dependency graph are in [`architecture.md` §3](../architecture.md#3-modules).

## Alternatives considered
| Option | Why not |
|---|---|
| One feature module per screen | Gradle overhead and wiring cost for a solo developer with ~20 screens. Feature boundaries are enforced by a "no feature-to-feature imports" rule and review instead. Can be split later if a second developer joins. |
| Single module | Domain purity can't be enforced, and the pricing/payment logic would pick up Android dependencies and slow tests. |
| MVI library (Orbit, Mavericks) | Adds a dependency and a learning curve for the same unidirectional pattern a plain ViewModel gives. |
| Koin | Runtime resolution errors. Hilt's compile-time graph catches wiring mistakes before the kiosk boots. |
| Ktor client | Retrofit + OkHttp has a more mature MockWebServer, certificate pinning and interceptor story on Android. |
| `EncryptedSharedPreferences` for the token | `security-crypto` is deprecated and gets no fixes. SharedPreferences is synchronous and has no Flow API. Secure DataStore keeps one storage technology for all settings. |
| XML Views | D-24, and the motion inventory (PRD §11.4) is far cheaper to build in Compose. |

## Consequences
- Positive: domain logic is tested on the JVM in seconds. Fakes plug in at interface boundaries. The build stays small enough for fast local iteration.
- Negative: `:app` grows large. Feature isolation depends on discipline (`conventions.md`) rather than the compiler.
- Follow-ups: DEV-002 creates the modules and a dependency check. `README.md` links here (DEV-019). The 1.0 UI is English only (D-29), so there is no locale-switching ADR. If a second language is approved after 1.0, record the mechanism in a new ADR.
