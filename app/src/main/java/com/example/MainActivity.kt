package com.example

import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.NextTheme

enum class Screen {
    LIST, VIEW, EDIT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup database, repository and settings
        val database = NoteDatabase.getDatabase(this)
        val repository = NoteRepository(database.noteDao())
        val settingsManager = SettingsManager(this)

        setContent {
            val themeMode = remember { mutableStateOf(settingsManager.getThemeMode()) }
            val fontSizeScale = remember { mutableStateOf(settingsManager.getFontSizeScale()) }

            NextTheme(
                themeMode = themeMode.value,
                fontSizeScale = fontSizeScale.value
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: NoteViewModel = viewModel(
                        factory = NoteViewModelFactory(repository, settingsManager)
                    )

                    var currentScreen by remember { mutableStateOf(Screen.LIST) }

                    // Navigation helper
                    val navigateToScreen: (Screen) -> Unit = { screen ->
                        currentScreen = screen
                    }

                    // Theme and Font update listeners for instant feedback
                    val onSettingsUpdated: () -> Unit = {
                        themeMode.value = settingsManager.getThemeMode()
                        fontSizeScale.value = settingsManager.getFontSizeScale()
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        matchScreen(
                            screen = screen,
                            viewModel = viewModel,
                            settingsManager = settingsManager,
                            onNavigate = navigateToScreen,
                            onSettingsUpdated = onSettingsUpdated
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun matchScreen(
    screen: Screen,
    viewModel: NoteViewModel,
    settingsManager: SettingsManager,
    onNavigate: (Screen) -> Unit,
    onSettingsUpdated: () -> Unit
) {
    when (screen) {
        Screen.LIST -> {
            NoteListScreen(
                viewModel = viewModel,
                settingsManager = settingsManager,
                onCreateClick = {
                    viewModel.resetEditor()
                    viewModel.selectNote(null)
                    onNavigate(Screen.EDIT)
                },
                onNoteClick = { note ->
                    viewModel.selectNote(note)
                    onNavigate(Screen.VIEW)
                },
                onSettingsUpdated = onSettingsUpdated
            )
        }
        Screen.VIEW -> {
            NoteViewerScreen(
                viewModel = viewModel,
                onBack = { onNavigate(Screen.LIST) },
                onEdit = { onNavigate(Screen.EDIT) }
            )
        }
        Screen.EDIT -> {
            NoteEditorScreen(
                viewModel = viewModel,
                settingsManager = settingsManager,
                onBack = { onNavigate(Screen.LIST) },
                onSave = {
                    viewModel.saveNote {
                        onNavigate(Screen.LIST)
                    }
                }
            )
        }
    }
}

@Composable
fun getNoteBackgroundColor(colorIndex: Int): Color {
    val isDark = MaterialTheme.colorScheme.background.toArgb() == Color(0xFF101216).toArgb() || 
                 MaterialTheme.colorScheme.background.toArgb() == Color(0xFF000000).toArgb()
                 
    return when (colorIndex) {
        1 -> if (isDark) Color(0xFF5A1E1E) else Color(0xFFFFD1D1) // Soft Red
        2 -> if (isDark) Color(0xFF1E4620) else Color(0xFFD1FFD6) // Soft Green
        3 -> if (isDark) Color(0xFF1E315A) else Color(0xFFD1E4FF) // Soft Blue
        4 -> if (isDark) Color(0xFF5A4D1E) else Color(0xFFFFF9D1) // Soft Yellow
        5 -> if (isDark) Color(0xFF3B1E5A) else Color(0xFFEED1FF) // Soft Purple
        else -> MaterialTheme.colorScheme.surfaceVariant // Standard Container Color
    }
}
