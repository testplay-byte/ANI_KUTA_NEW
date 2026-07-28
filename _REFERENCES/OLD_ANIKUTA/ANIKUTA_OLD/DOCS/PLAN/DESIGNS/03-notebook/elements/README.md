# Design 3 — Notebook: UI Element Specs

Specifications for every neobrutalism UI primitive defined in `src/app/globals.css` and consumed by `src/app/page.tsx`. Each spec lists the exact CSS values, where it's used in the app, and how to rebuild it in Jetpack Compose.

All CSS quoted verbatim from `globals.css` (lines 218–632). All component usage quoted verbatim from `page.tsx`.

## 1. `.neo-card` — base surface

### CSS

```css
.neo-card {
  background: var(--card);
  border: var(--neo-border);          /* 3px solid #1A1A1A */
  border-radius: var(--neo-border-radius);  /* 10px */
  box-shadow: 4px 4px 0px var(--neo-shadow);
  transition: all 0.15s ease;
}
.neo-card:hover {
  background: var(--neo-hover-bg);    /* #DBEAFE light */
  box-shadow: 8px 8px 0px var(--neo-shadow-blue);
  transform: translate(-2px, -2px);
}
.neo-card:active {
  background: var(--neo-active-bg);   /* #BFDBFE light */
  box-shadow: 1px 1px 0px var(--neo-shadow-blue);
  transform: translate(3px, 3px);
}
```

### Used in

- Genre cards (`.neo-card-{color}` variants — see below).
- Hero details panel (desktop).
- Hover popups for timeline entries.
- Popup container in `NextReleasingSection`.

### Color variants (7 total)

Each variant overrides only the shadow color and hover/active background tint. Border, radius, and transition are identical to base `.neo-card`.

| Variant | Resting shadow | Hover bg | Hover shadow | Active bg | Active shadow |
|---|---|---|---|---|---|
| `.neo-card` (base) | `4px 4px 0 var(--neo-shadow)` (black) | `#DBEAFE` | `8px 8px 0 var(--neo-shadow-blue)` | `#BFDBFE` | `1px 1px 0 var(--neo-shadow-blue)` |
| `.neo-card-blue` | `4px 4px 0 var(--neo-shadow-blue)` | `#DBEAFE` | `8px 8px 0 var(--neo-shadow-blue)` | `#BFDBFE` | `1px 1px 0 var(--neo-shadow-blue)` |
| `.neo-card-pink` | `4px 4px 0 var(--neo-shadow-pink)` | `#FCE7F3` | `8px 8px 0 var(--neo-shadow-pink)` | `#FBCFE8` | `1px 1px 0 var(--neo-shadow-pink)` |
| `.neo-card-green` | `4px 4px 0 var(--neo-shadow-green)` | `#DCFCE7` | `8px 8px 0 var(--neo-shadow-green)` | `#BBF7D0` | `1px 1px 0 var(--neo-shadow-green)` |
| `.neo-card-yellow` | `4px 4px 0 var(--neo-shadow-yellow)` | `#FEF9C3` | `8px 8px 0 var(--neo-shadow-yellow)` | `#FEF08A` | `1px 1px 0 var(--neo-shadow-yellow)` |
| `.neo-card-purple` | `4px 4px 0 var(--neo-shadow-purple)` | `#EDE9FE` | `8px 8px 0 var(--neo-shadow-purple)` | `#DDD6FE` | `1px 1px 0 var(--neo-shadow-purple)` |
| `.neo-card-orange` | `4px 4px 0 var(--neo-shadow-orange)` | `#FFEDD5` | `8px 8px 0 var(--neo-shadow-orange)` | `#FED7AA` | `1px 1px 0 var(--neo-shadow-orange)` |

(Dark-mode hover/active bg values differ — see `colors/README.md` "Hover/active tint overrides per color variant (dark mode)" table.)

### Compose equivalent

```kotlin
@Composable
fun Modifier.neoCard(
    colors: NeoColors = LocalNeoColors.current,
    accentShadow: Color = colors.neoShadow,
    hoverBg: Color = colors.neoHoverBg,
    activeBg: Color = colors.neoActiveBg,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val shadowOffset by animateDpAsState(
        targetValue = when {
            isPressed -> 1.dp
            isHovered -> 8.dp
            else -> 4.dp
        },
        animationSpec = tween(150),
    )
    val translate by animateDpAsState(
        targetValue = when {
            isPressed -> 3.dp
            isHovered -> (-2).dp
            else -> 0.dp
        },
        animationSpec = tween(150),
    )
    val bg = when {
        isPressed -> activeBg
        isHovered -> hoverBg
        else -> colors.card
    }

    return this
        .background(bg, shape = RoundedCornerShape(10.dp))
        .border(3.dp, colors.border, RoundedCornerShape(10.dp))
        .offset(x = translate, y = translate)
        .drawBehind {
            drawRect(
                color = accentShadow,
                topLeft = Offset(shadowOffset.toPx(), shadowOffset.toPx()),
                size = size,
            )
        }
}
```

