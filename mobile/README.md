# BudgetMate Mobile (Android)

Native Android implementation of BudgetMate using Kotlin, XML layouts/fragments, and Retrofit.

## Tech Stack

- Kotlin
- Android XML layouts + Fragments (Material Components)
- Retrofit + OkHttp
- DataStore (JWT token persistence)
- MVVM-style state via ViewModel

## Features Implemented

- Authentication:
  - Login
  - Register
  - Forgot Password
  - Reset Password
  - Session restore using saved JWT
- Dashboard:
  - Budget and spending summaries
  - Active budget count
  - Recent expenses list
  - Budget utilization status cards
- Expenses:
  - Create expense
  - Update expense
  - List expenses
  - Delete expense
- Budgets:
  - Create budget
  - Update budget
  - List budgets
  - Delete budget
- Currency display:
  - Select display currency in app bar
- Admin:
  - List users
  - Active/inactive user metrics
  - Admin/self delete protection in UI
  - Delete user

## Backend Alignment

The app calls the same backend route groups used by the web frontend:

- /api/v1/auth/*
- /api/v1/budgets/*
- /api/v1/expenses/*
- /api/v1/users/*

Default local development endpoints are selected automatically at runtime:

- Emulator: `http://10.0.2.2:8080/api/v1/` and `ws://10.0.2.2:8080/ws-native`
- Physical phone: `http://127.0.0.1:8080/api/v1/` and `ws://127.0.0.1:8080/ws-native`

Configured in `app/build.gradle.kts` via:

- `BuildConfig.API_BASE_URL_EMULATOR`
- `BuildConfig.WS_BASE_URL_EMULATOR`
- `BuildConfig.API_BASE_URL_DEVICE`
- `BuildConfig.WS_BASE_URL_DEVICE`

For physical devices, run ADB reverse before launching the app:

- `adb reverse tcp:8080 tcp:8080`

Optional: if you prefer Wi-Fi LAN routing instead of ADB reverse, set a custom host during build:

- `./gradlew :app:assembleDebug -PDEVICE_HOST=<your-lan-ip>`

## Open in Android Studio

1. Open Android Studio.
2. Select Open and choose the mobile folder.
3. Let Gradle sync complete.
4. Make sure Gradle JDK is set to 17 or 21 in Android Studio settings.
5. Run app configuration on an Android emulator.

## Run Requirements

1. Start backend server on your machine at port 8080.
2. Use Android emulator directly, or use `adb reverse tcp:8080 tcp:8080` for physical phones.
3. Ensure internet permission is enabled in AndroidManifest.xml (already set).

## Notes

- Realtime dashboard updates are websocket/STOMP based (subscribed per user) and trigger dashboard refresh.
- Google OAuth mobile deep-link callback is handled using `budgetmate://oauth2/redirect`.

## Project Structure

- app/src/main/java/edu/cit/delacruz/budgetmate/data
  - model: request/response models
  - network: Retrofit API + client
  - repository: backend feature repository
  - session: DataStore token manager
- app/src/main/java/edu/cit/delacruz/budgetmate/ui
  - AppViewModel.kt
  - AppRoot.kt
  - theme/*
- app/src/main/java/edu/cit/delacruz/budgetmate/MainActivity.kt

## Next Steps

1. Add date pickers and category dropdown components for stronger input validation.
2. Add pagination controls for large expenses/budgets/user lists.
3. Add repository and ViewModel test coverage.
4. Add UI test coverage for auth and CRUD flows.
