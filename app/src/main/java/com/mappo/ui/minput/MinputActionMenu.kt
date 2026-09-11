package com.mappo.ui.minput

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One row in a [MinputActionMenu]: a leading glyph + a single-line label that FIRES rather
 * than selects. [enabled] false greys the row in place — the conventional "this action exists
 * but isn't available right now" read (an empty clipboard greying Paste), which is why
 * unavailable actions stay listed instead of disappearing.
 *
 * [destructive] tints the row with the error role for irreversible actions (Clear).
 */
@Immutable
class MinputAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The minput ACTION menu: a flat list of one-line commands summoned by a control.
 *
 * Styles M3's `DropdownMenu` + `DropdownMenuItem` (behavior, dismissal, and menu motion stay
 * stock, per the library's styling-over-reinvention pillar). Distinct from
 * [MinputDropdownMenu], which is a single-CHOICE picker — it tracks a `current` value and
 * renders a check on it. Nothing here is "current": every row is a verb, so there is no
 * selection state and no trailing check.
 *
 * Deliberately single-line, with NO helper text: the remap group editor's tile menus name
 * self-evident verbs (Edit / Copy / Paste / Move / Clear) where a second line of prose would
 * be noise. The two-line icon + title + helper form lives on `RichMenuItem` in the remap
 * package — reach for that one when an action genuinely needs tutorializing.
 *
 * Picking dismisses FIRST, then invokes — so a handler that opens another surface isn't
 * racing this menu's exit animation.
 */
@Composable
fun MinputActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<MinputAction>,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        actions.forEach { action ->
            val tint = when {
                action.destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            DropdownMenuItem(
                enabled = action.enabled,
                leadingIcon = {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(MinputActionMenuIconSize),
                        tint = tint,
                    )
                },
                text = {
                    Text(
                        action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (action.destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
                onClick = { onDismissRequest(); action.onClick() },
            )
        }
    }
}

/** Leading-glyph edge in an action menu. Matches the pill family's leading-icon scale so a
 *  menu summoned by a pill reads as the same family. */
private val MinputActionMenuIconSize = 18.dp