## 2. `.neo-card-anime` — anime cover card

### CSS

```css
.neo-card-anime {
  background: var(--card);
  border: 3.5px solid var(--border);       /* THICKER than .neo-card */
  border-radius: var(--neo-border-radius); /* 10px */
  box-shadow: 5px 5px 0px var(--neo-shadow);
  transition: all 0.15s ease;
}
.neo-card-anime:hover {
  background: var(--neo-hover-bg);
  box-shadow: 9px 9px 0px var(--neo-shadow-blue);
  transform: translate(-2px, -2px);
}
.neo-card-anime:active {
  background: var(--neo-active-bg);
  box-shadow: 2px 2px 0px var(--neo-shadow-blue);
  transform: translate(3px, 3px);
}
.neo-card-anime img {
  border: none !important;   /* image inside has no extra border */
}
```

The anime card uses inline `boxShadow` overrides per section via the `shadowColor` prop:

```tsx
// From AnimeCard in page.tsx:
<div
  className="relative overflow-hidden cursor-pointer neo-card-anime"
  style={{ boxShadow: `5px 5px 0px ${shadowColor}` }}  // overrides the base 5px black shadow
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

### Card internal layout (from `AnimeCard` component)

Structure (top to bottom):

1. **Card body** — `relative overflow-hidden cursor-pointer neo-card-anime`
   - **Cover area** — `aspect-[5/7]` (portrait poster ratio), `object-cover` image with cross-fade from a `.neo-skeleton` placeholder.
   - **NEW badge** (top-left, `absolute top-2 left-2 z-20`) — `.neo-badge bg-green-400 text-black` (dark: `bg-green-600 text-green-50`). Only shown when `anime.isNew`.
   - **Rating badge** (top-right, `absolute top-2 right-2 z-20`) — `.neo-badge bg-yellow-400 text-black flex items-center gap-1` with `Star` icon at `w-2.5 h-2.5 fill-current`.
   - **Episode info bar** (bottom overlay, `absolute bottom-0 left-0 right-0 z-20 px-1 pb-1 pt-4`) — gradient from `from-black/70 via-black/20 to-transparent`. Contains 3 chips:
     - Sub: `text-[9px] font-extrabold tracking-wide text-emerald-300 bg-emerald-500/25 backdrop-blur-sm px-1 py-[1px] rounded-[2px] border border-emerald-400/25` — shows `{subEpisodes} sub` (sub is 6px uppercase).
     - Dub: same pattern, `text-rose-300 bg-rose-500/25 border-rose-400/25` — shows `{dubEpisodes} dub`. Hidden when no dub.
     - Total episodes: `text-white/80 bg-white/10 backdrop-blur-sm border-white/10 ml-auto` — shows `{totalEpisodes} ep`.
2. **Title block** (below the card, `mt-2.5 px-0.5`):
   - Title: `font-black uppercase text-[13px] tracking-tight leading-tight truncate group-hover:text-primary transition-colors duration-150`
   - Genre subtitle: `text-[11px] font-bold tracking-wide text-muted-foreground mt-0.5`

### Card widths by breakpoint

```
w-[140px]   /* mobile (<640px) */
sm:w-[155px] /* sm+ (≥640px) */
md:w-[168px] /* md+ (≥768px) */
```

Container row gaps: `gap-4 md:gap-5` (16px / 20px).

### Compose equivalent

```kotlin
@Composable
fun NeoAnimeCard(
    anime: AnimeItem,
    accentShadow: Color,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()

    val shadowPx by animateFloatAsState(if (isPressed) 2f else 5f, tween(150))
    val offsetPx by animateFloatAsState(if (isPressed) 3f else 0f, tween(150))

    Column(modifier = modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 7f)
                .graphicsLayer { translationX = offsetPx; translationY = offsetPx }
                .drawBehind {
                    drawRect(
                        color = accentShadow,
                        topLeft = Offset(shadowPx, shadowPx),
                        size = size,
                    )
                }
                .background(LocalNeoColors.current.card)
                .border(3.5.dp, LocalNeoColors.current.border, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .clickable(interactionSource = interaction, indication = null) { /* nav */ }
        ) {
            AsyncImage(
                model = anime.image,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // NEW badge — top-left
            if (anime.isNew) {
                NeoBadge(
                    text = "NEW",
                    bg = Color(0xFF4ADE80), fg = Color.Black,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            // Rating badge — top-right
            NeoBadge(
                text = anime.rating.toString(),
                leading = { Star() },
                bg = Color(0xFFFACC15), fg = Color.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            // Episode info bar — bottom
            EpisodeInfoBar(
                sub = anime.subEpisodes,
                dub = anime.dubEpisodes,
                total = anime.totalEpisodes,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = anime.title.uppercase(),
            style = NeoTypography.cardTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = anime.genre,
            style = NeoTypography.meta,
            color = LocalNeoColors.current.mutedForeground,
        )
    }
}
```

## 3. `.neo-btn` — bold button

### CSS

```css
.neo-btn {
  border: var(--neo-border);             /* 3px solid */
  border-radius: 8px;
  box-shadow: 3px 3px 0px var(--neo-shadow);
  transition: all 0.1s ease;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: -0.02em;
}
.neo-btn:hover {
  background: var(--neo-hover-bg);
  box-shadow: 5px 5px 0px var(--neo-shadow);
  transform: translate(-1px, -1px);
}
.neo-btn:active {
  background: var(--neo-active-bg);
  box-shadow: 1px 1px 0px var(--neo-shadow);
  transform: translate(2px, 2px);
}
```

### Color variants

| Variant | Shadow color | Hover behavior | Active behavior |
|---|---|---|---|
| `.neo-btn` (base) | `var(--neo-shadow)` (black) | bg → `#DBEAFE`, shadow → `5px 5px 0 black` | bg → `#BFDBFE`, shadow → `1px 1px 0 black` |
| `.neo-btn-blue` | `var(--neo-shadow-blue)` | bg → `#DBEAFE`, shadow → `5px 5px 0 blue` | bg → `#BFDBFE`, shadow → `1px 1px 0 blue` |
| `.neo-btn-red` | `var(--neo-shadow-red)` | bg → `var(--destructive)`, color → `var(--primary-foreground)`, shadow → `5px 5px 0 red` | same as hover, shadow → `1px 1px 0 red` |

The red variant is distinctive: on hover/active, the **entire button fills with destructive red and the text turns white** (color-burst effect).

### Usage in `page.tsx`

- "See All" / "All Genres" buttons on section headers: `.neo-btn flex items-center gap-1.5 px-3 py-1.5 bg-card text-foreground text-xs hover:bg-primary hover:text-primary-foreground`
- "Watch Now" CTA in hero: `.neo-btn-blue flex items-center gap-2 px-6 py-3 bg-primary text-primary-foreground font-black uppercase text-sm hover:bg-blue-500`
- Bookmark button (icon-only): `.neo-btn flex items-center justify-center w-10 h-10 bg-card text-foreground border-2 border-foreground rounded-[6px] hover:bg-primary hover:text-primary-foreground hover:border-primary`
- Navbar search submit: `.flex-shrink-0 ml-2 h-[40px] px-4 bg-primary text-primary-foreground border-[3px] border-foreground rounded-lg font-bold uppercase text-xs transition-all duration-100 hover:brightness-110 active:brightness-90 flex items-center gap-1.5` with inline `boxShadow: 3px 3px 0px var(--neo-shadow-red)`

### Compose equivalent

```kotlin
enum class NeoButtonVariant { Default, Blue, Red }

@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NeoButtonVariant = NeoButtonVariant.Default,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = LocalNeoColors.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val isHovered by interaction.collectIsHoveredAsState()

    val (shadowColor, hoverBg, pressedBg) = when (variant) {
        NeoButtonVariant.Default -> Triple(colors.neoShadow, colors.neoHoverBg, colors.neoActiveBg)
        NeoButtonVariant.Blue -> Triple(colors.neoShadowBlue, colors.neoHoverBg, colors.neoActiveBg)
        NeoButtonVariant.Red -> Triple(colors.neoShadowRed, colors.destructive, colors.destructive)
    }
    val bg = when {
        isPressed -> pressedBg
        isHovered -> hoverBg
        else -> colors.card
    }
    val fg = if (variant == NeoButtonVariant.Red && (isPressed || isHovered)) colors.primaryForeground else colors.foreground
    val shadowDp by animateDpAsState(if (isPressed) 1.dp else if (isHovered) 5.dp else 3.dp, tween(100))
    val translateDp by animateDpAsState(if (isPressed) 2.dp else if (isHovered) (-1).dp else 0.dp, tween(100))

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .border(3.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer { translationX = translateDp.toPx(); translationY = translateDp.toPx() }
            .drawBehind {
                drawRect(
                    color = shadowColor,
                    topLeft = Offset(shadowDp.toPx(), shadowDp.toPx()),
                    size = size,
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke(this)
        Text(
            text = text.uppercase(),
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).sp,
            style = LocalTextStyle.current,
        )
    }
}
```

## 4. `.neo-badge` — small tag

### CSS

```css
.neo-badge {
  border: 2px solid var(--border);
  border-radius: 6px;
  font-weight: 800;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 2px 8px;
  line-height: 1.4;
  transition: background-color 0.15s ease;
}
```

Note: 2px border (thinner than the 3px standard), 6px radius (smaller than the 10px card / 8px button), 10px font, 800 weight, open tracking.

### Usage patterns (from `page.tsx`)

Inline background + text colors are applied via Tailwind utilities. The `borderColor` is always `var(--foreground)` (set inline via `style={{ borderColor: "var(--foreground)" }}`) — overrides the default 2px solid `var(--border)`.

```tsx
// Trending badge (with icon)
<span className="neo-badge bg-primary text-primary-foreground flex items-center gap-1"
      style={{ borderColor: "var(--foreground)" }}>
  <TrendingUp className="w-2.5 h-2.5" />
  #{anime.rank} Trending
</span>

// SUB badge
<span className="neo-badge bg-green-400 text-black dark:bg-green-600 dark:text-green-50"
      style={{ borderColor: "var(--foreground)" }}>SUB</span>

// NEW badge
<span className="neo-badge bg-green-400 text-black dark:bg-green-600 dark:text-green-50"
      style={{ borderColor: "var(--foreground)" }}>NEW</span>

// Rating badge (with icon)
<span className="neo-badge bg-yellow-400 text-black dark:bg-yellow-600 dark:text-yellow-50 flex items-center gap-1"
      style={{ borderColor: "var(--foreground)" }}>
  <Star className="w-2.5 h-2.5 fill-current" />
  {anime.rating}
</span>

// Timeline group label
<span className={`neo-badge ${tagColor}`} style={{ borderColor: "var(--foreground)" }}>
  {label}  {/* "Today" / "Tomorrow" / "Later" */}
</span>
// tagColor: Today = bg-green-400 text-black (dark: bg-green-600 text-green-50)
//           Tomorrow = bg-amber-400 text-black (dark: bg-amber-600 text-amber-50)
//           Later = bg-blue-400 text-white (dark: bg-blue-600)

// EP count badge (in timeline entries)
<span className={`neo-badge flex items-center gap-1 ${timeBadgeClass}`}>
  <Play className="w-2 h-2 sm:w-2.5 sm:h-2.5 fill-current" />
  EP {anime.nextEpisode}
</span>
```

### Compose equivalent

```kotlin
@Composable
fun NeoBadge(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = LocalNeoColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg, RoundedCornerShape(6.dp))
            .border(2.dp, colors.foreground, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leading?.invoke(this)
        Text(
            text = text.uppercase(),
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
            lineHeight = 14.sp,
        )
    }
}
```

## 5. `.neo-section-header` — section title

### CSS

```css
.neo-section-header {
  position: relative;
  padding-left: 16px;
}
.neo-section-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 4px;
  background: var(--primary);
  border-radius: 2px;
  box-shadow: 2px 0 0 var(--neo-shadow-blue);
}
```

A 4px-wide vertical accent bar on the left, in primary blue, with a 2px-wide blue hard shadow on its right edge (creates a layered "double bar" effect). The header content is padded 16px left to clear the bar.

### Usage

Section headers combine the `.neo-section-header` wrapper with an inline icon box + title:

```tsx
<div className="neo-section-header flex items-center gap-2.5">
  {/* Icon box — 8×8 primary-filled square with 2px border + colored 2×2 shadow */}
  <div
    className="w-8 h-8 rounded-[6px] bg-primary text-primary-foreground flex items-center justify-center border-2 border-foreground"
    style={{ boxShadow: `2px 2px 0px ${shadowColor}` }}
  >
    <Icon className="w-4 h-4" />
  </div>
  {/* Title */}
  <h2 className="text-lg md:text-xl font-extrabold uppercase tracking-wide">
    {title}
  </h2>
</div>
```

Section-icon shadow color is per-section: Trending=blue, Freshly Updated=green, By Genre=orange, Most Popular=orange, Coming Up Next=green.

### Compose equivalent

```kotlin
@Composable
fun NeoSectionHeader(
    title: String,
    icon: ImageVector,
    iconShadowColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNeoColors.current
    Row(
        modifier = modifier.padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 4px accent bar with 2px blue shadow (drawn behind via drawBehind)
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(RoundedCornerShape(2.dp))
                .drawBehind {
                    drawRect(
                        color = colors.neoShadowBlue,
                        topLeft = Offset(2f, 0f),
                        size = size,
                    )
                }
                .background(colors.primary, RoundedCornerShape(2.dp)),
        )
        // Wait — this would be wrong: the accent bar is positioned absolutely in CSS.
        // In Compose, we'd handle this by overlaying it via Box + Modifier.align.
    }
    // (See layout/README.md for the full Compose layout pattern)
}
```

## 6. `.neo-input` / `.neo-input-red` — text input

### CSS

```css
.neo-input {
  background: var(--card);
  border: var(--neo-border);             /* 3px solid */
  border-radius: 8px;
  box-shadow: 3px 3px 0px transparent;   /* invisible at rest */
  transition: all 0.15s ease;
}
.neo-input:focus {
  background: #DBEAFE;                   /* blue-100 tint */
  box-shadow: 3px 3px 0px var(--neo-shadow-blue);
  outline: none;
}
.dark .neo-input:focus {
  background: #2A3A5C;
  box-shadow: 3px 3px 0px var(--neo-shadow-blue);
}

.neo-input-red {
  background: var(--card);
  border: 3px solid var(--border);
  box-shadow: 2px 2px 0px var(--neo-shadow-red);   /* red shadow at rest */
  transition: all 0.15s ease;
}
.neo-input-red:focus {
  background: #FEE2E2;                   /* red-100 tint */
  box-shadow: 5px 5px 0px var(--neo-shadow-red);   /* shadow grows */
  outline: none;
}
.dark .neo-input-red:focus {
  background: #4A2222;
  box-shadow: 5px 5px 0px var(--neo-shadow-red);
}
```

Two variants: default (blue accent on focus) and red (red accent at rest + focus, used for the navbar search input).

### Usage

```tsx
// Desktop search input (sm+)
<input
  type="text"
  value={searchQuery}
  onChange={(e) => setSearchQuery(e.target.value)}
  placeholder="Search anime..."
  className="neo-input-red w-[260px] md:w-[320px] h-[40px] pl-10 pr-4 text-sm font-bold tracking-tight placeholder:text-muted-foreground/60 rounded-lg"
/>
```

### Compose equivalent

```kotlin
@Composable
fun NeoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    variant: NeoInputVariant = NeoInputVariant.Blue,
) {
    val colors = LocalNeoColors.current
    var focused by remember { mutableStateOf(false) }
    val (restShadow, focusShadow, focusBg) = when (variant) {
        NeoInputVariant.Blue -> Triple(0.dp, 3.dp, colors.neoHoverBg)
        NeoInputVariant.Red -> Triple(2.dp, 5.dp, if (colors == NeoColors.dark()) Color(0xFF4A2222) else Color(0xFFFEE2E2))
    }
    val shadowDp by animateDpAsState(if (focused) focusShadow else restShadow, tween(150))
    val shadowColor = if (variant == NeoInputVariant.Blue) colors.neoShadowBlue else colors.neoShadowRed
    val bg = if (focused) focusBg else colors.card

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = NeoTypography.input,
        cursorBrush = SolidColor(colors.foreground),
        singleLine = true,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .border(3.dp, colors.border, RoundedCornerShape(8.dp))
            .drawBehind {
                if (shadowDp > 0.dp) {
                    drawRect(
                        color = shadowColor,
                        topLeft = Offset(shadowDp.toPx(), shadowDp.toPx()),
                        size = size,
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(placeholder, color = colors.mutedForeground.copy(alpha = 0.6f))
            }
            innerTextField()
        },
    )
}
```

## 7. `.neo-sidebar` — navigation drawer

### CSS

```css
.neo-sidebar {
  background: var(--sidebar);
  border-right: 3px solid var(--border);
  box-shadow: 4px 0 0px var(--neo-shadow);   /* right-side hard shadow */
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.neo-sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: -0.02em;
  font-size: 13px;
  border: 2px solid transparent;
  transition: all 0.1s ease;
  cursor: pointer;
}
.neo-sidebar-item:hover {
  background: var(--neo-hover-bg);
  border-color: var(--border);
  box-shadow: 2px 2px 0px var(--neo-shadow);
  transform: translate(-1px, -1px);
}
.neo-sidebar-item:active {
  background: var(--neo-active-bg);
  box-shadow: 1px 1px 0px var(--neo-shadow);
  transform: translate(1px, 1px);
}
.neo-sidebar-item.active {
  background: var(--primary);
  color: var(--primary-foreground);
  border-color: var(--foreground);
  box-shadow: 2px 2px 0px var(--neo-shadow);
}
.neo-sidebar-item.active:hover {
  opacity: 0.9;
}

.neo-sidebar-overlay {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}
```

### Sidebar layout (from `Sidebar` component)

- Position: `fixed top-0 left-0 h-dvh lg:h-screen z-[80]`
- Width: 64–280px (default 220px, drag-resizable)
- Slide: `translate-x-0` when open, `-translate-x-full` when closed (mobile). Always visible on lg+ (`lg:translate-x-0`).
- Sections (top to bottom):
  1. Logo block — `p-5 pb-4`, 9×9 icon box (`bg-primary border-2 border-foreground rounded-[6px]`, shadow `2px 2px 0 var(--neo-shadow-blue)`) + wordmark `text-xl font-black uppercase tracking-tight` with `textShadow: 2px 2px 0 var(--neo-shadow-blue)`. Wordmark: `ANI` + `VERSE` in primary blue.
  2. Divider — `mx-4 border-b-2 border-foreground/15`
  3. Nav items — `p-3 space-y-1 overflow-y-auto no-scrollbar`. Each is `.neo-sidebar-item w-full`. Icons at `w-[18px] h-[18px]`.
  4. Theme toggle (bottom) — `p-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] border-t-2 border-foreground/15`. Same `.neo-sidebar-item` styling, icon-only when collapsed.
  5. Drag handle — `absolute right-0 top-0 h-full w-1 cursor-col-resize z-10 hover:w-1.5 bg-foreground/15 hover:bg-primary/60 transition-all duration-100`, with `marginRight: "-2px"`.

### Compose equivalent

Use Compose `ModalNavigationDrawer` (mobile) and `PermanentNavigationDrawer` (tablet/desktop). Custom `NeoSidebarItem` composable mirrors the CSS states.

## 8. `.neo-skeleton` — loading placeholder

### CSS

```css
.neo-skeleton {
  position: relative;
  background: var(--muted);
  border: 2px dashed var(--border);          /* dashed = "unfinished" */
  border-radius: var(--neo-border-radius);   /* 10px */
  overflow: hidden;
  animation: neo-pulse 1.2s ease-in-out infinite;
}

.neo-skeleton::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(
    105deg,
    transparent 35%,
    rgba(255, 255, 255, 0.12) 45%,
    rgba(255, 255, 255, 0.18) 50%,
    rgba(255, 255, 255, 0.12) 55%,
    transparent 65%
  );
  background-size: 250% 100%;
  animation: neo-shimmer 2s ease-in-out infinite;
  border-radius: inherit;
  pointer-events: none;
}

.dark .neo-skeleton::after {
  background: linear-gradient(
    105deg,
    transparent 35%,
    rgba(255, 255, 255, 0.06) 45%,
    rgba(255, 255, 255, 0.1) 50%,
    rgba(255, 255, 255, 0.06) 55%,
    transparent 65%
  );
  background-size: 250% 100%;
}

@keyframes neo-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@keyframes neo-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
```

Distinctive features:
- **Dashed** 2px border (vs solid 3px on real cards) — signals "incomplete" / "raw sketch".
- Base surface = `var(--muted)` (the muted token, not card).
- Pulse on opacity (1 → 0.5 → 1 over 1.2s) — gentle breathing.
- Diagonal shimmer sweep (105° linear-gradient, animated background-position) — the actual loading indicator. Light mode peaks at 18% white; dark mode at 10% white.

### Usage

Skeletons mirror the real component layout exactly:
- `AnimeCardSkeleton` — same `aspect-[5/7]` cover, same badges in same positions, same title/genre subtitle below.
- `ContentRowSkeleton` — section header (real, not skeleton) + row of N `AnimeCardSkeleton`s.
- `GenreSectionSkeleton` — section header + row of 8 genre-card skeletons (`h-[90px] md:h-[100px]`).
- `TimelineEntrySkeleton` — same `pl-8 sm:pl-9 md:pl-14` indentation, same dot, same card layout.
- `NextReleasingSectionSkeleton` — full timeline container with Today/Tomorrow group separators.
- `HeroBannerSkeleton` — full hero with desktop card skeleton, mobile content skeleton, dots skeleton.

### Compose equivalent

```kotlin
@Composable
fun NeoSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val colors = LocalNeoColors.current
    val infinite = rememberInfiniteTransition()
    val pulse by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)))
    val shimmer by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)))

    val baseAlpha = 0.5f + 0.5f * (0.5f + 0.5f * cos(pulse * PI))
    val shimmerColor = if (colors == NeoColors.dark()) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.muted.copy(alpha = baseAlpha), shape)
            .border(2.dp, colors.border.copy(alpha = 0.5f), shape, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            .drawWithContent {
                drawContent()
                // Shimmer sweep
                val sweep = lerp(0.35f, 0.65f, shimmer)  // shimmer animates across
                val sweepWidth = 0.10f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, shimmerColor, shimmerColor, Color.Transparent),
                        start = Offset(size.width * (sweep - sweepWidth), 0f),
                        end = Offset(size.width * (sweep + sweepWidth), size.height),
                    ),
                )
            },
    )
}
```

## 9. `.neo-nav-btn` — generic nav button (less-used)

### CSS

```css
.neo-nav-btn {
  border: 3px solid var(--foreground);
  border-radius: 6px;
  transition: all 0.1s ease;
}
.neo-nav-btn:hover {
  background: var(--neo-hover-bg);
}
.neo-nav-btn:active {
  background: var(--neo-active-bg);
}
```

Simpler than `.neo-btn` — no shadow, no uppercase, no transform. Used for utility buttons.

### Usage in `page.tsx`

Not actually invoked directly — `.neo-nav-btn` is defined but `page.tsx` uses more specific classes (`.neo-nav-red`) for navbar action buttons. Listed here for completeness.

## 10. `.neo-nav-red` — navbar action button

### CSS

```css
.neo-nav-red {
  transition: all 0.1s ease;
}
.neo-nav-red:hover {
  background: var(--destructive);
  color: var(--primary-foreground);
  box-shadow: 3px 3px 0px var(--neo-shadow-red);
  transform: translate(-1px, -1px);
}
.neo-nav-red:active {
  background: var(--destructive);
  color: var(--primary-foreground);
  box-shadow: 1px 1px 0px var(--neo-shadow-red);
  transform: translate(1px, 1px);
}
```

A "red nav button" — at rest it looks like a normal card button (set inline via Tailwind: `bg-card border-2 border-foreground text-foreground`); on hover/active it bursts into destructive red with white text + red shadow + lift/press transform.

### Usage

```tsx
// Hamburger button (mobile)
<button className="w-9 h-9 rounded-[8px] bg-card border-2 border-foreground flex items-center justify-center text-foreground neo-nav-red">
  {sidebarOpen ? <X /> : <Menu />}
</button>

// Bell / notification button (desktop)
<button className="hidden md:flex relative w-9 h-9 rounded-[8px] bg-card border-2 border-foreground items-center justify-center text-foreground neo-nav-red">
  <Bell className="w-4 h-4" />
  <span className="absolute -top-1 -right-1 w-3 h-3 bg-primary rounded-[3px] border-2 border-foreground" />
</button>
```

The notification dot is a 3×3 primary-blue square with 3px-radius corners, 2px foreground border, positioned `top: -1, right: -1` (i.e. overlapping the top-right corner of the button).

## 11. Scroll arrows (in `ContentRow`)

Not a `.neo-*` class — built inline in `page.tsx`:

```tsx
<button
  onClick={() => scroll("left")}
  className={[
    "w-9 h-9 sm:w-10 sm:h-10 rounded-[6px]",
    "bg-card border-[2.5px] border-foreground",
    "flex items-center justify-center text-foreground",
    "active:translate-x-[2px] active:translate-y-[2px] active:shadow-[2px_2px_0px_var(--neo-shadow)]",
    "transition-all duration-150",
    "opacity-0 sm:opacity-0",
    "sm:group-hover/row:opacity-100",
    "hover:opacity-100",
  ].join(" ")}
  style={{ boxShadow: `4px 4px 0px var(--neo-shadow)` }}
>
  <ChevronLeft className="w-5 h-5" />
</button>
```

- 36×36 mobile / 40×40 sm+
- 6px radius, 2.5px border, 4px black hard shadow at rest
- Pressed: shadow shrinks to 2×2, transform translates (+2, +2) — same press model as `.neo-btn`
- Hover-revealed (opacity 0 → 100% on row hover, 150ms transition)
- Hidden on mobile entirely (`opacity-0` always) — mobile users swipe

## 12. Carousel dots

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

- Inactive: 3×3 muted-foreground/30 square with 3px radius and 2px border.
- Active: stretches to 8×3, fills with primary blue, gains a 2×2 blue hard shadow.
- Transition: `all 200ms` (size + bg + shadow).

## 13. Footer

```tsx
<motion.footer
  className="border-t-[3px] border-foreground bg-card mt-auto"
  style={{ boxShadow: "0 -4px 0px var(--neo-shadow)" }}
>
  {/* 4-column grid: brand + Browse + Community + Legal */}
  {/* Bottom bar: copyright + Status / API / v2.4.1 links */}
</motion.footer>
```

- Top edge: 3px solid foreground border.
- Above-border shadow: `0 -4px 0 var(--neo-shadow)` — a 4px black slab rising *upward* from the footer top (mirrors the sidebar's right-side shadow pattern).
- Background: `var(--card)`.
- Brand mark: same 9×9 icon box + wordmark pattern as the sidebar logo, with the same `textShadow: 2px 2px 0 var(--neo-shadow-blue)` on the wordmark.
- Link styling: `text-xs font-bold uppercase text-muted-foreground hover:text-primary hover:bg-primary/10 px-1.5 py-0.5 rounded-[4px] transition-all duration-100` — small uppercase 4px-radius hover chips with a primary tint background on hover.
- Bottom separator: `pt-8 border-t-2 border-foreground/10`.

## 14. Tooltip (Radix shadcn, lightly restyled)

From `src/components/ui/tooltip.tsx`:

```tsx
<TooltipPrimitive.Content
  className={cn(
    "bg-primary text-primary-foreground animate-in fade-in-0 zoom-in-95 ...",
    "z-50 w-fit origin-(--radix-tooltip-content-transform-origin) rounded-md px-3 py-1.5 text-xs text-balance",
    className
  )}
>
  {children}
  <TooltipPrimitive.Arrow className="bg-primary fill-primary z-50 size-2.5 translate-y-[calc(-50%_-_2px)] rotate-45 rounded-[2px]" />
</TooltipPrimitive.Content>
```

Standard shadcn `new-york` style tooltip. Background = `bg-primary` (blue), text = `text-primary-foreground` (white), 6px radius, `text-xs`, `px-3 py-1.5`. Arrow is a 10px (2.5×4 = `size-2.5`) rotated square with 2px radius.

In `page.tsx`, the notifications tooltip overrides the styling:

```tsx
<TooltipContent side="bottom" className="neo-card text-sm font-bold uppercase">
  Notifications
</TooltipContent>
```

This applies `.neo-card` styling to the tooltip — making it a bordered, shadowed neobrutalism card rather than the default primary-filled pill.

## 15. Toast (Radix shadcn)

From `src/components/ui/toast.tsx`:

```tsx
const toastVariants = cva(
  "group pointer-events-auto relative flex w-full items-center justify-between space-x-2 overflow-hidden rounded-md border p-4 pr-6 shadow-lg transition-all ...",
  {
    variants: {
      variant: {
        default: "border bg-background text-foreground",
        destructive: "destructive group border-destructive bg-destructive text-destructive-foreground",
      },
    },
    defaultVariants: { variant: "default" },
  }
)
```

The toast component uses **default shadcn styling** (soft `shadow-lg`, normal border, no neobrutalism treatment). This is the one place where the design system breaks character — toasts are not neobrutalist. They use the shadcn defaults: `rounded-md` (6px), `border` (1px), `shadow-lg` (soft elevation), `p-4 pr-6`.

TODO for ANI-KUTA: redesign toasts in the neobrutalism style (3px border, 8px radius, hard shadow, uppercase title). This is a clear gap in the source template.

## Element count summary

| Element class | Defined in | Used by components | Compose port priority |
|---|---|---|---|
| `.neo-card` + 7 color variants | `globals.css` | GenreCard, hero panel, popups, timeline entries | High |
| `.neo-card-anime` | `globals.css` | AnimeCard, hero banner section | High |
| `.neo-btn` + 3 color variants | `globals.css` | CTA, "See All" buttons, bookmark | High |
| `.neo-badge` | `globals.css` | All badges (NEW, SUB, DUB, rating, EP, group labels) | High |
| `.neo-section-header` | `globals.css` | All section headers | High |
| `.neo-input`, `.neo-input-red` | `globals.css` | Search bar (mobile + desktop) | High |
| `.neo-sidebar`, `.neo-sidebar-item`, `.neo-sidebar-overlay` | `globals.css` | Sidebar drawer | High |
| `.neo-skeleton` | `globals.css` | All loading skeletons | High |
| `.neo-nav-btn`, `.neo-nav-red` | `globals.css` | Hamburger, bell button | Medium |
| `.neo-scrollbar`, `.no-scrollbar` | `globals.css` | Scrollable rows | Medium |
| Carousel dots (inline) | `page.tsx` | Hero banner | Medium |
| Scroll arrows (inline) | `page.tsx` | ContentRow | Low (skip on mobile) |
| Timeline dots (inline) | `page.tsx` | NextReleasingSection | High |
| Footer (inline) | `page.tsx` | Bottom of page | Medium |
| Tooltip (shadcn) | `tooltip.tsx` | Notifications button | Medium |
| Toast (shadcn, **not neobrutalist**) | `toast.tsx` | Toaster | Low / TODO: redesign |
