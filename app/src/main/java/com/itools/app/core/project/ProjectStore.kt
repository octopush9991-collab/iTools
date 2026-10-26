package com.itools.app.core.project

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.itools.app.core.media.ImportedAsset
import com.itools.app.core.model.CanvasSpec
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val durationMs: Long,
    val thumbnail: File?,
    val sizeBytes: Long,
    val clipCount: Int
)

/**
 * File-backed project store. No database, no network, no account.
 *
 * Directory layout:
 *   files/projects/<id>/project.json
 *   files/projects/<id>/thumb.jpg
 *   files/projects/<id>/assets/<copied media>
 */
class ProjectStore(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val root: File get() = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(id: String): File = File(root, id)

    fun assetsDir(id: String): File = File(projectDir(id), "assets").apply { mkdirs() }

    /** Allocates a new file below `assets/` and returns both the file and its project-relative URI. */
    fun newAsset(id: String, prefix: String, extension: String): Pair<File, String> {
        val name = "${prefix}_${System.currentTimeMillis()}.$extension"
        return File(assetsDir(id), name) to "assets/$name"
    }

    fun resolve(id: String, relativeUri: String): Uri {
        if (relativeUri.isBlank()) return Uri.EMPTY
        return if (relativeUri.startsWith("content://") || relativeUri.startsWith("file://")) {
            Uri.parse(relativeUri)
        } else {
            Uri.fromFile(File(projectDir(id), relativeUri))
        }
    }

    suspend fun list(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            runCatching {
                val file = File(dir, "project.json")
                if (!file.exists()) return@runCatching null
                val project = json.decodeFromString<Project>(file.readText())
                ProjectSummary(
                    id = project.id,
                    name = project.name,
                    updatedAt = project.updatedAt,
                    durationMs = project.durationMs,
                    thumbnail = File(dir, project.thumbnail ?: "thumb.jpg").takeIf { it.exists() },
                    sizeBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                    clipCount = project.tracks.sumOf { it.clips.size }
                )
            }.getOrNull()
        }?.sortedByDescending { it.updatedAt } ?: emptyList()
    }

    suspend fun create(name: String, canvas: CanvasSpec = CanvasSpec()): Project = withContext(Dispatchers.IO) {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            canvas = canvas,
            export = com.itools.app.core.model.ExportSettings(
                width = canvas.width,
                height = canvas.height,
                fps = canvas.fps
            ),
            tracks = defaultTracks()
        )
        projectDir(project.id).mkdirs()
        File(projectDir(project.id), "assets").mkdirs()
        save(project)
        project
    }

    /** Empty tracks make the editor understandable before the first import. */
    fun defaultTracks(): List<com.itools.app.core.model.Track> = listOf(
        com.itools.app.core.model.Track(type = com.itools.app.core.model.TrackType.VIDEO),
        com.itools.app.core.model.Track(type = com.itools.app.core.model.TrackType.OVERLAY),
        com.itools.app.core.model.Track(type = com.itools.app.core.model.TrackType.TEXT),
        com.itools.app.core.model.Track(type = com.itools.app.core.model.TrackType.AUDIO)
    )

    suspend fun load(id: String): Project? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(projectDir(id), "project.json")
            if (!file.exists()) return@runCatching null
            json.decodeFromString<Project>(file.readText()).copy(id = id)
        }.getOrNull()
    }

    suspend fun save(project: Project) = withContext(Dispatchers.IO) {
        val dir = projectDir(project.id).apply { mkdirs() }
        File(dir, "assets").mkdirs()
        val target = File(dir, "project.json")
        val temp = File(dir, "project.json.tmp")
        temp.writeText(json.encodeToString(Project.serializer(), project.copy(updatedAt = System.currentTimeMillis())))
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText())
            temp.delete()
        }
        Unit
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        projectDir(id).deleteRecursively()
        Unit
    }

    suspend fun rename(id: String, name: String): Project? {
        val project = load(id) ?: return null
        val updated = project.copy(name = name.ifBlank { project.name }, updatedAt = System.currentTimeMillis())
        save(updated)
        return updated
    }

    suspend fun duplicate(id: String): Project? = withContext(Dispatchers.IO) {
        val sourceDir = projectDir(id)
        val source = load(id) ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val newDir = projectDir(newId)
        sourceDir.copyRecursively(newDir, overwrite = false)
        val copy = source.copy(
            id = newId,
            name = source.name + " (salinan)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        save(copy)
        copy
    }

    suspend fun importMedia(projectId: String, uri: Uri): ImportedAsset? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMime(uri)
        val kind = when {
            mime.startsWith("video/") -> ClipKind.VIDEO
            mime.startsWith("image/") -> ClipKind.IMAGE
            mime.startsWith("audio/") -> ClipKind.AUDIO
            else -> return@withContext null
        }
        val displayName = queryDisplayName(uri) ?: "media_${System.currentTimeMillis()}"
        val extension = displayName.substringAfterLast('.', "").ifBlank { defaultExtension(mime) }
        val safe = displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        val targetName = "${kind.name.lowercase()}_${System.currentTimeMillis()}_${safe.take(24)}.$extension"
        val assets = File(projectDir(projectId), "assets").apply { mkdirs() }
        val target = File(assets, targetName)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
        } ?: return@withContext null

        val meta = probe(Uri.fromFile(target))
        ImportedAsset(
            localUri = "assets/$targetName",
            originalName = displayName,
            kind = kind,
            durationMs = meta.durationMs,
            width = meta.width,
            height = meta.height,
            rotation = meta.rotation
        )
    }

    suspend fun generateThumbnail(project: Project): String? = withContext(Dispatchers.IO) {
        val clip = project.tracks.flatMap { it.clips }
            .filter { it.kind == ClipKind.VIDEO || it.kind == ClipKind.IMAGE }
            .minByOrNull { it.startMs } ?: return@withContext null
        val uri = resolve(project.id, clip.uri)
        val retriever = MediaMetadataRetriever()
        val bitmap = runCatching {
            retriever.setDataSource(context, uri)
            if (clip.kind == ClipKind.IMAGE) {
                retriever.embeddedPicture?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?: retriever.getFrameAtTime(0)
            } else {
                retriever.getFrameAtTime(
                    TimeUnit.MILLISECONDS.toMicros(clip.sourceStartMs + 100),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            }
        }.getOrNull()
        retriever.release()
        if (bitmap == null) return@withContext null
        val scale = 512f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val out = File(projectDir(project.id), "thumb.jpg")
        out.outputStream().use { stream -> scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream) }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        "thumb.jpg"
    }

    suspend fun storageUsed(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearPreviewCache(): Unit = withContext(Dispatchers.IO) {
        File(context.cacheDir, "previews").deleteRecursively()
        File(context.cacheDir, "generated").deleteRecursively()
        Unit
    }

    data class MediaMeta(val durationMs: Long, val width: Int, val height: Int, val rotation: Int)

    fun probe(uri: Uri): MediaMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            MediaMeta(duration, width, height, rotation)
        } catch (_: Exception) {
            MediaMeta(0, 0, 0, 0)
        } finally {
            retriever.release()
        }
    }

    fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun guessMime(uri: Uri): String {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "mp4", "m4v", "mov", "mkv", "webm" -> "video/mp4"
            "jpg", "jpeg", "png", "webp", "heic" -> "image/jpeg"
            "mp3", "m4a", "aac", "wav", "ogg", "flac" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    private fun defaultExtension(mime: String): String = when {
        mime.startsWith("video/") -> "mp4"
        mime.startsWith("image/") -> "jpg"
        mime.startsWith("audio/") -> "m4a"
        else -> "bin"
    }
}
