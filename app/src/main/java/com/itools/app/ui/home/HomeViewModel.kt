package com.itools.app.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itools.app.IToolsApp
import com.itools.app.core.media.ImportedAsset
import com.itools.app.core.model.CanvasSpec
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.Project
import com.itools.app.core.model.Track
import com.itools.app.core.model.TrackType
import com.itools.app.core.project.ProjectStore
import com.itools.app.core.project.ProjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val loading: Boolean = true,
    val projects: List<ProjectSummary> = emptyList(),
    val sharedUris: List<Uri> = emptyList(),
    val storageBytes: Long = 0L,
    val busy: String? = null,
    val message: String? = null,
    val error: String? = null
)

sealed interface HomeEvent {
    data class OpenProject(val id: String) : HomeEvent
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as IToolsApp
    private val store: ProjectStore = app.store

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            app.sharedUris.collect { uris ->
                _state.update { it.copy(sharedUris = uris) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val projects = store.list()
            val storage = store.storageUsed()
            _state.update {
                it.copy(
                    loading = false,
                    projects = projects,
                    storageBytes = storage,
                    error = null
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun createProject(
        name: String,
        canvas: CanvasSpec = CanvasSpec(),
        importUris: List<Uri> = emptyList(),
        openAfterCreate: Boolean = true
    ) {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = "Membuat proyek…") }
            var project = store.create(name.trim().ifBlank { defaultName() }, canvas)
            var imported = 0
            if (importUris.isNotEmpty()) {
                for (uri in importUris) {
                    val asset = withContext(Dispatchers.IO) {
                        store.importMedia(project.id, uri)
                    } ?: continue
                    project = appendAsset(project, asset)
                    imported++
                }
            }
            if (imported > 0) {
                val thumb = withContext(Dispatchers.IO) { store.generateThumbnail(project) }
                project = project.copy(thumbnail = thumb)
            }
            withContext(Dispatchers.IO) { store.save(project) }
            if (importUris.isNotEmpty() && imported == 0) {
                store.delete(project.id)
                _state.update {
                    it.copy(
                        busy = null,
                        error = "Media yang dibagikan tidak dapat dibaca."
                    )
                }
                return@launch
            }
            if (importUris.isNotEmpty()) app.clearSharedUris()
            _state.update { it.copy(busy = null) }
            refresh()
            if (openAfterCreate) {
                _events.send(HomeEvent.OpenProject(project.id))
            } else {
                _state.update {
                    it.copy(
                        message = if (imported > 0) {
                            "${imported} media diimpor ke proyek baru."
                        } else {
                            "Proyek ${project.name} dibuat."
                        }
                    )
                }
            }
        }
    }

    fun duplicateProject(id: String) {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = "Menduplikasi proyek…") }
            val copy = store.duplicate(id)
            _state.update {
                it.copy(
                    busy = null,
                    message = if (copy != null) "Proyek diduplikasi." else "Proyek gagal diduplikasi."
                )
            }
            refresh()
        }
    }

    fun renameProject(id: String, name: String) {
        viewModelScope.launch {
            store.rename(id, name)
            _state.update { it.copy(message = "Nama proyek diperbarui.") }
            refresh()
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = "Menghapus proyek…") }
            store.delete(id)
            _state.update {
                it.copy(busy = null, message = "Proyek dan seluruh media lokalnya dihapus.")
            }
            refresh()
        }
    }

    fun clearSharedUris() {
        app.clearSharedUris()
    }

    private fun defaultName(): String =
        "Proyek ${java.text.SimpleDateFormat("d MMM HH.mm", java.util.Locale("id", "ID")).format(java.util.Date())}"

    private fun appendAsset(project: Project, asset: ImportedAsset): Project {
        val type = if (asset.isAudio) TrackType.AUDIO else TrackType.VIDEO
        val existing = project.tracks.firstOrNull { it.type == type }
        val track = existing ?: Track(type = type)
        val base = if (existing == null) project.copy(tracks = project.tracks + track) else project
        val target = base.tracks.first { it.id == track.id }
        val start = target.durationMs
        val duration = when {
            asset.isImage -> 4_000L
            asset.durationMs > 0 -> asset.durationMs
            else -> 5_000L
        }
        val clip = Clip(
            kind = asset.kind,
            uri = asset.localUri,
            name = asset.originalName,
            startMs = start,
            durationMs = duration,
            sourceStartMs = 0,
            sourceDurationMs = duration,
            sourceTotalMs = if (asset.isImage) 0 else asset.durationMs.coerceAtLeast(duration),
            sourceWidth = asset.width,
            sourceHeight = asset.height,
            sourceRotation = asset.rotation
        )
        return base.updateTrack(target.id) {
            it.copy(clips = (it.clips + clip).sortedBy { c -> c.startMs })
        }
    }
}
