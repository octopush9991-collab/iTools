package com.itools.app.core.effects

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import com.itools.app.core.model.MaskShape
import com.itools.app.core.model.MaskSpec

/**
 * Animated mask shapes (linear / circle / rectangle) with feathering and inversion.
 *
 * Coordinates are normalised: (0,0) is the bottom-left corner and (1,1) the top-right.
 */
@OptIn(UnstableApi::class)
class MaskEffect(private val spec: MaskSpec) : ShaderGlEffect() {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false

    override fun createProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        MaskProgram(useHdr, spec)
}

@OptIn(UnstableApi::class)
private class MaskProgram(
    useHdr: Boolean,
    spec: MaskSpec
) : SimpleShaderProgram(useHdr, FRAGMENT_SHADER) {

    private val shape = when (spec.shape) {
        MaskShape.LINEAR -> 0
        MaskShape.CIRCLE -> 1
        MaskShape.RECTANGLE -> 2
    }

    override fun onProgramCreated(program: GlProgram) {
        program.setIntUniform("uShape", shape)
        program.setFloatsUniform(
            "uCenter",
            floatArrayOf(spec.centerX.coerceIn(0f, 1f), spec.centerY.coerceIn(0f, 1f))
        )
        program.setFloatUniform("uWidth", spec.width.coerceAtLeast(0.01f))
        program.setFloatUniform("uHeight", spec.height.coerceAtLeast(0.01f))
        program.setFloatUniform("uRotation", Math.toRadians(spec.rotation.toDouble()).toFloat())
        program.setFloatUniform("uFeather", spec.feather.coerceIn(0.001f, 0.49f))
        program.setFloatUniform("uInvert", if (spec.invert) 1f else 0f)
    }

    override fun setUniforms(presentationTimeUs: Long) = Unit

    private companion object {
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform int uShape;
            uniform vec2 uCenter;
            uniform float uWidth;
            uniform float uHeight;
            uniform float uRotation;
            uniform float uFeather;
            uniform float uInvert;
            varying vec2 vTexSamplingCoord;

            vec2 rotate(vec2 p, float angle) {
                float c = cos(angle);
                float s = sin(angle);
                return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
            }

            float maskAlpha(vec2 uv) {
                vec2 p = rotate(uv - uCenter, -uRotation);
                if (uShape == 0) {
                    // Half plane above the line, with a soft edge.
                    return smoothstep(-uFeather, uFeather, p.y);
                } else if (uShape == 1) {
                    vec2 q = p / vec2(uWidth * 0.5, uHeight * 0.5);
                    float d = length(q);
                    return 1.0 - smoothstep(1.0 - uFeather * 2.0, 1.0, d);
                } else {
                    vec2 h = vec2(uWidth * 0.5, uHeight * 0.5);
                    vec2 q = abs(p) - h;
                    float sdf = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
                    return 1.0 - smoothstep(-uFeather, uFeather, sdf);
                }
            }

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                float m = maskAlpha(vTexSamplingCoord);
                if (uInvert > 0.5) {
                    m = 1.0 - m;
                }
                gl_FragColor = vec4(src.rgb, src.a * m);
            }
        """
    }
}
