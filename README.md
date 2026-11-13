# FitnessHubHuawei

Privacy-first Android fitness hub: Huawei Watch Fit 3 → Gadgetbridge → Health Connect → this app,
plus a gym logger and a small Ktor backend.
Full plan: [fitness-hub-plan.md](fitness-hub-plan.md) · work list: [TODO.md](TODO.md).

## Stack
**App:** Kotlin · Jetpack Compose (Material 3) · MVVM + Repository · type-safe Navigation · Room · Health Connect 1.1.0 · WorkManager
**Server:** Ktor 3.6 (Netty) · kotlinx.serialization · Exposed 1.6 + H2 — see [server/README.md](server/README.md)
**Build:** AGP 9.5.1 (built-in Kotlin) · Gradle 9.9.1 · Kotlin 2.4.21 · KSP 2.3.13 · minSdk 26 · compile/targetSdk 37 · JDK 21

## Structure
```
app/src/main/java/ro/fitnesshub/
  FitnessHubApp.kt          Application + AppContainer (manual DI)
  MainActivity.kt
  data/
    HealthRepository.kt     Health Connect → Room cache → UI
    GymRepository.kt        exercises, training days, workouts, sets
    health/                 HealthDataSource, HealthConnectSource, permissions, SeedDataWriter
    db/                     Room database, daily_summary + gym tables
  gym/                      pure gym logic: parsing, progression, PRs, suggestions (unit-tested)
  sync/                     background Health Connect sync (WorkManager)
  ui/
    navigation/             @Serializable routes, one nested graph per bottom tab
    dashboard/              today's steps, distance, calories, heart rate, sleep
    workout/                workout home, exercise library, training-day editor, active workout, progress
    settings/               Health Connect permissions, privacy, debug tools
    log/                    debug log (counts and statuses only)
    privacy/                privacy policy, also the Health Connect rationale screen
  util/AppLog.kt
app/src/debug/AndroidManifest.xml   write permissions for the "Seed test data" tool
server/                     Ktor backend (own README)
```

## Build and run
- **App:** open the folder in Android Studio (a version that supports AGP 9.5), let Gradle sync, press Run.
- **Server:** `./gradlew :server:run`, then `curl http://localhost:8080/health`.
  Emulator reaches it at `http://10.0.2.2:8080`; a USB phone after `adb reverse tcp:8080 tcp:8080`.
  Details: [server/README.md](server/README.md).
- **Tests:** `./gradlew testDebugUnitTest :server:test`
- **GitHub Actions:** every push to `main` runs both test suites and builds `app-debug.apk` (Actions → latest run → Artifacts).

## Test data
Debug builds have Settings → *Seed test data*: 21 days of fake steps, heart rate, sleep and calories written to Health Connect.
Use it on the emulator. *Delete test data* removes only records this app wrote.
