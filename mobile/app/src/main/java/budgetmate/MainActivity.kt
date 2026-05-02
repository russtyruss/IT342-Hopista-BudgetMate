package budgetmate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomnavigation.BottomNavigationView
import budgetmate.data.network.ApiClient
import budgetmate.data.network.NetworkEndpointResolver
import budgetmate.data.repository.BudgetMateRepository
import budgetmate.data.session.SessionManager
import budgetmate.ui.AdminFragment
import budgetmate.ui.AuthMode
import budgetmate.ui.AppViewModel
import budgetmate.ui.AppViewModelFactory
import budgetmate.ui.BudgetsFragment
import budgetmate.ui.CurrencyUi
import budgetmate.ui.DashboardFragment
import budgetmate.ui.ExpensesFragment
import budgetmate.ui.ProfileFragment
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

class MainActivity : AppCompatActivity() {

    val appViewModel: AppViewModel by viewModels {
        val sessionManager = SessionManager(applicationContext)
        val api = ApiClient.create(sessionManager)
        val repository = BudgetMateRepository(api)
        AppViewModelFactory(repository, sessionManager)
    }

    private var selectedNavId: Int = R.id.nav_dashboard
    private var isUpdatingCurrencySpinner = false
    private var currencyAdapter: ArrayAdapter<String>? = null
    private var lastSupportedCurrencies: List<String> = emptyList()
    private var lastUserId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupAuthActions()
        setupBottomNavigation()
        setupCurrencySpinner()
        observeUiState()

