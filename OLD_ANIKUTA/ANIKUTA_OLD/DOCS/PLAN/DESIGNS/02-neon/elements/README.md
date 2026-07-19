# UI Element Specs — Dark Neon (ANI-KUTA adaptation)

Exact styling for every key UI element in the Dark Neon design. Quoted from
`DESIGN.md` §7–§12, §16, §18. Tailwind classes are the source of truth; the
Compose column shows the equivalent.

Conventions:
- 1 Tailwind unit = 4px. `p-4` = 16px, `h-12` = 48px, `rounded-2xl` = 16px
  radius, etc.
- All transitions: `transition-all` or `transition-colors`, 200ms (color
  changes, value changes) or 300ms (hover effects). See `motion/README.md`.

---

## 1. Primary action button (Lime)

> Source: §7.1.

| Property | Value | Compose |
|----------|-------|---------|
| Width | `w-full` (block) or auto | `Modifier.fillMaxWidth()` |
| Height | `h-12` (48px) primary; `h-11` (44px) secondary | `48.dp` / `44.dp` |
| Background | `bg-accent-lime` (`#BCFF5F`) | `NeonColors.accentLime` |
| Text color | `text-bg-base` (`#1e1e24`) | `NeonColors.bgBase` |
| Font size | `text-base` (16px) | `16.sp` |
| Font weight | `font-medium` (500) | `FontWeight.Medium` |
| Font family | Sans | `FontFamily.SansSerif` |
| Radius | `rounded-xl` (12px) | `RoundedCornerShape(12.dp)` |
| Glow | `shadow-glow-lime` (`0 0 20px rgba(188,255,95,0.2)`) | `Modifier.neonGlow(NeonGlow.lime, 20.dp)` (custom) |
| Hover bg | `hover:bg-[#d4ff99]` (lighter lime) | `animateColorAsState(target = if (hovered) Color(0xFFD4FF99) else accentLime)` |
| Press | `active:scale-[0.98]` (CSS) / `whileTap scale 0.98` | `Modifier.scale(if (pressed) 0.98f else 1f)` driven by `interactionSource.collectIsPressedAsState()` |
| Disabled | `disabled:opacity-50 disabled:cursor-not-allowed` | `Modifier.alpha(if (enabled) 1f else 0.5f)` |
| Layout | `flex items-center justify-center gap-2` | `Row(horizontalArrangement = Center, verticalAlignment = CenterVertically)` with `8.dp` spacing |
| Icon | `w-5 h-5` (20px), no explicit color (inherits text color = bg-base) | `Icon(modifier = Modifier.size(20.dp), tint = bgBase)` |
| Transition | `transition-all duration-300` | `animateColorAsState(tween(300))` + scale spring |

---

## 2. Secondary action button (Sky)

> Source: §7.2.

Same as primary but:
- Background: `bg-accent-sky` (`#5FC9FF`)
- Glow: `shadow-glow-sky`
- No custom hover color — uses opacity fade instead (`hover:opacity-90` or
  similar).
- Text color: still `text-bg-base` (dark on bright accent).
- Compose glow: `Modifier.neonGlow(NeonGlow.sky, 20.dp)`.

---

## 3. Toggle button (active / inactive)

> Source: §7.3.

| Property | Active | Inactive |
|----------|--------|----------|
| Background | `bg-accent-lime/10` (lime at 10%) | `bg-bg-base` |
| Border | `border border-accent-lime/20` | `border border-white/[0.08]` |
| Text color | `text-accent-lime` | `text-text-muted`, hover → `text-text-secondary` |
| Padding | `py-2.5` (10px vertical) | same |
| Radius | `rounded-xl` (12px) | same |
| Font | `font-medium text-sm` | same |
| Layout | `flex items-center justify-center gap-2` | same |
| Transition | `transition-all duration-200` | same |

---

## 4. Icon button (subtle / destructive)

> Source: §7.4.

| Property | Subtle | Destructive |
|----------|--------|-------------|
| Padding | `p-1.5` (6px) | same |
| Radius | `rounded-lg` (8px) | same |
| Text/icon color | `text-text-dim`, hover → `text-accent-sky` | `text-text-dim`, hover → `text-accent-coral` |
| Hover background | `hover:bg-accent-sky/10` | `hover:bg-accent-coral/10` |
| Icon size | `w-3.5 h-3.5` (14px) | same |
| Transition | `transition-colors` | same |

