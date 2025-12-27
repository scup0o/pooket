package com.project.pooket.ui.features.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.project.pooket.core.navigation.FloatingMenuButton
import com.project.pooket.core.theme.AppThemeConfig
import com.project.pooket.data.local.setting.DisplaySettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingMainScreen(
    nightLightConfig: DisplaySettingsRepository.NightLightConfig,
    isDarkTheme: Boolean,
    onThemeChange: (AppThemeConfig) -> Unit,
    onToggleNightLight: () -> Unit,
    onWarmthChange: (Float) -> Unit,
    onDimmingChange: (Float) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Setting") }) }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = innerPadding.calculateEndPadding(LayoutDirection.Ltr)
            )){
            Column() {
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onThemeChange(if (isDarkTheme) AppThemeConfig.LIGHT else AppThemeConfig.DARK) })

                Switch(
                    checked = nightLightConfig.isEnabled,
                    onCheckedChange = { onToggleNightLight() },
                )
                AnimatedVisibility(
                    visible = nightLightConfig.isEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Text("Warmth")
                        Slider(value = nightLightConfig.warmth, onValueChange = { onWarmthChange(it) })

                        Text("Dimming")
                        Slider(
                            value = nightLightConfig.dimming,
                            onValueChange = { onDimmingChange(it) })

                    }
                }
            }
            FloatingMenuButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .padding(top = 32.dp)
            )
        }


    }
}