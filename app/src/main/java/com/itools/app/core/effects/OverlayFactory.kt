package com.itools.app.core.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.itools.app.core.model.CanvasSpec
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.KeyframeMath
import com.itools.app.core.model.TextAlign
import com.itools.app.core.model.TextAnimation
import com.itools.app.core.model.Transform
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A text / sticker / caption overlay scheduled on the project timeline.
 *
 * Composition-level overlays receive the global presentation timestamp, so time windows are simply
 * `clip.startMs..clip.endMs`. Bitmaps are rendered locally with Canvas; no font files or image
 * assets are bundled, which keeps the APK small and avoids licensing issues.
 */
@OptIn(UnstableApi::class)
class ProjectOverlay(
    private val clip: Clip,
    private val canvas: CanvasSpec,
    private val outputWidth: Int,
    private val outputHeight: Int
) : BitmapOverlay() {

    private var cachedBitmap: Bitmap? = null
    private var cachedText: String? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val localMs = localTimeMs(presentationTimeUs)
        if (!isActive(localMs)) return EMPTY_BITMAP
        return when (clip.kind) {
            ClipKind.STICKER -> stickerBitmap(clip.sticker?.size ?: 0.24f)
            else -> textBitmap(localMs)
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val localMs = localTimeMs(presentationTimeUs)
        if (!isActive(localMs)) {
            return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        }
        val transform = KeyframeMath.transformAt(clip, localMs)
        var alpha = KeyframeMath.opacityAt(clip, localMs).coerceIn(0f, 1f)
        alpha *= fadeFactor(localMs)
        var offsetY = transform.offsetY
        var scaleX = transform.scaleX
        var scaleY = transform.scaleY

        val animation = when (clip.kind) {
            ClipKind.STICKER -> clip.sticker?.animation ?: TextAnimation.NONE
            else -> clip.text?.animation ?: TextAnimation.NONE
        }
        val progress = animationProgress(localMs, animation)
        when (animation) {
            TextAnimation.FADE_IN -> alpha *= progress
            TextAnimation.SLIDE_UP -> offsetY -= (1f - progress) * 0.14f
            TextAnimation.POP -> {
                val bounce = 1f + 0.12f * sin(progress * Math.PI).toFloat()
                val grow = 0.55f + 0.45f * progress
                scaleX *= grow * bounce
                scaleY *= grow * bounce
            }
            TextAnimation.TYPEWRITER, TextAnimation.NONE -> Unit
        }

        val sizeScale = if (clip.kind == ClipKind.STICKER) {
            min(outputWidth, outputHeight).toFloat() / min(canvas.width, canvas.height).coerceAtLeast(1)
        } else {
            outputWidth.toFloat() / canvas.width.coerceAtLeast(1)
        }

        return StaticOverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setBackgroundFrameAnchor(
                transform.offsetX.coerceIn(-1f, 1f),
                offsetY.coerceIn(-1f, 1f)
            )
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(
                (scaleX * sizeScale).coerceAtLeast(0.01f),
                (scaleY * sizeScale).coerceAtLeast(0.01f)
            )
            .setRotationDegrees(transform.rotation)
            .build()
    }

    private fun isActive(localMs: Long): Boolean = localMs in 0..clip.durationMs

    private fun localTimeMs(presentationTimeUs: Long): Long =
        presentationTimeUs / 1_000 - clip.startMs

    private fun fadeFactor(localMs: Long): Float {
        var factor = 1f
        if (clip.fadeInMs > 0) {
            factor *= smooth((localMs.toFloat() / clip.fadeInMs).coerceIn(0f, 1f))
        }
        if (clip.fadeOutMs > 0) {
            factor *= smooth(((clip.durationMs - localMs).toFloat() / clip.fadeOutMs).coerceIn(0f, 1f))
        }
        return factor
    }

    private fun animationProgress(localMs: Long, animation: TextAnimation): Float {
        if (animation == TextAnimation.NONE) return 1f
        val duration = if (animation == TextAnimation.TYPEWRITER) {
            clip.durationMs.coerceAtLeast(1)
        } else {
            min(420L, (clip.durationMs / 2).coerceAtLeast(1))
        }
        return smooth((localMs.toFloat() / duration).coerceIn(0f, 1f))
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    private fun textBitmap(localMs: Long): Bitmap {
        val spec = clip.text ?: return EMPTY_BITMAP
        var text = spec.text
        if (spec.animation == TextAnimation.TYPEWRITER) {
            val visible = (text.length * animationProgress(localMs, TextAnimation.TYPEWRITER)).toInt()
            text = text.take(visible.coerceAtLeast(1))
        }
        val renderScale = canvas.width / 1080f
        val cacheKey = "$text|${spec.fontSize}|${spec.bold}|${spec.italic}|${spec.color}"
        cachedBitmap?.let { if (cachedText == cacheKey) return it }

        val bitmap = renderText(text, spec, renderScale)
        cachedBitmap = bitmap
        cachedText = cacheKey
        return bitmap
    }

    private fun renderText(
        text: String,
        spec: com.itools.app.core.model.TextSpec,
        renderScale: Float
    ): Bitmap {
        val fontPx = (spec.fontSize * renderScale).coerceAtLeast(8f)
        val typeface = Typeface.create(familyName(spec.fontFamily), styleOf(spec.bold, spec.italic))
        val maxWidth = (canvas.width * 0.86f).toInt().coerceAtLeast(fontPx.toInt())

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = fontPx
            this.typeface = typeface
            color = spec.color.toInt()
            letterSpacing = spec.letterSpacing
            textAlign = Paint.Align.LEFT
            if (spec.shadow) {
                setShadowLayer(fontPx * 0.07f, 0f, fontPx * 0.04f, 0xAA000000.toInt())
            }
        }
        val alignment = when (spec.align) {
            TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val measured = buildLayout(text, paint, maxWidth, alignment)
        var contentWidth = 0f
        for (i in 0 until measured.lineCount) {
            contentWidth = max(contentWidth, measured.getLineWidth(i))
        }
        val width = contentWidth.toInt().coerceAtLeast(1)
        val layout = if (width >= maxWidth) {
            measured
        } else {
            buildLayout(text, paint, width, alignment)
        }

        val strokePx = spec.strokeWidth * renderScale
        val padding = max(strokePx * 2f, fontPx * 0.10f).coerceAtLeast(2f).toInt() + 4
        val bitmapWidth = (layout.width + padding * 2).coerceAtLeast(2)
        val bitmapHeight = (layout.height + padding * 2).coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (Color.alpha(spec.background.toInt()) > 0) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            bgPaint.color = spec.background.toInt()
            bgPaint.style = Paint.Style.FILL
            val radius = padding.toFloat()
            canvas.drawRoundRect(
                padding * 0.25f,
                padding * 0.25f,
                bitmapWidth - padding * 0.25f,
                bitmapHeight - padding * 0.25f,
                radius,
                radius,
                bgPaint
            )
        }

        canvas.save()
        canvas.translate(padding.toFloat(), padding.toFloat())
        if (strokePx > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = spec.strokeColor.toInt()
            paint.clearShadowLayer()
            layout.draw(canvas)
        }
        paint.style = Paint.Style.FILL
        paint.color = spec.color.toInt()
        if (spec.shadow) {
            paint.setShadowLayer(fontPx * 0.07f, 0f, fontPx * 0.04f, 0xAA000000.toInt())
        }
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(alignment)
            .setIncludePad(true)
            .setLineSpacing(0f, 1f)
        return builder.build()
    }

    private fun stickerBitmap(sizeFraction: Float): Bitmap {
        val key = "sticker|${clip.sticker?.emoji}|$sizeFraction"
        cachedBitmap?.let { if (cachedText == key) return it }
        val emoji = clip.sticker?.emoji ?: "✨"
        val fontSize = (sizeFraction * min(canvas.width, canvas.height)).coerceAtLeast(24f)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
        }
        val metrics = paint.fontMetrics
        val padding = fontSize * 0.12f
        val width = (paint.measureText(emoji) + padding * 2).toInt().coerceAtLeast(2)
        val height = ((metrics.descent - metrics.ascent) + padding * 2).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(emoji, padding, padding - metrics.ascent, paint)
        cachedBitmap = bitmap
        cachedText = key
        return bitmap
    }

    private fun familyName(family: String): String = when (family) {
        "serif" -> "serif"
        "mono" -> "monospace"
        "cursive" -> "cursive"
        else -> "sans-serif"
    }

    private fun styleOf(bold: Boolean, italic: Boolean): Int {
        var style = Typeface.NORMAL
        if (bold) style = style or Typeface.BOLD
        if (italic) style = style or Typeface.ITALIC
        return style
    }

    companion object {
        fun transformOf(clip: Clip, localMs: Long): Transform = KeyframeMath.transformAt(clip, localMs)
    }
}