---

## 5. Ghost / text button

> Source: §7.5.

| Property | Value |
|----------|-------|
| Layout | `flex items-center gap-1.5` (6px gap) |
| Font | `text-xs` (12px) |
| Text color | `text-text-muted`, hover → `text-white` |
| Padding | `px-3 py-1.5` (12px / 6px) |
| Radius | `rounded-lg` (8px) |
| Hover bg | `hover:bg-white/[0.04]` |
| Icon | `w-3 h-3` (12px) |
| Transition | `transition-colors` |

---

## 6. Loading spinner

> Source: §7.6.

| Property | Value |
|----------|-------|
| Size | `w-5 h-5` (20px) standard; `w-3.5 h-3.5` (14px) small |
| Border | `border-2 border-bg-base/30 border-t-bg-base` (standard) — i.e. dark ring at 30% + dark top edge for the spinner arc |
| Small variant border | `border border-accent-coral/30 border-t-accent-coral` |
| Radius | `rounded-full` |
| Animation | `animate-spin` (1s linear infinite) → Compose `InfiniteTransition` rotating 0→360°, 1000ms linear |
| Compose | `CircularProgressIndicator` styled, or custom `Canvas` rotating arc |

---

## 7. Standard card

> Source: §8.1.

| Property | Value |
|----------|-------|
| Background | `bg-bg-surface` (`#28282f`) |
| Border | `border border-white/[0.08]` |
| Radius | `rounded-2xl` (16px) |
| Padding | `p-5` (20px) |
| Header layout | `flex items-center gap-2 mb-5` (8px gap, 20px bottom margin) |
| Header icon badge | `w-7 h-7 rounded-lg bg-accent-sky/5 border border-accent-sky/10 flex items-center justify-center` (28×28dp, 8px radius, sky-tinted) |
| Header icon | `w-3.5 h-3.5 text-accent-sky` (14px, sky) |
| Header label | `text-xs font-medium text-text-muted uppercase tracking-wider` (12px, semibold medium, muted, uppercase, wider letter-spacing) |

### Compose sketch

```kotlin
Surface(
    color = NeonColors.bgSurface,
    border = BorderStroke(1.dp, NeonColors.borderDefault),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.padding(20.dp),
) {
    Column {
        Row(
            horizontalArrangement = spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp),
        ) {
            IconBadge(accent = NeonColors.accentSky) {
                Icon(Icons.Outlined.BarChart, tint = NeonColors.accentSky,
                     modifier = Modifier.size(14.dp))
            }
            Text("CARD TITLE", style = labelStyle)
        }
        // content
    }
}
```

---

## 8. Stat card (small)

> Source: §8.2.

| Property | Value |
|----------|-------|
| Background | `bg-bg-surface` |
| Border | `border border-white/[0.08]` |
| Radius | `rounded-2xl` (16px) |
| Padding | `p-4` (16px) |
| Layout | `flex flex-col justify-between` |
| Header row | `flex items-center gap-2 mb-3` (8px gap, 12px bottom) |
| Header icon badge | `w-7 h-7 rounded-lg bg-accent-lime/5 border border-accent-lime/10 flex items-center justify-center` |
| Header label | `text-[10px] font-medium text-text-muted uppercase tracking-wider` (10px, muted, uppercase) |
| Value | `text-lg font-bold font-mono text-white` (18px, bold, mono) |
| Sub-value | `text-xs font-mono mt-1 text-accent-lime` (12px, mono, lime) |

---

## 9. Highlighted / status card

> Source: §8.3.

Conditional coloring based on state. All variants: `rounded-xl p-4 border`.

| State | Background | Border | Icon | Icon color |
|-------|------------|--------|------|------------|
| Positive / good | `bg-accent-lime/5` | `border-accent-lime/10` | `CheckCircle2` | `text-accent-lime` |
| Warning / info | `bg-accent-sky/5` | `border-accent-sky/10` | `Sparkles` | `text-accent-sky` |
| Danger | `bg-accent-coral/5` | `border-accent-coral/10` | `AlertTriangle` | `text-accent-coral` |

Title: `text-sm font-semibold text-white`. Description:
`text-xs text-text-secondary`.

