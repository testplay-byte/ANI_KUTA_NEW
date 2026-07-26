# Design 4 — Coffee (AniVerse Notebook) — Motion & Animation

Animations are split between **framer-motion** (React component-level animations) and **CSS keyframes** (utility-class animations). The template uses framer-motion ^12.23.2 and `tw-animate-css ^1.3.5` (the Tailwind v4 successor to `tailwindcss-animate`).

## framer-motion variants (page.tsx lines 800–818)

### `fadeSlideUp` — staggered list-item entrance

```ts
const fadeSlideUp = {
  hidden: { opacity: 0, y: 16 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.3, ease: [0.0, 0.0, 0.2, 1], delay: i * 0.06 },
  }),
};
```

- Used by: `AnimeCard`, `GenreCard`.
- Custom prop `i` (item index) drives stagger delay of 60ms per item.
- Ease `[0, 0, 0.2, 1]` is a Material-emphasized-decelerate curve (gentle ease-out).
- Mobile bypass: `initial={isMobile ? "visible" : "hidden"}` and `whileInView={isMobile ? undefined : "visible"}` so mobile cards render in final state immediately (because IntersectionObserver fails inside `overflow-x-auto` containers).

### `sectionFade` — section container entrance

```ts
const sectionFade = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.4, ease: [0.0, 0.0, 0.2, 1] },
  },
};
```

- Used by: `ContentRow`, `GenreSection`, `NextReleasingSection`.
- Slightly slower (0.4s) and larger y-distance (20px) than `fadeSlideUp` to differentiate section-level vs item-level motion.
- Always uses `viewport={{ once: true, margin: "-60px" }}` (or `-50px` for items) — i.e. trigger when 60px of section is visible, then never re-animate.

## Component-level framer-motion usage

### Header entrance (line 1680)

```tsx
<motion.div
  initial={{ y: -20, opacity: 0 }}
  animate={{ y: 0, opacity: 1 }}
  transition={{ duration: 0.4, ease: [0.0, 0.0, 0.2, 1] }}
>
```

Header slides down 20px on page load. No whileInView gating — animates on mount.

### Hero background cross-fade (line 1869)

```tsx
<AnimatePresence mode="wait">
  <motion.div
    key={`hero-bg-${heroIndex}`}
    initial={{ opacity: 0 }}
    animate={{ opacity: 1 }}
    exit={{ opacity: 0 }}
    transition={{ duration: 0.8, ease: "easeInOut" }}
  >
```

- `mode="wait"` ensures outgoing bg fully fades out before incoming fades in.
- 0.8s is the longest duration in the template — gives the hero bg a cinematic feel.
- `key=hero-bg-${heroIndex}` triggers re-mount + AnimatePresence enter/exit on every carousel change.

### Hero text slide (line 1900)

```tsx
<AnimatePresence mode="wait">
  <motion.div
    key={`hero-text-${heroIndex}`}
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: -10 }}
    transition={{ duration: 0.45, ease: [0.0, 0.0, 0.2, 1] }}
  >
```

Text enters from below (y:20→0) and exits upward (y:0→-10) — i.e. text "moves up and out" while new text "moves up and in", creating a sense of forward motion through the carousel.

### Hero CTA buttons (lines 1952, 1968)

```tsx
<motion.button whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
```

- Hover: scale up 3%.
- Tap: scale down 3%.
- No explicit `transition` — uses framer-motion's default spring.
- Same pattern for bookmark button but with larger range: `whileHover scale:1.1, whileTap scale:0.9` (the bookmark is round and smaller, so a bigger swing reads well).

### AnimeCard play button (line 984)

```tsx
<AnimatePresence>
  {isHovered && (
    <motion.div
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.8 }}
      transition={{ duration: 0.15, ease: [0.0, 0.0, 0.2, 1] }}
    >
```

Quick pop-in/out on hover. 0.15s is the shortest animation in the template — the play button needs to feel instant.

### AnimeCard hover lift (CSS, line 901)

```css
.pt-3.transition-transform.duration-300.ease-out.hover:scale-[1.05].origin-top
```

The OUTER wrapper (not the card body) scales 5% on hover with `origin-top` (so the card scales down from its top edge, keeping the bottom aligned with the row below). The inner image separately does `group-hover:scale-[1.06]` + `brightness-[0.85]` for parallax depth.

### GenreCard hover (line 1105)

```tsx
<motion.div
  whileHover={{ scale: 1.03, y: -4 }}
  whileTap={{ scale: 0.97 }}
  transition={{ duration: 0.2, ease: [0.0, 0.0, 0.2, 1] }}
>
```

