# Design 3 — Notebook: Motion & Animation Patterns

Documents every animation in the template: CSS keyframes in `globals.css`, Framer Motion variants in `page.tsx`, transition timings, and the mobile/reduced-motion overrides.

## Two motion layers

1. **CSS keyframes** (`globals.css`) — ambient, always-on effects (skeleton pulse, shimmer, pop-in utility, shake/slide-in utilities). These run via class application, not React state.
2. **Framer Motion** (`page.tsx`) — component-driven, gesture-responsive, lifecycle-aware. Used for entrance animations, hover/tap feedback, hero carousel transitions, popup entrances, sidebar drawer.

Framer Motion is the dominant system; CSS keyframes are reserved for things Framer Motion can't easily do (continuous infinite loops with overlapping opacity + position animations on the same element, like the skeleton).

## Signature easing curve

```ts
cubic-bezier(0.34, 1.56, 0.64, 1)
```

This bezier has a Y value >1 (1.56) — it **overshoots past the target** before settling. Mirrors a spring with medium damping. Used as the default ease for:

- Sidebar drawer open/close (`transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)` in CSS)
- All `neoSlideUp` card entrances (0.25s)
- All `neoSectionPop` section entrances (0.3s)
- Hero text slide-in (0.35s)
- Hero loaded/skeleton swap (0.5s)
- Content section loaded/skeleton swap (0.45s)
- Timeline entry entrance (0.25s)
- Popup entrances (timeline + anime card hover)
- Mobile search button morph (with delay 0.15s)

For Framer Motion spring physics, the equivalent is `dampingRatio ≈ 0.55` (medium-bouncy). The anime card hover popup uses explicit spring values: `stiffness: 400, damping: 18, mass: 0.9` — a slightly less bouncy spring than the bezier (faster settle, smaller overshoot).

For snappy interactions (button press, card press feedback), the template uses plain `ease` at 0.1s for buttons and 0.15s for cards — no overshoot, just a quick linear-ish interpolation.

## CSS keyframes (`globals.css` lines 706–751)

### `neo-pulse` — skeleton opacity pulse

```css
@keyframes neo-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
```

- Duration: 1.2s
- Timing: `ease-in-out`
- Iteration: `infinite`
- Applied to: `.neo-skeleton` (the base surface)
- Effect: gentle breathing — opacity drops to 50% in the middle of each cycle, returns to 100%.

### `neo-shimmer` — skeleton diagonal light sweep

```css
@keyframes neo-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
```

- Duration: 2s
- Timing: `ease-in-out`
- Iteration: `infinite`
- Applied to: `.neo-skeleton::after` (the overlay pseudo-element)
- Effect: a diagonal 105° linear-gradient sweeps across the skeleton from right (200%) to left (-200%), creating the "loading shimmer" effect. Light mode peaks at 18% white; dark mode at 10% white.

The two animations run in parallel on the same element (pulse on the base, shimmer on the overlay) — they have different durations (1.2s vs 2s) so they drift in and out of phase, avoiding a mechanical sync.

### `neo-pop-in` — utility pop-in bounce

```css
@keyframes neo-pop-in {
  0%   { transform: scale(0.8) rotate(-2deg); opacity: 0; }
  60%  { transform: scale(1.05) rotate(0.5deg); opacity: 1; }
  100% { transform: scale(1) rotate(0deg); opacity: 1; }
}

.neo-pop-in {
  animation: neo-pop-in 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}
```

- Duration: 0.35s
- Timing: `cubic-bezier(0.34, 1.56, 0.64, 1)` (spring overshoot)
- Applied to: any element with class `.neo-pop-in`
- Effect: scales up from 80% with a -2° rotation, overshoots to 105% with +0.5° rotation, settles at 100% / 0°. The rotation adds a playful "thrown-on" feel.
- **Note:** Defined and exposed as a utility class but **never actually used** in `page.tsx`. Available for ad-hoc pop-in moments — TODO to find a use or remove.

### `neo-shake` — defined but unused

```css
@keyframes neo-shake {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(-1.5deg); }
  75% { transform: rotate(1.5deg); }
}
```

