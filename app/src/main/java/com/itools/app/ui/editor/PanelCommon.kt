package com.itools.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.itools.app.ui.components.SectionTitle
import com.itools.app.ui.theme.IToolsPalette

/** Colours offered by the local text, canvas and chroma pickers. */
val PanelColors: List<Long> = listOf(
    0xFFFFFFFF,
    0xFF000000,
    0xFFCFFF47,
    0xFFFFD84D,
    0xFFFF6B6B,
    0xFF72C7FF,
    0xFF58D6C1,
    0xFFCB8CFF,
    0xFFFF9F43,
    0xFF9AA4A8
)

fun rgbToLong(red: Int, green: Int, blue: Int): Long =
    0xFF000000L or
        (red.coerceIn(0, 255).toLong() shl 16) or
        (green.coerceIn(0, 255).toLong() shl 8) or
        blue.coerceIn(0, 255).toLong()

fun redOf(color: Long): Int = ((color shr 16) and 0xFF).toInt()

fun greenOf(color: Long): Int = ((color shr 8) and 0xFF).toInt()

fun blueOf(color: Long): Int = (color and 0xFF).toInt()

@Composable
fun PanelSection(title: String, subtitle: String? = null) {
    SectionTitle(title = title, subtitle = subtitle)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun PanelPrimaryButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IToolsPalette.Accent,
            contentColor = Color(0xFF162000)
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(label)
    }
}

@Composable
fun PanelOutlinedButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(label)
    }
}

@Composable
fun PanelActionRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
fun PanelSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF162000),
                checkedTrackColor = IToolsPalette.Accent
            )
        )
    }
}

@Composable
fun PanelColorPicker(
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        PanelColors.forEach { value ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(value))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                if (active) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (luminance(value) > 0.55f) Color.Black else Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

private fun luminance(color: Long): Float {
    val r = redOf(color) / 255f
    val g = greenOf(color) / 255f
    val b = blueOf(color) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

fun clipLabel(kind: com.itools.app.core.model.ClipKind): String = kindLabel(kind)
