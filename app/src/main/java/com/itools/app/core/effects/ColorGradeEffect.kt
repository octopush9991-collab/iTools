package com.itools.app.core.effects

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import com.itools.app.core.model.ColorGrade

/**
 * One-pass colour pipeline: brightness, contrast, saturation, white balance, vignette and grain.
 *
 * Filter presets are ordinary [ColorGrade] values, so a preset and a manual adjustment are the
 * same code path. This keeps preview and export identical.
 */
@OptIn(UnstableApi::class)
class ColorGradeEffect(private val grade: ColorGrade) : ShaderGlEffect() {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = grade.isNeutral

    override fun createProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        ColorGradeProgram(useHdr, grade)
}

@OptIn(UnstableApi::class)
private class ColorGradeProgram(
    useHdr: Boolean,
    grade: ColorGrade
) : SimpleShaderProgram(useHdr, FRAGMENT_SHADER) {

    private val brightness = grade.brightness
    private val contrast = grade.contrast
    private val saturation = grade.saturation
    private val temperature = grade.temperature
    private val tint = grade.tint
    private val vignette = grade.vignette
    private val grain = grade.grain
    private val intensity = grade.intensity.coerceIn(0f, 1f)

    override fun onProgramCreated(program: GlProgram) {
        program.setFloatUniform("uBrightness", brightness)
        program.setFloatUniform("uContrast", contrast)
        program.setFloatUniform("uSaturation", saturation)
        program.setFloatUniform("uTemperature", temperature)
        program.setFloatUniform("uTint", tint)
        program.setFloatUniform("uVignette", vignette)
        program.setFloatUniform("uGrain", grain)
        program.setFloatUniform("uIntensity", intensity)
    }

    override fun setUniforms(presentationTimeUs: Long) {
        glProgram.setFloatUniform("uTime", presentationTimeUs / 1_000_000f)
    }

    private companion object {
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uTemperature;
            uniform float uTint;
            uniform float uVignette;
            uniform float uGrain;
            uniform float uIntensity;
            uniform float uTime;
            varying vec2 vTexSamplingCoord;

            vec3 grade(vec3 c) {
                c += uBrightness;
                c = (c - 0.5) * uContrast + 0.5;
                float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                c = mix(vec3(luma), c, uSaturation);
                c.r += uTemperature * 0.18;
                c.b -= uTemperature * 0.18;
                c.g += uTint * 0.12;
                c.r -= uTint * 0.06;
                c.b -= uTint * 0.06;
                return c;
            }

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 outColor = mix(src.rgb, grade(src.rgb), uIntensity);
                float dist = distance(vTexSamplingCoord, vec2(0.5));
                outColor *= 1.0 - uVignette * smoothstep(0.35, 0.85, dist);
                float noise = fract(
                    sin(dot(vTexSamplingCoord * (uTime + 1.0), vec2(12.9898, 78.233))) * 43758.5453
                );
                outColor += (noise - 0.5) * uGrain * 0.18;
                gl_FragColor = vec4(clamp(outColor, 0.0, 1.0), src.a);
            }
        """
    }
}
