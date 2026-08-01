package app.confused.anikuta.feature.setupwizard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.dp
import app.confused.anikuta.feature.setupwizard.theme.*
import app.confused.anikuta.feature.setupwizard.components.*
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.delay

// ============================================================================
// STATE
// ============================================================================

enum class WizardStep(val route: String) {
    WELCOME("welcome"),
    THEME("theme"),
    FOLDER("folder"),
    PERMISSIONS("permissions"),
    RESTORE("restore"),
    FORMAT("format"),
    PROCESSING("processing"),
    SUMMARY("summary"),
    LINKING("linking"),
    MANUAL("manual"),
    RESTORE_SUMMARY("restore-summary"),
    RESTORE_PROCESSING("restore-processing"),
    RESTORE_SUCCESS("restore-success"),
    POISON("poison"),
    FINISH("finish");
}

val STEP_ORDER = WizardStep.values().toList()

enum class AdName(val label: String) { POISON("Daily dose of poison"), PILLS("Daily dose of pills") }
enum class AdTiming(val label: String) { APP_OPEN("On app open"), EPISODE_START("On episode start"), BOTH("Both") }
enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

data class AdSettings(
    val name: AdName = AdName.POISON,
    val frequency: Int = 2,
    val timing: AdTiming = AdTiming.APP_OPEN,
)

data class LinkedAnime(
    val id: Int,
    val backupName: String,
    val linked: Boolean,
    val matchedName: String? = null,
)

data class WizardState(
    val step: WizardStep = WizardStep.WELCOME,
    val paletteIndex: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val folderSelected: Boolean = false,
    val permissions: Map<String, Boolean> = mapOf("installApps" to false, "notifications" to false, "battery" to false, "allFiles" to false),
    val linkedAnime: List<LinkedAnime> = DEFAULT_ANIME,
    val adSettings: AdSettings = AdSettings(),
) {
    val palette get() = AllPalettes[paletteIndex]
}

val DEFAULT_ANIME = listOf(
    LinkedAnime(1, "Frieren: Beyond Journey's End", true, "Sousou no Frieren"),
    LinkedAnime(2, "Jujutsu Kaisen Season 2", true, "Jujutsu Kaisen 2nd Season"),
    LinkedAnime(3, "Demon Slayer: Hashira Training", false),
    LinkedAnime(4, "Attack on Titan Final", true, "Shingeki no Kyojin: The Final Season"),
    LinkedAnime(5, "Spy x Family Code: White", false),
    LinkedAnime(6, "Chainsaw Man", true, "Chainsaw Man"),
    LinkedAnime(7, "One Piece Egghead Arc", false),
    LinkedAnime(8, "Solo Leveling", true, "Ore dake Level Up na Ken"),
)

// ============================================================================
// HELPERS
// ============================================================================

/** Mix two colors: result = base * (1-ratio) + tint * ratio */
private fun colorMix(base: Color, tint: Color, ratio: Float): Color {
    return Color(
        red = base.red * (1 - ratio) + tint.red * ratio,
        green = base.green * (1 - ratio) + tint.green * ratio,
        blue = base.blue * (1 - ratio) + tint.blue * ratio,
        alpha = 1f,
    )
}

// ============================================================================
// MAIN APP
// ============================================================================

