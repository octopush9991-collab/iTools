package com.itools.app.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.itools.app.IToolsApp
import com.itools.app.core.audio.AudioAnalyzer
import com.itools.app.core.audio.ProceduralMusic
import com.itools.app.core.media.ImportedAsset
import com.itools.app.core.model.CanvasSpec
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.ColorGrade
import com.itools.app.core.model.ExportSettings
import com.itools.app.core.model.FitMode
import com.itools.app.core.model.Keyframe
import com.itools.app.core.model.MaskSpec
import com.itools.app.core.model.Project
import com.itools.app.core.model.ProjectOps
import com.itools.app.core.model.StickerSpec
import com.itools.app.core.model.TextSpec
import com.itools.app.core.model.Track
import com.itools.app.core.model.TrackType
import com.itools.app.core.model.Transform
import com.itools.app.core.model.TransitionSpec
import com.itools.app.core.model.ChromaSpec
import com.itools.app.core.project.ProjectStore
import com.itools.app.core.render.ExportEngine
import com.itools.app.core.render.FrameTools
import com.itools.app.core.tools.AutoEnhance
import com.itools.app.core.tools.SubtitleCue
import com.itools.app.core.tools.SubtitleIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class EditorUiState(
    val loading: Boolean = true,
    val project: Project? = null,
    val selectedClipId: String? = null,
    val playheadMs: Long = 0L,
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    val previewVersion: Long = 0L,
    val busy: String? = null,
    val message: String? = null,
    val error: String? = null
)

class EditorViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val app = application as IToolsApp
    private val store: ProjectStore = app.store
    private val frameTools = FrameTools(application, store)
    private val projectId: String = savedStateHandle.get<String>("projectId").orEmpty()

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val exportEngine = ExportEngine(application, store)
    val exportState = exportEngine.state

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private var transactionBaseline: Project? = null
    private var saveJob: Job? = null
    private var autoSaveEnabled = true

    init {
        viewModelScope.launch {
            app.settings.settings.collect { autoSaveEnabled = it.autoSave }
        }
        load()
    }

    fun load() {
        if (projectId.isBlank()) {
            _state.value = EditorUiState(
                loading = false,
                error = "ID proyek tidak ditemukan."
            )
            return
        }
        viewModelScope.launch {
            val loaded = store.load(projectId)
            if (loaded == null) {
                _state.value = EditorUiState(
                    loading = false,
                    error = "Proyek tidak dapat dibuka. File mungkin sudah dihapus."
                )
                return@launch
            }
            val fixed = if (loaded.tracks.none { it.type == TrackType.VIDEO }) {
                loaded.copy(tracks = store.defaultTracks() + loaded.tracks)
            } else {
                loaded
            }
            _state.value = EditorUiState(
                loading = false,
                project = fixed,
                playheadMs = 0,
                previewVersion = 1
            )
        }
    }

    // ---------------------------------------------------------------- state helpers

    private fun say(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Clears both the snackbar message and a stale recoverable error. */
    fun clearTransientMessages() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun showMessage(text: String) {
        say(text)
    }

    private fun setBusy(text: String?) {
        _state.update { it.copy(busy = text) }
    }

    /**
     * Runs a media operation with the busy indicator, and always clears it afterwards. Without the
     * finally block a thrown IO/Media3 error would leave the editor permanently busy.
     */
    private fun launchBusy(
        message: String,
        failureMessage: String = "Proses gagal. Coba lagi.",
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            setBusy(message)
            try {
                block()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                say(failureMessage)
            } finally {
                setBusy(null)
            }
        }
    }

    private fun pushUndo(project: Project) {
        undoStack.addLast(project)
        while (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun apply(
        next: Project,
        select: String? = null,
        clearSelection: Boolean = false,
        history: Boolean = true,
        preview: Boolean = true,
        playhead: Long? = null
    ) {
        val current = _state.value.project ?: return
        if (next == current) return
        if (history && transactionBaseline == null) pushUndo(current)
        val selected = when {
            clearSelection -> null
            select != null -> select
            else -> _state.value.selectedClipId?.takeIf { next.clip(it) != null }
        }
        _state.update {
            it.copy(
                project = next,
                selectedClipId = selected,
                playheadMs = (playhead ?: it.playheadMs).coerceIn(0, max(1, next.durationMs)),
                undoCount = undoStack.size,
                redoCount = redoStack.size,
                previewVersion = it.previewVersion + if (preview) 1 else 0
            )
        }
        scheduleSave(next)
    }

    private fun edit(
        history: Boolean = true,
        preview: Boolean = true,
        block: (Project) -> Project
    ) {
        val current = _state.value.project ?: return
        apply(block(current), history = history, preview = preview)
    }

    private fun scheduleSave(project: Project) {
        saveJob?.cancel()
        if (!autoSaveEnabled) return
        saveJob = viewModelScope.launch {
            delay(550)
            store.save(project)
        }
    }

    fun saveNow() {
        val project = _state.value.project ?: return
        saveJob?.cancel()
        viewModelScope.launch { store.save(project) }
    }

    fun undo() {
        val current = _state.value.project ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        _state.update {
            it.copy(
                project = previous,
                selectedClipId = it.selectedClipId?.takeIf { id -> previous.clip(id) != null },
                undoCount = undoStack.size,
                redoCount = redoStack.size,
                previewVersion = it.previewVersion + 1
            )
        }
        scheduleSave(previous)
    }

    fun redo() {
        val current = _state.value.project ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        _state.update {
            it.copy(
                project = next,
                selectedClipId = it.selectedClipId?.takeIf { id -> next.clip(id) != null },
                undoCount = undoStack.size,
                redoCount = redoStack.size,
                previewVersion = it.previewVersion + 1
            )
        }
        scheduleSave(next)
    }

    fun beginTransaction() {
        if (transactionBaseline == null) transactionBaseline = _state.value.project
    }

    fun endTransaction() {
        val baseline = transactionBaseline ?: return
        transactionBaseline = null
        val current = _state.value.project ?: return
        if (baseline != current) {
            pushUndo(baseline)
            _state.update { it.copy(undoCount = undoStack.size, redoCount = redoStack.size) }
        }
    }

    private fun editLive(preview: Boolean = true, block: (Project) -> Project) {
        val current = _state.value.project ?: return
        val next = block(current)
        if (next == current) return
        _state.update {
            it.copy(
                project = next,
                playheadMs = it.playheadMs.coerceIn(0, max(1, next.durationMs)),
                previewVersion = it.previewVersion + if (preview) 1 else 0
            )
        }
        scheduleSave(next)
    }

    // ---------------------------------------------------------------- selection / playhead

    fun selectClip(clipId: String?) {
        _state.update { it.copy(selectedClipId = clipId) }
    }

    fun setPlayhead(ms: Long) {
        val duration = _state.value.project?.durationMs ?: 0
        _state.update { it.copy(playheadMs = ms.coerceIn(0, max(1, duration))) }
    }

    fun onPreviewPosition(ms: Long) {
        _state.update {
            val duration = it.project?.durationMs ?: 0
            it.copy(playheadMs = ms.coerceIn(0, max(1, duration)))
        }
    }

    fun stepFrame(direction: Int) {
        val project = _state.value.project ?: return
        val frameMs = (1000 / project.canvas.fps.coerceAtLeast(1)).coerceAtLeast(16)
        setPlayhead(_state.value.playheadMs + direction * frameMs)
    }

    fun selectedClip(): Clip? = _state.value.selectedClipId?.let { _state.value.project?.clip(it) }

    // ---------------------------------------------------------------- timeline edits

    fun splitAtPlayhead() {
        val clip = selectedClip() ?: return
        val at = _state.value.playheadMs
        val next = ProjectOps.split(_state.value.project ?: return, clip.id, at)
        if (next == _state.value.project) {
            say("Geser playhead ke dalam klip untuk memotong.")
            return
        }
        apply(next, select = clip.id)
        say("Klip dipotong.")
    }

    fun deleteSelected() {
        val clip = selectedClip() ?: return
        apply(ProjectOps.deleteClip(_state.value.project ?: return, clip.id), clearSelection = true)
        say("Klip dihapus. Gunakan Urungkan jika keliru.")
    }

    fun removeClip(clipId: String) {
        apply(ProjectOps.deleteClip(_state.value.project ?: return, clipId), clearSelection = true)
    }

    fun toggleSelectedClipMuted() {
        val clip = selectedClip() ?: return
        if (clip.kind != ClipKind.AUDIO && clip.kind != ClipKind.VIDEO) {
            say("Hanya klip audio atau video yang memiliki suara.")
            return
        }
        edit {
            it.updateClip(clip.id) { current -> current.copy(muted = !current.muted) }
        }
        say(if (clip.muted) "Suara klip diaktifkan." else "Suara klip dibisukan.")
    }

    fun duplicateSelected() {
        val clip = selectedClip() ?: return
        val project = _state.value.project ?: return
        val result = ProjectOps.duplicateClip(project, clip.id) ?: return
        val (next, copy) = result
        apply(next, select = copy.id, playhead = copy.startMs)
        say("Klip diduplikasi.")
    }

    fun moveClipBy(clipId: String, deltaMs: Long) {
        editLive { project ->
            val clip = project.clip(clipId) ?: return@editLive project
            ProjectOps.move(project, clipId, clip.startMs + deltaMs)
        }
    }

    fun trimStartBy(clipId: String, deltaMs: Long) {
        editLive { ProjectOps.trimStart(it, clipId, deltaMs) }
    }

    fun trimEndBy(clipId: String, deltaMs: Long) {
        editLive { ProjectOps.trimEnd(it, clipId, deltaMs) }
    }

    fun setSpeed(speed: Float) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setSpeed(it, clip.id, speed) }
    }

    fun setVolume(volume: Float) {
        val project = _state.value.project ?: return
        val clip = selectedClip() ?: return
        val next = if (clip.keyframes.isEmpty()) {
            ProjectOps.setVolume(project, clip.id, volume)
        } else {
            val local = (_state.value.playheadMs - clip.startMs).coerceIn(0, clip.durationMs)
            val frame = ProjectOps.snapshotKeyframe(clip, local).copy(
                volume = volume.coerceIn(0f, 2f)
            )
            ProjectOps.addKeyframe(project, clip.id, frame)
        }
        apply(next)
    }

    fun setFades(fadeIn: Long, fadeOut: Long) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setFades(it, clip.id, fadeIn, fadeOut) }
    }

    fun setOpacity(opacity: Float) {
        val project = _state.value.project ?: return
        val clip = selectedClip() ?: return
        val next = if (clip.keyframes.isEmpty()) {
            ProjectOps.setOpacity(project, clip.id, opacity)
        } else {
            val local = (_state.value.playheadMs - clip.startMs).coerceIn(0, clip.durationMs)
            val frame = ProjectOps.snapshotKeyframe(clip, local).copy(
                opacity = opacity.coerceIn(0f, 1f)
            )
            ProjectOps.addKeyframe(project, clip.id, frame)
        }
        apply(next)
    }

    fun setTransform(transform: Transform) {
        val project = _state.value.project ?: return
        val clip = selectedClip() ?: return
        val next = if (clip.keyframes.isEmpty()) {
            ProjectOps.setTransform(project, clip.id, transform)
        } else {
            // Once a clip is animated, moving the transform controls writes a keyframe at the
            // playhead. This is the same auto-keyframe behaviour expected from a video editor.
            val local = (_state.value.playheadMs - clip.startMs).coerceIn(0, clip.durationMs)
            val frame = ProjectOps.snapshotKeyframe(clip, local).copy(
                scaleX = transform.scaleX,
                scaleY = transform.scaleY,
                rotation = transform.rotation,
                offsetX = transform.offsetX,
                offsetY = transform.offsetY
            )
            ProjectOps.addKeyframe(project, clip.id, frame)
        }
        apply(next)
    }

    fun resetTransform() {
        setTransform(Transform())
    }

    fun setColor(grade: ColorGrade) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setGrade(it, clip.id, grade) }
    }

    fun resetColor() {
        setColor(ColorGrade())
    }

    fun setFilter(filterId: String, intensity: Float) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setFilter(it, clip.id, filterId, intensity) }
    }

    fun setChroma(chroma: ChromaSpec?) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setChroma(it, clip.id, chroma) }
    }

    fun setMask(mask: MaskSpec?) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setMask(it, clip.id, mask) }
    }

    fun setTransition(incoming: Boolean, spec: TransitionSpec?) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setTransition(it, clip.id, incoming, spec) }
    }

    fun addKeyframeAtPlayhead() {
        val clip = selectedClip() ?: return
        val local = (_state.value.playheadMs - clip.startMs).coerceIn(0, clip.durationMs)
        val keyframe = ProjectOps.snapshotKeyframe(clip, local)
        edit { ProjectOps.addKeyframe(it, clip.id, keyframe) }
        say("Keyframe ditambahkan pada ${local} ms.")
    }

    fun removeKeyframeAtPlayhead() {
        val clip = selectedClip() ?: return
        val local = (_state.value.playheadMs - clip.startMs).coerceIn(0, clip.durationMs)
        val nearest = clip.keyframes.minByOrNull { kotlin.math.abs(it.timeMs - local) }
        if (nearest == null) {
            say("Klip ini belum memiliki keyframe.")
            return
        }
        edit { ProjectOps.removeKeyframe(it, clip.id, nearest.timeMs) }
        say("Keyframe pada ${nearest.timeMs} ms dihapus.")
    }

    fun removeKeyframe(timeMs: Long) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.removeKeyframe(it, clip.id, timeMs) }
    }

    fun setOverlayDuration(durationMs: Long) {
        val clip = selectedClip() ?: return
        if (clip.kind != ClipKind.TEXT &&
            clip.kind != ClipKind.STICKER &&
            clip.kind != ClipKind.CAPTION
        ) return
        val safe = durationMs.coerceIn(300, 600_000)
        edit {
            it.updateClip(clip.id) { current ->
                current.copy(durationMs = safe, sourceDurationMs = safe)
            }
        }
    }

    fun clearKeyframes() {
        val clip = selectedClip() ?: return
        edit { ProjectOps.clearKeyframes(it, clip.id) }
    }

    fun addKeyframe(keyframe: Keyframe) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.addKeyframe(it, clip.id, keyframe) }
    }

    // ---------------------------------------------------------------- text, stickers, captions

    fun addText(spec: TextSpec = TextSpec()) {
        val project = _state.value.project ?: return
        val (next, clip) = ProjectOps.addText(project, _state.value.playheadMs, spec)
        apply(next, select = clip.id, playhead = clip.startMs)
    }

    fun addSticker(spec: StickerSpec) {
        val project = _state.value.project ?: return
        val (next, clip) = ProjectOps.addSticker(project, _state.value.playheadMs, spec)
        apply(next, select = clip.id)
    }

    fun addCaptionAtPlayhead(text: String) {
        val project = _state.value.project ?: return
        val (next, clip) = ProjectOps.addCaption(
            project,
            _state.value.playheadMs,
            2_500,
            text.ifBlank { "Subtitle baru" }
        )
        apply(next, select = clip.id)
    }

    fun updateText(spec: TextSpec) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setText(it, clip.id, spec) }
    }

    fun updateSticker(spec: StickerSpec) {
        val clip = selectedClip() ?: return
        edit { ProjectOps.setSticker(it, clip.id, spec) }
    }

    fun setCaptions(cues: List<SubtitleCue>) {
        val project = _state.value.project ?: return
        if (cues.isEmpty()) return
        val pairs = cues.map { it.startMs to (it.endMs - it.startMs to it.text) }
        apply(ProjectOps.setCaptions(project, pairs))
        say("${cues.size} subtitle dimuat.")
    }

    fun clearCaptions() {
        edit { ProjectOps.removeCaptionTrack(it) }
        say("Track subtitle dihapus.")
    }

    fun importSrt(content: String) {
        val cues = SubtitleIO.parse(content)
        if (cues.isEmpty()) {
            say("Tidak ada subtitle valid di file tersebut.")
            return
        }
        setCaptions(cues)
    }

    fun captionCues(): List<SubtitleCue> {
        val project = _state.value.project ?: return emptyList()
        return project.tracks
            .filter { it.type == TrackType.CAPTION }
            .flatMap { it.clips }
            .sortedBy { it.startMs }
            .map { clip ->
                SubtitleCue(
                    startMs = clip.startMs,
                    endMs = clip.endMs,
                    text = clip.text?.text ?: ""
                )
            }
    }

    fun exportSrt(): String = SubtitleIO.toSrt(captionCues())

    /**
     * Splits a pasted script into subtitles. If a local audio/video clip exists, silence detection
     * supplies the speech ranges; otherwise the text is distributed evenly. No speech recognition
     * is involved, so this is described in the UI as a timing helper, not automatic transcription.
     */
    fun buildCaptionsFromScript(script: String) {
        val lines = script.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            say("Teks naskah masih kosong.")
            return
        }
        launchBusy("Menganalisis waktu hening…", "Subtitle gagal dibuat dari naskah.") {
            val project = _state.value.project ?: return@launchBusy
            val mediaClip = project.tracks.flatMap { it.clips }
                .firstOrNull { it.kind == ClipKind.AUDIO }
                ?: project.tracks.flatMap { it.clips }
                    .firstOrNull { it.kind == ClipKind.VIDEO && it.uri.isNotBlank() }
            var ranges = emptyList<Pair<Long, Long>>()
            if (mediaClip != null) {
                val envelope = withContext(Dispatchers.IO) {
                    AudioAnalyzer.decodeEnvelope(app, store.resolve(project.id, mediaClip.uri))
                }
                if (envelope != null) {
                    val silence = AudioAnalyzer.detectSilence(envelope)
                    var cursor = 0L
                    val duration = max(project.durationMs, envelope.durationMs)
                    val result = ArrayList<Pair<Long, Long>>()
                    for (range in silence) {
                        if (range.startMs - cursor >= 350) result.add(cursor to range.startMs)
                        cursor = range.endMs
                    }
                    if (duration - cursor >= 350) result.add(cursor to duration)
                    ranges = result
                }
            }
            val cues = ArrayList<SubtitleCue>()
            val total = max(project.durationMs, lines.size * 1_400L)
            if (ranges.size >= lines.size) {
                for (i in lines.indices) {
                    val range = ranges[i]
                    cues.add(SubtitleCue(range.first, max(range.first + 500, range.second), lines[i]))
                }
            } else {
                val slot = total / lines.size
                for (i in lines.indices) {
                    val start = i * slot
                    cues.add(SubtitleCue(start, start + (slot * 0.92f).toLong(), lines[i]))
                }
            }
            setCaptions(cues)
            say("${cues.size} subtitle dibuat dari naskah.")
        }
    }

    // ---------------------------------------------------------------- media

    fun importMedia(uri: Uri, targetType: TrackType = TrackType.VIDEO) {
        importMediaBatch(listOf(uri), targetType)
    }

    /**
     * Imports files one at a time and applies the result once. Importing in parallel used to race
     * on the project state and could silently drop a file, so this method is intentionally serial.
     */
    fun importMediaBatch(uris: List<Uri>, targetType: TrackType = TrackType.VIDEO) {
        if (uris.isEmpty()) return
        if (_state.value.busy != null) return
        launchBusy("Mengimpor ${uris.size} media…", "Media gagal diimpor.") {
            var project = _state.value.project ?: return@launchBusy
            var imported = 0
            var lastClip: Clip? = null
            for (uri in uris) {
                val asset = withContext(Dispatchers.IO) { store.importMedia(project.id, uri) } ?: continue
                val type = when {
                    asset.isAudio -> TrackType.AUDIO
                    targetType == TrackType.OVERLAY -> TrackType.OVERLAY
                    else -> TrackType.VIDEO
                }
                val duration = when {
                    asset.isImage -> 4_000L
                    asset.durationMs > 0 -> asset.durationMs
                    else -> 5_000L
                }
                val videoTrackEnd = project.tracks.filter { it.type == TrackType.VIDEO }
                    .maxOfOrNull { it.durationMs } ?: 0L
                val start = if (type == TrackType.VIDEO) videoTrackEnd else _state.value.playheadMs
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
                val (next, added) = addToFreeTrack(project, type, clip)
                project = next
                lastClip = added
                imported++
            }
            if (imported == 0) {
                say("Format media tidak didukung atau file tidak dapat dibaca.")
                return@launchBusy
            }
            apply(project, select = lastClip?.id, playhead = lastClip?.startMs)
            say(if (imported == 1) "${lastClip?.name ?: "Media"} ditambahkan." else "$imported media diimpor.")
            refreshThumbnail()
        }
    }

    fun importVoiceover(relativeUri: String) {
        launchBusy("Memproses rekaman…", "Rekaman suara gagal ditambahkan.") {
            val project = _state.value.project ?: return@launchBusy
            val meta = withContext(Dispatchers.IO) {
                store.probe(store.resolve(project.id, relativeUri))
            }
            val duration = meta.durationMs.coerceAtLeast(500)
            val clip = Clip(
                kind = ClipKind.AUDIO,
                uri = relativeUri,
                name = "Rekaman suara",
                startMs = _state.value.playheadMs,
                durationMs = duration,
                sourceStartMs = 0,
                sourceDurationMs = duration,
                sourceTotalMs = duration
            )
            val (next, added) = addToFreeTrack(project, TrackType.AUDIO, clip)
            apply(next, select = added.id)
            say("Rekaman suara ditambahkan (${meta.durationMs / 1000} detik).")
        }
    }

    fun newVoiceFile(): Pair<File, String>? {
        val project = _state.value.project ?: return null
        return store.newAsset(project.id, "voice", "m4a")
    }

    fun addGeneratedMusic(styleId: String) {
        launchBusy("Membuat musik lokal…", "Musik gagal dibuat.") {
            val project = _state.value.project ?: return@launchBusy
            val duration = project.durationMs.coerceIn(10_000, 180_000)
            val (file, relative) = store.newAsset(project.id, "music", "wav")
            val ok = withContext(Dispatchers.IO) {
                ProceduralMusic.generate(file, styleId, duration)
            }
            if (!ok) {
                say("Musik gagal dibuat.")
                return@launchBusy
            }
            val clip = Clip(
                kind = ClipKind.AUDIO,
                uri = relative,
                name = "Musik ${ProceduralMusic.styles.firstOrNull { it.id == styleId }?.label ?: styleId}",
                startMs = 0,
                durationMs = duration,
                sourceStartMs = 0,
                sourceDurationMs = duration,
                sourceTotalMs = duration
            )
            val track = Track(type = TrackType.AUDIO, name = "Musik latar")
            val base = if (project.tracks.any { it.id == track.id }) project else project.withTrack(track)
            val next = base.updateTrack(track.id) { it.copy(clips = listOf(clip)) }
            apply(next, select = clip.id)
            say("Musik latar ditambahkan. Semua nada dibuat di perangkat.")
        }
    }

    private fun addToFreeTrack(
        project: Project,
        type: TrackType,
        clip: Clip
    ): Pair<Project, Clip> {
        val candidates = project.tracks.filter { it.type == type }
        val freeTrack = candidates.firstOrNull { track ->
            track.clips.none { overlaps(it, clip) }
        } ?: Track(type = type, name = if (candidates.isEmpty()) type.label else "${type.label} ${candidates.size + 1}")
        val base = if (project.tracks.any { it.id == freeTrack.id }) {
            project
        } else {
            project.withTrack(freeTrack)
        }
        val next = base.updateTrack(freeTrack.id) {
            it.copy(clips = (it.clips + clip).sortedBy { c -> c.startMs })
        }
        return next to clip
    }

    private fun overlaps(a: Clip, b: Clip): Boolean =
        a.startMs < b.endMs && b.startMs < a.endMs

    private suspend fun refreshThumbnail() {
        val project = _state.value.project ?: return
        val relative = withContext(Dispatchers.IO) { store.generateThumbnail(project) } ?: return
        val next = project.copy(thumbnail = relative)
        _state.update { it.copy(project = next) }
        scheduleSave(next)
    }

    // ---------------------------------------------------------------- local tools

    fun toggleReverse() {
        val clip = selectedClip() ?: return
        if (clip.kind != ClipKind.VIDEO) {
            say("Balik klip hanya tersedia untuk video.")
            return
        }
        launchBusy(
            "Membalik klip… 0%",
            "Klip gagal dibalik. Coba klip yang lebih pendek atau resolusi lebih rendah."
        ) {
            val project = _state.value.project ?: return@launchBusy
            val result = frameTools.reverse(project, clip) { percent ->
                viewModelScope.launch {
                    if (_state.value.busy != null) {
                        setBusy("Membalik klip… ${percent.coerceIn(0, 100)}%")
                    }
                }
            }
            if (result == null) {
                say("Klip gagal dibalik. Coba klip yang lebih pendek atau resolusi lebih rendah.")
                return@launchBusy
            }
            edit { current ->
                current.updateClip(clip.id) {
                    it.copy(
                        uri = result.relativeUri,
                        name = "Balik ${it.name}",
                        sourceStartMs = 0,
                        sourceDurationMs = result.durationMs,
                        sourceTotalMs = result.durationMs,
                        durationMs = result.durationMs,
                        speed = 1f,
                        reversed = true,
                        muted = true
                    )
                }
            }
            say(
                "Klip dibalik (${result.frameCount} frame). Audio dibuang; " +
                    "video dibuat ulang di perangkat."
            )
        }
    }

    fun freezeFrame() {
        val clip = selectedClip() ?: return
        if (clip.kind != ClipKind.VIDEO && clip.kind != ClipKind.IMAGE) {
            say("Bekukan bingkai membutuhkan video atau gambar.")
            return
        }
        launchBusy("Mengambil bingkai…", "Bingkai tidak dapat diambil.") {
            val project = _state.value.project ?: return@launchBusy
            val local = (_state.value.playheadMs - clip.startMs).coerceIn(0, clip.durationMs)
            val result = frameTools.freezeFrame(project, clip, local)
            if (result == null) {
                say("Bingkai tidak dapat diambil.")
                return@launchBusy
            }
            val frozen = result.clip.copy(startMs = _state.value.playheadMs)
            val (next, added) = addToFreeTrack(project, TrackType.OVERLAY, frozen)
            apply(next, select = added.id)
            say("Bingkai dibekukan selama 2,5 detik.")
        }
    }

    fun autoEnhance() {
        val clip = selectedClip() ?: return
        if (clip.kind != ClipKind.VIDEO && clip.kind != ClipKind.IMAGE) {
            say("Auto-enhance hanya untuk video atau gambar.")
            return
        }
        launchBusy("Menganalisis gambar…", "Analisis warna gagal.") {
            val project = _state.value.project ?: return@launchBusy
            val suggestion = AutoEnhance.analyze(
                app,
                store.resolve(project.id, clip.uri),
                clip.kind == ClipKind.VIDEO,
                clip.sourceDurationMs
            )
            if (suggestion == null) {
                say("Analisis warna gagal.")
                return@launchBusy
            }
            edit { ProjectOps.setGrade(it, clip.id, suggestion.grade) }
            say("Auto-enhance: ${suggestion.note}")
        }
    }

    fun detectBeats() {
        val project = _state.value.project ?: return
        val clip = selectedClip()?.takeIf { it.kind == ClipKind.AUDIO || it.kind == ClipKind.VIDEO }
            ?: project.tracks.flatMap { it.clips }
                .firstOrNull { it.kind == ClipKind.AUDIO }
            ?: project.tracks.flatMap { it.clips }
                .firstOrNull { it.kind == ClipKind.VIDEO && it.uri.isNotBlank() }
        if (clip == null) {
            say("Tambahkan audio atau video terlebih dahulu.")
            return
        }
        launchBusy("Mendeteksi ketukan…", "Audio tidak dapat dianalisis.") {
            val envelope = withContext(Dispatchers.IO) {
                AudioAnalyzer.decodeEnvelope(app, store.resolve(project.id, clip.uri))
            }
            if (envelope == null) {
                say("Audio tidak dapat dianalisis.")
                return@launchBusy
            }
            val beats = AudioAnalyzer.detectBeats(envelope)
            apply(project.copy(beatMarkersMs = beats.beatsMs))
            say(
                if (beats.bpm > 0) "Terdeteksi ${beats.beatsMs.size} ketukan, sekitar ${beats.bpm} BPM."
                else "Ketukan tidak terdeteksi dengan jelas."
            )
        }
    }

    fun detectSilence() {
        val project = _state.value.project ?: return
        val clip = selectedClip()?.takeIf { it.kind == ClipKind.AUDIO || it.kind == ClipKind.VIDEO }
            ?: project.tracks.flatMap { it.clips }
                .firstOrNull { it.kind == ClipKind.AUDIO }
            ?: return
        launchBusy("Mendeteksi bagian hening…", "Audio tidak dapat dianalisis.") {
            val envelope = withContext(Dispatchers.IO) {
                AudioAnalyzer.decodeEnvelope(app, store.resolve(project.id, clip.uri))
            }
            if (envelope == null) {
                say("Audio tidak dapat dianalisis.")
                return@launchBusy
            }
            val silence = AudioAnalyzer.detectSilence(envelope)
            val markers = silence.map { it.startMs }
            apply(project.copy(markersMs = markers))
            say("${silence.size} bagian hening ditandai pada timeline.")
        }
    }

    fun clearAnalysisMarkers() {
        val project = _state.value.project ?: return
        if (project.beatMarkersMs.isEmpty() && project.markersMs.isEmpty()) {
            say("Tidak ada penanda untuk dibersihkan.")
            return
        }
        apply(project.copy(beatMarkersMs = emptyList(), markersMs = emptyList()), preview = false)
        say("Penanda ketukan dan keheningan dibersihkan.")
    }

    // ---------------------------------------------------------------- project settings

    fun renameProject(name: String) {
        edit(preview = false) { ProjectOps.rename(it, name) }
    }

    fun setCanvas(canvas: CanvasSpec) {
        edit(preview = true) { ProjectOps.setCanvas(it, canvas) }
    }

    fun setExport(settings: ExportSettings) {
        edit(preview = false) { ProjectOps.setExport(it, settings) }
    }

    fun setFitMode(fit: FitMode) {
        edit {
            it.copy(
                canvas = it.canvas.copy(fit = fit),
                export = it.export.copy(fit = fit)
            )
        }
    }

    fun toggleTrackMuted(trackId: String) {
        edit { ProjectOps.toggleMute(it, trackId) }
    }

    fun toggleTrackHidden(trackId: String) {
        edit { ProjectOps.toggleHidden(it, trackId) }
    }

    fun startExport() {
        val project = _state.value.project ?: return
        saveNow()
        exportEngine.start(viewModelScope, project)
    }

    fun cancelExport() = exportEngine.cancel()

    fun resetExportState() = exportEngine.reset()

    override fun onCleared() {
        saveJob?.cancel()
        _state.value.project?.let { project ->
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { store.save(project) }
        }
        exportEngine.cancel()
        super.onCleared()
    }
}
