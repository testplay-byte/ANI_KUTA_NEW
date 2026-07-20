package com.testplaybyte.animeapp.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.testplaybyte.animeapp.R

// Bundled Roboto font family — guarantees all weight variants are available
// on ALL devices. Without bundling, some devices don't have ExtraBold (800)
// or Black (900) installed, so bold text renders as Regular.
val RobotoFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),     // 400
    Font(R.font.roboto_medium, FontWeight.Medium),       // 500
    Font(R.font.roboto_bold, FontWeight.Bold),           // 700
    Font(R.font.roboto_black, FontWeight.ExtraBold),     // 800
    Font(R.font.roboto_black, FontWeight.Black),         // 900
)

// M3 type scale — matches the web prototype.
// Uses the bundled Roboto family so ExtraBold/Black weights render correctly
// on all devices (system Roboto may not have these weights).
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.02).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.01).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.01).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
)
