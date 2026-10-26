package com.itools.app.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itools.app.core.model.Clip
import com.itools.app.core.model.Project
import com.itools.app.core.model.TrackType
import com.itools.app.core.tools.SubtitleIO
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CaptionPanel(
    project: Project,
    selectedClipId: String?,
    busy: Boolean,
    viewModel: EditorViewModel,
    onSeek: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val captions = project.tracks
        .filter { it.type == TrackType.CAPTION }
        .flatMap { it.clips }
        .sortedBy { it.startMs }

    fun writeExport(uri: Uri?, content: String, extension: String) {
        if (uri == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.bufferedWriter().use { it.write(content) }
                    } ?: error("Stream tidak tersedia")
                }.isSuccess
            }
            viewModel.showMessage(
                if (ok) "Subtitle $extension berhasil disimpan."
                else "Subtitle $extension gagal disimpan."
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                viewModel.showMessage("File subtitle kosong atau tidak dapat dibaca.")
            } else {
                viewModel.importSrt(text)
            }
        }
    }
    val srtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri -> writeExport(uri, viewModel.exportSrt(), "SRT") }
    val vttLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vtt")
    ) { uri -> writeExport(uri, SubtitleIO.toVtt(viewModel.captionCues()), "VTT") }

    PanelShell(
        title = "Subtitle",
        subtitle = "Tambah manual atau impor SRT/VTT. Tidak ada pengenalan suara palsu."
    ) {
        SmallCaps("Subtitle di proyek")
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (captions.isEmpty()) "Belum ada subtitle."
            else "${captions.size} subtitle • hingga ${SubtitleIO.formatTime(
                captions.maxOfOrNull { it.endMs } ?: 0L,
                ','
            )}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Teks subtitle baru") },
            placeholder = { Text("Ditambahkan pada playhead selama 2,5 detik") },
            minLines = 1,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        PanelPrimaryButton(
            label = "Tambahkan di playhead",
            icon = Icons.Default.Add,
            enabled = draft.isNotBlank() && !busy
        ) {
            viewModel.addCaptionAtPlayhead(draft)
            draft = ""
        }

        Spacer(Modifier.height(16.dp))
        SmallCaps("Impor & ekspor")
        Spacer(Modifier.height(7.dp))
        PanelActionRow {
            PanelOutlinedButton(
                label = "Impor SRT/VTT",
                icon = Icons.Default.Upload,
                enabled = !busy
            ) {
                importLauncher.launch(
                    arrayOf(
                        "text/plain",
                        "text/vtt",
                        "application/x-subrip",
                        "application/octet-stream"
                    )
                )
            }
            PanelOutlinedButton(
                label = "Ekspor SRT",
                icon = Icons.Default.Download,
                enabled = captions.isNotEmpty()
            ) {
                srtLauncher.launch(recommendedName(project.name, "srt"))
            }
            PanelOutlinedButton(
                label = "Ekspor VTT",
                icon = Icons.Default.Download,
                enabled = captions.isNotEmpty()
            ) {
                vttLauncher.launch(recommendedName(project.name, "vtt"))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "File ditulis lewat pemilih dokumen sistem. Tidak ada unggahan ke server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (captions.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SmallCaps("Daftar subtitle")
            Spacer(Modifier.height(7.dp))
            captions.forEach { clip ->
                CaptionRow(
                    clip = clip,
                    selected = clip.id == selectedClipId,
                    onOpen = {
                        viewModel.selectClip(clip.id)
                        onSeek(clip.startMs)
                    },
                    onDelete = { viewModel.removeClip(clip.id) }
                )
                Spacer(Modifier.height(5.dp))
            }
            Spacer(Modifier.height(10.dp))
            PanelOutlinedButton(
                label = "Hapus seluruh track subtitle",
                icon = Icons.Default.DeleteSweep
            ) { confirmClear = true }
        }

        Spacer(Modifier.height(16.dp))
        InfoCard(
            "Butuh pembagian waktu otomatis? Gunakan panel AI lokal → Naskah ke subtitle. " +
                "Fitur itu memakai deteksi keheningan dan pembagian waktu, bukan transkripsi."
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua subtitle?") },
            text = {
                Text("Seluruh ${captions.size} subtitle akan dihapus dari proyek. Tindakan ini bisa diurungkan.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearCaptions()
                }) { Text("Hapus", color = IToolsPalette.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun CaptionRow(
    clip: Clip,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen),
        color = if (selected) {
            IToolsPalette.CaptionTrack.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        }
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 3.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = clip.text?.text ?: clip.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${SubtitleIO.formatTime(clip.startMs, ',')} → " +
                        SubtitleIO.formatTime(clip.endMs, ','),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Hapus subtitle",
                    tint = IToolsPalette.Danger,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private fun recommendedName(projectName: String, extension: String): String {
    val base = projectName.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "subtitle" }
        .take(40)
    return "${base}.$extension"
}