No class consumes this. Defined as a potential hover-shake utility. TODO.

### `neo-slide-in-right` — defined but unused

```css
@keyframes neo-slide-in-right {
  from { transform: translateX(20px); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
```

No class consumes this. TODO.

### Reduced motion override

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

When the OS reports reduced-motion preference, all CSS animations and transitions are forced to near-instant (0.01ms). Skeletons stop pulsing, hover effects become instantaneous, the sidebar drawer snaps open/closed.

**Note:** This CSS rule does **not** affect Framer Motion animations — those are JS-driven and would need a separate check. The template doesn't wire up a Framer Motion reduced-motion config; reduced-motion users will still see Framer Motion's spring entrances. This is a minor accessibility gap in the source template — ANI-KUTA should check `LocalAccessibilityManager` and gate both CSS *and* Compose animations on it.

## Framer Motion variants (`page.tsx`)

### `neoSlideUp` — staggered card entrance

```ts
const neoSlideUp = {
  hidden: { opacity: 0, y: 24, rotate: -1 },
  visible: (i: number) => ({
    opacity: 1, y: 0, rotate: 0,
    transition: { duration: 0.25, ease: [0.34, 1.56, 0.64, 1], delay: i * 0.05 },
  }),
};
```

- Hidden state: 24px below final position, 1° counter-clockwise rotation, 0% opacity.
- Visible state: at final position, 0° rotation, 100% opacity.
- Per-item stagger: `i * 0.05` (50ms per index).
- Duration: 250ms per item.
- Easing: signature spring overshoot bezier.
- Used by: `AnimeCard`, `GenreCard`.

### `neoSectionPop` — section entrance

```ts
const neoSectionPop = {
  hidden: { opacity: 0, scale: 0.95 },
  visible: {
    opacity: 1, scale: 1,
    transition: { duration: 0.3, ease: [0.34, 1.56, 0.64, 1] },
  },
};
```

- Hidden state: 95% scale, 0% opacity.
- Visible state: 100% scale, 100% opacity.
- Duration: 300ms.
- Easing: spring overshoot.
- No stagger (single-shot per section).
- Used by: `ContentRow`, `GenreSection`, `NextReleasingSection`.

## Per-component motion catalog

### Navbar header — slide-down on mount

```tsx
<motion.div
  initial={{ y: -20, opacity: 0 }}
  animate={{ y: 0, opacity: 1 }}
  transition={{ duration: 0.3, ease: [0.34, 1.56, 0.64, 1] }}
  className="mx-2 sm:mx-3 md:mx-6 mt-3 md:mt-4 relative z-50"
>
```

Slides down 20px from above with fade-in. Spring overshoot at 300ms. Fires once on mount.

### Hero banner — slide+fade in (loaded ↔ skeleton swap)

```tsx
<AnimatePresence mode="wait">
  {dataLoaded ? (
    <motion.div
      key="hero-loaded"
      initial={{ opacity: 0, y: 12, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.5, ease: [0.34, 1.56, 0.64, 1] }}
    >
      <HeroBanner />
    </motion.div>
  ) : (
    <motion.div
      key="hero-skeleton"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.3 }}
    >
      <HeroBannerSkeleton />
    </motion.div>
  )}
</AnimatePresence>
```

- Loaded state: rises 12px + scales from 98% + fades in, 500ms spring.
- Skeleton state: fade in/out only, 300ms linear.
- `AnimatePresence mode="wait"` ensures the skeleton fully exits before the loaded state begins — no overlap.

### Hero background image — cross-fade per slide

```tsx
<AnimatePresence mode="wait">
  <motion.div
    key={`hero-bg-${heroIndex}`}
    initial={{ opacity: 0 }}
    animate={{ opacity: 1 }}
    exit={{ opacity: 0 }}
    transition={{ duration: 0.6, ease: "easeInOut" }}
    className="absolute inset-0"
  >
    <img src={featuredAnime.banner || featuredAnime.image} ... />
  </motion.div>
</AnimatePresence>
```

