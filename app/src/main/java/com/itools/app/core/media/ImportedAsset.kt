package com.itools.app.core.media

import com.itools.app.core.model.ClipKind

data class ImportedAsset(
    val localUri: String,
    val originalName: String,
    val kind: ClipKind,
    val durationMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0
) {
    val isImage: Boolean get() = kind == ClipKind.IMAGE
    val isAudio: Boolean get() = kind == ClipKind.AUDIO
}