@Composable
fun SetupWizardApp(onComplete: () -> Unit = {}) {
    var state by remember { mutableStateOf(WizardState()) }
    var poisonStep by remember { mutableStateOf(0) }
    val isPoison = state.step == WizardStep.POISON
    val systemDark = isSystemInDarkTheme()
    // Poison screen is ALWAYS dark red, regardless of user's theme mode
    val isDark = if (isPoison) true else when (state.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    // When light mode is active (and not on poison screen), adjust palette surfaces
    val basePalette = if (isPoison) PoisonPalette else state.palette
    val effectivePalette = if (!isDark && !isPoison) {
        basePalette.copy(
            background = LightBg,
            surface1 = LightSurface1,
            surface2 = LightSurface2,
            surface3 = colorMix(LightSurface2, basePalette.primary, 0.15f),
            surface4 = colorMix(LightSurface1, basePalette.primary, 0.1f),
            surface5 = colorMix(LightBg, basePalette.primary, 0.05f),
        )
    } else basePalette

    // ── Real preference integration ──
    // When the user finishes the wizard, apply their choices to the real
    // app preferences (theme + ads) and mark the wizard as completed.
    val context = androidx.compose.ui.platform.LocalContext.current
    val applyPreferences: () -> Unit = {
        try {
            val prefs = org.koin.core.context.GlobalContext.get()
            // Apply theme preferences
            val themePrefs = prefs.get<app.confused.anikuta.core.preferences.ThemePreferences>()
            val mode = when (state.themeMode) {
                ThemeMode.DARK -> app.confused.anikuta.core.preferences.ThemeMode.DARK
                ThemeMode.LIGHT -> app.confused.anikuta.core.preferences.ThemeMode.LIGHT
                ThemeMode.SYSTEM -> app.confused.anikuta.core.preferences.ThemeMode.SYSTEM
            }
            themePrefs.themeMode.set(mode)
            // Apply ad preferences
            val adsPrefs = prefs.get<app.confused.anikuta.core.ads.AdsPreferences>()
            adsPrefs.setAdsEnabled(true)
            adsPrefs.setDailyQuota(state.adSettings.frequency.coerceIn(1, 10))
            // Mark wizard as completed
            val setupPrefs = prefs.get<app.confused.anikuta.core.preferences.SetupWizardPreferences>()
            setupPrefs.setCompleted(true)
        } catch (e: Exception) {
            android.util.Log.e("SetupWizard", "Failed to apply preferences", e)
        }
    }

    SetupWizardTheme(palette = effectivePalette, isDark = isDark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Progress bar — FLUSH AT THE VERY TOP (y=0, full width, no padding).
                // The transparent status bar is drawn ON TOP of this bar.
                WizardProgressBar(
                    current = STEP_ORDER.indexOf(state.step),
                    total = STEP_ORDER.size,
                    color = effectivePalette.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Screen content — fills remaining space, with status bar padding
                Box(modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = {
                            (slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(tween(300)))
                        },
                        label = "screen"
                    ) { step ->
                        when (step) {
                            WizardStep.WELCOME -> WelcomeScreen(
                                palette = effectivePalette,
                                onNext = { state = state.copy(step = WizardStep.THEME) }
                            )
                            WizardStep.THEME -> ThemeScreen(
                                palette = effectivePalette,
                                paletteIndex = state.paletteIndex,
                                onPaletteChange = { state = state.copy(paletteIndex = it) },
                                themeMode = state.themeMode,
                                onThemeModeChange = { state = state.copy(themeMode = it) },
                                onBack = { state = state.copy(step = WizardStep.WELCOME) },
                                onNext = { state = state.copy(step = WizardStep.FOLDER) }
                            )
                            WizardStep.FOLDER -> FolderScreen(
                                palette = effectivePalette,
                                folderSelected = state.folderSelected,
                                onSelect = { state = state.copy(folderSelected = true) },
                                onBack = { state = state.copy(step = WizardStep.THEME) },
                                onNext = { state = state.copy(step = WizardStep.PERMISSIONS) }
                            )
                            WizardStep.PERMISSIONS -> PermissionsScreen(
                                palette = effectivePalette,
                                permissions = state.permissions,
                                onToggle = { key -> state = state.copy(permissions = state.permissions.toMutableMap().apply { this[key] = !(this[key] ?: false) }) },
                                onBack = { state = state.copy(step = WizardStep.FOLDER) },
                                onNext = { state = state.copy(step = WizardStep.RESTORE) }
                            )
                            WizardStep.RESTORE -> RestoreScreen(
                                palette = effectivePalette,
                                onBack = { state = state.copy(step = WizardStep.PERMISSIONS) },
                                onNext = { state = state.copy(step = WizardStep.FORMAT) },
                                onSkip = { state = state.copy(step = WizardStep.POISON) }
                            )
                            WizardStep.FORMAT -> FormatScreen(
                                palette = effectivePalette,
                                onBack = { state = state.copy(step = WizardStep.RESTORE) },
                                onNext = { state = state.copy(step = WizardStep.PROCESSING) }
                            )
                            WizardStep.PROCESSING -> ProcessingScreen(
                                palette = effectivePalette,
                                onNext = { state = state.copy(step = WizardStep.SUMMARY) }
                            )
                            WizardStep.SUMMARY -> SummaryScreen(
                                palette = effectivePalette,
                                onCancel = { state = state.copy(step = WizardStep.FORMAT) },
                                onNext = { state = state.copy(step = WizardStep.LINKING) }
                            )
                            WizardStep.LINKING -> LinkingScreen(
                                palette = effectivePalette,
                                linkedAnime = state.linkedAnime,
                                onUnlink = { id -> state = state.copy(linkedAnime = state.linkedAnime.map { if (it.id == id) it.copy(linked = false, matchedName = null) else it }) },
                                onBack = { state = state.copy(step = WizardStep.SUMMARY) },
                                onNext = { state = state.copy(step = WizardStep.MANUAL) }
                            )
                            WizardStep.MANUAL -> ManualScreen(
                                palette = effectivePalette,
                                linkedAnime = state.linkedAnime,
                                onLink = { id, name -> state = state.copy(linkedAnime = state.linkedAnime.map { if (it.id == id) it.copy(linked = true, matchedName = name) else it }) },
                                onBack = { state = state.copy(step = WizardStep.LINKING) },
                                onNext = { state = state.copy(step = WizardStep.RESTORE_SUMMARY) }
                            )
                            WizardStep.RESTORE_SUMMARY -> RestoreSummaryScreen(
                                palette = effectivePalette,
                                linkedAnime = state.linkedAnime,
                                onBack = { state = state.copy(step = WizardStep.MANUAL) },
                                onNext = { state = state.copy(step = WizardStep.RESTORE_PROCESSING) }
                            )
                            WizardStep.RESTORE_PROCESSING -> RestoreProcessingScreen(
                                palette = effectivePalette,
                                linkedAnime = state.linkedAnime,
                                onNext = { state = state.copy(step = WizardStep.RESTORE_SUCCESS) }
                            )
                            WizardStep.RESTORE_SUCCESS -> RestoreSuccessScreen(
                                palette = effectivePalette,
                                onNext = { state = state.copy(step = WizardStep.POISON); poisonStep = 0 }
                            )
                            WizardStep.POISON -> PoisonScreen(
                                palette = effectivePalette,
                                adSettings = state.adSettings,
                                onUpdate = { state = state.copy(adSettings = it) },
                                step = poisonStep,
                                onStepChange = { poisonStep = it },
                                onBack = { state = state.copy(step = WizardStep.RESTORE_SUCCESS) },
                                onNext = { state = state.copy(step = WizardStep.FINISH) }
                            )
                            WizardStep.FINISH -> FinishScreen(
                                palette = effectivePalette,
                                state = state,
                                onRestart = {
                                    // Apply preferences + mark completed + notify caller
                                    applyPreferences()
                                    onComplete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// SHARED COMPONENTS
// ============================================================================

/**
 * Screen layout: fixed heading at top, centered content in the middle, fixed footer at bottom.
 * Content is vertically CENTERED in the available space (not squished to top).
 * If content overflows, it scrolls.
 */
@Composable
fun ScreenLayout(
    palette: WizardPalette,
    heading: String? = null,
    onBack: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    nextText: String = "Next",
    nextEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (heading != null) {
            PageHeading(heading, palette)
        }
        // Content area — fills available space, centers content vertically
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
        if (onBack != null || onNext != null) {
            ActionRow(back = onBack, next = onNext, nextText = nextText, palette = palette, nextEnabled = nextEnabled)
        }
    }
}
// ============================================================================

@Composable
fun WizardProgressBar(current: Int, total: Int, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { (current + 1f) / total },
        modifier = modifier.height(3.dp),
        color = color,
        trackColor = color.copy(alpha = 0.15f),
    )
}

@Composable
fun PageHeading(text: String, palette: WizardPalette, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = palette.primary,
        fontSize = 42.sp,
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.75).sp,
        lineHeight = 46.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 20.dp, top = 0.dp, end = 20.dp)
    )
}

@Composable
fun DescriptiveTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 20.sp,
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        fontSize = 14.sp,
        fontFamily = RobotoFamily,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun ActionRow(back: (() -> Unit)? = null, next: (() -> Unit)? = null, nextText: String = "Next", palette: WizardPalette, nextEnabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (back != null) {
            WizardButton("Back", back, palette, isPrimary = false, leadingIcon = { Icon(Icons.Default.ArrowBack, null, Modifier.size(20.dp)) }, modifier = Modifier.weight(1f))
        }
        if (next != null) {
            WizardButton(nextText, next, palette, isPrimary = true, enabled = nextEnabled, trailingIcon = { Icon(Icons.Default.ArrowForward, null, Modifier.size(20.dp)) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun WizardButton(text: String, onClick: () -> Unit, palette: WizardPalette, isPrimary: Boolean = true, enabled: Boolean = true, leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = if (isPrimary) ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = palette.onPrimary, disabledContainerColor = palette.primary.copy(alpha = 0.3f)) else ButtonDefaults.buttonColors(containerColor = palette.surface3, contentColor = MaterialTheme.colorScheme.onBackground),
        modifier = modifier.height(52.dp),
    ) {
        if (leadingIcon != null) leadingIcon()
        Text(text, fontWeight = FontWeight.ExtraBold, fontFamily = RobotoFamily, fontSize = 16.sp)
        if (trailingIcon != null) trailingIcon()
    }
}

// ============================================================================
// SCREENS — each uses: Column(fillMaxSize) > PageHeading (fixed) > Column(weight(1f).verticalScroll) > ActionRow (fixed)
// ============================================================================

@Composable
fun WelcomeScreen(palette: WizardPalette, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Welcome to Anime App!",
                color = palette.primary,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RobotoFamily,
                letterSpacing = (-0.75).sp,
                lineHeight = 46.sp,
                modifier = Modifier.padding(top = 0.dp)
            )
            Text(
                "Let's get things quickly set up for you.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 15.sp,
                fontFamily = RobotoFamily,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        // Scrollable content with floating shapes animation
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Floating shapes background animation
            FloatingShapes(palette, modifier = Modifier.fillMaxSize())
            // Scrollable list on top
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val items = listOf(
                    "Track what you watch" to Icons.Default.CheckCircle,
                    "Pick up anywhere" to Icons.Default.Sync,
                    "Never miss a release" to Icons.Default.Notifications
                )
                items.forEachIndexed { _, (title, icon) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface2).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(palette.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = RobotoFamily)
                    }
                }
            }
        }
        // Fixed footer
        ActionRow(next = onNext, nextText = "Get Started", palette = palette)
    }
}

