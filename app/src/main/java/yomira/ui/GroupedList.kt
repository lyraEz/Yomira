package yomira.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GroupRadius = 20.dp

/** Shape of a row inside a grouped card: the first rounds the top, the last the bottom. */
fun groupShape(first: Boolean, last: Boolean, radius: Dp = GroupRadius): Shape = RoundedCornerShape(
    topStart = if (first) radius else 0.dp,
    topEnd = if (first) radius else 0.dp,
    bottomStart = if (last) radius else 0.dp,
    bottomEnd = if (last) radius else 0.dp,
)

/**
 * One row of a grouped card (iOS grouped lists): consecutive rows join into one frosted panel
 * with rounded ends and a hairline between rows that starts at [dividerInset]. Each row is its own
 * lazy item, so long lists stay as fast as plain ones.
 */
@Composable
fun Modifier.groupedRow(first: Boolean, last: Boolean, dividerInset: Dp = 70.dp): Modifier {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    return this
        .padding(horizontal = 16.dp)
        .padding(bottom = if (last) 4.dp else 0.dp)
        .clip(groupShape(first, last))
        .background(GlassCard.color())
        .drawBehind {
            if (!first) {
                drawRect(
                    color = line,
                    topLeft = Offset(dividerInset.toPx(), 0f),
                    size = Size(size.width - dividerInset.toPx(), 0.5.dp.toPx()),
                )
            }
        }
}

/** Title over a group of rows, with an optional count and action on the right. */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * Capsule button. [filled] uses the accent (the main action of a row or card), otherwise a faint
 * tinted capsule.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        filled -> scheme.primary
        else -> scheme.primary.copy(alpha = 0.14f)
    }
    val content = if (filled) scheme.onPrimary else scheme.primary
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) container else scheme.onSurface.copy(alpha = 0.08f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) content else scheme.onSurface.copy(alpha = 0.38f)
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

/** Small coloured tag ("18+", "API"). */
@Composable
fun TagPill(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