### ANI-KUTA mapping

| Anime state | Variant | Icon |
|-------------|---------|------|
| Completed | Positive | `check_circle` |
| Currently watching / airing today | Warning | `play_circle` |
| Dropped / error | Danger | `pause_circle` |
| New episode available | Positive | `notifications_active` |

---

## 10. Icon badge

> Source: §8.4.

Two sizes.

| Variant | Size | Radius | Background | Border | Icon | Glow |
|---------|------|--------|------------|--------|------|------|
| Small | `w-7 h-7` (28dp) | `rounded-lg` (8dp) | `bg-accent-{c}/5` | `border-accent-{c}/10` | `w-3.5 h-3.5 text-accent-{c}` (14dp) | none |
| Large | `w-9 h-9` (36dp) | `rounded-xl` (12dp) | `bg-accent-{c}/10` | `border-accent-{c}/20` | `w-5 h-5 text-accent-{c}` (20dp) | `shadow-glow-step` |

Used next to titles, in sidebars, and as section headers.

---

## 11. Decorative in-card glow

> Source: §8.5.

Decorative blurred circle, absolutely positioned, `pointer-events-none`.

| Variant | Size | Blur | Background | Position |
|---------|------|------|------------|----------|
| Default | `w-32 h-32` (128dp) | `blur-[60px]` | `bg-accent-lime/5` | `top-0 right-0` |
| Small | `w-10 h-10` (40dp) | `blur-[20px]` | `bg-accent-lime/10` | `top-0 right-0` |
| Centered | `w-32 h-32` (128dp) | `blur-[60px]` | `bg-accent-sky/5` | `top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2` |

Compose: `Modifier.drawBehind { drawCircle(Brush.radialGradient(listOf(color, Transparent))) }` clipped to the card.

---

## 12. Icons

> Source: §9.

- **Library (web):** Lucide React. Line-art, consistent stroke width.
- **Library (ANI-KUTA):** Material Symbols Outlined (Android-idiomatic
  line-art). Alternative: a Lucide-compatible port if pixel parity is
  required.

### Icon size conventions (§9.2)

| Context | Size | Class | Compose `.size(?)` |
|---------|------|-------|---------------------|
| Inside small badge | 14px | `w-3.5 h-3.5` | `14.dp` |
| Inside label row | 14px | `w-3.5 h-3.5` | `14.dp` |
| Inside button | 16–20px | `w-4 h-4` / `w-5 h-5` | `16.dp` / `20.dp` |
| Section icon | 16–20px | `w-4 h-4` / `w-5 h-5` | `16.dp` / `20.dp` |
| Empty state icon | 28–32px | `w-7 h-7` / `w-8 h-8` | `28.dp` / `32.dp` |
| Feature preview icon | 24–28px | `w-6 h-6` / `w-7 h-7` | `24.dp` / `28.dp` |

### Icon color rules (§9.3)

- Icons inherit their context color: `text-accent-lime`, `text-accent-sky`,
  `text-accent-coral`, or `text-text-muted`.
- Icon badges use the accent at 5–10% opacity for background, 10–20% for
  border.
- Never use raw `text-white` for icons in labels — use `text-text-muted` or
  accent colors.

### Common icon assignments (§9.4) — adapted for ANI-KUTA

| Concept | Original Lucide icon | ANI-KUTA Material Symbol | Accent |
|---------|---------------------|--------------------------|--------|
| Profit / Gain / Positive | `ArrowUpRight` | `trending_up` / `arrow_upward` | Lime |
| Loss / Negative | `ArrowDownRight` | `trending_down` / `arrow_downward` | Coral |
| Money / Currency | `DollarSign` | `payments` | Context |
| Percentage / Rate | `Percent` | `percent` | Context |
| Chart / Volume | `BarChart3` | `bar_chart` | Sky |
| Calculator / Fees | `Calculator` | `calculate` | Sky |
| Target / Estimation | `Target` | `target` / `my_location` | Sky |
| Speed / Leverage | `Zap` | `bolt` / `speed` | Context |
| Security / Confidence | `Shield` | `shield` | Sky |
| Activity / Live | `Activity` | `graphic_eq` / `pulse` | Lime |
| Success / Confirmed | `CheckCircle2` | `check_circle` | Lime |
| Warning | `AlertTriangle` | `warning` | Coral / Sky |
| Magic / AI / Smart | `Sparkles` | `auto_awesome` | Sky |
| Time / History | `Clock` | `schedule` | Muted |
| Edit / Modify | `Pencil` | `edit` | Sky |
| Delete / Remove | `Trash2` | `delete` | Coral |
| Save / Confirm | `Save` | `save` | Sky |
| Refresh | `RotateCcw` | `refresh` | Muted |
| Eye / Live Preview | `Eye` | `visibility` | Sky |
| Keyboard | `Keyboard` | `keyboard` | Sky |

