package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nersika_preferences")

data class PersistedSession(
    val userId: String,
    val sessionTokenHash: String,
    val tokenVersion: Int,
    val expiresAtMillis: Long
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val SESSION_TOKEN_HASH = stringPreferencesKey("session_token_hash")
        val SESSION_TOKEN_VERSION = intPreferencesKey("session_token_version")
        val SESSION_EXPIRES_AT = longPreferencesKey("session_expires_at")
    }

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[Keys.LANGUAGE] ?: AppLanguage.EN.code)
    }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    val persistedSessionFlow: Flow<PersistedSession?> = context.dataStore.data.map { prefs ->
        val uid = prefs[Keys.ACTIVE_USER_ID]
        val hash = prefs[Keys.SESSION_TOKEN_HASH]
        val version = prefs[Keys.SESSION_TOKEN_VERSION] ?: -1
        val expiresAt = prefs[Keys.SESSION_EXPIRES_AT] ?: 0L
        if (uid.isNullOrBlank() || hash.isNullOrBlank() || version < 1 || expiresAt <= 0L) {
            null
        } else {
            PersistedSession(
                userId = uid,
                sessionTokenHash = hash,
                tokenVersion = version,
                expiresAtMillis = expiresAt
            )
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun saveSession(session: PersistedSession) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_USER_ID] = session.userId
            prefs[Keys.SESSION_TOKEN_HASH] = session.sessionTokenHash
            prefs[Keys.SESSION_TOKEN_VERSION] = session.tokenVersion
            prefs[Keys.SESSION_EXPIRES_AT] = session.expiresAtMillis
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_USER_ID)
            prefs.remove(Keys.SESSION_TOKEN_HASH)
            prefs.remove(Keys.SESSION_TOKEN_VERSION)
            prefs.remove(Keys.SESSION_EXPIRES_AT)
        }
    }
}
