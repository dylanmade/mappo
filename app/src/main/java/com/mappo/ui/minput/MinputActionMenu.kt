package com.mappo.ui.minput

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
 * The menu that renders these lives in `MinputMenu.kt`, alongside [MinputDropdownMenu] — the
 * two share one surface, type scale, row height and disabled treatment.
 */
@Immutable
class MinputAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)
