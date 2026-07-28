package app.confused.anikuta.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.R

/**
 * Bundled Roboto font family — guarantees all weight variants are available
 * on ALL devices.
 *
 * Without bundling, some devices don't have ExtraBold (800) or Black (900)
 * installed, so bold text renders as Regular. This is the fix for the
 * "text is not bold" issue the owner reported.
 *
 * Ported from the updated prototype (`PROTOTYPE_REFERENCE/Anime_App/.../theme/Type.kt`).
 */
val RobotoFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),     // 400
    Font(R.font.roboto_medium, FontWeight.Medium),       // 500
    Font(R.font.roboto_bold, FontWeight.Bold),           // 700
    Font(R.font.roboto_black, FontWeight.ExtraBold),     // 800
    Font(R.font.roboto_black, FontWeight.Black),         // 900
)

/**
 * ANIKUTA typography — M3 type scale with bundled Roboto + ExtraBold.
 *
 * Per the updated prototype: all bold text uses FontWeight.ExtraBold (800)
 * for visibility on Android. The RobotoFamily is bundled so ExtraBold
 * renders correctly on all devices.
 */
val AnikutaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.02).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.01).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.01).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)
