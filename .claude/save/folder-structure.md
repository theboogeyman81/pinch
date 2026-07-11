# Folder Structure

Living map of `android/app/src/main/java/com/pinch/gary/`. Update this whenever a new feature package is scaffolded (at the start of that feature's build-order week) — keep it in sync with the actual tree, don't let it drift into aspirational territory.

Status key: **[BUILT]** exists with real logic · **[STUB]** placeholder only · **[PLANNED]** not created yet, listed here so the eventual location is known in advance.

```
com.pinch.gary/
├── GaryApplication.kt                  [BUILT]
├── MainActivity.kt                     [BUILT]  — single Activity, hosts NavHost
│
├── core/
│   ├── di/                             [BUILT]  NetworkModule, DispatchersModule, AppScopeModule
│   ├── security/                       [BUILT]  KeystoreManager.kt
│   ├── permissions/                    [BUILT]  RequiredPermissions.kt, PermissionState.kt
│   ├── theme/                          [BUILT]  Color/Type/Shape/GaryTheme.kt
│   ├── navigation/                     [BUILT]  Destinations.kt, GaryNavHost.kt
│   ├── appstate/                       [BUILT]  AppState.kt (wired to GlassesManager only so far), GaryOrchestrator.kt
│   └── util/                           [BUILT]  Logger.kt — never accepts ByteArray/InputStream
│
├── glasses/                            [BUILT]  — week 1–2 feature
│   ├── GlassesManager.kt / GlassesManagerImpl.kt
│   ├── ble/                            BleScanner.kt, BleConnectionManager.kt, BleGattProfile.kt
│   ├── mjpeg/                          MjpegStreamClient.kt, FrameRingBuffer.kt
│   ├── service/                        GlassesForegroundService.kt
│   ├── model/                          GlassesConnectionState.kt, GlassesDevice.kt
│   └── GlassesViewModel.kt
│
├── vision/                             [PLANNED] — week 3–4
├── gesture/                            [PLANNED] — week 3–4
├── voice/                              [PLANNED] — week 5–6
├── garyclient/                         [PLANNED] — week 7–8, gets domain/ + data/
├── smarthome/                          [PLANNED] — week 9–10, gets domain/ + data/
├── usercontext/                        [PLANNED] — week 9–10, gets domain/ + data/ (named to avoid shadowing android.content.Context)
│
└── ui/
    ├── main/                           [BUILT]  MainScreen.kt, MainViewModel.kt — status dot + state label, no buttons
    ├── onboarding/                     [STUB]   real content week 11–12
    ├── settings/                       [STUB]   real content week 11–12
    ├── permissions/                    [BUILT]  semi-real — BLE + location runtime requests
    └── components/                    [BUILT]  StatusDot.kt
```

Resources:
```
app/src/main/res/xml/network_security_config.xml   [BUILT]  scoped to local LAN cleartext traffic
```