@Composable
fun ThemeScreen(palette: WizardPalette, paletteIndex: Int, onPaletteChange: (Int) -> Unit, themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Theme", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mini preview — animated phone that cycles through screen states (MUCH bigger)
            MiniAnimePreview(
                palette = palette,
                modifier = Modifier.size(width = 180.dp, height = 340.dp).padding(vertical = 4.dp)
            )
            DescriptiveTitle("Choose your theme", modifier = Modifier.fillMaxWidth())
            Text(
                "Pick a mode and a color and we are set with it.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 0.dp)
            )
            Spacer(Modifier.height(12.dp))
            // Mode toggle: Dark / Light / System
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(999.dp)).padding(4.dp)
            ) {
                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(999.dp)).background(if (themeMode == mode) palette.primary else Color.Transparent).clickable { onThemeModeChange(mode) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            mode.label,
                            color = if (themeMode == mode) palette.onPrimary else MaterialTheme.colorScheme.onBackground,
                            fontSize = 12.sp,
                            fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Palette carousel
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(AllPalettes.size) { i ->
                    val p = AllPalettes[i]
                    Column(
                        modifier = Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).background(if (i == paletteIndex) p.primary.copy(alpha = 0.12f) else palette.surface2).border(2.dp, if (i == paletteIndex) p.primary else Color.Transparent, RoundedCornerShape(16.dp)).clickable { onPaletteChange(i) }.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(brush = Brush.linearGradient(listOf(p.primary, p.primary.copy(alpha = 0.7f)))))
                        Spacer(Modifier.height(8.dp))
                        Text(PaletteNames[i], color = if (i == paletteIndex) p.primary else MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Fixed footer
        ActionRow(back = onBack, next = onNext, palette = palette)
    }
}

