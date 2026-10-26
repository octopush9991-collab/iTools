package com.itools.app.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itools.app.core.model.Clip
import com.itools.app.core.model.Project
import com.itools.app.core.model.Track
import com.itools.app.core.model.TrackType
import com.itools.app.core.model.formatDuration
import com.itools.app.core.model.formatTimecode
import com.itools.app.ui.theme.IToolsPalette
import kotlin.math.max
import kotlin.math.min

private val RulerHeight = 28.dp
private val TrackRowHeight = 52.dp
private val HeaderWidth = 86.dp
private val HeaderGap = 4.dp

/**
 * Gesture timeline drawn with Canvas. It supports:
 *  - tap a clip to select it and move the playhead,
 *  - tap empty space to deselect,
 *  - drag the middle of a clip to move it,
 *  - drag either edge (18 dp) to trim,
 *  - drag the ruler to scrub.
 *
 * Clips on a track never overlap; move/trim operations clamp to their neighbours.
 */
@Composable
fun TimelineView(
    project: Project,
    selectedClipId: String?,
    playheadMs: Long,
    dpPerSecond: Float,
    onSelectClip: (String?) -> Unit,
    onScrub: (Long) -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val rulerPx = with(density) { RulerHeight.toPx() }
    val rowPx = with(density) { TrackRowHeight.toPx() }
    val trimPx = with(density) { 18.dp.toPx() }
    val pxPerSecond = with(density) { dpPerSecond.dp.toPx() }
    val durationSec = max(project.durationMs, 5_000L) / 1000f
    val contentHeight = RulerHeight + TrackRowHeight * project.tracks.size + 6.dp

    val currentProject = rememberUpdatedState(project)
    val currentPlayhead = rememberUpdatedState(playheadMs)
    val currentScale = rememberUpdatedState(pxPerSecond)
    val currentSelect = rememberUpdatedState(onSelectClip)
    val currentScrub = rememberUpdatedState(onScrub)
    val currentMove = rememberUpdatedState(onMoveClip)
    val currentTrimStart = rememberUpdatedState(onTrimStart)
    val currentTrimEnd = rememberUpdatedState(onTrimEnd)
    val currentBegin = rememberUpdatedState(onGestureStart)
    val currentEnd = rememberUpdatedState(onGestureEnd)

    val labelPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            color = android.graphics.Color.WHITE
        }
    }
    val rulerPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            color = android.graphics.Color.argb(190, 200, 215, 210)
            textAlign = Paint.Align.LEFT
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1013))
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewportDp = maxWidth - HeaderWidth - HeaderGap
            val contentWidth: Dp = maxOf(
                viewportDp,
                (durationSec * dpPerSecond).dp + 36.dp
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 292.dp)
                    .verticalScroll(vertical)
                    .padding(bottom = 4.dp)
            ) {
                TrackHeaders(
                    tracks = project.tracks,
                    selectedClipId = selectedClipId,
                    onToggleMute = onToggleMute,
                    onToggleHidden = onToggleHidden
                )
                Spacer(Modifier.width(HeaderGap))
                Box(
                    Modifier
                        .width(viewportDp)
                        .height(contentHeight)
                        .horizontalScroll(horizontal)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(contentWidth)
                            .height(contentHeight)
                            .pointerInput(Unit) {
                                var mode: DragMode = DragMode.None
                                var clipId: String? = null
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val data = currentProject.value
                                        val scale = currentScale.value
                                        val time = timeAt(offset.x, scale, data.durationMs)
                                        val trackIndex = trackIndexAt(offset.y, rulerPx, rowPx)
                                        clipId = null
                                        mode = if (offset.y < rulerPx || trackIndex == null ||
                                            trackIndex >= data.tracks.size
                                        ) {
                                            currentScrub.value(time)
                                            DragMode.Scrub
                                        } else {
                                            val track = data.tracks[trackIndex]
                                            val hit = clipAt(track, offset.x, scale)
                                            if (hit == null) {
                                                currentSelect.value(null)
                                                currentScrub.value(time)
                                                DragMode.Scrub
                                            } else {
                                                currentSelect.value(hit.id)
                                                clipId = hit.id
                                                val left = hit.startMs / 1000f * scale
                                                val right = hit.endMs / 1000f * scale
                                                val zone = when {
                                                    offset.x - left <= trimPx -> DragMode.TrimStart
                                                    right - offset.x <= trimPx -> DragMode.TrimEnd
                                                    else -> DragMode.Move
                                                }
                                                currentBegin.value()
                                                zone
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        val scale = currentScale.value
                                        val deltaMs = (dragAmount.x / scale * 1000f).toLong()
                                        when (mode) {
                                            DragMode.Scrub -> {
                                                currentScrub.value(
                                                    timeAt(
                                                        change.position.x,
                                                        scale,
                                                        currentProject.value.durationMs
                                                    )
                                                )
                                            }
                                            DragMode.Move -> clipId?.let { currentMove.value(it, deltaMs) }
                                            DragMode.TrimStart -> clipId?.let { currentTrimStart.value(it, deltaMs) }
                                            DragMode.TrimEnd -> clipId?.let { currentTrimEnd.value(it, deltaMs) }
                                            DragMode.None -> Unit
                                        }
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (mode != DragMode.Scrub && mode != DragMode.None) {
                                            currentEnd.value()
                                        }
                                        mode = DragMode.None
                                        clipId = null
                                    },
                                    onDragCancel = {
                                        if (mode != DragMode.Scrub && mode != DragMode.None) {
                                            currentEnd.value()
                                        }
                                        mode = DragMode.None
                                        clipId = null
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val data = currentProject.value
                                    val scale = currentScale.value
                                    val time = timeAt(offset.x, scale, data.durationMs)
                                    val trackIndex = trackIndexAt(offset.y, rulerPx, rowPx)
                                    if (offset.y < rulerPx || trackIndex == null ||
                                        trackIndex >= data.tracks.size
                                    ) {
                                        currentScrub.value(time)
                                        return@detectTapGestures
                                    }
                                    val track = data.tracks[trackIndex]
                                    val hit = clipAt(track, offset.x, scale)
                                    if (hit == null) {
                                        currentSelect.value(null)
                                    } else {
                                        currentSelect.value(hit.id)
                                    }
                                    currentScrub.value(time)
                                }
                            }
                    ) {
                        drawTimeline(
                            project = project,
                            selectedClipId = selectedClipId,
                            playheadMs = playheadMs,
                            pxPerSecond = pxPerSecond,
                            rulerPx = rulerPx,
                            rowPx = rowPx,
                            labelPaint = labelPaint,
                            rulerPaint = rulerPaint
                        )
                    }
                }
            }
        }
    }
}

