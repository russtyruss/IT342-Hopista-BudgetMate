package budgetmate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import budgetmate.data.model.BudgetRequest
import budgetmate.data.model.BudgetResponse
import budgetmate.data.model.ExpenseRequest
import budgetmate.data.model.ExpenseResponse
import budgetmate.data.model.UserResponse
import budgetmate.data.repository.BudgetMateRepository
import budgetmate.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val exchangeResult: Double? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AppViewModel(
    private val repository: BudgetMateRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

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
                setError(error)
                return@launch
            }

            if (token.isNullOrBlank()) {
                return@launch
            }

            setLoading(true)
            switchAuthMode(AuthMode.LOGIN)
            clearMessages()
            clearAuthPrompt()
            sessionManager.saveToken(token)
            fetchCurrentUser(onSuccessMessage = "Welcome back")
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
                    setSuccess("Login successful! Redirecting...")
                    delay(1_000)
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
                    setSuccess("Account created successfully! Redirecting to login...")
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
            sessionManager.clearToken()
            _uiState.value = AppUiState(checkingSession = false)
        }
    }

    fun refreshAll() {
        loadBudgets()
        loadExpenses()
        if (isAdmin()) {
            loadUsers()
        }
    }

    fun loadBudgets() {
        viewModelScope.launch {
            repository.getBudgets()
                .onSuccess { budgets ->
                    _uiState.value = _uiState.value.copy(budgets = budgets)
                }
                .onFailure { error -> setError(error.message ?: "Unable to load budgets") }
        }
    }

    fun createBudget(request: BudgetRequest) {
        viewModelScope.launch {
            setLoading(true)
            repository.createBudget(request)
                .onSuccess {
                    setSuccess("Budget created")
                    loadBudgets()
                }
                .onFailure { error -> setError(error.message ?: "Unable to create budget") }
            setLoading(false)
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            repository.deleteBudget(id)
                .onSuccess {
                    setSuccess("Budget deleted")
                    loadBudgets()
                }
                .onFailure { error -> setError(error.message ?: "Unable to delete budget") }
        }
    }

    fun loadExpenses() {
        viewModelScope.launch {
            repository.getExpenses()
                .onSuccess { expenses ->
                    _uiState.value = _uiState.value.copy(expenses = expenses)
                }
                .onFailure { error -> setError(error.message ?: "Unable to load expenses") }
        }
    }

    fun createExpense(request: ExpenseRequest) {
        viewModelScope.launch {
            setLoading(true)
            repository.createExpense(request)
                .onSuccess {
                    setSuccess("Expense created")
                    loadExpenses()
                    loadBudgets()
                }
                .onFailure { error -> setError(error.message ?: "Unable to create expense") }
            setLoading(false)
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpense(id)
                .onSuccess {
                    setSuccess("Expense deleted")
                    loadExpenses()
                    loadBudgets()
                }
                .onFailure { error -> setError(error.message ?: "Unable to delete expense") }
        }
    }

    fun convertCurrency(amount: Double, from: String, to: String) {
        viewModelScope.launch {
            setLoading(true)
            repository.convertCurrency(amount, from, to)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(exchangeResult = response.conversionResult)
                }
                .onFailure { error -> setError(error.message ?: "Unable to convert currency") }
            setLoading(false)
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            repository.getUsers()
                .onSuccess { users ->
                    _uiState.value = _uiState.value.copy(users = users)
                }
                .onFailure { error -> setError(error.message ?: "Unable to load users") }
        }
    }

    fun deleteUser(id: Long) {
        viewModelScope.launch {
            repository.deleteUser(id)
                .onSuccess {
                    setSuccess("User deleted")
                    loadUsers()
                }
                .onFailure { error -> setError(error.message ?: "Unable to delete user") }
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

    private suspend fun fetchCurrentUser(onSuccessMessage: String? = null) {
        repository.me()
            .onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    checkingSession = false,
                    user = user,
                    successMessage = onSuccessMessage
                )
                refreshAll()
            }
            .onFailure { _ ->
                sessionManager.clearToken()
                _uiState.value = AppUiState(checkingSession = false)
            }
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
