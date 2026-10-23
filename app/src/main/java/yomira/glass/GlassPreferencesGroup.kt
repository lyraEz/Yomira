package yomira.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import yomira.i18n.YMR
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** "Liquid glass" group in Settings → Appearance. */
@Composable
fun glassPreferencesGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val prefs = remember { GlassPreferences() }
    val recommended = remember { GlassDevice.recommendedQuality(context) }
    val qualityNames = mapOf(
        GlassQuality.OFF to stringResource(YMR.strings.glass_quality_off),
        GlassQuality.BATTERY to stringResource(YMR.strings.glass_quality_battery),
        GlassQuality.BALANCED to stringResource(YMR.strings.glass_quality_balanced),
        GlassQuality.ULTRA to stringResource(YMR.strings.glass_quality_ultra),
    )
    val blur by prefs.blurRadius().collectAsState()
    val height by prefs.refractionHeight().collectAsState()
    val amount by prefs.refractionAmount().collectAsState()
    val vibrance by prefs.vibrance().collectAsState()
    val brightness by prefs.brightness().collectAsState()
    val tint by prefs.tintAlpha().collectAsState()
    val highlight by prefs.highlight().collectAsState()
    val press by prefs.pressScale().collectAsState()

    return Preference.PreferenceGroup(
        title = stringResource(YMR.strings.glass_settings),
        preferenceItems = listOfNotNull(
            if (yomira.diagnostics.CrashGuard.isSafeMode(context)) {
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(YMR.strings.glass_safe_mode),
                    subtitle = stringResource(YMR.strings.glass_safe_mode_summary),
                    onClick = {
                        yomira.diagnostics.CrashGuard.setSafeMode(context, false)
                        (context as? android.app.Activity)?.recreate()
                    },
                )
            } else {
                null
            },
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(YMR.strings.glass_preview),
            ) { GlassPreview() },
            Preference.PreferenceItem.ListPreference(
                pref = prefs.mode(),
                title = stringResource(YMR.strings.glass_quality),
                entries = persistentMapOf(
                    GlassQualityMode.AUTO to stringResource(
                        YMR.strings.glass_quality_auto,
                        qualityNames.getValue(recommended),
                    ),
                    GlassQualityMode.OFF to qualityNames.getValue(GlassQuality.OFF),
                    GlassQualityMode.BATTERY to qualityNames.getValue(GlassQuality.BATTERY),
                    GlassQualityMode.BALANCED to qualityNames.getValue(GlassQuality.BALANCED),
                    GlassQualityMode.ULTRA to qualityNames.getValue(GlassQuality.ULTRA),
                ),
            ),
            slider(YMR.strings.glass_blur, blur.roundToInt(), 0, 32, "${blur.roundToInt()} dp") {
                prefs.blurRadius().set(it.toFloat())
            },
            slider(YMR.strings.glass_refraction_height, (height * 100).roundToInt(), 0, 45, "${(height * 100).roundToInt()}%") {
                prefs.refractionHeight().set(it / 100f)
            },
            slider(YMR.strings.glass_refraction_amount, (amount * 100).roundToInt(), 0, 100, "${(amount * 100).roundToInt()}%") {
                prefs.refractionAmount().set(it / 100f)
            },
            slider(YMR.strings.glass_vibrance, (vibrance * 100).roundToInt(), 100, 250, "${(vibrance * 100).roundToInt()}%") {
                prefs.vibrance().set(it / 100f)
            },
            slider(YMR.strings.glass_brightness, (brightness * 100).roundToInt(), -20, 30, "${(brightness * 100).roundToInt()}") {
                prefs.brightness().set(it / 100f)
            },
            slider(YMR.strings.glass_tint, (tint * 100).roundToInt(), 0, 80, "${(tint * 100).roundToInt()}%") {
                prefs.tintAlpha().set(it / 100f)
            },
            slider(YMR.strings.glass_highlight, (highlight * 100).roundToInt(), 0, 100, "${(highlight * 100).roundToInt()}%") {
                prefs.highlight().set(it / 100f)
            },
            slider(YMR.strings.glass_press, ((press - 1f) * 100).roundToInt(), 0, 15, "+${((press - 1f) * 100).roundToInt()}%") {
                prefs.pressScale().set(1f + it / 100f)
            },
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.depthEffect(),
                title = stringResource(YMR.strings.glass_depth),
                subtitle = stringResource(YMR.strings.glass_depth_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.chromaticAberration(),
                title = stringResource(YMR.strings.glass_chromatic),
                subtitle = stringResource(YMR.strings.glass_chromatic_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.shadow(),
                title = stringResource(YMR.strings.glass_shadow),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.haptics(),
                title = stringResource(YMR.strings.glass_haptics),
                subtitle = stringResource(YMR.strings.glass_haptics_summary),
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(YMR.strings.glass_reset),
                onClick = { prefs.resetLook() },
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(YMR.strings.glass_device_note)),
        ).toImmutableList(),
    )
}

