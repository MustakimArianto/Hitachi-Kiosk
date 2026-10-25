# ADR-0005: `releaseLocal` run variant for checking the R8 build

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-25 |
| Related | NFR-003, NFR-005, TR-SEC-07, TR-PRF-01, DEV-115, DEV-116, [technical-design §2.5](../technical-design.md) |

## Context
`release` is minified and resource-shrunk, but it cannot be run for a smoothness or R8 check: it is unsigned, points at the `prod` host, and contains no fakes (`UnavailableCardTerminal` fails every sale), because TR-SEC-07 forbids debug code in it. R8 breakage (serialization, Hilt, Room, navigation routes) only shows when the minified code runs.

## Decision
Add a build type `releaseLocal` to `:app` (`kiosk.android.application`): `initWith(release)` (same R8, shrink, `isDebuggable = false`, same `proguard-rules.pro`), `applicationIdSuffix = ".local"`, `versionNameSuffix = "-local"`, signed with the Android debug key, `dev` environment fields plus `MOCK_BASE_URL`. Its source set adds `src/debug` (kotlin, res, assets, manifest), and it takes `core:testing` and DataStore as `releaseLocalImplementation`. Run it with `./gradlew :app:installReleaseLocal`. `checkReleaseApk` stays registered for `release` only, so `release` still rejects every debug class. `releaseLocal` is never distributed.

## Alternatives considered
| Option | Why not |
|---|---|
| Sign `release` locally and run it | Stays strict, so it cannot get past activation without a real backend. |
| Add debug tools to `release` behind a flag | Breaks TR-SEC-07 and FR-019 AC3. |
| Turn R8 on in `debug` | Loses debuggability and slows the inner loop; does not test the release configuration. |

## Consequences
- Positive: the exact release R8 configuration can be run with fakes; the id suffix lets it sit beside debug and release on a device.
- Negative / cost: `src/debug` is compiled a third time; a debug-source change can break `releaseLocal` (build it before merging, `verify` does not).
- Follow-ups: the on-device smoothness run on the iMin unit is in `todo.md`; the baseline profile stays in 1.0.0 (DEV-031).
