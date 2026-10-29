package yomira.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.DropdownMenu
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clearFocusOnSoftKeyboardHide
import tachiyomi.presentation.core.util.runOnEnterKeyPressed

private val CompactHeight = 60.dp
private val TitleBlockHeight = 50.dp
private val SubtitleHeight = 22.dp

/**
 * Header of the main tabs (Library, Browse): a big title with a short line under it and round
 * buttons on the right. While the page scrolls up it folds into one compact line, and the
 * Scaffold (with `largeTopBar`) frosts it as it does. With [search] set to a query it turns into a
 * search field instead.
 */
@Composable
fun TabHeader(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    search: TabHeaderSearch? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (search?.query != null) {
        // the search bar stays put while the results scroll
        if (scrollBehavior != null) {
            SideEffect {
                scrollBehavior.state.heightOffsetLimit = 0f
                scrollBehavior.state.heightOffset = 0f
            }
        }
        HeaderSearchBar(search = search, modifier = modifier)
        return
    }

    val density = LocalDensity.current
    val expanded = CompactHeight + TitleBlockHeight + (if (subtitle != null) SubtitleHeight else 0.dp)
    val limit = with(density) { (CompactHeight - expanded).toPx() }
    if (scrollBehavior != null) {
        SideEffect {
            if (scrollBehavior.state.heightOffsetLimit != limit) scrollBehavior.state.heightOffsetLimit = limit
        }
    }
    val fraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    val offset = with(density) { (scrollBehavior?.state?.heightOffset ?: 0f).toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height((expanded + offset).coerceAtLeast(CompactHeight))
            .clipToBounds(),
    ) {
        // Compact line: the small title fades in as the big one leaves
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CompactHeight)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = ((fraction - 0.55f) / 0.45f).coerceIn(0f, 1f) },
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        // Big title with its summary, sliding up under the compact line
        Column(
            modifier = Modifier
                .padding(top = CompactHeight, start = 20.dp, end = 20.dp)
                .graphicsLayer {
                    alpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)
                    translationY = -fraction * 12.dp.toPx()
                },
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = lerp(34.sp, 28.sp, fraction),
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Search state of a [TabHeader]: [query] null means the search is closed. */
class TabHeaderSearch(
    val query: String?,
    val onQueryChange: (String?) -> Unit,
    val placeholder: String? = null,
    val onSearch: (String) -> Unit = {},
)

@Composable
private fun HeaderSearchBar(search: TabHeaderSearch, modifier: Modifier = Modifier) {
    val query = search.query.orEmpty()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requestKeyboard = rememberSaveable { mutableStateOf(query.isEmpty()) }
    LaunchedEffect(focusRequester) {
        if (requestKeyboard.value) {
            focusRequester.requestFocus()
            requestKeyboard.value = false
        }
    }
    val submit: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        if (query.isNotBlank()) search.onSearch(query)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(CompactHeight)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // see SearchToolbar: on Android < P focus bounces back to the field unless it has somewhere to land
        Box(Modifier.size(1.dp).focusable())
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = search.placeholder ?: stringResource(MR.strings.action_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { search.onQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .runOnEnterKeyPressed(action = submit)
                        .clearFocusOnSoftKeyboardHide(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Cancel,
                    contentDescription = stringResource(MR.strings.action_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            search.onQueryChange("")
                            focusRequester.requestFocus()
                        }
                        .padding(8.dp),
                )
            }
        }
        Text(
            text = stringResource(MR.strings.action_cancel),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) {
                    focusManager.clearFocus()
                    search.onQueryChange(null)
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/**
 * Round button of a [TabHeader]. [active] tints it with the accent and adds a dot (filters on,
 * for example).
 */
@Composable
fun HeaderButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) scheme.primary.copy(alpha = 0.16f) else scheme.onSurface.copy(alpha = 0.07f))
                .border(0.5.dp, scheme.onSurface.copy(alpha = 0.08f), CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> scheme.onSurface.copy(alpha = 0.38f)
                    active -> scheme.primary
                    else -> scheme.onSurface
                },
                modifier = Modifier.size(21.dp),
            )
        }
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(scheme.background)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
    }
}

/** A round [HeaderButton] frame around any icon content. */
@Composable
fun HeaderButtonFrame(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(scheme.onSurface.copy(alpha = 0.07f))
            .border(0.5.dp, scheme.onSurface.copy(alpha = 0.08f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides scheme.onSurface,
        ) { content() }
    }
}

/**
 * The actions of a screen as [HeaderButton]s: icon actions become round buttons and the overflow
 * entries go into a menu behind a "more" button.
 */
@Composable
fun HeaderActions(actions: ImmutableList<AppBar.AppBarAction>) {
    actions.filterIsInstance<AppBar.Action>().forEach { action ->
        val custom = action.iconContent
        val icon = action.icon
        if (icon == null) {
            if (custom != null) HeaderButtonFrame(onClick = action.onClick, enabled = action.enabled) { custom() }
            return@forEach
        }
        HeaderButton(
            icon = icon,
            contentDescription = action.title,
            onClick = action.onClick,
            enabled = action.enabled,
            active = action.iconTint != null,
        )
    }
    val overflow = actions.filterIsInstance<AppBar.OverflowAction>()
    if (overflow.isNotEmpty()) {
        var open by remember { mutableStateOf(false) }
        Box {
            HeaderButton(
                icon = Icons.Outlined.MoreHoriz,
                contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                onClick = { open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                overflow.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.title) },
                        onClick = {
                            open = false
                            item.onClick()
                        },
                    )
                }
            }
        }
    }
}
