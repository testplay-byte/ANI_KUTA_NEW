# Motion & Animation Specs — Dark Neon (ANI-KUTA adaptation)

What animates, durations, easings, when to use motion vs. not, and the
anti-patterns. Quoted from `DESIGN.md` §13, §20.

The web design uses **Framer Motion**. ANI-KUTA uses **Compose animation
APIs** (`AnimatedVisibility`, `AnimatedContent`, `animateColorAsState`,
`InfiniteTransition`, etc.). The intent — what moves, how fast, with what
feel — is preserved; the API substrate changes.

---

## 1. Library

> Source: §13.1.

| Platform | Library | Import |
|----------|---------|--------|
| Web (source) | Framer Motion | `import { motion, AnimatePresence } from 'framer-motion'` |
| Compose (ANI-KUTA) | Compose animation | `androidx.compose.animation.*` + `androidx.compose.animation.core.*` |

---

## 2. Page / section transitions

> Source: §13.2.

### What

When the active top-level section changes (e.g. Home → Detail → Library),
the old section fades out and the new section fades in. Both transitions
are 200ms. Wrapped in `AnimatePresence mode="wait"` so the exit completes
before the enter starts.

### Web (source)

```tsx
<AnimatePresence mode="wait">
  {activeSection === 'dashboard' && (
    <motion.div key="dashboard"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}>
      <DashboardSection />
    </motion.div>
  )}
</AnimatePresence>
```

### Compose

```kotlin
AnimatedContent(
    targetState = activeSection,
    transitionSpec = {
        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
    },
    contentKey = { it },
) { section ->
    when (section) {
        Section.Home -> HomeSection()
        Section.Detail -> DetailSection()
        // ...
    }
}
```

Use `Crossfade` if you don't need exit animations — simpler, same effect.

---

## 3. Stagger container

> Source: §13.3.

### What

Cards / list items appear one-by-one with a 50ms stagger. Each item fades
in and rises 8px over 250ms.

### Web (source)

```tsx
const staggerContainer = {
  animate: { transition: { staggerChildren: 0.05 } },
};
const staggerItem = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.25 } },
};

<motion.div variants={staggerContainer} initial="initial" animate="animate">
  <motion.div variants={staggerItem}>Card 1</motion.div>
  <motion.div variants={staggerItem}>Card 2</motion.div>
</motion.div>
```

### Compose

```kotlin
val items = listOf(...)
LazyColumn {
    itemsIndexed(items) { index, item ->
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(index * 50L)
            visible = true
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 4 },
        ) {
            Card(item)
        }
    }
}
```

For ANI-KUTA: stagger the home-page rail entrance — each rail slides up
50ms after the previous.

---

## 4. Modal animation

> Source: §13.4.

### What

Modals scale up from 95% and rise 20px while fading in, with a spring.
Spring params: `damping=25, stiffness=300` — a slightly-underdamped spring
that overshoots just barely and settles fast.

### Web (source)

```tsx
<motion.div
  initial={{ opacity: 0, scale: 0.95, y: 20 }}
  animate={{ opacity: 1, scale: 1, y: 0 }}
  exit={{ opacity: 0, scale: 0.95, y: 20 }}
  transition={{ type: 'spring', damping: 25, stiffness: 300 }}
>
```

### Compose

```kotlin
AnimatedVisibility(
    visible = visible,
    enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) +
            scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), initialScale = 0.95f) +
            slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { 40 },
    exit = fadeOut(...) + scaleOut(...) + slideOutVertically(...),
)
```

`dampingRatio = 0.8f` approximates Framer's `damping=25, stiffness=300`
feel — slight overshoot, quick settle.

---

## 5. Keypad animation

> Source: §13.5.

### Mobile (bottom sheet)

```tsx
<motion.div
  initial={{ y: '100%' }}
  animate={{ y: 0 }}
  exit={{ y: '100%' }}
  transition={{ type: 'spring', damping: 28, stiffness: 300 }}
>
```

Compose:

```kotlin
ModalBottomSheet(
    sheetState = rememberModalBottomSheetState(),
    // Material 3 ModalBottomSheet has its own spring; tune via sheetState
)
```

Or custom:

```kotlin
AnimatedVisibility(
    visible = visible,
    enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { it },
    exit = slideOutVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { it },
)
```

`dampingRatio = 0.9f` matches Framer's `damping=28` (slightly more damped
than the modal — sheets shouldn't bounce).

### Desktop (floating popup)

```tsx
<motion.div
  initial={{ opacity: 0, scale: 0.9, y: 8 }}
  animate={{ opacity: 1, scale: 1, y: 0 }}
  exit={{ opacity: 0, scale: 0.9, y: 8 }}
  transition={{ type: 'spring', damping: 25, stiffness: 300 }}
>
```

Same spring as modal, smaller offsets. For ANI-KUTA: not used (no desktop).

---

## 6. Progress bar animation

> Source: §13.6.

