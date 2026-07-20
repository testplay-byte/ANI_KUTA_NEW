package app.confused.anikuta.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * ANIKUTA color palette — derived from the owner's chosen primary color #B1F256.
 *
 * See `DESIGN_LANGUAGE/03-themes/anikuta-palette.md` for the full spec.
 * Structure adapted from the prototype (`PROTOTYPE_REFERENCE/Anime_App/.../theme/Color.kt`).
 *
 * Dark theme is the default (per owner preference).
 */

// ── Dark theme — surface tonal tiers (5 levels) ──────────────────────────────
val BgDark = Color(0xFF14111F)
val Surface1Dark = Color(0xFF1B1729)
val Surface2Dark = Color(0xFF221E33)
val Surface3Dark = Color(0xFF2A2540)
val Surface4Dark = Color(0xFF332D4C)
val Surface5Dark = Color(0xFF3D3656)

// ── Dark theme — text tiers ──────────────────────────────────────────────────
val TextDark = Color(0xFFECE6F5)
val TextMutedDark = Color(0xFFA89EC0)
val TextSubtleDark = Color(0xFF6E6688)

// ── Dark theme — M3 color roles ──────────────────────────────────────────────
val PrimaryDark = Color(0xFFB1F256)
val PrimaryFgDark = Color(0xFF1A2E00)
val OnPrimaryContainerDark = Color(0xFFD4F5A0)
val PrimaryContainerDark = Color(0xFF4A6B1A)
val SecondaryDark = Color(0xFFCCC2DC)
val SecondaryContainerDark = Color(0xFF4A4458)
val TertiaryDark = Color(0xFFEFB8C8)
val TertiaryContainerDark = Color(0xFF633B48)
val ErrorDark = Color(0xFFF2B8B5)
val ErrorContainerDark = Color(0xFF8C1D18)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF49454F)

// ── Light theme — surface tonal tiers ────────────────────────────────────────
val BgLight = Color(0xFFFEF7FF)
val Surface1Light = Color(0xFFF3EDF7)
val Surface2Light = Color(0xFFEDE7F4)
val Surface3Light = Color(0xFFE7E0EB)
val Surface4Light = Color(0xFFDDD6E4)
val Surface5Light = Color(0xFFD0C9DD)

// ── Light theme — text tiers ─────────────────────────────────────────────────
val TextLight = Color(0xFF1D1B20)
val TextMutedLight = Color(0xFF49454F)
val TextSubtleLight = Color(0xFF766C8E)

// ── Light theme — M3 color roles ─────────────────────────────────────────────
val PrimaryLight = Color(0xFF5A8C1A)
val PrimaryFgLight = Color(0xFFFFFFFF)
val OnPrimaryContainerLight = Color(0xFF1A2E00)
val PrimaryContainerLight = Color(0xFFD4F5A0)
val SecondaryLight = Color(0xFF625B71)
val SecondaryContainerLight = Color(0xFFE8DEF8)
val TertiaryLight = Color(0xFF7D5260)
val TertiaryContainerLight = Color(0xFFFFD8E4)
val OutlineLight = Color(0xFF79747E)
val OutlineVariantLight = Color(0xFFCAC4D0)

// ── AMOLED theme — overrides (pure black background + near-black ramp) ──────
val BgAmoled = Color(0xFF000000)
val Surface1Amoled = Color(0xFF0A0A0F)
val Surface2Amoled = Color(0xFF0C0C0C)
val Surface3Amoled = Color(0xFF131313)
val Surface4Amoled = Color(0xFF1B1B1B)
val Surface5Amoled = Color(0xFF232323)

// ── Functional colors ────────────────────────────────────────────────────────
val WarnDark = Color(0xFFFFCC80)
val SuccessDark = Color(0xFFA5D6A7)