Additional ANI-KUTA-only assignments:

| Concept | Material Symbol | Accent |
|---------|----------------|--------|
| New episode / airing soon | `notifications_active` | Lime |
| Watching / in progress | `play_circle` | Sky |
| Dropped / paused | `pause_circle` | Coral |
| Plan to watch | `bookmark` | Muted |
| Rating / score | `star` | Lime |
| Schedule / calendar | `calendar_today` | Sky |
| Search | `search` | Muted |
| Download | `download` | Sky |
| Settings | `settings` | Muted |
| Library | `library_books` | Muted |
| Home / discover | `home` | Muted |

---

## 13. Form inputs & keypads

> Source: §10.

The original's keypad-input pattern is for trading apps; ANI-KUTA adapts the
same shape to episode pickers, source pickers, etc.

### Keypad input (tap-to-enter) (§10.1)

| Property | Value |
|----------|-------|
| Label | `text-xs font-medium text-text-muted uppercase tracking-wider block mb-2` |
| Required asterisk | `text-accent-coral ml-0.5` |
| Container | `relative` |
| Leading icon | `absolute left-3 top-1/2 -translate-y-1/2`, `w-4 h-4 text-text-dim` |
| Button (input) | `w-full h-11 bg-bg-base border rounded-xl pl-10 pr-4 text-sm font-mono text-left flex items-center cursor-pointer transition-all select-none overflow-hidden` |
| Active state | `border-accent-lime/40 ring-2 ring-accent-lime/15` |
| Inactive state | `border-white/[0.08] hover:border-white/[0.15]` |
| Value text | `truncate text-white` if value; `text-white/15` if placeholder |
| Active cursor | `inline-block w-[2px] h-5 bg-accent-sky animate-pulse ml-0.5 rounded-full shrink-0` (2×20dp sky, pulsing) |

### Numeric keypad — mobile bottom sheet (§10.2)

Slides up from bottom on screens < 1024px.

| Property | Value |
|----------|-------|
| Backdrop | `fixed inset-0 z-40 bg-black/20`, click to dismiss |
| Panel | `fixed bottom-0 left-0 right-0 z-50 bg-bg-sidebar/[0.97] backdrop-blur-2xl border-t border-white/[0.08] rounded-t-2xl shadow-2xl` |
| Safe-area padding | `env(safe-area-inset-bottom, 16px)` |
| Display row | `px-4 pt-3 pb-2 flex items-center justify-between border-b border-white/[0.06]` |
| Display label | `text-[10px] text-text-muted uppercase tracking-wider font-semibold` |
| Display icon | `w-3.5 h-3.5 text-accent-sky` |
| Display value | `text-xl font-bold font-mono text-white min-w-[80px] text-right` (20px bold mono) |
| Display cursor | `inline-block w-[2px] h-5 bg-accent-sky animate-pulse ml-0.5 rounded-full align-middle` |
| Key grid | `grid grid-cols-3 gap-1.5 p-3 max-w-sm mx-auto` (3 cols, 6px gap, 12px padding, max 384px wide) |
| Key button | `h-14 rounded-xl text-xl font-mono font-semibold bg-white/[0.06] text-white hover:bg-white/[0.1] flex items-center justify-center transition-colors` (56dp tall, 12px radius, 20px mono) |
| Confirm button | `w-full h-12 rounded-xl text-base font-medium bg-accent-lime text-bg-base active:scale-[0.98] shadow-lg` |

Key layout: `['1','2','3','4','5','6','7','8','9','.','0','backspace']`.

For ANI-KUTA: this pattern becomes the **episode picker**, **source picker**,
and **playback-speed picker** bottom sheets. Same visual recipe, different
content.

