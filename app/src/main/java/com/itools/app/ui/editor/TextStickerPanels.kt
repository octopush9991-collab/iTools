package com.itools.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.Fonts
import com.itools.app.core.model.StickerLibrary
import com.itools.app.core.model.StickerSpec
import com.itools.app.core.model.TextAlign as SpecAlign
import com.itools.app.core.model.TextAnimation
import com.itools.app.core.model.TextSpec
import com.itools.app.core.model.formatDuration
import com.itools.app.ui.components.ChoiceChips
import com.itools.app.ui.components.InfoCard
import com.itools.app.ui.components.LabeledSlider
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette

@Composable
fun TextPanel(
    selected: Clip?,
    viewModel: EditorViewModel
) {
    val active = selected?.takeIf { it.kind == ClipKind.TEXT || it.kind == ClipKind.CAPTION }
    var draft by remember(active?.id, active?.text) {
        mutableStateOf(active?.text ?: TextSpec(text = ""))
    }

    PanelShell(
        title = if (active?.kind == ClipKind.CAPTION) "Subtitle" else "Teks",
        subtitle = "Teks dirender lokal menjadi bitmap, lalu dibakar ke pratinjau dan hasil ekspor."
    ) {
        if (active != null) {
            SelectedClipSummary(active)
            Spacer(Modifier.height(10.dp))
            LabeledSlider(
                label = "Durasi tampil",
                value = active.durationMs.toFloat(),
                valueRange = 500f..15_000f,
                valueText = formatDuration(active.durationMs),
                onValueChange = {
                    viewModel.beginTransaction()
                    viewModel.setOverlayDuration(it.toLong())
                },
                onValueChangeFinished = { viewModel.endTransaction() }
            )
            Spacer(Modifier.height(8.dp))
        } else {
            InfoCard("Belum ada teks terpilih. Teks baru akan ditambahkan pada posisi playhead.")
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = draft.text,
            onValueChange = { draft = draft.copy(text = it) },
            label = { Text("Isi teks") },
            placeholder = { Text("Contoh: Selamat datang di vlog saya") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        TextPreview(draft)
        Spacer(Modifier.height(14.dp))

        SmallCaps("Gaya huruf")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = Fonts.families,
            selected = draft.fontFamily,
            onSelect = { draft = draft.copy(fontFamily = it) }
        )
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            options = listOf("left" to "Kiri", "center" to "Tengah", "right" to "Kanan"),
            selected = when (draft.align) {
                SpecAlign.LEFT -> "left"
                SpecAlign.CENTER -> "center"
                SpecAlign.RIGHT -> "right"
            },
            onSelect = {
                draft = draft.copy(
                    align = when (it) {
                        "left" -> SpecAlign.LEFT
                        "right" -> SpecAlign.RIGHT
                        else -> SpecAlign.CENTER
                    }
                )
            }
        )
        Spacer(Modifier.height(6.dp))
        PanelActionRow {
            FilterChip(
                selected = draft.bold,
                onClick = { draft = draft.copy(bold = !draft.bold) },
                label = { Text("Tebal") }
            )
            FilterChip(
                selected = draft.italic,
                onClick = { draft = draft.copy(italic = !draft.italic) },
                label = { Text("Miring") }
            )
            FilterChip(
                selected = draft.shadow,
                onClick = { draft = draft.copy(shadow = !draft.shadow) },
                label = { Text("Bayangan") }
            )
        }
        Spacer(Modifier.height(8.dp))
        LabeledSlider(
            label = "Ukuran",
            value = draft.fontSize,
            valueRange = 18f..160f,
            valueText = "${draft.fontSize.toInt()}",
            onValueChange = { draft = draft.copy(fontSize = it) }
        )
        LabeledSlider(
            label = "Tebal garis luar",
            value = draft.strokeWidth,
            valueRange = 0f..12f,
            valueText = String.format("%.1f", draft.strokeWidth),
            onValueChange = { draft = draft.copy(strokeWidth = it) }
        )
        LabeledSlider(
            label = "Jarak huruf",
            value = draft.letterSpacing,
            valueRange = -0.05f..0.3f,
            valueText = String.format("%.2f", draft.letterSpacing),
            onValueChange = { draft = draft.copy(letterSpacing = it) }
        )
        Spacer(Modifier.height(10.dp))

        SmallCaps("Warna teks")
        Spacer(Modifier.height(7.dp))
        PanelColorPicker(
            selected = draft.color,
            onSelect = { draft = draft.copy(color = it) }
        )
        Spacer(Modifier.height(10.dp))
        PanelSwitchRow(
            title = "Kotak latar",
            subtitle = "Latar gelap transparan agar teks mudah dibaca.",
            checked = draft.background != 0x00000000L,
            onChecked = {
                draft = draft.copy(background = if (it) 0x99000000 else 0x00000000)
            }
        )
        Spacer(Modifier.height(6.dp))

        SmallCaps("Animasi")
        Spacer(Modifier.height(7.dp))
        ChoiceChips(
            options = listOf(
                "none" to "Tanpa",
                "fade" to "Fade",
                "slide" to "Naik",
                "pop" to "Pop",
                "type" to "Mesin tik"
            ),
            selected = when (draft.animation) {
                TextAnimation.NONE -> "none"
                TextAnimation.FADE_IN -> "fade"
                TextAnimation.SLIDE_UP -> "slide"
                TextAnimation.POP -> "pop"
                TextAnimation.TYPEWRITER -> "type"
            },
            onSelect = {
                draft = draft.copy(
                    animation = when (it) {
                        "fade" -> TextAnimation.FADE_IN
                        "slide" -> TextAnimation.SLIDE_UP
                        "pop" -> TextAnimation.POP
                        "type" -> TextAnimation.TYPEWRITER
                        else -> TextAnimation.NONE
                    }
                )
            }
        )
        Spacer(Modifier.height(16.dp))

        val canApply = draft.text.isNotBlank()
        PanelPrimaryButton(
            label = if (active == null) "Tambahkan teks" else "Terapkan perubahan",
            icon = if (active == null) Icons.Default.Add else Icons.Default.Done,
            enabled = canApply
        ) {
            val spec = if (draft.text.isBlank()) draft.copy(text = "Teks baru") else draft
            if (active == null) {
                viewModel.addText(spec)
            } else {
                viewModel.updateText(spec)
                viewModel.showMessage("Teks diperbarui.")
            }
        }
        if (active != null) {
            Spacer(Modifier.height(8.dp))
            PanelOutlinedButton(
                label = "Hapus teks",
                icon = Icons.Default.Delete
            ) { viewModel.deleteSelected() }
        }
    }
}

