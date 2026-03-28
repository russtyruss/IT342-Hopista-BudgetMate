package budgetmate.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "budgetmate_session")

class SessionManager(private val context: Context) {

    private val tokenKey = stringPreferencesKey("jwt_token")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences: Preferences ->
        preferences[tokenKey]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(tokenKey)
        }
    }

    suspend fun getToken(): String? = tokenFlow.first()
}
