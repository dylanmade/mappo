package com.mappo.ui.minput

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mappo.ui.screen.softDropShadow

/**
 * The minput dialog: a platform [Dialog] window wearing the library's card chrome — the
 * minput-styled AlertDialog. Window behavior stays stock wherever stock exists:
 * whole-screen dim (matched to the minput scrim), back / outside-tap / gamepad-B
 * dismissal, conventional window motion.
 *
 * Keyboard placement is the one deliberate deviation from stock, and it is LAYOUT MATH,
 * not window mechanics: the card rests dead-center, and a rising keyboard pushes it up
 * only as far as needed to keep [MinputDialogImeGap] above the keys. Placement reads the
 * ANIMATED [WindowInsets.Companion.ime] value, so the card rides the keyboard's own
 * animation curve in both directions. With no keyboard the same math is plain centering,
 * so non-typing dialogs need nothing. (Stock decor fitting instead re-centers the card
 * in the strip left above the keyboard — on a landscape device whose IME takes over half
 * the screen, that parks the card in mid-air far from the keys; and every attempt to fix
 * placement at the WINDOW layer — gravity flips, inset listeners, soft-input modes —
 * snapped into place instead of animating. Layout on the animated inset is the whole
 * answer; don't reintroduce window mechanics here.)
 *
 * Use this for transient cards summoned from arbitrary composition depth (a primitive's
 * editor, quick confirmations). Use [MinputModal] for surfaces that live INSIDE the app
 * canvas — the remap panels and cards stacked over them — where in-composition stacking
 * and the modal focus contract are the point.
 */
@Composable
fun MinputDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        // Full-screen transparent host, so the composition receives the RAW, animated
        // ime insets — decor fitting would consume them (and reposition with a snap).
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Restyle, don't rebuild: the only window tweak is the dim, matched to the
        // minput modal scrim so both modal families read as one plane system.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(MinputDialogDim) }

        val imeInsets = WindowInsets.ime
        val shape = RoundedCornerShape(MinputMorphCorner)
        val container = minputBoxContainer()
        Box(
            Modifier
                .fillMaxSize()
                // Physical B cancels, matching every minput modal's close affordance;
                // the dialog window owns key input while up.
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.ButtonB) {
                        if (event.type == KeyEventType.KeyDown) onDismissRequest()
                        true
                    } else {
                        false
                    }
                },
        ) {
            // The host window covers the whole screen, so "outside" taps land here —
            // this layer reproduces dismissOnClickOutside. Never a d-pad stop.
            Box(
                Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            Layout(
                content = {
                    Column(
                        modifier = modifier
                            .width(MinputDialogWidth)
                            // The card swallows its own taps so they can't fall through
                            // to the dismiss layer beneath.
                            .pointerInput(Unit) { detectTapGestures { } }
                            .softDropShadow(cornerRadius = MinputMorphCorner)
                            .clip(shape)
                            .background(container)
                            .border(minputBevelBorder(container, MinputMorphCorner), shape)
                            .padding(MinputDialogPadding),
                    ) {
                        // The card paints its plane with a raw background, so it must
                        // provide the content color a Surface would.
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            content()
                        }
                    }
                },
            ) { measurables, constraints ->
                val card = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
                // Reading the animated inset during measure re-runs placement every
                // frame of the keyboard's animation — the card rides its curve.
                val imeBottom = imeInsets.getBottom(this)
                val gap = MinputDialogImeGap.roundToPx()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val centerY = (constraints.maxHeight - card.height) / 2
                    val clearY = constraints.maxHeight - imeBottom - gap - card.height
                    val y = minOf(centerY, clearY).coerceAtLeast(gap)
                    card.place((constraints.maxWidth - card.width) / 2, y)
                }
            }
        }
    }
}

/** Window dim matched to the [MinputModal] scrim (scrim @ 0.32 alpha). */
private const val MinputDialogDim = 0.32f

/** Content inset of the dialog card — the library's dense take on AlertDialog's 24dp. */
private val MinputDialogPadding = 10.dp

/** Card width — the conventional resting width inside M3 AlertDialog's 280–560dp
 *  envelope (and what the platform default width policy gave the card on device). */
private val MinputDialogWidth = 360.dp

/** Gap kept between the card and the top of a visible keyboard. */
private val MinputDialogImeGap = 12.dp
