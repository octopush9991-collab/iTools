package com.itools.app.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class EditorTool(val label: String, val icon: ImageVector) {
    MEDIA("Media", Icons.Default.PhotoLibrary),
    AUDIO("Audio", Icons.Default.MusicNote),
    TEXT("Teks", Icons.Default.TextFields),
    STICKER("Stiker", Icons.Default.EmojiEmotions),
    FILTER("Filter", Icons.Default.FilterVintage),
    ADJUST("Warna", Icons.Default.Tune),
    TRANSFORM("Transformasi", Icons.Default.OpenWith),
    SPEED("Kecepatan", Icons.Default.Speed),
    TRANSITION("Transisi", Icons.Default.SwapHoriz),
    MASK("Masker", Icons.Default.Crop),
    CHROMA("Chroma", Icons.Default.Colorize),
    KEYFRAME("Keyframe", Icons.Default.Timeline),
    CANVAS("Kanvas", Icons.Default.AspectRatio),
    CAPTION("Subtitle", Icons.Default.Subtitles),
    LOCAL_AI("AI lokal", Icons.Default.AutoAwesome),
    EXPORT("Ekspor", Icons.Default.FileUpload)
}
