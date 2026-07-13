
# Architecture Decisions

Curated, living decision log for the Android app (`android/`). Distinct from `.claude/spec/` (raw brainstorm transcript) — this file only holds settled decisions, so future sessions don't re-derive them. Append new entries at the bottom; don't rewrite history, add a superseding entry instead.

---

## ADR-001: Single `:app` Gradle module

Not multi-module. Module boundaries between the 7 planned managers (glasses, vision, gesture, voice, garyclient, smarthome, usercontext) aren't proven yet, this is a solo dev project, and a single module means fastest iteration with no build-graph overhead.

**Revisit when:** a real seam appears — e.g. if `vision/` needs to be reused outside the app, or build times start hurting iteration speed.

## ADR-002: Hilt for dependency injection

Chosen over Koin. Compile-time safety matters here because of how many long-lived singletons (BLE GATT client, WebSocket client, Keystore wrapper) need consistent scoping across a Foreground-Service-heavy app. Runtime-resolved DI failures on BLE/service lifecycles are exactly the kind of bug you don't want discovered on a real phone in the field.

## ADR-003: Package-by-feature, with domain/data split only where it earns its keep

Top-level packages are features (`glasses/`, `vision/`, `gesture/`, `voice/`, `garyclient/`, `smarthome/`, `usercontext/`), not layers. Only `garyclient`, `smarthome`, `usercontext` get `domain/`/`data/` subpackages, because they have real domain models worth separating from wiring. `glasses`/`gesture`/`voice` are single-responsibility state-machine managers — flat subpackages, no domain/data split, since that split would be over-engineering for what they do.

## ADR-004: Gradle project nested at `android/`, not repo root

Matches the repo layout CLAUDE.md already commits to (`android/`, `cloud/`, `hardware/`, `docs/` as siblings). Avoids surgery when `cloud/` and `hardware/` land later.

## ADR-005: `usercontext` package name, not `context`

A package literally named `context` would shadow every unqualified `android.content.Context` reference app-wide. Named `usercontext` instead. Not created yet — scaffolded only when its build-order week starts.

## ADR-006: `network_security_config.xml` required from day one

Not mentioned explicitly in CLAUDE.md, but necessary: the ESP32 MJPEG stream and the local Home Assistant API are both plain `http://`, and Android has blocked cleartext traffic by default since API 28. Without this config, `GlassesManager`'s MJPEG client fails silently on any real device. Implemented as an app-wide `base-config` rather than domain-pinned, because both peers live at DHCP-assigned IPs discovered at runtime and Android's network-security-config has no CIDR/subnet syntax — only exact domain/IP matches. `GlassesManager` and `SmartHomeManager` are the only two code paths that ever issue `http://` requests; everything else (Gary cloud) is `https://`.

## ADR-007: `AppState` + `GaryOrchestrator` pattern

Compose-idiomatic version of CLAUDE.md's "AppState is single source of truth, managers don't talk directly" rule (originally written with an iOS/observable-object mental model in the brainstorm docs). Each feature manager exposes a read-only `StateFlow`; `core/appstate/AppState.kt` combines them into one `StateFlow<AppUiState>`. `core/appstate/GaryOrchestrator.kt` is the only class allowed to depend on more than one manager — all cross-feature wiring (e.g. glasses frames → vision engine) goes through it. Screen ViewModels depend only on `AppState`, never on individual managers directly.

## ADR-008: Interfaces around untestable-on-emulator surfaces

Per CLAUDE.md's note that the Android emulator is unreliable for BLE/camera testing: `GlassesManager` is defined as an interface with a `GlassesManagerImpl`, so everything above the BLE/MJPEG boundary is unit-testable with a fake. The actual BLE GATT / MJPEG-over-WiFi code needs a manual on-device test pass instead of CI — tracked per-feature in `.claude/save/features/<feature>.md`.

## ADR-009: compileSdk/targetSdk pinned to 34, not 35

The plan originally called for 35, but the dev machine's local Android SDK only has platform 34 installed (no `cmdline-tools`/`sdkmanager` available to auto-fetch 35 during Gradle sync). Pinned to 34 so the scaffold builds offline against what's actually installed. Bump to 35 once platform 35 + build-tools are installed via Android Studio's SDK Manager — nothing in the code depends on 35-specific APIs yet.

## ADR-010: Gesture input is camera-only (MediaPipe) for v0, radar deferred to v1 hardware

CLAUDE.md originally specced BGT60TR13C radar as the primary gesture input, replacing a second camera entirely. No radar hardware (or any glasses hardware) exists yet — only the software prototype is being built right now. Decision: build `GestureRecognizer` against MediaPipe hand landmarks only for v0, using the phone's own camera via `VisionEngine`. Radar stays the plan for the next hardware prototype, not this one.

To avoid rework when radar arrives: `GestureRecognizer` takes its input through a `GestureSource` interface (or equivalent seam) so the command-mapping/debounce logic doesn't care whether events came from MediaPipe landmarks or radar BLE events. When hardware lands, radar becomes another `GestureSource` implementation — CLAUDE.md's "radar primary, MediaPipe fallback" plan is unchanged for v1, it's just not what v0 builds against.

**Revisit when:** BGT60TR13C breakout board is in hand and wired to the ESP32 (see CLAUDE.md's open questions).
