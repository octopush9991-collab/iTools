package com.itools.app.ui.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.FilterPresets
import com.itools.app.core.model.Transform
import com.itools.app.ui.components.ChoiceChips
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.LabeledSlider
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette
import kotlin.math.roundToInt

private fun visualOrNull(selected: Clip?): Clip? =
    selected?.takeIf { it.kind == ClipKind.VIDEO || it.kind == ClipKind.IMAGE }

@Composable
fun FilterPanel(selected: Clip?, viewModel: EditorViewModel) {
    val active = visualOrNull(selected)
    PanelShell(
        title = "Filter",
        subtitle = "Preset warna deterministik. Intensitas 0% mengembalikan warna asli."
    ) {
        if (active == null) {
            NoClipHint("Pilih klip video atau gambar untuk menerapkan filter.")
            return@PanelShell
        }
        SelectedClipSummary(active)
        Spacer(Modifier.height(14.dp))
        SmallCaps("Preset")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = FilterPresets.all.map { it.id to it.label },
            selected = active.color.filterId,
            onSelect = { id ->
                viewModel.setFilter(id, active.color.intensity.coerceAtLeast(0.01f))
            }
        )
        Spacer(Modifier.height(14.dp))
        LabeledSlider(
            label = "Intensitas filter",
            value = active.color.intensity,
            valueRange = 0f..1f,
            valueText = "${(active.color.intensity * 100).roundToInt()}%",
            onValueChange = {
                viewModel.beginTransaction()
                viewModel.setFilter(active.color.filterId, it)
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Filter disimpan sebagai nilai warna di proyek JSON, jadi hasil ekspor identik " +
                "dengan pratinjau dan tidak bergantung pada aset eksternal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        PanelOutlinedButton(
            label = "Reset warna",
            icon = Icons.Default.Refresh
        ) { viewModel.resetColor() }
    }
}

