package com.itools.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itools.app.core.settings.AppSettings
import com.itools.app.ui.IToolsRoot
import com.itools.app.ui.theme.IToolsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) captureSharedMedia(intent)
        val app = application as IToolsApp

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            IToolsTheme(themeSetting = settings.theme) {
                IToolsRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureSharedMedia(intent)
    }

    /**
     * Saves media shared by another app into the in-memory queue. Files are copied into the project
     * folder by [com.itools.app.core.project.ProjectStore] when the user creates a project, so the
     * editor works even if the sending app releases its URI permission later.
     */
    private fun captureSharedMedia(intent: Intent?) {
        val app = application as IToolsApp
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(shareStream(intent))
            Intent.ACTION_SEND_MULTIPLE -> multipleShareStreams(intent)
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }.filter { it.scheme == "content" || it.scheme == "file" }
        if (uris.isEmpty()) return

        for (uri in uris) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        app.addSharedUris(uris)
    }

    @Suppress("DEPRECATION")
    private fun shareStream(intent: Intent): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun multipleShareStreams(intent: Intent): List<Uri> =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull()
                .orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.filterNotNull().orEmpty()
        }
}
