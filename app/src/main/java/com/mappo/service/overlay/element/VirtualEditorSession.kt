package com.mappo.service.overlay.element

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import com.mappo.MainActivity
import com.mappo.service.input.InputDispatcher
import com.mappo.ui.nav.MappoRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the virtual set editor is open (2026-10-15). The editor is NOT its own activity any
 * more: it is the controls screen's second body, swapped in under the SAME hierarchy bar the
 * physical editor uses — picking a set of the other kind in the bar's Buttons crumb crossfades
 * the body, nothing else moves. No exit confirm: leaving is just switching back.
 *
 * Replaces `OverlayLiveEditController` + `OverlayEditActivity`, whose overlay windows drew over a
 * translucent activity of their own (Mappo backgrounded, the editor launched, Mappo relaunched on
 * exit — the "chunky" switch).
 *
 * While the editor is ON SCREEN ([onEditorShown] / [onEditorHidden], driven by its composition
 * and Mappo's lifecycle), the run-mode [OverlayPresenter] is hidden — its windows would sit above
 * Mappo's screen — and it comes back, if it was showing, when the editor leaves the screen.
 */
@Singleton
class VirtualEditorSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overlayEditor: OverlayEditor,
    private val runPresenter: OverlayPresenter,
    private val inputDispatcher: InputDispatcher,
) {

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _backdrop = MutableStateFlow<Bitmap?>(null)
    /** A frozen shot of the game, when the editor was summoned over one ([openOverGame]); null
     *  inside Mappo (there is nothing behind it to show). */
    val backdrop: StateFlow<Bitmap?> = _backdrop.asStateFlow()

    private var restoreRunOverlay = false
    private var shown = false

    /** The virtual set being edited (a layer's parent set while a layer is) — null before any. */
    val setId: StateFlow<Long?> = overlayEditor.editingSetId

    /** Edit layout [layoutId]'s virtual sets (the layout the controls screen is viewing). */
    fun editLayout(layoutId: Long?) = overlayEditor.setLayout(layoutId)

    /** Open the editor, on virtual set [virtualSetId] when given (else its current one). */
    fun open(virtualSetId: Long? = null) {
        virtualSetId?.let(overlayEditor::focusSet)
        if (_open.value) return
        _open.value = true
        Log.i(TAG, "virtual editor opened (set=$virtualSetId)")
    }

    fun close() {
        if (!_open.value) return
        _open.value = false
        _backdrop.value = null
        Log.i(TAG, "virtual editor closed")
    }

    /** The editor is on screen: get the run-mode overlay out of its way. */
    fun onEditorShown() {
        if (shown) return
        shown = true
        restoreRunOverlay = runPresenter.isShowing()
        if (restoreRunOverlay) runPresenter.hide()
    }

    /** The editor left the screen (switched away, or Mappo backgrounded): restore the overlay. */
    fun onEditorHidden() {
        if (!shown) return
        shown = false
        if (restoreRunOverlay) runPresenter.show()
        restoreRunOverlay = false
    }

    /**
     * The out-of-Mappo entry (the over-game toolbar's Edit overlay): shoot the game first so the
     * editor can show it behind the buttons, then bring Mappo up on the controls screen with the
     * editor open.
     */
    fun openOverGame() {
        val launch: (Bitmap?) -> Unit = { bmp ->
            _backdrop.value = bmp
            open()
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_ROUTE, MappoRoute.remapControls())
            runCatching { context.startActivity(intent) }
                .onFailure { Log.e(TAG, "open Mappo for the virtual editor failed", it) }
        }
        // queryPrimaryDisplayForegroundPackage is null while Mappo itself is foreground — a shot
        // then would only be Mappo's own screen.
        if (inputDispatcher.queryPrimaryDisplayForegroundPackage() != null) {
            inputDispatcher.captureScreenshot(launch)
        } else {
            launch(null)
        }
    }

    private companion object {
        const val TAG = "VirtualEditorSession"
    }
}
