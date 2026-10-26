package com.itools.app.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AudioEnvelope(
    val sampleRate: Int,
    val samples: FloatArray,
    val durationMs: Long
)

data class SilenceRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

data class BeatResult(val bpm: Int, val beatsMs: List<Long>)

/**
 * Offline audio analysis. Audio is decoded to a compact mono envelope (8 kHz) with
 * MediaExtractor/MediaCodec; nothing is uploaded and no ML model is required.
 */
object AudioAnalyzer {

    private const val TARGET_RATE = 8_000

    suspend fun decodeEnvelope(
        context: Context,
        uri: Uri,
        maxSeconds: Int = 300
    ): AudioEnvelope? = withContext(Dispatchers.IO) {
        runCatching { decodeInternal(context, uri, maxSeconds) }.getOrNull()
    }

    private fun decodeInternal(context: Context, uri: Uri, maxSeconds: Int): AudioEnvelope? {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(i)
            if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i
                format = candidate
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            return null
        }
        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bins = FloatArray(TARGET_RATE * maxSeconds)
        val counts = IntArray(TARGET_RATE * maxSeconds)
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var encoding = MediaFormat.ENCODING_PCM_16BIT
        var frameIndex = 0L
        val maxFrames = maxSeconds.toLong() * TARGET_RATE

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val timeoutUs = 10_000L

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inIndex >= 0) {
                    val input = codec.getInputBuffer(inIndex)
                    val sampleSize = if (input == null) -1 else extractor.readSampleData(input, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outIndex = codec.dequeueOutputBuffer(info, timeoutUs)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outFormat = codec.outputFormat
                    sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    encoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        MediaFormat.ENCODING_PCM_16BIT
                    }
                }

                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                else -> if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        if (encoding == MediaFormat.ENCODING_PCM_FLOAT) {
                            val floats = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                            while (floats.hasRemaining()) {
                                var mono = 0f
                                for (c in 0 until channels) {
                                    if (!floats.hasRemaining()) break
                                    mono += floats.get()
                                }
                                frameIndex = writeBin(bins, counts, frameIndex, mono / channels, sampleRate, maxFrames)
                            }
                        } else {
                            val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                            while (shorts.hasRemaining()) {
                                var mono = 0f
                                for (c in 0 until channels) {
                                    if (!shorts.hasRemaining()) break
                                    mono += shorts.get() / 32_768f
                                }
                                frameIndex = writeBin(bins, counts, frameIndex, mono / channels, sampleRate, maxFrames)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                    if (frameIndex >= maxFrames) {
                        outputDone = true
                    }
                }
            }
        }

        runCatching { codec.stop() }
        codec.release()
        extractor.release()

        val lastBin = counts.indexOfLast { it > 0 }
        if (lastBin <= 0) return null
        val samples = FloatArray(lastBin + 1) { index ->
            if (counts[index] > 0) bins[index] / counts[index] else 0f
        }
        AudioEnvelope(
            sampleRate = TARGET_RATE,
            samples = samples,
            durationMs = (samples.size * 1000L) / TARGET_RATE
        )
    }

    private fun writeBin(
        bins: FloatArray,
        counts: IntArray,
        frameIndex: Long,
        mono: Float,
        sampleRate: Int,
        maxFrames: Long
    ): Long {
        if (frameIndex >= maxFrames) return frameIndex
        val timeSec = frameIndex.toDouble() / sampleRate.coerceAtLeast(1)
        val bin = (timeSec * TARGET_RATE).toInt()
        if (bin in bins.indices) {
            bins[bin] += mono.coerceIn(-1f, 1f)
            counts[bin]++
        }
        return frameIndex + 1
    }

    /** Energy-based onset detector with adaptive thresholding and a 220 ms refractory period. */
    fun detectBeats(envelope: AudioEnvelope): BeatResult {
        val rate = envelope.sampleRate
        val window = (rate * 0.02).toInt().coerceAtLeast(16)
        val hop = (rate * 0.01).toInt().coerceAtLeast(8)
        if (envelope.samples.size < window * 4) return BeatResult(0, emptyList())

        val energies = ArrayList<Float>(envelope.samples.size / hop + 1)
        var start = 0
        while (start + window <= envelope.samples.size) {
            var sum = 0.0
            for (i in start until start + window) {
                val v = envelope.samples[i].toDouble()
                sum += v * v
            }
            energies.add(sqrt(sum / window).toFloat())
            start += hop
        }
        val onsets = ArrayList<Int>()
        val history = 8
        for (i in history until energies.size) {
            var average = 0f
            for (j in i - history until i) average += energies[j]
            average /= history
            val flux = energies[i] - average
            if (energies[i] > 0.012f && flux > average * 0.55f && flux > 0.004f) {
                val timeMs = i * hop * 1000 / rate
                if (onsets.isEmpty() || timeMs - onsets.last() >= 220) {
                    onsets.add(timeMs)
                }
            }
        }
        if (onsets.size < 2) return BeatResult(0, onsets.map { it.toLong() })

        val intervals = onsets.zipWithNext { a, b -> b - a }.sorted()
        val median = intervals[intervals.size / 2].coerceAtLeast(1)
        var bpm = (60_000.0 / median).toInt()
        while (bpm < 60) bpm *= 2
        while (bpm > 200) bpm /= 2
        return BeatResult(bpm, onsets.map { it.toLong() })
    }

    /** Returns quiet ranges (RMS below [thresholdDb]) at least [minSilenceMs] long. */
    fun detectSilence(
        envelope: AudioEnvelope,
        thresholdDb: Float = -38f,
        minSilenceMs: Long = 500
    ): List<SilenceRange> {
        val rate = envelope.sampleRate
        val window = (rate * 0.05).toInt().coerceAtLeast(16)
        if (envelope.samples.isEmpty()) return emptyList()
        val threshold = Math.pow(10.0, thresholdDb / 20.0).toFloat()
        val windows = ArrayList<Pair<Long, Boolean>>()
        var start = 0
        while (start < envelope.samples.size) {
            val end = min(envelope.samples.size, start + window)
            var sum = 0.0
            for (i in start until end) {
                val v = envelope.samples[i].toDouble()
                sum += v * v
            }
            val rms = sqrt(sum / (end - start).coerceAtLeast(1)).toFloat()
            windows.add((start * 1000L / rate) to (rms < threshold))
            start = end
        }
        val ranges = ArrayList<SilenceRange>()
        var silentStart = -1L
        for ((timeMs, silent) in windows) {
            if (silent && silentStart < 0) silentStart = timeMs
            if (!silent && silentStart >= 0) {
                if (timeMs - silentStart >= minSilenceMs) {
                    ranges.add(SilenceRange(silentStart, timeMs))
                }
                silentStart = -1
            }
        }
        if (silentStart >= 0) {
            val endMs = envelope.durationMs
            if (endMs - silentStart >= minSilenceMs) ranges.add(SilenceRange(silentStart, endMs))
        }
        return ranges
    }

    /** Extracts a compact peak/RMS summary for the UI. */
    fun waveform(envelope: AudioEnvelope, points: Int = 240): FloatArray {
        if (envelope.samples.isEmpty() || points <= 0) return FloatArray(0)
        val result = FloatArray(points)
        val bucket = envelope.samples.size.toFloat() / points
        for (p in 0 until points) {
            val from = (p * bucket).toInt()
            val to = max(from + 1, ((p + 1) * bucket).toInt()).coerceAtMost(envelope.samples.size)
            var peak = 0f
            for (i in from until to) peak = max(peak, abs(envelope.samples[i]))
            result[p] = peak.coerceIn(0f, 1f)
        }
        return result
    }
}
