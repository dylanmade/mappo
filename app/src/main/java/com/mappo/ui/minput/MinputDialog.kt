package com.mappo.ui.minput

import android.util.Log
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mappo.ui.screen.softDropShadow

/**
 * The minput dialog: a platform [Dialog] window wearing the library's card chrome — the
 * minput-styled AlertDialog. The library directive applies at full strength here: the
 * WINDOW behavior stays stock (platform-default width policy, whole-screen dim, back /
 * outside-tap dismissal, conventional window motion, decor-fitted keyboard avoidance —
 * no hand-rolled inset or hosting logic), and only the card itself is restyled. A
 * hand-rolled in-composition hosting layer was built 2026-08-09 and deleted the same
 * day: it re-implemented all of the above, badly.
 *
 * Use this for transient cards summoned from arbitrary composition depth (a primitive's
 * editor, quick confirmations). Use [MinputModal] for surfaces that live INSIDE the app
 * canvas — the remap panels and cards stacked over them — where in-composition stacking
 * and the modal focus contract are the point.
 *
 * Gamepad B dismisses (the dialog window owns key input while up, so this is handled
 * here); back and outside-tap dismiss via the platform.
 *
 * @param anchorBottom pins the card to the BOTTOM of the dialog's keyboard-fitted frame
 *   (small margin above the keys) instead of centering in it. For dialogs whose whole
 *   life is spent typing (the text field's editor): the IME can take over half the
 *   screen, and a card centered in the remaining strip floats with equal voids above and
 *   below — anchoring it to the keyboard reads intentional.
 */
@Composable
fun MinputDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorBottom: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // Restyle, don't rebuild — window tweaks, each earned on device:
        // - Dim matched to the minput modal scrim so both modal families read as one
        //   plane system.
        // - Soft input ADJUST_NOTHING: placement is entirely the decor's inset FIT
        //   (Compose Dialog's default), which shrinks the frame by the real IME height.
        //   Leaving RESIZE on top of the fit double-applied the keyboard and crushed the
        //   card's content.
        // - anchorBottom → gravity BOTTOM within that fitted frame, a small margin up.
        //   (Disabling the fit entirely was tried and put the card UNDER the keys — the
        //   IME really is >half this landscape screen; the fit is the correct adaptive
        //   mechanism, the default CENTER gravity within it was the odd-looking part.)
        val view = LocalView.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        val anchorMarginPx = with(LocalDensity.current) { MinputDialogAnchorMargin.roundToPx() }
        SideEffect {
            dialogWindow?.setDimAmount(MinputDialogDim)
            dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            if (anchorBottom && dialogWindow != null) {
                val params = dialogWindow.attributes
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.y = anchorMarginPx
                dialogWindow.attributes = params
            }
        }

        val shape = RoundedCornerShape(MinputMorphCorner)
        val container = minputBoxContainer()
        // Geometry diagnostics for the IME-vs-dialog placement saga: card bounds, window
        // size/position on screen, display size, and the IME inset the system reports to
        // THIS window. Read via `adb logcat -s "MinputDialog:D"`.
        var lastGeometryLog by remember { mutableStateOf("") }
        Column(
            modifier = modifier
                // M3 AlertDialog's width envelope — conventional dialog sizing.
                .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
                .onGloballyPositioned { coords ->
                    val card = coords.boundsInWindow()
                    val root = view.rootView
                    val screenLoc = IntArray(2).also { root.getLocationOnScreen(it) }
                    val insets = ViewCompat.getRootWindowInsets(view)
                    val ime = insets?.getInsets(WindowInsetsCompat.Type.ime())
                    val dm = view.resources.displayMetrics
                    val geometry =
                        "card=${card.top.toInt()}..${card.bottom.toInt()}h${(card.bottom - card.top).toInt()} " +
                            "window=${root.width}x${root.height}@y${screenLoc[1]} " +
                            "display=${dm.widthPixels}x${dm.heightPixels} imeBottomInset=${ime?.bottom}"
                    if (geometry != lastGeometryLog) {
                        lastGeometryLog = geometry
                        Log.d("MinputDialog", geometry)
                    }
                }
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

/** Gap kept between an [anchorBottom] card and the frame bottom (≈ the keyboard top). */
private val MinputDialogAnchorMargin = 12.dp
