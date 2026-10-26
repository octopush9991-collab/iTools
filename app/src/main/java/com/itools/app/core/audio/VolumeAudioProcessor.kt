package com.itools.app.core.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.itools.app.core.model.Clip
import com.itools.app.core.model.KeyframeMath
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** Volume, keyframed volume and fade curves for a single clip. */
object AudioGain {
    fun gainAt(clip: Clip, localMs: Long): Float {
        var gain = KeyframeMath.volumeAt(clip, localMs)
        if (clip.fadeInMs > 0) {
            gain *= smooth((localMs.toFloat() / clip.fadeInMs).coerceIn(0f, 1f))
        }
        if (clip.fadeOutMs > 0) {
            gain *= smooth(
                ((clip.durationMs - localMs).toFloat() / clip.fadeOutMs).coerceIn(0f, 1f)
            )
        }
        return gain.coerceIn(0f, 4f)
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)
}

/**
 * Sample-accurate 16-bit PCM gain processor.
 *
 * It runs after the speed processor (when a clip is retimed), therefore the local timestamp used
 * for fades and keyframes matches the output timeline heard by the user.
 */
@OptIn(UnstableApi::class)
class VolumeAudioProcessor(
    private val clip: Clip
) : BaseAudioProcessor() {

    private var frameIndex = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        frameIndex = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size < 2) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val input = inputBuffer.order(ByteOrder.nativeOrder()).duplicate().order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(size).order(ByteOrder.nativeOrder())

        while (input.remaining() >= 2) {
            val sample = input.short.toInt()
            val frame = frameIndex / channels
            val localMs = min(clip.durationMs, frame * 1_000L / sampleRate)
            val gain = AudioGain.gainAt(clip, localMs)
            val scaled = (sample * gain).toInt().coerceIn(-32_768, 32_767)
            output.putShort(scaled.toShort())
            frameIndex++
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
}
