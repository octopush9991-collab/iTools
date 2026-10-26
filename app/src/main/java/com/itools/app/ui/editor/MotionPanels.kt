package com.itools.app.ui.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itools.app.core.model.ChromaSpec
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.MaskShape
import com.itools.app.core.model.MaskSpec
import com.itools.app.core.model.TransitionSpec
import com.itools.app.core.model.TransitionType
import com.itools.app.core.model.formatDuration
import com.itools.app.ui.components.ChoiceChips
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.LabeledSlider
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette
import kotlin.math.abs
import kotlin.math.roundToInt

private fun editableVisual(selected: Clip?): Clip? =
    selected?.takeIf { it.kind == ClipKind.VIDEO || it.kind == ClipKind.IMAGE }

@Composable
fun SpeedPanel(selected: Clip?, viewModel: EditorViewModel) {
    val active = selected?.takeIf {
        it.kind == ClipKind.VIDEO || it.kind == ClipKind.AUDIO || it.kind == ClipKind.IMAGE
    }
    PanelShell(
        title = "Kecepatan",
        subtitle = "Ubah durasi klip, balik video, atau bekukan satu bingkai."
    ) {
        if (active == null) {
            NoClipHint("Pilih klip video atau audio untuk mengubah kecepatan.")
            return@PanelShell
        }
        SelectedClipSummary(active)
        Spacer(Modifier.height(12.dp))
        SmallCaps("Preset kecepatan")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = listOf(
                "0.25" to "0,25×",
                "0.5" to "0,5×",
                "1" to "1×",
                "1.5" to "1,5×",
                "2" to "2×",
                "4" to "4×"
            ),
            selected = listOf("0.25", "0.5", "1", "1.5", "2", "4").firstOrNull {
                abs(it.toFloat() - active.speed) < 0.01f
            },
            onSelect = { viewModel.setSpeed(it.toFloat()) }
        )
        Spacer(Modifier.height(10.dp))
        LabeledSlider(
            label = "Kecepatan",
            value = active.speed.coerceIn(0.1f, 8f),
            valueRange = 0.1f..8f,
            valueText = String.format("%.2f×", active.speed),
            onValueChange = {
                viewModel.beginTransaction()
                viewModel.setSpeed(it)
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )
        Text(
            "Durasi di timeline: ${formatDuration(active.durationMs)}. " +
                "Kecepatan memperpanjang atau memendekkan klip sekaligus.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (active.kind != ClipKind.IMAGE) {
            PanelSwitchRow(
                title = "Bisukan klip",
                subtitle = "Berguna saat membuat versi slow-motion tanpa audio.",
                checked = active.muted,
                onChecked = { viewModel.toggleSelectedClipMuted() }
            )
            Spacer(Modifier.height(8.dp))
        }
        PanelOutlinedButton(
            label = "Kembalikan ke 1×",
            icon = Icons.Default.Refresh
        ) { viewModel.setSpeed(1f) }

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        SmallCaps("Balik video")
        Spacer(Modifier.height(6.dp))
        Text(
            "Video dibaca bingkai demi bingkai (24 fps) lalu dirender ulang secara lokal. " +
                "Audio dibuang dan klip dibatasi 15 detik.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (active.reversed) {
            InfoCard("Klip ini sudah merupakan hasil video terbalik.")
            Spacer(Modifier.height(8.dp))
        }
        PanelPrimaryButton(
            label = if (active.kind == ClipKind.VIDEO) "Balik klip sekarang" else "Hanya untuk video",
            icon = Icons.Default.FastForward,
            enabled = active.kind == ClipKind.VIDEO
        ) { viewModel.toggleReverse() }

        Spacer(Modifier.height(18.dp))
        PanelDivider()

        SmallCaps("Bekukan bingkai")
        Spacer(Modifier.height(6.dp))
        Text(
            "Playhead menentukan bingkai yang diambil. Hasilnya menjadi gambar 2,5 detik pada " +
                "track overlay, sehingga klip asli tidak berubah.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        PanelOutlinedButton(
            label = "Bekukan bingkai di playhead",
            icon = Icons.Default.AcUnit,
            enabled = active.kind == ClipKind.VIDEO || active.kind == ClipKind.IMAGE
        ) { viewModel.freezeFrame() }
    }
}

