package com.itools.app.core.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.itools.app.core.model.ColorGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class EnhanceSuggestion(
    val grade: ColorGrade,
    val averageLuma: Float,
    val contrastEstimate: Float,
    val averageSaturation: Float,
    val note: String
)

/**
 * Histogram-based auto enhancement. This is deterministic local image analysis, not a cloud AI
 * feature: it samples a handful of frames and proposes brightness, contrast and saturation values.
 */
object AutoEnhance {

    suspend fun analyze(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        durationMs: Long
    ): EnhanceSuggestion? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmaps = if (isVideo) sampleVideoFrames(context, uri, durationMs) else sampleImage(context, uri)
            if (bitmaps.isEmpty()) return@runCatching null
            var lumaSum = 0f
            var lumaSqSum = 0f
            var satSum = 0f
            var redSum = 0f
            var blueSum = 0f
            var count = 0
            val pixels = IntArray(48 * 48)
            for (source in bitmaps) {
                val bitmap = Bitmap.createScaledBitmap(source, 48, 48, true)
                bitmap.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                val hsv = FloatArray(3)
                for (pixel in pixels) {
                    val r = Color.red(pixel) / 255f
                    val g = Color.green(pixel) / 255f
                    val b = Color.blue(pixel) / 255f
                    val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    lumaSum += luma
                    lumaSqSum += luma * luma
                    redSum += r
                    blueSum += b
                    Color.RGBToHSV(
                        (r * 255).toInt(),
                        (g * 255).toInt(),
                        (b * 255).toInt(),
                        hsv
                    )
                    satSum += hsv[1]
                    count++
                }
                if (bitmap !== source) bitmap.recycle()
            }
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            if (count == 0) return@runCatching null

            val meanLuma = lumaSum / count
            val variance = (lumaSqSum / count - meanLuma * meanLuma).coerceAtLeast(0f)
            val stdDev = sqrt(variance)
            val meanSat = satSum / count
            val meanRed = redSum / count
            val meanBlue = blueSum / count

            val brightness = ((0.48f - meanLuma) * 0.55f).coerceIn(-0.16f, 0.16f)
            val contrast = if (stdDev < 0.17f) {
                (0.17f / (stdDev + 0.05f)).coerceIn(1.0f, 1.22f)
            } else {
                1f
            }
            val saturation = if (meanSat < 0.28f) {
                (0.28f / (meanSat + 0.06f)).coerceIn(1.0f, 1.28f)
            } else {
                1f
            }
            val temperature = when {
                meanRed < meanBlue * 0.94f -> 0.08f
                meanRed > meanBlue * 1.08f -> -0.04f
                else -> 0f
            }
            val note = buildString {
                if (meanLuma < 0.35f) append("Video cenderung gelap. ")
                if (meanLuma > 0.68f) append("Video cenderung terang. ")
                if (stdDev < 0.14f) append("Kontras rendah. ")
                if (meanSat < 0.2f) append("Warna kurang jenuh. ")
                if (isEmpty()) append("Tingkat cahaya sudah seimbang.")
            }
            EnhanceSuggestion(
                grade = ColorGrade(
                    filterId = "auto",
                    intensity = 1f,
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    temperature = temperature
                ),
                averageLuma = meanLuma,
                contrastEstimate = stdDev,
                averageSaturation = meanSat,
                note = note.trim()
            )
        }.getOrNull()
    }

    private fun sampleVideoFrames(
        context: Context,
        uri: Uri,
        durationMs: Long
    ): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val result = ArrayList<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            val duration = if (durationMs > 0) durationMs else {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }
            val points = listOf(0.1, 0.3, 0.5, 0.7, 0.9)
            for (fraction in points) {
                val timeUs = (duration * fraction * 1_000).toLong()
                retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )?.let { result.add(it) }
            }
        } catch (_: Exception) {
            // Ignore and keep whatever frames were decoded.
        } finally {
            retriever.release()
        }
        return result
    }

    private fun sampleImage(context: Context, uri: Uri): List<Bitmap> {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
            ?: runCatching {
                uri.path?.let { path -> BitmapFactory.decodeFile(path) }
            }.getOrNull()
        return if (bitmap != null) listOf(bitmap) else emptyList()
    }

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    fun lumaOf(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
        val pixels = IntArray(16 * 16)
        scaled.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        var sum = 0f
        for (pixel in pixels) {
            sum += (Color.red(pixel) * 0.2126f + Color.green(pixel) * 0.7152f + Color.blue(pixel) * 0.0722f) / 255f
        }
        if (scaled !== bitmap) scaled.recycle()
        return sum / pixels.size
    }

    fun contrastOf(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        var sum = 0f
        var sumSq = 0f
        for (pixel in pixels) {
            val luma = (Color.red(pixel) * 0.2126f + Color.green(pixel) * 0.7152f + Color.blue(pixel) * 0.0722f) / 255f
            sum += luma
            sumSq += luma * luma
        }
        if (scaled !== bitmap) scaled.recycle()
        val mean = sum / pixels.size
        return sqrt((sumSq / pixels.size - mean * mean).coerceAtLeast(0f))
    }

    fun exposureWarning(luma: Float): String? = when {
        luma < 0.22f -> "Sangat gelap"
        luma > 0.82f -> "Sangat terang"
        else -> null
    }

    fun difference(a: ColorGrade, b: ColorGrade): Float =
        abs(a.brightness - b.brightness) + abs(a.contrast - b.contrast) +
            abs(a.saturation - b.saturation) + abs(a.temperature - b.temperature)

    fun shouldSuggest(luma: Float, contrast: Float, saturation: Float): Boolean =
        luma < 0.38f || luma > 0.68f || contrast < 0.14f || saturation < 0.2f

    fun clamp(value: Float): Float = max(0f, value)
}