Combined scale + y-translate — lifts the card 4px AND scales 3% simultaneously. This is the same physical-paper-lift idea as `.card-paper-hover` (which uses `translateY(-3px) rotate(-0.3deg)` via CSS). The two patterns coexist: cards in horizontal rows use CSS hover; cards in vertical grid use framer.

### Nav active pill — shared layout animation (line 1717)

```tsx
<motion.div
  layoutId="nav-pill"
  className="absolute inset-0 bg-white/15 rounded-lg border border-white/10"
  transition={{ type: "spring", stiffness: 400, damping: 30 }}
/>
```

- `layoutId` is framer-motion's shared-element transition: when the active nav button changes, the highlight pill **slides** between buttons instead of fading out/in.
- Spring physics: `stiffness 400` (medium-firm), `damping 30` (moderately damped, no overshoot).
- This is the only `layoutId` usage in the template.

### Theme toggle icon swap (line 1812)

```tsx
<AnimatePresence mode="wait">
  {isDark ? (
    <motion.div key="sun"
      initial={{ rotate: -90, opacity: 0 }}
      animate={{ rotate: 0, opacity: 1 }}
      exit={{ rotate: 90, opacity: 0 }}
      transition={{ duration: 0.2 }}>
      <Sun className="w-4 h-4" />
    </motion.div>
  ) : (
    <motion.div key="moon"
      initial={{ rotate: 90, opacity: 0 }}
      animate={{ rotate: 0, opacity: 1 }}
      exit={{ rotate: -90, opacity: 0 }}
      transition={{ duration: 0.2 }}>
      <Moon className="w-4 h-4" />
    </motion.div>
  )}
</AnimatePresence>
```

- `mode="wait"` so the exiting icon finishes before the new one enters.
- Sun enters rotating from -90°, exits rotating to +90° (clockwise).
- Moon enters from +90°, exits to -90° (counter-clockwise).
- 0.2s duration — quick but visible.

### Mobile menu dropdown (line 1768)

```tsx
<AnimatePresence>
  {mobileMenuOpen && (
    <motion.div
      initial={{ opacity: 0, y: -8, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -8, scale: 0.95 }}
      transition={{ duration: 0.15, ease: [0.0, 0.0, 0.2, 1] }}
    >
```

Drops in from top-right (origin at hamburger button location). 0.15s — quick.

### Hover popup — Coming-Up-Next (line 1604)

```tsx
<motion.div
  ref={popupRef}
  initial={{ opacity: 0, scale: 0.95, y: popupPos.placement === "above" ? 8 : -8 }}
  animate={{ opacity: 1, scale: 1, y: 0 }}
  exit={{ opacity: 0, scale: 0.95, y: popupPos.placement === "above" ? 8 : -8 }}
  transition={{ duration: 0.2, ease: [0.34, 1.56, 0.64, 1] }}
>
```

- Placement-aware y-offset: if popup appears above the entry, it enters from below (+8) and exits downward (+8); if below, the reverse.
- Ease `[0.34, 1.56, 0.64, 1]` is a Material-back-ease (slight overshoot past 1 then settle) — gives the popup a playful pop.
- 300ms hover-stillness delay before triggering (avoids flicker when sweeping across entries).
- 200ms hide-delay grace period so users can move mouse from entry to popup without dismissing.

### Footer entrance (line 2044)

```tsx
<motion.footer
  initial={{ opacity: 0 }}
  whileInView={{ opacity: 1 }}
  viewport={{ once: true }}
  transition={{ duration: 0.4 }}
>
```

Simple opacity fade. No translation.

## CSS keyframe animations (globals.css)

### `shimmer` (line 358)

```css
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.skeleton-shimmer-light {
  background: linear-gradient(90deg, #E5DAD0 25%, #D8CCBC 50%, #E5DAD0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
```

- Linear-gradient with 3 stops (muted → slightly darker → muted).
- Background-position shifts from `200% 0` to `-200% 0` over 1.5s.
- Used by: AnimeCard image placeholder (until image loads).

### `ink-spread` (line 375)

```css
@keyframes ink-spread {
  0% { transform: scale(0); opacity: 0.6; }
  100% { transform: scale(1); opacity: 0; }
}
```

- Defined but **not currently used** in `page.tsx`.
- Reserved for future loading indicators (an ink-blot growing outward then fading).
- ANI-KUTA could use this as a tap-ripple equivalent.

### `gentle-float` (line 409)

