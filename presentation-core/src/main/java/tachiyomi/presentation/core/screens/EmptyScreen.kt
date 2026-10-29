package tachiyomi.presentation.core.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.presentation.core.i18n.stringResource

data class EmptyScreenAction(
    val stringRes: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun EmptyScreen(
    stringRes: StringResource,
    modifier: Modifier = Modifier,
    actions: ImmutableList<EmptyScreenAction>? = null,
    help: @Composable (() -> Unit)? = null,
    title: String? = null,
) {
    EmptyScreen(
        message = stringResource(stringRes),
        modifier = modifier,
        actions = actions,
        help = help,
        title = title,
    )
}

/**
 * An empty page: a crescent moon (Yomira's night), an optional [title], the [message] and the
 * [actions] as capsules, the first one filled with the accent.
 */
@Composable
fun EmptyScreen(
    message: String,
    modifier: Modifier = Modifier,
    actions: ImmutableList<EmptyScreenAction>? = null,
    help: @Composable (() -> Unit)? = null,
    title: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NightMark()

        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = message,
            modifier = Modifier
                .padding(top = if (title != null) 8.dp else 20.dp)
                .widthIn(max = 340.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        help?.let { help() }

        if (!actions.isNullOrEmpty()) {
            Column(
                modifier = Modifier.padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                actions.fastForEachIndexed { index, action ->
                    EmptyScreenButton(action = action, filled = index == 0)
                }
            }
        }
    }
}

@Composable
private fun EmptyScreenButton(action: EmptyScreenAction, filled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val container = if (filled) scheme.primary else scheme.onSurface.copy(alpha = 0.08f)
    val content = if (filled) scheme.onPrimary else scheme.onSurface
    Row(
        modifier = Modifier
            .widthIn(min = 232.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(action.stringRes),
            color = content,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

/** A crescent moon in a soft accent circle, with the gold dot of the Yomira logo. */
@Composable
private fun NightMark() {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val r = size.minDimension / 2f
            drawCircle(color = accent, radius = r * 0.82f, center = Offset(r * 0.92f, r * 1.04f))
            drawCircle(
                color = Color.Black,
                radius = r * 0.7f,
                center = Offset(r * 1.34f, r * 0.72f),
                blendMode = BlendMode.Clear,
            )
            drawCircle(color = MoonGold, radius = r * 0.17f, center = Offset(r * 1.62f, r * 1.56f))
            drawCircle(color = accent.copy(alpha = 0.55f), radius = r * 0.07f, center = Offset(r * 1.78f, r * 0.42f))
        }
    }
}

private val MoonGold = Color(0xFFFFC857)
