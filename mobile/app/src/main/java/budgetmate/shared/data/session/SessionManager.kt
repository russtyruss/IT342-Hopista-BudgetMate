package edu.cit.hopista.budgetmate.shared.data.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(private val context: Context) {

    private val tokenKey = "jwt_token"

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "budgetmate_secure_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun saveToken(token: String) {
        encryptedPrefs.edit().putString(tokenKey, token).apply()
    }

    suspend fun clearToken() {
        encryptedPrefs.edit().remove(tokenKey).apply()
    }

    suspend fun getToken(): String? = encryptedPrefs.getString(tokenKey, null)
}
