package com.itools.app.core.effects

import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlMatrixTransformation
import androidx.media3.effect.GlEffect
import androidx.media3.effect.BaseGlShaderProgram
import android.content.Context
import androidx.media3.common.util.GlProgram
import com.itools.app.core.model.Clip
import com.itools.app.core.model.KeyframeMath
import com.itools.app.core.model.TransitionType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Per-frame scale / rotation / position driven by the clip's keyframes.
 *
 * A keyframe-less clip still uses its static [com.itools.app.core.model.Transform], so the same
 * effect covers basic positioning and animation.
 */
@OptIn(UnstableApi::class)
class KeyframedTransformEffect(private val clip: Clip) : GlMatrixTransformation {

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray {
        val transform = KeyframeMath.transformAt(clip, presentationTimeUs / 1_000)
        val rad = Math.toRadians(transform.rotation.toDouble()).toFloat()
        val cosR = cos(rad)
        val sinR = sin(rad)
        val sx = transform.scaleX
        val sy = transform.scaleY
        return floatArrayOf(
            sx * cosR, sx * sinR, 0f, 0f,
            -sy * sinR, sy * cosR, 0f, 0f,
            0f, 0f, 1f, 0f,
            transform.offsetX, transform.offsetY, 0f, 1f
        )
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        clip.keyframes.isEmpty() && clip.transform.isIdentity

    override fun configure(inputWidth: Int, inputHeight: Int): Size =
        Size(inputWidth, inputHeight)
}

/** Applies clip opacity, keyframed opacity and the visual fade-in/out. */
@OptIn(UnstableApi::class)
class OpacityEffect(private val clip: Clip) : ShaderGlEffect() {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        clip.opacity >= 1f && clip.keyframes.isEmpty() && clip.fadeInMs <= 0 && clip.fadeOutMs <= 0

    override fun createProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        OpacityProgram(useHdr, clip)

    fun alphaAt(presentationTimeUs: Long): Float {
        val localMs = (presentationTimeUs / 1_000).coerceIn(0, clip.durationMs)
        var alpha = KeyframeMath.opacityAt(clip, localMs).coerceIn(0f, 1f)
        alpha *= fadeFactor(localMs)
        return alpha
    }

    private fun fadeFactor(localMs: Long): Float {
        var factor = 1f
        if (clip.fadeInMs > 0) {
            factor *= smoothStep((localMs.toFloat() / clip.fadeInMs).coerceIn(0f, 1f))
        }
        if (clip.fadeOutMs > 0) {
            val remaining = (clip.durationMs - localMs).toFloat() / clip.fadeOutMs
            factor *= smoothStep(remaining.coerceIn(0f, 1f))
        }
        return factor
    }

    private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)
}

@OptIn(UnstableApi::class)
private class OpacityProgram(
    useHdr: Boolean,
    private val clip: Clip
) : SimpleShaderProgram(useHdr, FRAGMENT_SHADER) {

    private val opacity = OpacityEffect(clip)

    override fun setUniforms(presentationTimeUs: Long) {
        glProgram.setFloatUniform("uAlpha", opacity.alphaAt(presentationTimeUs))
    }

    private companion object {
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform float uAlpha;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                gl_FragColor = vec4(src.rgb, src.a * uAlpha);
            }
        """
    }
}

/**
 * Boundary transition. Media3 compositions do not support overlapping clips, so iTools renders
 * transitions inside each clip's own time range: fade through black/white or a zoom/slide whose
 * alpha reaches zero at the boundary. A true crossfade would require an overlap and is therefore
 * explicitly documented as not available.
 */
@OptIn(UnstableApi::class)
class TransitionEffect(private val clip: Clip) : ShaderGlEffect() {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        clip.transitionIn == null && clip.transitionOut == null

    override fun createProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        TransitionProgram(useHdr, clip)
}

@OptIn(UnstableApi::class)
private class TransitionProgram(
    useHdr: Boolean,
    clip: Clip
) : SimpleShaderProgram(useHdr, FRAGMENT_SHADER) {

    private val durationMs = clip.durationMs.coerceAtLeast(1)
    private val inDuration = clip.transitionIn?.durationMs?.coerceAtLeast(1) ?: 0
    private val outDuration = clip.transitionOut?.durationMs?.coerceAtLeast(1) ?: 0
    private val inMode = clip.transitionIn?.type?.shaderMode ?: -1
    private val outMode = clip.transitionOut?.type?.shaderMode ?: -1

    override fun onProgramCreated(program: GlProgram) {
        program.setIntUniform("uInMode", inMode)
        program.setIntUniform("uOutMode", outMode)
        program.setFloatUniform("uInDuration", inDuration.toFloat())
        program.setFloatUniform("uOutDuration", outDuration.toFloat())
        program.setFloatUniform("uDuration", durationMs.toFloat())
    }

    override fun setUniforms(presentationTimeUs: Long) {
        glProgram.setFloatUniform("uTimeMs", presentationTimeUs / 1_000f)
    }

    private companion object {
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform int uInMode;
            uniform int uOutMode;
            uniform float uInDuration;
            uniform float uOutDuration;
            uniform float uDuration;
            uniform float uTimeMs;
            varying vec2 vTexSamplingCoord;

            float smoothf(float t) {
                t = clamp(t, 0.0, 1.0);
                return t * t * (3.0 - 2.0 * t);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                float alpha = 1.0;
                float white = 0.0;

                if (uInMode >= 0) {
                    float p = smoothf(uTimeMs / max(uInDuration, 1.0));
                    alpha *= p;
                    if (uInMode == 1) {
                        white = max(white, 1.0 - p);
                    } else if (uInMode == 2) {
                        float zoom = 1.15 - 0.15 * p;
                        uv = (uv - 0.5) / zoom + 0.5;
                    } else if (uInMode == 3) {
                        uv.x += (1.0 - p) * 0.35;
                    } else if (uInMode == 4) {
                        uv.y -= (1.0 - p) * 0.35;
                    }
                }

                if (uOutMode >= 0) {
                    float elapsed = max(uDuration - uTimeMs, 0.0);
                    float p = smoothf(elapsed / max(uOutDuration, 1.0));
                    alpha *= p;
                    if (uOutMode == 1) {
                        white = max(white, 1.0 - p);
                    } else if (uOutMode == 2) {
                        float zoom = 1.15 - 0.15 * p;
                        uv = (uv - 0.5) / zoom + 0.5;
                    } else if (uOutMode == 3) {
                        uv.x -= (1.0 - p) * 0.35;
                    } else if (uOutMode == 4) {
                        uv.y += (1.0 - p) * 0.35;
                    }
                }

                vec4 src = texture2D(uTexSampler, uv);
                vec3 rgb = mix(src.rgb, vec3(1.0), white);
                gl_FragColor = vec4(rgb, src.a * alpha);
            }
        """
    }
}

internal val TransitionType.shaderMode: Int
    get() = when (this) {
        TransitionType.NONE -> -1
        TransitionType.FADE_BLACK -> 0
        TransitionType.FADE_WHITE -> 1
        TransitionType.ZOOM -> 2
        TransitionType.SLIDE_LEFT -> 3
        TransitionType.SLIDE_UP -> 4
    }
