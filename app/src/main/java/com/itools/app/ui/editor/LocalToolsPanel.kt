package com.itools.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.Project
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette

@Composable
fun LocalToolsPanel(
    project: Project,
    selected: Clip?,
    busy: Boolean,
    viewModel: EditorViewModel
) {
    var script by remember { mutableStateOf("") }
    val visual = selected?.takeIf { it.kind == ClipKind.VIDEO || it.kind == ClipKind.IMAGE }
    val hasAudioSource = project.tracks.flatMap { it.clips }.any {
        it.kind == ClipKind.AUDIO || (it.kind == ClipKind.VIDEO && it.uri.isNotBlank())
    }

    PanelShell(
        title = "AI lokal",
        subtitle = "Alat bantu berbasis analisis sinyal dan aturan lokal, bukan model cloud."
    ) {
        InfoCard(
            "Semua fitur di panel ini berjalan di perangkat. Tidak ada API, akun, atau unggahan. " +
                "Istilah “AI” dipakai untuk analisis otomatis yang benar-benar tersedia secara lokal."
        )
        Spacer(Modifier.height(16.dp))

        SmallCaps("Auto-enhance warna")
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (visual == null) {
                "Pilih klip video atau gambar untuk menganalisis histogram dan mengoreksi cahaya " +
                    "serta kontras secara otomatis."
            } else {
                "Menganalisis sampel bingkai dari ${visual.name}, lalu menetapkan koreksi warna. " +
                    "Hasilnya tetap dapat diubah manual di panel Warna."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(9.dp))
        PanelPrimaryButton(
            label = "Auto-enhance klip terpilih",
            icon = Icons.Default.AutoFixHigh,
            enabled = visual != null && !busy
        ) { viewModel.autoEnhance() }

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        SmallCaps("Deteksi ketukan")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Menghitung energi audio dan menandai ketukan pada timeline. Cocok untuk " +
                "menyusun potongan mengikuti irama. Gunakan audio atau video yang memiliki suara.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        MarkerStat(
            label = "Penanda ketukan",
            value = "${project.beatMarkersMs.size}",
            accent = IToolsPalette.Accent
        )
        Spacer(Modifier.height(8.dp))
        PanelOutlinedButton(
            label = "Deteksi ketukan",
            icon = Icons.Default.GraphicEq,
            enabled = hasAudioSource && !busy
        ) { viewModel.detectBeats() }

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        SmallCaps("Deteksi keheningan")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Menandai bagian dengan amplitudo rendah. Daftar hening ini dipakai oleh " +
                "pembagi naskah menjadi subtitle. Tidak ada transkripsi suara.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        MarkerStat(
            label = "Penanda hening",
            value = "${project.markersMs.size}",
            accent = IToolsPalette.SecondaryBlue
        )
        Spacer(Modifier.height(8.dp))
        PanelOutlinedButton(
            label = "Deteksi keheningan",
            icon = Icons.Default.VolumeMute,
            enabled = hasAudioSource && !busy
        ) { viewModel.detectSilence() }

        Spacer(Modifier.height(10.dp))
        PanelOutlinedButton(
            label = "Bersihkan semua penanda analisis",
            icon = Icons.Default.DeleteSweep,
            enabled = (project.beatMarkersMs.isNotEmpty() || project.markersMs.isNotEmpty()) && !busy
        ) { viewModel.clearAnalysisMarkers() }

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        SmallCaps("Naskah ke subtitle")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tempel naskah, satu baris per subtitle. Bila ada audio, waktu pembagian " +
                "mengikuti bagian hening; bila tidak, durasi dibagi merata.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = script,
            onValueChange = { script = it },
            label = { Text("Naskah") },
            placeholder = { Text("Baris pertama\nBaris kedua\nBaris ketiga") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        PanelPrimaryButton(
            label = "Buat subtitle dari naskah",
            enabled = script.isNotBlank() && !busy
        ) {
            viewModel.buildCaptionsFromScript(script)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Hasilnya dapat disunting di panel Subtitle, lalu diekspor sebagai SRT atau VTT.",
            style = MaterialTheme.typography.bodySmall,
            color = IToolsPalette.TextMuted
        )

        Spacer(Modifier.height(18.dp))
        PanelDivider()
        InfoCard(
            "Musik latar prosedural ada di panel Audio. Nada dibuat dengan osilator lokal, " +
                "tanpa sampel atau lisensi pihak ketiga."
        )
    }
}

@Composable
private fun MarkerStat(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = accent.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = accent
            )
        }
    }
}
