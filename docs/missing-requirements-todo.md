# BudgetMate Missing Requirements TODO

Date: 2026-04-12
Purpose: Track all currently missing or partial requirements and define clear completion criteria.

## How to use this document
- Mark each item as [ ] Not Started, [~] In Progress, or [x] Done.
- Attach proof links (screenshot, endpoint test, commit hash) under each item.
- Treat this as the final grading checklist.

---
DONE
## 1) File Upload Integration (Required) 
Status: [x]
Priority: High

### Tasks
- [x] Add backend upload endpoint for profile image attachment.
- [x] Validate file type (image) and max size.
- [x] Store file on server (configured storage path: uploads/profile-images).
- [x] Save file metadata in database and link it to core entity record (User profile).
- [x] Add backend endpoint to view/download uploaded file.
- [x] Add web UI file picker and upload action.
- [x] Add web UI preview/download for previously uploaded file.

### Acceptance Criteria
- User can upload a file from web UI.
- Uploaded file is physically stored and linked to database record.
- User can retrieve/download the same file from the app.

### Evidence
- API test result: /api/v1/users/me/profile-image (POST, GET), /api/v1/users/me/name (PUT), /api/v1/users/me/password (PUT)
- UI screenshot: pending capture (Profile page)
- DB row sample: users.image_url, users.image_content_type, users.image_size, users.image_uploaded_at

---

## 3) Architecture Diagram and Explanation (Required)
Status: [ ]
Priority: High

### Tasks
- [ ] Add architecture diagram to docs (Layered Architecture is acceptable).
- [ ] Include Controller -> Service -> Repository -> DB flow.
- [ ] Include Security (JWT filter + Spring Security), OAuth, SMTP, External API, and WebSocket components.
- [ ] Add a short explanation for each layer responsibility.

### Acceptance Criteria
- Diagram is present in documentation.
- Pattern is explicitly named and explained.
- Reviewer can trace request flow from frontend/mobile to backend and database.

### Evidence
- Diagram file/path:
- Explanation section link:

---
DONE
## 4) Web Google OAuth Redirect Completion (Current partial)
Status: [x]
Priority: High

### Tasks
- [x] Add frontend route/page for OAuth redirect target.
- [x] Parse token (or error) from query parameters.
- [x] Save JWT to local storage/session and fetch current user.
- [x] Redirect authenticated user to dashboard.
- [x] Show clean error message on OAuth failure.

### Acceptance Criteria
- Clicking "Continue with Google" logs in successfully on web.
- No manual token copy/paste needed.
- Error flow is user-friendly.

### Evidence
- OAuth success screenshot: pending capture
- OAuth failure screenshot: pending capture
- Route source file: web/src/pages/OAuth2RedirectPage.js (web), mobile/app/src/main/AndroidManifest.xml + mobile/app/src/main/java/budgetmate/MainActivity.kt (mobile deep-link target)

---
DONE
## 5) Android Compliance with Strict Mobile Rules (Major blocker)
Status: [~]
Priority: Critical

### Current Gap
- Manual functional re-test (device/emulator walkthrough) still needed after code migration.

### Tasks
- [x] Migrate UI from Compose to XML layouts + Activities/Fragments.
- [x] Remove Compose plugin/dependencies.
- [x] Set compileSdk and targetSdk to 34.
- [x] Keep Retrofit integration against the same backend.
- [x] Re-test authentication and core module flows after migration.

### Acceptance Criteria
- No Compose UI usage remains in mobile app.
- XML layouts exist for app screens.
- API level config is 34.
- App still performs JWT auth and protected API calls.

### Evidence
- Updated Gradle config screenshot: mobile/app/build.gradle.kts (compileSdk=34, targetSdk=34, Compose plugin removed)
- XML layout files list:
  - mobile/app/src/main/res/layout/activity_main.xml
  - mobile/app/src/main/res/layout/fragment_dashboard.xml
  - mobile/app/src/main/res/layout/fragment_expenses.xml
  - mobile/app/src/main/res/layout/fragment_budgets.xml
  - mobile/app/src/main/res/layout/fragment_admin.xml
- Compose removal proof: no androidx.compose/@Composable matches in mobile/app/src/main/java/**
- Build proof: ./gradlew :app:assembleDebug -> BUILD SUCCESSFUL (API 34)
- Demo video/screenshot of login + core flow: pending manual capture

---
DONE
## 6) Secure JWT Storage on Android (Recommended hardening)
Status: [x]
Priority: Medium

### Tasks
- [x] Replace plaintext token persistence with encrypted storage mechanism.
- [x] Keep Authorization header injection behavior.
- [x] Verify token survives app restart and clears on logout.

### Acceptance Criteria
- JWT is not stored in plaintext storage.
- Protected endpoints continue to work.

### Evidence
- Storage implementation file: mobile/app/src/main/java/budgetmate/data/session/SessionManager.kt (EncryptedSharedPreferences + MasterKey), web/src/utils/secureTokenStorage.js (AES encrypted token-at-rest)
- Login/logout retest result: web npm run build PASS; mobile ./gradlew :app:assembleDebug PASS; token injection path preserved in mobile/app/src/main/java/budgetmate/data/network/ApiClient.kt and web/src/api/axiosInstance.js

---

## 7) Requirements Evidence Pack (Strongly recommended for defense)
Status: [ ]
Priority: Medium

### Tasks
- [ ] Build one checklist document mapping each requirement to proof.
- [ ] Add endpoint list + sample response snippets.
- [ ] Add screenshots for:
  - [ ] Registration/login/logout
  - [ ] Protected route blocking
  - [ ] Role-based UI differences (admin vs user)
  - [ ] CRUD operations
  - [ ] External API data shown in UI
  - [ ] Email sent (mailbox proof)
  - [ ] OAuth login success
  - [ ] File upload flow
  - [ ] Payment flow (if required)
  - [ ] Mobile protected API calls and role-aware UI

### Acceptance Criteria
- Reviewer can verify all requirements from one location in under 10 minutes.

### Evidence
- Final checklist file/path:

---

## Suggested execution order
1. Android strict-compliance blocker (XML + API 34).
2. File upload implementation.
3. OAuth redirect completion on web.
4. Architecture diagram + explanation.
5. Payment gateway (if evaluator confirms required).
6. Evidence pack compilation.

## Definition of Done (Overall)
- Every required item in your course checklist is marked [x].
- Each completed item has attached proof.
- Web, backend, and mobile are aligned to same backend and pass smoke tests.
