package com.itools.app.core.tools

import java.util.Locale

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Local SRT / WebVTT import and export. No speech recognition is performed here: automatic caption
 * generation without an on-device model would be dishonest, so iTools ships manual captions,
 * SRT/VTT import, and SRT/VTT export.
 */
object SubtitleIO {

    private val timePattern = Regex(
        """(\d{1,2}:)?\d{1,2}:\d{2}[.,]\d{1,3}"""
    )
    private val timingLine = Regex(
        """^\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[.,]\d{1,3}).*$"""
    )

    fun parse(content: String): List<SubtitleCue> {
        val normalized = content
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val cues = ArrayList<SubtitleCue>()
        val blocks = normalized.split(Regex("\n{2,}"))
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val timingIndex = lines.indexOfFirst { timingLine.matches(it) }
            if (timingIndex < 0) continue
            val match = timingLine.matchEntire(lines[timingIndex].trim()) ?: continue
            val start = parseTime(match.groupValues[1]) ?: continue
            val end = parseTime(match.groupValues[2]) ?: continue
            if (end <= start) continue
            val text = lines.drop(timingIndex + 1)
                .joinToString("\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()
            if (text.isNotEmpty()) cues.add(SubtitleCue(start, end, text))
        }
        return cues.sortedBy { it.startMs }
    }

    fun toSrt(cues: List<SubtitleCue>): String = buildString {
        cues.sortedBy { it.startMs }.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(formatTime(cue.startMs, ',')).append(" --> ")
            append(formatTime(cue.endMs, ',')).append('\n')
            append(cue.text.trim()).append("\n\n")
        }
    }

    fun toVtt(cues: List<SubtitleCue>): String = buildString {
        append("WEBVTT\n\n")
        cues.sortedBy { it.startMs }.forEach { cue ->
            append(formatTime(cue.startMs, '.')).append(" --> ")
            append(formatTime(cue.endMs, '.')).append('\n')
            append(cue.text.trim()).append("\n\n")
        }
    }

    fun formatTime(ms: Long, separator: Char): String {
        val safe = ms.coerceAtLeast(0)
        val hours = safe / 3_600_000
        val minutes = (safe % 3_600_000) / 60_000
        val seconds = (safe % 60_000) / 1_000
        val millis = safe % 1_000
        return String.format(
            Locale.US,
            "%02d:%02d:%02d%c%03d",
            hours, minutes, seconds, separator, millis
        )
    }

    fun parseTime(value: String): Long? {
        val cleaned = value.trim().replace(',', '.')
        val parts = cleaned.split(':')
        if (parts.size !in 2..3) return null
        val secondsPart = parts.last()
        val secParts = secondsPart.split('.')
        val seconds = secParts.getOrNull(0)?.toLongOrNull() ?: return null
        val millis = (secParts.getOrNull(1) ?: "0")
            .padEnd(3, '0')
            .take(3)
            .toLongOrNull() ?: 0
        val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0
        return ((hours * 60 + minutes) * 60 + seconds) * 1_000 + millis
    }

    fun extractTimes(line: String): Pair<Long, Long>? {
        val matches = timePattern.findAll(line).take(2).toList()
        if (matches.size < 2) return null
        val start = parseTime(matches[0].value) ?: return null
        val end = parseTime(matches[1].value) ?: return null
        return start to end
    }
}
