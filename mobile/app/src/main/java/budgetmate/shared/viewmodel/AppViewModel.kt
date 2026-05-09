package budgetmate.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import budgetmate.shared.data.model.BudgetRequest
import budgetmate.shared.data.model.BudgetResponse
import budgetmate.shared.data.model.ExpenseRequest
import budgetmate.shared.data.model.ExpenseResponse
import budgetmate.shared.data.model.ProfileImagePayload
import budgetmate.shared.data.model.UserResponse
import budgetmate.shared.data.network.AdminUsersRealtimeClient
import budgetmate.shared.data.network.DashboardRealtimeClient
import budgetmate.shared.data.network.NetworkEndpointResolver
import budgetmate.shared.data.repository.BudgetMateRepository
import budgetmate.shared.data.session.SessionManager
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

enum class AuthMode {
    LOGIN,
    REGISTER,
    RECOVER
}

data class AppUiState(
    val checkingSession: Boolean = true,
    val loading: Boolean = false,
    val authMode: AuthMode = AuthMode.LOGIN,
    val authPromptMessage: String? = null,
    val user: UserResponse? = null,
    val budgets: List<BudgetResponse> = emptyList(),
    val expenses: List<ExpenseResponse> = emptyList(),
    val users: List<UserResponse> = emptyList(),
    val profileImagePayload: ProfileImagePayload? = null,
    val selectedCurrency: String = "PHP",
    val supportedCurrencies: List<String> = listOf("PHP", "USD", "EUR", "JPY", "GBP", "AUD", "CAD", "SGD", "HKD"),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AppViewModel(
    private val repository: BudgetMateRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val DATA_CACHE_WINDOW_MS = 6_000L
        private const val PROFILE_IMAGE_CACHE_WINDOW_MS = 20_000L
        private const val DASHBOARD_PAGE_SIZE = 200
    }

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var dashboardRealtimeClient: DashboardRealtimeClient? = null
    private var reconnectJob: Job? = null
    private var subscribedUserId: Long? = null
    private var lastRealtimeRefreshAt: Long = 0L
    private var adminUsersRealtimeClient: AdminUsersRealtimeClient? = null
    private var adminUsersReconnectJob: Job? = null
    private var adminUsersRealtimeConnected: Boolean = false
    private val suppressDeletedUserIds = mutableSetOf<Long>()
    private var lastBudgetsLoadedAt: Long = 0L
    private var lastExpensesLoadedAt: Long = 0L
    private var lastUsersLoadedAt: Long = 0L
    private var lastProfileImageLoadedAt: Long = 0L

    init {
        restoreSession()
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    fun switchAuthMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(
            authMode = mode,
            errorMessage = null,
            successMessage = null
        )
    }

    fun clearAuthPrompt() {
        _uiState.value = _uiState.value.copy(authPromptMessage = null)
    }

    fun handleGoogleOAuthCallback(token: String?, error: String?) {
        viewModelScope.launch {
            if (!error.isNullOrBlank()) {
                switchAuthMode(AuthMode.LOGIN)
                setError(parseOAuthError(error))
                return@launch
            }

            if (token.isNullOrBlank()) {
                switchAuthMode(AuthMode.LOGIN)
                setError("Google login did not return a valid token. Please try again.")
                return@launch
            }

            setLoading(true)
            switchAuthMode(AuthMode.LOGIN)
            clearMessages()
            clearAuthPrompt()
            sessionManager.saveToken(token)
            fetchCurrentUser(
                onSuccessMessage = "Welcome back",
                onFailureMessage = "Google login failed. Please try again."
            )
            setLoading(false)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                setError("Please fill up all fields.")
                return@launch
            }

            setLoading(true)
            clearMessages()
            repository.login(email, password)
                .onSuccess { auth ->
                    sessionManager.saveToken(auth.accessToken)
                    fetchCurrentUser(onSuccessMessage = "Welcome back, ${auth.name}")
                }
                .onFailure { error ->
                    setError(error.message ?: "Invalid email or password")
                }
            setLoading(false)
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            if (name.isBlank() || email.isBlank() || password.isBlank()) {
                setError("Please fill up all fields.")
                return@launch
            }

            setLoading(true)
            clearMessages()
            repository.register(name, email, password)
                .onSuccess {
                    setSuccess("Registration successful redirecting to login screen")
                    delay(1_500)
                    sessionManager.clearToken()
                    _uiState.value = _uiState.value.copy(
                        authMode = AuthMode.LOGIN,
                        authPromptMessage = "Account created! Please sign in.",
                        successMessage = null,
                        errorMessage = null
                    )
                }
                .onFailure { error ->
                    setError(error.message ?: "Registration failed. Please try again.")
                }
            setLoading(false)
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            setLoading(true)
            repository.forgotPassword(email)
                .onSuccess { message -> setSuccess(message) }
                .onFailure { error -> setError(error.message ?: "Unable to process forgot password") }
            setLoading(false)
        }
    }

    fun resetPassword(token: String, newPassword: String) {
        viewModelScope.launch {
            setLoading(true)
            repository.resetPassword(token, newPassword)
                .onSuccess { message -> setSuccess(message) }
                .onFailure { error -> setError(error.message ?: "Unable to reset password") }
            setLoading(false)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            disconnectRealtimeDashboard()
            disconnectAdminUsersRealtime()
            sessionManager.clearToken()
            _uiState.value = AppUiState(checkingSession = false)
        }
    }

    fun refreshAll(force: Boolean = false) {
        loadBudgets(force)
        loadExpenses(force)
        if (isAdmin()) {
            loadUsers(force)
        }
    }

    fun refreshCurrentUser() {
        viewModelScope.launch {
            repository.me()
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(user = user)
                    if (isAdminUser(user)) {
                        connectAdminUsersRealtime()
                    } else {
                        disconnectAdminUsersRealtime()
                    }
                }
                .onFailure { error -> setError(error.message ?: "Unable to refresh user profile") }
        }
    }

    fun updateMyName(name: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                setError("Name is required")
                return@launch
            }

            setLoading(true)
            repository.updateMyName(name.trim())
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(user = user)
                    setSuccess("Display name updated")
                }
                .onFailure { error -> setError(error.message ?: "Unable to update display name") }
            setLoading(false)
        }
    }

    fun changeMyPassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            if (currentPassword.isBlank() || newPassword.isBlank()) {
                setError("Current password and new password are required")
                return@launch
            }

            setLoading(true)
            repository.changeMyPassword(currentPassword, newPassword)
                .onSuccess { setSuccess("Password updated") }
                .onFailure { error -> setError(error.message ?: "Unable to update password") }
            setLoading(false)
        }
    }

    fun uploadMyProfileImage(filePart: MultipartBody.Part) {
        viewModelScope.launch {
            setLoading(true)
            repository.uploadMyProfileImage(filePart)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(user = user)
                    setSuccess("Profile picture uploaded")
                    loadProfileImage(force = true)
                }
                .onFailure { error -> setError(error.message ?: "Unable to upload profile picture") }
            setLoading(false)
        }
    }

    fun loadProfileImage(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force &&
            _uiState.value.profileImagePayload != null &&
            now - lastProfileImageLoadedAt < PROFILE_IMAGE_CACHE_WINDOW_MS
        ) {
            return
        }

        viewModelScope.launch {
            repository.downloadMyProfileImage()
                .onSuccess { payload ->
                    lastProfileImageLoadedAt = System.currentTimeMillis()
                    _uiState.value = _uiState.value.copy(profileImagePayload = payload)
                }
                .onFailure {
                    // Keep the last image payload to avoid flicker/disappearing avatar on transient failures.
                }
        }
    }

    fun clearProfileImage() {
        lastProfileImageLoadedAt = 0L
        _uiState.value = _uiState.value.copy(profileImagePayload = null)
    }

    fun setDisplayCurrency(currency: String) {
        val normalized = currency.uppercase()
        if (!_uiState.value.supportedCurrencies.contains(normalized)) {
            return
        }
        _uiState.value = _uiState.value.copy(selectedCurrency = normalized)
    }

    fun loadBudgets(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastBudgetsLoadedAt < DATA_CACHE_WINDOW_MS) {
            return
        }

        viewModelScope.launch {
            repository.getBudgets(size = DASHBOARD_PAGE_SIZE)
                .onSuccess { budgets ->
                    lastBudgetsLoadedAt = System.currentTimeMillis()
                    _uiState.value = _uiState.value.copy(budgets = budgets)
                }
                .onFailure { error -> setError(error.message ?: "Unable to load budgets") }
        }
    }

    fun createBudget(request: BudgetRequest) {
        viewModelScope.launch {
            setLoading(true)
            repository.createBudget(request)
                .onSuccess { createdBudget ->
                    lastBudgetsLoadedAt = System.currentTimeMillis()
                    val nextBudgets = listOf(createdBudget) + _uiState.value.budgets.filterNot { it.id == createdBudget.id }
                    _uiState.value = _uiState.value.copy(budgets = nextBudgets)
                    setSuccess("Budget created")
                    loadBudgets(force = true)
                }
                .onFailure { error -> setError(error.message ?: "Unable to create budget") }
            setLoading(false)
        }
    }

    fun updateBudget(id: Long, request: BudgetRequest) {
        viewModelScope.launch {
            setLoading(true)
            repository.updateBudget(id, request)
                .onSuccess {
                    setSuccess("Budget updated")
                    loadBudgets(force = true)
                }
                .onFailure { error -> setError(error.message ?: "Unable to update budget") }
            setLoading(false)
        }
    }

    fun deleteBudget(id: Long, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteBudget(id)
                .onSuccess {
                    setSuccess("Budget and linked expenses deleted")
                    loadBudgets(force = true)
                    loadExpenses(force = true)
                    onComplete(true)
                }
                .onFailure { error ->
                    setError(error.message ?: "Unable to delete budget")
                    onComplete(false)
                }
        }
    }

    fun loadExpenses(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastExpensesLoadedAt < DATA_CACHE_WINDOW_MS) {
            return
        }

        viewModelScope.launch {
            repository.getExpenses(size = DASHBOARD_PAGE_SIZE)
                .onSuccess { expenses ->
                    lastExpensesLoadedAt = System.currentTimeMillis()
                    _uiState.value = _uiState.value.copy(expenses = expenses)
                }
                .onFailure { error -> setError(error.message ?: "Unable to load expenses") }
        }
    }

    fun createExpense(request: ExpenseRequest) {
        viewModelScope.launch {
            setLoading(true)
            repository.createExpense(request)
                .onSuccess { createdExpense ->
                    lastExpensesLoadedAt = System.currentTimeMillis()
                    val nextExpenses = listOf(createdExpense) + _uiState.value.expenses.filterNot { it.id == createdExpense.id }
                    _uiState.value = _uiState.value.copy(expenses = nextExpenses)
                    setSuccess("Expense created")
                    loadExpenses(force = true)
                    loadBudgets(force = true)
                }
                .onFailure { error -> setError(error.message ?: "Unable to create expense") }
            setLoading(false)
        }
    }

    fun updateExpense(id: Long, request: ExpenseRequest) {
        viewModelScope.launch {
            setLoading(true)
            repository.updateExpense(id, request)
                .onSuccess {
                    setSuccess("Expense updated")
                    loadExpenses(force = true)
                    loadBudgets(force = true)
                }
                .onFailure { error -> setError(error.message ?: "Unable to update expense") }
            setLoading(false)
        }
    }

    fun deleteExpense(id: Long, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteExpense(id)
                .onSuccess {
                    setSuccess("Expense deleted")
                    loadExpenses(force = true)
                    loadBudgets(force = true)
                    onComplete(true)
                }
                .onFailure { error ->
                    setError(error.message ?: "Unable to delete expense")
                    onComplete(false)
                }
        }
    }

    fun loadUsers(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUsersLoadedAt < DATA_CACHE_WINDOW_MS) {
            return
        }

        viewModelScope.launch {
            repository.getUsers()
                .onSuccess { users ->
                    lastUsersLoadedAt = System.currentTimeMillis()
                    val filteredUsers = users.filterNot { suppressDeletedUserIds.contains(it.id) }
                    _uiState.value = _uiState.value.copy(users = filteredUsers)
                }
                .onFailure { error -> setError(error.message ?: "Unable to load users") }
        }
    }

    fun deleteUser(id: Long, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val previousUsers = _uiState.value.users
            suppressDeletedUserIds.add(id)
            _uiState.value = _uiState.value.copy(
                users = previousUsers.filterNot { it.id == id }
            )

            repository.deleteUser(id)
                .onSuccess {
                    setSuccess("User deleted")
                    refreshUsersUntilDeleted(id)
                    onComplete(true)
                }
                .onFailure { error ->
                    suppressDeletedUserIds.remove(id)
                    _uiState.value = _uiState.value.copy(users = previousUsers)
                    setError(error.message ?: "Unable to delete user")
                    onComplete(false)
                }
        }
    }

    private suspend fun refreshUsersUntilDeleted(deletedUserId: Long) {
        repeat(6) { attempt ->
            repository.getUsers()
                .onSuccess { users ->
                        lastUsersLoadedAt = System.currentTimeMillis()
                    val stillPresent = users.any { it.id == deletedUserId }
                    val filteredUsers = users.filterNot { suppressDeletedUserIds.contains(it.id) }
                    _uiState.value = _uiState.value.copy(users = filteredUsers)
                    if (!stillPresent) {
                        suppressDeletedUserIds.remove(deletedUserId)
                        return
                    }
                }

            if (attempt < 5) {
                delay(400)
            }
        }
    }

    fun isAdmin(): Boolean {
        val roles = _uiState.value.user?.roles ?: emptySet()
        return roles.any { it == "ROLE_ADMIN" || it == "ADMIN" }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val token = sessionManager.getToken()
            if (token.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(checkingSession = false)
                return@launch
            }

            fetchCurrentUser()
        }
    }

    private suspend fun fetchCurrentUser(
        onSuccessMessage: String? = null,
        onFailureMessage: String? = null
    ) {
        repository.me()
            .onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    checkingSession = false,
                    user = user,
                    successMessage = onSuccessMessage
                )
                connectRealtimeDashboard(user.id)
                if (isAdminUser(user)) {
                    connectAdminUsersRealtime()
                } else {
                    disconnectAdminUsersRealtime()
                }
                loadProfileImage()
                refreshAll()
            }
            .onFailure { _ ->
                disconnectRealtimeDashboard()
                disconnectAdminUsersRealtime()
                sessionManager.clearToken()
                _uiState.value = AppUiState(checkingSession = false)
                if (!onFailureMessage.isNullOrBlank()) {
                    switchAuthMode(AuthMode.LOGIN)
                    setError(onFailureMessage)
                }
            }
    }

    private fun parseOAuthError(rawError: String): String {
        val decodedOnce = decodeSafe(rawError)
        val decodedTwice = decodeSafe(decodedOnce)
        val cleaned = decodedTwice.replace('+', ' ').trim()
        return if (cleaned.isBlank()) {
            "Google login failed. Please try again."
        } else {
            cleaned
        }
    }

    private fun decodeSafe(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            value
        }
    }

    private fun connectRealtimeDashboard(userId: Long) {
        if (subscribedUserId == userId && dashboardRealtimeClient != null) {
            return
        }

        disconnectRealtimeDashboard()
        subscribedUserId = userId
        dashboardRealtimeClient = DashboardRealtimeClient(
            websocketUrl = NetworkEndpointResolver.wsBaseUrl,
            onDashboardChanged = { triggerRealtimeRefresh() },
            onConnectionLost = { scheduleReconnect(userId) }
        )
        dashboardRealtimeClient?.connect(userId)
    }

    private fun triggerRealtimeRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastRealtimeRefreshAt < 700) {
            return
        }
        lastRealtimeRefreshAt = now
        refreshAll()
    }

    private fun scheduleReconnect(userId: Long) {
        if (reconnectJob?.isActive == true) {
            return
        }

        reconnectJob = viewModelScope.launch {
            delay(2_500)
            if (_uiState.value.user?.id == userId) {
                dashboardRealtimeClient?.connect(userId)
            }
        }
    }

    private fun disconnectRealtimeDashboard() {
        reconnectJob?.cancel()
        reconnectJob = null
        subscribedUserId = null
        dashboardRealtimeClient?.disconnect()
        dashboardRealtimeClient = null
    }

    private fun connectAdminUsersRealtime() {
        if (adminUsersRealtimeConnected && adminUsersRealtimeClient != null) {
            return
        }

        disconnectAdminUsersRealtime()
        adminUsersRealtimeConnected = true
        adminUsersRealtimeClient = AdminUsersRealtimeClient(
            websocketUrl = NetworkEndpointResolver.wsBaseUrl,
            onUsersChanged = { loadUsers() },
            onConnectionLost = { scheduleAdminUsersReconnect() }
        )
        adminUsersRealtimeClient?.connect()
    }

    private fun scheduleAdminUsersReconnect() {
        if (adminUsersReconnectJob?.isActive == true) {
            return
        }

        adminUsersReconnectJob = viewModelScope.launch {
            delay(2_500)
            if (isAdmin()) {
                adminUsersRealtimeClient?.connect()
            }
        }
    }

    private fun disconnectAdminUsersRealtime() {
        adminUsersReconnectJob?.cancel()
        adminUsersReconnectJob = null
        adminUsersRealtimeConnected = false
        adminUsersRealtimeClient?.disconnect()
        adminUsersRealtimeClient = null
    }

    private fun isAdminUser(user: UserResponse): Boolean {
        return user.roles.any { it == "ROLE_ADMIN" || it == "ADMIN" }
    }

    override fun onCleared() {
        disconnectRealtimeDashboard()
        disconnectAdminUsersRealtime()
        super.onCleared()
    }

    private fun setLoading(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(loading = isLoading)
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message, successMessage = null)
    }

    private fun setSuccess(message: String) {
        _uiState.value = _uiState.value.copy(successMessage = message, errorMessage = null)
    }
}

class AppViewModelFactory(
    private val repository: BudgetMateRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