### Numeric keypad — desktop floating popup (§10.3)

For ANI-KUTA: not applicable (no desktop). Listed for completeness.

Appears near the input as a positioned floating card. Same internal
structure as mobile but smaller: `h-11` keys (44dp) instead of `h-14`,
`text-lg` (18px) instead of `text-xl`, confirm `h-10` (40dp) instead of
`h-12`. Container: `fixed z-50 bg-bg-sidebar/[0.97] backdrop-blur-2xl border
border-white/[0.12] rounded-2xl shadow-2xl`, width `280px`.

Positioning logic: default below input left-aligned; flip above if viewport
overflow; shift left if right overflow; min left 16px.

### Leverage slider (§10.4)

For ANI-KUTA: becomes **playback speed slider** / **skip-intro-length
slider**. Same shape.

| Property | Value |
|----------|-------|
| Label | `text-xs font-medium text-text-muted uppercase tracking-wider` |
| Number input | `w-16 h-7 text-center text-xs font-mono rounded-lg border bg-accent-lime/10 border-accent-lime/20 text-accent-lime focus:border-accent-lime/30 focus:ring-accent-lime/10 focus:ring-2 outline-none transition-all` |
| Unit suffix | `text-xs text-text-muted font-mono` (e.g. "x") |
| Slider track | `w-full h-2 rounded-full appearance-none cursor-pointer` |
| Slider fill | `linear-gradient(to right, #BCFF5F 0%, #BCFF5F ${percent}%, rgba(255,255,255,0.06) ${percent}%, rgba(255,255,255,0.06) 100%)` |

### Input mode toggle (§10.5)

For ANI-KUTA: becomes **dub/sub toggle**, **quality picker toggle**.

| Property | Active | Inactive |
|----------|--------|----------|
| Layout | `grid grid-cols-2 gap-2` | (same) |
| Button | `py-2 rounded-xl font-medium text-xs flex items-center justify-center gap-1.5 transition-all border` | (same) |
| Background | `bg-accent-lime/10` | `bg-bg-base` |
| Border | `border-accent-lime/20` | `border border-white/[0.08]` |
| Text | `text-accent-lime` | `text-text-muted` |
| Icon | `w-3.5 h-3.5` (14dp) | same |

---

## 14. Modals & overlays

> Source: §11.

### Full edit modal (§11.1)

| Property | Value |
|----------|-------|
| Backdrop | `fixed inset-0 z-30 bg-black/40 backdrop-blur-sm` (40% black + small blur), click to close |
| Modal container | `fixed inset-4 sm:inset-auto sm:left-1/2 sm:top-1/2 sm:-translate-x-1/2 sm:-translate-y-1/2 sm:w-full sm:max-w-lg z-40` |
| Modal surface | `bg-bg-sidebar/[0.98] backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl overflow-y-auto max-h-[90vh] custom-scrollbar` |
| Inner padding | `p-5 sm:p-6` (20–24px) |
| Structure | Header (icon + title + close) → Form → Action buttons |
| Animation | `motion.div initial={{opacity:0,scale:0.95,y:20}} animate={{opacity:1,scale:1,y:0}} exit={{opacity:0,scale:0.95,y:20}} transition={{type:'spring',damping:25,stiffness:300}}` |
| Close via | Backdrop click, X button, Escape key |
| Wrap in | `<AnimatePresence>` for enter/exit |

### Confirmation / simple overlay (§11.2)

- Backdrop: `fixed inset-0 z-40 bg-black/20` (20% black, no blur)
- Click backdrop to dismiss
- No close X — interaction is the dismiss

For ANI-KUTA: confirmation overlays for "remove from library?", "delete
download?", "mark as dropped?".

### Compose mapping

| Web | Compose |
|-----|---------|
| `fixed inset-0 z-30 bg-black/40 backdrop-blur-sm` (backdrop) | `Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).graphicsLayer { renderEffect = RenderEffect.createBlurEffect(4.dp.toPx(), 4.dp.toPx(), CLAMP) }` |
| Modal surface | `Surface(color = bgSidebar.copy(alpha = 0.98f), border = BorderStroke(1.dp, borderStrong), shape = RoundedCornerShape(16.dp))` + blur graphicsLayer |
| `motion.div` spring enter/exit | `AnimatedVisibility(visible, enter = springIn(...), exit = springOut(...))` |
| `<AnimatePresence>` | `AnimatedVisibility` |

