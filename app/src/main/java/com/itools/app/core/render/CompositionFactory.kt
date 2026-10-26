package com.itools.app.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.itools.app.core.audio.VolumeAudioProcessor
import com.itools.app.core.effects.ChromaKeyEffect
import com.itools.app.core.effects.ColorGradeEffect
import com.itools.app.core.effects.KeyframedTransformEffect
import com.itools.app.core.effects.MaskEffect
import com.itools.app.core.effects.OpacityEffect
import com.itools.app.core.effects.ProjectOverlay
import com.itools.app.core.effects.TransitionEffect
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.FitMode
import com.itools.app.core.model.KeyframeMath
import com.itools.app.core.model.Project
import com.itools.app.core.model.Track
import com.itools.app.core.model.TrackType
import com.itools.app.core.project.ProjectStore
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Translates an iTools [Project] into a Media3 [Composition].
 *
 * The exact same builder is used for preview and export. The only differences are output size and
 * whether audio is included, which is why the exported file matches the preview.
 */
@OptIn(UnstableApi::class)
class CompositionFactory(
    private val context: Context,
    private val store: ProjectStore
) {

    data class Result(
        val composition: Composition,
        val width: Int,
        val height: Int,
        val overlayCount: Int
    )

    fun build(
        project: Project,
        outputWidth: Int,
        outputHeight: Int,
        includeAudio: Boolean,
        outputFps: Int = project.canvas.fps
    ): Result {
        val width = even(outputWidth)
        val height = even(outputHeight)
        val totalMs = max(1L, project.durationMs)
        val sequences = ArrayList<EditedMediaItemSequence>()
        val videoInputs = ArrayList<OverlayInput?>()
        var hasAudio = false

        val primaryTrack = project.tracks.firstOrNull { it.type == TrackType.VIDEO && !it.hidden }
        val primarySequence = buildVisualSequence(
            project = project,
            track = primaryTrack,
            totalMs = totalMs,
            outputWidth = width,
            outputHeight = height,
            primary = true,
            includeAudio = includeAudio && primaryTrack?.muted != true
        )
        if (primarySequence != null) {
            sequences.add(primarySequence.sequence)
            videoInputs.add(null)
            hasAudio = hasAudio || primarySequence.hasAudio
        } else {
            sequences.add(
                fallbackBlackSequence(width, height, totalMs, project.canvas.fps, project.canvas.background)
            )
            videoInputs.add(null)
        }

        // One video sequence per overlay track. Overlay placement is done by the compositor.
        for (track in project.tracks.filter { it.type == TrackType.OVERLAY && !it.hidden }) {
            val built = buildVisualSequence(
                project = project,
                track = track,
                totalMs = totalMs,
                outputWidth = width,
                outputHeight = height,
                primary = false,
                includeAudio = includeAudio && !track.muted
            ) ?: continue
            sequences.add(built.sequence)
            videoInputs.add(OverlayInput(track.id, track))
            hasAudio = hasAudio || built.hasAudio
        }

        // Audio-only tracks become their own sequences and are mixed by Media3.
        if (includeAudio) {
            for (track in project.tracks.filter { it.type == TrackType.AUDIO && !it.muted }) {
                val sequence = buildAudioSequence(project, track, totalMs) ?: continue
                sequences.add(sequence)
                hasAudio = true
            }
        }

        val overlays = buildOverlays(project, width, height)

        val compositionVideoEffects = ArrayList<androidx.media3.common.Effect>()
        if (overlays.isNotEmpty()) {
            compositionVideoEffects.add(OverlayEffect(overlays))
        }
        // FrameDropEffect only removes frames; it never invents higher frame rates. It is still
        // useful to honour 24/30 fps exports from 60 fps sources.
        compositionVideoEffects.add(
            FrameDropEffect.createDefaultFrameDropEffect(outputFps.coerceIn(1, 120).toFloat())
        )
        val compositionEffects = Effects(emptyList(), compositionVideoEffects)

        val builder = Composition.Builder(sequences)
            .setVideoCompositorSettings(
                ProjectVideoCompositorSettings(
                    outputWidth = width,
                    outputHeight = height,
                    videoInputs = videoInputs
                )
            )
            .setEffects(compositionEffects)
            .experimentalSetForceAudioTrack(includeAudio && hasAudio)

        return Result(builder.build(), width, height, overlays.size)
    }

    private data class OverlayInput(val trackId: String, val track: Track)

    private data class BuiltSequence(
        val sequence: EditedMediaItemSequence,
        val hasAudio: Boolean
    )

    private fun buildVisualSequence(
        project: Project,
        track: Track?,
        totalMs: Long,
        outputWidth: Int,
        outputHeight: Int,
        primary: Boolean,
        includeAudio: Boolean
    ): BuiltSequence? {
        if (track == null) return null
        val clips = track.clips
            .filter { it.isVisual && it.kind != ClipKind.TEXT && it.kind != ClipKind.STICKER }
            .sortedBy { it.startMs }
        if (clips.isEmpty()) return null

        val builder = EditedMediaItemSequence.Builder()
        var cursor = 0L
        var hasAudio = false
        for (clip in clips) {
            val start = clip.startMs.coerceAtLeast(0)
            if (start < cursor) continue // overlapping clips on one track are rejected by ProjectOps
            val gap = start - cursor
            if (gap > 0) {
                builder.addItem(
                    fillerItem(
                        gap, primary, outputWidth, outputHeight,
                        project.canvas.fps, project.canvas.background
                    )
                )
            }
            val item = buildVisualItem(
                project = project,
                clip = clip,
                primary = primary,
                outputWidth = outputWidth,
                outputHeight = outputHeight,
                includeAudio = includeAudio
            )
            if (item == null) {
                // Missing media must not shorten the composition: keep the clip's time slot.
                builder.addItem(
                    fillerItem(
                        clip.durationMs, primary, outputWidth, outputHeight,
                        project.canvas.fps, project.canvas.background
                    )
                )
            } else {
                builder.addItem(item.first)
                hasAudio = hasAudio || item.second
            }
            cursor = start + clip.durationMs
        }
        if (cursor < totalMs) {
            builder.addItem(
                fillerItem(
                    totalMs - cursor, primary, outputWidth, outputHeight,
                    project.canvas.fps, project.canvas.background
                )
            )
        }
        return BuiltSequence(builder.build(), hasAudio)
    }

    private fun buildVisualItem(
        project: Project,
        clip: Clip,
        primary: Boolean,
        outputWidth: Int,
        outputHeight: Int,
        includeAudio: Boolean
    ): Pair<EditedMediaItem, Boolean>? {
        val uri = resolve(project, clip) ?: return null
        val mediaItem = mediaItemFor(clip, uri)
        val videoEffects = ArrayList<androidx.media3.common.Effect>()
        val audioProcessors = ArrayList<AudioProcessor>()

        val speed = clip.speed.coerceIn(0.1f, 8f)
        val retimed = speed != 1f && clip.kind != ClipKind.IMAGE
        val wantsAudio = includeAudio && !clip.muted && clip.kind != ClipKind.IMAGE
        var audioVideoEffect: androidx.media3.common.Effect? = null
        var audioSpeedProcessor: AudioProcessor? = null
        if (retimed) {
            val provider = ConstantSpeedProvider(speed)
            if (wantsAudio) {
                val pair = Effects.createExperimentalSpeedChangingEffect(provider)
                audioSpeedProcessor = pair.first
                audioVideoEffect = pair.second
            } else {
                audioVideoEffect = androidx.media3.effect.SpeedChangeEffect(provider)
            }
        }

        audioVideoEffect?.let { videoEffects.add(it) }

        // Every video input is normalised to the canvas before compositing. Overlay placement and
        // scale are then applied by ProjectVideoCompositorSettings.
        videoEffects.add(
            Presentation.createForWidthAndHeight(
                outputWidth,
                outputHeight,
                layoutFor(project.export.fit)
            )
        )
        if (primary) {
            if (clip.keyframes.isNotEmpty() || !clip.transform.isIdentity) {
                videoEffects.add(KeyframedTransformEffect(clip))
            }
            if (clip.fadeInMs > 0 || clip.fadeOutMs > 0 || clip.opacity < 1f || clip.keyframes.isNotEmpty()) {
                videoEffects.add(OpacityEffect(clip))
            }
        }

        if (!clip.color.isNeutral) videoEffects.add(ColorGradeEffect(clip.color))
        clip.chroma?.let { videoEffects.add(ChromaKeyEffect(it)) }
        clip.mask?.let { videoEffects.add(MaskEffect(it)) }
        if (clip.transitionIn != null || clip.transitionOut != null) {
            videoEffects.add(TransitionEffect(clip))
        }

        if (wantsAudio) {
            if (audioSpeedProcessor != null) audioProcessors.add(audioSpeedProcessor)
            if (clip.volume != 1f || clip.fadeInMs > 0 || clip.fadeOutMs > 0 || clip.keyframes.isNotEmpty()) {
                audioProcessors.add(VolumeAudioProcessor(clip))
            }
        }

        // CompositionPlayer requires a duration for every item. Media3 documents this as the media
        // duration *before* clipping, so it must include sourceStartMs for trimmed clips.
        val durationUs = if (clip.kind == ClipKind.IMAGE) {
            clip.durationMs.coerceAtLeast(16) * 1_000
        } else {
            (clip.sourceStartMs + clip.sourceDurationMs).coerceAtLeast(1) * 1_000
        }
        val builder = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(!wantsAudio)
            .setRemoveVideo(false)
            .setDurationUs(durationUs)
            .setEffects(Effects(audioProcessors, videoEffects))
        if (clip.kind == ClipKind.IMAGE) {
            builder.setFrameRate(project.canvas.fps.coerceIn(1, 120))
        }
        return builder.build() to wantsAudio
    }

    private fun buildAudioSequence(
        project: Project,
        track: Track,
        totalMs: Long
    ): EditedMediaItemSequence? {
        val clips = track.clips.filter { it.kind == ClipKind.AUDIO }.sortedBy { it.startMs }
        if (clips.isEmpty()) return null
        val builder = EditedMediaItemSequence.Builder()
        var cursor = 0L
        for (clip in clips) {
            val start = clip.startMs.coerceAtLeast(0)
            if (start < cursor) continue
            if (start > cursor) appendSilence(builder, start - cursor)
            val uri = resolve(project, clip)
            if (uri == null) {
                // Missing media must not shorten the sequence: keep the clip's time slot as silence.
                appendSilence(builder, clip.durationMs)
                cursor = start + clip.durationMs
                continue
            }
            val mediaItem = mediaItemFor(clip, uri)
            val processors = ArrayList<AudioProcessor>()
            val speed = clip.speed.coerceIn(0.1f, 8f)
            if (speed != 1f) {
                processors.add(SpeedChangingAudioProcessor(ConstantSpeedProvider(speed)))
            }
            if (clip.volume != 1f || clip.fadeInMs > 0 || clip.fadeOutMs > 0 || clip.keyframes.isNotEmpty()) {
                processors.add(VolumeAudioProcessor(clip))
            }
            val item = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .setDurationUs(
                    (clip.sourceStartMs + clip.sourceDurationMs).coerceAtLeast(1) * 1_000
                )
                .setEffects(Effects(processors, emptyList()))
                .build()
            builder.addItem(item)
            cursor = start + clip.durationMs
        }
        if (cursor < totalMs) appendSilence(builder, totalMs - cursor)
        return builder.build()
    }

    /**
     * CompositionPlayer rejects compositions that contain gaps, so silence is inserted as a real
     * audio item. Files are small (8 kHz mono PCM) and cached by duration.
     */
    private fun appendSilence(builder: EditedMediaItemSequence.Builder, durationMs: Long) {
        var remaining = durationMs
        while (remaining > 0) {
            val chunk = min(remaining, 10_000L)
            val file = silentFile(chunk)
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0)
                        .setEndPositionMs(chunk)
                        .build()
                )
                .build()
            builder.addItem(
                EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .setDurationUs(chunk * 1_000)
                    .build()
            )
            remaining -= chunk
        }
    }

    private fun silentFile(durationMs: Long): File {
        val dir = File(context.cacheDir, "generated").apply { mkdirs() }
        val rounded = (durationMs / 100).coerceAtLeast(1) * 100
        val file = File(dir, "silence_${rounded}ms.wav")
        if (file.exists()) return file
        val rate = 8_000
        val samples = FloatArray(((rounded * rate) / 1_000).toInt().coerceAtLeast(1))
        com.itools.app.core.audio.WavWriter.write(file, samples, rate, channels = 1)
        return file
    }

    /**
     * Media3 only supports gaps in audio sequences. Visual holes are filled with a black frame
     * (primary) or a transparent frame (overlay, hidden by alpha=0 in the compositor).
     */
    private fun fillerItem(
        durationMs: Long,
        primary: Boolean,
        outputWidth: Int,
        outputHeight: Int,
        fps: Int,
        background: Long
    ): EditedMediaItem {
        val file = fillerFile(primary, background)
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setImageDurationMs(durationMs.coerceAtLeast(16))
            .build()
        val videoEffects = if (primary) {
            listOf<androidx.media3.common.Effect>(
                Presentation.createForWidthAndHeight(
                    outputWidth,
                    outputHeight,
                    Presentation.LAYOUT_STRETCH_TO_FIT
                )
            )
        } else {
            emptyList()
        }
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setDurationUs(durationMs.coerceAtLeast(16) * 1_000)
            .setFrameRate(fps.coerceIn(24, 60))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
    }

    private fun fillerFile(primary: Boolean, background: Long): File {
        val dir = File(context.cacheDir, "generated").apply { mkdirs() }
        val colorHex = background.toInt().toUInt().toString(16)
        val name = if (primary) "filler_$colorHex.png" else "filler_transparent.png"
        val file = File(dir, name)
        if (file.exists()) return file
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(if (primary) background.toInt() else Color.TRANSPARENT)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun mediaItemFor(clip: Clip, uri: Uri): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        if (clip.kind == ClipKind.IMAGE) {
            builder.setImageDurationMs(clip.durationMs.coerceAtLeast(100))
        } else if (clip.kind == ClipKind.VIDEO || clip.kind == ClipKind.AUDIO) {
            val startMs = clip.sourceStartMs.coerceAtLeast(0)
            val endMs = (clip.sourceStartMs + clip.sourceDurationMs).coerceAtLeast(startMs + 50)
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
        }
        return builder.build()
    }

    private fun buildOverlays(
        project: Project,
        outputWidth: Int,
        outputHeight: Int
    ): List<ProjectOverlay> {
        val result = ArrayList<ProjectOverlay>()
        for (track in project.tracks) {
            if (track.hidden) continue
            if (track.type != TrackType.TEXT &&
                track.type != TrackType.STICKER &&
                track.type != TrackType.CAPTION
            ) continue
            for (clip in track.clips.sortedBy { it.startMs }) {
                if (clip.kind == ClipKind.TEXT ||
                    clip.kind == ClipKind.STICKER ||
                    clip.kind == ClipKind.CAPTION
                ) {
                    result.add(ProjectOverlay(clip, project.canvas, outputWidth, outputHeight))
                }
            }
        }
        return result
    }

    private fun fallbackBlackSequence(
        outputWidth: Int,
        outputHeight: Int,
        totalMs: Long,
        fps: Int,
        background: Long
    ): EditedMediaItemSequence {
        val file = blackFrameFile(background)
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setImageDurationMs(totalMs.coerceAtLeast(100))
            .build()
        val item = EditedMediaItem.Builder(mediaItem)
            .setDurationUs(totalMs.coerceAtLeast(100) * 1_000)
            .setFrameRate(fps.coerceIn(24, 60))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            outputWidth,
                            outputHeight,
                            Presentation.LAYOUT_STRETCH_TO_FIT
                        )
                    )
                )
            )
            .build()
        return EditedMediaItemSequence.Builder(item).build()
    }

    private fun blackFrameFile(background: Long): File {
        val dir = File(context.cacheDir, "generated").apply { mkdirs() }
        val colorHex = background.toInt().toUInt().toString(16)
        val file = File(dir, "background_$colorHex.png")
        if (file.exists()) return file
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(background.toInt())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun resolve(project: Project, clip: Clip): Uri? {
        if (clip.uri.isBlank()) return null
        val uri = store.resolve(project.id, clip.uri)
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            if (!File(path).exists()) return null
        }
        return uri
    }

    private fun even(value: Int): Int = (value.coerceIn(2, 7_680) / 2) * 2

    private fun layoutFor(fit: FitMode): Int = when (fit) {
        FitMode.FIT -> Presentation.LAYOUT_SCALE_TO_FIT
        FitMode.CROP -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        FitMode.STRETCH -> Presentation.LAYOUT_STRETCH_TO_FIT
    }

    private class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    /**
     * Gives every video input its place on the canvas.
     *
     * Input 0 is the primary sequence and always fills the frame. Overlay sequences are positioned
     * and faded using the clip active at the current composition timestamp.
     */
    private class ProjectVideoCompositorSettings(
        private val outputWidth: Int,
        private val outputHeight: Int,
        private val videoInputs: List<OverlayInput?>
    ) : VideoCompositorSettings {

        override fun getOutputSize(inputSizes: List<Size>): Size = Size(outputWidth, outputHeight)

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            if (inputId !in videoInputs.indices) return object : OverlaySettings {}
            val input = videoInputs[inputId] ?: return object : OverlaySettings {}
            val timeMs = presentationTimeUs / 1_000
            val clip = input.track.clips.firstOrNull { timeMs in it.startMs until it.endMs }
                ?: return StaticZeroOverlay
            val localMs = (timeMs - clip.startMs).coerceIn(0, clip.durationMs)
            val transform = KeyframeMath.transformAt(clip, localMs)
            var alpha = KeyframeMath.opacityAt(clip, localMs).coerceIn(0f, 1f)
            if (clip.fadeInMs > 0) {
                alpha *= smooth((localMs.toFloat() / clip.fadeInMs).coerceIn(0f, 1f))
            }
            if (clip.fadeOutMs > 0) {
                alpha *= smooth(
                    ((clip.durationMs - localMs).toFloat() / clip.fadeOutMs).coerceIn(0f, 1f)
                )
            }
            return androidx.media3.effect.StaticOverlaySettings.Builder()
                .setAlphaScale(alpha)
                .setBackgroundFrameAnchor(
                    transform.offsetX.coerceIn(-1f, 1f),
                    transform.offsetY.coerceIn(-1f, 1f)
                )
                .setOverlayFrameAnchor(0f, 0f)
                .setScale(
                    transform.scaleX.coerceAtLeast(0.01f),
                    transform.scaleY.coerceAtLeast(0.01f)
                )
                .setRotationDegrees(transform.rotation)
                .build()
        }

        private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

        private companion object {
            val StaticZeroOverlay: OverlaySettings =
                androidx.media3.effect.StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        }
    }

    companion object {

        /**
         * Preview dimensions: preserve the canvas ratio, cap the short edge to keep GPU load and
         * memory low on phones. Export uses the requested export dimensions instead.
         */
        fun previewSize(project: Project, maxShortEdge: Int = 720): Pair<Int, Int> {
            val canvas = project.canvas
            val short = min(canvas.width, canvas.height).coerceAtLeast(1)
            if (short <= maxShortEdge) return canvas.width to canvas.height
            val scale = maxShortEdge.toFloat() / short
            return even((canvas.width * scale).toInt()) to even((canvas.height * scale).toInt())
        }

        fun exportSize(project: Project): Pair<Int, Int> {
            val export = project.export
            val w = export.width.coerceIn(144, 7_680)
            val h = export.height.coerceIn(144, 7_680)
            return even(w) to even(h)
        }

        private fun even(value: Int): Int = (value.coerceAtLeast(2) / 2) * 2
    }
}
