package com.mappo.ui.minput

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.mappo.ui.screen.softDropShadow

/**
 * The minput dialog: a platform [Dialog] window wearing the library's card chrome — the
 * minput-styled AlertDialog. The library directive applies at full strength here: the
 * WINDOW behavior is entirely stock (centered, platform-default width policy, whole-screen
 * dim, back / outside-tap dismissal, conventional window motion, and panning above the
 * soft keyboard — no hand-rolled inset or hosting logic), and only the card itself is
 * restyled. A hand-rolled in-composition hosting layer was built 2026-08-09 and deleted
 * the same day: it re-implemented all of the above, badly.
 *
 * Use this for transient cards summoned from arbitrary composition depth (a primitive's
 * editor, quick confirmations). Use [MinputModal] for surfaces that live INSIDE the app
 * canvas — the remap panels and cards stacked over them — where in-composition stacking
 * and the modal focus contract are the point.
 *
 * Gamepad B dismisses (the dialog window owns key input while up, so this is handled
 * here); back and outside-tap dismiss via the platform.
 */
@Composable
fun MinputDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // Restyle, don't rebuild: the only window tweak is the dim, matched to the minput
        // modal scrim so both modal families read as one plane system.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(MinputDialogDim) }

        val shape = RoundedCornerShape(MinputMorphCorner)
        val container = minputBoxContainer()
        Column(
            modifier = modifier
                // M3 AlertDialog's width envelope — conventional dialog sizing.
                .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.ButtonB) {
                        if (event.type == KeyEventType.KeyDown) onDismissRequest()
                        true
                    } else {
                        false
                    }
                }
                .softDropShadow(cornerRadius = MinputMorphCorner)
                .clip(shape)
                .background(container)
                .border(minputBevelBorder(container, MinputMorphCorner), shape)
                .padding(MinputDialogPadding),
        ) {
            // The card paints its plane with a raw background, so it must provide the
            // content color a Surface would.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                content()
            }
        }
    }
}

/** Window dim matched to the [MinputModal] scrim (scrim @ 0.32 alpha). */
private const val MinputDialogDim = 0.32f

/** Content inset of the dialog card — the library's dense take on AlertDialog's 24dp. */
private val MinputDialogPadding = 10.dp
