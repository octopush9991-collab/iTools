package com.itools.app.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class MusicStyle(
    val id: String,
    val label: String,
    val bpm: Int,
    val description: String
)

/**
 * A small procedural music engine. Every note is synthesised on-device with additive oscillators
 * and noise; there are no samples, loops, or licence obligations. Useful as royalty-free background
 * music when the user has no audio files.
 */
object ProceduralMusic {

    val styles = listOf(
        MusicStyle("calm", "Tenang", 72, "Pad lembut, tanpa drum"),
        MusicStyle("lofi", "Lo-fi", 82, "Aksen vinyl dan akord jazz"),
        MusicStyle("energy", "Semangat", 126, "Four-on-the-floor dan arpeggio"),
        MusicStyle("epic", "Epik", 100, "Drone rendah dan akord lebar")
    )

    suspend fun generate(
        output: File,
        styleId: String,
        durationMs: Long,
        sampleRate: Int = 44_100
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val style = styles.firstOrNull { it.id == styleId } ?: styles.first()
            val durationSec = (durationMs / 1000.0).coerceIn(2.0, 180.0)
            val totalSamples = (durationSec * sampleRate).toInt()
            val buffer = FloatArray(totalSamples)
            val random = Random(style.id.hashCode() * 31 + style.bpm)
            render(buffer, sampleRate, style, random)
            normalize(buffer, 0.9f)
            applyTrackFades(buffer, sampleRate, 0.25)
            WavWriter.write(output, buffer, sampleRate, channels = 1)
            output.exists() && output.length() > 44
        }.getOrDefault(false)
    }

    private fun render(buffer: FloatArray, rate: Int, style: MusicStyle, random: Random) {
        val beatSec = 60.0 / style.bpm
        val barSec = beatSec * 4
        val totalSec = buffer.size.toDouble() / rate
        val minor = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        val major = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val isMinor = style.id != "energy"
        val scale = if (isMinor) minor else major
        val rootMidi = if (isMinor) 57 else 60
        val progressions = when (style.id) {
            "lofi" -> listOf(intArrayOf(0, 3, 5, 4), intArrayOf(0, 5, 3, 4))
            "epic" -> listOf(intArrayOf(0, 5, 3, 4), intArrayOf(0, 3, 5, 6))
            "energy" -> listOf(intArrayOf(0, 4, 5, 3), intArrayOf(0, 5, 3, 4))
            else -> listOf(intArrayOf(0, 5, 3, 4), intArrayOf(0, 3, 4, 0))
        }
        val progression = progressions[random.nextInt(progressions.size)]

        var bar = 0
        var time = 0.0
        while (time < totalSec) {
            val degree = progression[bar % progression.size]
            val chordMidi = chordNotes(rootMidi, scale, degree)
            val chordSec = minOf(barSec, totalSec - time)

            // Warm pad.
            for ((i, midi) in chordMidi.withIndex()) {
                val freq = midiToFreq(midi)
                val amp = when (style.id) {
                    "epic" -> 0.085f
                    "lofi" -> 0.065f
                    "calm" -> 0.075f
                    else -> 0.045f
                }
                addTone(
                    buffer, rate, time, chordSec * 0.96, freq,
                    amp / (1 + i * 0.25f), Wave.SINE, attack = 0.12, release = 0.5
                )
                if (style.id == "lofi" || style.id == "epic") {
                    addTone(
                        buffer, rate, time, chordSec * 0.96, freq * 1.004,
                        amp * 0.35f, Wave.TRIANGLE, attack = 0.2, release = 0.6
                    )
                }
            }

            // Bass.
            val bassMidi = chordMidi[0] - 12
            val bassPattern = when (style.id) {
                "energy" -> listOf(0.0, 1.0, 2.0, 3.0)
                "calm" -> listOf(0.0)
                else -> listOf(0.0, 2.0)
            }
            for (beatOffset in bassPattern) {
                val at = time + beatOffset * beatSec
                if (at >= totalSec) continue
                addTone(
                    buffer, rate, at, beatSec * 0.85, midiToFreq(bassMidi),
                    if (style.id == "epic") 0.24f else 0.18f,
                    Wave.TRIANGLE, attack = 0.01, release = 0.18
                )
            }

            // Arpeggio / melody.
            if (style.id != "calm") {
                val steps = if (style.id == "energy") 8 else 4
                for (step in 0 until steps) {
                    val at = time + step * (barSec / steps)
                    if (at >= totalSec) continue
                    val note = chordMidi[(step + bar) % chordMidi.size] + if (step >= steps / 2) 12 else 0
                    addTone(
                        buffer, rate, at, barSec / steps * 0.65, midiToFreq(note),
                        if (style.id == "energy") 0.09f else 0.05f,
                        Wave.TRIANGLE, attack = 0.005, release = 0.10
                    )
                }
            }

            // Drums.
            if (style.id != "calm") {
                val kicks = if (style.id == "energy") listOf(0.0, 1.0, 2.0, 3.0) else listOf(0.0, 2.0)
                for (beat in kicks) {
                    addKick(buffer, rate, time + beat * beatSec, if (style.id == "epic") 0.32f else 0.24f)
                }
                val snares = listOf(1.0, 3.0)
                for (beat in snares) {
                    addNoise(
                        buffer, rate, time + beat * beatSec, 0.16,
                        if (style.id == "lofi") 0.05f else 0.085f, random, decay = 26.0
                    )
                }
                val hats = if (style.id == "energy") 8 else 4
                for (i in 0 until hats) {
                    addNoise(
                        buffer, rate, time + i * (barSec / hats), 0.05,
                        if (style.id == "lofi") 0.018f else 0.028f,
                        random, decay = 70.0
                    )
                }
            }

            // Vinyl crackle for lo-fi.
            if (style.id == "lofi") {
                addNoise(buffer, rate, time, chordSec, 0.006f, random, decay = 1.2)
            }

            time += barSec
            bar++
        }

        // Low drone for epic.
        if (style.id == "epic") {
            addTone(
                buffer, rate, 0.0, totalSec, midiToFreq(rootMidi - 24),
                0.10f, Wave.SINE, attack = 0.8, release = 1.2
            )
        }
    }

    private fun chordNotes(rootMidi: Int, scale: IntArray, degree: Int): IntArray {
        fun note(step: Int): Int {
            val octave = step / scale.size
            val index = ((step % scale.size) + scale.size) % scale.size
            return rootMidi + scale[index] + octave * 12
        }
        val base = degree
        return intArrayOf(note(base), note(base + 2), note(base + 4))
    }

    private fun midiToFreq(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    private enum class Wave { SINE, TRIANGLE, SAW }

    private fun addTone(
        buffer: FloatArray,
        rate: Int,
        startSec: Double,
        durationSec: Double,
        freq: Double,
        amp: Float,
        wave: Wave,
        attack: Double,
        release: Double
    ) {
        if (durationSec <= 0 || amp <= 0f) return
        val start = (startSec * rate).toInt().coerceAtLeast(0)
        val length = (durationSec * rate).toInt().coerceAtLeast(1)
        if (start >= buffer.size) return
        val end = minOf(buffer.size, start + length)
        val attackSamples = (attack * rate).toInt().coerceAtLeast(1)
        val releaseSamples = (release * rate).toInt().coerceAtLeast(1)
        var phase = 0.0
        val step = freq / rate
        for (i in start until end) {
            val local = i - start
            val remaining = end - i
            val envelope = when {
                local < attackSamples -> local.toFloat() / attackSamples
                remaining < releaseSamples -> remaining.toFloat() / releaseSamples
                else -> 1f
            }
            val sample = when (wave) {
                Wave.SINE -> sin(phase * 2.0 * PI)
                Wave.TRIANGLE -> 2.0 * abs(2.0 * (phase - Math.floor(phase + 0.5)))
                Wave.SAW -> 2.0 * (phase - Math.floor(phase + 0.5))
            }
            buffer[i] += (sample * amp * envelope).toFloat()
            phase += step
        }
    }

    private fun addKick(buffer: FloatArray, rate: Int, startSec: Double, amp: Float) {
        val start = (startSec * rate).toInt()
        if (start >= buffer.size) return
        val length = (0.18 * rate).toInt()
        val end = minOf(buffer.size, start + length)
        var phase = 0.0
        var frequency = 120.0
        for (i in start until end) {
            val t = (i - start).toDouble() / rate
            frequency = 48.0 + 92.0 * exp(-t * 28.0)
            phase += frequency / rate
            val envelope = exp(-t * 16.0)
            buffer[i] += (sin(phase * 2.0 * PI) * amp * envelope).toFloat()
        }
    }

    private fun addNoise(
        buffer: FloatArray,
        rate: Int,
        startSec: Double,
        durationSec: Double,
        amp: Float,
        random: Random,
        decay: Double
    ) {
        val start = (startSec * rate).toInt()
        if (start >= buffer.size) return
        val length = (durationSec * rate).toInt().coerceAtLeast(1)
        val end = minOf(buffer.size, start + length)
        for (i in start until end) {
            val t = (i - start).toDouble() / rate
            val envelope = exp(-t * decay)
            val noise = random.nextFloat() * 2f - 1f
            buffer[i] += noise * amp * envelope.toFloat()
        }
    }

    private fun normalize(buffer: FloatArray, peakTarget: Float) {
        var peak = 0f
        for (sample in buffer) peak = max(peak, abs(sample))
        if (peak <= 0f) return
        val gain = peakTarget / peak
        if (gain >= 1f && peak <= peakTarget) return
        for (i in buffer.indices) buffer[i] *= gain
        // Soft clip to avoid hard distortion.
        for (i in buffer.indices) {
            val x = buffer[i]
            if (abs(x) > 0.95f) buffer[i] = Math.tanh(x.toDouble()).toFloat() * 0.95f
        }
    }

    private fun applyTrackFades(buffer: FloatArray, rate: Int, fadeSec: Double) {
        val fade = (fadeSec * rate).toInt().coerceAtLeast(1).coerceAtMost(buffer.size / 2)
        for (i in 0 until fade) {
            val gain = i.toFloat() / fade
            buffer[i] *= gain
            buffer[buffer.lastIndex - i] *= gain
        }
    }
}