### What

When a progress value changes, the bar's width animates from current to
target over 1000ms with `easeOut`. Used for download progress, watch
progress, episode-progress scrubbing.

### Web (source)

```tsx
<motion.div
  className="h-full rounded-full bg-accent-lime"
  initial={{ width: 0 }}
  animate={{ width: `${percent}%` }}
  transition={{ duration: 1, ease: 'easeOut' }}
/>
```

### Compose

```kotlin
val animatedWidth by animateFloatAsState(
    targetValue = percent / 100f,
    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
)
Box(
    modifier = Modifier
        .fillMaxWidth(animatedWidth)
        .height(4.dp)
        .background(NeonColors.accentLime, CircleShape)
)
```

For ANI-KUTA: episode watch-progress bar in the player; library
"continue watching" progress bars; download progress bars.

---

## 7. Keypress / button press

> Source: §13.7, §13.8.

### Keypress micro-interaction

```tsx
<motion.button whileTap={{ scale: 0.9 }}>
```

Mobile scale = 0.9; desktop popup scale = 0.92 (smaller).

### Button press (CSS)

```html
<button className="active:scale-[0.98] transition-all">
```

### Compose

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val pressed by interactionSource.collectIsPressedAsState()
val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring())
Button(
    onClick = ...,
    interactionSource = interactionSource,
    modifier = Modifier.scale(scale),
) { ... }
```

For ANI-KUTA: apply `scale(0.98f)` on press to every interactive element.
Use `scale(0.9f)` for keypad-style taps where the press is the primary
interaction.

---

## 8. Live / active indicator (pulse)

> Source: §13.9.

### What

A 8×8dp sky dot with an expanding halo (`animate-ping`) — used to signal
"this thing is live / actively updating". Often paired with a small
uppercase "LIVE" label.

### Web (source)

```html
<span className="relative flex h-2 w-2">
  <span className="animate-ping absolute inline-flex h-full w-full rounded-full
    bg-accent-sky opacity-75" />
  <span className="relative inline-flex rounded-full h-2 w-2 bg-accent-sky" />
</span>
<!-- + optional label -->
<span className="text-[9px] font-bold text-accent-sky uppercase tracking-widest">
  LIVE
</span>
```

### Compose

```kotlin
val infinite = rememberInfiniteTransition()
val haloScale by infinite.animateFloat(
    initialValue = 1f, targetValue = 2f,
    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
)
val haloAlpha by infinite.animateFloat(
    initialValue = 0.75f, targetValue = 0f,
    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
)
Box(contentAlignment = Alignment.Center) {
    Box(Modifier.size(8.dp).scale(haloScale).background(NeonColors.accentSky.copy(alpha = haloAlpha), CircleShape))
    Box(Modifier.size(8.dp).background(NeonColors.accentSky, CircleShape))
}
```

For ANI-KUTA: "AIRING NOW" indicator on a detail page; "downloading"
indicator on a library entry; "live stream available" badge. **Use
sparingly** — at most one per screen, otherwise the screen pulses.

---

## 9. Blinking cursor

> Source: §13.10.

### What

A 2×20dp sky bar that pulses opacity 1 ↔ 0.5 every ~2s. Used inside active
text inputs and active keypad displays to signal "this field is focused".

### Web (source)

```html
<span className="inline-block w-[2px] h-5 bg-accent-sky animate-pulse
  ml-0.5 rounded-full" />
```

### Compose

```kotlin
val infinite = rememberInfiniteTransition()
val alpha by infinite.animateFloat(
    initialValue = 1f, targetValue = 0.3f,
    animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
)
Box(Modifier.size(width = 2.dp, height = 20.dp).background(NeonColors.accentSky.copy(alpha = alpha), CircleShape))
```

---

## 10. Animated top border (live preview)

> Source: §13.11.

### What

A 1dp-tall, full-width gradient bar at the top of a "live preview" pane —
the middle of the gradient is sky at 20%, fading to transparent at both
ends. Pulses opacity.

### Web (source)

```html
<div className="absolute top-0 left-0 w-full h-1
  bg-gradient-to-r from-transparent via-accent-sky/20 to-transparent
  animate-pulse" />
