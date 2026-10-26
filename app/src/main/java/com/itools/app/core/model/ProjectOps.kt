package com.itools.app.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Frame-accurate-ish keyframe interpolation used by both preview and export. */
object KeyframeMath {
    fun transformAt(clip: Clip, localTimeMs: Long): Transform {
        if (clip.keyframes.isEmpty()) return clip.transform
        val frames = clip.keyframes.sortedBy { it.timeMs }
        val (a, b, t) = segment(frames, localTimeMs) ?: return clip.transform
        return Transform(
            scaleX = lerp(a.scaleX, b.scaleX, t),
            scaleY = lerp(a.scaleY, b.scaleY, t),
            rotation = lerp(a.rotation, b.rotation, t),
            offsetX = lerp(a.offsetX, b.offsetX, t),
            offsetY = lerp(a.offsetY, b.offsetY, t)
        )
    }

    fun opacityAt(clip: Clip, localTimeMs: Long): Float {
        if (clip.keyframes.isEmpty()) return clip.opacity
        val frames = clip.keyframes.sortedBy { it.timeMs }
        val (a, b, t) = segment(frames, localTimeMs) ?: return clip.opacity
        return lerp(a.opacity, b.opacity, t) * clip.opacity
    }

    fun volumeAt(clip: Clip, localTimeMs: Long): Float {
        if (clip.keyframes.isEmpty()) return clip.effectiveVolume
        val frames = clip.keyframes.sortedBy { it.timeMs }
        val (a, b, t) = segment(frames, localTimeMs) ?: return clip.effectiveVolume
        return lerp(a.volume, b.volume, t) * (if (clip.muted) 0f else 1f)
    }