@Composable
fun FolderScreen(palette: WizardPalette, folderSelected: Boolean, onSelect: () -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    var scanning by remember(folderSelected) { mutableStateOf(folderSelected) }
    LaunchedEffect(scanning) { if (scanning) { delay(1500); scanning = false } }
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Folder", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(200.dp).padding(vertical = 8.dp)) {
                FolderVisual(palette, selected = folderSelected && !scanning)
            }
            DescriptiveTitle(if (folderSelected) "Folder connected!" else "Select your anime folder", modifier = Modifier.fillMaxWidth())
            Subtitle(
                when {
                    folderSelected && scanning -> "Scanning your library…"
                    folderSelected && !scanning -> "Your library is ready to go. Continue when you are."
                    else -> "Pick the folder where your anime library lives. We'll scan it and organize everything for you."
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (!folderSelected) {
                OutlinedButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally).height(44.dp),
                    border = BorderStroke(1.5.dp, palette.primary)
                ) {
                    Icon(Icons.Default.Folder, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select Folder", color = palette.primary, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
                }
            } else {
                // Folder card
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface2).border(1.dp, palette.primary, RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(palette.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Folder, null, tint = palette.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("/storage/anime-library", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
                        Text(if (scanning) "Scanning…" else "247 items · ready", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    if (!scanning) Icon(Icons.Default.Check, null, tint = palette.primary)
                }
            }
        }
        // Fixed footer
        if (scanning) {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WizardButton("Back", onBack, palette, isPrimary = false, enabled = true, leadingIcon = { Icon(Icons.Default.ArrowBack, null, Modifier.size(20.dp)) }, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(999.dp)).background(palette.surface3), contentAlignment = Alignment.Center) {
                    Text("Scanning…", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        } else {
            ActionRow(back = onBack, next = onNext, nextText = "Continue", palette = palette, nextEnabled = folderSelected)
        }
    }
}

@Composable
fun PermissionsScreen(palette: WizardPalette, permissions: Map<String, Boolean>, onToggle: (String) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val rows = listOf(
        "installApps" to ("Install apps" to "Allow installing anime extensions"),
        "notifications" to ("Notifications" to "Get notified about new episodes"),
        "battery" to ("Battery" to "Allow background sync for updates"),
        "allFiles" to ("All files access" to "Access all files on your device"),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Permissions", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(180.dp).padding(vertical = 4.dp)) { ShieldVisual(palette) }
            DescriptiveTitle("Grant permissions", modifier = Modifier.fillMaxWidth())
            Subtitle("Optional: you can skip these", modifier = Modifier.fillMaxWidth().padding(top = 0.dp))
            Spacer(Modifier.height(8.dp))
            rows.forEach { (key, pair) ->
                val isOn = permissions[key] ?: false
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface2).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (isOn) palette.primary else palette.surface3),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (key) {
                                "installApps" -> Icons.Default.Download
                                "notifications" -> Icons.Default.Notifications
                                "battery" -> Icons.Default.BatteryFull
                                else -> Icons.Default.Folder
                            },
                            null,
                            tint = if (isOn) palette.onPrimary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pair.first, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
                        Text(pair.second, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                    Switch(checked = isOn, onCheckedChange = { onToggle(key) }, colors = SwitchDefaults.colors(checkedTrackColor = palette.primary, checkedThumbColor = palette.onPrimary))
                }
            }
        }
        // Fixed footer
        ActionRow(back = onBack, next = onNext, nextText = "Continue", palette = palette)
    }
}

