package app.anikuta.player.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A bottom-sheet value-entry for subtitle numeric settings. Shown from the
 * bottom of the screen (NOT a center popup) so the video player stays visible
 * behind it and the user can see subtitle changes in real time as they type.
 *
 * Uses a custom 4×3 numeric keypad (experimental) or falls back to the device
 * keyboard via an [OutlinedTextField], depending on [PlayerPreferences.useCustomKeypad].
 *
 * The input is shown LIVE on the slider value itself (via [onLiveChange]) so
 * the user sees the effect immediately — the keypad does NOT show the value
 * in its own display. The video + the original setting row (which reflects
 * the live value) act as the preview.
 *
 * @param title   What's being edited (e.g. "Font size").
 * @param initial The starting value.
 * @param suffix  Optional unit label shown after the value (e.g. "ms", "%").
 * @param min     Minimum allowed value (input clamped on confirm).
 * @param max     Maximum allowed value (input clamped on confirm).
 * @param onLiveChange Called on every keystroke with the current (unclamped) value so the slider updates live.
 * @param onConfirm Called with the user-entered value (clamped to range) when Done/OK is pressed.
 * @param onDismiss Close the sheet without applying (revert is the caller's responsibility if needed).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NumericEntrySheet(
    title: String,
    initial: Int,
    suffix: String = "",
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    onLiveChange: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    CustomKeypadSheet(
        title = title,
        initial = initial,
        suffix = suffix,
        min = min,
        max = max,
        onLiveChange = onLiveChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CustomKeypadSheet(
    title: String,
    initial: Int,
    suffix: String,
    min: Int,
    max: Int,
    onLiveChange: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf(initial.toString()) }

    // Push live updates whenever the input changes.
    val liveValue = input.toIntOrNull() ?: initial
    androidx.compose.runtime.LaunchedEffect(input) {
        onLiveChange(liveValue)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // ---- Title + value display with +/- buttons (on top) ----
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Minus button (decrement by 1, clamped to min)
                StepperButton(isPlus = false) {
                    val v = (input.toIntOrNull() ?: initial) - 1
                    input = v.coerceIn(min, max).toString()
                }
                // Value display
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (input.isEmpty()) "—" else "$input$suffix",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                // Plus button (increment by 1, clamped to max)
                StepperButton(isPlus = true) {
                    val v = (input.toIntOrNull() ?: initial) + 1
                    input = v.coerceIn(min, max).toString()
                }
            }

            // ---- Keypad grid (custom layout) ----
            // 4 columns × 4 rows. Left 3 columns = numbers. Right column =
            // DEL (spans 2 rows) + OK (spans 2 rows). Bottom row = 0 (spans 3).
            //   [1][2][3][DEL]
            //   [4][5][6][   ]   ← DEL spans 2 rows
            //   [7][8][9][OK ]
            //   [0  0  0][   ]   ← 0 spans 3 cols, OK spans 2 rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Left 3 columns: numbers 1-9 + 0 at bottom
                Column(
                    modifier = Modifier.weight(3f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KeypadButton("1", Modifier.weight(1f)) { if (input == "0") input = "1" else if (input.length < 8) input += "1" }
                        KeypadButton("2", Modifier.weight(1f)) { if (input == "0") input = "2" else if (input.length < 8) input += "2" }
                        KeypadButton("3", Modifier.weight(1f)) { if (input == "0") input = "3" else if (input.length < 8) input += "3" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KeypadButton("4", Modifier.weight(1f)) { if (input == "0") input = "4" else if (input.length < 8) input += "4" }
                        KeypadButton("5", Modifier.weight(1f)) { if (input == "0") input = "5" else if (input.length < 8) input += "5" }
                        KeypadButton("6", Modifier.weight(1f)) { if (input == "0") input = "6" else if (input.length < 8) input += "6" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KeypadButton("7", Modifier.weight(1f)) { if (input == "0") input = "7" else if (input.length < 8) input += "7" }
                        KeypadButton("8", Modifier.weight(1f)) { if (input == "0") input = "8" else if (input.length < 8) input += "8" }
                        KeypadButton("9", Modifier.weight(1f)) { if (input == "0") input = "9" else if (input.length < 8) input += "9" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KeypadButton("0", Modifier.weight(1f)) { if (input == "0") input = "0" else if (input.length < 8) input += "0" }
                    }
                }
                // Right column: DEL (top, 2 rows tall) + OK (bottom, 2 rows tall).
                // Fixed height (not weight) so the sheet doesn't expand to fill
                // the screen — each action button = 2 number rows + spacing =
                // 52 + 8 + 52 = 112dp.
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeypadButton(
                        key = "DEL",
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        onClick = { if (input.isNotEmpty()) input = input.dropLast(1) },
                    )
                    KeypadButton(
                        key = "OK",
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        onClick = {
                            val v = input.toIntOrNull() ?: initial
                            onConfirm(v.coerceIn(min, max))
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun KeypadButton(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isAction = key == "OK" || key == "DEL"
    val containerColor = when {
        key == "OK" -> MaterialTheme.colorScheme.primary
        key == "DEL" -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerHigh // numbers now use a tonal surface so they read as buttons
    }
    val contentColor = when {
        key == "OK" -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        shadowElevation = if (isAction) 2.dp else 1.dp,
        tonalElevation = if (isAction) 2.dp else 1.dp,
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            when (key) {
                "DEL" -> Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = contentColor, modifier = Modifier.size(24.dp))
                "OK" -> Icon(Icons.Default.Check, contentDescription = "Confirm", tint = contentColor, modifier = Modifier.size(26.dp))
                else -> Text(key, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = contentColor)
            }
        }
    }
}

/**
 * Compact +/- stepper button for the value display row. Tapping increments
 * or decrements the current value by 1 (clamped by the caller).
 */
@Composable
private fun StepperButton(isPlus: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlus) Icons.Default.Add else Icons.Default.Remove,
                contentDescription = if (isPlus) "Increase" else "Decrease",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
