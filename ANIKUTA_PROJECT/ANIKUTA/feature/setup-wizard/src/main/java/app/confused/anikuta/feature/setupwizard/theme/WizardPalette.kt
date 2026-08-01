package app.confused.anikuta.feature.setupwizard.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class WizardPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    val surface5: Color,
)

val LimePalette = WizardPalette(
    primary = LimePrimary, onPrimary = LimeOnPrimary,
    primaryContainer = LimePrimaryContainer, onPrimaryContainer = LimeOnPrimaryContainer,
    background = LimeBg, surface1 = LimeSurface1, surface2 = LimeSurface2,
    surface3 = LimeSurface3, surface4 = LimeSurface4, surface5 = LimeSurface5,
)

val TealPalette = WizardPalette(
    primary = TealPrimary, onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer, onPrimaryContainer = TealOnPrimaryContainer,
    background = TealBg,
    surface1 = Color(0xFF0F2329), surface2 = Color(0xFF142D35),
    surface3 = Color(0xFF1A3740), surface4 = Color(0xFF1F414B), surface5 = Color(0xFF254B56),
)

val PurplePalette = WizardPalette(
    primary = PurplePrimary, onPrimary = PurpleOnPrimary,
    primaryContainer = PurplePrimaryContainer, onPrimaryContainer = PurpleOnPrimaryContainer,
    background = PurpleBg,
    surface1 = Color(0xFF1B1729), surface2 = Color(0xFF221E33),
    surface3 = Color(0xFF2A2540), surface4 = Color(0xFF332D4C), surface5 = Color(0xFF3D3656),
)

val CoralPalette = WizardPalette(
    primary = CoralPrimary, onPrimary = CoralOnPrimary,
    primaryContainer = CoralPrimaryContainer, onPrimaryContainer = CoralOnPrimaryContainer,
    background = CoralBg,
    surface1 = Color(0xFF291515), surface2 = Color(0xFF331C1C),
    surface3 = Color(0xFF3D2424), surface4 = Color(0xFF472C2C), surface5 = Color(0xFF523434),
)

val AllPalettes = listOf(LimePalette, TealPalette, PurplePalette, CoralPalette)
val PaletteNames = listOf("Lime", "Teal", "Purple", "Coral")

val PoisonPalette = WizardPalette(
    primary = PoisonPrimary, onPrimary = PoisonOnPrimary,
    primaryContainer = PoisonPrimaryContainer, onPrimaryContainer = PoisonOnPrimaryContainer,
    background = PoisonBg, surface1 = PoisonSurface1, surface2 = PoisonSurface2,
    surface3 = PoisonSurface3, surface4 = PoisonSurface4, surface5 = PoisonSurface5,
)

val LocalWizardPalette = staticCompositionLocalOf { LimePalette }

@Composable
fun SetupWizardTheme(
    palette: WizardPalette = LimePalette,
    isDark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.primary, onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer, onPrimaryContainer = palette.onPrimaryContainer,
            background = palette.background, surface = palette.surface1,
            surfaceVariant = palette.surface2, onBackground = TextLight, onSurface = TextLight,
        )
    } else {
        lightColorScheme(
            primary = palette.primary, onPrimary = palette.onPrimary,
            primaryContainer = Color(0xFFDFF5B0), onPrimaryContainer = palette.onPrimaryContainer,
            background = LightBg, surface = LightSurface1,
            surfaceVariant = LightSurface2, onBackground = TextDark, onSurface = TextDark,
        )
    }
    CompositionLocalProvider(LocalWizardPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = SetupWizardTypography, content = content)
    }
}
