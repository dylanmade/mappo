package com.mappo.ui.screen.remap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mappo.R
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ActivatorGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.displayLabel
import com.mappo.data.model.steam.displayNameFor
import com.mappo.data.model.steam.InputSource
import com.mappo.service.input.modes.SourceModeCatalog
import com.mappo.ui.component.MoveModeState
import com.mappo.ui.component.moveModeCell
import com.mappo.ui.component.moveModeLongPressSource
import com.mappo.ui.component.rememberMoveModeState
import com.mappo.ui.glyph.InputGlyphs
import com.mappo.ui.screen.displayLabel as activatorDisplayLabel
import com.mappo.ui.screen.remap.settings.SourceModeSettingsSchema
import com.mappo.ui.theme.LocalMappoExtraColors
import com.mappo.ui.minput.MinputAction
import com.mappo.ui.minput.MinputActionMenu
import com.mappo.ui.minput.MinputBoxStroke
import com.mappo.ui.minput.MinputElevatedContainer
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputPanelDividerInset
import com.mappo.ui.minput.MinputPanelHeaderHeight
import com.mappo.ui.minput.MinputPillIconSize
import com.mappo.ui.minput.MinputTextEditDialog
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import kotlinx.coroutines.delay

/**
 * The expanded ("advanced") in-place editor a group box grows into.
 *
 * **Rebuilt 2026-09-11 as a TABLE.** The old form was a vertical list of command rows that grew
 * downward as the user added commands, each row carrying its own press-type pill, output button,
 * label field, cog and kebab. The table replaces it: one row per physical input, one COLUMN per
 * press type, and a cell at every intersection. Reading "what does Y do when I hold it" is now a
 * lookup instead of a scan, and the grid is fixed-size — it can't grow unboundedly, which is what
 * made the list form hard to design the basic views against.
 *
 * The trade, accepted deliberately: a cell holds at most ONE command, so the old "several
 * commands of the same press type on one input" affordance is gone from the UI. The schema still
 * allows it (see the cell-ops block in `ControllerConfigRepository`); the table just doesn't
 * surface it.
 *
 * Anatomy: sticky header (group identity · flow arrow · mode pill · cog/kebab/close) over an
 * inset divider, then the table — a frozen glyph column pinned at the start, and a horizontally
 * scrolling body carrying the press-type header row and every cell.
 *
 * Base-set view edits inline; layer view resolves override→base, renders read-only, and routes
 * cell taps to the full-screen editor (which materializes the override).
 */
internal class RemapGroupEditorCallbacks(
    val onSetBindingGroupMode: (bindingGroupId: Long, mode: BindingMode) -> Unit,
    val onOpenModeSettings: (bindingGroupId: Long, source: InputSource) -> Unit,
    val onEditCommand: (bindingId: Long, current: BindingOutput, title: String) -> Unit,
    val onOpenInputEditor: (inputSource: InputSource, groupInputKey: String, label: String) -> Unit,
    val onClearOverride: (inputSource: InputSource, groupInputKey: String) -> Unit,
    val onSetLabel: (bindingId: Long, label: String) -> Unit,
    val onResetGroup: (bindingGroupId: Long) -> Unit,
    val onConfigure: (activatorId: Long, title: String) -> Unit,
    // ── Cell ops (see ControllerConfigRepository's advanced-table block) ──
    /** Ensure the cell exists, then open the command picker against it. Drives both the empty
     *  tile's "New" and the defined tile's "Edit" — the difference is only whether the ensure
     *  step has anything to do. */
    val onAssignCell: (
        bindingGroupId: Long,
        inputKey: String,
        type: ActivatorType,
        current: BindingOutput,
        title: String,
    ) -> Unit,
    val onClearCell: (bindingGroupId: Long, inputKey: String, type: ActivatorType) -> Unit,
    val onCopyCell: (bindingGroupId: Long, inputKey: String, type: ActivatorType) -> Unit,
    val onPasteCell: (bindingGroupId: Long, inputKey: String, type: ActivatorType) -> Unit,
    val onMoveCell: (
        bindingGroupId: Long,
        fromKey: String, fromType: ActivatorType,
        toKey: String, toType: ActivatorType,
    ) -> Unit,
    /** Whether anything has been copied this session — greys Paste rather than hiding it. */
    val clipboardOccupied: Boolean,
)

