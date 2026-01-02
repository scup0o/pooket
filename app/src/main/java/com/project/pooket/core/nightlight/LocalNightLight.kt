package com.project.pooket.core.nightlight

import androidx.compose.runtime.compositionLocalOf
import com.project.pooket.data.local.setting.DisplaySettingsRepository

val LocalNightLightConfig = compositionLocalOf {
    DisplaySettingsRepository.NightLightConfig()}