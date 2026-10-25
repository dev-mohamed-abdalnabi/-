package com.next.files

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    private val legacyPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Perm.refresh(this)
        Ctl.bump()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Ctl.app = applicationContext
        Prefs.init(applicationContext)
        Perm.refresh(this)
        Ctl.requestAccess = { requestAccess() }
        setContent { AppTheme { Shell() } }
    }

    override fun onResume() {
        super.onResume()
        Perm.refresh(this)
        Ctl.bump()
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPerm.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
        }
    }
}

@Composable
fun Shell() {
    val cs = MaterialTheme.colorScheme
    val d = remember { DlgState() }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bottom = if (imeOpen) 16.dp
    else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp
    val overlay = Ctl.stack.lastOrNull()

    val owner = LocalLifecycleOwner.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) Perm.refresh(ctx)
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    BackHandler(enabled = overlay != null) { Ctl.pop() }
    BackHandler(enabled = overlay == null && Ctl.tab != 0) { Ctl.tab = 0 }

    Surface(Modifier.fillMaxSize(), color = cs.surface, contentColor = cs.onSurface) {
        if (!Perm.granted) {
            PermissionScreen()
        } else {
            Box(Modifier.fillMaxSize()) {
                Crossfade(Ctl.tab, Modifier.fillMaxSize(), label = "tab") { t ->
                    when (t) {
                        0 -> HomeScreen(bottom)
                        1 -> BrowserScreen(bottom, d)
                        else -> SettingsScreen(bottom)
                    }
                }
                AnimatedVisibility(
                    !imeOpen && overlay == null, Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, cs.surface)))
                            .navigationBarsPadding().padding(top = 28.dp, bottom = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FloatingNav(Ctl.tab) { Ctl.tab = it }
                    }
                }
                AnimatedVisibility(
                    overlay != null, enter = slideInVertically { it / 3 } + fadeIn(), exit = fadeOut()
                ) {
                    Box(Modifier.fillMaxSize().background(cs.surface)) {
                        when (val o = overlay) {
                            is Overlay.Collection -> CollectionScreen(o.kind, d)
                            is Overlay.Favorites -> FavoritesScreen(d)
                            is Overlay.Trash -> TrashScreen()
                            is Overlay.Editor -> EditorScreen(o.path)
                            is Overlay.Images -> ImageViewer(o)
                            null -> {}
                        }
                    }
                }
                SnackbarHost(
                    Ctl.snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = if (imeOpen) 12.dp else if (overlay != null) 24.dp else 100.dp)
                )
                Dialogs(d)
                BusyOverlay()
            }
        }
    }
}