/**
 * The table's press-type columns, in the user's specified order. Note this is NOT
 * [pressTypeOrder] (the canonical Steam render order) — the table puts Chord before the
 * Down/Up edge triggers because the edge pair reads as a tail-end special case.
 *
 * `SOFT_PRESS` is absent by design: it's a sub-input (the trigger's "soft_press" row), not an
 * activator the user picks. See `feedback_soft_press_unified_to_soft_pull`.
 */
internal val pressTypeColumns = listOf(
    ActivatorType.FULL_PRESS,
    ActivatorType.LONG_PRESS,
    ActivatorType.DOUBLE_PRESS,
    ActivatorType.CHORDED_PRESS,
    ActivatorType.START_PRESS,
    ActivatorType.RELEASE_PRESS,
)

/**
 * Identity accent for a press-type column, or **null for [ActivatorType.FULL_PRESS]** — the
 * standard press deliberately keeps the ordinary control surface, so "no accent" is a real
 * value here rather than a missing one. Sourced from [LocalMappoExtraColors] because these are
 * fixed learned hues, not theme roles (see the doc on `MappoExtraColors`).
 */
@Composable
internal fun ActivatorType.columnAccent(): Color? {
    val extras = LocalMappoExtraColors.current
    return when (this) {
        ActivatorType.FULL_PRESS -> null
        ActivatorType.LONG_PRESS -> extras.pressLong
        ActivatorType.DOUBLE_PRESS -> extras.pressDouble
        ActivatorType.CHORDED_PRESS -> extras.pressChord
        ActivatorType.START_PRESS -> extras.pressDown
        ActivatorType.RELEASE_PRESS -> extras.pressUp
        ActivatorType.SOFT_PRESS -> null
    }
}

/** Which cell a table coordinate names. The move-mode key type, and the identity the tile
 *  menus act on. */
internal data class CellKey(val inputKey: String, val type: ActivatorType)

