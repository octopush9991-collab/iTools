package com.itools.app.core.render

import android.content.ContentValues
import android.content.Context
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.itools.app.core.model.Project
import com.itools.app.core.project.ProjectStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
class ExportEngine(
    private val context: Context,
    private val store: ProjectStore
) {

    sealed interface State {
        data object Idle : State
        data class Running(val progress: Int, val note: String = "Menyiapkan…") : State
        data class Success(
            val uri: Uri,
            val displayName: String,
            val sizeBytes: Long,
            val savedToGallery: Boolean
        ) : State

        data class Failure(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var transformer: Transformer? = null

    fun start(scope: CoroutineScope, project: Project) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Main.immediate) {
            _state.value = State.Running(0)
            val tempDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val temp = File(tempDir, "iTools_$stamp.mp4")
            var progressJob: Job? = null
            try {
                val size = CompositionFactory.exportSize(project)
                val composition = CompositionFactory(context, store).build(
                    project = project,
                    outputWidth = size.first,
                    outputHeight = size.second,
                    includeAudio = project.export.includeAudio,
                    outputFps = project.export.fps
                ).composition

                val settings = VideoEncoderSettings.Builder()
                    .setBitrate((project.export.bitrateMbps.coerceIn(1, 200) * 1_000_000))
                    .build()
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(settings)
                    .build()

                val finished = CompletableDeferred<Result<ExportResult?>>()
                val exportListener = object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        if (!finished.isCompleted) finished.complete(Result.success(exportResult))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (!finished.isCompleted) finished.complete(Result.failure(exportException))
                    }
                }

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(project.export.codec.mime)
                    .setAudioMimeType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(exportListener)
                    .build()
                this@ExportEngine.transformer = transformer

                progressJob = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        if (transformer.getProgress(holder) ==
                            Transformer.PROGRESS_STATE_AVAILABLE
                        ) {
                            _state.value = State.Running(
                                holder.progress.coerceIn(0, 100),
                                "Mengekspor video…"
                            )
                        }
                        delay(250)
                    }
                }

                _state.value = State.Running(1, "Mulai mengekspor…")
                transformer.start(composition, temp.absolutePath)
                val result = finished.await()

                progressJob.cancel()
                progressJob = null
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    val message = when (error) {
                        is ExportException -> describe(error)
                        else -> error?.localizedMessage ?: "Ekspor gagal."
                    }
                    temp.delete()
                    _state.value = State.Failure(message)
                    return@launch
                }

                _state.value = State.Running(96, "Menyimpan ke galeri…")
                val saved = withContext(Dispatchers.IO) {
                    saveToGallery(
                        temp,
                        "iTools_$stamp.mp4",
                        project.export.codec == com.itools.app.core.model.VideoCodec.HEVC,
                        stamp
                    )
                }
                temp.delete()
                _state.value = State.Success(
                    uri = saved.uri,
                    displayName = saved.name,
                    sizeBytes = saved.size,
                    savedToGallery = saved.gallery
                )
            } catch (t: Throwable) {
                progressJob?.cancel()
                temp.delete()
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = State.Failure(t.localizedMessage ?: "Ekspor gagal.")
            } finally {
                this@ExportEngine.transformer = null
            }
        }
    }

    fun cancel() {
        transformer?.let { runCatching { it.cancel() } }
        transformer = null
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    fun reset() {
        if (job?.isActive != true) _state.value = State.Idle
    }

    private data class Saved(val uri: Uri, val name: String, val size: Long, val gallery: Boolean)

    private fun saveToGallery(
        source: File,
        displayName: String,
        hevc: Boolean,
        stamp: String
    ): Saved {
        val mime = if (hevc) "video/mp4" else "video/mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mime)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/iTools")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                runCatching {
                    resolver.openOutputStream(uri)?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output, 1 shl 20) }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.onSuccess {
                    return Saved(uri, displayName, source.length(), true)
                }.onFailure {
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
        }

        // Legacy path or MediaStore failure. App-specific external storage never needs a runtime
        // permission and remains reachable through the share sheet.
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "iTools"
        ).apply { mkdirs() }
        val target = File(dir, displayName)
        source.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(mime),
            null
        )
        val authority = "${context.packageName}.files"
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, authority, target)
        }.getOrElse { Uri.fromFile(target) }
        return Saved(uri, displayName, target.length(), false)
    }

    private fun describe(error: ExportException): String = when (error.errorCode) {
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED ->
            "Encoder tidak tersedia untuk pengaturan ini. Coba H.264 atau resolusi lebih rendah."
        ExportException.ERROR_CODE_DECODING_FAILED ->
            "Media sumber tidak dapat didekode. Periksa file yang rusak atau tidak didukung."
        ExportException.ERROR_CODE_MUXING_FAILED ->
            "Gagal menulis file hasil. Coba resolusi atau bitrate lain."
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "File media hilang. Impor ulang media tersebut."
        ExportException.ERROR_CODE_IO_NO_PERMISSION ->
            "Aplikasi tidak memiliki izin menulis ke lokasi tersebut."
        else -> error.localizedMessage ?: "Ekspor gagal (kode ${error.errorCode})."
    }

    companion object {
        /** Used by the UI to translate simulator-free source codec labels. */
        const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        const val VIDEO_H264 = "video/avc"
        const val VIDEO_HEVC = "video/hevc"
        val ALL_VIDEO_MIME = listOf(VIDEO_H264, VIDEO_HEVC)
        const val DEFAULT_AUDIO = MimeTypes.AUDIO_AAC
    }
}
