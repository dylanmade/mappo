package com.mappo.ui.minput

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * Touch-mode focus recovery for a modal-grade surface (a panel, a morph modal, the group
 * editor): when the subtree under this modifier loses ALL focus while [enabled], re-seat
 * controller focus on [target].
 *
 * Why this exists (the group editor's device-tested lesson): any TAP flips the window into
 * touch mode, which CLEARS Compose focus wholesale — after that, d-pad navigation inside the
 * surface is dead until something re-seats it. Two guards are load-bearing:
 * - **Touch mode only.** In key mode a subtree focus loss means focus legitimately moved
 *   elsewhere — recovering would yank it straight back.
 * - **Deferred through state + LaunchedEffect.** Focus-loss also fires while the composition
 *   is being disposed, and a synchronous requestFocus mid-detach corrupts the focus-node
 *   lifecycle; an effect simply never runs on a disposing composition.
 *
 * [target] should be a real BUTTON inside the surface (its Close button by convention),
 * never a container — a focused container that spatially contains its children is a
 * directional-search dead end.
 */
@Composable
fun Modifier.minputFocusRecovery(enabled: Boolean, target: FocusRequester): Modifier {
    val inputModeManager = LocalInputModeManager.current
    // Read live inside the deferred effect — the surface may have been dismissed between the
    // focus-loss event and the effect running (e.g. a tap on its own Close button).
    val currentEnabled by rememberUpdatedState(enabled)
    var hadFocus by remember { mutableStateOf(false) }
    var recoveryTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(recoveryTick) {
        if (recoveryTick > 0 && currentEnabled && inputModeManager.inputMode == InputMode.Touch) {
            runCatching { target.requestFocus() }
        }
    }
    return onFocusChanged { state ->
        if (state.hasFocus) {
            hadFocus = true
        } else if (hadFocus && enabled) {
            recoveryTick++
        }
    }
}