@Composable
private fun slider(
    title: dev.icerock.moko.resources.StringResource,
    value: Int,
    min: Int,
    max: Int,
    label: String,
    onChange: (Int) -> Unit,
) = Preference.PreferenceItem.SliderPreference(
    value = value,
    min = min,
    max = max,
    title = stringResource(title),
    subtitle = label,
    onValueChanged = {
        onChange(it)
        true
    },
)

/** A moving colourful scene with glass controls on top, reflecting the current settings live. */
@Composable
private fun GlassPreview() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val backdrop = rememberGlassBackdrop(MaterialTheme.colorScheme.surface)
    val transition = rememberInfiniteTransition(label = "glassPreview")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(176.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(primary, tertiary, secondary)))
                    val r = size.minDimension * 0.42f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = r * 0.5f,
                        center = Offset(size.width * (0.5f + 0.35f * cos(phase)), size.height * (0.45f + 0.25f * sin(phase))),
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = r * 0.7f,
                        center = Offset(size.width * (0.5f - 0.3f * sin(phase)), size.height * (0.6f + 0.2f * cos(phase))),
                    )
                },
        ) {
            Text(
                text = "Yomira",
                color = Color.White,
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(56.dp)
                    .glass(RoundedCornerShape(percent = 50), backdrop = backdrop, interaction = rememberGlassInteraction())
                    .clickable {},
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(YMR.strings.glass_settings), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .glass(CircleShape, backdrop = backdrop, interaction = rememberGlassInteraction(), small = true)
                    .clickable {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Settings → Appearance → Experience: comfort options outside the player. */
@Composable
fun experiencePreferencesGroup(): Preference.PreferenceGroup {
    val prefs = remember { yomira.ui.ExperiencePreferences() }
    val watch = remember { yomira.player.WatchLogPreferences() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val goals = listOf(0, 15, 30, 45, 60, 90, 120, 180)
    val none = stringResource(YMR.strings.watch_goal_none)
    return Preference.PreferenceGroup(
        title = stringResource(YMR.strings.experience_title),
        preferenceItems = listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.hideSpoilers(),
                title = stringResource(YMR.strings.experience_spoilers),
                subtitle = stringResource(YMR.strings.experience_spoilers_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = remember { yomira.ui.ContinueWatchingPreferences() }.showOnHome(),
                title = stringResource(YMR.strings.experience_continue_card),
                subtitle = stringResource(YMR.strings.experience_continue_card_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.continueShortcut(),
                title = stringResource(YMR.strings.experience_shortcut),
                subtitle = stringResource(YMR.strings.experience_shortcut_summary),
                onValueChanged = {
                    if (!it) yomira.ui.ContinueShortcut.remove(context)
                    true
                },
            ),
            Preference.PreferenceItem.ListPreference(
                pref = watch.dailyGoal(),
                title = stringResource(YMR.strings.watch_goal_title),
                entries = goals.associateWith { minutes ->
                    if (minutes == 0) none else "$minutes min"
                }.toImmutableMap(),
            ),
        ).toImmutableList(),
    )
}
