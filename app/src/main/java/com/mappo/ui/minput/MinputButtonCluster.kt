package com.mappo.ui.minput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * **A button cluster** (2026-11-11): several ACTIONS sharing one container — one pill (the box
 * plane + the family bevel, [size]'s height and corner, so it measures like a [MinputButton] of
 * that variant beside it), its members bare icon buttons parted by [MinputClusterDivider]s. For
 * verbs that belong together and act alike (the virtual button editor's Undo | Redo).
 *
 * Not a [MinputGroupButton]: nothing here is selected — every member fires. The M3 relative is the
 * connected button group / split button; minput owns only the chrome, the members are ordinary
 * [MinputButton]s ([MinputClusterButton]).
 *
 * [vertical] stacks the members down a column of the variant's WIDTH — for a side-pinned bar.
 */
@Composable
fun MinputButtonCluster(
    modifier: Modifier = Modifier,
    size: MinputSize = MinputSize.Standard,
    vertical: Boolean = false,
    content: @Composable () -> Unit,
) {
    val container = minputBoxContainer()
    Surface(
        shape = RoundedCornerShape(size.corner),
        color = container,
        border = minputBevelBorder(container, size.corner),
        modifier = modifier,
    ) {
        // The stroke rule: members sit inside the ring. The end inset keeps the outer members'
        // glyphs off the pill's round ends.
        if (vertical) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(size.height).padding(vertical = MinputClusterEndInset),
            ) { content() }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(size.height).padding(horizontal = MinputClusterEndInset),
            ) { content() }
        }
    }
}

/** One member of a [MinputButtonCluster]: a bare icon button at the cluster's [size], its glyph
 *  at the variant's size and the chromed buttons' label strength (the cluster is its chrome). */
@Composable
fun MinputClusterButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: MinputSize = MinputSize.Standard,
) {
    MinputButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        bare = true,
        // onSurface, not the bare utility grey.
        filled = true,
        leadingIcon = icon,
        iconSize = size.iconSize,
        size = size,
        contentDescription = contentDescription,
    )
}

/** The hairline parting two members of a [MinputButtonCluster] — across the row, or (with
 *  [vertical], matching the cluster's) across the column. */
@Composable
fun MinputClusterDivider(
    modifier: Modifier = Modifier,
    size: MinputSize = MinputSize.Standard,
    vertical: Boolean = false,
) {
    val length = size.height - MinputClusterDividerInset * 2
    Box(
        modifier
            .then(if (vertical) Modifier.width(length).height(1.dp) else Modifier.height(length).width(1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Air between a cluster's round end and its first / last member. */
private val MinputClusterEndInset = 2.dp

/** How far a cluster divider stops short of the container's edges, each end. */
private val MinputClusterDividerInset = 5.dp
