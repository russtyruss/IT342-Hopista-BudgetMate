package budgetmate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import budgetmate.data.network.ApiClient
import budgetmate.data.repository.BudgetMateRepository
import budgetmate.data.session.SessionManager
import budgetmate.ui.AppRoot
import budgetmate.ui.AppViewModel
import budgetmate.ui.AppViewModelFactory
import budgetmate.ui.theme.BudgetMateTheme

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels {
        val sessionManager = SessionManager(applicationContext)
        val api = ApiClient.create(sessionManager)
        val repository = BudgetMateRepository(api)
        AppViewModelFactory(repository, sessionManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BudgetMateTheme {
                AppRoot(viewModel = appViewModel)
            }
        }

        handleOAuthCallbackIntent(intent)
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
}
