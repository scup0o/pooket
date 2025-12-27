package com.project.pooket.data.local.setting

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.project.pooket.core.theme.AppThemeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("display_settings")

@Singleton
class DisplaySettingsRepository @Inject constructor(
    @ApplicationContext context: Context
){
    private val dataStore = context.dataStore
    private object Keys{
        val THEME = stringPreferencesKey("theme")
        val NL_ENABLED = booleanPreferencesKey("nl_enabled")
        val NL_WARMTH = floatPreferencesKey("nl_warmth")
        val NL_DIMMING = floatPreferencesKey("nl_dimming")
    }

    //theme
    val themeFlow : Flow<AppThemeConfig> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val value = prefs[Keys.THEME] ?: AppThemeConfig.FOLLOW_SYSTEM.name
            try {
                AppThemeConfig.valueOf(value)
            }
            catch (e: Exception){
                AppThemeConfig.FOLLOW_SYSTEM
            }
        }

    suspend fun setTheme(theme: AppThemeConfig){
        dataStore.edit { prefs -> prefs[Keys.THEME] = theme.name }
    }

    //night-light
    data class NightLightConfig(
        val isEnabled : Boolean = false,
        val warmth: Float = 0.5f,
        val dimming: Float = 0.0f,
    )

    val nightLightFlow : Flow<NightLightConfig> = dataStore.data
        .catch { if(it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            NightLightConfig(
                isEnabled = prefs[Keys.NL_ENABLED] ?: false,
                warmth = prefs[Keys.NL_WARMTH]?: 0.5f,
                dimming = prefs[Keys.NL_DIMMING]?: 0.0f
            )
        }

    suspend fun updateNightLight(isEnabled: Boolean? = null, warmth: Float? = null, dimming: Float? = null){
        dataStore.edit { prefs->
            isEnabled?.let { prefs[Keys.NL_ENABLED] = it }
            warmth?.let { prefs[Keys.NL_WARMTH] = it }
            dimming?.let { prefs[Keys.NL_DIMMING] = it }
        }
    }
}