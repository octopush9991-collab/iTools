package com.itools.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itools.app.core.model.Clip
import com.itools.app.core.model.ClipKind
import com.itools.app.core.model.formatDuration
import com.itools.app.ui.components.SmallCaps
import com.itools.app.ui.theme.IToolsPalette

@Composable
fun PanelShell(
    title: String,
    subtitle: String? = null,
    badge: String = "GRATIS",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 30.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = IToolsPalette.Accent.copy(alpha = 0.13f)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = IToolsPalette.Accent,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
fun SelectedClipSummary(clip: Clip) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = clip.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
                Text(
                    text = "${kindLabel(clip.kind)} • ${formatDuration(clip.durationMs)} • " +
                        "mulai ${formatDuration(clip.startMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Terpilih",
                style = MaterialTheme.typography.labelSmall,
                color = IToolsPalette.Accent
            )
        }
    }
}

@Composable
fun NoClipHint(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun PanelDivider() {
    Spacer(Modifier.height(18.dp))
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    )
    Spacer(Modifier.height(18.dp))
}

@Composable
fun StatLine(items: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items.forEach { (label, value) ->
            Column {
                SmallCaps(label)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

fun kindLabel(kind: ClipKind): String = when (kind) {
    ClipKind.VIDEO -> "Video"
    ClipKind.IMAGE -> "Gambar"
    ClipKind.AUDIO -> "Audio"
    ClipKind.TEXT -> "Teks"
    ClipKind.STICKER -> "Stiker"
    ClipKind.CAPTION -> "Subtitle"
}