@Composable
fun RestoreScreen(palette: WizardPalette, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Restore Backup", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(180.dp).padding(vertical = 8.dp)) { RestoreVisual(palette) }
            DescriptiveTitle("Restore backup", modifier = Modifier.fillMaxWidth())
            Subtitle(
                "Got a backup from a previous install? Restore your library, history, and settings in one tap.",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNext,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally).height(44.dp),
                border = BorderStroke(1.5.dp, palette.primary)
            ) {
                Icon(Icons.Default.FileDownload, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Select Backup File", color = palette.primary, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
            }
        }
        // Fixed footer (Back + Skip)
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WizardButton("Back", onBack, palette, isPrimary = false, enabled = true, leadingIcon = { Icon(Icons.Default.ArrowBack, null, Modifier.size(20.dp)) }, modifier = Modifier.weight(1f))
            TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                Text("Skip", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FormatScreen(palette: WizardPalette, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Restore Backup", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(280.dp).padding(vertical = 8.dp)) { WarningVisual(palette) }
            Text(
                "This is not the format I was expecting.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Still, I can try to restore from it properly.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            // File details card
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface2).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(palette.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Description, null, tint = palette.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("anime_backup_2025-01-15.json", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
                    Text("2.3 MB · JSON (unknown schema)", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
        // Fixed footer
        ActionRow(back = onBack, next = onNext, nextText = "Try restoring anyway", palette = palette)
    }
}

@Composable
fun ProcessingScreen(palette: WizardPalette, onNext: () -> Unit) {
    LaunchedEffect(Unit) { delay(2500); onNext() }
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Restore Backup", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(260.dp).padding(vertical = 8.dp)) { ProcessingVisual(palette) }
            DescriptiveTitle("Processing backup", modifier = Modifier.fillMaxWidth())
            Subtitle("Reading your backup file and extracting data…", modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Processing", color = palette.primary, fontSize = 13.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(8.dp))
                repeat(3) { Box(Modifier.size(6.dp).clip(CircleShape).background(palette.primary)); Spacer(Modifier.width(4.dp)) }
            }
        }
        // Fixed footer
        Text("Please wait…", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), textAlign = TextAlign.Center)
    }
}

