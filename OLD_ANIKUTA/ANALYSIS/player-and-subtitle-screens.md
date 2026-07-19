# Player + Subtitle Screens — Design-Language Analysis (OLD ANIKUTA)

> **Task ID:** D-1
> **Scope:** Player page, subtitle-tracks sheet, subtitle-settings sheet, quality sheet,
> and the custom numeric keypad. Other player screens (audio/server/speed/more sheets,
> fullscreen controls) are mentioned only where they contextualize the owner-flagged screens.
> **Source tree:** `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/player/`
> **ADR context:** ADR-015 (custom M3-inspired design language), ADR-012 (YouTube-style watch
> page with mini-player + episodes below + maximizable).

---

## 0. File map

| Screen | File (relative to `app/src/main/java/app/anikuta/player/`) | Lines |
|---|---|---|
| Player page (MINIMIZED host) | `PlayerScreen.kt` (`PlayerMode.MINIMIZED ->` branch, 246–606) | 1049 |
| Minimized-mode controls overlay | `controls/MinimizedControls.kt` | 524 |
| Fullscreen-mode controls overlay (context) | `controls/FullscreenControls.kt` | 358 |
| Quality sheet | `controls/sheets/PlayerSheets.kt` → `QualitySheet` (70–157) | 560 |
| Subtitle-tracks sheet | `controls/sheets/PlayerSheets.kt` → `SubtitleTracksSheet` (196–323) | — |
| Subtitle-settings sheet (host) | `controls/sheets/PlayerSheets.kt` → `SubtitleSettingsSheet` (521–560) | — |
| Subtitle-settings content panel | `controls/SubtitleSettingsPanel.kt` | 523 |
| Custom numeric keypad | `controls/NumericKeypad.kt` | 279 |
| Reusable sheet wrapper | `controls/sheets/PlayerSheet.kt` | 95 |
| Activity host (edge-to-edge setup) | `PlayerActivity.kt` | 2533 |

The watch page is a Compose screen (`PlayerScreen`) hosted by a hybrid Activity
(`PlayerActivity`). The Activity wraps an MPV `AndroidView`; the controls, sheets, and
keypad are 100% Compose. ADR-012 (watch page = mini-player + episodes + maximize) is
already implemented as `PlayerMode.MINIMIZED` vs `PlayerMode.FULLSCREEN` on the same
`PlayerScreen`. `PlayerActivity.kt:455` calls `enableEdgeToEdge()`; system bars are
hidden only in FULLSCREEN (`PlayerActivity.kt:459`).

---

## 1. Player page (MINIMIZED mode)

### 1.1 Layout structure (top to bottom)

```
┌────────────────────────────────────────────────────────────┐
│ Status bar (visible in MINIMIZED — Activity does NOT hide) │
├────────────────────────────────────────────────────────────┤
│ Floating pill top bar (conditional on showTopBar pref)     │ ← §1.3(a)
│   [← Back]    "AniKuta"    [⚙ Settings]                   │
│   (currently .statusBarsPadding() — OWNER WANTS REMOVED)   │
├────────────────────────────────────────────────────────────┤
│ Video area (16:9, 14.dp rounded, 6.dp h-padding, Black bg) │ ← §1.3(b)
│   ┌─ AndroidView(MPV) ──────────────────────────────┐      │
│   │  [00:00 / 24:00]                  [💬][HD]       │      │ ← MinimizedControls
│   │             ▶ (transparent play/pause)           │      │
│   │  ━━━━━━━━━━━━━━━━━━ ⊙                  [⛶]      │      │
│   └──────────────────────────────────────────────────┘      │
├────────────────────────────────────────────────────────────┤
│ 35dp vertical-gradient "fade-out zone" (NO blur yet —      │ ← §1.3(c)
│  owner wants gradient + BLUR; blur not implemented)        │
├────────────────────────────────────────────────────────────┤
│ LazyColumn (scrolls as one unit, YouTube-style):           │ ← §1.3(d)
│  • EPISODE X badge (Surface, primary, 8.dp corners)        │
│  • Title (titleLarge, Bold)                                │
│  • Release date (bodySmall, onSurfaceVariant)              │
│  • Description (bodyMedium, onSurfaceVariant, 3 ln)        │
│  • Server + audio-version dropdowns                        │
│  • HorizontalDivider                                       │
│  • "Episodes" header + inline episode rows                 │
└────────────────────────────────────────────────────────────┘
```

### 1.2 Key design details (owner-flagged)

#### (a) Floating pill top bar — currently `statusBarsPadding()`, owner wants edge-to-edge

`PlayerScreen.kt:252–309`:

```kotlin
if (showTopBar) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()                       // ← OWNER WANTS THIS REMOVED
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(..., horizontalArrangement = Arrangement.SpaceBetween) {
            // 36dp circular back button (secondaryContainer)
            // "AniKuta" title (titleMedium, Bold, primary)
            // 36dp circular settings button (secondaryContainer)
        }
    }
}
```

**Owner preference:** top navigation bar does NOT show a gap below the status bar — it
should be edge-to-edge with no `statusBarsPadding()`. The whole point of `enableEdgeToEdge()`
is to let the bar sit *under* the status bar; the current `.statusBarsPadding()` defeats
that. The same pattern is repeated at `PlayerScreen.kt:314–322` for the video area itself
(`if (!showTopBar) Modifier.statusBarsPadding() else Modifier.padding(top = 8.dp)`) — that
fallback should also be dropped. `FullscreenControls.kt:126` uses the same padding; in
fullscreen the system bars are hidden so it's a no-op, but should be dropped for consistency.

#### (b) Video area — rounded, themed, dynamic-color

`PlayerScreen.kt:311–456`. The video container:

```kotlin
Box(Modifier.fillMaxWidth()
        .padding(horizontal = 6.dp)
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(14.dp))
        .background(Color.Black)) {
    AndroidView(factory = { /* inflate R.layout.mpv_view */ }, modifier = Modifier.fillMaxSize())
    // ... MinimizedControls overlay ...
}
```

- 14.dp rounded corners, 6.dp horizontal padding (so rounded corners are visible against the themed bg)
- 8.dp top padding only when the top bar is shown
- `Color.Black` background behind MPV (letterbox-friendly)
- Dynamic theming: `PlayerScreen` is wrapped in `MaterialTheme(colorScheme = themedColorScheme)` (line 239) where `themedColorScheme` is generated from `coverColor` (AniList cover art): `generateDynamicScheme(Color(coverColor)).toM3ColorScheme()` — the same pattern as the detail page

#### (c) Darkening gradient + blur effect (gradient implemented; blur is design preference only)

`PlayerScreen.kt:591–603`:

