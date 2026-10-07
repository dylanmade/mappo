package com.mappo.ui.screen.remap

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.mappo.ui.minput.MinputAction
import com.mappo.ui.minput.MinputActionMenu
import com.mappo.ui.minput.MinputBar
import com.mappo.ui.minput.MinputBarIconTextGap
import com.mappo.ui.minput.MinputBarStackGap
import com.mappo.ui.minput.MinputBarWidgetIconSize
import com.mappo.ui.minput.MinputEdge
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputGroupButton
import com.mappo.ui.minput.MinputIconSize
import com.mappo.ui.minput.MinputSize
import com.mappo.ui.minput.MinputIcon
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputDropdownArrowSize
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
 *  - **centre** — the action-set switcher ([ActionSetSwitch]); the set-management kebab
 *    ([ActionSetMenu]) hangs off its end WITHOUT being part of what gets centred.
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
                BarStackButton(
                    appPackage = appPackage,
                    overline = if (previewing) "Previewing layout" else "Active layout",
                    value = layoutName,
                    highlighted = identityHighlighted,
                    onClick = onIdentityClick,
                    modifier = Modifier.testTag("bar:identity"),
                )
            },
            // ── centre: the action-set switcher, with its kebab hung off the end ──
            centre = {
                ActionSetSwitch(
                    config = config,
                    viewingSet = viewingSet,
                    onSelectActionSet = onSelectActionSet,
                    modifier = Modifier.testTag("bar:sets"),
                )
            },
            centreTrailing = { ActionSetMenu(config = config, onAddSet = onAddSet) },
            // ── end: which editor is on screen ──
            end = { EditorSwitcher(onEditVirtual = onEditOverlay) },
        )
    }
}

/**
 * **The bar's slots: start, centre, end — with the centre centred in the BAR** (not in the slack
 * between its neighbours), and the flanks held to what is left over.
 *
 * A Box with three alignments gets the centring right and the crowding wrong: nothing stops a
 * long layout name from running under the middle cluster, because nothing measures the two
 * against each other. So the centre is measured FIRST, at its own size, and each flank is then
 * offered exactly the room that remains on its side. A name too long for that ellipsizes
 * ([NameableText]) instead of colliding.
 *
 * **[centreTrailing] rides along beside the centre without being part of it** (Dylan,
 * 2026-09-27). The action sets' kebab used to be measured into the centred cluster, which meant
 * the thing actually being centred was "the sets plus a menu button" and the sets themselves sat
 * half a kebab to the left of the middle of the screen. The switch is the control the eye lines
 * up on — and the one that lines up with the controller image below it — so it is the anchor, and
 * the kebab hangs off its end.
 */
@Composable
private fun BarSlots(
    start: @Composable () -> Unit,
    centre: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    centreTrailing: @Composable () -> Unit = {},
) {
    Layout(
        modifier = modifier,
        contents = listOf(centre, centreTrailing, end, start),
    ) { (centreM, trailingM, endM, startM), constraints ->
        val height = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val centrePlaceable = centreM.firstOrNull()?.measure(loose)
        val centreW = centrePlaceable?.width ?: 0
        val centreLeft = (constraints.maxWidth - centreW) / 2
        val trailingPlaceable = trailingM.firstOrNull()?.measure(loose)
        val trailingW = trailingPlaceable?.width ?: 0
        val gap = SlotGap.roundToPx()
        // What a flank may take: its side of the centre, less the gap that keeps the two from
        // touching — and, on the END side, less whatever hangs off the centre there. With no
        // centre at all (a layout with no action sets) each flank simply gets half the bar.
        val startMax = (centreLeft - gap).coerceAtLeast(0)
        val endMax = (constraints.maxWidth - centreLeft - centreW - trailingW - gap).coerceAtLeast(0)
        val endPlaceable = endM.firstOrNull()?.measure(loose.copy(maxWidth = endMax))
        val startPlaceable = startM.firstOrNull()?.measure(loose.copy(maxWidth = startMax))
        layout(constraints.maxWidth, height) {
            fun place(placeable: androidx.compose.ui.layout.Placeable?, x: Int) {
                placeable?.place(x, (height - placeable.height) / 2)
            }
            place(startPlaceable, 0)
            place(centrePlaceable, centreLeft)
            place(trailingPlaceable, centreLeft + centreW)
            place(endPlaceable, constraints.maxWidth - (endPlaceable?.width ?: 0))
        }
    }
}

/**
 * The identity widget: the viewed application's launcher icon beside an overline + value
 * stack, the whole thing one chrome-less button.
 *
 * Two mounts, deliberately the SAME component (2026-09-28): the top bar's identity (ACTIVE
 * LAYOUT over the layout's name — toggles the layouts drawer) and the layouts drawer's own
 * header (the application's name alone, as the overline, with a dropdown arrow — toggles
 * applications mode; 2026-10-05, Dylan: no "Application" label, and a smaller launcher icon
 * matching the application rows). The drawer's header row sits level with the bar, on the
 * same plane, so the two read as one strip with two widgets on it.
 *
 * Chrome-less by design — the bars carry no pills now — so the OPEN state is read off the text
 * and glyph going accent rather than off a highlight plate. [dropdownArrow] adds the standard
 * dropdown triangle at the end, in the overline's colour, flipping while [highlighted] (the
 * exposed-dropdown convention). Names are user-typed or launcher labels, so they go through
 * [NameableText] (never a bare `Text` in chrome).
 */
