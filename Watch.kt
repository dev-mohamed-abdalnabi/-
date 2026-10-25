package com.next.files

import android.os.FileObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** تحديث تلقائي للمجلد لما ملفات تتغير من برّه */
@Composable
fun WatchDir(dir: File) {
    DisposableEffect(dir.path) {
        var job: Job? = null
        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF
        @Suppress("DEPRECATION")
        val obs = object : FileObserver(dir.path, mask) {
            override fun onEvent(event: Int, path: String?) {
                job?.cancel()
                job = Ctl.scope.launch { delay(500); Ctl.bump() }
            }
        }
        try { obs.startWatching() } catch (e: Exception) { }
        onDispose {
            try { obs.stopWatching() } catch (e: Exception) { }
            job?.cancel()
        }
    }
}
