package com.project.pooket.core

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.pooket.core.theme.AppThemeConfig
import com.project.pooket.data.local.setting.DisplaySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalViewModel @Inject constructor(
    private val displaySettingsRepo: DisplaySettingsRepository
) : ViewModel() {
    val appTheme = displaySettingsRepo.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeConfig.FOLLOW_SYSTEM)
    val nightLightState = displaySettingsRepo.nightLightFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DisplaySettingsRepository.NightLightConfig())

    fun setTheme(theme: AppThemeConfig){
        viewModelScope.launch {
            displaySettingsRepo.setTheme(theme)
        }
    }

    fun toggleNightLight() {
        val current = nightLightState.value.isEnabled
        Log.d("NightLight", "Toggling Night Light. Current: $current, New: ${!current}")

        viewModelScope.launch {
            try {
                displaySettingsRepo.updateNightLight(isEnabled = !current)
                Log.d("NightLight", "Save Success")
            } catch (e: Exception) {
                Log.e("NightLight", "Save Failed", e)
            }
        }
    }

    fun updateWarmth(warmIntensity: Float){
        viewModelScope.launch {
            displaySettingsRepo.updateNightLight(warmth = warmIntensity)
        }
    }

    fun updateDimming(value: Float){
        viewModelScope.launch { displaySettingsRepo.updateNightLight(dimming = value) }
    }
}