---

## 15. Tables & lists

> Source: §12.

### Data table (desktop) (§12.1)

| Property | Value |
|----------|-------|
| Container | `bg-bg-surface border border-white/[0.08] rounded-2xl overflow-hidden` |
| Header row | `grid grid-cols-[...] gap-3 px-4 py-3 border-b border-white/[0.08] bg-bg-sidebar/50 text-xs font-semibold text-text-muted uppercase tracking-wider` |
| Body wrapper | `max-h-[calc(100dvh-320px)] overflow-y-auto custom-scrollbar` |
| Body row | `grid grid-cols-[...] gap-3 px-4 py-3 border-b border-white/[0.04] items-center hover:bg-white/[0.02] transition-colors` |

For ANI-KUTA: episode lists, library lists, schedule lists. Same shape —
grid columns adapt per content.

### Responsive table (§12.2)

| Variant | Visibility | Columns |
|---------|------------|---------|
| Desktop row | `hidden md:grid` | Full data, ~7+ cols |
| Mobile row | `md:hidden grid` | Condensed, 5 cols max |

For ANI-KUTA: phone = single-column list of cards; tablet = full data table.
Use `WindowSizeClass` to switch.

### Direction badge (§12.3)

For ANI-KUTA: becomes **dub/sub badge**, **airing status badge**.

| Variant | Desktop | Mobile |
|---------|---------|--------|
| Container | `inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-lg` | `inline-flex items-center gap-0.5 text-[10px] font-medium px-1.5 py-0.5 rounded-lg` |
| Long / positive | `bg-accent-lime/10 text-accent-lime` + `TrendingUp w-3 h-3` | same with `w-2.5 h-2.5` icon, no text |
| Short / negative | `bg-accent-coral/10 text-accent-coral` + `TrendingDown w-3 h-3` | same with `w-2.5 h-2.5` icon, no text |

---

## 16. Navigation patterns

> Source: §16.

### Desktop sidebar (§16.1)

For ANI-KUTA: tablet/large-foldable drawer.

| Property | Value |
|----------|-------|
| Width | `w-[220px] shrink-0` (220dp) |
| Visibility (web) | `max-lg:hidden` (hidden below 1024px) |
| Visibility (ANI-KUTA) | Shown at `WindowSizeClass.Expanded` |
| Brand row | `h-16 border-b` (64dp tall, border-bottom) |
| Section label | `text-[10px] uppercase dim` (uppercase, muted-dim) |
| Active nav item | `w-full flex items-center gap-3 px-3 py-2.5 rounded-xl bg-accent-lime/7 border border-accent-lime/14.5 text-accent-lime transition-all duration-200` |
| Inactive nav item | `w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-text-muted hover:text-text-secondary hover:bg-white/[0.04] border border-transparent transition-all duration-200` |
| Label text | `text-sm font-medium` (14px, medium) |
| Optional footer block | Quick stats (Total Trades, Total Fees, etc.) — for ANI-KUTA, "Watch Time", "Episodes Left", "Continue Watching" |

### Mobile header + tab bar (§16.2)

For ANI-KUTA: phone layout.

#### Header bar (h-14 / 56dp)

| Property | Value |
|----------|-------|
| Container | `lg:hidden h-14 bg-bg-sidebar border-b border-white/[0.06] flex items-center justify-between px-4` |
| Left brand | `flex items-center gap-2` |
| Brand badge | `w-8 h-8 rounded-lg bg-accent-lime/10 border border-accent-lime/20 flex items-center justify-center` |
| Brand icon | `w-4 h-4 text-accent-lime` (16dp lime) |
| Brand text | `text-sm font-bold text-white` (14px bold) |
| Right menu button | `w-8 h-8 rounded-lg flex items-center justify-center text-text-muted hover:text-white hover:bg-white/[0.08]` |
| Menu icon | `w-4 h-4` (16dp) |

#### Tab bar (below header)

| Property | Value |
|----------|-------|
| Container | `flex border-b border-white/[0.06] bg-bg-sidebar overflow-x-auto` |
| Tab button | `flex-1 flex items-center justify-center gap-1 py-3 text-[11px] font-medium whitespace-nowrap` |
| Tab active | `text-accent-lime border-b-2 border-accent-lime` |
| Tab inactive | `text-text-muted` |
| Tab icon | `w-4 h-4` (16dp) |

