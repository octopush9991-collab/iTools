package com.itools.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "itools_settings")

data class AppSettings(
    val theme: String = "dark",
    val keepScreenOn: Boolean = true,
    val showSafeArea: Boolean = true,
    val autoSave: Boolean = true,
    val reduceMotion: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val showSafeArea = booleanPreferencesKey("show_safe_area")
        val autoSave = booleanPreferencesKey("auto_save")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.theme] ?: "dark",
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            showSafeArea = prefs[Keys.showSafeArea] ?: true,
            autoSave = prefs[Keys.autoSave] ?: true,
            reduceMotion = prefs[Keys.reduceMotion] ?: false
        )
    }

    suspend fun setTheme(value: String) = edit { it[Keys.theme] = value }

    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }

    suspend fun setShowSafeArea(value: Boolean) = edit { it[Keys.showSafeArea] = value }

    suspend fun setAutoSave(value: Boolean) = edit { it[Keys.autoSave] = value }

    suspend fun setReduceMotion(value: Boolean) = edit { it[Keys.reduceMotion] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