private enum class DragMode { None, Scrub, Move, TrimStart, TrimEnd }

@Composable
private fun TrackHeaders(
    tracks: List<Track>,
    selectedClipId: String?,
    onToggleMute: (String) -> Unit,
    onToggleHidden: (String) -> Unit
) {
    Column(Modifier.width(HeaderWidth)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RulerHeight)
                .padding(start = 5.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "TRACK",
                style = MaterialTheme.typography.labelSmall,
                color = IToolsPalette.TextMuted,
                fontSize = 9.sp
            )
        }
        tracks.forEach { track ->
            val color = trackColor(track.type)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackRowHeight)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = if (track.hidden) 0.35f else 1f))
                )
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = shortName(track.type),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (track.hidden) IToolsPalette.TextMuted else IToolsPalette.TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        HeaderIcon(
                            icon = if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            active = !track.muted,
                            onClick = { onToggleMute(track.id) }
                        )
                        HeaderIcon(
                            icon = if (track.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            active = !track.hidden,
                            onClick = { onToggleHidden(track.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) IToolsPalette.TextMuted else IToolsPalette.Danger,
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun DrawScope.drawTimeline(
    project: Project,
    selectedClipId: String?,
    playheadMs: Long,
    pxPerSecond: Float,
    rulerPx: Float,
    rowPx: Float,
    labelPaint: Paint,
    rulerPaint: Paint
) {
    val width = size.width
    val height = size.height
    val background = Color(0xFF0B1013)
    val rulerBg = Color(0xFF121A1E)
    val border = Color(0xFF243035)
    drawRect(background)
    drawRect(rulerBg, size = androidx.compose.ui.geometry.Size(width, rulerPx))
    drawLine(border, androidx.compose.ui.geometry.Offset(0f, rulerPx), androidx.compose.ui.geometry.Offset(width, rulerPx), 1.dp.toPx())

    // Ruler ticks.
    val intervalSec = when {
        pxPerSecond >= 92f -> 1
        pxPerSecond >= 50f -> 2
        pxPerSecond >= 27f -> 5
        else -> 10
    }
    val maxSec = (width / pxPerSecond).toInt() + intervalSec
    var sec = 0
    while (sec <= maxSec) {
        val x = sec * pxPerSecond
        val major = sec % (intervalSec * 2) == 0
        drawLine(
            color = Color.White.copy(alpha = if (major) 0.30f else 0.15f),
            start = androidx.compose.ui.geometry.Offset(x, if (major) rulerPx * 0.48f else rulerPx * 0.68f),
            end = androidx.compose.ui.geometry.Offset(x, rulerPx),
            strokeWidth = 1.dp.toPx()
        )
        if (major) {
            rulerPaint.textSize = 9.sp.toPx()
            drawContext.canvas.nativeCanvas.drawText(
                clockLabel(sec * 1000L),
                x + 3.dp.toPx(),
                rulerPx * 0.45f,
                rulerPaint
            )
        }
        sec += intervalSec
    }

    // Beat markers are subtle vertical lines.
    project.beatMarkersMs.take(400).forEach { at ->
        val x = at / 1000f * pxPerSecond
        if (x in 0f..width) {
            drawLine(
                IToolsPalette.Accent.copy(alpha = 0.23f),
                androidx.compose.ui.geometry.Offset(x, rulerPx),
                androidx.compose.ui.geometry.Offset(x, height),
                1.dp.toPx()
            )
        }
    }

    // Rows and clips.
    project.tracks.forEachIndexed { index, track ->
        val top = rulerPx + index * rowPx
        val rowBottom = top + rowPx
        drawRect(
            if (index % 2 == 0) Color(0xFF0E1418) else Color(0xFF10171B),
            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(width, rowPx)
        )
        drawLine(
            border.copy(alpha = 0.65f),
            androidx.compose.ui.geometry.Offset(0f, rowBottom),
            androidx.compose.ui.geometry.Offset(width, rowBottom),
            1.dp.toPx()
        )
        val color = trackColor(track.type)
        track.clips.forEach { clip ->
            val left = clip.startMs / 1000f * pxPerSecond
            val right = clip.endMs / 1000f * pxPerSecond
            if (right < 0f || left > width) return@forEach
            val blockTop = top + 5.dp.toPx()
            val blockBottom = rowBottom - 5.dp.toPx()
            val blockWidth = (right - left).coerceAtLeast(3.dp.toPx())
            val clipColor = when (clip.kind) {
                com.itools.app.core.model.ClipKind.AUDIO -> IToolsPalette.Audio
                com.itools.app.core.model.ClipKind.IMAGE -> Color(0xFF58A6FF)
                com.itools.app.core.model.ClipKind.TEXT -> IToolsPalette.TextTrack
                com.itools.app.core.model.ClipKind.STICKER -> IToolsPalette.StickerTrack
                com.itools.app.core.model.ClipKind.CAPTION -> IToolsPalette.CaptionTrack
                else -> color
            }
            val alpha = when {
                track.hidden -> 0.25f
                track.muted && track.type == TrackType.AUDIO -> 0.45f
                else -> 1f
            }
            drawRoundRect(
                color = clipColor.copy(alpha = 0.78f * alpha),
                topLeft = androidx.compose.ui.geometry.Offset(left + 1.dp.toPx(), blockTop),
                size = androidx.compose.ui.geometry.Size(
                    blockWidth - 2.dp.toPx(),
                    (blockBottom - blockTop).coerceAtLeast(8.dp.toPx())
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )
            drawRoundRect(
                color = clipColor.copy(alpha = alpha),
                topLeft = androidx.compose.ui.geometry.Offset(left + 1.dp.toPx(), blockTop),
                size = androidx.compose.ui.geometry.Size(
                    blockWidth - 2.dp.toPx(),
                    (blockBottom - blockTop).coerceAtLeast(8.dp.toPx())
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                style = Stroke(1.dp.toPx())
            )
            if (clip.id == selectedClipId) {
                drawRoundRect(
                    color = IToolsPalette.Accent,
                    topLeft = androidx.compose.ui.geometry.Offset(left - 1.dp.toPx(), blockTop - 1.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        blockWidth + 2.dp.toPx(),
                        blockBottom - blockTop + 2.dp.toPx()
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()),
                    style = Stroke(2.dp.toPx())
                )
                drawTrimHandles(left, right, blockTop, blockBottom, blockWidth)
            }
            drawClipLabel(
                clip = clip,
                left = left,
                right = left + blockWidth,
                top = blockTop,
                bottom = blockBottom,
                paint = labelPaint
            )
        }

        // Silence markers sit on audio rows as small triangles.
        if (track.type == TrackType.AUDIO) {
            project.markersMs.take(120).forEach { at ->
                val x = at / 1000f * pxPerSecond
                if (x in 0f..width) {
                    val path = Path().apply {
                        moveTo(x, rowBottom - 4.dp.toPx())
                        lineTo(x + 6.dp.toPx(), rowBottom - 4.dp.toPx())
                        lineTo(x + 3.dp.toPx(), rowBottom - 11.dp.toPx())
                        close()
                    }
                    drawPath(path, IToolsPalette.Danger.copy(alpha = 0.8f))
                }
            }
        }
    }

    // Empty state hint over the video row.
    if (project.tracks.flatMap { it.clips }.isEmpty()) {
        labelPaint.textSize = 11.sp.toPx()
        labelPaint.color = android.graphics.Color.argb(180, 170, 190, 180)
        drawContext.canvas.nativeCanvas.drawText(
            "Ketuk Media untuk menambahkan klip pertama",
            14.dp.toPx(),
            rulerPx + rowPx * 0.58f,
            labelPaint
        )
    }

    // Playhead.
    val playheadX = (playheadMs / 1000f) * pxPerSecond
    if (playheadX in 0f..width) {
        drawLine(
            IToolsPalette.Playhead,
            androidx.compose.ui.geometry.Offset(playheadX, rulerPx * 0.32f),
            androidx.compose.ui.geometry.Offset(playheadX, height),
            2.dp.toPx()
        )
        val path = Path().apply {
            moveTo(playheadX - 5.dp.toPx(), 1.dp.toPx())
            lineTo(playheadX + 5.dp.toPx(), 1.dp.toPx())
            lineTo(playheadX, 8.dp.toPx())
            close()
        }
        drawPath(path, IToolsPalette.Playhead)
        labelPaint.textSize = 9.sp.toPx()
        labelPaint.color = android.graphics.Color.WHITE
        drawContext.canvas.nativeCanvas.drawText(
            formatTimecode(playheadMs),
            (playheadX + 5.dp.toPx()).coerceAtMost(width - 54.dp.toPx()),
            18.dp.toPx(),
            labelPaint
        )
    }

    // Project end marker.
    val endX = projectorEnd(project) / 1000f * pxPerSecond
    if (endX in 0f..width) {
        drawLine(
            Color.White.copy(alpha = 0.25f),
            androidx.compose.ui.geometry.Offset(endX, rulerPx),
            androidx.compose.ui.geometry.Offset(endX, height),
            1.dp.toPx()
        )
    }
}

private fun DrawScope.drawTrimHandles(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    blockWidth: Float
) {
    if (blockWidth < 16.dp.toPx()) return
    val handleWidth = 3.dp.toPx()
    val inset = 1.dp.toPx()
    drawRoundRect(
        IToolsPalette.Accent.copy(alpha = 0.9f),
        topLeft = androidx.compose.ui.geometry.Offset(left + inset, top + 6.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(handleWidth, bottom - top - 12.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
    )
    drawRoundRect(
        IToolsPalette.Accent.copy(alpha = 0.9f),
        topLeft = androidx.compose.ui.geometry.Offset(right - inset - handleWidth, top + 6.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(handleWidth, bottom - top - 12.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
    )
}

private fun DrawScope.drawClipLabel(
    clip: Clip,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    paint: Paint
) {
    if (right - left < 28.dp.toPx()) return
    val canvas = drawContext.canvas.nativeCanvas
    val padding = 5.dp.toPx()
    canvas.save()
    canvas.clipRect(left + padding, top, right - padding, bottom)
    paint.textSize = 10.sp.toPx()
    paint.color = android.graphics.Color.WHITE
    val baseline = (top + bottom) / 2f - (paint.ascent() + paint.descent()) / 2f - 1.dp.toPx()
    canvas.drawText(clip.name, left + padding, baseline, paint)
    if (!clip.isVisual) {
        paint.textSize = 8.sp.toPx()
        paint.color = android.graphics.Color.argb(190, 255, 255, 255)
        canvas.drawText(
            formatDuration(clip.durationMs),
            left + padding,
            baseline + 10.dp.toPx(),
            paint
        )
    }
    canvas.restore()
}

private fun timeAt(x: Float, pxPerSecond: Float, durationMs: Long): Long {
    if (pxPerSecond <= 0f) return 0L
    return ((x / pxPerSecond) * 1000f).toLong().coerceIn(0L, max(1L, durationMs))
}

private fun trackIndexAt(y: Float, rulerPx: Float, rowPx: Float): Int? {
    if (y < rulerPx) return null
    val index = ((y - rulerPx) / rowPx).toInt()
    return index.takeIf { it >= 0 }
}

private fun clipAt(track: Track, x: Float, pxPerSecond: Float): Clip? =
    track.clips.firstOrNull {
        val left = it.startMs / 1000f * pxPerSecond
        val right = it.endMs / 1000f * pxPerSecond
        x in left..right
    }

private fun trackColor(type: TrackType): Color = when (type) {
    TrackType.VIDEO -> IToolsPalette.Video
    TrackType.OVERLAY -> IToolsPalette.OverlayTrack
    TrackType.AUDIO -> IToolsPalette.Audio
    TrackType.TEXT -> IToolsPalette.TextTrack
    TrackType.STICKER -> IToolsPalette.StickerTrack
    TrackType.CAPTION -> IToolsPalette.CaptionTrack
}

private fun shortName(type: TrackType): String = when (type) {
    TrackType.VIDEO -> "Video"
    TrackType.OVERLAY -> "Overlay"
    TrackType.AUDIO -> "Audio"
    TrackType.TEXT -> "Teks"
    TrackType.STICKER -> "Stiker"
    TrackType.CAPTION -> "Subtitle"
}

private fun clockLabel(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun projectorEnd(project: Project): Long =
    project.tracks.flatMap { it.clips }.maxOfOrNull { it.endMs } ?: 0L
