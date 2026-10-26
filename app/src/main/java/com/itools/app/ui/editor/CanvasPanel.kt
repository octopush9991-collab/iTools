package com.itools.app.ui.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itools.app.core.model.CanvasSpec
import com.itools.app.core.model.FitMode
import com.itools.app.ui.components.ChoiceChips
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.SmallCaps

private data class CanvasPresetOption(
    val id: String,
    val label: String,
    val spec: CanvasSpec
)

private val canvasOptions = listOf(
    CanvasPresetOption("9:16", "9:16 • 1080×1920", CanvasSpec(1080, 1920, 30)),
    CanvasPresetOption("16:9", "16:9 • 1920×1080", CanvasSpec(1920, 1080, 30)),
    CanvasPresetOption("1:1", "1:1 • 1080×1080", CanvasSpec(1080, 1080, 30)),
    CanvasPresetOption("4:5", "4:5 • 1080×1350", CanvasSpec(1080, 1350, 30)),
    CanvasPresetOption("21:9", "21:9 • 2560×1080", CanvasSpec(2560, 1080, 30)),
    CanvasPresetOption("4k", "4K • 2160×3840", CanvasSpec(2160, 3840, 30))
)

@Composable
fun CanvasPanel(
    canvas: CanvasSpec,
    viewModel: EditorViewModel
) {
    PanelShell(
        title = "Kanvas",
        subtitle = "Rasio, resolusi, orientasi, latar, dan cara media mengisi bingkai."
    ) {
        SmallCaps("Kanvas saat ini")
        Spacer(Modifier.height(5.dp))
        Text(
            text = "${canvas.width} × ${canvas.height} • ${canvas.ratioLabel} • ${canvas.fps} fps",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Pratinjau memakai resolusi lebih kecil agar hemat memori. Ekspor memakai " +
                "ukuran penuh yang dipilih di panel ini atau di panel Ekspor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        SmallCaps("Rasio & resolusi")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = canvasOptions.map { it.id to it.label },
            selected = canvasOptions.firstOrNull {
                it.spec.width == canvas.width && it.spec.height == canvas.height
            }?.id,
            onSelect = { id ->
                canvasOptions.firstOrNull { it.id == id }?.let { preset ->
                    viewModel.setCanvas(
                        preset.spec.copy(
                            fps = canvas.fps,
                            background = canvas.background,
                            fit = canvas.fit
                        )
                    )
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        PanelOutlinedButton(
            label = "Tukar orientasi (${if (canvas.width >= canvas.height) "jadi vertikal" else "jadi lanskap"})",
            icon = Icons.Default.SwapHoriz
        ) {
            viewModel.setCanvas(
                canvas.copy(
                    width = canvas.height,
                    height = canvas.width
                )
            )
        }

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        SmallCaps("Frame rate proyek")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = listOf(
                "24" to "24 fps",
                "25" to "25 fps",
                "30" to "30 fps",
                "60" to "60 fps"
            ),
            selected = canvas.fps.toString(),
            onSelect = { value ->
                viewModel.setCanvas(canvas.copy(fps = value.toIntOrNull()?.coerceIn(1, 120) ?: 30))
            }
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = "Frame rate proyek menentukan langkah maju/mundur satu bingkai dan target " +
                "ekspor awal. Sumber dengan fps lebih tinggi hanya dapat diturunkan saat ekspor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        SmallCaps("Cara media mengisi kanvas")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = listOf(
                FitMode.FIT.name to "Pas masuk",
                FitMode.CROP.name to "Isi penuh",
                FitMode.STRETCH.name to "Regangkan"
            ),
            selected = canvas.fit.name,
            onSelect = { id ->
                FitMode.values().firstOrNull { it.name == id }?.let { viewModel.setFitMode(it) }
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (canvas.fit) {
                FitMode.FIT -> "Seluruh bingkai terlihat, sisa ruang memakai warna latar kanvas."
                FitMode.CROP -> "Bingkai dipenuhi; tepi media yang berlebih dipotong."
                FitMode.STRETCH -> "Media diregangkan mengikuti kanvas. Berguna untuk koreksi cepat."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        SmallCaps("Warna latar")
        Spacer(Modifier.height(7.dp))
        PanelColorPicker(
            selected = canvas.background,
            onSelect = { viewModel.setCanvas(canvas.copy(background = it)) }
        )
        Spacer(Modifier.height(10.dp))
        InfoCard(
            "Warna latar dipakai saat media tidak memenuhi kanvas, pada celah antar klip, dan " +
                "pada bingkai pengisi. Perubahan langsung terlihat di pratinjau."
        )
    }
}
