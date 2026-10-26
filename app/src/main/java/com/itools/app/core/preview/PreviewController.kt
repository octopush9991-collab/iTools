package com.itools.app.core.preview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.PreviewingMultipleInputVideoGraph
import androidx.media3.transformer.CompositionPlayer
import com.itools.app.core.model.Project
import com.itools.app.core.render.CompositionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns a [CompositionPlayer] for the editor preview.
 *
 * A CompositionPlayer can only play a single Composition, so every timeline edit creates a new
 * player. Rebuilds are debounced by the UI (not here) to avoid starting a decoder per slider tick.
 */
@OptIn(UnstableApi::class)
class PreviewController(
    private val context: Context,
    private val compositionFactory: CompositionFactory
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _player = MutableStateFlow<CompositionPlayer?>(null)
    val player: StateFlow<CompositionPlayer?> = _player.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pollJob: Job? = null
    private var lastPosition = 0L
    private var lastProject: Project? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _player.value?.let { player ->
                    if (lastPosition > 0 && player.currentPosition < lastPosition - 200) {
                        player.seekTo(lastPosition)
                    }
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _error.value = error.localizedMessage ?: "Pemutar pratinjau gagal."
        }
    }

    fun setProject(project: Project, keepPosition: Boolean = true) {
        val wasPlaying = _player.value?.isPlaying == true
        val resumeAt = (if (keepPosition) lastPosition else 0L)
            .coerceIn(0, project.durationMs.coerceAtLeast(0))
        lastProject = project
        releasePlayer()
        _error.value = null
        try {
            val size = CompositionFactory.previewSize(project)
            val result = compositionFactory.build(
                project = project,
                outputWidth = size.first,
                outputHeight = size.second,
                includeAudio = project.export.includeAudio,
                outputFps = project.canvas.fps
            )
            val newPlayer = CompositionPlayer.Builder(context)
                .setPreviewingVideoGraphFactory(PreviewingMultipleInputVideoGraph.Factory())
                .build()
            newPlayer.addListener(listener)
            newPlayer.setComposition(result.composition)
            newPlayer.prepare()
            _player.value = newPlayer
            if (resumeAt > 0) newPlayer.seekTo(resumeAt)
            lastPosition = resumeAt
            _positionMs.value = resumeAt
            // Rebuilds used to stop playback after every edit. If the user was playing, continue
            // from the same position; at the very end, wrap to the start instead of staying ended.
            if (wasPlaying) {
                if (resumeAt >= project.durationMs - 50) {
                    newPlayer.seekTo(0)
                    lastPosition = 0
                    _positionMs.value = 0
                }
                newPlayer.play()
            }
            startPolling()
        } catch (t: Throwable) {
            _error.value = t.message ?: "Tidak dapat membuat pratinjau."
            _player.value = null
            _isPlaying.value = false
        }
    }

    fun playPause() {
        val player = _player.value ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun play() {
        _player.value?.play()
    }

    fun pause() {
        _player.value?.pause()
    }

    fun seekTo(positionMs: Long) {
        val player = _player.value ?: return
        val safe = positionMs.coerceIn(0, lastProject?.durationMs ?: Long.MAX_VALUE)
        player.seekTo(safe)
        lastPosition = safe
        _positionMs.value = safe
    }

    /** Called when the user scrubs; keeps the position that will be restored after a rebuild. */
    fun notePosition(positionMs: Long) {
        lastPosition = positionMs
        _positionMs.value = positionMs
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                val player = _player.value ?: break
                val position = runCatching { player.currentPosition }.getOrDefault(0L)
                if (!player.isPlaying) {
                    // While paused, currentPosition is authoritative (seek, frame step).
                    lastPosition = position
                } else {
                    lastPosition = position
                }
                _positionMs.value = position
                delay(if (player.isPlaying) 50 else 150)
            }
        }
    }

    private fun releasePlayer() {
        pollJob?.cancel()
        pollJob = null
        _player.value?.let { player ->
            runCatching { player.removeListener(listener) }
            runCatching { player.pause() }
            runCatching { player.release() }
        }
        _player.value = null
        _isPlaying.value = false
    }

    fun release() {
        releasePlayer()
        scope.cancel()
    }
}