```css
@keyframes gentle-float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-6px) rotate(1deg); }
}
.animate-gentle-float {
  animation: gentle-float 5s ease-in-out infinite;
}
```

- 5-second cycle: floats up 6px and rotates 1°, then back.
- Used by: hero decorative blurred glow (`top-24 right-[20%]` `bg-primary/10 blur-2xl`).

### `.card-paper-hover` (line 381)

```css
.card-paper-hover {
  transition: transform 200ms ease-out, box-shadow 200ms ease-out;
}
.card-paper-hover:hover {
  transform: translateY(-3px) rotate(-0.3deg);
  box-shadow:
    0 10px 28px var(--notebook-shadow-hover),
    0 3px 10px var(--notebook-shadow);
}
```

- 200ms ease-out.
- Lift -3px + tilt -0.3° + 2-layer shadow (large diffuse + small tight).
- **Defined but NOT applied** in `page.tsx` — the actual AnimeCard hover uses Tailwind's `hover:scale-[1.05]` instead. The `.card-paper-hover` class is reserved for future use; ANI-KUTA should adopt it as the standard card hover.

## tw-animate-css utilities (used in shadcn components)

These come from the `tw-animate-css` package (Tailwind v4 successor to `tailwindcss-animate`):

- `animate-in fade-in-0 zoom-in-95` — tooltip enter
- `data-[state=closed]:animate-out fade-out-0 zoom-out-95` — tooltip exit
- `data-[side=bottom]:slide-in-from-top-2` etc. — direction-aware slide-in
- `data-[state=open]:animate-in slide-in-from-top-full sm:slide-in-from-bottom-full` — toast enter
- `data-[state=closed]:animate-out fade-out-80 slide-out-to-right-full` — toast exit
- `data-[swipe=move]:transition-none data-[swipe=end]:animate-out` — toast swipe dismissal

These are utility classes that wrap CSS keyframes shipped by `tw-animate-css`. They're only used in `tooltip.tsx` and `toast.tsx`.

## Animation timing catalog

| Animation | Duration | Ease | When |
|---|---|---|---|
| `fadeSlideUp` item entrance | 0.3s | `[0,0,0.2,1]` decel | +60ms × i stagger |
| `sectionFade` section entrance | 0.4s | `[0,0,0.2,1]` decel | on viewport enter, once |
| Header entrance | 0.4s | `[0,0,0.2,1]` decel | on mount |
| Hero bg cross-fade | 0.8s | `easeInOut` | on carousel change |
| Hero text slide | 0.45s | `[0,0,0.2,1]` decel | on carousel change |
| Hero CTA hover/tap | (default spring) | spring | on hover/tap |
| Play button pop | 0.15s | `[0,0,0.2,1]` decel | on card hover |
| AnimeCard hover scale (CSS) | 0.3s | `ease-out` | on hover |
| GenreCard hover | 0.2s | `[0,0,0.2,1]` decel | on hover/tap |
| Nav pill slide | spring | stiffness 400 damping 30 | on nav change |
| Theme icon swap | 0.2s | linear-ish (no ease specified beyond rotate) | on toggle |
| Mobile menu dropdown | 0.15s | `[0,0,0.2,1]` decel | on open/close |
| Popup entrance | 0.2s | `[0.34,1.56,0.64,1]` back-out | after 300ms hover delay |
| Footer fade | 0.4s | linear | on viewport enter |
| Shimmer skeleton | 1.5s loop | ease-in-out | while image loading |
| `gentle-float` glow | 5s loop | ease-in-out | always (decoration) |
| `.card-paper-hover` (defined, unused) | 0.2s | ease-out | on hover |
| `ink-spread` (defined, unused) | (no class) | — | reserved for loading |

## Reduced-motion support (globals.css lines 419–425)

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

Forces all CSS animations/transitions to near-zero duration. **Note**: this only covers CSS animations, NOT framer-motion. The framer-motion animations would need a separate `useReducedMotion()` hook check to be fully accessible. The template does NOT do this — an accessibility gap to fix in ANI-KUTA.

## Compose adaptation

### Motion specs → Compose