    private fun segment(frames: List<Keyframe>, timeMs: Long): Triple<Keyframe, Keyframe, Float>? {
        if (frames.isEmpty()) return null
        if (frames.size == 1) return Triple(frames[0], frames[0], 0f)
        if (timeMs <= frames.first().timeMs) return Triple(frames.first(), frames.first(), 0f)
        if (timeMs >= frames.last().timeMs) return Triple(frames.last(), frames.last(), 0f)
        for (i in 0 until frames.lastIndex) {
            val left = frames[i]
            val right = frames[i + 1]
            if (timeMs in left.timeMs..right.timeMs) {
                val span = (right.timeMs - left.timeMs).coerceAtLeast(1)
                return Triple(left, right, (timeMs - left.timeMs).toFloat() / span)
            }
        }
        return Triple(frames.last(), frames.last(), 0f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

/** Operations are pure functions so undo can be a simple snapshot stack. */
object ProjectOps {

    fun addMediaClip(
        project: Project,
        asset: com.itools.app.core.media.ImportedAsset,
        atMs: Long = -1,
        trackType: TrackType = if (asset.isAudio) TrackType.AUDIO else TrackType.VIDEO
    ): Pair<Project, Clip> {
        val type = when {
            asset.isAudio -> TrackType.AUDIO
            trackType == TrackType.OVERLAY -> TrackType.OVERLAY
            else -> TrackType.VIDEO
        }
        val track = project.tracks.firstOrNull { it.type == type } ?: Track(type = type)
        val base = if (project.tracks.none { it.id == track.id }) project.withTrack(track) else project
        val target = base.tracks.first { it.id == track.id }
        val start = if (atMs >= 0) atMs else target.durationMs
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
        val updated = base.updateTrack(target.id) { it.copy(clips = (it.clips + clip).sortedBy { c -> c.startMs }) }
        return updated to clip
    }

    fun addText(project: Project, atMs: Long, text: TextSpec = TextSpec()): Pair<Project, Clip> {
        val track = project.tracks.firstOrNull { it.type == TrackType.TEXT } ?: Track(type = TrackType.TEXT)
        val base = if (project.tracks.none { it.id == track.id }) project.withTrack(track) else project
        val clip = Clip(
            kind = ClipKind.TEXT,
            name = text.text.take(18),
            startMs = atMs,
            durationMs = 3_000,
            sourceDurationMs = 3_000,
            text = text
        )
        val updated = base.updateTrack(track.id) { it.copy(clips = it.clips + clip) }
        return updated to clip
    }

    fun addSticker(project: Project, atMs: Long, sticker: StickerSpec): Pair<Project, Clip> {
        val track = project.tracks.firstOrNull { it.type == TrackType.STICKER } ?: Track(type = TrackType.STICKER)
        val base = if (project.tracks.none { it.id == track.id }) project.withTrack(track) else project
        val clip = Clip(
            kind = ClipKind.STICKER,
            name = "Stiker ${sticker.emoji}",
            startMs = atMs,
            durationMs = 3_000,
            sourceDurationMs = 3_000,
            sticker = sticker
        )
        return base.updateTrack(track.id) { it.copy(clips = it.clips + clip) } to clip
    }

    fun addCaption(project: Project, atMs: Long, durationMs: Long, text: String): Pair<Project, Clip> {
        val track = project.tracks.firstOrNull { it.type == TrackType.CAPTION } ?: Track(type = TrackType.CAPTION)
        val base = if (project.tracks.none { it.id == track.id }) project.withTrack(track) else project
        val clip = Clip(
            kind = ClipKind.CAPTION,
            name = text.take(18),
            startMs = atMs,
            durationMs = durationMs,
            sourceDurationMs = durationMs,
            text = TextSpec(
                text = text,
                fontSize = 52f,
                color = 0xFFFFFFFF,
                background = 0x99000000,
                bold = true,
                strokeWidth = 0f,
                shadow = false,
                align = TextAlign.CENTER
            )
        )
        return base.updateTrack(track.id) { it.copy(clips = it.clips + clip) } to clip
    }

    fun split(project: Project, clipId: String, atMs: Long): Project {
        val clip = project.clip(clipId) ?: return project
        if (atMs <= clip.startMs + 80 || atMs >= clip.endMs - 80) return project
        val leftDuration = atMs - clip.startMs
        val speed = clip.speed.coerceAtLeast(0.05f)
        val leftSource = (leftDuration * speed).toLong()
        val right = clip.copy(
            id = java.util.UUID.randomUUID().toString(),
            startMs = atMs,
            durationMs = clip.durationMs - leftDuration,
            sourceStartMs = clip.sourceStartMs + leftSource,
            sourceDurationMs = (clip.sourceDurationMs - leftSource).coerceAtLeast(1),
            keyframes = clip.keyframes.filter { it.timeMs >= leftDuration }.map { it.copy(timeMs = it.timeMs - leftDuration) },
            transitionIn = null
        )
        val left = clip.copy(
            durationMs = leftDuration,
            sourceDurationMs = leftSource.coerceAtLeast(1),
            keyframes = clip.keyframes.filter { it.timeMs < leftDuration },
            transitionOut = null
        )
        return project.updateTrack(project.trackOf(clipId)!!.id) { track ->
            track.copy(clips = track.clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) }
                .sortedBy { it.startMs })
        }
    }

    fun trimStart(project: Project, clipId: String, deltaMs: Long): Project {
        val clip = project.clip(clipId) ?: return project
        val track = project.trackOf(clipId) ?: return project
        val prevEnd = track.clips
            .filter { it.id != clipId && it.endMs <= clip.startMs }
            .maxOfOrNull { it.endMs } ?: 0L
        val sourceLimit = clip.startMs - (clip.sourceStartMs / clip.speed.coerceAtLeast(0.05f)).toLong()
        val minStart = max(prevEnd, sourceLimit)
        val newStart = (clip.startMs + deltaMs).coerceAtLeast(minStart)
        val applied = newStart - clip.startMs
        if (abs(applied) < 10) return project
        val newDuration = clip.durationMs - applied
        if (newDuration < 100) return project
        val newSourceStart = (clip.sourceStartMs + (applied * clip.speed).toLong()).coerceAtLeast(0)
        return project.updateClip(clipId) {
            it.copy(
                startMs = newStart,
                durationMs = newDuration,
                sourceStartMs = newSourceStart,
                sourceDurationMs = (newDuration * clip.speed).toLong().coerceAtLeast(1),
                keyframes = it.keyframes.map { k -> k.copy(timeMs = (k.timeMs - applied).coerceAtLeast(0)) }
            )
        }
    }

    fun trimEnd(project: Project, clipId: String, deltaMs: Long): Project {
        val clip = project.clip(clipId) ?: return project
        val track = project.trackOf(clipId) ?: return project
        val nextStart = track.clips
            .filter { it.id != clipId && it.startMs >= clip.endMs }
            .minOfOrNull { it.startMs } ?: Long.MAX_VALUE
        val nextLimit = if (nextStart == Long.MAX_VALUE) Long.MAX_VALUE else nextStart - clip.startMs
        // Media clips can only be extended while the source still has frames/samples left. Images
        // and generated overlays have no such limit.
        val sourceLimit = if (
            (clip.kind == ClipKind.VIDEO || clip.kind == ClipKind.AUDIO) &&
            clip.sourceTotalMs > 0
        ) {
            val speed = clip.speed.coerceAtLeast(0.05f)
            val remainingSource = (clip.sourceTotalMs - clip.sourceStartMs).coerceAtLeast(0)
            (remainingSource / speed).toLong()
        } else {
            Long.MAX_VALUE
        }
        val maxDuration = min(nextLimit, sourceLimit)
            .coerceAtLeast(clip.durationMs) // never force the clip to shrink on a bad edit
        val minDuration = min(100L, maxDuration)
        val newDuration = (clip.durationMs + deltaMs).coerceIn(minDuration, maxDuration)
        if (newDuration == clip.durationMs) return project
        return project.updateClip(clipId) {
            it.copy(
                durationMs = newDuration,
                sourceDurationMs = (newDuration * clip.speed).toLong().coerceAtLeast(1),
                keyframes = it.keyframes.filter { k -> k.timeMs <= newDuration }
            )
        }
    }

    fun move(project: Project, clipId: String, newStartMs: Long): Project {
        val clip = project.clip(clipId) ?: return project
        val track = project.trackOf(clipId) ?: return project
        val others = track.clips.filter { it.id != clipId }.sortedBy { it.startMs }
        val prevEnd = others.filter { it.startMs < clip.startMs }.maxOfOrNull { it.endMs } ?: 0L
        val nextStart = others.filter { it.startMs > clip.startMs }.minOfOrNull { it.startMs } ?: Long.MAX_VALUE
        val upper = if (nextStart == Long.MAX_VALUE) Long.MAX_VALUE else nextStart - clip.durationMs
        val target = newStartMs.coerceIn(prevEnd, max(prevEnd, upper))
        return project.updateClip(clipId) { it.copy(startMs = target) }
    }

    fun deleteClip(project: Project, clipId: String): Project {
        val track = project.trackOf(clipId) ?: return project
        return project.updateTrack(track.id) { it.copy(clips = it.clips.filterNot { c -> c.id == clipId }) }
    }

    fun duplicateClip(project: Project, clipId: String): Pair<Project, Clip>? {
        val clip = project.clip(clipId) ?: return null
        val track = project.trackOf(clipId) ?: return null
        // Append after every existing clip so the copy can never overlap a neighbour.
        val start = max(clip.endMs, track.clips.maxOfOrNull { it.endMs } ?: 0L)
        val copy = clip.copy(
            id = java.util.UUID.randomUUID().toString(),
            startMs = start,
            transitionIn = null,
            keyframes = clip.keyframes.toList()
        )
        val updated = project.updateTrack(track.id) {
            it.copy(clips = (it.clips + copy).sortedBy { c -> c.startMs })
        }
        return updated to copy
    }

    fun setSpeed(project: Project, clipId: String, speed: Float): Project {
        val clip = project.clip(clipId) ?: return project
        val safe = speed.coerceIn(0.1f, 8f)
        if (clip.kind == ClipKind.TEXT || clip.kind == ClipKind.STICKER || clip.kind == ClipKind.CAPTION) return project
        val ratio = clip.speed / safe
        return project.updateClip(clipId) {
            it.copy(
                speed = safe,
                durationMs = (clip.durationMs * ratio).toLong().coerceAtLeast(100),
                keyframes = it.keyframes.map { k -> k.copy(timeMs = (k.timeMs * ratio).toLong()) }
            )
        }
    }

    fun setVolume(project: Project, clipId: String, volume: Float): Project =
        project.updateClip(clipId) { it.copy(volume = volume.coerceIn(0f, 2f)) }

    fun setFades(project: Project, clipId: String, fadeIn: Long, fadeOut: Long): Project =
        project.updateClip(clipId) {
            it.copy(
                fadeInMs = fadeIn.coerceIn(0, it.durationMs / 2),
                fadeOutMs = fadeOut.coerceIn(0, it.durationMs / 2)
            )
        }

    fun setFilter(project: Project, clipId: String, filterId: String, intensity: Float): Project {
        val preset = FilterPresets.byId(filterId)
        return project.updateClip(clipId) {
            it.copy(color = preset.grade.copy(filterId = filterId, intensity = intensity.coerceIn(0f, 1f)))
        }
    }

    fun setGrade(project: Project, clipId: String, grade: ColorGrade): Project =
        project.updateClip(clipId) { it.copy(color = grade) }

    fun setChroma(project: Project, clipId: String, chroma: ChromaSpec?): Project =
        project.updateClip(clipId) { it.copy(chroma = chroma) }

    fun setMask(project: Project, clipId: String, mask: MaskSpec?): Project =
        project.updateClip(clipId) { it.copy(mask = mask) }

    fun setTransform(project: Project, clipId: String, transform: Transform): Project =
        project.updateClip(clipId) { it.copy(transform = transform) }

    fun setOpacity(project: Project, clipId: String, opacity: Float): Project =
        project.updateClip(clipId) { it.copy(opacity = opacity.coerceIn(0f, 1f)) }

    fun setText(project: Project, clipId: String, text: TextSpec): Project =
        project.updateClip(clipId) { it.copy(text = text, name = text.text.take(18)) }

    fun setSticker(project: Project, clipId: String, sticker: StickerSpec): Project =
        project.updateClip(clipId) { it.copy(sticker = sticker, name = "Stiker ${sticker.emoji}") }

    fun setTransition(project: Project, clipId: String, incoming: Boolean, spec: TransitionSpec?): Project =
        project.updateClip(clipId) {
            if (incoming) it.copy(transitionIn = spec) else it.copy(transitionOut = spec)
        }

    fun addKeyframe(project: Project, clipId: String, keyframe: Keyframe): Project =
        project.updateClip(clipId) {
            val merged = (it.keyframes.filterNot { k -> abs(k.timeMs - keyframe.timeMs) < 16 } + keyframe)
                .sortedBy { k -> k.timeMs }
            it.copy(keyframes = merged)
        }

    fun removeKeyframe(project: Project, clipId: String, timeMs: Long): Project =
        project.updateClip(clipId) {
            it.copy(keyframes = it.keyframes.filterNot { k -> abs(k.timeMs - timeMs) < 16 })
        }

    fun clearKeyframes(project: Project, clipId: String): Project =
        project.updateClip(clipId) { it.copy(keyframes = emptyList()) }

    fun snapshotKeyframe(clip: Clip, localTimeMs: Long): Keyframe {
        val t = KeyframeMath.transformAt(clip, localTimeMs)
        return Keyframe(
            timeMs = localTimeMs.coerceIn(0, clip.durationMs),
            scaleX = t.scaleX,
            scaleY = t.scaleY,
            rotation = t.rotation,
            offsetX = t.offsetX,
            offsetY = t.offsetY,
            opacity = KeyframeMath.opacityAt(clip, localTimeMs),
            volume = KeyframeMath.volumeAt(clip, localTimeMs)
        )
    }

    fun setCanvas(project: Project, canvas: CanvasSpec): Project =
        project.copy(
            canvas = canvas,
            export = project.export.copy(width = canvas.width, height = canvas.height, fps = canvas.fps),
            updatedAt = System.currentTimeMillis()
        )

    fun setExport(project: Project, settings: ExportSettings): Project =
        project.copy(export = settings, updatedAt = System.currentTimeMillis())

    fun toggleMute(project: Project, trackId: String): Project =
        project.updateTrack(trackId) { it.copy(muted = !it.muted) }

    fun toggleHidden(project: Project, trackId: String): Project =
        project.updateTrack(trackId) { it.copy(hidden = !it.hidden) }

    fun rename(project: Project, name: String): Project =
        project.copy(name = name.ifBlank { project.name }, updatedAt = System.currentTimeMillis())

    fun moveTrack(project: Project, trackId: String, delta: Int): Project {
        val index = project.tracks.indexOfFirst { it.id == trackId }
        if (index < 0) return project
        val target = (index + delta).coerceIn(0, project.tracks.lastIndex)
        if (target == index) return project
        val mutable = project.tracks.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        return project.copy(tracks = mutable, updatedAt = System.currentTimeMillis())
    }

    fun setCaptions(project: Project, captions: List<Pair<Long, Pair<Long, String>>>): Project {
        val track = project.tracks.firstOrNull { it.type == TrackType.CAPTION } ?: Track(type = TrackType.CAPTION)
        val base = if (project.tracks.none { it.id == track.id }) project.withTrack(track) else project
        val clips = captions.map { (start, rest) ->
            Clip(
                kind = ClipKind.CAPTION,
                name = rest.second.take(18),
                startMs = start,
                durationMs = rest.first,
                sourceDurationMs = rest.first,
                text = TextSpec(
                    text = rest.second,
                    fontSize = 52f,
                    background = 0x99000000,
                    bold = true,
                    shadow = false
                )
            )
        }
        return base.updateTrack(track.id) { it.copy(clips = clips.sortedBy { c -> c.startMs }) }
    }

    fun removeCaptionTrack(project: Project): Project =
        project.copy(tracks = project.tracks.filterNot { it.type == TrackType.CAPTION })

    /** Adds a black padding clip so the main sequence stays gap-free for Media3. */
    fun totalDuration(project: Project): Long = project.durationMs

    fun clampPlayhead(project: Project, ms: Long): Long = ms.coerceIn(0, max(1, project.durationMs))
}
