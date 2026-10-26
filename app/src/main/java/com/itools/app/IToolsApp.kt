package com.itools.app

import android.app.Application
import android.net.Uri
import com.itools.app.core.project.ProjectStore
import com.itools.app.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide services. iTools has no DI framework because there is nothing remote to inject:
 * a file-backed project store, a preference store, and the media shared into the activity.
 */
class IToolsApp : Application() {

    lateinit var store: ProjectStore
        private set
    lateinit var settings: SettingsRepository
        private set

    private val _sharedUris = MutableStateFlow<List<Uri>>(emptyList())
    val sharedUris: StateFlow<List<Uri>> = _sharedUris.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        store = ProjectStore(this)
        settings = SettingsRepository(this)
    }

    fun addSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _sharedUris.update { current ->
            (current + uris).distinctBy { it.toString() }
        }
    }

    fun removeSharedUri(uri: Uri) {
        _sharedUris.update { current -> current.filterNot { it == uri } }
    }

    fun clearSharedUris() {
        _sharedUris.value = emptyList()
    }
}
