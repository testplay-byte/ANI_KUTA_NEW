package app.confused.anikuta.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * ANIKUTA color palette — derived from the owner's chosen primary color #B1F256.
 *
 * See `DESIGN_LANGUAGE/03-themes/anikuta-palette.md` for the full spec.
 * Structure adapted from the prototype (`PROTOTYPE_REFERENCE/Anime_App/.../theme/Color.kt`).
 *
 * Dark theme is the default (per owner preference).
 *
 * **Light mode redesign (Session 1):** the previous light palette used
 * purple-tinted backgrounds (inherited from Material 3 defaults) that clashed
 * with warm/green accents. The new light palette uses warm-neutral backgrounds
 * with slightly darker card surfaces for clear hierarchy. The accent stays
 * rich/saturated in light mode (not muddy).
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

// ── Light theme — surface tonal tiers (warm-neutral, darker cards) ───────────
// Redesigned per Session 1: warm-neutral backgrounds (no purple tint) that
// harmonize with any accent. Card surfaces are slightly darker than the
// background (not lighter) for clear hierarchy per owner preference.
val BgLight = Color(0xFFFAF9F6) // warm off-white (no purple tint)
val Surface1Light = Color(0xFFF2F0EB) // slightly darker than bg — used for cards
val Surface2Light = Color(0xFFECEAE3) // darker still — elevated cards
val Surface3Light = Color(0xFFE3E0D7) // surfaceVariant — used for toggle backgrounds
val Surface4Light = Color(0xFFD8D5CB) // darker tier
val Surface5Light = Color(0xFFCCC9BE) // darkest tier

// ── Light theme — text tiers ─────────────────────────────────────────────────
val TextLight = Color(0xFF1C1B18) // near-black, warm
val TextMutedLight = Color(0xFF5C5A54) // muted warm grey
val TextSubtleLight = Color(0xFF8A8780) // subtle warm grey

// ── Light theme — M3 color roles ─────────────────────────────────────────────
// The primary in light mode is derived per-accent (see AccentColors.kt) —
// these are the fallback values for the Lime accent.
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

// ── AMOLED theme — subtle grey surfaces (not pure black) ─────────────────────
// Per Session 1 item 9.1: cards blended too much into pure black. These
// subtle grey tints make cards distinguishable without being obviously grey.
val BgAmoled = Color(0xFF000000) // pure black background (stays pure for OLED)
val Surface1Amoled = Color(0xFF121212) // subtle grey — cards are visible
val Surface2Amoled = Color(0xFF1A1A1A) // slightly lighter — elevated cards
val Surface3Amoled = Color(0xFF242424) // surfaceVariant — toggle backgrounds

// ── Functional colors ────────────────────────────────────────────────────────
val WarnDark = Color(0xFFFFCC80)
val SuccessDark = Color(0xFFA5D6A7)
