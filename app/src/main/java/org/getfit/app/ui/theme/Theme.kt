package org.getfit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.getfit.app.BuildConfig

/**
 * The two setts, one per channel.
 *
 * A dev build is a different colour from a production one for the same reason
 * it is a different package and a different launcher name: with both installed,
 * the only thing stopping a session being logged in the wrong copy is being
 * able to see at a glance which one is open. The literals here have to stay in
 * step with the `brand_accent` resValue in app/build.gradle.kts, which is what
 * colours the launcher icon.
 */
val ProductionAccent = Color(0xFFF4511E)
val DevAccent = Color(0xFF00897B)

/** Filled in on a set that has been done, hollow on one still to do. */
val CompletedGreen = Color(0xFF2E7D32)

/** Macro colours, used wherever protein/carbs/fat are broken out together. */
val ProteinColour = Color(0xFF1565C0)
val CarbsColour = Color(0xFFEF6C00)
val FatColour = Color(0xFF8E24AA)

private val accent: Color get() = if (BuildConfig.DEV_BUILD) DevAccent else ProductionAccent

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = Color(0xFF9E9E9E),
    background = Color(0xFF111312),
    surface = Color(0xFF1B1E1D),
    surfaceVariant = Color(0xFF2A2E2C),
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = Color(0xFF5A5F5C),
    background = Color(0xFFFAF9F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EAE7),
)

@Composable
fun GetFitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(accent) else lightScheme(accent),
        content = content,
    )
}