@Composable
internal fun BarStackButton(
    appPackage: String?,
    overline: String,
    /** The second line; null = the overline alone (then it is a NAME, and ellipsizes). */
    value: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dropdownArrow: Boolean = false,
    valueMaxWidth: Dp = IdentityNameMaxWidth,
    iconSize: Dp = MinputBarWidgetIconSize,
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
            AppIconImage(icon, size = iconSize)
        } else {
            // No application in view (or no launcher icon for it): the generic apps glyph
            // keeps the widget's shape, so the stack beside it doesn't shift about.
            MinputIcon(
                Icons.Filled.Apps,
                contentDescription = null,
                size = if (iconSize >= MinputBarWidgetIconSize) MinputIconSize.M else MinputIconSize.S,
                tint = overlineColor,
            )
        }
        Spacer(Modifier.width(MinputBarIconTextGap))
        Column(
            verticalArrangement = Arrangement.spacedBy(MinputBarStackGap),
            // Never fill: the stack wraps its text, but gives way before the arrow does.
            modifier = Modifier.weight(1f, fill = false),
        ) {
            if (value == null) {
                NameableText(
                    text = overline.uppercase(),
                    style = minputOverlineTextStyle(),
                    color = overlineColor,
                    maxWidth = valueMaxWidth,
                )
            } else {
                Text(
                    text = overline.uppercase(),
                    style = minputOverlineTextStyle(),
                    color = overlineColor,
                    maxLines = 1,
                )
                NameableText(
                    text = value,
                    style = minputMiniTextStyle(),
                    color = nameColor,
                    maxWidth = valueMaxWidth,
                )
            }
        }
        if (dropdownArrow) {
            val flip by animateFloatAsState(
                targetValue = if (highlighted) 180f else 0f,
                label = "dropdownArrow",
            )
            // Ink-measured, so the gap is the whole gap — the glyph brings no margin of its own.
            Spacer(Modifier.width(MinputGlyphLabelGap))
            MinputIcon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                size = MinputDropdownArrowSize,
                tint = overlineColor,
                modifier = Modifier.rotate(flip),
            )
        }
    }
}

/**
 * The layout-set switcher: one segment per set on a [MinputGroupButton].
 *
 * **It is what the bar CENTRES on** (Dylan, 2026-09-27), which is why it is its own composable
 * rather than one half of a cluster: the switch is the control the eye lines up, so the
 * set-management kebab beside it ([ActionSetMenu]) is placed as an addendum to a centred switch
 * instead of being measured into the thing being centred. See [BarSlots].
 *
 * Segments take the library's default surface-2 fill: the bar is a real surface again, so the
 * old `minputBoxContainer()` override — which existed because the cluster rode a pod — went
 * with the pods.
 */
@Composable
private fun ActionSetSwitch(
    config: ControllerConfig?,
    viewingSet: ActionSetGraph?,
    onSelectActionSet: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sets = config?.actionSets.orEmpty()
    if (sets.isEmpty()) return
    MinputGroupButton(
        options = sets.map { it.actionSet.id },
        selected = viewingSet?.actionSet?.id ?: sets.first().actionSet.id,
        onSelect = onSelectActionSet,
        optionLabel = { id -> sets.firstOrNull { it.actionSet.id == id }?.actionSet?.title.orEmpty() },
        modifier = modifier,
    )
}

/**
 * The set-management kebab — everything about the layout sets which isn't a choice.
 *
 * **"New layout set" lives HERE** (Dylan, 2026-09-27). It used to be a "+" action segment closing
 * the group button — which the library called a sanctioned convention break and Dylan called what
 * it is: a group button is a single-CHOICE control, and a segment that fires instead of selecting
 * asks the eye to read one row as two kinds of thing. The kebab was sitting there empty next to it.
 * Rename / duplicate / delete / layers join it there as they land.
 *
 * It carries its own leading gap, because it is placed AGAINST the end of the switch rather than
 * spaced inside a Row with it (see [BarSlots]).
 */
@Composable
private fun ActionSetMenu(
    config: ControllerConfig?,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (config?.actionSets.orEmpty().isEmpty()) return
    var menuOpen by remember { mutableStateOf(false) }
    // The menu hangs off the kebab's own Box (MinputActionMenu measures its anchor itself).
    Box(modifier = modifier.padding(start = MinputGlyphLabelGap)) {
        MinputIconButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "Manage layout sets",
            onClick = { menuOpen = !menuOpen },
            modifier = Modifier.testTag("bar:sets-menu"),
        )
        MinputActionMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            actions = if (menuOpen) {
                listOf(MinputAction("New layout set", Lucide.Plus, onClick = onAddSet))
            } else emptyList(),
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
    val iconSize: MinputIconSize,
) {
    PHYSICAL(Icons.Filled.SportsEsports, "Physical buttons editor", EditorGlyphSizeSolid),
    VIRTUAL(Icons.Outlined.Layers, "Virtual buttons editor", EditorGlyphSizeStroke),
}

/**
 * The editor switch's glyph sizes, one step apart on the icon scale: the filled silhouette and
 * the stroke mark do not read alike at one size (Dylan, 2026-09-27). Ink sizes since the
 * MinputIcon migration (2026-10-06) — the same visible sizes the tuned 17dp / 14dp boxes drew.
 */
private val EditorGlyphSizeSolid = MinputIconSize.M
private val EditorGlyphSizeStroke = MinputIconSize.S

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
