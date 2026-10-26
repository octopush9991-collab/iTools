package com.itools.app.core.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max

/**
 * Root document of an iTools project.
 *
 * The file is plain JSON inside the project directory. Media files referenced by [Clip.uri]
 * are copied into the same directory so a project can be moved, backed up, or inspected
 * without any cloud service.
 */
@Serializable
data class Project(
    val schema: Int = SCHEMA,
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val canvas: CanvasSpec = CanvasSpec(),
    val tracks: List<Track> = emptyList(),
    val export: ExportSettings = ExportSettings(),
    val thumbnail: String? = null,
    val beatMarkersMs: List<Long> = emptyList(),
    /** Silence or analysis markers shown on the audio track. */
    val markersMs: List<Long> = emptyList()
) {
    val durationMs: Long
        get() = tracks.asSequence()
            .flatMap { it.clips.asSequence() }
            .maxOfOrNull { it.endMs } ?: 0L

    val videoTrack: Track?
        get() = tracks.firstOrNull { it.type == TrackType.VIDEO }

    val textClips: List<Clip>
        get() = tracks.filter { it.type == TrackType.TEXT || it.type == TrackType.CAPTION }
            .flatMap { it.clips }

    val audioTracks: List<Track>
        get() = tracks.filter { it.type == TrackType.AUDIO }

    fun trackOf(clipId: String): Track? = tracks.firstOrNull { t -> t.clips.any { it.id == clipId } }

    fun clip(clipId: String): Clip? = tracks.asSequence()
        .flatMap { it.clips.asSequence() }
        .firstOrNull { it.id == clipId }

    fun updateClip(clipId: String, block: (Clip) -> Clip): Project {
        val track = trackOf(clipId) ?: return this
        val updated = track.copy(clips = track.clips.map { if (it.id == clipId) block(it) else it })
        return copy(tracks = tracks.map { if (it.id == track.id) updated else it }, updatedAt = System.currentTimeMillis())
    }

    fun updateTrack(trackId: String, block: (Track) -> Track): Project =
        copy(tracks = tracks.map { if (it.id == trackId) block(it) else it }, updatedAt = System.currentTimeMillis())

    fun withTrack(track: Track): Project = copy(tracks = tracks + track, updatedAt = System.currentTimeMillis())

    companion object {
        const val SCHEMA = 1
    }
}

@Serializable
data class CanvasSpec(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val background: Long = 0xFF000000,
    val fit: FitMode = FitMode.FIT
) {
    val ratioLabel: String
        get() {
            val g = gcd(width, height)
            return "${width / g}:${height / g}"
        }

    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    companion object {
        private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

        val presets = listOf(
            CanvasSpec(1080, 1920, 30),
            CanvasSpec(1920, 1080, 30),
            CanvasSpec(1080, 1080, 30),
            CanvasSpec(1080, 1350, 30),
            CanvasSpec(2560, 1080, 30),
            CanvasSpec(2160, 3840, 30)
        )
    }
}

@Serializable
enum class FitMode { FIT, CROP, STRETCH }

@Serializable
data class Track(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType,
    val name: String = type.label,
    val clips: List<Clip> = emptyList(),
    val muted: Boolean = false,
    val hidden: Boolean = false,
    val locked: Boolean = false
) {
    val durationMs: Long get() = clips.maxOfOrNull { it.endMs } ?: 0L
}

@Serializable
enum class TrackType(val label: String, val visual: Boolean) {
    VIDEO("Video utama", true),
    OVERLAY("Overlay video", true),
    AUDIO("Audio", false),
    TEXT("Teks", true),
    STICKER("Stiker", true),
    CAPTION("Subtitle", true)
}

