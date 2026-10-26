package com.itools.app.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itools.app.core.audio.ProceduralMusic
import com.itools.app.core.audio.VoiceRecorder
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.Project
import com.itools.app.core.model.TrackType
import com.itools.app.core.model.formatDuration
import com.itools.app.ui.components.ChoiceChips
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.LabeledSlider
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette
import kotlinx.coroutines.delay

@Composable
fun MediaPanel(
    project: Project,
    selected: Clip?,
    viewModel: EditorViewModel,
    busy: Boolean,
    onSeek: (Long) -> Unit
) {
    val importMain = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMediaBatch(uris, TrackType.VIDEO)
    }
    val importOverlay = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMediaBatch(uris, TrackType.OVERLAY)
    }
    val mimeTypes = arrayOf("video/*", "image/*", "audio/*")

    PanelShell(
        title = "Media",
        subtitle = "Impor dari penyimpanan perangkat. File disalin ke folder proyek."
    ) {
        PanelPrimaryButton(
            label = "Tambah video, foto, atau audio",
            icon = Icons.Default.Add,
            enabled = !busy
        ) { importMain.launch(mimeTypes) }
        Spacer(Modifier.height(8.dp))
        PanelOutlinedButton(
            label = "Tambah sebagai overlay",
            icon = Icons.Default.Layers,
            enabled = !busy
        ) { importOverlay.launch(mimeTypes) }
        Spacer(Modifier.height(12.dp))
        InfoCard(
            text = "Pemilih file sistem dipakai langsung, jadi iTools tidak memerlukan izin " +
                "galeri. Media tetap dapat dipakai setelah aplikasi pengirim ditutup."
        )
        Spacer(Modifier.height(18.dp))

        if (selected != null) {
            SelectedClipSummary(selected)
            Spacer(Modifier.height(10.dp))
            PanelOutlinedButton(
                label = "Hapus klip terpilih",
                icon = Icons.Default.Delete
            ) { viewModel.deleteSelected() }
            Spacer(Modifier.height(18.dp))
        }

        SmallCaps("Klip di timeline")
        Spacer(Modifier.height(6.dp))
        val clips = project.tracks.flatMap { track ->
            track.clips.map { track.type to it }
        }.sortedBy { it.second.startMs }
        if (clips.isEmpty()) {
            NoClipHint("Belum ada media. Tambahkan file untuk mulai mengedit.")
        } else {
            clips.take(30).forEach { (type, clip) ->
                ClipRow(
                    clip = clip,
                    trackLabel = type.label,
                    onClick = {
                        viewModel.selectClip(clip.id)
                        onSeek(clip.startMs)
                    }
                )
            }
            if (clips.size > 30) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "Menampilkan 30 dari ${clips.size} klip. Gunakan timeline untuk sisanya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Tip: video vertikal, persegi, dan lanskap dapat dicampur dalam satu proyek. " +
                "Setiap klip dinormalisasi ke kanvas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ClipRow(clip: Clip, trackLabel: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (clip.kind) {
                    ClipKind.AUDIO -> Icons.Default.MusicNote
                    ClipKind.TEXT -> Icons.Default.Add
                    else -> Icons.Default.Layers
                },
                contentDescription = null,
                tint = IToolsPalette.Accent,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    clip.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$trackLabel • mulai ${formatDuration(clip.startMs)} • ${formatDuration(clip.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (clip.muted) {
                Icon(
                    Icons.Default.VolumeOff,
                    contentDescription = "Dibisukan",
                    tint = IToolsPalette.Danger,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AudioPanel(
    project: Project,
    selected: Clip?,
    viewModel: EditorViewModel,
    busy: Boolean,
    onSeek: (Long) -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var pendingRelative by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    val startRecording = {
        val target = viewModel.newVoiceFile()
        if (target == null) {
            viewModel.showMessage("Proyek belum siap untuk merekam.")
        } else if (recorder.start(target.first)) {
            pendingRelative = target.second
            startedAt = SystemClock.elapsedRealtime()
            elapsed = 0L
            recording = true
        } else {
            target.first.delete()
            viewModel.showMessage("Mikrofon tidak tersedia atau sedang dipakai aplikasi lain.")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else {
            permissionDenied = true
            viewModel.showMessage("Izin mikrofon diperlukan untuk merekam voice-over.")
        }
    }

    LaunchedEffect(recording, startedAt) {
        while (recording) {
            elapsed = SystemClock.elapsedRealtime() - startedAt
            delay(200)
        }
    }

    PanelShell(
        title = "Audio",
        subtitle = "Voice-over, musik prosedural, volume, dan fade. Semua di perangkat."
    ) {
        SmallCaps("Rekam voice-over")
        Spacer(Modifier.height(7.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = if (recording) {
                IToolsPalette.Danger.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (recording) IToolsPalette.Danger else IToolsPalette.Accent
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (recording) "Merekam…" else "Rekaman ditambahkan pada playhead",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (recording) formatDuration(elapsed) else "Maksimal sesuai ruang penyimpanan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (recording) {
                        IconButton(onClick = {
                            val file = recorder.stop()
                            val relative = pendingRelative
                            recording = false
                            pendingRelative = null
                            if (file != null && relative != null) {
                                viewModel.importVoiceover(relative)
                            } else {
                                viewModel.showMessage("Rekaman terlalu pendek atau gagal disimpan.")
                            }
                        }) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Hentikan rekaman",
                                tint = IToolsPalette.Danger
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startRecording() else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Mulai rekaman",
                                tint = IToolsPalette.Accent
                            )
                        }
                    }
                }
                if (permissionDenied) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Izin mikrofon ditolak. Aktifkan lewat pengaturan aplikasi bila ingin merekam.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IToolsPalette.Danger
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SmallCaps("Musik latar prosedural")
        Spacer(Modifier.height(6.dp))
        Text(
            "Nada dibuat dengan osilator lokal. Tidak ada sampel atau lisensi pihak ketiga.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(9.dp))
        var styleId by remember { mutableStateOf(ProceduralMusic.styles.first().id) }
        ChoiceChips(
            options = ProceduralMusic.styles.map { it.id to it.label },
            selected = styleId,
            onSelect = { styleId = it }
        )
        Spacer(Modifier.height(8.dp))
        PanelPrimaryButton(
            label = "Buat musik latar",
            icon = Icons.Default.MusicNote,
            enabled = !busy
        ) { viewModel.addGeneratedMusic(styleId) }

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        val audioClip = selected?.takeIf {
            it.kind == ClipKind.AUDIO || it.kind == ClipKind.VIDEO
        }
        if (audioClip == null) {
            NoClipHint(
                "Pilih klip audio atau video untuk mengatur volume, fade, dan bisu. " +
                    "Ketuk klip pada timeline terlebih dahulu."
            )
        } else {
            SelectedClipSummary(audioClip)
            Spacer(Modifier.height(12.dp))
            PanelSwitchRow(
                title = "Bisukan klip",
                subtitle = if (audioClip.kind == ClipKind.VIDEO) {
                    "Video tetap tampil, suaranya dimatikan."
                } else {
                    "Audio tetap ada di timeline tanpa suara."
                },
                checked = audioClip.muted,
                onChecked = { viewModel.toggleSelectedClipMuted() }
            )
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                label = "Volume",
                value = audioClip.volume.coerceIn(0f, 2f),
                valueRange = 0f..2f,
                valueText = "${(audioClip.volume * 100).toInt()}%",
                onValueChange = {
                    viewModel.beginTransaction()
                    viewModel.setVolume(it)
                },
                onValueChangeFinished = { viewModel.endTransaction() }
            )
            if (audioClip.keyframes.isNotEmpty()) {
                Text(
                    "Klip memiliki keyframe: perubahan volume ditulis sebagai keyframe di playhead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IToolsPalette.Accent
                )
                Spacer(Modifier.height(6.dp))
            }
            LabeledSlider(
                label = "Fade masuk",
                value = audioClip.fadeInMs / 1000f,
                valueRange = 0f..2.5f,
                valueText = String.format("%.1f dtk", audioClip.fadeInMs / 1000f),
                onValueChange = {
                    viewModel.beginTransaction()
                    viewModel.setFades((it * 1000).toLong(), audioClip.fadeOutMs)
                },
                onValueChangeFinished = { viewModel.endTransaction() }
            )
            LabeledSlider(
                label = "Fade keluar",
                value = audioClip.fadeOutMs / 1000f,
                valueRange = 0f..2.5f,
                valueText = String.format("%.1f dtk", audioClip.fadeOutMs / 1000f),
                onValueChange = {
                    viewModel.beginTransaction()
                    viewModel.setFades(audioClip.fadeInMs, (it * 1000).toLong())
                },
                onValueChangeFinished = { viewModel.endTransaction() }
            )
            Spacer(Modifier.height(10.dp))
            PanelActionRow {
                PanelOutlinedButton(
                    label = "Nolkan fade",
                    modifier = Modifier.width(150.dp)
                ) { viewModel.setFades(0, 0) }
            }
        }

        Spacer(Modifier.height(18.dp))
        PanelDivider()
        SmallCaps("Track audio")
        Spacer(Modifier.height(6.dp))
        val audioClips = project.tracks.filter { it.type == TrackType.AUDIO }
            .flatMap { it.clips }
            .sortedBy { it.startMs }
        if (audioClips.isEmpty()) {
            NoClipHint("Belum ada audio. Rekam voice-over atau buat musik latar.")
        } else {
            audioClips.forEach { clip ->
                ClipRow(
                    clip = clip,
                    trackLabel = "Audio",
                    onClick = {
                        viewModel.selectClip(clip.id)
                        onSeek(clip.startMs)
                    }
                )
            }
        }    }
}
