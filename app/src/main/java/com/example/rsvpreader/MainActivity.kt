package com.example.rsvpreader

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RSVPReaderApp() }
    }
}

private sealed interface AppScreen {
    data object Library : AppScreen
    data object Settings : AppScreen
    data class Reader(val documentId: String) : AppScreen
}

@Composable
fun RSVPReaderApp() {
    val context = LocalContext.current
    val documentStore = remember { DocumentStore(context.applicationContext) }
    val settingsStore = remember { AppSettingsStore(context.applicationContext) }
    val importer = remember { FileImporter(context.applicationContext) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Library) }
    var exitDialog by remember { mutableStateOf(false) }

    val useDark = settings.darkMode
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }
    MaterialTheme(colorScheme = if (useDark) darkColorScheme() else lightColorScheme()) {
        when (val s = screen) {
            AppScreen.Library -> {
                BackHandler { exitDialog = true }
                LibraryScreen(
                    store = documentStore,
                    importer = importer,
                    onOpenDocument = { screen = AppScreen.Reader(it) },
                    onSettings = { screen = AppScreen.Settings }
                )
            }
            AppScreen.Settings -> {
                BackHandler { screen = AppScreen.Library }
                SettingsScreen(
                    settings = settings,
                    onChange = {
                        settings = it
                        settingsStore.save(it)
                    },
                    onBack = { screen = AppScreen.Library }
                )
            }
            is AppScreen.Reader -> {
                BackHandler { screen = AppScreen.Library }
                ReaderScreen(
                    documentId = s.documentId,
                    store = documentStore,
                    settings = settings,
                    onSettingsChange = {
                        settings = it
                        settingsStore.save(it)
                    },
                    onBack = { screen = AppScreen.Library }
                )
            }
        }

        if (exitDialog) {
            AlertDialog(
                onDismissRequest = { exitDialog = false },
                title = { Text("앱을 종료하시겠습니까?") },
                text = { Text("읽던 위치는 자동으로 저장됩니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        exitDialog = false
                        (context as? Activity)?.finish()
                    }) { Text("종료") }
                },
                dismissButton = { TextButton(onClick = { exitDialog = false }) { Text("취소") } }
            )
        }
    }
}