For ANI-KUTA: the tab bar is the bottom navigation (`NavigationBar` in
Material 3) re-themed to neon tokens — but the visual recipe (lime active
with bottom border, muted inactive) carries over.

---

## 17. Empty state

> Source: §18.2.

| Property | Value |
|----------|-------|
| Container | `motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} text-center py-12` |
| Icon badge | `w-16 h-16 mx-auto rounded-2xl bg-accent-lime/5 border border-accent-lime/10 flex items-center justify-center mb-4` (64×64dp, 16dp radius, lime-tinted, 16dp bottom) |
| Icon | `w-8 h-8 text-accent-lime/30` (32dp, lime at 30%) |
| Heading | `text-lg font-semibold text-white mb-2` (18px, semibold, 8dp bottom) |
| Description | `text-sm text-text-muted mb-6 max-w-md mx-auto` (14px, muted, 24dp bottom, max 448px wide) |
| CTA button | Lime primary button (see §1) — `px-6 h-12 rounded-xl` |

---

## 18. Key / value row

> Source: §18.3.

| Variant | Layout |
|---------|--------|
| Plain | `flex items-center justify-between` + `text-xs text-text-muted` (label) + `font-mono text-sm text-white` (value) |
| With icon | Same, label becomes `text-xs text-text-muted flex items-center gap-1.5` with `Icon w-3 h-3` (12dp); value `font-mono text-sm text-accent-sky` |

For ANI-KUTA: detail-page metadata rows ("Studio: Bones", "Episodes: 24",
"Score: 8.45", "Status: Airing"). The mono value + accent sky for emphasized
values carries over.

---

## 19. Loading spinner (small)

> Source: §7.6 small variant.

| Property | Value |
|----------|-------|
| Size | `w-3.5 h-3.5` (14dp) |
| Border | `border border-accent-coral/30 border-t-accent-coral` (1px coral at 30% + 1px coral top) |
| Radius | `rounded-full` |
| Animation | `animate-spin` (1s linear infinite) |

---

## 20. Live / active indicator (pulse)

> Source: §13.9.

| Property | Value |
|----------|-------|
| Container | `relative flex h-2 w-2` (8×8dp) |
| Halo | `animate-ping absolute inline-flex h-full w-full rounded-full bg-accent-sky opacity-75` |
| Core | `relative inline-flex rounded-full h-2 w-2 bg-accent-sky` |
| Label | `text-[9px] font-bold text-accent-sky uppercase tracking-widest` ("LIVE") |

For ANI-KUTA: "AIRING NOW" indicator on a detail page when an episode is
live. Use sparingly — at most one per screen.

---

## 21. Blinking cursor

> Source: §13.10.

| Property | Value |
|----------|-------|
| Element | `inline-block w-[2px] h-5 bg-accent-sky animate-pulse ml-0.5 rounded-full shrink-0` |
| Size | 2×20dp sky |
| Animation | `animate-pulse` (opacity 1↔0.5, ~2s) |

Used inside active keypad inputs and active search fields.

---

## 22. Animated top border (live preview)

> Source: §13.11.

| Property | Value |
|----------|-------|
| Container | `absolute top-0 left-0 w-full h-1` (1dp tall, full width, top-anchored) |
| Background | `bg-gradient-to-r from-transparent via-accent-sky/20 to-transparent` (sky at 20% in the middle) |
| Animation | `animate-pulse` |

For ANI-KUTA: top edge of the player view while a stream is loading —
signals "preparing stream, don't tap away".

---

## 23. Scrollbar

> Source: §15.

| Property | Value |
|----------|-------|
| Width | 6px |
| Track | transparent |
| Thumb | `rgba(255,255,255,0.1)` (white at 10%) |
| Thumb hover | `rgba(255,255,255,0.2)` (white at 20%) |
| Thumb radius | `9999px` (fully rounded) |

Applied to all scrollable regions: `overflow-y-auto custom-scrollbar`.

Compose: draw via `Modifier.drawWithContent` reading `LazyListState`
scroll progress; or accept platform scrollbar tinted toward white/10%.
