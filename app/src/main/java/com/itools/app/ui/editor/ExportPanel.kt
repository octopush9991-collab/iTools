package com.itools.app.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.itools.app.core.model.CanvasSpec
import com.itools.app.core.model.ExportPresets
import com.itools.app.core.model.ExportSettings
import com.itools.app.core.model.FitMode
import com.itools.app.core.model.Project
import com.itools.app.core.model.VideoCodec
import com.itools.app.core.model.formatBytes
import com.itools.app.core.model.formatDuration
import com.itools.app.core.render.ExportEngine
import com.itools.app.ui.components.ChoiceChips
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.LabeledSlider
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class ResolvedPreset(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int
)

@Composable
fun ExportPanel(
    project: Project,
    exportState: ExportEngine.State,
    busy: Boolean,
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    val export = project.export
    val presets = resolvedPresets(project.canvas)
    val selectedPreset = presets.firstOrNull {
        it.width == export.width && it.height == export.height && it.fps == export.fps
    }?.id

    PanelShell(
        title = "Ekspor",
        subtitle = "Render akhir di perangkat. Tanpa watermark, batas durasi, atau pembelian."
    ) {
        when (exportState) {
            is ExportEngine.State.Running -> {
                RunningExport(
                    state = exportState,
                    onCancel = { viewModel.cancelExport() }
                )
                Spacer(Modifier.height(14.dp))
                InfoCard(
                    "Biarkan aplikasi tetap terbuka selama ekspor. Proses memakai encoder " +
                        "perangkat dan dapat lebih lambat pada video 4K."
                )
                return@PanelShell
            }
            is ExportEngine.State.Success -> {
                ExportSuccess(
                    state = exportState,
                    onShare = { uri -> shareVideo(context, uri, viewModel) },
                    onOpen = { uri -> openVideo(context, uri, viewModel) },
                    onAgain = {
                        viewModel.resetExportState()
                        viewModel.startExport()
                    },
                    onClose = { viewModel.resetExportState() }
                )
                return@PanelShell
            }
            is ExportEngine.State.Failure -> {
                ExportFailure(
                    message = exportState.message,
                    onRetry = {
                        viewModel.resetExportState()
                        viewModel.startExport()
                    },
                    onClose = { viewModel.resetExportState() }
                )
                return@PanelShell
            }
            ExportEngine.State.Idle -> Unit
        }

        SmallCaps("Ringkasan")
        Spacer(Modifier.height(5.dp))
        Text(
            text = "${export.width} × ${export.height} • ${export.fps} fps • " +
                "${export.codec.label} • ${export.bitrateMbps} Mbps",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Durasi ${formatDuration(project.durationMs)} • perkiraan ukuran " +
                formatBytes(estimatedBytes(project.durationMs, export)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        SmallCaps("Resolusi")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = presets.map { it.id to it.label },
            selected = selectedPreset,
            onSelect = { id ->
                presets.firstOrNull { it.id == id }?.let { preset ->
                    viewModel.setExport(
                        export.copy(
                            width = preset.width,
                            height = preset.height,
                            fps = preset.fps,
                            bitrateMbps = preset.bitrate
                        )
                    )
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Pilihan mengikuti rasio kanvas ${project.canvas.ratioLabel}. Ukuran dapat " +
                "berbeda dari daftar umum agar tidak ada peregangan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))
        SmallCaps("Frame rate")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = listOf(
                "24" to "24",
                "25" to "25",
                "30" to "30",
                "60" to "60"
            ),
            selected = export.fps.toString(),
            onSelect = { value ->
                viewModel.setExport(export.copy(fps = value.toIntOrNull() ?: export.fps))
            }
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Ekspor hanya dapat menurunkan frame rate relatif terhadap sumber. " +
                "Frame rate lebih tinggi tidak menambah detail gerakan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))
        SmallCaps("Codec video")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = VideoCodec.values().map { it.name to it.label },
            selected = export.codec.name,
            onSelect = { id ->
                VideoCodec.values().firstOrNull { it.name == id }?.let {
                    viewModel.setExport(export.copy(codec = it))
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (export.codec == VideoCodec.HEVC) {
                "HEVC lebih kecil pada kualitas sama, tetapi dukungan pemutaran berbeda antar perangkat."
            } else {
                "H.264 paling kompatibel dengan perangkat dan aplikasi lain."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))
        LabeledSlider(
            label = "Bitrate video",
            value = export.bitrateMbps.toFloat(),
            valueRange = 2f..64f,
            valueText = "${export.bitrateMbps} Mbps",
            onValueChange = {
                viewModel.beginTransaction()
                viewModel.setExport(export.copy(bitrateMbps = it.roundToInt().coerceIn(2, 64)))
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )

        Spacer(Modifier.height(12.dp))
        ChoiceChips(
            options = listOf(
                FitMode.FIT.name to "Pas masuk",
                FitMode.CROP.name to "Isi penuh",
                FitMode.STRETCH.name to "Regangkan"
            ),
            selected = export.fit.name,
            onSelect = { id ->
                FitMode.values().firstOrNull { it.name == id }?.let { viewModel.setFitMode(it) }
            }
        )

        Spacer(Modifier.height(6.dp))
        PanelSwitchRow(
            title = "Sertakan audio",
            subtitle = "Matikan untuk ekspor video tanpa suara.",
            checked = export.includeAudio,
            onChecked = { viewModel.setExport(export.copy(includeAudio = it)) }
        )

        Spacer(Modifier.height(16.dp))
        PanelPrimaryButton(
            label = "Mulai ekspor",
            icon = Icons.Default.FileDownload,
            enabled = project.durationMs > 0 && !busy
        ) { viewModel.startExport() }

        if (project.durationMs <= 0) {
            Spacer(Modifier.height(8.dp))
            NoClipHint("Tambahkan minimal satu klip sebelum mengekspor.")
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Hasil disimpan ke Movies/iTools. Tanpa watermark dan tanpa batas durasi.",
                style = MaterialTheme.typography.bodySmall,
                color = IToolsPalette.Accent
            )
        }
    }
}

@Composable
private fun RunningExport(
    state: ExportEngine.State.Running,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.note,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${state.progress}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = IToolsPalette.Accent
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = IToolsPalette.Accent,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Spacer(Modifier.height(12.dp))
            PanelOutlinedButton(label = "Batalkan ekspor") { onCancel() }
        }
    }
}

@Composable
private fun ExportSuccess(
    state: ExportEngine.State.Success,
    onShare: (Uri) -> Unit,
    onOpen: (Uri) -> Unit,
    onAgain: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = IToolsPalette.Accent.copy(alpha = 0.10f)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = IToolsPalette.Accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Ekspor selesai", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatBytes(state.sizeBytes) + " • " + if (state.savedToGallery) {
                    "tersimpan di Galeri (Movies/iTools)"
                } else {
                    "tersimpan di penyimpanan aplikasi (Movies/iTools)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            PanelActionRow {
                PanelPrimaryButton(
                    label = "Bagikan",
                    icon = Icons.Default.Share,
                    modifier = Modifier.weight(1f)
                ) { onShare(state.uri) }
                PanelOutlinedButton(
                    label = "Buka",
                    icon = Icons.Default.OpenInNew,
                    modifier = Modifier.weight(1f)
                ) { onOpen(state.uri) }
            }
            Spacer(Modifier.height(8.dp))
            PanelActionRow {
                PanelOutlinedButton(
                    label = "Ekspor lagi",
                    modifier = Modifier.weight(1f)
                ) { onAgain() }
                PanelOutlinedButton(
                    label = "Tutup",
                    modifier = Modifier.weight(1f)
                ) { onClose() }
            }
        }
    }
}

