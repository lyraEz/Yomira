package eu.kanade.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R

// YMR --> profile-style header card at the top of "More"
@Composable
fun LogoHeader() {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.28f),
                        colors.tertiary.copy(alpha = 0.18f),
                        colors.surfaceContainer,
                    ),
                ),
            )
            .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = R.drawable.ic_splash_logo,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Yomira",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
// YMR <--