@Composable
fun TransitionPanel(selected: Clip?, viewModel: EditorViewModel) {
    val active = editableVisual(selected)
    PanelShell(
        title = "Transisi",
        subtitle = "Transisi batas klip: fade, zoom, atau geser di dalam durasi klip."
    ) {
        if (active == null) {
            NoClipHint("Pilih klip video atau gambar untuk mengatur transisi.")
            return@PanelShell
        }
        SelectedClipSummary(active)
        Spacer(Modifier.height(12.dp))
        InfoCard(
            "Media3 tidak mendukung klip yang saling tumpang tindih, jadi transisi di sini " +
                "berjalan di dalam klip (bukan crossfade dua klip)."
        )
        Spacer(Modifier.height(14.dp))

        val typeOptions = listOf("none" to "Tidak ada") +
            TransitionType.values().filter { it != TransitionType.NONE }.map { it.name to it.label }

        SmallCaps("Transisi masuk")
        Spacer(Modifier.height(6.dp))
        ChoiceChips(
            options = typeOptions,
            selected = active.transitionIn?.type?.name ?: "none",
            onSelect = { id ->
                viewModel.setTransition(
                    incoming = true,
                    spec = transitionOf(id, active.transitionIn?.durationMs ?: 400)
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        SmallCaps("Transisi keluar")
        Spacer(Modifier.height(6.dp))
        ChoiceChips(
            options = typeOptions,
            selected = active.transitionOut?.type?.name ?: "none",
            onSelect = { id ->
                viewModel.setTransition(
                    incoming = false,
                    spec = transitionOf(id, active.transitionOut?.durationMs ?: 400)
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        val duration = active.transitionIn?.durationMs
            ?: active.transitionOut?.durationMs
            ?: 400L
        LabeledSlider(
            label = "Durasi transisi",
            value = duration.toFloat(),
            valueRange = 100f..1_500f,
            valueText = "${duration} ms",
            onValueChange = { value ->
                val ms = value.toLong().coerceIn(100, 1_500)
                viewModel.beginTransaction()
                active.transitionIn?.let {
                    viewModel.setTransition(true, it.copy(durationMs = ms))
                }
                active.transitionOut?.let {
                    viewModel.setTransition(false, it.copy(durationMs = ms))
                }
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )
        Spacer(Modifier.height(10.dp))
        PanelActionRow {
            PanelOutlinedButton(
                label = "Hapus keduanya",
                icon = Icons.Default.Refresh,
                modifier = Modifier.width(180.dp)
            ) {
                viewModel.setTransition(true, null)
                viewModel.setTransition(false, null)
            }
        }
    }
}

private fun transitionOf(id: String, durationMs: Long): TransitionSpec? {
    if (id == "none") return null
    val type = TransitionType.values().firstOrNull { it.name == id } ?: return null
    return TransitionSpec(type = type, durationMs = durationMs.coerceIn(100, 1_500))
}

@Composable
fun MaskPanel(selected: Clip?, viewModel: EditorViewModel) {
    val active = editableVisual(selected)
    PanelShell(
        title = "Masker",
        subtitle = "Bentuk topeng dengan tepi lembut. Area transparan mengikuti latar kanvas."
    ) {
        if (active == null) {
            NoClipHint("Pilih klip video atau gambar untuk menambahkan masker.")
            return@PanelShell
        }
        SelectedClipSummary(active)
        Spacer(Modifier.height(10.dp))
        val mask = active.mask
        PanelSwitchRow(
            title = "Aktifkan masker",
            subtitle = "Masker diterapkan di klip ini saja.",
            checked = mask != null,
            onChecked = { enabled ->
                viewModel.setMask(if (enabled) MaskSpec() else null)
            }
        )
        if (mask == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Pilih bentuk masker untuk mulai. Masker juga dapat diinversi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@PanelShell
        }
        Spacer(Modifier.height(12.dp))
        SmallCaps("Bentuk")
        Spacer(Modifier.height(6.dp))
        ChoiceChips(
            options = listOf(
                "linear" to "Garis",
                "circle" to "Lingkaran",
                "rectangle" to "Persegi"
            ),
            selected = when (mask.shape) {
                MaskShape.LINEAR -> "linear"
                MaskShape.CIRCLE -> "circle"
                MaskShape.RECTANGLE -> "rectangle"
            },
            onSelect = { id ->
                viewModel.setMask(
                    mask.copy(
                        shape = when (id) {
                            "circle" -> MaskShape.CIRCLE
                            "rectangle" -> MaskShape.RECTANGLE
                            else -> MaskShape.LINEAR
                        }
                    )
                )
            }
        )
        Spacer(Modifier.height(10.dp))
        MaskSlider(
            label = "Pusat horizontal",
            value = mask.centerX,
            range = 0f..1f,
            onStart = { viewModel.beginTransaction() },
            onValue = { viewModel.setMask(mask.copy(centerX = it)) },
            onFinished = { viewModel.endTransaction() }
        )
        MaskSlider(
            label = "Pusat vertikal",
            value = mask.centerY,
            range = 0f..1f,
            onStart = { viewModel.beginTransaction() },
            onValue = { viewModel.setMask(mask.copy(centerY = it)) },
            onFinished = { viewModel.endTransaction() }
        )
        MaskSlider(
            label = "Lebar",
            value = mask.width,
            range = 0.05f..2f,
            onStart = { viewModel.beginTransaction() },
            onValue = { viewModel.setMask(mask.copy(width = it)) },
            onFinished = { viewModel.endTransaction() }
        )
        MaskSlider(
            label = "Tinggi",
            value = mask.height,
            range = 0.05f..2f,
            onStart = { viewModel.beginTransaction() },
            onValue = { viewModel.setMask(mask.copy(height = it)) },
            onFinished = { viewModel.endTransaction() }
        )
        MaskSlider(
            label = "Rotasi",
            value = mask.rotation,
            range = -180f..180f,
            text = "${mask.rotation.roundToInt()}°",
            onStart = { viewModel.beginTransaction() },
            onValue = { viewModel.setMask(mask.copy(rotation = it)) },
            onFinished = { viewModel.endTransaction() }
        )
        MaskSlider(
            label = "Kelembutan tepi",
            value = mask.feather,
            range = 0.01f..0.49f,
            onStart = { viewModel.beginTransaction() },
            onValue = { viewModel.setMask(mask.copy(feather = it)) },
            onFinished = { viewModel.endTransaction() }
        )
        PanelSwitchRow(
            title = "Inversi masker",
            subtitle = "Menampilkan bagian luar bentuk, bukan bagian dalam.",
            checked = mask.invert,
            onChecked = { viewModel.setMask(mask.copy(invert = it)) }
        )
        Spacer(Modifier.height(10.dp))
        PanelOutlinedButton(
            label = "Reset masker",
            icon = Icons.Default.Refresh
        ) { viewModel.setMask(MaskSpec()) }
    }
}

@Composable
private fun MaskSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    text: String = String.format("%.2f", value),
    onStart: () -> Unit,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit
) {
    LabeledSlider(
        label = label,
        value = value,
        valueRange = range,
        valueText = text,
        onValueChange = {
            onStart()
            onValue(it)
        },
        onValueChangeFinished = onFinished
    )
}

@Composable
fun ChromaPanel(selected: Clip?, viewModel: EditorViewModel) {
    val active = editableVisual(selected)
    PanelShell(
        title = "Chroma key",
        subtitle = "Hapus latar berwarna seragam. Cocok untuk green screen sederhana."
    ) {
        if (active == null) {
            NoClipHint("Pilih klip video atau gambar untuk memakai chroma key.")
            return@PanelShell
        }
        SelectedClipSummary(active)
        Spacer(Modifier.height(10.dp))
        val chroma = active.chroma
        PanelSwitchRow(
            title = "Aktifkan chroma key",
            subtitle = "Warna yang dipilih menjadi transparan.",
            checked = chroma != null,
            onChecked = { enabled ->
                viewModel.setChroma(if (enabled) ChromaSpec() else null)
            }
        )
        if (chroma == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Gunakan latar hijau atau biru yang merata. Pencahayaan yang tidak rata membuat " +
                    "hasil kurang bersih.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@PanelShell
        }
        Spacer(Modifier.height(12.dp))
        SmallCaps("Warna kunci")
        Spacer(Modifier.height(7.dp))
        PanelColorPicker(
            selected = chroma.keyColor,
            onSelect = { viewModel.setChroma(chroma.copy(keyColor = it)) }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "RGB ${redOf(chroma.keyColor)} / ${greenOf(chroma.keyColor)} / ${blueOf(chroma.keyColor)}",
            style = MaterialTheme.typography.labelSmall,
            color = IToolsPalette.TextMuted
        )
        Spacer(Modifier.height(8.dp))
        ChromaSlider(
            label = "Merah",
            value = redOf(chroma.keyColor).toFloat(),
            range = 0f..255f,
            onValue = {
                viewModel.beginTransaction()
                viewModel.setChroma(
                    chroma.copy(
                        keyColor = rgbToLong(
                            it.toInt(),
                            greenOf(chroma.keyColor),
                            blueOf(chroma.keyColor)
                        )
                    )
                )
            },
            onFinished = { viewModel.endTransaction() }
        )
        ChromaSlider(
            label = "Hijau",
            value = greenOf(chroma.keyColor).toFloat(),
            range = 0f..255f,
            onValue = {
                viewModel.beginTransaction()
                viewModel.setChroma(
                    chroma.copy(
                        keyColor = rgbToLong(
                            redOf(chroma.keyColor),
                            it.toInt(),
                            blueOf(chroma.keyColor)
                        )
                    )
                )
            },
            onFinished = { viewModel.endTransaction() }
        )
        ChromaSlider(
            label = "Biru",
            value = blueOf(chroma.keyColor).toFloat(),
            range = 0f..255f,
            onValue = {
                viewModel.beginTransaction()
                viewModel.setChroma(
                    chroma.copy(
                        keyColor = rgbToLong(
                            redOf(chroma.keyColor),
                            greenOf(chroma.keyColor),
                            it.toInt()
                        )
                    )
                )
            },
            onFinished = { viewModel.endTransaction() }
        )
        LabeledSlider(
            label = "Kemiripan",
            value = chroma.similarity,
            valueRange = 0f..0.8f,
            valueText = String.format("%.2f", chroma.similarity),
            onValueChange = {
                viewModel.beginTransaction()
                viewModel.setChroma(chroma.copy(similarity = it))
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )
        LabeledSlider(
            label = "Kelembutan",
            value = chroma.smoothness,
            valueRange = 0.01f..0.4f,
            valueText = String.format("%.2f", chroma.smoothness),
            onValueChange = {
                viewModel.beginTransaction()
                viewModel.setChroma(chroma.copy(smoothness = it))
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )
        LabeledSlider(
            label = "Kurangi spill",
            value = chroma.spill,
            valueRange = 0f..1f,
            valueText = "${(chroma.spill * 100).roundToInt()}%",
            onValueChange = {
                viewModel.beginTransaction()
                viewModel.setChroma(chroma.copy(spill = it))
            },
            onValueChangeFinished = { viewModel.endTransaction() }
        )
        Spacer(Modifier.height(8.dp))
        PanelOutlinedButton(
            label = "Reset chroma",
            icon = Icons.Default.Refresh
        ) { viewModel.setChroma(ChromaSpec()) }
    }
}

@Composable
private fun ChromaSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit
) {
    LabeledSlider(
        label = label,
        value = value,
        valueRange = range,
        valueText = value.roundToInt().toString(),
        onValueChange = onValue,
        onValueChangeFinished = onFinished
    )
}
