package com.rynekryz.sniffsniff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rynekryz.sniffsniff.core.common.settings.ThemeMode
import com.rynekryz.sniffsniff.core.designsystem.SniffSniffTheme
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings
            val dark = when (current?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            SniffSniffTheme(
                darkTheme = dark,
                dynamicColor = current?.dynamicColor ?: true,
                pureBlack = current?.pureBlack ?: false,
                systemFont = current?.systemFont ?: false,
                haptics = current?.haptics ?: true,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (current == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    } else {
                        SniffSniffApp()
                    }
                }
            }
        }
    }
}