```

### Compose

```kotlin
val infinite = rememberInfiniteTransition()
val alpha by infinite.animateFloat(
    initialValue = 1f, targetValue = 0.4f,
    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
)
Box(
    Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to NeonColors.accentSky.copy(alpha = 0.2f * alpha),
                1f to Color.Transparent,
            )
        )
)
```

For ANI-KUTA: top edge of the player view while a stream is preparing —
signals "loading, don't tap away".

---

## 11. Standard transition classes

> Source: §13.12.

| Property | Duration | Web class | Compose spec |
|----------|---------|-----------|--------------|
| Color change | 200ms | `transition-colors` | `animateColorAsState(tween(200))` |
| All properties | 200ms | `transition-all duration-200` | `animateXAsState(tween(200))` for each property |
| Hover effects | 300ms | `transition-all duration-300` | `animateXAsState(tween(300))` |
| Value changes | 200ms | `transition-all duration-200` (on data display) | `animateXAsState(tween(200))` |

### Rule

Every interactive element gets a transition. Never leave a button or input
without one (§20).

### Compose convention

For hover/press/focus state changes, prefer `animateColorAsState(tween(200))`
or `spring()` over raw `Modifier.background(...)`. The animation system
handles interpolation automatically.

---

## 12. Ambient glow orb motion

> Source: §14.3.

### What

Three blurred colored circles drift slowly behind content. Each orb
translates `+10px, -10px` over its half-cycle, then back.

| Orb | Animation |
|-----|-----------|
| Lime | `orb-float 8s ease-in-out infinite` |
| Sky | `orb-float 10s ease-in-out infinite reverse` |
| Coral | static (no motion) |

### Web keyframe

```css
@keyframes orb-float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(10px, -10px); }
}
```

### Compose

```kotlin
val infinite = rememberInfiniteTransition()
val progress by infinite.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
)
val offset = 10.dp * progress   // 0.dp → 10.dp
Box(Modifier.offset(x = offset, y = -offset).size(256.dp).blur(100.dp).background(Lime.copy(alpha = 0.05f)))
```

Note: `Modifier.blur` on Android requires API 31+ for true Gaussian blur.
Below 31, approximate via a pre-rendered blurred bitmap or skip the orbs on
older devices (the visual loss is minor).

---

## 13. Loading spinner (CSS spin)

> Source: §7.6.

### What

A 20dp circular border with one arc accent that rotates 360° infinitely
at 1s linear.

### Compose

Use `CircularProgressIndicator` from Material 3, styled:

```kotlin
CircularProgressIndicator(
    modifier = Modifier.size(20.dp),
    color = NeonColors.bgBase,
    trackColor = NeonColors.bgBase.copy(alpha = 0.3f),
    strokeWidth = 2.dp,
)
```

Or for the small coral variant:

```kotlin
CircularProgressIndicator(
    modifier = Modifier.size(14.dp),
    color = NeonColors.accentCoral,
    trackColor = NeonColors.accentCoral.copy(alpha = 0.3f),
    strokeWidth = 1.dp,
)
```

---

## 14. When to use motion vs. not

### Use motion when

- A section is entering or leaving the viewport (page transition, modal
  open/close, bottom sheet, popup).
- A live value is updating (progress bar, count-up, blinking cursor).
- The user's attention needs to be drawn to a change (ping halo on "new
  episode available", color change on status flip).
- A list is being revealed for the first time (stagger entrance).
- The user is pressing a control (scale 0.98, ripple).

### Do NOT use motion when

- A static value is displayed (no count-up for a score that doesn't change).
- A list re-orders frequently (stagger only on first reveal, not on every
  update — otherwise it jitters).
- The user is mid-scroll (don't animate scroll position; let them scroll).
- A layout property (width/height) would change frequently (causes layout
  thrash — animate transform/opacity instead).
- It would slow the user down. The principle is "animated but not
  distracting" (§1). 200–300ms transitions settle fast; anything longer
  than 300ms for a routine transition is too slow.

---

## 15. Anti-patterns (§20)

### DO

- Use `AnimatePresence` (web) / `AnimatedVisibility` (Compose) for
  enter/exit animations.
- Add `transition-all` or `transition-colors` (web) / `animateAsState`
  (Compose) to all interactive elements.
- Use spring motion for modals and sheets (`damping=25, stiffness=300` or
  Compose `dampingRatio=0.8f, stiffness=Spring.StiffnessMedium`).
- Animate transform (scale/translate) and opacity, not layout.

### DON'T

- Never animate layout properties (width/height) with Framer Motion on
  frequently-updated values. In Compose, the same rule applies: animating
  `Modifier.width(...)` triggers relayout on every frame; use
  `Modifier.fillMaxWidth(animatedFraction)` or transform instead.
- Never leave interactive elements without hover/focus transitions.
- Never use `h-screen` (web) / hardcoded pixel heights (Compose).
- Never loop an animation that doesn't communicate state. The live ping,
  blinking cursor, and ambient orbs are the only intentional loops — and
  each signals "this is live" or "this is the app's idle state".

### Compose-specific notes

- `Modifier.animateContentSize()` is the equivalent of animating width/
  height — useful for occasional layout changes (a card expanding to show
  more), but **never** for values that update frequently (it will thrash).
- `AnimatedContent` is heavier than `Crossfade`. Use `Crossfade` when you
  only need a fade; use `AnimatedContent` when you need different enter/
  exit for different targets.
- `InfiniteTransition` is cheap — multiple `animateFloat`s on the same
  `InfiniteTransition` instance share a single clock. Use one per screen
  for all the pulses.
- Always provide a `contentKey` to `AnimatedContent` to avoid unnecessary
  recomposition.
