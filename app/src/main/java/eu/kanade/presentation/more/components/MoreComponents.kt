package eu.kanade.presentation.more.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// grouped iOS-style sections: caption header, rounded card, hairline dividers between rows

/** Inset of the row dividers: they start after the icon tile, like iOS lists. */
private val DividerInset = 58.dp

@Composable
fun MoreSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        if (title.isNotBlank()) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            DividedColumn(
                inset = DividerInset,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                content = content,
            )
        }
    }
}

/** Stacks its children vertically and draws a hairline between neighbours. */
@Composable
fun DividedColumn(
    inset: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bounds = remember { ArrayList<Int>() }
    Layout(
        content = content,
        modifier = modifier.drawWithContent {
            drawContent()
            val stroke = 0.5.dp.toPx()
            val start = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) inset.toPx() else 0f
            val end = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) size.width else size.width - inset.toPx()
            bounds.forEach { y ->
                drawLine(color, Offset(start, y.toFloat()), Offset(end, y.toFloat()), stroke)
            }
        },
    ) { measurables, constraints ->
        val child = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(child) }
        val width = placeables.maxOfOrNull { it.width }?.coerceAtLeast(constraints.minWidth) ?: constraints.minWidth
        val height = placeables.sumOf { it.height }
        layout(width, height) {
            bounds.clear()
            var y = 0
            val visible = placeables.filter { it.height > 0 }
            visible.forEachIndexed { index, placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
                if (index < visible.lastIndex) bounds.add(y)
            }
        }
    }
}

/** iOS system colours for icon tiles. */
private val TilePalette = listOf(
    Color(0xFF0A84FF),
    Color(0xFF30B158),
    Color(0xFFFF9500),
    Color(0xFFFF453A),
    Color(0xFFAF52DE),
    Color(0xFFFF375F),
    Color(0xFF32ADE6),
    Color(0xFF5E5CE6),
    Color(0xFF8E8E93),
    Color(0xFF00C7BE),
)

/** Stable tile colour for a row, picked from its icon so the same icon always gets the same colour. */
fun moreTileColor(key: String): Color = TilePalette[Math.floorMod(key.hashCode(), TilePalette.size)]

@Composable
fun MoreIconTile(
    icon: ImageVector? = null,
    painter: Painter? = null,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        } else if (painter != null) {
            Icon(painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun MoreItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
    ) {
        MoreRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            iconPainter = iconPainter,
            tileColor = iconTint ?: moreTileColor(icon?.name ?: title),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
fun MoreSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tileColor: Color? = null,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent,
    ) {
        MoreRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            iconPainter = null,
            tileColor = tileColor ?: moreTileColor(icon.name),
        ) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun MoreRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    iconPainter: Painter?,
    tileColor: Color,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null || iconPainter != null) {
            MoreIconTile(icon = icon, painter = iconPainter, color = tileColor)
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}