| Web pattern | Compose equivalent |
|---|---|
| `fadeSlideUp` stagger | `AnimatedVisibility` per item with `LaunchedEffect(item.id) { delay(i * 60) }` to trigger; or `LazyRow` with `animateItem()` (Compose 1.7+) for reordering |
| `sectionFade` | `AnimatedVisibility(visibleState = remember { MutableTransitionState(false).apply { targetState = true } })` |
| Hero cross-fade `mode="wait"` | `Crossfade(targetState = heroIndex, animationSpec = tween(800, easing = FastOutSlowInEasing))` |
| Hero text slide | `AnimatedContent(targetState = heroIndex, transitionSpec = { fadeIn + slideInVertically() togetherWith fadeOut + slideOutVertically() })` |
| `whileHover scale:1.03` | `Modifier.pointerInput` + `graphicsLayer { scaleX = if (hovered) 1.03f else 1f }` with `animateFloatAsState` |
| `whileTap scale:0.97` | `Modifier.pointerInput { detectTapGestures(onPress = …) }` + same `graphicsLayer` with pressed state |
| `layoutId="nav-pill"` spring | Compose `SharedTransitionLayout` (1.7+) with `Modifier.sharedElement(rememberSharedContentState(key = "nav-pill"), boundsTransform = { _, _ -> spring(dampingRatio = 0.8f, stiffness = 400f) })` |
| Theme icon rotate-swap | `Crossfade(targetState = isDark, animationSpec = tween(200))` with rotation via `graphicsLayer { rotationZ = … }` |
| Mobile menu dropdown | `AnimatedVisibility(visible = menuOpen, enter = fadeIn() + slideInVertically() + scaleIn(), exit = fadeOut() + slideOutVertically() + scaleOut())` |
| Popup placement-aware y | `AnimatedVisibility` with custom `EnterTransition`/`ExitTransition` selecting slide direction by placement |
| `[0.34,1.56,0.64,1]` back-out ease | `spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)` (slight overshoot) OR `androidx.compose.animation.core.FastOutSlowInEasing` for non-overshoot |
| `[0,0,0.2,1]` Material decel | `androidx.compose.animation.core.FastOutSlowInEasing` (cubic `(0, 0, 0.2, 1)`) |
| `easeInOut` | `androidx.compose.animation.core.FastOutSlowInEasing` |
| `gentle-float` 5s infinite | `rememberInfiniteTransition`; `animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing)))`; apply via `graphicsLayer { translationY = -6.dp.toPx() * sin(value * 2 * PI).toFloat(); rotationZ = (value * 2 * PI).toFloat() }` |
| `shimmer` skeleton | `Modifier.drawBehind` + `rememberInfiniteTransition` translating a `linear-gradient` Brush horizontally |
| `prefers-reduced-motion` | Check `Settings.Global.ANIMATOR_DURATION_SCALE` or `LocalAccessibilityManager.current?.isReduceMotionEnabled` and skip non-essential animations |
| `ink-spread` (unused but reserved) | `rememberInfiniteTransition` with `scale 0→1 opacity 0.6→0` over 1s, used as tap ripple |

### Compose timing constants

```kotlin
object CoffeeMotion {
    const val Instant = 50       // 0.05s — micro
    const val Fast = 150         // 0.15s — play button pop
    const val Quick = 200        // 0.2s — popup, theme toggle, hover
    const val Standard = 300     // 0.3s — fadeSlideUp
    const val Section = 400      // 0.4s — sectionFade, header, footer
    const val Hero = 450         // 0.45s — hero text slide
    const val HeroBg = 800       // 0.8s — hero bg cross-fade
    const val Float = 5000       // 5s — gentle-float loop
    const val Shimmer = 1500     // 1.5s — skeleton loop

    val Decel = FastOutSlowInEasing  // [0, 0, 0.2, 1]
    val BackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val SpringPill = spring<Float>(dampingRatio = 1f, stiffness = 400f)  // nav pill
    val SpringHover = spring<Float>(dampingRatio = 1f, stiffness = 350f) // generic hover
}
```

### Hover on Android

Web hover (`onMouseEnter`/`onMouseLeave`) has no direct Compose equivalent on touch devices. For ANI-KUTA:

- Replace hover-reveal patterns (bookmark button, play button on card, scroll arrows on row) with **long-press menu** or **always-visible** equivalents.
- Replace hover popup in Coming-Up-Next with **tap-to-expand** (the template already supports tap via `handleCardClick`).
- Keep the CSS-style card lift for mouse-enabled devices (ChromeOS, external mouse on Android) via `Modifier.pointerInput { detectTapGestures(onPress = …) }` checking pointer type.

### Reduced motion on Android

Use:
```kotlin
val reduceMotion by LocalAccessibilityManager.current
    ?.isReduceMotionEnabled()?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }

if (!reduceMotion) {
    // run animation
}
```
Or globally set `AnimationSpeed` to 0 in `MaterialTheme.motionScheme` when reduced motion is on.