@Composable
private fun TextPreview(spec: TextSpec) {
    val family = when (spec.fontFamily) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1013), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(spec.background),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = spec.text.ifBlank { "Pratinjau teks" },
                color = Color(spec.color),
                fontFamily = family,
                fontSize = (spec.fontSize / 3f).coerceIn(12f, 34f).sp,
                fontWeight = if (spec.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (spec.italic) FontStyle.Italic else FontStyle.Normal,
                textAlign = when (spec.align) {
                    SpecAlign.LEFT -> TextAlign.Left
                    SpecAlign.RIGHT -> TextAlign.Right
                    else -> TextAlign.Center
                },
                letterSpacing = spec.letterSpacing.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun StickerPanel(
    selected: Clip?,
    viewModel: EditorViewModel
) {
    val active = selected?.takeIf { it.kind == ClipKind.STICKER }
    var category by remember { mutableStateOf(StickerLibrary.categories.keys.first()) }
    var size by remember(active?.id) { mutableFloatStateOf(active?.sticker?.size ?: 0.24f) }
    var animation by remember(active?.id) {
        mutableStateOf(active?.sticker?.animation ?: TextAnimation.POP)
    }

    PanelShell(
        title = "Stiker",
        subtitle = "Emoji dari sistem, dirender tanpa pustaka maupun aset berlisensi."
    ) {
        if (active != null) {
            SelectedClipSummary(active)
            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Ukuran stiker",
                value = size,
                valueRange = 0.06f..0.8f,
                valueText = "${(size * 100).toInt()}%",
                onValueChange = {
                    size = it
                    viewModel.beginTransaction()
                    viewModel.updateSticker(
                        active.sticker?.copy(size = it) ?: StickerSpec(size = it, animation = animation)
                    )
                },
                onValueChangeFinished = { viewModel.endTransaction() }
            )
            Spacer(Modifier.height(6.dp))
        } else {
            InfoCard("Ketuk stiker untuk menambahkannya pada posisi playhead.")
            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Ukuran stiker baru",
                value = size,
                valueRange = 0.06f..0.8f,
                valueText = "${(size * 100).toInt()}%",
                onValueChange = { size = it }
            )
        }

        ChoiceChips(
            options = listOf(
                "none" to "Tanpa",
                "fade" to "Fade",
                "slide" to "Naik",
                "pop" to "Pop"
            ),
            selected = when (animation) {
                TextAnimation.FADE_IN -> "fade"
                TextAnimation.SLIDE_UP -> "slide"
                TextAnimation.POP -> "pop"
                else -> "none"
            },
            onSelect = {
                animation = when (it) {
                    "fade" -> TextAnimation.FADE_IN
                    "slide" -> TextAnimation.SLIDE_UP
                    "pop" -> TextAnimation.POP
                    else -> TextAnimation.NONE
                }
                if (active != null) {
                    viewModel.updateSticker(
                        active.sticker?.copy(animation = animation)
                            ?: StickerSpec(size = size, animation = animation)
                    )
                }
            }
        )
        Spacer(Modifier.height(14.dp))

        SmallCaps("Kategori")
        Spacer(Modifier.height(6.dp))
        ChoiceChips(
            options = StickerLibrary.categories.keys.map { it to it },
            selected = category,
            onSelect = { category = it }
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StickerLibrary.categories[category].orEmpty().forEach { emoji ->
                Surface(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable {
                            viewModel.addSticker(
                                StickerSpec(emoji = emoji, size = size, animation = animation)
                            )
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 26.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (active != null) {
            LabeledSlider(
                label = "Durasi stiker",
                value = active.durationMs.toFloat(),
                valueRange = 500f..15_000f,
                valueText = formatDuration(active.durationMs),
                onValueChange = {
                    viewModel.beginTransaction()
                    viewModel.setOverlayDuration(it.toLong())
                },
                onValueChangeFinished = { viewModel.endTransaction() }
            )
            PanelOutlinedButton(
                label = "Hapus stiker",
                icon = Icons.Default.Delete
            ) { viewModel.deleteSelected() }
        }
    }
}
