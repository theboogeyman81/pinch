
# Pinch/Gary — Android App Scaffold

## Context

The `pinch` repo currently has zero commits and zero code — only planning docs (`CLAUDE.md` at root is the architecture source of truth; `.claude/spec/01_main.md`, `.txt/plan.txt`, `.txt/tasks.txt` are earlier brainstorm artifacts CLAUDE.md supersedes). The goal now is to lay down a production-quality Android app skeleton, then build it out feature-by-feature per the 12-week order already defined in CLAUDE.md (GlassesManager first). This plan covers the scaffold only — not the GlassesManager feature logic itself, which is the next step after this lands.

Decisions locked in with the user:
- **Package ID**: `com.pinch.gary`
- **Gradle structure**: single `:app` module (not multi-module) — module boundaries between the 7 planned managers aren't proven yet, solo dev, fastest iteration; can split into multi-module later once real seams appear.
- **`.claude/save/`**: a persistent, in-repo decision/status log (distinct from `.claude/spec/`'s raw brainstorm) so future Claude sessions don't re-derive settled architecture calls. Lives at repo root, sibling to `CLAUDE.md`.

## Recommended Approach

### Monorepo placement
Nest the Gradle project at `android/`, matching the repo layout CLAUDE.md already commits to (`android/`, `cloud/`, `hardware/`, `docs/` siblings). Avoids surgery when `cloud/`/`hardware/` land later.

### Gradle setup
- `android/settings.gradle.kts` — single `include(":app")`, centralized `dependencyResolutionManagement`.
- `android/build.gradle.kts` (root) — plugins declared `apply false`: Android application, Kotlin Android, Kotlin Compose compiler plugin (Kotlin 2.x moved this out of AGP — don't use the old `composeOptions.kotlinCompilerExtensionVersion`), kotlinx-serialization, KSP, Hilt.
- `gradle/libs.versions.toml` — add version catalog entries for the **entire** eventual stack now (MediaPipe, TFLite, OpenCV, LiveKit, Play Billing included), but only wire real `implementation()` calls in `app/build.gradle.kts` for what week 1–2 needs. Avoids repeated catalog churn without pulling in unused deps yet.
- `app/build.gradle.kts` — `namespace`/`applicationId` = `com.pinch.gary`, `minSdk = 26`, `compileSdk`/`targetSdk = 35`, Compose enabled. Active dependencies this week: Compose BOM + Material3, Hilt (+ KSP compiler), `lifecycle-viewmodel-compose`, `navigation-compose`, coroutines, kotlinx-serialization-json, OkHttp (+ logging interceptor), DataStore-preferences.
- **DI: Hilt**, confirmed over Koin — compile-time safety matters here given how many long-lived singletons (BLE GATT client, WebSocket client, Keystore wrapper) need consistent scoping across a Foreground-Service-heavy app; runtime-resolved DI failures on BLE/service lifecycles are exactly the bugs you don't want discovered on a real phone.
- `GaryApplication : Application()` with `@HiltAndroidApp`, top-level (not inside a feature package).

### Package structure — `app/src/main/java/com/pinch/gary/`

```
com.pinch.gary/
├── GaryApplication.kt
├── MainActivity.kt                    — single Activity, hosts NavHost
│
├── core/
│   ├── di/            NetworkModule, DispatchersModule, AppScopeModule
│   ├── security/       KeystoreManager.kt   — wraps Android Keystore; shared by later garyclient (JWT) + smarthome (HA token)
│   ├── permissions/    RequiredPermissions.kt, PermissionState.kt
│   ├── theme/          Color/Type/Shape/GaryTheme.kt
│   ├── navigation/     Destinations.kt (sealed class), GaryNavHost.kt
│   ├── appstate/       AppState.kt, GaryOrchestrator.kt   — see State Management below
│   └── util/           Logger.kt — only accepts String/metadata, never ByteArray/InputStream (guardrail against logging raw MJPEG/PCM)
│
├── glasses/            — BUILD NOW (week 1–2)
│   ├── GlassesManager.kt / GlassesManagerImpl.kt   — state machine: Disconnected → Scanning → BleConnected → Streaming
│   ├── ble/            BleScanner.kt, BleConnectionManager.kt, BleGattProfile.kt (UUID constants — placeholder pending hardware/ firmware decisions)
│   ├── mjpeg/          MjpegStreamClient.kt (OkHttp multipart parser), FrameRingBuffer.kt (3–5 frames, RAM only, no write())
│   ├── service/        GlassesForegroundService.kt (foregroundServiceType="connectedDevice|microphone")
│   ├── model/          GlassesConnectionState.kt, GlassesDevice.kt
│   └── GlassesViewModel.kt
│
├── vision/, gesture/, voice/, garyclient/, smarthome/  — DO NOT CREATE YET, scaffold when their build-order week starts
├── usercontext/        — DO NOT CREATE YET; name is `usercontext` not `context` — a package literally named `context` shadows every unqualified `android.content.Context` reference app-wide
│
└── ui/
    ├── main/           MainScreen.kt, MainViewModel.kt — real now: status dot + state label + battery, no buttons (per CLAUDE.md UI spec)
    ├── onboarding/      placeholder composable now, real content week 11–12
    ├── settings/        placeholder now
    ├── permissions/     semi-real now — BLE + location runtime requests needed for week 1–2 glasses work
    └── components/      StatusDot.kt
```

Layering rule: only `garyclient`, `smarthome`, `usercontext` get `domain/`/`data/` subpackages (when their week arrives) since they have real domain models worth separating from wiring. `glasses`/`gesture`/`voice` are single-responsibility state-machine managers — flat subpackages, no domain/data split (would be over-engineering).

### State management
Compose-idiomatic version of "AppState is single source of truth, managers don't talk directly":
- Each feature manager is `@Singleton`, holds a private `MutableStateFlow`, exposes a public read-only `StateFlow`.
- `core/appstate/AppState.kt` (`@Singleton`) injects each manager's `StateFlow` and `combine()`s them into one `StateFlow<AppUiState>`, `stateIn`'d against an `@ApplicationScope` `CoroutineScope`. This week it wires in `GlassesManager` only — grows one manager at a time as features land.
- `core/appstate/GaryOrchestrator.kt` is the **only** class allowed to depend on more than one manager (cross-feature wiring: glasses frames → vision engine, gestures → voice session, etc., once those exist).
- Screen ViewModels (`MainViewModel`, etc.) depend only on `AppState`, never on individual managers — `MainScreen` never imports `GlassesManager` directly.

### Testing
- `app/src/test/` (JUnit + MockK + Turbine + kotlinx-coroutines-test): state machine transitions, `FrameRingBuffer` eviction, later gesture debounce logic, WS message (de)serialization, `AppState.combine()` aggregation.
- `app/src/androidTest/`: Compose UI tests for `MainScreen` against fake `AppUiState`, permission-flow tests, Foreground Service lifecycle.
- Per CLAUDE.md's explicit note that the emulator is unreliable for BLE/camera: keep the untestable-on-emulator surface thin by hiding it behind interfaces (`GlassesManager` interface + `Impl`), so everything above the BLE/MJPEG boundary is unit-testable with a fake. BLE GATT, MJPEG-over-real-WiFi, and (later) MediaPipe/YOLO/LiveKit-against-real-hardware need a documented manual test pass on a real phone instead of CI — track this as a checklist in `.claude/save/features/<feature>.md`.

### AndroidManifest.xml
Full permission list declared now (declaring ≠ requesting at runtime, which stays deferred to each feature's week):
`BLUETOOTH_SCAN` (`usesPermissionFlags="neverForLocation"`), `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` (needed later for SmartHomeManager's "am I home" geofence — not in CLAUDE.md's literal list but required since Android 10 for background geofencing), `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`.

Also add `res/xml/network_security_config.xml` scoped to the local LAN now — the ESP32 MJPEG stream and HA local API are plain `http://`, and Android blocks cleartext by default since API 28. Omitting this makes `GlassesManager`'s MJPEG client fail silently in week 1–2 on any real device.

Declares `GaryApplication`, `MainActivity` (launcher), and `GlassesForegroundService` (`android:exported="false"`).

### Scaffold now vs defer
**Now, real content**: all Gradle files, full `app/build.gradle.kts`, full manifest + network security config, `GaryApplication`/`MainActivity`/`core/navigation` (all 4 destinations wired, nav graph compiles), `core/theme`, `core/di` (Network/Dispatchers/AppScope modules), `core/security/KeystoreManager` (cheap to build correctly once now, needed soon by two future features), `core/appstate` (wired to GlassesManager only), `ui/main` (real), `ui/permissions` (semi-real), the full `glasses/` package including the Foreground Service.

**Defer, do not pre-create**: `vision/`, `gesture/`, `voice/`, `garyclient/`, `smarthome/`, `usercontext/` packages — created only when their build-order week starts. `ui/onboarding`, `ui/settings` stay one-line placeholders until week 11–12. Play Billing wiring waits until then too.

### `.claude/save/` — new directory, repo root, sibling to `CLAUDE.md`
Distinct from `.claude/spec/` (raw brainstorm transcript) — this is the curated, living decision/status log.
- **`architecture-decisions.md`** — ADR log, seeded with: single `:app` module; Hilt for DI; package-by-feature with domain/data splits limited to garyclient/smarthome/usercontext; Gradle nested at `android/`; `usercontext` naming (avoids shadowing `android.content.Context`); `network_security_config.xml` requirement; AppState + GaryOrchestrator pattern.
- **`folder-structure.md`** — the concrete package tree above, kept current; append as each new feature package is scaffolded at the start of its week.
- **`features/glasses-manager.md`** — stub for this week's feature: state machine diagram, BLE GATT UUIDs (placeholder pending hardware/ firmware decisions), MJPEG endpoint scheme, Foreground Service notification spec, ring-buffer handoff contract to the future VisionEngine, and the manual on-device test checklist.
Other `features/*.md` files are created just-in-time per feature, not all seven now.

## Critical Files
- `/Users/prathamyaligar/Desktop/pinch/CLAUDE.md` — architecture source of truth, already read
- `/Users/prathamyaligar/Desktop/pinch/android/gradle/libs.versions.toml`
- `/Users/prathamyaligar/Desktop/pinch/android/app/build.gradle.kts`
- `/Users/prathamyaligar/Desktop/pinch/android/app/src/main/AndroidManifest.xml`
- `/Users/prathamyaligar/Desktop/pinch/android/app/src/main/java/com/pinch/gary/core/appstate/AppState.kt`
- `/Users/prathamyaligar/Desktop/pinch/android/app/src/main/java/com/pinch/gary/glasses/GlassesManager.kt`
- `/Users/prathamyaligar/Desktop/pinch/.claude/save/architecture-decisions.md` (new)
- `/Users/prathamyaligar/Desktop/pinch/.claude/save/folder-structure.md` (new)
- `/Users/prathamyaligar/Desktop/pinch/.claude/save/features/glasses-manager.md` (new)

## Verification
1. `cd android && ./gradlew :app:assembleDebug` — project compiles clean with the scaffold (empty `glasses/` logic is fine at this stage, but the module graph, Hilt setup, and Compose theme must build).
2. `./gradlew :app:testDebugUnitTest` — placeholder unit test (e.g. for `GlassesConnectionState` transitions once stubbed) runs green, confirming the test infra (JUnit/MockK/Turbine) is wired correctly.
3. Install the debug APK on the real Android phone (per CLAUDE.md — not the emulator) and confirm: app launches, `MainActivity` shows the nav graph's `MainScreen` with the status dot placeholder, permissions screen requests BLE + location correctly, and the Foreground Service notification appears without crashing.
4. Manually diff `.claude/save/folder-structure.md` against the actual `app/src/main/java/com/pinch/gary/` tree to confirm they match before moving on to GlassesManager feature work.