@Composable
private fun ExportFailure(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = IToolsPalette.Danger.copy(alpha = 0.10f)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = IToolsPalette.Danger,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Ekspor gagal", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            PanelActionRow {
                PanelOutlinedButton(
                    label = "Coba lagi",
                    modifier = Modifier.weight(1f)
                ) { onRetry() }
                PanelOutlinedButton(
                    label = "Tutup",
                    modifier = Modifier.weight(1f)
                ) { onClose() }
            }
        }
    }
}

private fun resolvedPresets(canvas: CanvasSpec): List<ResolvedPreset> {
    val shortEdge = min(canvas.width, canvas.height).coerceAtLeast(2)
    val longEdge = max(canvas.width, canvas.height).coerceAtLeast(2)
    return ExportPresets.all.map { preset ->
        val targetShort = min(preset.width, preset.height)
        val scale = targetShort.toFloat() / shortEdge
        val width = even((canvas.width * scale).roundToInt()).coerceIn(2, 7_680)
        val height = even((canvas.height * scale).roundToInt()).coerceIn(2, 7_680)
        ResolvedPreset(
            id = "${width}x${height}@${preset.fps}",
            label = "${width}×${height} • ${preset.fps} fps",
            width = width,
            height = height,
            fps = preset.fps,
            bitrate = preset.bitrate
        )
    }.distinctBy { it.id }
}

private fun even(value: Int): Int = (value.coerceAtLeast(2) / 2) * 2

private fun estimatedBytes(durationMs: Long, export: ExportSettings): Long {
    val seconds = durationMs / 1000.0
    val video = export.bitrateMbps * 1_000_000.0 / 8.0 * seconds
    val audio = if (export.includeAudio) 128_000.0 / 8.0 * seconds else 0.0
    return (video + audio).toLong()
}

private fun shareVideo(context: Context, uri: Uri, viewModel: EditorViewModel) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Bagikan video iTools"))
    }.onFailure {
        viewModel.showMessage("Tidak ada aplikasi yang dapat menerima video ini.")
    }
}

private fun openVideo(context: Context, uri: Uri, viewModel: EditorViewModel) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(view)
    }.onFailure {
        viewModel.showMessage("Tidak ada pemutar video yang tersedia.")
    }
}
