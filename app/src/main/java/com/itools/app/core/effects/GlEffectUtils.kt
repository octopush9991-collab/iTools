package com.itools.app.core.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect

/**
 * Shared GLSL plumbing for iTools effects.
 *
 * All effects use a full-screen triangle strip in normalised device coordinates and sample the
 * input texture with the same transformation matrix Media3 uses for its built-in effects.
 * Shaders are inline (no assets) so the app has no hidden binary payloads.
 */
@OptIn(UnstableApi::class)
internal object GlEffectUtils {

    const val VERTEX_SHADER: String = """
        attribute vec4 aFramePosition;
        uniform mat4 uTransformationMatrix;
        uniform mat4 uTexTransformationMatrix;
        varying vec2 vTexSamplingCoord;

        void main() {
            gl_Position = uTransformationMatrix * aFramePosition;
            vec2 textureCoord = aFramePosition.xy * 0.5 + 0.5;
            vTexSamplingCoord =
                (uTexTransformationMatrix * vec4(textureCoord, 0.0, 1.0)).xy;
        }
    """

    fun createProgram(fragmentShader: String): GlProgram =
        try {
            GlProgram(VERTEX_SHADER, fragmentShader)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

    /** Binds the quad and the identity matrices required by [VERTEX_SHADER]. */
    fun GlProgram.bindBaseAttributes() {
        setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        val identity = GlUtil.create4x4IdentityMatrix()
        setFloatsUniform("uTransformationMatrix", identity)
        setFloatsUniform("uTexTransformationMatrix", identity)
    }

    fun GlProgram.drawFrame(inputTexId: Int) {
        use()
        setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
        bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
    }
}

/** Base class that hides the OpenGL boilerplate from the individual effects. */
@OptIn(UnstableApi::class)
internal abstract class SimpleShaderProgram(
    useHdr: Boolean,
    fragmentShader: String
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    /**
     * Created lazily on the GL thread. Doing this in the property initialiser would run before the
     * subclass constructor has populated its fields, so `onProgramCreated` would see zero values.
     */
    protected val glProgram: GlProgram by lazy {
        GlEffectUtils.createProgram(fragmentShader).also {
            with(GlEffectUtils) { it.bindBaseAttributes() }
            onProgramCreated(it)
        }
    }

    protected open fun onProgramCreated(program: GlProgram) = Unit

    override fun configure(inputWidth: Int, inputHeight: Int): Size =
        Size(inputWidth, inputHeight)

    final override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            setUniforms(presentationTimeUs)
            with(GlEffectUtils) { glProgram.drawFrame(inputTexId) }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    protected abstract fun setUniforms(presentationTimeUs: Long)
}

/** Wraps a shader factory in a Media3 [GlEffect]. */
@OptIn(UnstableApi::class)
abstract class ShaderGlEffect : GlEffect {
    abstract fun createProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram

    final override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): BaseGlShaderProgram = createProgram(context, useHdr)
}

/** 1x1 transparent bitmap used when an overlay is outside its time range. */
internal val EMPTY_BITMAP: Bitmap by lazy {
    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).drawColor(Color.TRANSPARENT)
    }
}

/** Draws a small rounded rectangle behind text. */
internal fun drawRoundedBackground(
    canvas: Canvas,
    paint: Paint,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    corner: Float,
    color: Int
) {
    if (Color.alpha(color) == 0) return
    paint.reset()
    paint.isAntiAlias = true
    paint.color = color
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(left, top, right, bottom, corner, corner, paint)
}