```kotlin
androidx.compose.foundation.layout.Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(35.dp)
        .align(Alignment.TopCenter)
        .background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f   to MaterialTheme.colorScheme.background,
                0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                1f   to MaterialTheme.colorScheme.background.copy(alpha = 0f),
            ),
        ),
)
```

- 35.dp tall, aligned to the top of the LazyColumn wrapper (just below the video)
- Three-stop vertical gradient: opaque → 85% → transparent
- Uses the themed background color so it visually blends with whichever palette is active
- **BLUR IS NOT IMPLEMENTED.** The owner explicitly notes blur "may not be implemented yet but is a design preference" — the new design should layer a real blur effect (e.g. `Modifier.blur` on a snapshot of the video) on top of / instead of this gradient.

#### (d) Below the player — episode details block

`PlayerScreen.kt:482–535` (the `episode_details` LazyColumn item):

```kotlin
Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    // 1) Episode number badge — small pill in surfaceContainerHigh with primary text
    Surface(shape = RoundedCornerShape(8.dp), color = surfaceContainerHigh) {
        Text("EPISODE ${EpisodeTitleParser.formatEpisodeNumber(episode_number)}",
             style = labelMedium, fontWeight = Bold, color = primary,
             modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
    Spacer(Modifier.height(8.dp))
    // 2) Episode title — titleLarge, Bold, onSurface
    // 3) Release date (only if date_upload > 0) — bodySmall, onSurfaceVariant,
    //    formatted via SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    // 4) Description (only if summary not blank) — bodyMedium, onSurfaceVariant,
    //    maxLines = 3, overflow = Ellipsis
}
```

Order is exactly as the owner specified: **episode number → title → release date → description.**
The episode-number badge is a quiet visual anchor (small pill, `primary` text) that doesn't
compete with the title.

### 1.3 What the owner likes (KEEP)

- Floating pill-shaped top bar with back / title / settings — the bar shape and contents.
- Rounded video container (14.dp) sitting on the themed background.
- Dynamic theming from AniList cover color.
- LazyColumn scroll-as-one-unit (YouTube-style) — episode details + dropdowns + episode list scroll together.
- Episode details block ordering (episode # → title → date → description).
- 35dp fade-out gradient (will be upgraded to gradient + blur).
- Minimized-controls layout (timestamp TL, seekbar bottom, fullscreen right, sub+quality TR).

### 1.4 What the owner wants changed

| # | Current | Desired |
|---|---|---|
| 1 | `.statusBarsPadding()` on the floating top bar (`PlayerScreen.kt:257`) | Remove it — bar sits edge-to-edge under the status bar. |
| 2 | `.statusBarsPadding()` on the video area when `showTopBar == false` (`PlayerScreen.kt:318`) | Drop it — video goes edge-to-edge too. |
| 3 | 35dp gradient-only "fade-out zone" (`PlayerScreen.kt:591–603`) | Add a real **blur** effect on top of / behind the gradient. |

---

## 2. Minimized controls overlay

### 2.1 File path
`OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/player/controls/MinimizedControls.kt`

### 2.2 Layout structure (overlay on top of the video; only when `controlsVisible == true`)

```
┌─────────────────────────────────────────────────────────┐
│ 00:00 / 24:00                       [💬 Subtitles] [HD] │  ← top row
│                  ▶ / ⏸  (transparent, 72dp target)      │  ← center
│ ━━━━━━━━━━━━━━━━━━━━━ ⊙                       ⛶       │  ← bottom row
│   MinimalSeekbar (weight 1f)                   Fullscreen
└─────────────────────────────────────────────────────────┘
```

### 2.3 Key design details (owner-flagged)

#### (a) Timestamp at TOP-LEFT — `MinimizedControls.kt:267–275`

```kotlin
Text(
    text = "${formatTime(position)} / ${formatTime(duration)}",
    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
    modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp),
)
```

12sp / Medium / white. Format `m:ss` or `h:mm:ss`. Single string, slash-separated.

#### (b) Top-right corner — ONLY subtitle + quality — `MinimizedControls.kt:278–295`

```kotlin
Row(
    modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    TransparentIconButton(Icons.Default.Subtitles,   "Subtitles", onSubtitleClick)
    TransparentIconButton(Icons.Default.HighQuality, "Quality",   onQualityClick)
}
```

- Two icons only, in a Row, spaced 4.dp. Subtitle LEFT of quality.
- `TransparentIconButton` (lines 495–515): 36dp Box, no background, 22dp icon, white at 85% alpha — clean, minimal.
- Matches the owner's spec verbatim: **"Top-right corner: ONLY two options — subtitles and quality selection."** The minimized mode does NOT surface the audio / server / speed / more-options buttons that exist in fullscreen (`FullscreenControls.kt:160–164`).

#### (c) Seek bar at BOTTOM — `MinimizedControls.kt:318–342`

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    MinimalSeekbar(position, duration, bufferAheadTime, onSeekTo, modifier = Modifier.weight(1f))
    Box(Modifier.width(8.dp))
    TransparentIconButton(Icons.Default.Fullscreen, "Fullscreen", onMaximize)
}
```

- Bottom-aligned Row, 8dp horizontal padding, 6dp bottom. `MinimalSeekbar` takes `weight(1f)`, 8dp gap, **Fullscreen button on the RIGHT** ✓.

#### (d) MinimalSeekbar — custom 5dp track, 14dp thumb during drag — `MinimizedControls.kt:367–486`

- 28dp touch target, 5dp thin track (`RoundedCornerShape(3.dp)`)
- Three layers: inactive (white 30%) → buffer-ahead segment (white 50%) → active progress (primary)
- 14dp thumb (primary), only visible *while dragging*
- Floating time indicator above the thumb while dragging: 60dp wide pill (`Color.Black @ 0.7f`, 6dp corners, 11sp white text)
- Drag is `detectHorizontalDragGestures` — live `scrubPosition` updates, `onSeekTo` fires on `onDragEnd`

#### (e) Center play/pause + double-tap zones — `MinimizedControls.kt:105–156, 301–316`

- Single tap anywhere: toggle controls visibility
- Double-tap left/right thirds: ±10s (animation on tapped side, text-only pill "-10s" / "+10s")
- Double-tap center third: toggle play/pause (animation in center, icon in dark circle)
- When controls visible, a 72dp transparent Box in the center captures taps and toggles play/pause

#### (f) Auto-hide — `PlayerScreen.kt:180–201`

- Fullscreen: 4s; Minimized: 5s (user-requested longer); Lock button: 3s separately.

### 2.4 Owner-likes (KEEP) / Owner-changes

Nothing structural — the owner enumerated exactly the four zones the current code implements.
The only delta is the parent screen's `statusBarsPadding()` removal (§1.4).

---

## 3. Subtitle-tracks bottom-up menu (`SubtitleTracksSheet`)

### 3.1 File path
`controls/sheets/PlayerSheets.kt:196–323`. Wrapped by `PlayerSheet` at `controls/sheets/PlayerSheet.kt`.

### 3.2 Layout structure (top to bottom)

```
┌─────────────────────────────────────────────────────────┐
│ ⎯⎯⎯ ← default M3 drag handle (NOT removed in code)      │ ← OWNER WANTS DISABLED
│ Subtitles                          (titleMedium, Bold)  │
├─────────────────────────────────────────────────────────┤
│ [⚙]  Subtitle Settings                          >       │ ← navigates to SubtitleSettingsSheet
├─────────────────────────────────────────────────────────┤
│ ────── (HorizontalDivider, outlineVariant) ─────────── │
│ Subtitle track   (labelMedium, SemiBold, onSurfaceVar)  │
│  ┌──────┐ ┌──────────┐ ┌──────────┐  …                  │ ← FlowRow of chips
│  │ Off  │ │ English  │ │ Japanese │                     │
│  └──────┘ └──────────┘ └──────────┘                     │
│   (outline   (FilterChip,                               │
│    AssistChip  filled when                              │
│    when sel.)  selected)                                │
└─────────────────────────────────────────────────────────┘
```

### 3.3 Key design details

#### (a) Reusable `PlayerSheet` wrapper — `PlayerSheet.kt:24–53`

```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    // ⚠ No dragHandle = null — the default drag handle IS shown.
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = titleMedium, fontWeight = Bold, color = onSurface,
             modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}
