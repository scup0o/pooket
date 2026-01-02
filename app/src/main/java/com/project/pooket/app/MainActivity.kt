package com.project.pooket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.core.GlobalViewModel
import com.project.pooket.core.theme.AppTheme
import com.project.pooket.core.navigation.AppRouting
import com.project.pooket.core.navigation.NavigationManager
import com.project.pooket.core.theme.AppThemeConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.project.pooket.core.nightlight.LocalNightLightConfig
import com.project.pooket.core.nightlight.NightLightOverlay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var navManager: NavigationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val globalViewModel: GlobalViewModel = hiltViewModel()
            val currentTheme by globalViewModel.appTheme.collectAsStateWithLifecycle()
            val nightLightConfig by globalViewModel.nightLightState.collectAsStateWithLifecycle()
            val useDarkTheme = when (currentTheme){
                AppThemeConfig.DARK -> true
                AppThemeConfig.LIGHT -> false
                AppThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalNightLightConfig provides nightLightConfig ) {
                AppTheme(darkTheme = useDarkTheme) {
                    AppRouting(navManager, viewModel = globalViewModel, isDarkTheme = useDarkTheme)

                    NightLightOverlay(
                        isEnabled = nightLightConfig.isEnabled,
                        warmth = nightLightConfig.warmth,
                        dimming = nightLightConfig.dimming
                    )
                }
            }

        }
    }
}