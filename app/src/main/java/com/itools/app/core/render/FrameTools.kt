package com.itools.app.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.Project
import com.itools.app.core.project.ProjectStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Local frame operations.
 *
 * Freeze-frame is exact: a still bitmap is extracted and inserted as an image clip.
 *
 * Reverse is a local pre-render. Frames are sampled at [REVERSE_FPS], ordered backwards and encoded
 * with Media3 Transformer. Audio is dropped because reversing AAC audio is not meaningful and the
 * platform does not expose a reliable reverse-audio codec path. Short clips only.
 */
@OptIn(UnstableApi::class)
class FrameTools(
    private val context: Context,
    private val store: ProjectStore
) {

    data class FreezeResult(val clip: Clip, val relativeUri: String)
    data class ReverseResult(val relativeUri: String, val durationMs: Long, val frameCount: Int)

    suspend fun freezeFrame(
        project: Project,
        clip: Clip,
        localTimeMs: Long,
        durationMs: Long = 2_500
    ): FreezeResult? = withContext(Dispatchers.IO) {
        val uri = store.resolve(project.id, clip.uri)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val sourceMs = clip.sourceStartMs +
                (localTimeMs.coerceIn(0, clip.durationMs) * clip.speed).toLong()
            val bitmap = retriever.getFrameAtTime(
                sourceMs.coerceAtLeast(0) * 1_000,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: return@withContext null
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: clip.sourceRotation
            val oriented = rotate(bitmap, rotation)
            val (file, relative) = store.newAsset(project.id, "freeze", "jpg")
            file.outputStream().use { out ->
                oriented.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            val frozen = Clip(
                kind = ClipKind.IMAGE,
                uri = relative,
                name = "Bekukan ${clip.name}",
                startMs = clip.startMs + localTimeMs,
                durationMs = durationMs.coerceIn(300, 15_000),
                sourceStartMs = 0,
                sourceDurationMs = durationMs.coerceIn(300, 15_000),
                sourceWidth = oriented.width,
                sourceHeight = oriented.height,
                sourceRotation = 0
            )
            oriented.recycle()
            FreezeResult(frozen, relative)
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun reverse(
        project: Project,
        clip: Clip,
        onProgress: (Int) -> Unit = {}
    ): ReverseResult? {
        if (clip.kind != ClipKind.VIDEO) return null
        val durationMs = clip.sourceDurationMs.coerceIn(200, MAX_REVERSE_MS)
        val fps = REVERSE_FPS
        val frameCount = ((durationMs * fps) / 1000).toInt().coerceIn(2, 600)
        val frameDurationMs = (durationMs.toDouble() / frameCount).roundToInt().coerceAtLeast(1)
        val frameDir = File(context.cacheDir, "reverse/${UUID.randomUUID()}").apply { mkdirs() }

        val frameData = withContext(Dispatchers.IO) {
            val uri = store.resolve(project.id, clip.uri)
            val retriever = MediaMetadataRetriever()
            val frames = ArrayList<File>(frameCount)
            try {
                retriever.setDataSource(context, uri)
                val rotation = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: clip.sourceRotation
                for (i in 0 until frameCount) {
                    val fraction =
                        (frameCount - 1 - i).toDouble() / (frameCount - 1).coerceAtLeast(1)
                    val sourceMs = clip.sourceStartMs + (durationMs * fraction).toLong()
                    val bitmap = retriever.getFrameAtTime(
                        sourceMs * 1_000,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: continue
                    val oriented = rotate(bitmap, rotation)
                    val file = File(frameDir, "f_${i.toString().padStart(5, '0')}.jpg")
                    file.outputStream().use { out ->
                        oriented.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    if (oriented !== bitmap) oriented.recycle()
                    bitmap.recycle()
                    frames.add(file)
                    if (i % 8 == 0) onProgress((i * 45) / frameCount.coerceAtLeast(1))
                }
            } catch (_: Throwable) {
                // Partial frame sets are still usable; a completely empty set is rejected below.
            } finally {
                retriever.release()
            }
            frames
        }
        if (frameData.isEmpty()) {
            frameDir.deleteRecursively()
            return null
        }

        // Transformer must be created on a thread that owns a Looper.
        return try {
            withContext(Dispatchers.Main.immediate) {
                val width = even(if (clip.sourceWidth > 0) clip.sourceWidth else project.canvas.width)
                val height = even(if (clip.sourceHeight > 0) clip.sourceHeight else project.canvas.height)
                val sequenceBuilder = EditedMediaItemSequence.Builder()
                for (frame in frameData) {
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.fromFile(frame))
                        .setImageDurationMs(frameDurationMs.toLong())
                        .build()
                    sequenceBuilder.addItem(
                        EditedMediaItem.Builder(mediaItem)
                            .setDurationUs(frameDurationMs.toLong() * 1_000)
                            .setFrameRate(fps)
                            .setEffects(
                                Effects(
                                    emptyList(),
                                    listOf(
                                        Presentation.createForWidthAndHeight(
                                            width,
                                            height,
                                            Presentation.LAYOUT_SCALE_TO_FIT
                                        )
                                    )
                                )
                            )
                            .build()
                    )
                }
                onProgress(50)

                val (output, relative) = store.newAsset(project.id, "reverse", "mp4")
                val composition = Composition.Builder(sequenceBuilder.build())
                    .experimentalSetForceAudioTrack(false)
                    .build()
                val finished = CompletableDeferred<Boolean>()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder().setBitrate(12_000_000).build()
                            )
                            .build()
                    )
                    .setListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            if (!finished.isCompleted) finished.complete(true)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (!finished.isCompleted) finished.complete(false)
                        }
                    })
                    .build()
                transformer.start(composition, output.absolutePath)
                onProgress(70)
                val success = finished.await()
                onProgress(100)
                if (!success || !output.exists()) {
                    output.delete()
                    null
                } else {
                    ReverseResult(relative, frameDurationMs.toLong() * frameData.size, frameData.size)
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            frameDir.deleteRecursively()
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun even(value: Int): Int = ((value.coerceIn(2, 7_680)) / 2) * 2

    companion object {
        /** Reverse is limited so frame extraction cannot exhaust memory or storage. */
        const val MAX_REVERSE_MS = 15_000L
        const val REVERSE_FPS = 24
    }
}
