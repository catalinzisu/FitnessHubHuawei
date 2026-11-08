# FitnessHubHuawei

Privacy-first Android fitness hub: Huawei Watch Fit 3 → Gadgetbridge → Health Connect → this app.
Full plan: [fitness-hub-plan.md](fitness-hub-plan.md).

## Stack
Kotlin · Jetpack Compose (Material 3) · MVVM + Repository · Room · Health Connect client 1.1.0
AGP 8.13.2 · Gradle 8.14.3 · Kotlin 2.2.21 · minSdk 26 · targetSdk 36

## Structure
```
app/src/main/java/ro/fitnesshub/
  FitnessHubApp.kt          Application + AppContainer (manual DI)
  MainActivity.kt
  data/
    HealthRepository.kt     Health Connect → Room cache → UI
    health/                 HealthDataSource, HealthConnectSource, permissions, SeedDataWriter
    db/                     Room database, daily_summary + gym tables
  ui/
    navigation/             bottom navigation (Today, Workout, Coach, Settings)
    dashboard/              today's steps, distance, calories, heart rate, sleep
    settings/               Health Connect permissions, privacy, debug tools
    log/                    debug log (counts and statuses only)
    privacy/                privacy policy, also the Health Connect rationale screen
  util/AppLog.kt
app/src/debug/AndroidManifest.xml   write permissions for the "Seed test data" tool
```

## Build
- **Android Studio:** open the folder, let Gradle sync, press Run.
  To create the Gradle wrapper scripts once: Gradle tool window → Tasks → build setup → `wrapper`, then commit `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`.
- **GitHub Actions:** every push to `main` builds `app-debug.apk` (Actions → latest run → Artifacts).

## Test data
Debug builds have Settings → *Seed test data*: 21 days of fake steps, heart rate, sleep and calories written to Health Connect.
Use it on the emulator. *Delete test data* removes only records this app wrote.
