package com.itools.app.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Minimal microphone recorder. The resulting M4A file is copied into the project asset folder by
 * the editor; recordings never leave the device.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(output: File): Boolean {
        if (recorder != null) return false
        return try {
            output.parentFile?.mkdirs()
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(48_000)
                setAudioEncodingBitRate(160_000)
                setAudioChannels(1)
                setOutputFile(output.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            target = output
            true
        } catch (_: Throwable) {
            runCatching { recorder?.release() }
            recorder = null
            target?.delete()
            target = null
            false
        }
    }

    /** Stops and returns the recording file, or null when nothing usable was written. */
    fun stop(): File? {
        val mediaRecorder = recorder ?: return null
        val file = target
        recorder = null
        target = null
        return try {
            mediaRecorder.stop()
            mediaRecorder.release()
            file?.takeIf { it.exists() && it.length() > 512 }
        } catch (_: Throwable) {
            runCatching { mediaRecorder.release() }
            file?.delete()
            null
        }
    }

    fun cancel() {
        val mediaRecorder = recorder
        recorder = null
        if (mediaRecorder != null) {
            runCatching { mediaRecorder.stop() }
            runCatching { mediaRecorder.release() }
        }
        target?.delete()
        target = null
    }
}
