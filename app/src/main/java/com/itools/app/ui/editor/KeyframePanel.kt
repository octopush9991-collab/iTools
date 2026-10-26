package com.itools.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.Keyframe
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun KeyframePanel(
    selected: Clip?,
    playheadMs: Long,
    viewModel: EditorViewModel,
    onSeek: (Long) -> Unit
) {
    val active = selected
    PanelShell(
        title = "Keyframe",
        subtitle = "Animasi posisi, skala, rotasi, opasitas, dan volume sepanjang klip."
    ) {
        if (active == null) {
            NoClipHint("Pilih klip di timeline untuk mengatur keyframe.")
            return@PanelShell
        }
        SelectedClipSummary(active)
        Spacer(Modifier.height(12.dp))

        val local = (playheadMs - active.startMs).coerceIn(0, active.durationMs)
        val nearest = active.keyframes.minByOrNull { abs(it.timeMs - local) }

        InfoCard(
            text = if (active.kind == ClipKind.AUDIO) {
                "Klip audio memakai keyframe volume. Nilai lain tetap tersimpan meski tidak terlihat."
            } else {
                "Keyframe pertama mengubah kontrol Transformasi, Opasitas, dan Volume menjadi " +
                    "animasi. Nilai baru otomatis ditulis pada posisi playhead."
            }
        )
        Spacer(Modifier.height(12.dp))

        SmallCaps("Posisi saat ini")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${local} ms di dalam klip (${local / 1000f} detik)",
            style = MaterialTheme.typography.titleMedium,
            color = IToolsPalette.Accent
        )
        Text(
            text = if (nearest == null) {
                "Belum ada keyframe pada klip ini."
            } else {
                "Keyframe terdekat: ${nearest.timeMs} ms"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        PanelPrimaryButton(
            label = "Tambah keyframe di playhead",
            icon = Icons.Default.Add
        ) { viewModel.addKeyframeAtPlayhead() }
        Spacer(Modifier.height(8.dp))
        PanelActionRow {
            PanelOutlinedButton(
                label = "Hapus terdekat",
                icon = Icons.Default.Delete,
                enabled = nearest != null,
                modifier = Modifier.width(165.dp)
            ) { viewModel.removeKeyframeAtPlayhead() }
            PanelOutlinedButton(
                label = "Hapus semua",
                icon = Icons.Default.DeleteSweep,
                enabled = active.keyframes.isNotEmpty(),
                modifier = Modifier.width(165.dp)
            ) { viewModel.clearKeyframes() }
        }

        if (active.keyframes.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SmallCaps("Daftar keyframe (${active.keyframes.size})")
            Spacer(Modifier.height(7.dp))
            active.keyframes.sortedBy { it.timeMs }.forEach { keyframe ->
                KeyframeRow(
                    keyframe = keyframe,
                    selected = nearest?.timeMs == keyframe.timeMs,
                    onOpen = { onSeek(active.startMs + keyframe.timeMs) },
                    onRemove = { viewModel.removeKeyframe(keyframe.timeMs) }
                )
                Spacer(Modifier.height(5.dp))
            }
            Spacer(Modifier.height(8.dp))
            PanelOutlinedButton(
                label = "Lompat ke keyframe terakhir",
                icon = Icons.Default.SkipNext
            ) {
                active.keyframes.maxByOrNull { it.timeMs }?.let {
                    onSeek(active.startMs + it.timeMs)
                }
            }
        }
    }
}

@Composable
private fun KeyframeRow(
    keyframe: Keyframe,
    selected: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen),
        color = if (selected) {
            IToolsPalette.Accent.copy(alpha = 0.14f)
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
                    text = "${keyframe.timeMs} ms",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) IToolsPalette.Accent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary(keyframe),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Hapus keyframe",
                    tint = IToolsPalette.Danger,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private fun summary(keyframe: Keyframe): String {
    val scale = if (keyframe.scaleX == keyframe.scaleY) {
        String.format("skala %.2f", keyframe.scaleX)
    } else {
        String.format("skala %.2f×%.2f", keyframe.scaleX, keyframe.scaleY)
    }
    return "$scale • rotasi ${keyframe.rotation.roundToInt()}° • " +
        "opasitas ${(keyframe.opacity * 100).roundToInt()}% • " +
        "volume ${(keyframe.volume * 100).roundToInt()}%"
}