@Composable
fun AdjustPanel(selected: Clip?, viewModel: EditorViewModel) {
    val active = visualOrNull(selected)
    PanelShell(
        title = "Warna",
        subtitle = "Koreksi warna satu tahap: cahaya, kontras, saturasi, white balance, vignette."
    ) {
        if (active == null) {
            NoClipHint("Pilih klip video atau gambar untuk mengatur warna.")
            return@PanelShell
        }
        SelectedClipSummary(active)
        Spacer(Modifier.height(12.dp))
        PanelPrimaryButton(
            label = "Auto-enhance dari histogram",
            icon = Icons.Default.AutoFixHigh
        ) { viewModel.autoEnhance() }
        Spacer(Modifier.height(12.dp))

        val grade = active.color
        GradeSlider(
            label = "Kecerahan",
            value = grade.brightness,
            range = -0.5f..0.5f,
            text = String.format("%+.2f", grade.brightness),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setColor(grade.copy(brightness = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        GradeSlider(
            label = "Kontras",
            value = grade.contrast,
            range = 0.5f..1.8f,
            text = String.format("%.2f", grade.contrast),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setColor(grade.copy(contrast = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        GradeSlider(
            label = "Saturasi",
            value = grade.saturation,
            range = 0f..2f,
            text = String.format("%.2f", grade.saturation),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setColor(grade.copy(saturation = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        GradeSlider(
            label = "Suhu warna",
            value = grade.temperature,
            range = -1f..1f,
            text = String.format("%+.2f", grade.temperature),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setColor(grade.copy(temperature = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        GradeSlider(
            label = "Tint",
            value = grade.tint,
            range = -1f..1f,
            text = String.format("%+.2f", grade.tint),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setColor(grade.copy(tint = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        GradeSlider(
            label = "Vignette",
            value = grade.vignette,
            range = 0f..1f,
            text = "${(grade.vignette * 100).roundToInt()}%",
            onValue = {
                viewModel.beginTransaction()
                viewModel.setColor(grade.copy(vignette = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        GradeSlider(
            label = "Grain",
            value = grade.grain,
            range = 0f..0.6f,
            text = "${(grade.grain * 100).roundToInt()}%",
            onValue = {
                viewModel.beginTransaction()
                viewModel.setColor(grade.copy(grain = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        LabeledSlider(
            label = "Opasitas klip",
            value = active.opacity,
            valueRange = 0f..1f,
            valueText = "${(active.opacity * 100).roundToInt()}%",
            onValueChange = {
                viewModel.beginTransaction()
                viewModel.setOpacity(it)
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )
        Spacer(Modifier.height(10.dp))
        PanelOutlinedButton(
            label = "Reset semua koreksi warna",
            icon = Icons.Default.Refresh
        ) {
            viewModel.resetColor()
            viewModel.setOpacity(1f)
        }
        if (active.keyframes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            InfoCard(
                "Klip memiliki keyframe. Perubahan opasitas ditulis sebagai keyframe pada playhead."
            )
        }
    }
}

@Composable
private fun GradeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    text: String,
    onValue: (Float) -> Unit,
    onEnd: () -> Unit
) {
    LabeledSlider(
        label = label,
        value = value,
        valueRange = range,
        valueText = text,
        onValueChange = onValue,
        onValueChangeFinished = onEnd
    )
}

@Composable
fun TransformPanel(selected: Clip?, viewModel: EditorViewModel) {
    val active = selected
    PanelShell(
        title = "Transformasi",
        subtitle = "Posisi, skala, dan rotasi. Keyframe membuat perubahan ini beranimasi."
    ) {
        if (active == null) {
            NoClipHint("Pilih klip untuk mengatur posisi dan skala.")
            return@PanelShell
        }
        SelectedClipSummary(active)
        Spacer(Modifier.height(6.dp))
        if (active.kind == ClipKind.TEXT ||
            active.kind == ClipKind.STICKER ||
            active.kind == ClipKind.CAPTION
        ) {
            InfoCard("Overlay teks dan stiker memakai transformasi yang sama dengan video.")
        }
        Spacer(Modifier.height(12.dp))

        val transform = active.transform
        TransformSlider(
            label = "Skala horizontal",
            value = transform.scaleX,
            range = 0.1f..3f,
            text = String.format("%.2f", transform.scaleX),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setTransform(transform.copy(scaleX = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        TransformSlider(
            label = "Skala vertikal",
            value = transform.scaleY,
            range = 0.1f..3f,
            text = String.format("%.2f", transform.scaleY),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setTransform(transform.copy(scaleY = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        TransformSlider(
            label = "Rotasi",
            value = transform.rotation,
            range = -180f..180f,
            text = "${transform.rotation.roundToInt()}°",
            onValue = {
                viewModel.beginTransaction()
                viewModel.setTransform(transform.copy(rotation = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        TransformSlider(
            label = "Geser horizontal",
            value = transform.offsetX,
            range = -1f..1f,
            text = String.format("%+.2f", transform.offsetX),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setTransform(transform.copy(offsetX = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        TransformSlider(
            label = "Geser vertikal",
            value = transform.offsetY,
            range = -1f..1f,
            text = String.format("%+.2f", transform.offsetY),
            onValue = {
                viewModel.beginTransaction()
                viewModel.setTransform(transform.copy(offsetY = it))
            },
            onEnd = { viewModel.endTransaction() }
        )
        LabeledSlider(
            label = "Opasitas",
            value = active.opacity,
            valueRange = 0f..1f,
            valueText = "${(active.opacity * 100).roundToInt()}%",
            onValueChange = {
                viewModel.beginTransaction()
                viewModel.setOpacity(it)
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )
        Spacer(Modifier.height(10.dp))
        PanelActionRow {
            PanelOutlinedButton(
                label = "Putar 90°",
                icon = Icons.Default.RotateRight,
                modifier = Modifier.width(150.dp)
            ) {
                val rotated = ((transform.rotation + 90f) % 360f + 360f) % 360f
                viewModel.setTransform(
                    transform.copy(rotation = if (rotated > 180f) rotated - 360f else rotated)
                )
            }
            PanelOutlinedButton(
                label = "Reset",
                icon = Icons.Default.Refresh,
                modifier = Modifier.width(120.dp)
            ) { viewModel.resetTransform() }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (active.keyframes.isEmpty()) {
                "Belum ada keyframe. Menambahkan keyframe akan mengubah kontrol ini menjadi animasi."
            } else {
                "${active.keyframes.size} keyframe aktif. Setiap perubahan transformasi ditulis pada playhead."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (active.keyframes.isEmpty()) IToolsPalette.TextMuted else IToolsPalette.Accent
        )
    }
}

@Composable
private fun TransformSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    text: String,
    onValue: (Float) -> Unit,
    onEnd: () -> Unit
) {
    LabeledSlider(
        label = label,
        value = value,
        valueRange = range,
        valueText = text,
        onValueChange = onValue,
        onValueChangeFinished = onEnd
    )
}
