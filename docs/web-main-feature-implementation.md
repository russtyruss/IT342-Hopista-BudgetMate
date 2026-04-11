# BudgetMate Web App Documentation

## 1. Main Feature Implementation

### Main Feature: Personal Budget and Expense Tracking
The main feature implemented in the web system is end-to-end personal finance tracking, where authenticated users can:
- Create, view, update, and delete expenses
- Create, view, update, and delete budgets
- Optionally link an expense to a specific budget so budget spending is updated
- View dashboard summaries of budgets and expenses

This matches the project direction of budgeting and expense management and is fully functional in the deployed web flow.

### Functional Connection to Backend and Database
The feature is connected to the backend and database through authenticated REST APIs.
- Web pages call API modules in `web/src/api/`
- Backend controllers/services process requests and enforce validation/security
- Data is persisted in PostgreSQL via JPA entities/repositories

### Main Purpose Fulfillment
The system allows users to perform the core purpose of the application:
- Track daily spending
- Set budget limits
- Monitor spending vs budget in dashboard and budget status views

### Validation and Error Handling
Validation is applied in both frontend and backend.

Frontend validation examples:
- Required inputs in forms (`required` fields)
- Numeric and range constraints for amount inputs (`type="number"`, `step`, `min`)

Backend validation examples (`@Valid` request DTOs):
- Expense:
  - `title` required, max 200
  - `amount` required, must be > 0
  - `currency` required
  - `category` required
  - `expenseDate` required
- Budget:
  - `category` required
  - `limitAmount` required, must be > 0
  - `currency` required
  - `startDate` and `endDate` required
  - `notes` max 200

Error handling examples:
- Unauthorized/forbidden access is handled by security configuration and Axios interceptor
- API failures show user-facing messages (e.g., failed load/save notices)
- Invalid form submissions return backend validation errors

Success/error feedback examples in web pages:
- Login success and failure messages
- "Failed to load expenses/budgets"
- "Failed to save expense/budget"

---

## 2. Web Integration

### Frontend to Backend API Integration
The web frontend is connected through Axios (`web/src/api/axiosInstance.js`) with JWT bearer token support.

Integrated modules include:
- `authApi.js`
- `expenseApi.js`
- `budgetApi.js`
- `userApi.js` (admin)
- `exchangeRateApi.js`

### Correct Data Save/Retrieve Flow
- Create/update actions submit JSON payloads to backend endpoints
- Backend validates and persists via JPA repositories
- List/detail endpoints return data used by pages/components
- Pagination responses are handled (`res.data.content`)

### Working Frontend-Backend-Database Interaction
Observed interaction path:
1. User action in web UI (e.g., add expense)
2. Frontend API call to `/api/v1/...`
3. Backend controller -> service -> repository
4. JPA persistence to PostgreSQL tables
5. Response returned and rendered in UI

Security/data isolation behavior:
- Role-based admin routes are protected (`ADMIN` only)
- Users can access only their own budget/expense data
- Admin endpoints for user management are restricted to admin role

---

## 4. Short Summary

### Description of the Main Feature
BudgetMate web implements personal budget and expense management with authentication, dashboard insights, budget linkage for expenses, and admin user management.

### Inputs and Validations Used
- Login/Register inputs with required constraints
- Expense form: title/description, amount, category, date, optional notes, optional linked budget
- Budget form: category, limit amount, currency, start/end date, optional notes
- Backend DTO validations enforce required fields, formats, and bounds

### How the Feature Works
- Users authenticate and receive JWT token
- Authenticated users manage expenses and budgets
- Expense entries can be linked to budgets to update spent amounts
- Dashboard aggregates and displays financial summaries
- Admin users can access admin dashboard to manage users and view active/inactive status

### API Endpoints Used
Authentication:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

Expenses:
- `POST /api/v1/expenses`
- `GET /api/v1/expenses`
- `GET /api/v1/expenses/{id}`
- `PUT /api/v1/expenses/{id}`
- `DELETE /api/v1/expenses/{id}`

Budgets:
- `POST /api/v1/budgets`
- `GET /api/v1/budgets`
- `GET /api/v1/budgets/{id}`
- `PUT /api/v1/budgets/{id}`
- `DELETE /api/v1/budgets/{id}`

Admin/User Management:
- `GET /api/v1/users`
- `GET /api/v1/users/{id}`
- `DELETE /api/v1/users/{id}`

Currency/Utility:
- `GET /api/v1/exchange-rates`
- `GET /api/v1/exchange-rates/convert`

### Database Tables/Entities Involved
Core tables/entities:
- `users` (`User`)
- `user_roles` (roles collection)
- `expenses` (`Expense`)
- `budgets` (`Budget`)

Auth/session control:
- `revoked_tokens` (`RevokedToken`)

These tables support authentication, authorization, budgeting, expense tracking, and token revocation behavior.
