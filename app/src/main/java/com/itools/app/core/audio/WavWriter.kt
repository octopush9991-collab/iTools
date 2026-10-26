package com.itools.app.core.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16-bit PCM WAV writer (mono or stereo). */
object WavWriter {

    fun write(
        file: File,
        samples: FloatArray,
        sampleRate: Int = 44_100,
        channels: Int = 1
    ) {
        val pcmBytes = samples.size * 2
        val byteRate = sampleRate * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcmBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort()) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmBytes)
        header.flip()

        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            out.write(header.array())
            val buffer = ByteBuffer.allocate(8_192).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                if (buffer.remaining() < 2) {
                    out.write(buffer.array(), 0, buffer.position())
                    buffer.clear()
                }
                buffer.putShort(
                    (sample.coerceIn(-1f, 1f) * 32_767f).toInt().toShort()
                )
            }
            if (buffer.position() > 0) {
                out.write(buffer.array(), 0, buffer.position())
            }
        }
    }
}