- Each hero slide is keyed by `heroIndex`.
- Cross-fade between slides: outgoing fades 1→0, incoming fades 0→1, 600ms easeInOut.
- `mode="wait"` ensures clean cross-fade (old fully exits before new begins).
- **Note:** With `mode="wait"` this is technically a "fade-out-then-fade-in" rather than a true cross-fade. The 600ms duration makes it feel like a soft transition.

### Hero text panel — slide-up per slide

```tsx
<AnimatePresence mode="wait">
  <motion.div
    key={`hero-text-${heroIndex}`}
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: -10 }}
    transition={{ duration: 0.35, ease: [0.34, 1.56, 0.64, 1] }}
  >
    {/* title, meta, description, CTA buttons */}
  </motion.div>
</AnimatePresence>
```

- Incoming: slides up 20px + fades in, 350ms spring.
- Outgoing: slides up 10px + fades out (continues upward — directional flow).
- Asymmetric deltas (20 in, 10 out) make forward navigation feel different from backward.
- `mode="wait"` — outgoing exits before incoming enters.

### Content sections (Trending / Freshly / Genre / Popular / Upcoming) — staggered entrance

```tsx
<AnimatePresence mode="wait">
  {dataLoaded ? (
    <motion.div
      key="trending-loaded"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.45, delay: 0, ease: [0.34, 1.56, 0.64, 1] }}
    >
      <ContentRow title="Trending Now" ... />
    </motion.div>
  ) : (
    <motion.div
      key="trending-skeleton"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.25 }}
    >
      <ContentRowSkeleton ... />
    </motion.div>
  )}
</AnimatePresence>
```

Each section has its own `delay` value to create a cascade:
| Section | Delay |
|---|---|
| Trending Now | 0.00s |
| Freshly Updated | 0.08s |
| By Genre | 0.16s |
| Most Popular | 0.24s |
| Coming Up Next | 0.32s |

So sections rise in sequence with 80ms between each — the whole main-content area unfolds in ~800ms after data loads.

### AnimeCard entrance

```tsx
<motion.div
  custom={index}
  variants={neoSlideUp}
  initial={skipAnimation ? "visible" : "hidden"}
  animate={skipAnimation ? "visible" : undefined}
  whileInView={skipAnimation ? undefined : "visible"}
  viewport={skipAnimation ? undefined : { once: true, margin: "-50px" }}
  className="flex-shrink-0 w-[140px] sm:w-[155px] md:w-[168px]"
>
```

- Uses `neoSlideUp` variant with `custom={index}` to drive the 50ms-per-item stagger.
- `whileInView` triggers when the card is 50px into the viewport, once only.
- On mobile (`skipAnimation = isMobile !== false`): the entrance is **skipped** — `initial="visible"` mounts the card in its final state. (See "Mobile animation suppression" below.)

### AnimeCard hover/tap feedback

```tsx
<motion.div
  whileHover={{ translateX: -2, translateY: -2 }}
  whileTap={{ translateX: 3, translateY: 3 }}
  transition={{ duration: 0.1 }}
  className="relative group"
  ref={cardRef}
>
  <div
    className="relative overflow-hidden cursor-pointer neo-card-anime"
    style={{ boxShadow: `5px 5px 0px ${shadowColor}` }}
    onMouseEnter={(e) => {
      (e.currentTarget as HTMLDivElement).style.boxShadow = `8px 8px 0px ${shadowColor}`;
    }}
    onMouseLeave={(e) => {
      (e.currentTarget as HTMLDivElement).style.boxShadow = `5px 5px 0px ${shadowColor}`;
    }}
    onMouseDown={(e) => {
      (e.currentTarget as HTMLDivElement).style.boxShadow = `2px 2px 0px ${shadowColor}`;
    }}
    onMouseUp={(e) => {
      (e.currentTarget as HTMLDivElement).style.boxShadow = `8px 8px 0px ${shadowColor}`;
    }}
  >
```

Two layers of feedback:
1. **Framer Motion** drives the `translate` transform on the wrapper (lift on hover, press on tap), 100ms.
2. **Direct DOM manipulation** (inline `style.boxShadow` change in event handlers) drives the shadow growth/shrink. Why direct DOM? Because Framer Motion can't easily animate `boxShadow` strings with template literals — direct style mutation is faster and more reliable.

