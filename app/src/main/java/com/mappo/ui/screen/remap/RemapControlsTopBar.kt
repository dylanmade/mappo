package com.mappo.ui.screen.remap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.ui.component.AppIconImage
import com.mappo.ui.component.NameableText
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputBar
import com.mappo.ui.minput.MinputBarIconTextGap
import com.mappo.ui.minput.MinputBarStackGap
import com.mappo.ui.minput.MinputBarWidgetIconSize
import com.mappo.ui.minput.MinputEdge
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputGroupButton
import com.mappo.ui.minput.MinputIconButtonIconSize
import com.mappo.ui.minput.MinputPillIconSize
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle

/**
 * The controls view's top bar (2026-09-26 rebuild).
 *
 * **The bar is a SURFACE again** — a [MinputBar] strip on the bar plane with a lit bottom edge,
 * so it reads as the top face of the device's front panel (Dylan, 2026-09-26). That retires the
 * 2026-08-29 design, where the bar was transparent and every cluster rode its own `MinputPod`:
 * the pods are gone and the controls sit directly on the strip, which is also why none of them
 * wears pill chrome any more. The bottom bar changed in the same pass, for the same reason.
 *
 * Three slots, laid over the bar's Box so the centre cluster is centred in the BAR (not in the
 * slack between its neighbours):
 *  - **start** — the identity widget: the viewed application's icon, then a two-line stack
 *    (ACTIVE / PREVIEWING LAYOUT over the layout's name). Toggles the layouts drawer. This is
 *    the pre-pod design brought back, chrome-less this time.
 *  - **centre** — the action-set switcher ([ActionSetCluster]) closed by a dormant kebab.
 *  - **end** — the EDITOR switcher: which editor the screen is showing, physical buttons
 *    (this view) or virtual buttons (the overlay editor). A two-segment group button, glyphs
 *    only, the live one wearing the highlight plane — the same "this is the one you are on"
 *    marking the action sets use.
 */
@Composable
internal fun RemapControlsTopBar(
    layoutName: String,
    /** True while the layout on screen is only being PREVIEWED — it is not the active one. */
    previewing: Boolean,
    appPackage: String?,
    identityHighlighted: Boolean,
    onIdentityClick: () -> Unit,
    config: ControllerConfig?,
    viewingSet: ActionSetGraph?,
    onSelectActionSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    onEditOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MinputBar(edge = MinputEdge.BOTTOM, modifier = modifier) {
        BarSlots(
            modifier = Modifier.fillMaxSize(),
            // ── start: the identity widget (the layouts drawer's summon) ──
            start = {
                BarIdentityButton(
                    appPackage = appPackage,
                    overline = if (previewing) "Previewing layout" else "Active layout",
                    layoutName = layoutName,
                    highlighted = identityHighlighted,
                    onClick = onIdentityClick,
                    modifier = Modifier.testTag("bar:identity"),
                )
            },
            // ── centre: the action-set switcher ──
            centre = {
                ActionSetCluster(
                    config = config,
                    viewingSet = viewingSet,
                    onSelectActionSet = onSelectActionSet,
                    onAddSet = onAddSet,
                )
            },
            // ── end: which editor is on screen ──
            end = { EditorSwitcher(onEditVirtual = onEditOverlay) },
        )
    }
}

/**
 * **The bar's three slots: start, centre, end — with the centre centred in the BAR** (not in the
 * slack between its neighbours), and the flanks held to what is left over.
 *
 * A Box with three alignments gets the centring right and the crowding wrong: nothing stops a
 * long layout name from running under the middle cluster, because nothing measures the two
 * against each other. So the centre is measured FIRST, at its own size, and each flank is then
 * offered exactly the room that remains on its side. A name too long for that ellipsizes
 * ([NameableText]) instead of colliding.
 */
@Composable
private fun BarSlots(
    start: @Composable () -> Unit,
    centre: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        contents = listOf(centre, end, start),
    ) { (centreM, endM, startM), constraints ->
        val height = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val centrePlaceable = centreM.firstOrNull()?.measure(loose)
        val centreW = centrePlaceable?.width ?: 0
        // What a flank may take: its side of the centre cluster, less the gap that keeps the two
        // from touching. With no centre cluster at all (a layout with no action sets) each flank
        // simply gets half the bar.
        val flankMax = ((constraints.maxWidth - centreW) / 2 - SlotGap.roundToPx()).coerceAtLeast(0)
        val flank = loose.copy(maxWidth = flankMax)
        val endPlaceable = endM.firstOrNull()?.measure(flank)
        val startPlaceable = startM.firstOrNull()?.measure(flank)
        layout(constraints.maxWidth, height) {
            fun place(placeable: androidx.compose.ui.layout.Placeable?, x: Int) {
                placeable?.place(x, (height - placeable.height) / 2)
            }
            place(startPlaceable, 0)
            place(centrePlaceable, (constraints.maxWidth - centreW) / 2)
            place(endPlaceable, constraints.maxWidth - (endPlaceable?.width ?: 0))
        }
    }
}

/**
 * The identity widget: the viewed application's launcher icon beside an overline + layout name
 * stack, the whole thing a button that toggles the layouts drawer.
 *
 * Chrome-less by design — the bars carry no pills now — so the OPEN state is read off the text
 * and glyph going accent rather than off a highlight plate. The layout's name is user-typed, so
 * it goes through [NameableText] (never a bare `Text` in chrome).
 */
