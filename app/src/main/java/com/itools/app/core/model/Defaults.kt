package com.itools.app.core.model

import java.util.Locale

data class FilterPreset(
    val id: String,
    val label: String,
    val grade: ColorGrade
)

object FilterPresets {
    val all: List<FilterPreset> = listOf(
        FilterPreset("none", "Asli", ColorGrade()),
        FilterPreset(
            "vivid", "Vivid",
            ColorGrade(saturation = 1.35f, contrast = 1.12f, temperature = 0.05f)
        ),
        FilterPreset(
            "warm", "Hangat",
            ColorGrade(temperature = 0.28f, saturation = 1.1f, brightness = 0.02f)
        ),
        FilterPreset(
            "cool", "Dingin",
            ColorGrade(temperature = -0.24f, tint = -0.06f, contrast = 1.05f)
        ),
        FilterPreset(
            "mono", "Mono",
            ColorGrade(saturation = 0f, contrast = 1.08f)
        ),
        FilterPreset(
            "noir", "Noir",
            ColorGrade(saturation = 0f, contrast = 1.35f, brightness = -0.06f, vignette = 0.35f)
        ),
        FilterPreset(
            "vintage", "Vintage",
            ColorGrade(saturation = 0.82f, contrast = 0.92f, temperature = 0.14f, vignette = 0.22f, grain = 0.18f)
        ),
        FilterPreset(
            "film", "Film",
            ColorGrade(saturation = 0.92f, contrast = 1.06f, temperature = 0.08f, grain = 0.12f, vignette = 0.12f)
        ),
        FilterPreset(
            "fade", "Fade",
            ColorGrade(saturation = 0.88f, contrast = 0.86f, brightness = 0.05f)
        ),
        FilterPreset(
            "tealorange", "Teal Orange",
            ColorGrade(temperature = 0.16f, tint = 0.12f, saturation = 1.18f, contrast = 1.1f)
        ),
        FilterPreset(
            "pastel", "Pastel",
            ColorGrade(saturation = 0.78f, brightness = 0.1f, contrast = 0.9f, temperature = 0.1f)
        ),
        FilterPreset(
            "cyber", "Cyberpunk",
            ColorGrade(temperature = -0.18f, tint = 0.3f, saturation = 1.3f, contrast = 1.18f)
        ),
        FilterPreset(
            "golden", "Golden",
            ColorGrade(temperature = 0.34f, saturation = 1.12f, brightness = 0.04f)
        ),
        FilterPreset(
            "forest", "Forest",
            ColorGrade(temperature = -0.12f, tint = -0.14f, saturation = 1.08f, vignette = 0.18f)
        ),
        FilterPreset(
            "sunset", "Sunset",
            ColorGrade(temperature = 0.3f, tint = 0.16f, saturation = 1.22f, vignette = 0.2f)
        )
    )

    fun byId(id: String): FilterPreset = all.firstOrNull { it.id == id } ?: all.first()
}

data class LocalTemplate(
    val id: String,
    val name: String,
    val description: String,
    val canvas: CanvasSpec,
    val badge: String
)

object LocalTemplates {
    val all = listOf(
        LocalTemplate(
            "vertical", "Vlog vertikal", "9:16 • 1080p • 30 fps",
            CanvasSpec(1080, 1920, 30), "Populer"
        ),
        LocalTemplate(
            "youtube", "YouTube", "16:9 • 1080p • 30 fps",
            CanvasSpec(1920, 1080, 30), "16:9"
        ),
        LocalTemplate(
            "square", "Feed persegi", "1:1 • 1080p • 30 fps",
            CanvasSpec(1080, 1080, 30), "1:1"
        ),
        LocalTemplate(
            "cinema", "Cinema", "21:9 • 2560×1080 • 24 fps",
            CanvasSpec(2560, 1080, 24), "21:9"
        ),
        LocalTemplate(
            "story", "Story", "4:5 • 1080×1350 • 30 fps",
            CanvasSpec(1080, 1350, 30), "4:5"
        ),
        LocalTemplate(
            "uhd", "Ultra HD", "4K vertikal • 30 fps",
            CanvasSpec(2160, 3840, 30), "4K"
        )
    )
}

object StickerLibrary {
    val categories: Map<String, List<String>> = linkedMapOf(
        "Reaksi" to listOf("✨", "🔥", "😂", "😍", "😮", "👏", "💯", "🙌", "😎", "🤯"),
        "Bentuk" to listOf("❤️", "⭐", "💥", "💫", "🔆", "⚡", "🎯", "🔴", "🟢", "🔵"),
        "Simbol" to listOf("➡️", "⬆️", "✅", "❌", "❗", "❓", "➕", "➖", "🔔", "📌"),
        "Hiburan" to listOf("🎬", "🎵", "🎧", "🎉", "🎊", "🍿", "🎮", "📷", "🎨", "🕹️"),
        "Alam" to listOf("🌿", "🌸", "🌊", "☀️", "🌙", "☁️", "🌈", "🍀", "🦋", "🌻")
    )
}

object ExportPresets {
    data class Preset(val label: String, val width: Int, val height: Int, val fps: Int, val bitrate: Int)

    val all = listOf(
        Preset("720p • 30", 720, 1280, 30, 6),
        Preset("1080p • 30", 1080, 1920, 30, 12),
        Preset("1080p • 60", 1080, 1920, 60, 18),
        Preset("2K • 30", 1440, 2560, 30, 24),
        Preset("4K • 30", 2160, 3840, 30, 45),
        Preset("4K • 60", 2160, 3840, 60, 64)
    )
}

object Fonts {
    val families = listOf(
        "sans" to "Sans",
        "serif" to "Serif",
        "mono" to "Mono",
        "cursive" to "Cursive"
    )
}

fun formatTimecode(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val frames = ((ms.coerceAtLeast(0) % 1000) / 33)
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, frames)
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