The shadow states:
| State | Shadow size |
|---|---|
| Rest | `5px 5px 0 shadowColor` |
| Hover (mouseEnter) | `8px 8px 0 shadowColor` |
| Pressed (mouseDown) | `2px 2px 0 shadowColor` |
| Released (mouseUp) | `8px 8px 0 shadowColor` (back to hover) |
| Leave (mouseLeave) | `5px 5px 0 shadowColor` (back to rest) |

### GenreCard hover/tap

```tsx
<motion.div
  whileHover={{ translateX: -2, translateY: -2 }}
  whileTap={{ translateX: 2, translateY: 2 }}
  transition={{ duration: 0.1 }}
>
```

Same lift-press model. Note: genre cards use **2px** press translation (not 3px like anime cards) — smaller because the cards are smaller.

### AnimeCard hover popup — spring entrance

```tsx
{createPortal(
  <AnimatePresence>
    {showPopup && (
      <motion.div
        initial={{ opacity: 0, scaleY: 0.3, scaleX: 0.85 }}
        animate={{ opacity: 1, scaleY: 1, scaleX: 1 }}
        exit={{ opacity: 0, scaleY: 0.3, scaleX: 0.85 }}
        transition={{
          type: "spring",
          stiffness: 400,
          damping: 18,
          mass: 0.9,
        }}
        className="fixed z-[9999] w-[260px] neo-card-anime overflow-hidden anime-card-popup pointer-events-auto"
        style={{
          top: `${popupStyle.top}px`,
          left: `${popupStyle.left}px`,
          boxShadow: `6px 6px 0px ${shadowColor}`,
          transformOrigin: popupStyle.origin,
        }}
      >
```