```

Conventions established here (worth keeping for the design language):
- `skipPartiallyExpanded = true` (sheets open fully, no half-state)
- `RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)` top corners
- `containerColor = surfaceContainerLow` (tonal elevation, not main background)
- 20dp horizontal / 8dp vertical padding on the inner Column
- Title: `titleMedium` / Bold / `onSurface`, 12dp bottom padding
- Selection indicator: textual `✓` (not an icon) — see `SheetOption` at `PlayerSheet.kt:55–94`

#### (b) "Subtitle Settings" navigation row — `PlayerSheets.kt:225–256`

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().clickable { showSettings = true }
        .padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = CenterVertically) {
        Icon(Icons.Default.Settings, contentDescription = null,
             tint = primary, modifier = Modifier.size(20.dp))
        Text("Subtitle Settings", style = bodyLarge, fontWeight = Medium, color = onSurface)
    }
    Icon(Icons.Default.ChevronRight, contentDescription = null,
         tint = onSurfaceVariant, modifier = Modifier.size(20.dp))
}
HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = outlineVariant)
```

14dp vertical padding, 16dp horizontal. Settings icon (primary) + label + chevron. When tapped, the sheet swaps to `SubtitleSettingsSheet`; dismissing the settings sheet also dismisses the track sheet (lines 211–218).

#### (c) Chip-based track selection — `PlayerSheets.kt:262–319`

Comment calls this "modern streaming-app pattern (YouTube, Netflix, etc.)":

```kotlin
if (tracks.size <= 1) {
    // Only "Off" exists — bodySmall explainer instead of a chip
    Text("No subtitles found in this stream.\nThe extension may not provide external subtitles for this episode.",
         style = bodySmall, color = onSurfaceVariant, modifier = Modifier.padding(16.dp))
} else {
    Text("Subtitle track", style = labelMedium, fontWeight = SemiBold, color = onSurfaceVariant, ...)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tracks.forEach { track ->
            val isSelected = track.id == currentId
            if (track.id <= 0) {
                // "Off" — outline-style AssistChip (visually distinct secondary action)
                AssistChip(
                    onClick = { onSelect(track.id); onDismiss() },
                    label = { Text("Off", fontWeight = if (isSelected) Bold else Medium) },
                    leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, ...) } } else null,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp,
                                          if (isSelected) primary else outline),
                )
            } else {
                // Language track — filled FilterChip when selected, tonal when not
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(track.id); onDismiss() },
                    label = { Text(track.name, fontWeight = if (isSelected) Bold else Medium) },
                    leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, ...) } } else null,
                )
            }
        }
    }
}
```

- **Off** is always an `AssistChip` with an outline border (1dp normal / 2dp primary when selected) — visually secondary to the language chips
- **Language tracks** are `FilterChip`s — M3 fills them with the selected-state color when active, otherwise tonal
- Chips reflow on narrow screens via `FlowRow` (`ExperimentalLayoutApi`); 8dp h+v spacing
- Check icon is the leading icon on the selected chip only
- Empty state: a bodySmall explainer instead of a sad chip

### 3.4 Owner-likes (KEEP) / Owner-changes

**KEEP:** Overall sheet layout (title → settings row → divider → chip group). Visual distinction between the "Off" outline chip and the language `FilterChip`s. The "Subtitle Settings" navigation row at the top. The empty-state message.