        handleOAuthCallbackIntent(intent)
    }

    private fun setupCurrencySpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerCurrency)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (isUpdatingCurrencySpinner) {
                    return
                }
                val selected = parent?.getItemAtPosition(position)?.toString().orEmpty()
                if (selected.isNotBlank()) {
                    appViewModel.setDisplayCurrency(CurrencyUi.codeFromDisplayLabel(selected))
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallbackIntent(intent)
    }

    private fun handleOAuthCallbackIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "budgetmate" || data.host != "oauth2") {
            return
        }

        val token = data.getQueryParameter("token")
        val error = data.getQueryParameter("error")
        appViewModel.handleGoogleOAuthCallback(token = token, error = error)
    }

    private fun setupAuthActions() {
        findViewById<android.widget.Button>(R.id.btnLogin).setOnClickListener {
            val email = findViewById<android.widget.EditText>(R.id.etLoginEmail).text.toString()
            val password = findViewById<android.widget.EditText>(R.id.etLoginPassword).text.toString()
            appViewModel.login(email, password)
        }

        findViewById<android.widget.Button>(R.id.btnGoogle).setOnClickListener {
            appViewModel.clearMessages()
            appViewModel.clearAuthPrompt()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(buildGoogleAuthUrl()))
            startActivity(intent)
        }

        findViewById<android.widget.Button>(R.id.btnRegister).setOnClickListener {
            val name = findViewById<android.widget.EditText>(R.id.etRegisterName).text.toString()
            val email = findViewById<android.widget.EditText>(R.id.etRegisterEmail).text.toString()
            val password = findViewById<android.widget.EditText>(R.id.etRegisterPassword).text.toString()
            appViewModel.register(name, email, password)
        }

        findViewById<android.widget.Button>(R.id.btnSendResetLink).setOnClickListener {
            val email = findViewById<android.widget.EditText>(R.id.etRecoverEmail).text.toString()
            appViewModel.forgotPassword(email)
        }

        findViewById<android.widget.Button>(R.id.btnResetPassword).setOnClickListener {
            val token = findViewById<android.widget.EditText>(R.id.etResetToken).text.toString()
            val newPassword = findViewById<android.widget.EditText>(R.id.etNewPassword).text.toString()
            appViewModel.resetPassword(token, newPassword)
        }

        findViewById<android.widget.Button>(R.id.btnLogout).setOnClickListener {
            appViewModel.logout()
        }

        findViewById<android.widget.TextView>(R.id.btnToRegister).setOnClickListener {
            appViewModel.switchAuthMode(AuthMode.REGISTER)
        }
        findViewById<android.widget.TextView>(R.id.btnToRecover).setOnClickListener {
            appViewModel.switchAuthMode(AuthMode.RECOVER)
        }
        findViewById<android.widget.TextView>(R.id.btnBackToLoginFromRegister).setOnClickListener {
            appViewModel.switchAuthMode(AuthMode.LOGIN)
        }
        findViewById<android.widget.TextView>(R.id.btnBackToLoginFromRecover).setOnClickListener {
            appViewModel.switchAuthMode(AuthMode.LOGIN)
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            selectedNavId = item.itemId
            when (item.itemId) {
                R.id.nav_dashboard -> replaceMainFragment(DashboardFragment())
                R.id.nav_expenses -> replaceMainFragment(ExpensesFragment())
                R.id.nav_budgets -> replaceMainFragment(BudgetsFragment())
                R.id.nav_profile -> replaceMainFragment(ProfileFragment())
                R.id.nav_admin -> replaceMainFragment(AdminFragment())
            }
            true
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appViewModel.uiState.collect { state ->
                    val authContainer = findViewById<android.view.View>(R.id.authContainer)
                    val appContainer = findViewById<android.view.View>(R.id.appContainer)
                    val tvAuthMessage = findViewById<android.widget.TextView>(R.id.tvAuthMessage)
                    val spinnerCurrency = findViewById<Spinner>(R.id.spinnerCurrency)

                    authContainer.isVisible = state.user == null
                    appContainer.isVisible = state.user != null

                    showAuthSection(state.authMode)

                    val loginError = if (state.authMode == AuthMode.LOGIN) state.errorMessage.orEmpty() else ""
                    val loginPrompt = if (state.authMode == AuthMode.LOGIN) state.authPromptMessage.orEmpty() else ""
                    val authMessage = if (loginPrompt.isNotBlank()) loginPrompt else loginError
                    tvAuthMessage.isVisible = authMessage.isNotBlank()
                    tvAuthMessage.text = authMessage
                    val authMessageColor = if (loginPrompt.isNotBlank()) {
                        androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.bm_secondary)
                    } else {
                        androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.bm_error)
                    }
                    tvAuthMessage.setTextColor(authMessageColor)

                    if (loginPrompt.isNotBlank()) {
                        clearRegisterFields()
                        appViewModel.clearAuthPrompt()
                    }

                    if (lastUserId != null && state.user == null) {
                        clearLoginFields()
                    }
                    lastUserId = state.user?.id

                    val btnLogin = findViewById<android.widget.Button>(R.id.btnLogin)
                    val isSigningIn = state.loading && state.authMode == AuthMode.LOGIN
                    btnLogin.text = if (isSigningIn) "Signing in..." else "Sign In"
                    btnLogin.isEnabled = !isSigningIn

                    if (state.user != null) {
                        if (currencyAdapter == null || lastSupportedCurrencies != state.supportedCurrencies) {
                            val labels = state.supportedCurrencies.map { CurrencyUi.displayLabel(it) }
                            currencyAdapter = ArrayAdapter(
                                this@MainActivity,
                                R.layout.item_currency_spinner,
                                labels
                            ).also { adapter ->
                                adapter.setDropDownViewResource(R.layout.item_currency_dropdown)
                            }
                            spinnerCurrency.adapter = currencyAdapter
                            lastSupportedCurrencies = state.supportedCurrencies
                        }

                        if (spinnerCurrency.adapter !== currencyAdapter) {
                            spinnerCurrency.adapter = currencyAdapter
                        }

                        val selectedCurrencyIndex = state.supportedCurrencies.indexOf(state.selectedCurrency).coerceAtLeast(0)
                        if (spinnerCurrency.selectedItemPosition != selectedCurrencyIndex) {
                            isUpdatingCurrencySpinner = true
                            spinnerCurrency.setSelection(selectedCurrencyIndex, false)
                            isUpdatingCurrencySpinner = false
                        }

                        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
                        bottomNav.menu.findItem(R.id.nav_admin).isVisible = appViewModel.isAdmin()

                        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                            bottomNav.selectedItemId = selectedNavId
                            if (selectedNavId == R.id.nav_admin && !appViewModel.isAdmin()) {
                                selectedNavId = R.id.nav_dashboard
                                bottomNav.selectedItemId = R.id.nav_dashboard
                            }
                            replaceMainFragment(fragmentForNav(selectedNavId))
                        }
                    }
                }
            }
        }
    }

    private fun fragmentForNav(itemId: Int) = when (itemId) {
        R.id.nav_expenses -> ExpensesFragment()
        R.id.nav_budgets -> BudgetsFragment()
        R.id.nav_profile -> ProfileFragment()
        R.id.nav_admin -> AdminFragment()
        else -> DashboardFragment()
    }

    private fun showAuthSection(mode: AuthMode) {
        findViewById<android.view.View>(R.id.loginSection).isVisible = mode == AuthMode.LOGIN
        findViewById<android.view.View>(R.id.registerSection).isVisible = mode == AuthMode.REGISTER
        findViewById<android.view.View>(R.id.recoverSection).isVisible = mode == AuthMode.RECOVER
    }

    private fun clearRegisterFields() {
        findViewById<android.widget.EditText>(R.id.etRegisterName).setText("")
        findViewById<android.widget.EditText>(R.id.etRegisterEmail).setText("")
        findViewById<android.widget.EditText>(R.id.etRegisterPassword).setText("")
    }

    private fun clearLoginFields() {
        findViewById<android.widget.EditText>(R.id.etLoginEmail).setText("")
        findViewById<android.widget.EditText>(R.id.etLoginPassword).setText("")
    }

    private fun replaceMainFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun buildGoogleAuthUrl(): String {
        val apiBase = NetworkEndpointResolver.apiBaseUrl.trimEnd('/')
        val serverBase = if (apiBase.endsWith("/api/v1")) {
            apiBase.removeSuffix("/api/v1")
        } else {
            apiBase
        }

        return "$serverBase/oauth2/authorization/google"
    }
}
