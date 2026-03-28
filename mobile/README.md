# BudgetMate Mobile (Android)

Native Android implementation of BudgetMate using Kotlin, Jetpack Compose, and Retrofit.

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
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
  - Budget, expense, and balance summaries
  - Recent expenses list
- Expenses:
  - Create expense
  - List expenses
  - Delete expense
- Budgets:
  - Create budget
  - List budgets
  - Delete budget
- Exchange:
  - Currency conversion
- Admin:
  - List users
  - Delete user

## Backend Alignment

The app calls the same backend route groups used by the web frontend:

- /api/v1/auth/*
- /api/v1/budgets/*
- /api/v1/expenses/*
- /api/v1/exchange-rates/*
- /api/v1/users/*

Default API base URL (emulator-friendly):

- http://10.0.2.2:8080/api/v1/

Configured in app/build.gradle.kts via BuildConfig.API_BASE_URL.

## Open in Android Studio

1. Open Android Studio.
2. Select Open and choose the mobile folder.
3. Let Gradle sync complete.
4. Make sure Gradle JDK is set to 17 or 21 in Android Studio settings.
5. Run app configuration on an Android emulator.

## Run Requirements

1. Start backend server on your machine at port 8080.
2. Use Android emulator (10.0.2.2 points to host localhost).
3. Ensure internet permission is enabled in AndroidManifest.xml (already set).

## Notes

- Realtime dashboard updates are currently implemented as periodic polling every 15 seconds.
- Backend websocket is STOMP-based; a dedicated STOMP client integration can be added next for push updates.
- Google OAuth mobile deep-link flow is not wired yet in this initial implementation.

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

1. Add edit/update flows for budgets and expenses.
2. Add websocket STOMP subscription for true realtime refresh.
3. Add form validation and richer UI components.
4. Add tests for repository and viewmodel logic.
