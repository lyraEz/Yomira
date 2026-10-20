package yomira.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top bar with an iOS-style large title: big and bold under the bar while the page is at the top,
 * shrinking into the regular bar as the content scrolls up. The surrounding Scaffold fades its glass
 * in at the same pace, so the bar is clear over the page and frosted once something slides under it.
 */
@Composable
fun LargeTitleBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    colors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior?,
    modifier: Modifier = Modifier,
) {
    val base = MaterialTheme.typography
    val typography = remember(base) {
        base.copy(
            headlineMedium = base.headlineMedium.copy(
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
            ),
        )
    }
    MaterialTheme(typography = typography) {
        LargeTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            expandedHeight = 116.dp,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}
