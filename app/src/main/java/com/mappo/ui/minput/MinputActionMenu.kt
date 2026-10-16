package com.mappo.ui.minput

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One row in a [MinputActionMenu]: a leading glyph + a single-line label that FIRES rather
 * than selects. [enabled] false greys the row in place — the conventional "this action exists
 * but isn't available right now" read (an empty clipboard greying Paste), which is why
 * unavailable actions stay listed instead of disappearing.
 *
 * [destructive] tints the row with the error role for irreversible actions (Clear).
 *
 * [submenu] (2026-10-16) makes the row a CASCADE instead of a verb: activating it (tap, A, or
 * d-pad toward the chevron) opens a nested menu beside the row and leaves this menu up, and the
 * row wears the conventional trailing chevron — drawn by the menu, never by the call site, so
 * every nested menu in the app announces itself the same way. [onClick] is not invoked on such a
 * row. The slot composes the nested menu (any minput menu) inside a `Box` around the row, so it
 * anchors to the row; see [MinputSubmenu] for what it is handed.
 *
 * The menu that renders these lives in `MinputMenu.kt`, alongside [MinputDropdownMenu] — the
 * two share one surface, type scale, row height and disabled treatment.
 */
@Immutable
class MinputAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val submenu: (@Composable BoxScope.(MinputSubmenu) -> Unit)? = null,
    val onClick: () -> Unit = {},
)

/**
 * What a [MinputAction.submenu] slot is handed to drive its nested menu.
 *
 * @property expanded pass straight to the nested menu's `expanded`.
 * @property onDismissRequest the nested menu's `onDismissRequest`: closes it ALONE, back to the
 *   parent (Back / B / an outside tap / d-pad back toward the parent each step out one level).
 * @property onFinished call after a choice in the nested menu lands, to close the WHOLE cascade.
 *
 * WHERE it opens is not the slot's call: any minput menu composed here knows it is nested and
 * opens beside its parent menu by [minputCascadeSide] — its own `placement` and `caret` are ignored.
 */
@Immutable
class MinputSubmenu(
    val expanded: Boolean,
    val onDismissRequest: () -> Unit,
    val onFinished: () -> Unit,
)
