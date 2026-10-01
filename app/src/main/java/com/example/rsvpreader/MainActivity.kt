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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val persistedDarkMode = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("darkMode", false)
        setTheme(if (persistedDarkMode) R.style.Theme_RSVPReader_Dark else R.style.Theme_RSVPReader)
        super.onCreate(savedInstanceState)
        setContent { RSVPReaderApp() }
    }
}

private sealed interface AppScreen {
    data object Library : AppScreen
    data class Settings(val returnDocumentId: String? = null) : AppScreen
    data class Reader(val documentId: String) : AppScreen
}

private val AppScreenSaver = Saver<AppScreen, String>(
    save = { screen ->
        when (screen) {
            AppScreen.Library -> "library"
            is AppScreen.Reader -> "reader:" + screen.documentId
            is AppScreen.Settings -> "settings:" + (screen.returnDocumentId ?: "")
        }
    },
    restore = { encoded ->
        when {
            encoded == "library" -> AppScreen.Library
            encoded.startsWith("reader:") -> encoded.removePrefix("reader:")
                .takeIf { it.isNotBlank() }
                ?.let { AppScreen.Reader(it) }
                ?: AppScreen.Library
            encoded.startsWith("settings:") -> AppScreen.Settings(
                encoded.removePrefix("settings:").takeIf { it.isNotBlank() }
            )
            else -> AppScreen.Library
        }
    }
)

@Composable
fun RSVPReaderApp() {
    val context = LocalContext.current
    val documentStore = remember { DocumentStore(context.applicationContext) }
    val settingsStore = remember { AppSettingsStore(context.applicationContext) }
    val importer = remember { FileImporter(context.applicationContext) }
    val readerSessionHolder = remember { ReaderSessionHolder() }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var screen: AppScreen by rememberSaveable(stateSaver = AppScreenSaver) {
        mutableStateOf(AppScreen.Library)
    }
    var exitDialog by remember { mutableStateOf(false) }

    val useDark = settings.darkMode
    val lightBackground = if (settings.lightBackground == LightBackground.WARM) {
        Color(0xFFFFF7E6)
    } else {
        Color.White
    }
    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = settings.effectiveFocusColor,
            background = Color(0xFF121212),
            onBackground = Color(0xFFF2F2F2),
            surface = Color(0xFF171717),
            onSurface = Color(0xFFF2F2F2),
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFFCACACA),
            outline = Color(0xFF8A8A8A)
        )
    } else {
        lightColorScheme(
            primary = settings.focusColor,
            background = lightBackground,
            onBackground = Color(0xFF202020),
            surface = lightBackground,
            onSurface = Color(0xFF202020),
            surfaceVariant = if (settings.lightBackground == LightBackground.WARM) Color(0xFFF1E8D3) else Color(0xFFF2F2F2),
            onSurfaceVariant = Color(0xFF5F5F5F),
            outline = Color(0xFF777777)
        )
    }
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme) {
        when (val s = screen) {
            AppScreen.Library -> {
                BackHandler { exitDialog = true }
                LibraryScreen(
                    store = documentStore,
                    importer = importer,
                    onOpenDocument = {
                        readerSessionHolder.clear()
                        screen = AppScreen.Reader(it)
                    },
                    onSettings = { screen = AppScreen.Settings() }
                )
            }
            is AppScreen.Settings -> {
                val returnScreen: AppScreen = s.returnDocumentId
                    ?.let { AppScreen.Reader(it) }
                    ?: AppScreen.Library
                BackHandler { screen = returnScreen }
                SettingsScreen(
                    settings = settings,
                    onChange = {
                        settings = it
                        settingsStore.save(it)
                    },
                    onBack = { screen = returnScreen }
                )
            }
            is AppScreen.Reader -> {
                BackHandler {
                    readerSessionHolder.clear(s.documentId)
                    screen = AppScreen.Library
                }
                ReaderScreen(
                    documentId = s.documentId,
                    store = documentStore,
                    sessionHolder = readerSessionHolder,
                    settings = settings,
                    onSettingsChange = {
                        settings = it
                        settingsStore.save(it)
                    },
                    onOpenSettings = { screen = AppScreen.Settings(s.documentId) },
                    onBack = {
                        readerSessionHolder.clear(s.documentId)
                        screen = AppScreen.Library
                    }
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