@Serializable
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val kind: ClipKind,
    val uri: String = "",
    val name: String = "Klip",
    /** Position on the project timeline, in milliseconds. */
    val startMs: Long = 0,
    /** Duration on the timeline after speed is applied. */
    val durationMs: Long = 3_000,
    /** Offset inside the source media, in milliseconds. */
    val sourceStartMs: Long = 0,
    /** Source range before speed is applied. */
    val sourceDurationMs: Long = 3_000,
    /**
     * Full duration of the source media, in milliseconds, before any trimming. Zero means
     * unknown (text/image clips and older project files). It caps how far a clip can be extended.
     */
    val sourceTotalMs: Long = 0,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val opacity: Float = 1f,
    val transform: Transform = Transform(),
    val color: ColorGrade = ColorGrade(),
    val chroma: ChromaSpec? = null,
    val mask: MaskSpec? = null,
    val text: TextSpec? = null,
    val sticker: StickerSpec? = null,
    val transitionIn: TransitionSpec? = null,
    val transitionOut: TransitionSpec? = null,
    val keyframes: List<Keyframe> = emptyList(),
    /** Reverse is produced as a local pre-render. Audio is intentionally dropped. */
    val reversed: Boolean = false,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val sourceRotation: Int = 0
) {
    val endMs: Long get() = startMs + durationMs
    val effectiveVolume: Float get() = if (muted) 0f else volume
    val isVisual: Boolean get() = kind != ClipKind.AUDIO
}

@Serializable
enum class ClipKind { VIDEO, IMAGE, AUDIO, TEXT, STICKER, CAPTION }

@Serializable
data class Transform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    val isIdentity: Boolean
        get() = scaleX == 1f && scaleY == 1f && rotation == 0f && offsetX == 0f && offsetY == 0f
}

@Serializable
data class ColorGrade(
    val filterId: String = "none",
    val intensity: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f
) {
    val isNeutral: Boolean
        get() = filterId == "none" && brightness == 0f && contrast == 1f && saturation == 1f &&
            temperature == 0f && tint == 0f && vignette == 0f && grain == 0f
}

@Serializable
data class ChromaSpec(
    val keyColor: Long = 0xFF00FF00,
    val similarity: Float = 0.32f,
    val smoothness: Float = 0.08f,
    val spill: Float = 0.25f
)

@Serializable
data class MaskSpec(
    val shape: MaskShape = MaskShape.LINEAR,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 1f,
    val height: Float = 1f,
    val rotation: Float = 0f,
    val feather: Float = 0.12f,
    val invert: Boolean = false
)

@Serializable
enum class MaskShape { LINEAR, CIRCLE, RECTANGLE }

@Serializable
data class TextSpec(
    val text: String = "Teks baru",
    val fontFamily: String = "sans",
    val fontSize: Float = 64f,
    val color: Long = 0xFFFFFFFF,
    val background: Long = 0x00000000,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val align: TextAlign = TextAlign.CENTER,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 0f,
    val shadow: Boolean = true,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1f,
    val animation: TextAnimation = TextAnimation.NONE
)

@Serializable
enum class TextAlign { LEFT, CENTER, RIGHT }

@Serializable
enum class TextAnimation { NONE, FADE_IN, SLIDE_UP, POP, TYPEWRITER }

@Serializable
data class StickerSpec(
    val emoji: String = "✨",
    val size: Float = 0.24f,
    val animation: TextAnimation = TextAnimation.POP
)

@Serializable
data class TransitionSpec(
    val type: TransitionType = TransitionType.FADE_BLACK,
    val durationMs: Long = 400
)

@Serializable
enum class TransitionType(val label: String) {
    NONE("Tidak ada"),
    FADE_BLACK("Fade hitam"),
    FADE_WHITE("Fade putih"),
    ZOOM("Zoom"),
    SLIDE_LEFT("Geser kiri"),
    SLIDE_UP("Geser atas")
}

@Serializable
data class Keyframe(
    val timeMs: Long,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val opacity: Float = 1f,
    val volume: Float = 1f
)

@Serializable
data class ExportSettings(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val codec: VideoCodec = VideoCodec.H264,
    val bitrateMbps: Int = 12,
    val fit: FitMode = FitMode.FIT,
    val includeAudio: Boolean = true
)

@Serializable
enum class VideoCodec(val label: String, val mime: String) {
    H264("H.264", "video/avc"),
    HEVC("HEVC / H.265", "video/hevc")
}

/** Applies [block] only when the clip actually changes, keeping undo history meaningful. */
fun Project.editClip(clipId: String, block: (Clip) -> Clip): Project {
    val before = clip(clipId) ?: return this
    val after = block(before)
    return if (after == before) this else updateClip(clipId) { after }
}

fun Clip.withTimelineDuration(duration: Long): Clip {
    val safe = max(1L, duration)
    return copy(durationMs = safe, sourceDurationMs = (safe * speed).toLong())
}
