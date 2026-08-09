package com.mappo.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * App-wide IME spawn policy: **gamepad/keyboard-driven focus must never spawn the soft
 * keyboard.** A touch tap still opens it immediately; a field focused via D-pad opens it with
 * the activator button (gamepad A / Enter / D-pad center). Apply [mappoKeyboardOptions] AND
 * [Modifier.imeActivation] together on every inline text field.
 *
 * NOTE the primary defense is structural, not these helpers: app surfaces use the tap-to-edit
 * `MinputTextField` (a pill that opens an edit modal), so no inline field sits in the gamepad
 * navigation path at all. The value-based `BasicTextField` overload documents that it IGNORES
 * [KeyboardOptions.showKeyboardOnFocus], so [mappoKeyboardOptions] is inert on the inline
 * wells that remain — those live only inside editing modals, where the keyboard appearing on
 * focus is acceptable.
 */
@Composable
fun mappoKeyboardOptions(base: KeyboardOptions = KeyboardOptions.Default): KeyboardOptions {
    // In touch mode a tap both focuses and opens the IME (stock behavior). In key/gamepad
    // input mode, focus alone stays silent — the activator key below opens it.
    val touch = LocalInputModeManager.current.inputMode == InputMode.Touch
    return base.copy(showKeyboardOnFocus = touch)
}

/**
 * Opens the IME when the focused field receives the activator key (gamepad A / Enter / D-pad
 * center) — the "focus, then activate" half of the policy above. Only intercepts while the IME
 * is hidden, so Enter keeps its normal editing role once the keyboard is up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.imeActivation(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent { event ->
            val isActivator = event.key == Key.ButtonA ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter ||
                event.key == Key.DirectionCenter
            if (focused && isActivator && !imeVisible && event.type == KeyEventType.KeyDown) {
                keyboard?.show()
                true
            } else {
                false
            }
        }
}
