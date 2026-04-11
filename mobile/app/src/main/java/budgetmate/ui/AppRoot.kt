package budgetmate.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import budgetmate.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MainTab(val label: String) {
    DASHBOARD("Dashboard"),
    EXPENSES("Expenses"),
    BUDGETS("Budgets"),
    EXCHANGE("Exchange"),
    ADMIN("Admin")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        if (uiState.user != null) {
            uiState.errorMessage?.let { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
                viewModel.clearMessages()
            }
            uiState.successMessage?.let { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
                viewModel.clearMessages()
            }
        }

        if (uiState.user == null && (uiState.errorMessage != null || uiState.successMessage != null)) {
            delay(2_000)
            viewModel.clearMessages()
        }
    }

    if (uiState.checkingSession) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.user == null) {
        AuthScreen(
            viewModel = viewModel,
            uiState = uiState,
            isLoading = uiState.loading,
            snackbarHostState = snackbarHostState
        )
        return
    }

    var selectedTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    val availableTabs = if (viewModel.isAdmin()) {
        MainTab.entries
    } else {
        MainTab.entries.filter { it != MainTab.ADMIN }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.DASHBOARD) {
            viewModel.refreshAll()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("BudgetMate") },
                actions = {
                    TextButton(onClick = { viewModel.logout() }) {
                        Text("Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                availableTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        icon = {}
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        when (selectedTab) {
            MainTab.DASHBOARD -> DashboardScreen(uiState = uiState, modifier = Modifier.padding(padding))
            MainTab.EXPENSES -> ExpensesScreen(viewModel = viewModel, uiState = uiState, modifier = Modifier.padding(padding))
            MainTab.BUDGETS -> BudgetsScreen(viewModel = viewModel, uiState = uiState, modifier = Modifier.padding(padding))
            MainTab.EXCHANGE -> ExchangeScreen(viewModel = viewModel, uiState = uiState, modifier = Modifier.padding(padding))
            MainTab.ADMIN -> AdminScreen(viewModel = viewModel, uiState = uiState, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun AuthScreen(
    viewModel: AppViewModel,
    uiState: AppUiState,
    isLoading: Boolean,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Welcome to BudgetMate", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        when (uiState.authMode) {
                            AuthMode.LOGIN -> "Welcome back"
                            AuthMode.REGISTER -> "Create your account"
                            AuthMode.RECOVER -> "Recover your account"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    uiState.authPromptMessage?.let { prompt ->
                        Text(
                            text = prompt,
                            color = Color(0xFF1B5E20),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    uiState.successMessage?.let { success ->
                        Text(
                            text = success,
                            color = Color(0xFF1B5E20),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    uiState.errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    when (uiState.authMode) {
                        AuthMode.LOGIN -> LoginPanel(viewModel, isLoading)
                        AuthMode.REGISTER -> RegisterPanel(viewModel, isLoading)
                        AuthMode.RECOVER -> RecoverPanel(viewModel, isLoading)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginPanel(viewModel: AppViewModel, isLoading: Boolean) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                viewModel.clearAuthPrompt()
            },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearAuthPrompt()
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.login(email, password) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (isLoading) "Signing in..." else "Sign In")
        }
        OutlinedButton(
            onClick = {
                viewModel.clearMessages()
                viewModel.clearAuthPrompt()
                val googleAuthIntent = Intent(Intent.ACTION_VIEW, Uri.parse(buildGoogleAuthUrl()))
                context.startActivity(googleAuthIntent)
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Continue with Google")
        }
        TextButton(
            onClick = { viewModel.switchAuthMode(AuthMode.RECOVER) },
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Forgot password?")
        }
        TextButton(
            onClick = { viewModel.switchAuthMode(AuthMode.REGISTER) },
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Don't have an account? Sign up")
        }
    }
}

private fun buildGoogleAuthUrl(): String {
    val apiBase = BuildConfig.API_BASE_URL.trimEnd('/')
    val serverBase = if (apiBase.endsWith("/api/v1")) {
        apiBase.removeSuffix("/api/v1")
    } else {
        apiBase
    }

    return "$serverBase/oauth2/authorization/google"
}

@Composable
private fun RegisterPanel(viewModel: AppViewModel, isLoading: Boolean) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Must include uppercase, lowercase, number, and special character.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = { viewModel.register(name, email, password) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (isLoading) "Creating account..." else "Create Account")
        }
        TextButton(
            onClick = { viewModel.switchAuthMode(AuthMode.LOGIN) },
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Already have an account? Sign in")
        }
    }
}

@Composable
private fun RecoverPanel(viewModel: AppViewModel, isLoading: Boolean) {
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { viewModel.forgotPassword(email) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Send Reset Email")
        }
        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Reset Token") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.resetPassword(token, newPassword) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Reset Password")
        }
        TextButton(
            onClick = { viewModel.switchAuthMode(AuthMode.LOGIN) },
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Back to Sign in")
        }
    }
}

@Composable
private fun DashboardScreen(uiState: AppUiState, modifier: Modifier = Modifier) {
    val totalSpent = uiState.expenses.sumOf { it.amount }
    val totalBudget = uiState.budgets.sumOf { it.limitAmount }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SummaryCard(title = "Total Budget", value = totalBudget)
        }
        item {
            SummaryCard(title = "Total Spent", value = totalSpent)
        }
        item {
            SummaryCard(title = "Balance", value = totalBudget - totalSpent)
        }
        item {
            Text("Recent Expenses", style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.expenses.take(8)) { expense ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(expense.title, style = MaterialTheme.typography.titleSmall)
                    Text("${expense.category} - ${expense.currency} ${expense.amount}")
                    Text(expense.expenseDate)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(String.format("%.2f", value), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ExpensesScreen(viewModel: AppViewModel, uiState: AppUiState, modifier: Modifier = Modifier) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("PHP") }
    var category by remember { mutableStateOf("Other") }
    var expenseDate by remember { mutableStateOf("2026-01-01") }
    var description by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Add Expense", style = MaterialTheme.typography.titleMedium)
        }
        item {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Currency") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = expenseDate, onValueChange = { expenseDate = it }, label = { Text("Expense Date YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                    viewModel.createExpense(
                        budgetmate.data.model.ExpenseRequest(
                            title = title,
                            description = description,
                            amount = parsedAmount,
                            currency = currency,
                            category = category,
                            expenseDate = expenseDate,
                            isRecurring = false
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Create Expense")
            }
        }
        item {
            Text("Expenses", style = MaterialTheme.typography.titleMedium)
        }

        items(uiState.expenses) { expense ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(expense.title, style = MaterialTheme.typography.titleSmall)
                        Text("${expense.currency} ${expense.amount}")
                        Text(expense.category)
                    }
                    TextButton(onClick = { viewModel.deleteExpense(expense.id) }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsScreen(viewModel: AppViewModel, uiState: AppUiState, modifier: Modifier = Modifier) {
    var category by remember { mutableStateOf("Other") }
    var limitAmount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("PHP") }
    var startDate by remember { mutableStateOf("2026-01-01") }
    var endDate by remember { mutableStateOf("2026-12-31") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Add Budget", style = MaterialTheme.typography.titleMedium)
        }
        item {
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = limitAmount, onValueChange = { limitAmount = it }, label = { Text("Limit Amount") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Currency") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("Start Date YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("End Date YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(
                onClick = {
                    val parsedAmount = limitAmount.toDoubleOrNull() ?: 0.0
                    viewModel.createBudget(
                        budgetmate.data.model.BudgetRequest(
                            category = category,
                            limitAmount = parsedAmount,
                            currency = currency,
                            startDate = startDate,
                            endDate = endDate,
                            notes = notes
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Create Budget")
            }
        }

        items(uiState.budgets) { budget ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(budget.category, style = MaterialTheme.typography.titleSmall)
                        Text("${budget.currency} ${budget.limitAmount}")
                        Text("Spent: ${budget.spentAmount}")
                    }
                    TextButton(onClick = { viewModel.deleteBudget(budget.id) }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExchangeScreen(viewModel: AppViewModel, uiState: AppUiState, modifier: Modifier = Modifier) {
    var amount by remember { mutableStateOf("1") }
    var from by remember { mutableStateOf("PHP") }
    var to by remember { mutableStateOf("USD") }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Convert Currency", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = from, onValueChange = { from = it }, label = { Text("From") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("To") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(2.dp))
        Button(
            onClick = {
                viewModel.convertCurrency(amount.toDoubleOrNull() ?: 0.0, from, to)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Convert")
        }
        uiState.exchangeResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Result: $result",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AdminScreen(viewModel: AppViewModel, uiState: AppUiState, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        viewModel.loadUsers()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Admin: Users", style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.users) { user ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(user.name, style = MaterialTheme.typography.titleSmall)
                        Text(user.email)
                    }
                    if (user.id != uiState.user?.id) {
                        TextButton(onClick = { viewModel.deleteUser(user.id) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