- Asymmetric scale-in: Y scales from 30% (much smaller than X's 85%) — the popup appears to "grow upward" from a thin slit, like unfolding.
- `transformOrigin` is dynamic: `bottom center` if popup is above the card, `top center` if below.
- Spring: `stiffness 400, damping 18, mass 0.9` — feels like a punchy card-flip. Higher stiffness than typical springs (default is ~100), lower damping (less wobble), small mass (snappier).
- Popup is portaled to `document.body`, `position: fixed`, `z-[9999]`.
- Card hover popup is shown only after the pointer **stops moving for 300ms** on the card (see "Hover pause detection" below).

### Timeline entry entrance

```tsx
<motion.div
  initial={{ opacity: 0, x: -12 }}
  whileInView={{ opacity: 1, x: 0 }}
  viewport={{ once: true }}
  transition={{ duration: 0.25, ease: [0.34, 1.56, 0.64, 1], delay: actualIndex * 0.04 }}
>
```

- Slides in horizontally from the left (12px offset), fades in.
- 40ms stagger per entry (faster than cards' 50ms — denser list).
- 250ms spring.

### Timeline entry hover/tap

```tsx
<motion.div
  whileHover={{ translateX: -1, translateY: -1 }}
  whileTap={{ translateX: 2, translateY: 2 }}
  transition={{ duration: 0.1 }}
  className="neo-card flex items-center gap-2.5 ... grayscale transition-all duration-200 ease-out hover:grayscale-0 ${grayscaleFilter}"
>
```

- Smaller hover/press deltas (±1 / +2) — entries are smaller than cards.
- Grayscale treatment: `grayscale-0` (Today) / `grayscale-[50%]` (Tomorrow) / `grayscale` (Later). Hover clears grayscale via `hover:grayscale-0`. The grayscale transition runs over 200ms (longer than the lift) so the color "comes in" as the card rises.

### Timeline popup

```tsx
<motion.div
  initial={{ opacity: 0, scale: 0.95, y: hoverPlacement === "above" ? 8 : -8 }}
  animate={{ opacity: 1, scale: 1, y: 0 }}
  exit={{ opacity: 0, scale: 0.95, y: hoverPlacement === "above" ? 8 : -8 }}
  transition={{ duration: 0.2, ease: [0.34, 1.56, 0.64, 1] }}
  className="fixed z-[100] w-[300px] sm:w-[340px] md:w-[380px] neo-card-blue overflow-y-auto max-h-[50vh] no-scrollbar next-popup-panel"
>
```

- 200ms spring, scale from 95%.
- Y-offset is directional: if popup is above the entry, initial Y = +8 (slides up into place); if below, initial Y = -8 (slides down into place).
- Smaller spring than the card hover popup (200ms vs spring physics) — feels snappier because timeline entries are smaller.

### Sidebar drawer

The sidebar uses CSS transitions (not Framer Motion) for the slide animation:

```css
.neo-sidebar {
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
```

```tsx
<aside
  className={`fixed top-0 left-0 h-dvh lg:h-screen z-[80] neo-sidebar flex flex-col overflow-hidden transition-transform duration-300
    ${isOpen ? "translate-x-0" : "-translate-x-full"} lg:translate-x-0`}
  style={{ width: `${width}px` }}
>
```

- Slides from `translate-x-full (-100%)` to `translate-x-0` (visible).
- 300ms spring.
- On lg+, `lg:translate-x-0` keeps it always visible.

When the sidebar is being drag-resized, the width changes directly via state — the same `transition: transform` CSS rule applies a smooth width transition too (because `transition-transform` Tailwind class only transitions `transform`, but the `.neo-sidebar` rule transitions `transform` only — width changes are instantaneous during drag, which is what you want).

### Mobile sidebar overlay

```tsx
<AnimatePresence>
  {isOpen && (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
      className="fixed inset-0 z-[70] neo-sidebar-overlay lg:hidden"
      onClick={onClose}
    />
  )}
</AnimatePresence>
```

- Fade in 200ms.
- `AnimatePresence` ensures the exit fade completes before unmount.
- The overlay has `backdrop-filter: blur(4px)` (CSS, always on when mounted).

### Hamburger ↔ close icon morph (mobile search focus)

When mobile search is focused, the hamburger button collapses:

```tsx
<motion.button
  onClick={() => setSidebarOpen(!sidebarOpen)}
  className={`w-9 h-9 ... neo-nav-red whitespace-nowrap ${sidebarOpen ? "!bg-destructive !text-primary-foreground" : ""}`}
  animate={{ width: searchFocused ? 0 : 36, opacity: searchFocused ? 0 : 1, borderWidth: searchFocused ? 0 : 2 }}
  transition={{ duration: 0.2, ease: [0.4, 0, 0.2, 1] }}
  style={{ pointerEvents: searchFocused ? "none" : "auto" }}
>
  {sidebarOpen ? <X className="w-4 h-4" /> : <Menu className="w-4 h-4" />}
</motion.button>
```

- Width animates from 36px → 0, opacity 1 → 0, border 2 → 0.
- 200ms with `cubic-bezier(0.4, 0, 0.2, 1)` (Material's standard ease — a different curve from the spring, used because this is a UI affordance, not an entrance).
- When search is blurred, the same animation reverses.

### Bell ↔ search-submit morph (mobile)

When mobile search is focused, the bell button morphs into a search-submit button:

```tsx
<AnimatePresence mode="wait">
  {!searchFocused ? (
    <motion.button
      key="bell"
      exit={{ scale: 0.6, opacity: 0 }}
      transition={{ duration: 0.15 }}
      className="flex relative w-9 h-9 ... neo-nav-red"
    >
      <Bell className="w-4 h-4" />
      <span className="absolute -top-1 -right-1 w-3 h-3 bg-primary rounded-[3px] border-2 border-foreground" />
    </motion.button>
  ) : (
    <motion.button
      key="search-btn"
      initial={{ scale: 0.6, opacity: 0, width: 36 }}
      animate={{ scale: 1, opacity: 1, width: 88 }}
      exit={{ scale: 0.6, opacity: 0, width: 36 }}
      transition={{ duration: 0.25, delay: 0.15, ease: [0.34, 1.56, 0.64, 1] }}
      className="flex relative h-9 rounded-lg bg-primary border-[3px] border-foreground ... overflow-hidden whitespace-nowrap"
      style={{ boxShadow: "3px 3px 0px var(--neo-shadow-red)" }}
    >
      <Search className="w-3.5 h-3.5 flex-shrink-0" />
      <span>Search</span>
    </motion.button>
  )}
</AnimatePresence>
```

- `AnimatePresence mode="wait"` — bell exits first (150ms shrink+fade), then search button enters (250ms with 150ms delay → effectively starts as bell exits).
- Search button animates from 36px width → 88px width (growing to fit the "Search" text) + scales from 60% → 100% + fades in.
- Spring overshoot at 250ms — feels punchy.
- `overflow: hidden` + `whitespace: nowrap` clips the "Search" text during the width animation.

### Hero CTA buttons

```tsx
<motion.button
  whileHover={{ translateX: -1, translateY: -1 }}
  whileTap={{ translateX: 2, translateY: 2 }}
  className="neo-btn-blue flex items-center gap-1.5 px-4 py-2 bg-primary text-primary-foreground font-black uppercase text-xs hover:bg-blue-500"
>
  <Play className="w-3 h-3 fill-current" />
  Watch Now
</motion.button>
```

Smaller hover/press deltas than cards (±1 / +2 vs ±2 / +3). 100ms transition. The shadow growth is handled by the `.neo-btn-blue` CSS class (5px → 5px on hover, 1px on active), not Framer Motion.

### Carousel dots — CSS only

```tsx
<button
  onClick={() => setHeroIndex(i)}
  className={`transition-all duration-200 border-2 border-foreground ${
    i === heroIndex
      ? "w-8 h-3 bg-primary rounded-[3px]"
      : "w-3 h-3 bg-muted-foreground/30 hover:bg-muted-foreground/50 rounded-[3px]"
  }`}
  style={i === heroIndex ? { boxShadow: "2px 2px 0px var(--neo-shadow-blue)" } : {}}
/>
```

Pure CSS transition: 200ms `all` — animates width (3 → 8), background color, and box-shadow (none → 2px blue). No Framer Motion.

### Scroll arrows — CSS only

```tsx
<button
  className={[
    "w-9 h-9 sm:w-10 sm:h-10 rounded-[6px]",
    "bg-card border-[2.5px] border-foreground",
    "active:translate-x-[2px] active:translate-y-[2px] active:shadow-[2px_2px_0px_var(--neo-shadow)]",
    "transition-all duration-150",
    "opacity-0 sm:opacity-0",
    "sm:group-hover/row:opacity-100",
    "hover:opacity-100",
  ].join(" ")}
  style={{ boxShadow: `4px 4px 0px var(--neo-shadow)` }}
>
```

CSS-only:
- Opacity transition: 0 → 100% on row hover, 150ms.
- Active state: `translate(2px, 2px)` + shadow shrinks from `4px 4px 0 black` to `2px 2px 0 black`. Same press model as `.neo-btn`.

### Edge fade gradients — CSS only

```tsx
<div
  className="absolute right-0 top-0 bottom-0 w-10 sm:w-14 md:w-20 bg-gradient-to-l from-background via-background/80 to-transparent pointer-events-none z-10"
  style={{
    opacity: showRightBlur ? 1 : 0,
    transition: "opacity 300ms ease-out",
  }}
/>
```

Opacity fade-in/out, 300ms ease-out, driven by React state (`canScrollRight` / `canScrollLeft` from `useHorizontalScroll` hook).

## Hover pause detection

Two components (AnimeCard popup, timeline popup) use a **pointer-stillness detection** pattern — the popup only appears after the pointer has been stationary on the entry for 300ms. This avoids popups firing during scrolling or fast mouse movement.

```tsx
const stillnessTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
const popupTriggeredRef = useRef(false);  // once triggered, popup stays until leave

const handleCardMouseMove = useCallback(() => {
  if ("ontouchstart" in window) return;  // skip on touch devices
  if (hideTimeout.current) clearTimeout(hideTimeout.current);
  if (exitTimeout.current) { clearTimeout(exitTimeout.current); exitTimeout.current = undefined; }
  if (popupTriggeredRef.current) return;  // already triggered, keep showing

  if (stillnessTimerRef.current) clearTimeout(stillnessTimerRef.current);

  stillnessTimerRef.current = setTimeout(() => {
    requestTransition(() => {
      if (!popupTriggeredRef.current) {
        popupTriggeredRef.current = true;
        updatePopupPos();
        setShowPopup(true);
      }
    });
  }, 300);
}, [updatePopupPos, requestTransition]);
```

The pattern:
1. On mouse move, clear any pending stillness timer and start a new 300ms timer.
2. If the timer fires (pointer hasn't moved for 300ms), trigger the popup.
3. Once triggered, `popupTriggeredRef = true` — further mouse moves don't re-trigger.
4. On mouse leave, clear the stillness timer + start a 250ms exit delay (allows user to move into the popup itself without dismissing it).
5. On touch devices (`"ontouchstart" in window`), the whole pattern is skipped — touch users tap to toggle the popup instead.

A `CardHoverContext` ensures only one popup is visible at a time across all cards. When a new card requests hover, the previously-active card's popup is dismissed first (350ms exit animation), then the new one shows.

## Mobile animation suppression

The `useIsMobile()` hook returns `true` when `window.innerWidth < 640`. Components check this and **skip entrance animations on mobile**:

```tsx
const mobileCheck = useIsMobile();
const skipAnimation = mobileCheck !== false;  // undefined or true = skip
// ...
<motion.div
  variants={neoSlideUp}
  initial={skipAnimation ? "visible" : "hidden"}
  animate={skipAnimation ? "visible" : undefined}
  whileInView={skipAnimation ? undefined : "visible"}
  viewport={skipAnimation ? undefined : { once: true, margin: "-50px" }}
>
```

When `skipAnimation` is true:
- `initial="visible"` — mounts in the final state.
- `animate` is undefined — no transition.
- `whileInView` is undefined — no scroll trigger.
- `viewport` is undefined — no observer.

Result: on mobile, cards appear instantly with no entrance animation. Reasoning in code comments: scroll-triggered animations on mobile fire unreliably and feel janky; static-on-mount is smoother.

The same pattern applies to: `AnimeCard`, `ContentRow`, `GenreSection`, `GenreCard`, `NextReleasingSection`. Section-level animations are also skipped.

**Important:** This skips *entrance* animations only — hover/tap feedback (`whileHover`, `whileTap`) still works on mobile (where it manifests as touch-down/up feedback).

## Compose motion mapping

### Signature spring

```kotlin
import androidx.compose.animation.core.*

val NeoSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,  // ~0.56 (overshoot)
    stiffness = Spring.StiffnessMediumLow,           // ~400
    visibilityThreshold = 0.001f,
)

// For the popup-specific spring (stiffness 400, damping 18, mass 0.9):
val NeoPopupSpring = spring<Float>(
    dampingRatio = 0.55f,
    stiffness = 1500f,
)
```

### Entrance animations

Use `AnimatedVisibility` with `enter` spec:

```kotlin
AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn(spring()) + slideInVertically(spring()) { it / 4 },
    exit = fadeOut(spring()) + slideOutVertically(spring()) { -it / 8 },
) {
    AnimeCard(...)
}
```

For staggered entrances, use `LaunchedEffect` + `delay(index * 50L)` to flip each item's visible flag.

### Hover/tap lift

```kotlin
val interaction = remember { MutableInteractionSource() }
val isPressed by interaction.collectIsPressedAsState()
val isHovered by interaction.collectIsHoveredAsState()

val translate by animateFloatAsState(
    targetValue = when {
        isPressed -> 3f
        isHovered -> -2f
        else -> 0f
    },
    animationSpec = tween(100),
)
val shadowOffset by animateFloatAsState(
    targetValue = when {
        isPressed -> 1f
        isHovered -> 8f
        else -> 4f
    },
    animationSpec = tween(150),
)

Box(
    Modifier
        .graphicsLayer {
            translationX = translate
            translationY = translate
        }
        .drawBehind {
            drawRect(
                color = shadowColor,
                topLeft = Offset(shadowOffset, shadowOffset),
                size = size,
            )
        }
        .background(cardColor, RoundedCornerShape(10.dp))
        .border(3.dp, borderColor, RoundedCornerShape(10.dp))
        .clickable(interactionSource = interaction, indication = null) { /* ... */ }
)
```

### Reduced motion

```kotlin
val accessibilityManager = LocalAccessibilityManager.current
val reduceMotion = accessibilityManager?.let { false } ?: false  // TODO: check actual API

// Or check via LocalContext:
val context = LocalContext.current
val reduceMotion = Settings.Global.getFloat(
    context.contentResolver,
    Settings.Global.ANIMATOR_DURATION_SCALE,
    1f,
) == 0f
```

When `reduceMotion` is true, replace all `spring()` and `tween()` specs with `snap()` (instant).

### Mobile suppression

Use `WindowSizeClass`:

```kotlin
val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
val isMobile = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

AnimatedVisibility(
    visible = isVisible,
    enter = if (isMobile) { fadeIn(snap()) } else { fadeIn(spring()) + slideInVertically(spring()) { it / 4 } },
)
```

Or simpler — just skip `AnimatedVisibility` entirely on compact width and render children directly.

### Skeleton pulse + shimmer

Use `rememberInfiniteTransition`:

```kotlin
val infinite = rememberInfiniteTransition()
val pulse by infinite.animateFloat(
    initialValue = 1f,
    targetValue = 0.5f,
    animationSpec = infiniteRepeatable(
        animation = tween(1200, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
    ),
)
val shimmer by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(2000, easing = LinearEasing),
    ),
)

Box(
    Modifier
        .background(mutedColor.copy(alpha = pulse))
        .drawWithContent {
            drawContent()
            val sweepX = lerp(-0.5f, 1.5f, shimmer)  // -50% to 150% of width
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                    start = Offset(size.width * sweepX - size.width * 0.1f, 0f),
                    end = Offset(size.width * sweepX + size.width * 0.1f, size.height),
                ),
            )
        }
)
```

## Animation timing summary table

| Animation | Duration | Easing | Trigger |
|---|---|---|---|
| Skeleton pulse | 1200ms (loop) | ease-in-out | mount (CSS) |
| Skeleton shimmer | 2000ms (loop) | ease-in-out | mount (CSS) |
| Sidebar drawer | 300ms | spring bezier | open/close state |
| Sidebar overlay | 200ms | linear | open/close state |
| Navbar slide-down | 300ms | spring bezier | mount |
| Hero loaded↔skeleton swap | 500ms (loaded) / 300ms (skeleton) | spring / linear | data load |
| Hero bg cross-fade | 600ms | easeInOut | hero index change |
| Hero text slide | 350ms | spring bezier | hero index change |
| Section entrance (cascade) | 450ms each, 80ms stagger | spring bezier | data load |
| AnimeCard entrance | 250ms + 50ms stagger | spring bezier | whileInView |
| AnimeCard hover | 100ms | ease (linear) | hover state |
| AnimeCard tap | 100ms | ease (linear) | pressed state |
| Card hover popup | spring (~250ms) | spring (400/18/0.9) | 300ms pointer stillness |
| Timeline entry entrance | 250ms + 40ms stagger | spring bezier | whileInView |
| Timeline entry hover | 100ms | ease | hover state |
| Timeline popup | 200ms | spring bezier | hover pause or click |
| GenreCard entrance | 250ms + 50ms stagger | spring bezier | whileInView |
| GenreCard hover | 100ms | ease | hover state |
| Mobile hamburger collapse | 200ms | `cubic-bezier(0.4,0,0.2,1)` | search focus |
| Mobile bell→search morph | 250ms + 150ms delay | spring bezier | search focus |
| Carousel dot | 200ms | all (linear-ish) | hero index change |
| Scroll arrow opacity | 150ms | linear | row hover |
| Edge fade gradient | 300ms | ease-out | scroll position |
| Neo-btn hover/active | 100ms | ease | hover/active state |
| Neo-card hover/active | 150ms | ease | hover/active state |
| Neo-input focus | 150ms | ease | focus state |
| Neo-skeleton shimmer | 2000ms (loop) | ease-in-out | mount (CSS) |
| Neo-pop-in utility | 350ms | spring bezier | class application (unused) |