@Composable
fun SummaryScreen(palette: WizardPalette, onCancel: () -> Unit, onNext: () -> Unit) {
    val items = listOf(
        Icons.Default.PlayCircle to ("Anime detected" to "247"),
        Icons.Default.Category to ("Categories" to "12"),
        Icons.Default.Movie to ("Episodes tracked" to "1,432"),
        Icons.Default.History to ("Watch history" to "89"),
        Icons.Default.Settings to ("Settings" to "—"),
        Icons.Default.MenuBook to ("Manga entries" to "12"),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Restore Backup", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(180.dp).padding(vertical = 4.dp)) { ClipboardVisual(palette) }
            DescriptiveTitle("Backup summary", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { i, (icon, pair) ->
                val isWarn = i == 5
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(if (isWarn) Color(0xFFF2B8B5).copy(alpha = 0.1f) else palette.surface2).border(1.dp, if (isWarn) Color(0xFFF2B8B5).copy(alpha = 0.4f) else palette.surface3, RoundedCornerShape(16.dp)).padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (isWarn) Color(0xFFF2B8B5).copy(alpha = 0.18f) else palette.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = if (isWarn) Color(0xFFF2B8B5) else palette.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pair.first, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
                        Text(if (isWarn) "Not supported — will be skipped" else "Ready to restore", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    Text(pair.second, color = if (isWarn) Color(0xFFF2B8B5) else palette.primary, fontSize = 18.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        // Fixed footer
        ActionRow(back = onCancel, next = onNext, nextText = "Restore", palette = palette)
    }
}

@Composable
fun LinkingScreen(palette: WizardPalette, linkedAnime: List<LinkedAnime>, onUnlink: (Int) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    var revealed by remember { mutableStateOf(0) }
    var popupId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        while (revealed < linkedAnime.size) { delay(400); revealed++ }
    }
    val linked = linkedAnime.count { it.linked }
    val unlinked = linkedAnime.count { !it.linked }
    val total = linkedAnime.size
    val remaining = maxOf(0, total - revealed)
    val allRevealed = revealed >= total
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header (heading + title + subtitle + stats) — dedicated background card
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(16.dp)).padding(12.dp)
        ) {
            Text("Backup Restore", color = palette.primary, fontSize = 42.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.75).sp, lineHeight = 46.sp, modifier = Modifier.padding(top = 0.dp))
            DescriptiveTitle("Linking anime", modifier = Modifier.fillMaxWidth())
            Subtitle("Matching your backup entries", modifier = Modifier.fillMaxWidth().padding(top = 0.dp))
            Spacer(Modifier.height(4.dp))
            // Stats — wider (squished vertically), bigger values
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Linked" to linked to palette.primary,
                    "No match" to unlinked to Color(0xFFF2B8B5),
                    "Total" to total to MaterialTheme.colorScheme.onBackground,
                    "Remaining" to remaining to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                ).forEach { (pair, color) ->
                    val (label, value) = pair
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(palette.surface3).padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$value", color = color, fontSize = 20.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold)
                        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        // Scrollable list — dedicated section with surface background
        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            // Section label
            Text("Entries", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 4.dp))
            // List card with surface background
            LazyColumn(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(16.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            items(linkedAnime.take(revealed)) { anime ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(12.dp)).then(if (anime.linked) Modifier.clickable { popupId = anime.id } else Modifier).padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left half: name (wraps)
                    Column(Modifier.weight(1f)) {
                        Text(anime.backupName, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (anime.linked && anime.matchedName != null) Text(anime.matchedName, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    // Middle: marker (check or X) — dedicated section
                    Box(Modifier.size(26.dp).clip(CircleShape).background(if (anime.linked) palette.primary.copy(alpha = 0.18f) else Color(0xFFF2B8B5).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(if (anime.linked) Icons.Default.Check else Icons.Default.Close, null, tint = if (anime.linked) palette.primary else Color(0xFFF2B8B5), modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    // Right: thumbnail (linked only) — fixed size for uniform rows
                    if (anime.linked) {
                        Box(Modifier.size(width = 30.dp, height = 42.dp).clip(RoundedCornerShape(5.dp)).background(brush = Brush.linearGradient(listOf(palette.primary, palette.primary.copy(alpha = 0.5f)))), contentAlignment = Alignment.Center) {
                            Text(anime.backupName.first().toString(), color = palette.onPrimary, fontSize = 14.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        // Empty spacer to keep uniform row width
                        Box(Modifier.size(width = 30.dp, height = 42.dp))
                    }
                }
            }
            } // close LazyColumn
        } // close Column wrapper
        // Fixed footer
        ActionRow(back = onBack, next = onNext, palette = palette, nextEnabled = allRevealed)
    }
    // Popup — centered with frosted-glass style overlay
    if (popupId != null) {
        val anime = linkedAnime.find { it.id == popupId }
        if (anime != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { popupId = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface2).border(1.dp, palette.surface4, RoundedCornerShape(24.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Linked entry", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                    Text("This entry was auto-linked. If the match is wrong, mark it as not linked — you'll be able to link it manually.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = RobotoFamily)
                    Text(anime.backupName, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surface3).padding(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WizardButton("Keep linked", { popupId = null }, palette, isPrimary = false, enabled = true, modifier = Modifier.weight(1f))
                        WizardButton("Mark as not linked", { onUnlink(anime.id); popupId = null }, palette, isPrimary = true, enabled = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ManualScreen(palette: WizardPalette, linkedAnime: List<LinkedAnime>, onLink: (Int, String) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    var searchOpen by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    val unlinked = linkedAnime.filter { !it.linked }
    val mockResults = listOf(
        "Demon Slayer: Hashira Training Arc" to "Kimetsu no Yaiba · 2024",
        "Kimetsu no Yaiba: Hashira Geiko-hen" to "Japanese title · 2024",
        "Demon Slayer Season 4" to "Sequel · 8 eps",
        "Demon Slayer: To the Swordsmith Village" to "Movie · 2023",
        "Kimetsu no Yaiba: Yuukaku-hen" to "Entertainment District · 2021"
    )
    val filtered = mockResults.filter { it.first.contains(query, ignoreCase = true) || it.second.contains(query, ignoreCase = true) }
    val selectedAnime = linkedAnime.find { it.id == selectedId }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Restore Backup", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top animation — search magnifying glass OR all-linked celebration
            Box(modifier = Modifier.size(150.dp).padding(vertical = 8.dp)) {
                if (unlinked.isEmpty()) {
                    AllLinkedVisual(palette)
                } else {
                    SearchVisual(palette)
                }
            }
            DescriptiveTitle("Manual linking", modifier = Modifier.fillMaxWidth())
            Subtitle(
                if (unlinked.isEmpty()) "All anime are linked! You're ready to continue." else "${unlinked.size} anime need your help. Tap any entry to search for a match.",
                modifier = Modifier.fillMaxWidth().padding(top = 0.dp)
            )
            Spacer(Modifier.height(8.dp))
            unlinked.forEach { anime ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(12.dp)).clickable { selectedId = anime.id; query = anime.backupName; searchOpen = true }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(anime.backupName, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    // Plus icon (matches web prototype)
                    Box(Modifier.size(28.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        // Fixed footer
        ActionRow(back = onBack, next = onNext, nextText = "Continue", palette = palette)
    }
    // Search overlay — full screen, its own scroll via LazyColumn
    if (searchOpen) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(palette.surface3).clickable { searchOpen = false; selectedId = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Find a match", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
            Text("Linking: ${selectedAnime?.backupName ?: ""}", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            // Search bar with X + search icon
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(999.dp)).background(palette.surface2).border(1.5.dp, palette.surface4, RoundedCornerShape(999.dp)).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(value = query, onValueChange = { query = it }, textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp), modifier = Modifier.weight(1f), singleLine = true)
                if (query.isNotEmpty()) {
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(palette.surface3).clickable { query = "" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(palette.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Search, null, tint = palette.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            // Scrollable results
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (filtered.isEmpty()) {
                    item {
                        Text("No results found. Try a different search.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
                    }
                }
                items(filtered) { (title, sub) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(12.dp)).clickable { selectedId?.let { onLink(it, title) }; searchOpen = false; selectedId = null }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(width = 34.dp, height = 48.dp).clip(RoundedCornerShape(5.dp)).background(brush = Brush.linearGradient(listOf(palette.primary, palette.primary.copy(alpha = 0.5f)))), contentAlignment = Alignment.Center) {
                            Text(title.first().toString(), color = palette.onPrimary, fontSize = 13.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.Bold)
                            Text(sub, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                        Box(Modifier.size(28.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreSummaryScreen(palette: WizardPalette, linkedAnime: List<LinkedAnime>, onBack: () -> Unit, onNext: () -> Unit) {
    val linked = linkedAnime.count { it.linked }
    val toRestore = linked + 239
    val episodes = 1432
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Restore Backup", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DescriptiveTitle("Restore summary", modifier = Modifier.fillMaxWidth())
            Subtitle("Ready to restore. Review the details below.", modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            // Hero card
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(20.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Download, null, tint = palette.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Ready to restore", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold)
                        Text("Your library will be overwritten with the backup data.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
                // 2x2 stats grid — rounded cards with proper spacing
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.surface3).padding(12.dp)) {
                        Text("$toRestore", color = palette.primary, fontSize = 22.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        Text("Anime to restore", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.surface3).padding(12.dp)) {
                        Text("$linked", color = palette.primary, fontSize = 22.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        Text("Auto-linked", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.surface3).padding(12.dp)) {
                        Text("0", color = palette.primary, fontSize = 22.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        Text("Manually linked", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.surface3).padding(12.dp)) {
                        Text("$episodes", color = palette.primary, fontSize = 22.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        Text("Episodes", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Warning note
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.primary.copy(alpha = 0.07f)).border(1.dp, palette.primary.copy(alpha = 0.33f), RoundedCornerShape(16.dp)).padding(11.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Info, null, tint = palette.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("This will overwrite any existing library data. The restore process may take a few moments.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = RobotoFamily)
            }
        }
        // Fixed footer
        ActionRow(back = onBack, next = onNext, nextText = "Restore Now", palette = palette)
    }
}

@Composable
fun RestoreProcessingScreen(palette: WizardPalette, linkedAnime: List<LinkedAnime>, onNext: () -> Unit) {
    LaunchedEffect(Unit) { delay(3200); onNext() }
    val restored = linkedAnime.count { it.linked } + 239
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Restore Backup", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(180.dp).padding(vertical = 8.dp)) { RestoreProcessingVisual(palette) }
            DescriptiveTitle("Restoring your library", modifier = Modifier.fillMaxWidth())
            Subtitle("Please wait while we restore $restored anime to your library.", modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Restoring…", color = palette.primary, fontSize = 13.sp, fontFamily = RobotoFamily,
        fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(8.dp))
                repeat(3) { Box(Modifier.size(6.dp).clip(CircleShape).background(palette.primary)); Spacer(Modifier.width(4.dp)) }
            }
        }
        // Fixed footer
        Text("Please wait…", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), textAlign = TextAlign.Center)
    }
}

@Composable
fun RestoreSuccessScreen(palette: WizardPalette, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header
        PageHeading("Restore Backup", palette)
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(220.dp).padding(vertical = 8.dp)) { RestoreSuccessVisual(palette) }
            DescriptiveTitle("Restore successful!", modifier = Modifier.fillMaxWidth())
            Subtitle("Your library has been restored and is ready to go.", modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        // Fixed footer
        ActionRow(next = onNext, nextText = "Continue", palette = palette)
    }
}

@Composable
fun PoisonScreen(palette: WizardPalette, adSettings: AdSettings, onUpdate: (AdSettings) -> Unit, step: Int, onStepChange: (Int) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header — 42sp heading (50% bigger, consistent with all screens)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Choose Your Poison",
                color = Color(0xFFFF6B6B),
                fontSize = 42.sp,
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.75).sp,
                lineHeight = 46.sp,
                modifier = Modifier.padding(top = 0.dp),
            )
            Text(
                "Ads keep the app free. Let's make them non-intrusive — pick your daily dose.",
                color = Color(0xFFD9A0A0),
                fontSize = 14.sp,
                fontFamily = RobotoFamily,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Poison visual — centered when 1, equal-weight when 2-3 (all same size)
            val visualCount = if (step == 0) 1 else adSettings.frequency
            // Pill colors: red, blue, yellow for variety
            val pillColors = listOf(
                Color(0xFFE85D5D) to Color.White,  // red
                Color(0xFF5B8DEF) to Color.White,  // blue
                Color(0xFFE8C84B) to Color.White,  // yellow
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp),
                horizontalArrangement = if (visualCount == 1) Arrangement.Center else Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(visualCount) { i ->
                    val offsetY = when (i) { 0 -> (-12).dp; 1 -> 12.dp; 2 -> (-6).dp; else -> 0.dp }
                    // Equal weight so all bottles/pills are the same size
                    Box(modifier = if (visualCount == 1) Modifier.size(120.dp) else Modifier.weight(1f).height(160.dp).offset(y = offsetY)) {
                        if (adSettings.name == AdName.POISON) {
                            PoisonBottleVisual(palette, i)
                        } else {
                            val (c1, c2) = pillColors[i % 3]
                            PoisonPillVisual(palette, i, pillColor = c1, pillColor2 = c2)
                        }
                    }
                }
            }
            // Step dots
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(999.dp)).background(if (i <= step) palette.primary else palette.surface3))
                }
            }
            // Step content
            when (step) {
                0 -> {
                    Text("What should we call it?", color = Color(0xFFD9A0A0), fontSize = 12.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(999.dp)).padding(4.dp)) {
                        AdName.values().forEach { name ->
                            Row(
                                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(999.dp)).background(if (adSettings.name == name) palette.primary else Color.Transparent).clickable { onUpdate(adSettings.copy(name = name)) },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name.label, color = if (adSettings.name == name) palette.onPrimary else Color(0xFFD9A0A0), fontSize = 12.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Simpler summary
                    Text(adSettings.name.label, color = Color(0xFFFFEAEA), fontSize = 13.sp, fontFamily = RobotoFamily, modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(palette.primary.copy(alpha = 0.12f)).border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
                }
                1 -> {
                    Text("How many per day?", color = Color(0xFFD9A0A0), fontSize = 12.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(999.dp)).padding(4.dp)) {
                        listOf(1, 2, 3).forEach { n ->
                            Row(
                                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(999.dp)).background(if (adSettings.frequency == n) palette.primary else Color.Transparent).clickable { onUpdate(adSettings.copy(frequency = n)) },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$n ${if (n == 1) "ad" else "ads"}", color = if (adSettings.frequency == n) palette.onPrimary else Color(0xFFD9A0A0), fontSize = 12.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Simpler summary
                    Text("${adSettings.frequency} ${if (adSettings.frequency == 1) "ad" else "ads"} per day", color = Color(0xFFFFEAEA), fontSize = 13.sp, fontFamily = RobotoFamily, modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(palette.primary.copy(alpha = 0.12f)).border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
                }
                2 -> {
                    Text("When should they appear?", color = Color(0xFFD9A0A0), fontSize = 12.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdTiming.values().forEach { t ->
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(if (adSettings.timing == t) palette.primary else palette.surface2).border(1.dp, if (adSettings.timing == t) palette.primary else palette.surface3, RoundedCornerShape(999.dp)).clickable { onUpdate(adSettings.copy(timing = t)) }.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(t.label, color = if (adSettings.timing == t) palette.onPrimary else Color(0xFFD9A0A0), fontSize = 12.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Simpler summary: just frequency + timing (drop the name)
                    val summary = "${adSettings.frequency} ${if (adSettings.frequency == 1) "ad" else "ads"}/day · ${adSettings.timing.label}"
                    Text(summary, color = Color(0xFFFFEAEA), fontSize = 13.sp, fontFamily = RobotoFamily, modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(palette.primary.copy(alpha = 0.12f)).border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        // Fixed footer
        ActionRow(back = if (step > 0) ({ onStepChange(step - 1) }) else onBack, next = { if (step < 2) onStepChange(step + 1) else onNext() }, nextText = if (step < 2) "Next" else "Confirm", palette = palette)
    }
}

@Composable
fun FinishScreen(palette: WizardPalette, state: WizardState, onRestart: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Scrollable content
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bigger animation (200dp)
            Box(modifier = Modifier.size(200.dp).padding(vertical = 8.dp)) { FinishVisual(palette) }
            // Badge
            Row(
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, null, tint = palette.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Setup complete", color = palette.primary, fontSize = 12.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
            // Big heading — 42sp (50% bigger, consistent)
            Text("You're all set!", color = palette.primary, fontSize = 42.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.75).sp, lineHeight = 46.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Hope you have a beautiful journey ahead. Explore thousands of titles, track your progress, and never miss a new episode.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontFamily = RobotoFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            // Config summary — polished card with proper spacing + dividers
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface2).border(1.dp, palette.surface3, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Palette, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Theme", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontFamily = RobotoFamily)
                    }
                    Text("${PaletteNames[state.paletteIndex]} · ${state.themeMode.label.lowercase()}", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = palette.surface3, thickness = 1.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Folder, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Anime folder", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontFamily = RobotoFamily)
                    }
                    Text(if (state.folderSelected) "Connected" else "Skipped", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = palette.surface3, thickness = 1.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LibraryBooks, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Library restored", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontFamily = RobotoFamily)
                    }
                    Text("${state.linkedAnime.count { it.linked } + 239} anime", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = palette.surface3, thickness = 1.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Campaign, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Ads", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontFamily = RobotoFamily)
                    }
                    Text("${state.adSettings.frequency}/day · ${state.adSettings.timing.label}", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Fixed footer
        ActionRow(next = onRestart, nextText = "Start Exploring", palette = palette)
    }
}
