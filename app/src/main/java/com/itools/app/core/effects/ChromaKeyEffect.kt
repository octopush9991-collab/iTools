package com.itools.app.core.effects

import android.content.Context
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import com.itools.app.core.model.ChromaSpec

/**
 * Chroma key (green/blue screen) implemented in CbCr space, the same space editors use to avoid
 * brightness affecting the key. Spill suppression is applied to the dominant key channel.
 */
@OptIn(UnstableApi::class)
class ChromaKeyEffect(private val spec: ChromaSpec) : ShaderGlEffect() {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false

    override fun createProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        ChromaKeyProgram(useHdr, spec)
}

@OptIn(UnstableApi::class)
private class ChromaKeyProgram(
    useHdr: Boolean,
    spec: ChromaSpec
) : SimpleShaderProgram(useHdr, FRAGMENT_SHADER) {

    private val keyR = Color.red(spec.keyColor.toInt()) / 255f
    private val keyG = Color.green(spec.keyColor.toInt()) / 255f
    private val keyB = Color.blue(spec.keyColor.toInt()) / 255f
    private val similarity = spec.similarity.coerceIn(0f, 1f)
    private val smoothness = spec.smoothness.coerceIn(0.001f, 1f)
    private val spill = spec.spill.coerceIn(0f, 1f)
    private val keyIsGreen = keyG > keyR && keyG > keyB
    private val keyIsBlue = keyB > keyR && keyB > keyG

    override fun onProgramCreated(program: GlProgram) {
        program.setFloatsUniform("uKeyColor", floatArrayOf(keyR, keyG, keyB))
        program.setFloatUniform("uSimilarity", similarity * 0.45f)
        program.setFloatUniform("uSmoothness", smoothness * 0.35f + 0.001f)
        program.setFloatUniform("uSpill", spill)
        program.setFloatUniform("uKeyGreen", if (keyIsGreen) 1f else 0f)
        program.setFloatUniform("uKeyBlue", if (keyIsBlue) 1f else 0f)
    }

    override fun setUniforms(presentationTimeUs: Long) = Unit

    private companion object {
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec3 uKeyColor;
            uniform float uSimilarity;
            uniform float uSmoothness;
            uniform float uSpill;
            uniform float uKeyGreen;
            uniform float uKeyBlue;
            varying vec2 vTexSamplingCoord;

            vec2 chroma(vec3 c) {
                float cb = -0.1146 * c.r - 0.3854 * c.g + 0.5 * c.b;
                float cr = 0.5 * c.r - 0.4542 * c.g - 0.0458 * c.b;
                return vec2(cb, cr);
            }

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                float d = distance(chroma(src.rgb), chroma(uKeyColor));
                float alpha = smoothstep(uSimilarity, uSimilarity + uSmoothness, d);

                vec3 c = src.rgb;
                float dominance = max(c.r, max(c.g, c.b));
                if (uKeyGreen > 0.5) {
                    c.g -= uSpill * max(0.0, c.g - max(c.r, c.b)) * (1.0 - alpha);
                } else if (uKeyBlue > 0.5) {
                    c.b -= uSpill * max(0.0, c.b - max(c.r, c.g)) * (1.0 - alpha);
                } else {
                    c -= uSpill * 0.15 * (vec3(dominance) - c) * (1.0 - alpha);
                }

                gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a * alpha);
            }
        """
    }
}