@Composable
private fun BarIdentityButton(
    appPackage: String?,
    overline: String,
    layoutName: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    val overlineColor = if (highlighted) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val nameColor = if (highlighted) accent else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .minputInteractiveMotion(interaction)
            // The ripple wants a shape to clip to even with no fill behind it.
            .clip(RoundedCornerShape(IdentityCorner))
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                onClick = onClick,
            )
            .padding(horizontal = IdentityPadding),
    ) {
        val icon = rememberAppIconPainter(appPackage)
        if (icon != null) {
            AppIconImage(icon, size = MinputBarWidgetIconSize)
        } else {
            // No application in view (or no launcher icon for it): the generic apps glyph
            // keeps the widget's shape, so the stack beside it doesn't shift about.
            Icon(
                Icons.Filled.Apps,
                contentDescription = null,
                modifier = Modifier.size(MinputBarWidgetIconSize),
                tint = overlineColor,
            )
        }
        Spacer(Modifier.width(MinputBarIconTextGap))
        Column(verticalArrangement = Arrangement.spacedBy(MinputBarStackGap)) {
            Text(
                text = overline.uppercase(),
                style = minputOverlineTextStyle(),
                color = overlineColor,
                maxLines = 1,
            )
            NameableText(
                text = layoutName,
                style = minputMiniTextStyle(),
                color = nameColor,
                maxWidth = IdentityNameMaxWidth,
            )
        }
    }
}

/**
 * The action-set switcher: one segment per set on a [MinputGroupButton], closed by the "+"
 * action segment (add a set — the library's sanctioned convention break), then the set-
 * management kebab.
 *
 * The kebab is the standard vertical "more" glyph, bare (no fill, no ring) — a dormant utility
 * affordance, not a peer of the segments beside it (Dylan, 2026-09-26; it was a chromed cog).
 * Rename / duplicate / delete / layers land on it. Layers are deliberately absent from the
 * segments themselves.
 *
 * Segments take the library's default surface-2 fill: the bar is a real surface again, so the
 * old `minputBoxContainer()` override — which existed because the cluster rode a pod — went
 * with the pods.
 */
@Composable
private fun ActionSetCluster(
    config: ControllerConfig?,
    viewingSet: ActionSetGraph?,
    onSelectActionSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sets = config?.actionSets.orEmpty()
    if (sets.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MinputGlyphLabelGap),
        modifier = modifier,
    ) {
        MinputGroupButton(
            options = sets.map { it.actionSet.id },
            selected = viewingSet?.actionSet?.id ?: sets.first().actionSet.id,
            onSelect = onSelectActionSet,
            optionLabel = { id -> sets.firstOrNull { it.actionSet.id == id }?.actionSet?.title.orEmpty() },
            trailingActionIcon = Lucide.Plus,
            trailingActionDescription = "Add action set",
            onTrailingAction = onAddSet,
        )
        MinputIconButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "Manage action sets",
            onClick = {},
            enabled = false,
        )
    }
}

/**
 * **Which editor the screen is showing** (Dylan, 2026-09-26) — the physical buttons (this view)
 * or the virtual ones (the on-screen overlay's editor).
 *
 * It replaced the "Edit overlay" button, and the difference is the point: the two editors are
 * peers, so they read as one switch with a live half rather than as a screen plus a door out of
 * it. Glyphs only — a gamepad and stacked layers — with the live editor on the highlight plane,
 * exactly as the action sets mark the set being viewed.
 *
 * The GAMEPAD is filled and a size up; the LAYERS glyph is outlined at the family's pill scale
 * (Dylan, 2026-09-26/27) — deliberately not a matched pair. A hollow gamepad is a cage of thin
 * strokes that does not read at this size, and even filled its silhouette carries less weight per
 * dp than a three-stroke mark does, so it takes the utility scale; the layers glyph reads fine as
 * it is, and filling OR enlarging it turned it into a blob.
 *
 * Physical is always the selected one here, because this composable only exists on the physical
 * editor's own screen; picking virtual launches the overlay editor, which is its own activity
 * (and its own window over the game). When the overlay editor gains a bar of its own, it wears
 * this same switch with the other half lit.
 */
@Composable
private fun EditorSwitcher(
    onEditVirtual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MinputGroupButton(
        options = EditorKind.entries.toList(),
        selected = EditorKind.PHYSICAL,
        onSelect = { kind -> if (kind == EditorKind.VIRTUAL) onEditVirtual() },
        // Blank labels: an icon-only segment (see MinputGroupButton).
        optionLabel = { "" },
        optionIcon = { it.icon },
        optionDescription = { it.description },
        optionIconSize = { it.iconSize },
        modifier = modifier.testTag("bar:editors"),
    )
}

/** The two editors the controls bar switches between, each with the size its own artwork wants. */
private enum class EditorKind(
    val icon: ImageVector,
    val description: String,
    val iconSize: Dp,
) {
    PHYSICAL(Icons.Filled.SportsEsports, "Physical buttons editor", MinputIconButtonIconSize),
    VIRTUAL(Icons.Outlined.Layers, "Virtual buttons editor", MinputPillIconSize),
}

/** Air between the bar's centre cluster and either flank — see [BarSlots]. */
private val SlotGap = 8.dp

/** Corner the identity button's ripple clips to — it has no fill, so this is shape only. */
private val IdentityCorner = 6.dp

/** Horizontal air inside the identity button, standing in for the pill padding it no longer
 *  has: enough that the ripple isn't flush against the glyph and the name. */
private val IdentityPadding = 4.dp

/** Width cap for the layout name — names are unbounded, and the bar has a cluster to fit
 *  beside them. */
private val IdentityNameMaxWidth = 180.dp
