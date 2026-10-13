package yomira.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One capsule of [CapsuleTabs]. */
class CapsuleTab(val title: String, val badge: Int? = null)

/**
 * Horizontally scrolling capsule tabs (iOS segmented style) that replace Material tab rows: the
 * selected capsule is tinted with the accent colour, the others are faint, and the selection
 * scrolls into view with a spring.
 */
@Composable
fun CapsuleTabs(
    tabs: List<CapsuleTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val offsets = remember(tabs.size) { IntArray(tabs.size) }
    val widths = remember(tabs.size) { IntArray(tabs.size) }
    LaunchedEffect(selectedIndex, tabs.size) {
        val i = selectedIndex.takeIf { it in tabs.indices } ?: return@LaunchedEffect
        val target = (offsets[i] - (scroll.viewportSize - widths[i]) / 2).coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target, spring(dampingRatio = 0.8f, stiffness = 400f))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val accent = MaterialTheme.colorScheme.primary
            val onSurface = MaterialTheme.colorScheme.onSurface
            val background by animateColorAsState(
                targetValue = if (selected) accent.copy(alpha = 0.22f) else onSurface.copy(alpha = 0.06f),
                animationSpec = spring(stiffness = 500f),
                label = "capsuleBg",
            )
            val content by animateColorAsState(
                targetValue = if (selected) accent else onSurface.copy(alpha = 0.82f),
                animationSpec = spring(stiffness = 500f),
                label = "capsuleText",
            )
            val border by animateColorAsState(
                targetValue = if (selected) accent.copy(alpha = 0.45f) else onSurface.copy(alpha = 0.08f),
                animationSpec = spring(stiffness = 500f),
                label = "capsuleBorder",
            )
            Row(
                modifier = Modifier
                    .onGloballyPositioned {
                        offsets[index] = it.parentLayoutCoordinates?.let { _ -> it.positionInParentX() } ?: 0
                        widths[index] = it.size.width
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(background)
                    .border(0.5.dp, border, RoundedCornerShape(percent = 50))
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = tab.title,
                    color = content,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val badge = tab.badge
                if (badge != null && badge > 0) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(content.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = if (badge > 99) "99+" else "$badge",
                            color = content,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.positionInParentX(): Int =
    (parentLayoutCoordinates?.localPositionOf(this, androidx.compose.ui.geometry.Offset.Zero)?.x ?: 0f).toInt()