**CHANGE:**
1. `ModalBottomSheet` default drag handle is shown (because `PlayerSheet` doesn't pass `dragHandle = null`) → **Disable the bottom drag section** — pass `dragHandle = null`.
2. "needs to be updated a bit" (owner's words — no specific items given) → treat the chip group + nav row as the baseline; specifics TBD when the new design language doc is written.

---

## 4. Subtitle-settings bottom-up menu (`SubtitleSettingsSheet` + `SubtitleSettingsPanel`)

### 4.1 File path
- Host sheet: `controls/sheets/PlayerSheets.kt:521–560`
- Content panel: `controls/SubtitleSettingsPanel.kt`

### 4.2 Why the owner calls this a "prime example of design preferences"

This sheet hits every preference the owner has been articulating:

1. **Partial height** — does NOT cover the whole screen. The video stays visible behind it.
2. **Sectioned layout** with `SectionHeader` + `SectionDivider` + `SectionSpacer`.
3. **Tappable value chips** next to sliders — opens a custom keypad (NOT the device keyboard).
4. **Custom stepper** (−/value/+) for delay instead of a slider.
5. **Color picker rows** with a swatch + hex preview.
6. **Live-apply** — every change (`onSettingsChanged()`) is pushed to MPV immediately, so the user sees the effect on the video *behind* the sheet while they edit.
7. **No clutter** — the code comment explicitly says "Removed the top explanatory note (clutter)."

### 4.3 Layout structure (top to bottom)

```
┌─────────────────────────────────────────────────────────┐
│ ⎯⎯⎯ ← default M3 drag handle (kept — owner OK with it   │
│        here; "prime example" means the overall layout)   │
│ Subtitle Settings              (titleMedium, Bold)       │
├─────────────────────────────────────────────────────────┤
│  ▼ scrolls internally if it overflows the 450dp max ▼   │
│  Typography                       (titleSmall, primary)  │
│   Font                                  ▾ Sans Serif      │ ← FontSelectorRow
│   ─────                                                   │
│   Font size        [55]    ━━━━━━●━━━━━━━━━━━━━━━━━━━    │ ← TappableSliderRow
│   ─────                                                   │
│   Scale            [1.0x]  ━━●━━━━━━━━━━━━━━━━━━━━━━━    │ ← TappableSliderRow
│   ─────                                                   │
│   Border size      [3]     ━●━━━━━━━━━━━━━━━━━━━━━━━    │ ← TappableSliderRow
│   ─────                                                   │
│   Bold                            [⬤─]                    │ ← CompactSwitchRow
│   Italic                          [─⬤]                    │ ← CompactSwitchRow
│  Colors                           (titleSmall, primary)  │
│   Text color           ■ #FFFFFFFF                        │ ← ColorPickerRow
│   Border color         ■ #000000FF                        │ ← ColorPickerRow
│   Background color     ■ #00000000                        │ ← ColorPickerRow
│  Position & Misc                  (titleSmall, primary)  │
│   Position           [80%]   ━━━━━━━━━━━━━━━━●━━━━━━━━   │ ← TappableSliderRow
│   Shadow offset      [2]     ━●━━━━━━━━━━━━━━━━━━━━━━   │ ← TappableSliderRow
│   Override ASS styling            [─⬤]                    │ ← CompactSwitchRow
│   Delay            (−) [120ms] (+)                        │ ← DelayStepperRow
└─────────────────────────────────────────────────────────┘
```

### 4.4 Key design details

#### (a) Partial height — `PlayerSheets.kt:521–560`

```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    // dragHandle NOT set to null — system drag handle is shown.
    // (A previous custom drag handle on the RIGHT was removed; comment at lines 510–518.)
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .heightIn(max = 450.dp)                       // ← PARTIAL HEIGHT
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Text("Subtitle Settings",
             style = titleMedium, fontWeight = Bold, color = onSurface,
             modifier = Modifier.padding(bottom = 10.dp))
        SubtitleSettingsPanel(onSettingsChanged = { onApplySettings(); Log.d(...) })
    }
}
```

- `heightIn(max = 450.dp)` — explicitly chosen so the key settings are visible without scrolling but the video remains visible behind the sheet (comment at lines 510–518: tuned down from 480dp, then up to 450dp at owner request)
- `verticalScroll(rememberScrollState())` — content scrolls internally if it overflows
- 20dp horizontal / 4dp vertical padding
- Same 20dp top corners + `surfaceContainerLow` shell as `PlayerSheet` — visual consistency across player sheets
- Title at very top-left, 10dp bottom padding — minimal top padding so it sits right under the system drag handle

#### (b) Section primitives — `SubtitleSettingsPanel.kt:262–284`

```kotlin
@Composable private fun SectionHeader(title: String) {
    Text(title, style = titleSmall, fontWeight = Bold, color = primary,    // ← sections in primary
         modifier = Modifier.padding(bottom = 8.dp, top = 4.dp))
}
@Composable private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp),
                      color = outlineVariant.copy(alpha = 0.5f))            // ← subtle
}
@Composable private fun SectionSpacer() { Spacer(modifier = Modifier.height(20.dp)) }
```

Section titles are `titleSmall` / Bold / **primary** color. Dividers are `outlineVariant` at 50% alpha — they section the content without being visually loud. 6dp vertical padding around dividers, 20dp `SectionSpacer` between major sections.

#### (c) Tappable slider row — `SubtitleSettingsPanel.kt:290–337`

```kotlin
Column(modifier = Modifier.padding(vertical = 4.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = SpaceBetween, verticalAlignment = CenterVertically) {
        Text(label, style = bodyMedium, color = onSurface)
        // Tappable value chip — opens NumericEntrySheet
        Surface(shape = RoundedCornerShape(6.dp),
                color = surfaceContainerHighest,
                modifier = Modifier.clickable(onClick = onTapValue)) {
            Text(valueText, style = bodySmall, fontWeight = SemiBold, color = primary,    // ← value in primary
                 modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
    Slider(value = value, onValueChange = onChange, valueRange = range,
           modifier = Modifier.fillMaxWidth(),
           colors = SliderDefaults.colors(
               thumbColor = primary, activeTrackColor = primary,
               inactiveTrackColor = surfaceContainerHighest))     // ← matches the chip background
}
```

Label on the left, value chip on the right. Value chip is `surfaceContainerHighest` with `primary` text, 6dp rounded — tappable to open the custom keypad. The chip + slider together give two ways to edit the same value: drag the slider OR tap the chip and type a precise number on the custom keypad.

#### (d) Color picker row — `SubtitleSettingsPanel.kt:420–460`

24dp swatch with 6dp rounded corners and a 1dp outline border, 8-character hex string (`#AARRGGBB`) next to the swatch. Whole row is tappable → opens `ColorPickerSheet` (presets + custom RGBA sliders — not analyzed in detail here).

#### (e) Delay stepper row — `SubtitleSettingsPanel.kt:466–522`

```kotlin
Row(verticalAlignment = CenterVertically, horizontalArrangement = spacedBy(6.dp)) {
    // − button (32dp circle, surfaceContainerHigh)
    Surface(shape = CircleShape, color = surfaceContainerHigh,
            modifier = Modifier.size(32.dp).clickable { onChange((delay - 100).coerceIn(-5000, 5000)) }) {
        Box(contentAlignment = Center) { Icon(Icons.Default.Remove, contentDescription = "−100ms", Modifier.size(18.dp)) }
    }
    // Value (tappable — opens keypad)
    Surface(shape = RoundedCornerShape(6.dp), color = surfaceContainerHighest,
            modifier = Modifier.clickable(onClick = onTapValue)) {
        Text("${delay}ms", style = bodySmall, fontWeight = SemiBold, color = primary,
             modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
    // + button (32dp circle, surfaceContainerHigh)
    Surface(shape = CircleShape, color = surfaceContainerHigh,
            modifier = Modifier.size(32.dp).clickable { onChange((delay + 100).coerceIn(-5000, 5000)) }) {
        Box(contentAlignment = Center) { Icon(Icons.Default.Add, contentDescription = "+100ms", Modifier.size(18.dp)) }
    }
}
```

Three-part layout: − / value / +. 32dp circular buttons with `Remove` / `Add` icons. 100ms step, clamped to ±5000ms. Value chip is tappable → opens keypad for precise input. **This is the model the owner wants for any "discrete-step + precise-input" setting** — a slider would be wrong here (delay can be negative, and the user often wants exact values like −250ms).

#### (f) Font selector row — `SubtitleSettingsPanel.kt:358–415`

Full-width `Surface` with `RoundedCornerShape(8.dp)` and `surfaceContainerHigh` color; label on the left, `ArrowDropDown` icon on the right. Tap opens a `DropdownMenu` with options `["Sans Serif", "Serif", "Monospace", "Roboto"]`. The selected option is rendered in `FontWeight.Bold`.

#### (g) Compact switch row — `SubtitleSettingsPanel.kt:340–353`

```kotlin
Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
    horizontalArrangement = SpaceBetween, verticalAlignment = CenterVertically) {
    Text(label, style = bodyMedium, color = onSurface)
    Switch(checked = checked, onCheckedChange = onChange)
}
```

Used for `Bold`, `Italic`, `Override ASS styling`.

#### (h) Live-apply wiring — `SubtitleSettingsPanel.kt:82–83, 194–255`

```kotlin
editingDialog?.let { dialogKey ->
    val (title, initial, suffix, min, max) = when (dialogKey) {
        "fontSize"   -> Tuple5("Font size",   fontSize, "",  20, 100)
        "fontScale"  -> Tuple5("Scale (×10)", (fontScale * 10).toInt(), "", 5, 30)
        "borderSize" -> Tuple5("Border size", borderSize, "", 0, 10)
        "position"   -> Tuple5("Position",    position, "%", 0, 100)
        "shadow"     -> Tuple5("Shadow offset", shadowOffset, "", 0, 10)
        "delay"      -> Tuple5("Delay",       delay, "ms", -5000, 5000)
        else -> return@let
    }
    NumericEntrySheet(
        title = title, initial = initial, suffix = suffix, min = min, max = max,
        onLiveChange = { v -> /* write pref + onSettingsChanged() — applies to MPV live */ },
        onConfirm    = { v -> /* write pref + onSettingsChanged() + editingDialog = null */ },
        onDismiss    = { editingDialog = null },
    )
}
```

`onLiveChange` fires on every keystroke in the keypad — the slider value AND the MPV subtitle property both update as the user types. The video behind the sheet shows the result in real time.

### 4.5 Owner-likes (KEEP — this is the design-language exemplar)

- Partial height (`heightIn(max = 450.dp)`) + internal scroll.
- `SectionHeader` / `SectionDivider` / `SectionSpacer` primitives.
- `TappableSliderRow` (slider + tappable value chip → custom keypad).
- `ColorPickerRow` (swatch + hex → `ColorPickerSheet`).
- `DelayStepperRow` (−/value/+, 100ms step, tappable value).
- `FontSelectorRow` (full-width styled dropdown).
- `CompactSwitchRow` (label + M3 `Switch`).
- Live-apply to MPV (every keystroke + every slider move + every switch toggle pushes to MPV immediately).
- Title at very top-left, minimal top padding.
- Same 20dp top corners + `surfaceContainerLow` as `PlayerSheet` — visual consistency.

### 4.6 Owner-changes

Nothing structural — this is the "prime example." The new design language doc should
port these section primitives verbatim and reuse them for any settings sheet.

---

## 5. Custom numeric keypad (`NumericEntrySheet` / `CustomKeypadSheet`)

### 5.1 File path
`controls/NumericKeypad.kt`. Triggered from `SubtitleSettingsPanel` when the user taps any
tappable value chip (see §4.4(c) and §4.4(h)). It is a `ModalBottomSheet` — NOT a center
popup — so the video player stays visible behind it and the user can see subtitle changes
in real time as they type.

### 5.2 Layout structure (top to bottom)

```
┌─────────────────────────────────────────────────────────┐
│ ⎯⎯⎯ ← default M3 drag handle (kept)                     │
│ Font size                            (labelMedium, SemiBold, onSurfaceVariant) │
│ ┌────┐ ┌─────────────────────┐ ┌────┐                   │
│ │ −  │ │        55           │ │ +  │                   │ ← Stepper row (48dp circles + value Surface)
│ │    │ │  (headlineMedium,   │ │    │                   │
│ │    │ │   Bold, primary,    │ │    │                   │
│ │    │ │   centered)         │ │    │                   │
│ └────┘ └─────────────────────┘ └────┘                   │
│ ┌──┬──┬──┬──────────┐                                    │
│ │ 1│ 2│ 3│          │                                    │
│ ├──┼──┼──┤  DEL     │ ← 112dp tall, spans 2 rows         │
│ │ 4│ 5│ 6│ (backspace│                                   │
│ ├──┼──┼──┤  icon)   │                                    │
│ │ 7│ 8│ 9├──────────┤                                    │
│ ├──┴──┴──┤          │                                    │
│ │         │   OK     │ ← 112dp tall, primary color       │
│ │    0    │ (check   │   (spans 2 rows)                  │
│ │         │  icon)   │                                   │
│ └─────────┴──────────┘                                   │
└─────────────────────────────────────────────────────────┘
```

### 5.3 Key design details (owner-flagged)

#### (a) Sheet chrome — `NumericKeypad.kt:106–111`

Same shell as the other sheets — 20dp top corners, `surfaceContainerLow` background. Drag handle kept (not set to null).

#### (b) Title + stepper row — `NumericKeypad.kt:117–155`

```kotlin
Text(title,
     style = MaterialTheme.typography.labelMedium,
     fontWeight = FontWeight.SemiBold,
     color = MaterialTheme.colorScheme.onSurfaceVariant,    // ← subtle title
     modifier = Modifier.padding(bottom = 4.dp))
Row(Modifier.fillMaxWidth().padding(bottom = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    StepperButton(isPlus = false) {
        val v = (input.toIntOrNull() ?: initial) - 1
        input = v.coerceIn(min, max).toString()
    }
    Surface(shape = RoundedCornerShape(12.dp),
            color = surfaceContainerHighest,
            modifier = Modifier.weight(1f)) {
        Text(text = if (input.isEmpty()) "—" else "$input$suffix",
             style = MaterialTheme.typography.headlineMedium,    // ← big number
             fontWeight = FontWeight.Bold,
             color = MaterialTheme.colorScheme.primary,
             textAlign = TextAlign.Center,
             modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
    StepperButton(isPlus = true) {
        val v = (input.toIntOrNull() ?: initial) + 1
        input = v.coerceIn(min, max).toString()
    }
}
```

- Title is `labelMedium` / SemiBold / `onSurfaceVariant` — quiet, just labels the field
- Value display is BIG: `headlineMedium` / Bold / `primary` / centered, with unit suffix appended (e.g. "55", "120ms", "80%")
- Empty input shows an em-dash placeholder "—"
- Two 48dp `StepperButton`s (defined at lines 259–278): `RoundedCornerShape(12.dp)`, `surfaceContainerHigh` bg, `tonalElevation = 1.dp`, `Add`/`Remove` icons at 24dp. Each tap increments/decrements by 1, clamped to `[min, max]`

#### (c) Keypad grid — `NumericKeypad.kt:157–214`

This is the layout the owner explicitly asked to be documented. It's a 4-column grid with
the numbers on the left and the action buttons (DEL/OK) on the right:

```kotlin
// 4 columns × 4 rows. Left 3 columns = numbers. Right column =
// DEL (spans 2 rows) + OK (spans 2 rows). Bottom row = 0 (spans 3).
//   [1][2][3][DEL]
//   [4][5][6][   ]   ← DEL spans 2 rows
//   [7][8][9][OK ]
//   [0  0  0][   ]   ← 0 spans 3 cols, OK spans 2 rows
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    // Left 3 columns: numbers 1-9 + 0 at bottom
    Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(...) { KeypadButton("1", weight(1f)) {...}; KeypadButton("2", ...); KeypadButton("3", ...) }
        Row(...) { KeypadButton("4", ...); KeypadButton("5", ...); KeypadButton("6", ...) }
        Row(...) { KeypadButton("7", ...); KeypadButton("8", ...); KeypadButton("9", ...) }
        Row(...) { KeypadButton("0", weight(1f)) {...} }   // single wide 0 button
    }
    // Right column: DEL (top, 2 rows tall) + OK (bottom, 2 rows tall).
    // Fixed height (not weight) so the sheet doesn't expand to fill the screen —
    // each action button = 2 number rows + spacing = 52 + 8 + 52 = 112dp.
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeypadButton(key = "DEL",
                     modifier = Modifier.fillMaxWidth().height(112.dp),
                     onClick = { if (input.isNotEmpty()) input = input.dropLast(1) })
        KeypadButton(key = "OK",
                     modifier = Modifier.fillMaxWidth().height(112.dp),
                     onClick = {
                         val v = input.toIntOrNull() ?: initial
                         onConfirm(v.coerceIn(min, max))
                     })
    }
}
```

The actual on-screen layout (the code comment has a small typo — `0` is a single wide button, not 3 cells):

```
┌──────┬──────┬──────┬─────────┐
│  1   │  2   │  3   │         │
├──────┼──────┼──────┤  DEL    │ ← 112dp tall (spans 2 number rows)
│  4   │  5   │  6   │         │   backspace icon, surfaceContainerHigh
├──────┼──────┼──────┤         │
│  7   │  8   │  9   ├─────────┤
├──────┴──────┴──────┤         │
│         0          │   OK    │ ← 112dp tall, primary color, onPrimary icon
└────────────────────┴─────────┘   (spans 2 number rows)
```

- **Numbers**: 1–9 in a 3×3 grid, with 0 as a single wide button in the bottom row of the left column
- **DEL**: right column, top half, 112dp tall — backspace icon, `surfaceContainerHigh` background
- **OK**: right column, bottom half, 112dp tall — check icon, **primary** background, **onPrimary** icon tint
- 8dp spacing between every cell
- All buttons: `RoundedCornerShape(14.dp)`, min height 52dp (`heightIn(min = 52.dp)`), `shadowElevation = 1.dp` for numbers and `2.dp` for action buttons (subtle depth on the action keys)
- Number text style: `headlineSmall` / `SemiBold`. Number buttons use `surfaceContainerHigh` with `onSurface` text (tonal, not flat)

#### (d) Input behavior — `NumericKeypad.kt:98–104, 174–189, 200–213`

- Input is a `String` (so the user can have an empty field, leading zeros, etc.)
- Each number button: `if (input == "0") input = "<n>"` (replace leading zero) `else if (input.length < 8) input += "<n>"` (max 8 digits, append)
- `DEL` does `input = input.dropLast(1)`
- `OK` parses, clamps to `[min, max]`, fires `onConfirm`
- `LaunchedEffect(input) { onLiveChange(liveValue) }` pushes every keystroke to the caller immediately — the slider value AND the MPV subtitle property both update as the user types
- `liveValue = input.toIntOrNull() ?: initial` — empty input falls back to `initial` (no crash)

#### (e) Why a bottom sheet, not a center popup

From the file's KDoc (lines 39–46): the keypad is a bottom sheet (not a center popup) so the video player stays visible behind it and the user sees subtitle changes in real time as they type. *(Note: the KDoc also says "the keypad does NOT show the value in its own display" — that's slightly out of date; the live preview is now additive: the keypad shows the value AND the underlying setting row + video update live.)*

### 5.4 Owner-likes (KEEP) / Owner-changes

**KEEP:**
- Bottom-sheet form (NOT a center popup) — video stays visible behind.
- Title at top, then a stepper row (−/value/+) so the user can nudge by 1 without typing.
- 4-column grid layout: 3 columns of numbers on the left, 1 column for DEL (top, 112dp) + OK (bottom, 112dp, primary color).
- OK button uses the primary color (visual emphasis on the confirm action).
- DEL uses the backspace icon (not text "DEL").
- Live-apply via `onLiveChange` on every keystroke.
- Clamping to `[min, max]` on confirm.
- Same 20dp top corners + `surfaceContainerLow` shell as the other player sheets — visual consistency.

**CHANGE:** Nothing structural was flagged. The new design language doc should port this as the canonical numeric-input component for any setting that needs precise entry.

---

## 6. Quality selection bottom-up menu (`QualitySheet`)

### 6.1 File path
`controls/sheets/PlayerSheets.kt:70–157`. Uses the same `PlayerSheet` wrapper as `SubtitleTracksSheet` (see §3.3(a)).

### 6.2 Layout structure (two display modes)

The sheet has TWO modes (controlled by `qualitySheetDisplayMode` preference):

**"current" mode** (default — only qualities for the current server + audio version):

```
┌─────────────────────────────────────────────────────────┐
│ ⎯⎯⎯ ← default M3 drag handle                            │ ← OWNER WANTS DISABLED
│ Quality                             (titleMedium, Bold)  │
├─────────────────────────────────────────────────────────┤
│  1080p                                                  │
│  Default • Sub                  ✓                       │ ← SheetOption (selected)
│  720p                                                   │
│  Default • Sub                                          │ ← SheetOption
│  480p                                                   │
│  Default • Sub                                          │ ← SheetOption
└─────────────────────────────────────────────────────────┘
```

**"all" mode** (every quality from every server + audio version, grouped):

```
┌─────────────────────────────────────────────────────────┐
│ Quality                             (titleMedium, Bold)  │
├─────────────────────────────────────────────────────────┤
│ Default                          (titleSmall, primary)   │ ← server section header
│  SUB                              (labelMedium, SemiBold)│ ← audio subheader
│   1080p   Default • Sub          ✓                      │
│   720p    Default • Sub                                 │
│  DUB                                                    │
│   1080p   Default • Dub                                │
│ Vidstream                                               │ ← server section header
│  SUB                                                    │
│   ...                                                   │
└─────────────────────────────────────────────────────────┘
```

### 6.3 Key design details

#### (a) Two display modes — `PlayerSheets.kt:70–157`

```kotlin
@Composable
fun QualitySheet(
    videos: List<Video>,
    currentVideoUrl: String, currentVideoTitle: String = "",
    currentVideoServer: String = "", currentAudioVersion: String = "",
    displayMode: String = "current",                        // ← "current" | "all"
    onSelect: (Video) -> Unit, onDismiss: () -> Unit,
) {
    val allParsed = remember(videos) { videos.map { VideoTitleParser.parse(it) } }
    PlayerSheet(title = "Quality", onDismiss = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (displayMode == "all") {
                // Group by server → audio version → quality (descending)
                val byServer = allParsed.groupBy { it.server }
                byServer.entries.sortedBy { it.key }.forEach { (serverName, serverVideos) ->
                    item("server_header_$serverName") {
                        Text(serverName, style = titleSmall, fontWeight = Bold, color = primary,
                             modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                    }
                    val byAudio = serverVideos.groupBy { it.audio }
                    byAudio.entries.sortedBy { it.key.ordinal }.forEach { (audio, audioVideos) ->
                        item("audio_header_${serverName}_${audio.name}") {
                            Text(audio.label, style = labelMedium, fontWeight = SemiBold,
                                 color = onSurfaceVariant,
                                 modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                        audioVideos.sortedByDescending { it.quality ?: 0 }.forEachIndexed { index, parsed ->
                            item("quality_${serverName}_${audio.name}_$index") {
                                QualityOption(parsed, currentVideoTitle,
                                              onSelect = { onSelect(parsed.video); onDismiss() })
                            }
                        }
                    }
                }
            } else {
                // Filter to current server + audio version (fallback to all if no matches)
                val filtered = allParsed.filter {
                    (currentVideoServer.isBlank() || it.server == currentVideoServer) &&
                    (currentAudioVersion.isBlank() || it.audio.name == currentAudioVersion)
                }
                val list = if (filtered.isEmpty()) allParsed else filtered
                itemsIndexed(list, key = { i, _ -> "quality_$i" }) { _, parsed ->
                    QualityOption(parsed, currentVideoTitle,
                                  onSelect = { onSelect(parsed.video); onDismiss() })
                }
            }
        }
    }
}
```

#### (b) QualityOption row — `PlayerSheets.kt:159–181`

```kotlin
@Composable
private fun QualityOption(parsed: ParsedVideo, currentVideoTitle: String, onSelect: () -> Unit) {
    val qualityLabel = parsed.quality?.let { "${it}p" } ?: "Unknown"
    val subtitle = buildString { append(parsed.server); append(" • "); append(parsed.audio.label) }
    // Highlight by videoTitle (stable across re-resolutions) instead of videoUrl
    // (localhost:PORT changes between resolutions).
    val isSelected = currentVideoTitle.isNotBlank() && parsed.video.videoTitle == currentVideoTitle
    SheetOption(title = qualityLabel, subtitle = subtitle, selected = isSelected, onClick = onSelect)
}
```

Uses the shared `SheetOption` row (`PlayerSheet.kt:55–94`):

```kotlin
@Composable
fun SheetOption(title: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = CenterVertically, horizontalArrangement = SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = bodyMedium,
                 fontWeight = if (selected) Bold else Medium,
                 color = if (selected) primary else onSurface)
            if (subtitle != null) Text(subtitle, style = bodySmall, color = onSurfaceVariant)
        }
        if (selected) Text("✓", style = bodyLarge, fontWeight = Bold, color = primary)  // textual check
    }
}
```

- Two-line row: title (the resolution, e.g. "1080p") + subtitle (server • audio version)
- Selected row's title becomes `primary` + Bold; unselected is `onSurface` + Medium
- Selection indicator is a textual `✓` (not an icon) — matches the subtitle-tracks sheet's checkmark style
- 6dp vertical padding per row
- Selection match is by `videoTitle` (stable across re-resolutions), NOT `videoUrl` (the localhost proxy URL changes between resolutions — see comment at line 171)

### 6.4 Owner-likes (KEEP) / Owner-changes

**KEEP:**
- Two display modes (`"current"` and `"all"`) — lets power users see every quality option while keeping the default view simple.
- Grouping in `"all"` mode: server → audio version → quality (descending).
- Two-line `SheetOption` rows (resolution + server • audio subtitle).
- Textual `✓` selection indicator.
- Selection match by `videoTitle` (not `videoUrl`).

**CHANGE:** `PlayerSheet` (used by `QualitySheet`) does NOT pass `dragHandle = null` — the default M3 drag handle is shown. **Set `dragHandle = null`** on `ModalBottomSheet` so the quality sheet has NO top pull-down bar. This is an explicit owner design preference.

> **Implementation note for the new project:** Because `PlayerSheet` is shared by `QualitySheet`, `SubtitleTracksSheet`, `AudioTracksSheet`, `ServerSheet`, `SpeedSheet`, and `MoreOptionsSheet`, removing the drag handle is a per-call decision, not a global one. The new design should add a `showDragHandle: Boolean = true` parameter to `PlayerSheet` and pass `false` from `QualitySheet` and `SubtitleTracksSheet`. The subtitle-settings sheet keeps its drag handle (the owner calls it a "prime example" and didn't flag the handle).

---

## 7. Cross-cutting design-language takeaways (for the design-language docs)

These are the patterns the new design language doc should extract from these screens.

### 7.1 Sheet chrome

- `ModalBottomSheet` with `skipPartiallyExpanded = true`
- `RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)` top corners
- `containerColor = MaterialTheme.colorScheme.surfaceContainerLow`
- 20dp horizontal / 8dp vertical padding on the inner `Column`
- Title: `titleMedium` / `Bold` / `onSurface`, 12dp bottom padding before content
- Drag handle: configurable per sheet (default ON; OFF for quality + subtitle-tracks)

### 7.2 Section primitives (from `SubtitleSettingsPanel`)

- `SectionHeader(title)` — `titleSmall` / `Bold` / `primary`
- `SectionDivider` — `HorizontalDivider`, `outlineVariant` @ 50% alpha, 6dp vertical padding
- `SectionSpacer` — 20dp `Spacer`

### 7.3 Setting row primitives

| Primitive | Use case | Layout |
|---|---|---|
| `TappableSliderRow` | Numeric range with precise-input need | label + value chip (right) on row 1, slider on row 2; chip opens `NumericEntrySheet` |
| `CompactSwitchRow` | Boolean toggle | label + M3 `Switch` |
| `ColorPickerRow` | Color value | label + (swatch + hex); row tap opens `ColorPickerSheet` |
| `DelayStepperRow` | Discrete-step + precise-input | label + (− / value chip / +); 100ms step; chip opens `NumericEntrySheet` |
| `FontSelectorRow` | Enum dropdown | label, then full-width `Surface` with value + `ArrowDropDown`; tap opens `DropdownMenu` |

### 7.4 Selection indicator

- Textual `✓` (not an icon) — used in `SheetOption` and as the leading icon on selected chips.
- Selected row's title becomes `primary` + `Bold`.

### 7.5 Color palette usage

- `surfaceContainerLow` — sheet background
- `surfaceContainerHigh` — tonal surfaces (stepper buttons, font selector, transparent icon backgrounds in fullscreen)
- `surfaceContainerHighest` — value chips, keypad value display
- `primary` — section headers, selected text, value chip text, OK button background, active slider track + thumb
- `onSurface` — body text, unselected titles
- `onSurfaceVariant` — subtitles, section labels, hex codes
- `outlineVariant` — subtle dividers (at 50% alpha)
- `outline` — unselected chip borders

### 7.6 Typography usage

| Token | Used for |
|---|---|
| `headlineMedium` | Keypad value display (the big number) |
| `headlineSmall` | Keypad button text (1–9, 0) |
| `titleLarge` | Episode title on the player page |
| `titleMedium` | Sheet titles, "AniKuta" app name in top bar |
| `titleSmall` | Section headers within sheets |
| `bodyLarge` | "Subtitle Settings" nav row label |
| `bodyMedium` | Setting row labels, sheet option titles, episode description |
| `bodySmall` | Setting subtitles, hex codes, episode release date, empty-state messages |
| `labelMedium` | Episode number badge, keypad title, audio-version subheaders, "Subtitle track" label |

### 7.7 Shape language

- 20dp — sheet top corners
- 14dp — video container corners, keypad button corners
- 12dp — keypad value display, stepper buttons
- 8dp — episode number badge, font selector surface, FSSmallButton (fullscreen)
- 6dp — value chips, color swatches
- CircleShape — DelayStepper ± buttons (32dp), FSCenterButton (44dp)
- 3dp — MinimalSeekbar track

### 7.8 Edge-to-edge policy

- `PlayerActivity.kt:455` calls `enableEdgeToEdge()` — the app draws under system bars.
- MINIMIZED mode keeps the status bar visible (`PlayerActivity.kt:459` only hides system bars in FULLSCREEN).
- **The owner wants the top bar to sit UNDER the status bar (no `statusBarsPadding()`)** — the current `statusBarsPadding()` on the floating pill top bar defeats this and should be removed in the new design.
- FULLSCREEN mode hides system bars entirely (immersive sticky — `PlayerActivity.kt:2202–2213`).

### 7.9 Live-apply principle

Every setting that can be edited while watching (subtitle font size, scale, border, position,
shadow, delay, colors, override ASS) is **applied to MPV live** as the user edits — not
deferred to a "Save" button. The keypad fires `onLiveChange` on every keystroke; sliders
fire on every change; switches fire on every toggle; color pickers fire on every drag.
The video behind the sheet shows the result immediately. This is a hard design principle
for any in-playback settings UI in the new project.

---

## 8. Open questions for the owner (next-session inputs)

1. **"Bottom drag section needs to be disabled" (subtitle-tracks sheet)** — confirmed as the default M3 `ModalBottomSheet` drag handle (the little pill at the top of the sheet). Plan: pass `dragHandle = null` from `PlayerSheet` when called by `SubtitleTracksSheet`. Confirm with owner.
2. **Quality sheet drag handle** — owner explicitly said "it's set to false" as a design preference. The current code does NOT actually set `dragHandle = null` (the default handle is shown). The new project should set it to null. Confirm.
3. **Blur effect under the player** — the 35dp gradient fade-out zone is implemented; the owner wants a real blur added. Decide on the implementation approach (`Modifier.blur` on a `GraphicsLayer`-snapshot of the video vs. a separate blurred Bitmap vs. RenderEffect). Note `Modifier.blur` requires API 31+; older paths need a fallback.
4. **Top bar status-bar-padding removal** — confirm the owner wants the floating pill top bar to literally overlap the status bar (icons may clash with system status icons). Alternative: hide the status bar in MINIMIZED mode too (currently it's only hidden in FULLSCREEN). The owner's wording ("edge-to-edge, no status bar padding") suggests the overlap approach, but this needs a visual confirmation.
5. **Custom keypad `useCustomKeypad` preference** — the `SubtitleSettingsPanel` KDoc mentions a `PlayerPreferences.useCustomKeypad` toggle (line 56) but the implementation always uses `CustomKeypadSheet` (the `OutlinedTextField` fallback described in the KDoc doesn't exist in the code). Decide whether the new project should keep the custom keypad as the only option (current behavior) or restore the toggle.
6. **Fullscreen controls top-right has 5 icons (server / sub / audio / quality / more)** — the owner only flagged the MINIMIZED controls (which have 2: sub + quality). Should the fullscreen top-right also be reduced to 2, or is the 5-icon version OK because fullscreen is "power-user mode"?

---

## 9. Summary table — owner-flagged preferences → action

| Screen | Owner preference | Current code state | New-project action |
|---|---|---|---|
| Player page (MINIMIZED) | Top bar edge-to-edge, no status bar padding | `statusBarsPadding()` on top bar + video fallback | Remove `.statusBarsPadding()` from both |
| Player page (MINIMIZED) | Player at top, darkening gradient + blur below | 35dp gradient only (no blur) | Keep gradient, add `Modifier.blur` layer |
| Player page (MINIMIZED) | Below player: episode # / title / date / description | Already in this order | Port as-is |
| Player controls | Timestamp TL, seekbar bottom, fullscreen right, sub+quality TR | Exactly this layout in `MinimizedControls` | Port as-is |
| Subtitle-tracks sheet | "Updated a bit" + drag handle disabled | Default drag handle shown | Pass `dragHandle = null`; "updated a bit" TBD |
| Subtitle-settings sheet | Partial height, "prime example" | `heightIn(max = 450.dp)`, sectioned | Port as-is (canonical example) |
| Subtitle-settings sheet | Custom keyboard on field tap | `NumericEntrySheet` already wired | Port as-is (canonical keypad) |
| Quality sheet | Drag handle set to false | Default drag handle shown | Pass `dragHandle = null` |

---

*End of analysis. Total source files read in full: 7 (PlayerScreen.kt, MinimizedControls.kt,
FullscreenControls.kt, PlayerSheets.kt, PlayerSheet.kt, SubtitleSettingsPanel.kt,
NumericKeypad.kt). PlayerActivity.kt was spot-read for edge-to-edge setup.*