@Composable
internal fun RemapGroupEditor(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // Landing spot for controller focus when the editor opens (and after a tap wipes focus):
    // the TOP-LEFT tile — the first input's Press cell — falling back to the always-present
    // Close button when the table can't take focus (layer view). Directional focus can't step
    // INTO an overlay from a focused container that spatially contains everything, so the
    // simple view requests focus here explicitly.
    focusRequester: FocusRequester? = null,
) {
    // The header's mode pill edits the group's PRIMARY source (first row's source). Multi-source
    // groups (shoulder = trigger + bumper, utility = Start + Select) keep their secondary
    // sources' modes reachable through the cells' full-screen editor for now.
    val primarySource = group.rows.first().source
    val primaryGroup = viewingLayer?.presetFor(primarySource)?.group?.group
        ?: viewingSet?.presetFor(primarySource)?.group?.group
    val validModes = SourceModeCatalog.modesValidFor(primarySource)
    val modeName = primaryGroup?.mode?.displayNameFor(primarySource) ?: "mode"
    val editable = viewingLayer == null
    var headerMore by remember { mutableStateOf(false) }

    // Escape hatch for d-pad UP out of the table's top row: the table is a focus group whose
    // bounds contain every tile, so an unresolved UP search would pick the group itself as
    // "above" and re-enter at its first focusable, trapping focus. Top-row tiles route UP
    // explicitly, set DIRECTLY on each tile's own node — an ancestor-cascaded route proved to
    // win over per-child overrides, collapsing every route to one target.
    val headerKebabFocus = remember { FocusRequester() }
    val headerModePillFocus = remember { FocusRequester() }
    val headerCloseFocus = remember { FocusRequester() }
    val modePillFocusable = primaryGroup != null && validModes.isNotEmpty() &&
        editable && validModes.size > 1
    val headerCogFocusable = primaryGroup != null &&
        SourceModeSettingsSchema.hasSettings(primarySource, primaryGroup.mode)
    val tableUpTarget = if (modePillFocusable) headerModePillFocus else headerKebabFocus

    Column(modifier = modifier) {
        // ── Sticky header ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EditorHeaderHeight)
                // Same horizontal inset as the table — the header's chrome must sit flush
                // with the table's columns.
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Non-interactive group identity: hardware glyph + overline label. The Kenney
            // prompt is single-color, so it tints down to the overline treatment safely.
            Icon(
                InputGlyphs.sourcePainter(primarySource),
                contentDescription = null,
                modifier = Modifier.size(MinputPillIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(MinputGlyphLabelGap))
            Text(
                text = group.headerLabel().uppercase(),
                style = minputOverlineTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            EditorFlowArrow()
            Spacer(Modifier.width(8.dp))
            if (primaryGroup != null && validModes.isNotEmpty()) {
                ModePillDropdown(
                    source = primarySource,
                    currentMode = primaryGroup.mode,
                    validModes = validModes,
                    enabled = editable && validModes.size > 1,
                    onPick = { mode -> callbacks.onSetBindingGroupMode(primaryGroup.id, mode) },
                    overline = true,
                    elevated = true,
                    modifier = Modifier.focusRequester(headerModePillFocus),
                )
            } else {
                Text(
                    text = "DEFAULT",
                    style = minputOverlineTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            MinputIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Configure $modeName",
                onClick = { primaryGroup?.let { callbacks.onOpenModeSettings(it.id, primarySource) } },
                enabled = headerCogFocusable,
            )
            Box {
                RowKebab(
                    onClick = { headerMore = true },
                    contentDescription = "Group options",
                    modifier = Modifier.focusRequester(headerKebabFocus),
                )
                DropdownMenu(expanded = headerMore, onDismissRequest = { headerMore = false }) {
                    RichMenuItem(
                        title = "Import $modeName",
                        helper = "Bring in a mode and inputs from another layout.",
                        icon = Icons.Filled.Download,
                        // Future: layout import. Inert while the acquisition flow lands.
                        onClick = { headerMore = false },
                    )
                    RichMenuItem(
                        title = "Reset $modeName",
                        helper = "Return this group to its defaults.",
                        icon = Icons.Filled.RestartAlt,
                        enabled = editable && primaryGroup != null,
                        onClick = {
                            headerMore = false
                            primaryGroup?.let { callbacks.onResetGroup(it.id) }
                        },
                    )
                }
            }
            // No spacer: cog·kebab·close sit adjacent at one rhythm.
            MinputIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                modifier = Modifier
                    .focusRequester(headerCloseFocus)
                    .then(
                        if (focusRequester != null && !editable) {
                            Modifier.focusRequester(focusRequester)
                        } else Modifier,
                    ),
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = MinputPanelDividerInset))

        AdvancedTable(
            group = group,
            viewingSet = viewingSet,
            viewingLayer = viewingLayer,
            config = config,
            callbacks = callbacks,
            editable = editable,
            upTarget = tableUpTarget,
            focusRequester = focusRequester.takeIf { editable },
        )
    }
}

/**
 * The table proper.
 *
 * Two side-by-side columns inside ONE vertical scroller: a frozen glyph column, then the body
 * in a horizontal scroller. Freezing the glyphs is the whole point of splitting them out — a
 * user scrolled to the Up column must still be able to see which button the row belongs to.
 * Alignment between the two halves is structural, not synchronized: both use the same fixed
 * [TileHeight] and [TileGap], so they can't drift.
 */
@Composable
private fun AdvancedTable(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    editable: Boolean,
    upTarget: FocusRequester,
    focusRequester: FocusRequester?,
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val moveState = rememberMoveModeState<CellKey>()
    val haptic = LocalHapticFeedback.current
    // Which command's label the "Label" verb is editing: bindingId to its current text. The
    // table has no resting label FIELD any more (the cell renders the label as overline text),
    // so the editor dialog is summoned directly rather than by a MinputTextField pill.
    var labelTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    // Row index lookup for the controller move path's directional stepping — the table knows
    // its own neighbors, so MoveModeState delegates that resolution here (see its KDoc).
    val rowKeys = group.rows.map { it.subInputKey }

    fun stepMoveTarget(dRow: Int, dCol: Int) {
        val current = moveState.target ?: return
        val r = rowKeys.indexOf(current.inputKey).takeIf { it >= 0 } ?: return
        val c = pressTypeColumns.indexOf(current.type).takeIf { it >= 0 } ?: return
        val nr = (r + dRow).coerceIn(0, rowKeys.lastIndex)
        val nc = (c + dCol).coerceIn(0, pressTypeColumns.lastIndex)
        if (nr != r || nc != c) {
            // A tick per cell crossed: with no finger on the screen the haptic is the only
            // confirmation that the drop target actually moved.
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            moveState.moveTargetTo(CellKey(rowKeys[nr], pressTypeColumns[nc]))
        }
    }

    // Resolve the binding group that owns a row's source. Rows in a multi-source group
    // (shoulder = trigger + bumper) resolve independently.
    fun bindingGroupIdFor(spec: SimpleRowSpec): Long? =
        viewingSet?.presetFor(spec.source)?.group?.group?.id

    fun commitMove(pair: Pair<CellKey, CellKey>?) {
        val (from, to) = pair ?: return
        val spec = group.rows.firstOrNull { it.subInputKey == from.inputKey } ?: return
        val groupId = bindingGroupIdFor(spec) ?: return
        callbacks.onMoveCell(groupId, from.inputKey, from.type, to.inputKey, to.type)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // While a CONTROLLER move is in flight the table owns the d-pad: arrows walk the
            // drop target and B/Escape cancels, while the lifted tile keeps focus and owns
            // the confirm. Pointer-driven moves don't take this path — the finger is already
            // saying where to land.
            .onKeyEvent { event ->
                if (!moveState.active || moveState.pointerDriven) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                when (event.key) {
                    // Arrows walk the drop target. Consuming them is what stops normal focus
                    // traversal — focus must stay on the lifted tile for the whole move.
                    Key.DirectionUp -> { stepMoveTarget(-1, 0); true }
                    Key.DirectionDown -> { stepMoveTarget(1, 0); true }
                    Key.DirectionLeft -> { stepMoveTarget(0, -1); true }
                    Key.DirectionRight -> { stepMoveTarget(0, 1); true }
                    Key.Back, Key.Escape, Key.ButtonB -> { moveState.cancel(); true }
                    // NB: the activate keys are deliberately absent. The focused tile handles
                    // its own activation (see CommandTile) and events reach it first; a commit
                    // branch here would be a second, competing path.
                    else -> true // swallow everything else so focus can't wander mid-move
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vScroll)
                // On the scrollable node, not the outer Box — test scroll-to-node and
                // accessibility scroll actions both need the semantics to sit where the
                // scroll modifier is.
                .testTag("group-editor-table")
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            // ── Frozen glyph column ("column zero") ───────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
                Spacer(Modifier.height(ColumnHeaderHeight))
                group.rows.forEach { spec ->
                    Box(
                        modifier = Modifier.width(GlyphColumnWidth).height(TileHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        InputGlyphs.SubInputGlyph(
                            source = spec.source,
                            subInputKey = spec.subInputKey,
                            size = TableGlyphSize,
                        )
                    }
                }
            }
            Spacer(Modifier.width(TileGap))

            // ── Scrolling body: header row + cells ────────────────────────
            Column(
                modifier = Modifier.horizontalScroll(hScroll),
                verticalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    pressTypeColumns.forEach { type -> PressColumnHeader(type) }
                }
                group.rows.forEachIndexed { rowIndex, spec ->
                    // Layer view resolves override→base (ghost semantics): the layer's own
                    // group input wins when it exists, otherwise the base set's shows through.
                    val layerGroupInput = viewingLayer?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
                    val baseGroupInput = viewingSet?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
                    val groupInput = layerGroupInput ?: baseGroupInput
                    val subLabel = RemapSections.labelFor(spec.source, spec.subInputKey)
                    val groupId = bindingGroupIdFor(spec)

                    Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        pressTypeColumns.forEach { type ->
                            val cellKey = CellKey(spec.subInputKey, type)
                            val activator = groupInput?.firstActivatorOfType(type)
                            val binding = activator?.bindings?.firstOrNull()
                            val output = activator?.primaryOutput ?: BindingOutput.Unbound
                            val defined = binding != null && output != BindingOutput.Unbound
                            val title = "$subLabel · ${type.activatorDisplayLabel()}"

                            CommandTile(
                                accent = type.columnAccent(),
                                output = output.takeIf { defined },
                                label = binding?.label?.takeIf { it.isNotBlank() },
                                config = config,
                                enabled = editable && groupId != null,
                                cellKey = cellKey,
                                moveState = moveState,
                                onCommitMove = { commitMove(it) },
                                actions = {
                                    if (!editable) {
                                        // Layer view is read-only here: editing routes to the
                                        // full-screen editor (which materializes the override
                                        // onto the layer), and an input the layer actually
                                        // overrides can be handed back to the base set.
                                        layerTileActions(
                                            overridden = layerGroupInput != null,
                                            onEdit = {
                                                callbacks.onOpenInputEditor(spec.source, spec.subInputKey, subLabel)
                                            },
                                            onClearOverride = {
                                                callbacks.onClearOverride(spec.source, spec.subInputKey)
                                            },
                                        )
                                    } else {
                                        tileActions(
                                            defined = defined,
                                            clipboardOccupied = callbacks.clipboardOccupied,
                                            onEdit = {
                                                if (groupId != null) {
                                                    callbacks.onAssignCell(groupId, spec.subInputKey, type, output, title)
                                                }
                                            },
                                            onLabel = { binding?.let { labelTarget = it.id to it.label.orEmpty() } },
                                            onSettings = { activator?.let { callbacks.onConfigure(it.activator.id, title) } },
                                            onCopy = {
                                                if (groupId != null) callbacks.onCopyCell(groupId, spec.subInputKey, type)
                                            },
                                            onPaste = {
                                                if (groupId != null) callbacks.onPasteCell(groupId, spec.subInputKey, type)
                                            },
                                            onMove = { moveState.pickUp(cellKey, byPointer = false) },
                                            onClear = {
                                                if (groupId != null) callbacks.onClearCell(groupId, spec.subInputKey, type)
                                            },
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .then(
                                        // Top row escapes UP to the header; every other row
                                        // steps normally.
                                        if (rowIndex == 0) {
                                            Modifier.focusProperties { up = upTarget }
                                        } else Modifier,
                                    )
                                    .then(
                                        if (focusRequester != null && rowIndex == 0 &&
                                            type == ActivatorType.FULL_PRESS
                                        ) {
                                            Modifier.focusRequester(focusRequester)
                                        } else Modifier,
                                    ),
                            )
                        }
                    }
                }
            }
        }

        labelTarget?.let { (bindingId, current) ->
            MinputTextEditDialog(
                title = "Command label",
                initial = current,
                placeholder = "Label",
                onCommit = { callbacks.onSetLabel(bindingId, it) },
                onClose = { labelTarget = null },
            )
        }
    }
}

/** Column header: the press type's concept glyph over/before its overline name, both wearing
 *  the column accent so the header reads as the head of its colored stack. */
@Composable
private fun PressColumnHeader(type: ActivatorType) {
    val accent = type.columnAccent() ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.width(TileWidth).height(ColumnHeaderHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            type.pressIcon(),
            contentDescription = null,
            modifier = Modifier.size(ColumnHeaderIconSize),
            tint = accent,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = type.shortLabel().uppercase(),
            style = minputOverlineTextStyle(),
            color = accent,
            maxLines = 1,
        )
    }
}

/**
 * One cell.
 *
 * **Defined** ([output] non-null): a tall rounded button on the column's accent-tinted surface,
 * carrying the output glyph ahead of a stack of (optional) label over output text. The label
 * takes the overline treatment above the output's ordinary mini text — no separate label FIELD
 * exists any more, which is what bought the height.
 *
 * **Empty** ([output] null): transparent, strokeless, a dimmed "+" in the column accent. The
 * absence of any chrome is the signal — an empty cell shouldn't compete with real assignments
 * for attention across a six-column row.
 *
 * Output glyph and text deliberately do NOT take the column accent (readability); only the
 * container tint, the header, and the empty "+" carry it.
 */
@Composable
private fun CommandTile(
    accent: Color?,
    output: BindingOutput?,
    label: String?,
    config: ControllerConfig?,
    enabled: Boolean,
    cellKey: CellKey,
    moveState: MoveModeState<CellKey>,
    onCommitMove: (Pair<CellKey, CellKey>?) -> Unit,
    actions: () -> List<MinputAction>,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    val isOrigin = moveState.origin == cellKey
    val isTarget = moveState.active && moveState.target == cellKey

    val base = MinputElevatedContainer
    val container = when {
        output == null -> Color.Transparent
        accent == null -> base
        else -> accent.copy(alpha = TileAccentAlpha).compositeOver(base)
    }
    val shape = RoundedCornerShape(TileCorner)

    // Controller hold-to-move: a key-down starts a timer; crossing the long-press threshold
    // while still held lifts the tile instead of opening the menu. Hardware auto-repeat
    // re-delivers KeyDown, so the timestamp is only taken on the first one.
    var keyDownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(keyDownAt) {
        if (keyDownAt == 0L) return@LaunchedEffect
        delay(viewConfiguration.longPressTimeoutMillis)
        if (keyDownAt != 0L && !moveState.active) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            moveState.pickUp(cellKey, byPointer = false)
        }
    }

    /**
     * What activating this tile means — shared by the pointer tap and the key-up path.
     *
     * Written to be safe to run TWICE and in either order, because both `clickable` (which
     * handles Enter/DpadCenter itself) and this file's own key handler can fire for one
     * physical press, and their relative order depends on modifier-chain details that are not
     * worth depending on. The gamepad A button is why the key handler exists at all —
     * `clickable` doesn't recognize it.
     *
     * The idempotence trick: during a controller move, confirming only does something when the
     * target has actually MOVED off the origin. That makes the key-up which merely ends the
     * lifting hold a no-op, with no "swallow the next activation" flag to keep in sync.
     */
    fun activate() {
        if (moveState.active && !moveState.pointerDriven) {
            if (moveState.origin == cellKey) {
                // This is the release that ended the lift (or a confirm with the target still
                // on the origin). Do nothing — B cancels, arrows choose a destination.
                if (moveState.target != moveState.origin) onCommitMove(moveState.commit())
            } else {
                // A different tile was activated while a move is in flight: that's a drop.
                moveState.moveTargetTo(cellKey)
                onCommitMove(moveState.commit())
            }
            return
        }
        menuOpen = true
    }

    Box(
        modifier = modifier
            // Addressable per cell: "cell:<sub-input key>:<ACTIVATOR_TYPE>".
            .testTag(cellTestTag(cellKey))
            .width(TileWidth)
            .height(TileHeight)
            // Lifted tiles ride above their neighbors, and the pointer path translates them
            // under the finger.
            .zIndex(if (isOrigin) 10f else 0f)
            .graphicsLayer {
                if (isOrigin && moveState.pointerDriven) {
                    translationX = moveState.dragOffset.x
                    translationY = moveState.dragOffset.y
                }
                if (isOrigin) alpha = MoveOriginAlpha
            }
            .minputInteractiveMotion(interaction)
            .clip(shape)
            .background(container, shape)
            .then(
                when {
                    // Drop target gets a bright ring — the same "this is where it lands" read
                    // as the keyboard editor's valid-drop highlight.
                    isTarget && !isOrigin -> Modifier.border(
                        width = MoveTargetStroke,
                        color = LocalMappoExtraColors.current.dropZoneValid,
                        shape = shape,
                    )
                    // Empty cells stay strokeless by spec; defined ones wear the family bevel.
                    output != null -> Modifier.border(minputBevelBorder(container, TileCorner), shape)
                    else -> Modifier
                },
            )
            // Every tile is a focus stop, editable or not — a read-only layer view still
            // needs controller navigation to reach its menus.
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.key !in TileActivateKeys) return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        // Hold-to-move is an EDITING gesture, and it can't begin on top of a
                        // move already in flight.
                        if (enabled && !moveState.active && keyDownAt == 0L) {
                            keyDownAt = System.currentTimeMillis()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        keyDownAt = 0L
                        activate()
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                onClickLabel = if (output == null) "Assign command" else "Command options",
                onClick = ::activate,
            )
            .then(
                if (enabled) {
                    Modifier.moveModeLongPressSource(
                        state = moveState,
                        key = cellKey,
                        onCommit = onCommitMove,
                    )
                } else Modifier,
            )
            // AFTER the placement-shifting graphicsLayer, per
            // feedback_compose_modifier_order_position_observers — otherwise the registered
            // rect is the tile's resting position and drops land on the wrong cell.
            .moveModeCell(moveState, cellKey),
        contentAlignment = Alignment.Center,
    ) {
        if (output == null) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier
                    .size(EmptyTilePlusSize)
                    .alpha(EmptyTilePlusAlpha),
                tint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.padding(horizontal = TileContentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                InputGlyphs.outputPainter(output)?.let { painter ->
                    Icon(
                        painter,
                        contentDescription = null,
                        modifier = Modifier.size(TileOutputGlyphSize),
                        tint = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(MinputGlyphLabelGap))
                }
                Column(horizontalAlignment = Alignment.Start) {
                    if (label != null) {
                        Text(
                            text = label.uppercase(),
                            style = minputOverlineTextStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = output.displayLabel(config),
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        MinputActionMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            actions = actions(),
        )
    }
}

/**
 * The tile menu's verbs. Icons, no helper text (per the design) — these are self-evident, and
 * the two-line [RichMenuItem] form is reserved for actions that genuinely need tutorializing.
 *
 * Paste stays LISTED but greyed when nothing has been copied, so the menu's shape doesn't
 * shift between cells.
 *
 * **Label and Settings are additions to the specified list** (Edit / Copy / Paste / Move /
 * Clear): the table dropped the row's label field and cog, and without these two the tile's
 * label would be unsettable and activator settings — crucially the CHORD PARTNER, without
 * which a Chord cell can't function — would be unreachable.
 */
private fun tileActions(
    defined: Boolean,
    clipboardOccupied: Boolean,
    onEdit: () -> Unit,
    onLabel: () -> Unit,
    onSettings: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onMove: () -> Unit,
    onClear: () -> Unit,
): List<MinputAction> = if (!defined) {
    listOf(
        MinputAction("New", Icons.Filled.Add, onClick = onEdit),
        MinputAction("Paste", Icons.Filled.ContentPaste, enabled = clipboardOccupied, onClick = onPaste),
    )
} else {
    listOf(
        MinputAction("Edit", Icons.Filled.Edit, onClick = onEdit),
        MinputAction("Label", Icons.AutoMirrored.Filled.Label, onClick = onLabel),
        MinputAction("Settings", Icons.Filled.Settings, onClick = onSettings),
        MinputAction("Copy", Icons.Filled.ContentCopy, onClick = onCopy),
        MinputAction("Paste", Icons.Filled.ContentPaste, enabled = clipboardOccupied, onClick = onPaste),
        MinputAction("Move", Icons.Filled.OpenWith, onClick = onMove),
        MinputAction("Clear", Icons.AutoMirrored.Filled.Backspace, destructive = true, onClick = onClear),
    )
}

/** Layer view's cut-down menu. Editing on a layer must go through the full-screen editor so
 *  the override materializes onto the layer rather than mutating the base set's row, and
 *  Clear-override only means anything where an override actually exists. */
private fun layerTileActions(
    overridden: Boolean,
    onEdit: () -> Unit,
    onClearOverride: () -> Unit,
): List<MinputAction> = listOf(
    MinputAction("Edit on this layer", Icons.Filled.Edit, onClick = onEdit),
    MinputAction(
        "Clear override",
        Icons.Filled.Delete,
        enabled = overridden,
        destructive = true,
        onClick = onClearOverride,
    ),
)

/** Stable test handle for a cell. */
internal fun cellTestTag(key: CellKey): String = "cell:${key.inputKey}:${key.type.name}"

/** Keys that activate a focused tile. Mirrors what Compose's own `clickable` accepts, plus the
 *  gamepad A button so a controller's primary action works without a d-pad center. */
private val TileActivateKeys = setOf(
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.ButtonA,
)

/** Header identity label for a group. User-specified wording; Title Case is the deliberate
 *  exception to the sentence-case doctrine (hardware names read as proper nouns). */
internal fun RemapSimpleGroup.headerLabel(): String = when (this) {
    RemapSimpleGroup.LEFT_SHOULDER -> "Left Trigger"
    RemapSimpleGroup.LEFT_STICK -> "Left Joystick"
    RemapSimpleGroup.DPAD -> "Directional Pad"
    // "Button Pad" (not "Face Buttons") — matches the mode name of the same concept.
    RemapSimpleGroup.FACE -> "Button Pad"
    RemapSimpleGroup.RIGHT_SHOULDER -> "Right Trigger"
    RemapSimpleGroup.RIGHT_STICK -> "Right Joystick"
    RemapSimpleGroup.UTILITY -> "Utility Buttons"
}

/** Non-interactive input→output flow marker: a filled Lucide play triangle. */
@Composable
private fun EditorFlowArrow(modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.lucide_play_filled),
        contentDescription = null,
        modifier = modifier.size(EditorFlowArrowSize),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Shared press-type vocabulary (moved from the retired detail-pane editor) ─────────────────

/** Canonical Steam render order for press types. The advanced table uses [pressTypeColumns]
 *  instead — same set, user-specified column order. */
internal val pressTypeOrder = listOf(
    ActivatorType.FULL_PRESS,
    ActivatorType.LONG_PRESS,
    ActivatorType.DOUBLE_PRESS,
    ActivatorType.START_PRESS,
    ActivatorType.RELEASE_PRESS,
    ActivatorType.CHORDED_PRESS,
)

/**
 * Short label for a press type — the table's column names. NB: START_PRESS reads "Down" and
 * RELEASE_PRESS reads "Up" (Mappo wording — fires on the down / up edge). VDF import/export
 * must map Steam's "Start Press" ↔ "Down" and "Release Press" ↔ "Up".
 */
internal fun ActivatorType.shortLabel(): String = when (this) {
    ActivatorType.FULL_PRESS -> "Press"
    ActivatorType.LONG_PRESS -> "Long"
    ActivatorType.DOUBLE_PRESS -> "Double"
    ActivatorType.START_PRESS -> "Down"
    ActivatorType.RELEASE_PRESS -> "Up"
    ActivatorType.CHORDED_PRESS -> "Chord"
    ActivatorType.SOFT_PRESS -> "Soft"
}

internal fun ActivatorType.helperText(): String = when (this) {
    ActivatorType.FULL_PRESS -> "Fires on a normal press."
    ActivatorType.LONG_PRESS -> "Fires when held past the long-press time."
    ActivatorType.DOUBLE_PRESS -> "Fires on two quick presses."
    ActivatorType.START_PRESS -> "Fires the instant the button goes down."
    ActivatorType.RELEASE_PRESS -> "Fires when the button is let go."
    ActivatorType.CHORDED_PRESS -> "Fires only while another button is held."
    ActivatorType.SOFT_PRESS -> "Fires on a soft (partial) pull."
}

internal fun ActivatorType.pressIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    ActivatorType.FULL_PRESS -> Icons.Filled.TouchApp
    ActivatorType.LONG_PRESS -> Icons.Filled.Timer
    ActivatorType.DOUBLE_PRESS -> Icons.Filled.Repeat
    ActivatorType.START_PRESS -> Icons.Filled.Bolt
    ActivatorType.RELEASE_PRESS -> Icons.AutoMirrored.Filled.Logout
    ActivatorType.CHORDED_PRESS -> Icons.Filled.Link
    ActivatorType.SOFT_PRESS -> Icons.Filled.Adjust
}

/** The shared kebab ("more" button) used by the editor header. */
@Composable
internal fun RowKebab(
    onClick: () -> Unit,
    contentDescription: String = "Options",
    modifier: Modifier = Modifier,
) {
    MinputIconButton(
        icon = Icons.Filled.MoreVert,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
    )
}

/** A `DropdownMenuItem` with a leading icon and two-line title + helper text — the
 *  tutorializing form, for menus whose actions aren't self-evident. Self-evident verb menus
 *  use `MinputActionMenu` instead. [titleContent] swaps in a custom title composable; [title]
 *  still names the item for readers of the code. */
@Composable
internal fun RichMenuItem(
    title: String,
    helper: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    selected: Boolean = false,
    titleContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.material3.DropdownMenuItem(
        enabled = enabled,
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        text = {
            Column {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        onClick = onClick,
    )
}

// Header height is the family standard shared with the panel surfaces.
private val EditorHeaderHeight = MinputPanelHeaderHeight

/** Edge of the filled-play flow arrow in the header. */
private val EditorFlowArrowSize = 10.dp

// ── Table metrics ────────────────────────────────────────────────────────────────────────────

/** GOVERNING VARIABLE for column width. Every cell, and the header above it, is exactly this
 *  wide — a table whose columns flexed to content would put the same press type at a different
 *  x-offset on every row, destroying the scan the table exists to enable. */
private val TileWidth = 112.dp

/** Cell height. Taller than the old 38dp command rows because a cell now stacks an overline
 *  label above the output text where the row had a separate label field beside it. */
private val TileHeight = 40.dp

/** Gap between cells, and between the glyph column and the body. */
private val TileGap = 4.dp

/** The press-type header row's height. */
private val ColumnHeaderHeight = 20.dp
private val ColumnHeaderIconSize = 12.dp

/** The frozen glyph column. Wide enough for the largest hardware prompt plus breathing room. */
private val GlyphColumnWidth = 34.dp

/** Input glyphs render LARGER here than in the old rows — with the press-type word gone from
 *  the cell, the glyph is the row's only identity. */
private val TableGlyphSize = 22.dp

private val TileCorner = 10.dp
private val TileContentPadding = 8.dp
private val TileOutputGlyphSize = 14.dp

/** How strongly a column's accent tints its cells. Low on purpose: the tile must still read as
 *  a control surface of the same family as everything else, color-CODED rather than colored. */
private const val TileAccentAlpha = 0.22f

/** The empty cell's "+": present enough to invite a tap, faint enough that a row of empties
 *  doesn't read as content. */
private val EmptyTilePlusSize = 16.dp
private const val EmptyTilePlusAlpha = 0.3f

/** A lifted tile fades so the drop target beneath it stays readable. */
private const val MoveOriginAlpha = 0.55f
private val MoveTargetStroke = MinputBoxStroke * 2
