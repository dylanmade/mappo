package com.mappo.ui.screen.remap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.indication
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
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
import com.mappo.data.model.steam.displayNameFor
import com.mappo.data.model.steam.InputSource
import com.mappo.service.input.modes.SourceModeCatalog
import com.mappo.data.settings.MoveCommitGesture
import com.mappo.ui.component.MoveModeState
import com.mappo.ui.component.moveModeCell
import com.mappo.ui.component.moveModeLongPressSource
import com.mappo.ui.component.rememberMoveModeState
import com.mappo.ui.glyph.InputGlyphs
import com.mappo.ui.screen.displayLabel as activatorDisplayLabel
import com.mappo.ui.screen.remap.settings.SourceModeSettingsSchema
import com.mappo.ui.theme.LocalMappoExtraColors
import com.mappo.ui.theme.PressTypeColors
import com.mappo.ui.minput.minputStrokeInset
import com.mappo.ui.minput.MinputAction
import com.mappo.ui.minput.MinputActionMenu
import com.mappo.ui.minput.MinputDropdownMenu
import com.mappo.ui.minput.MinputMenuPlacement
import com.mappo.ui.minput.MinputElevatedContainer
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputOverflowScroll
import com.mappo.ui.minput.MinputPanelHeaderHeight
import com.mappo.ui.minput.MinputBoxStroke
import com.mappo.ui.minput.MinputSize
import com.mappo.ui.minput.MinputScrollbar
import com.mappo.ui.minput.MinputScrollbarThickness
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputDialog
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * **The COMMAND TILE and everything that acts on one** — the face it wears, the menu it offers,
 * the dialogs that menu summons, the press-type picker, and the machinery for carrying one across
 * the grid.
 *
 * It was an expanded ("advanced") in-place editor: a card a group box grew into, holding a TABLE
 * of these tiles with a header of its own (group identity · mode pill · cog · kebab · close). That
 * editor is gone (Dylan, 2026-09-27: "I am all in on this experimental view/edit mode, which means
 * I think we can finally ditch any code related to the former Advanced view") — the basic view's
 * rows became these very tiles in place, which is what made the card redundant. What the card
 * carried and edit mode needed a home for now lives on the stage: its dialogs at the stage's root
 * ([CommandTileDialogs]), its mode settings and Reset in each group's own action menu.
 *
 * The tile's own history is worth keeping. It replaced a vertical list of command rows, each with
 * its own press-type pill, output button, label field, cog and kebab (2026-09-11); the table that
 * replaced THAT pre-exposed every (input × press type) intersection, six columns of mostly nothing,
 * until a row became a STACK of the commands that exist plus one "+" (2026-09-20, see
 * [RowCommand]). A row's slots count outward from its input glyph, and a LEFT-flank row runs
 * outward to the left — so everything keyed on the index is untouched by mirroring; only the
 * rendered order and the sign of a column step flip.
 *
 * Base-set view edits inline; layer view resolves override→base, renders read-only, and routes
 * tile taps to the full-screen editor (which materializes the override).
 */
internal class RemapGroupEditorCallbacks(
    val onSetBindingGroupMode: (bindingGroupId: Long, mode: BindingMode) -> Unit,
    val onOpenModeSettings: (bindingGroupId: Long, source: InputSource) -> Unit,
    val onEditCommand: (bindingId: Long, current: BindingOutput, title: String) -> Unit,
    val onOpenInputEditor: (inputSource: InputSource, groupInputKey: String, label: String) -> Unit,
    val onClearOverride: (inputSource: InputSource, groupInputKey: String) -> Unit,
    /** The label editor's whole commit: the user label (blank clears it) and how the command
     *  prints — its device glyph and the device initials on its name. */
    val onSetLabel: (
        bindingId: Long,
        label: String,
        showDeviceIcon: Boolean,
        showDeviceInitials: Boolean,
    ) -> Unit,
    val onResetGroup: (bindingGroupId: Long) -> Unit,
    val onConfigure: (activatorId: Long, title: String) -> Unit,
    // ── Command ops (see ControllerConfigRepository's input-row block) ──
    /** Add a command of [type] to a row and open the command picker on it — what the row's
     *  trailing "+" tile does. The creation has to land first: the picker edits a binding. */
    val onAddCommand: (
        bindingGroupId: Long,
        inputKey: String,
        type: ActivatorType,
        title: String,
    ) -> Unit,
    /** Delete one command. Its activator goes too if that leaves it empty. */
    val onDeleteCommand: (bindingId: Long) -> Unit,
    /** Which press type a command fires on. */
    val onSetPressType: (bindingId: Long, type: ActivatorType) -> Unit,
    val onCopyCommand: (bindingId: Long) -> Unit,
    /** Paste over [bindingId], or APPEND to the row when it is null (the "+" tile's Paste). */
    val onPasteCommand: (bindingId: Long?, bindingGroupId: Long, inputKey: String) -> Unit,
    /**
     * Carry a command onto a row — within a group or across them, which is the same operation
     * since a command only points at its row.
     *
     * [swapWithBindingId] is the command it lands ON, which goes back the other way; null means
     * it landed on the row's "+" and is simply ADDED there (Dylan, 2026-09-20). Either way the
     * moved command keeps its own press type, and the auto-sort decides where in the row it
     * comes to rest.
     */
    val onMoveCommand: (
        bindingId: Long,
        toBindingGroupId: Long,
        toInputKey: String,
        swapWithBindingId: Long?,
    ) -> Unit,
    /** Whether anything has been copied this session — greys Paste rather than hiding it. */
    val clipboardOccupied: Boolean,
    // ── Group-level ops (2026-09-16): carrying commands between groups, whole-group copy/reset ──
    /** The group menu's clipboard, or null when nothing has been copied. */
    val groupClipboard: com.mappo.data.repository.ControllerConfigRepository.InputGroupSnapshot? = null,
    /** Copy a group: its rows as (bindingGroupId, sub-input key) in display order, the binding
     *  group whose mode + settings it carries, and which halves to take. */
    val onCopyGroup: (rows: List<Pair<Long, String>>, settingsGroupId: Long?, inputs: Boolean, settings: Boolean) -> Unit =
        { _, _, _, _ -> },
    val onPasteGroup: (rows: List<Pair<Long, String>>, settingsGroupId: Long?) -> Unit = { _, _ -> },
    /** Reset each binding group to its fresh-layout seed. */
    val onResetGroups: (bindingGroupIds: List<Long>) -> Unit = {},
)

/**
 * **The order a row's commands are auto-sorted into** — the order the table's fixed press-type
 * columns used to run in, kept as a sort when the columns went (Dylan, 2026-09-20). Also the
 * order the type picker lists them in.
 *
 * Note this is NOT [pressTypeOrder] (the canonical Steam render order): Chord comes before the
 * Down/Up edge triggers because the edge pair reads as a tail-end special case.
 *
 * `SOFT_PRESS` is absent by design: it's a sub-input (the trigger's "soft_press" row), not an
 * activator the user picks. See `feedback_soft_press_unified_to_soft_pull`.
 */
internal val pressTypeSortOrder = listOf(
    ActivatorType.FULL_PRESS,
    ActivatorType.LONG_PRESS,
    ActivatorType.DOUBLE_PRESS,
    ActivatorType.CHORDED_PRESS,
    ActivatorType.START_PRESS,
    ActivatorType.RELEASE_PRESS,
)

/**
 * The three column colors for a press type — header, tile tint, empty-cell "+".
 *
 * **To retune the palette, edit `PressTypePalette` in `ui/theme/Theme.kt`**; nothing here
 * derives or blends on top of those values, so what's written there is what renders.
 * `FULL_PRESS` maps to the neutral `press` entry (transparent tile = the ordinary surface).
 */
@Composable
internal fun ActivatorType.columnColors(): PressTypeColors {
    val palette = LocalMappoExtraColors.current.pressTypes
    return when (this) {
        ActivatorType.FULL_PRESS, ActivatorType.SOFT_PRESS -> palette.press
        ActivatorType.LONG_PRESS -> palette.long
        ActivatorType.DOUBLE_PRESS -> palette.double
        ActivatorType.CHORDED_PRESS -> palette.chord
        ActivatorType.START_PRESS -> palette.down
        ActivatorType.RELEASE_PRESS -> palette.up
    }
}

/**
 * Which cell a table coordinate names. The move-mode key type, and the identity the tile menus
 * act on.
 *
 * It carries its GROUP (2026-09-17) because the zoomed scene shows every group's table at once
 * and one move state spans them all: a command lifted from the d-pad can be carried to the face
 * buttons, so a cell's identity has to say which table it belongs to.
 *
 * And it names its row by the WHOLE [SimpleRowSpec], not by the sub-input key: a key is unique
 * only within one binding group, and a group's table can span two. The utility group is the
 * case that proved it — Start and Select are both a "click", so keying by the string alone gave
 * the two rows ONE identity, and with it one test tag, one focus handle and a d-pad step that
 * could never reach the second row.
 */
internal data class CellKey(
    val group: RemapSimpleGroup,
    val row: SimpleRowSpec,
    /**
     * Which TILE of the row, counting outward from the input glyph. A row holds its commands
     * followed by one "+" slot, so the last index is always the add affordance (2026-09-20) —
     * where the six fixed press-type columns used to be, and why this replaced the
     * `ActivatorType` a cell used to be keyed by. What a slot HOLDS is data, not identity:
     * resolve it through the row's commands (see [rowCommandsFor]).
     */
    val slot: Int,
) {
    val inputKey: String get() = row.subInputKey
    val source: InputSource get() = row.source
}

/** What the label editor is editing: the command's label, the name it falls back to, and how
 *  it prints. */
internal data class LabelEdit(
    val bindingId: Long,
    val label: String,
    val outputs: List<BindingOutput>,
    val showDeviceIcon: Boolean,
    val showDeviceInitials: Boolean,
)

/** What the "Type" verb is editing: one command, and the press type it currently fires on. */
internal data class TypeEdit(val bindingId: Long, val current: ActivatorType)

/**
 * The verbs one tile's menu offers, wired to the callbacks that carry them out.
 *
 * Shared by the advanced table and the basic view's EDIT MODE (Dylan, 2026-09-22): a tile is the
 * same object in both, so its menu has to be the same menu. One that differed by where the tile
 * happened to be drawn would be a different control wearing the same face.
 *
 * [onLabel] / [onType] hand back what the two dialog verbs want to edit, because the dialogs
 * outlive the menu that summoned them (and the tile, once it moves) — they belong to whoever
 * hosts the tiles. See [CommandTileDialogs], the other half.
 */
internal fun commandCellActions(
    cellKey: CellKey,
    command: RowCommand?,
    /** The sub-input's own name ("A Button"), for the editors this opens. */
    subLabel: String,
    /** That name qualified by the press type — what an editor opened from here is titled. */
    title: String,
    bindingGroupId: Long?,
    /** Does the viewed LAYER override this row? Only meaningful in layer view. */
    overridden: Boolean,
    editable: Boolean,
    callbacks: RemapGroupEditorCallbacks,
    moveState: MoveModeState<CellKey>,
    onLabel: (LabelEdit) -> Unit,
    onType: (TypeEdit) -> Unit,
): List<MinputAction> = if (!editable) {
    // Layer view is read-only here: editing routes to the full-screen editor (which materializes
    // the override onto the layer), and an input the layer actually overrides can be handed back
    // to base.
    layerTileActions(
        overridden = overridden,
        onEdit = { callbacks.onOpenInputEditor(cellKey.source, cellKey.inputKey, subLabel) },
        onClearOverride = { callbacks.onClearOverride(cellKey.source, cellKey.inputKey) },
    )
} else {
    tileActions(
        defined = command != null,
        clipboardOccupied = callbacks.clipboardOccupied,
        onEdit = {
            if (command != null) {
                callbacks.onEditCommand(command.id, command.output, title)
            } else if (bindingGroupId != null) {
                // The "+" makes the command first, then opens the picker on it — a new command
                // starts as a Regular Press and is retyped from the same menu.
                callbacks.onAddCommand(bindingGroupId, cellKey.inputKey, ActivatorType.FULL_PRESS, title)
            }
        },
        onLabel = {
            command?.let {
                onLabel(
                    LabelEdit(
                        bindingId = it.id,
                        label = it.binding.label.orEmpty(),
                        outputs = listOf(it.output),
                        showDeviceIcon = it.binding.showDeviceIcon,
                        showDeviceInitials = it.binding.showDeviceInitials,
                    ),
                )
            }
        },
        onType = { command?.let { onType(TypeEdit(it.id, it.type)) } },
        onSettings = { command?.let { callbacks.onConfigure(it.activator.id, title) } },
        onCopy = { command?.let { callbacks.onCopyCommand(it.id) } },
        onPaste = {
            if (bindingGroupId != null) {
                callbacks.onPasteCommand(command?.id, bindingGroupId, cellKey.inputKey)
            }
        },
        onMove = { moveState.pickUp(cellKey, byPointer = false) },
        onClear = { command?.let { callbacks.onDeleteCommand(it.id) } },
    )
}

/** The two dialogs a tile's menu can summon. Hosted by whoever draws the tiles, not by the tile:
 *  both outlive the menu, and one of them outlives a tile that has just been moved. */
@Composable
internal fun CommandTileDialogs(
    labelTarget: LabelEdit?,
    typeTarget: TypeEdit?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    onCloseLabel: () -> Unit,
    onCloseType: () -> Unit,
) {
    labelTarget?.let { target ->
        CommandLabelDialog(
            label = target.label,
            outputs = target.outputs,
            config = config,
            showDeviceIcon = target.showDeviceIcon,
            showDeviceInitials = target.showDeviceInitials,
            onCommit = { text, icons, initials ->
                callbacks.onSetLabel(target.bindingId, text, icons, initials)
            },
            onClose = onCloseLabel,
        )
    }
    typeTarget?.let { target ->
        PressTypeDialog(
            current = target.current,
            onPick = { type -> callbacks.onSetPressType(target.bindingId, type) },
            onClose = onCloseType,
        )
    }
}

/**
 * The "Type" verb's picker: which press type a command fires on.
 *
 * A stop-gap by design (Dylan, 2026-09-20) — the per-command configuration page will own this
 * eventually. It lives in the tile menu so the press-type LOOK can be worked on now: each type
 * shows the glyph and color the tile will wear, so picking one is picking a tile.
 */
@Composable
private fun PressTypeDialog(
    current: ActivatorType,
    onPick: (ActivatorType) -> Unit,
    onClose: () -> Unit,
) {
    MinputDialog(onDismissRequest = onClose) {
        Text(
            text = "PRESS TYPE",
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(TypeDialogTitleGap))
        pressTypeSortOrder.forEach { type ->
            val colors = type.columnColors()
            val selected = type == current
            val shape = RoundedCornerShape(TypeDialogRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (selected) colors.tile else Color.Transparent, shape)
                    .clickable(onClickLabel = type.columnLabel()) {
                        onPick(type)
                        onClose()
                    }
                    .padding(horizontal = TypeDialogRowPadding, vertical = TypeDialogRowPadding / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The glyph box is reserved even for Regular Press, which has none by design —
                // the names must still line up with each other.
                Box(
                    modifier = Modifier.size(TilePressGlyphSize),
                    contentAlignment = Alignment.Center,
                ) {
                    type.pressIcon()?.let { icon ->
                        Icon(icon, contentDescription = null, tint = colors.icon)
                    }
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = type.columnLabel(),
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = type.helperText(),
                        style = minputOverlineTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Current",
                        modifier = Modifier.size(MinputSize.Standard.iconSize),
                        tint = colors.icon,
                    )
                }
            }
        }
        Spacer(Modifier.height(TypeDialogTitleGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            MinputButton(text = "Cancel", onClick = onClose)
        }
    }
}

/**
 * One tile — a command, or the row's trailing "+".
 *
 * **A command** ([output] non-null): a capsule on its press type's accent-tinted surface, led
 * by that press type's glyph, carrying a stack of (optional) label over the output line. The
 * leading glyph is how a tile says which press type it is now that the columns are gone (Dylan,
 * 2026-09-20): it is left-aligned, larger than the output's own device glyph, and wears the
 * palette's [PressTypeColors.icon] rather than the tile's tint so it reads ON the tile instead
 * of dissolving into it. **Regular Press deliberately has no glyph** — it is the ordinary case,
 * and an icon for "nothing special" is noise on the tile the user sees most.
 *
 * **The "+"** ([output] null): unfilled, a dimmed plus inside a hairline ring ([tileOutline]).
 * The missing FILL is the signal — the create affordance shouldn't compete with the commands
 * beside it — while the ring still gives the empty slot a footprint to aim at.
 *
 * Output glyph and text deliberately do NOT take the press accent (readability); the container
 * tint, the press glyph and the "+" carry it.
 */
@Composable
internal fun CommandTile(
    colors: PressTypeColors,
    /** The press type whose glyph leads the tile, or null for Regular Press and the "+". */
    pressType: ActivatorType?,
    output: BindingOutput?,
    label: String?,
    /** The command's name as it should print — device initials already applied. */
    outputText: String,
    /** Whether the output device's glyph leads that name. Per command, from its Binding. */
    showDeviceIcon: Boolean,
    enabled: Boolean,
    cellKey: CellKey,
    moveState: MoveModeState<CellKey>,
    /** Is the stage's [MoveOverlay] drawing this tile right now? Then the one in the grid goes
     *  invisible — its slot, its focus ring and its drop marker stay exactly where they are, and
     *  the tile itself is over in the overlay, where no card's edge can clip it. */
    carried: Boolean,
    /** Grid-step offset this tile should animate to while a move previews a swap. */
    displacement: DpOffset,
    /** The lifted cell of the move currently being previewed — live OR still committing.
     *  Drives z-order and keys the slide animation, so the visual survives the handover from
     *  "dragging" to "written, waiting for the reload". */
    previewOrigin: CellKey?,
    onCommitMove: (Pair<CellKey, CellKey>?) -> Unit,
    /** Reports a controller-driven lift, so the table can track whether the button that
     *  started it is still held. */
    onControllerLift: (LiftPress) -> Unit,
    actions: () -> List<MinputAction>,
    modifier: Modifier = Modifier,
    /** How big this tile is — see [rowTileLook]. */
    look: TileLook,
    /**
     * **Non-null while the tile is still travelling into place**: how far its chrome has arrived,
     * read in the DRAW phase (Dylan, 2026-09-27).
     *
     * A tile that is arriving draws its fill and ring at that strength and NO content, because the
     * label it would draw is the one its host is walking into place above it (see
     * `EditPhase.ARRIVING`). Everything else about it is already real — it takes focus, it answers
     * a tap, it can be lifted — which is the point: the cursor no longer waits out the animation.
     */
    chrome: (() -> Float)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // TWO interaction sources, deliberately.
    //
    // Press/hover belong to the TILE — they happen while it's at rest, and their ripple should
    // be clipped to its shape and travel with it. FOCUS belongs to the CELL: it marks a
    // position in the grid, not an object being carried. Sharing one source put the focus
    // highlight on the translated node, so during a move the ring rode along with the lifted
    // tile and then snapped back to the origin with it on commit — read as the focus flashing
    // back to the old location.
    val pressInteraction = remember { MutableInteractionSource() }
    val focusInteraction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    // Live move state — the finger is down / the tile is lifted RIGHT NOW.
    val isOrigin = moveState.origin == cellKey
    val isTarget = moveState.active && moveState.target == cellKey
    // Preview state — spans the live move AND the commit that hasn't reloaded yet.
    val isPreviewOrigin = previewOrigin == cellKey
    val previewing = previewOrigin != null
    val density = LocalDensity.current

    // Swap preview, animated so the exchange reads as motion rather than a jump.
    //
    // Keyed on the CURRENT LIFT (`moveState.origin`) so every move starts from a fresh
    // Animatable at zero — otherwise a new drag inherits the previous one's in-flight
    // tween-back and the tile starts from somewhere it was never at. Same reasoning, and the
    // same fix, as `ReorderableTabBar`'s per-drag `Animatable`.
    // Keyed on the PREVIEW's origin, not the live one: keying on the live value would recreate
    // the Animatable at zero the instant the move committed, snapping the tile home a frame
    // before its new content arrives — the flash this whole preview exists to prevent.
    val slide = remember(previewOrigin, cellKey) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val slideTarget = with(density) { Offset(displacement.x.toPx(), displacement.y.toPx()) }
    LaunchedEffect(previewOrigin, slideTarget) {
        if (previewing) slide.animateTo(slideTarget, tween(MoveSlideMillis))
    }

    // The tint's own alpha IS the strength (see PressTypePalette); a fully transparent tint
    // leaves the plain elevated container, which is exactly what the Press column wants.
    val container = if (output == null) Color.Transparent else colors.tile.compositeOver(MinputElevatedContainer)
    val shape = RoundedCornerShape(look.corner)

    // Controller hold-to-move: a key-down starts a timer; crossing the long-press threshold
    // while still held lifts the tile instead of opening the menu. Hardware auto-repeat
    // re-delivers KeyDown, so the timestamp is only taken on the first one.
    // Only a cell that HOLDS something can be picked up. An empty cell is a slot, not a tile —
    // lifting one produced a move with nothing in it, which then "committed" a no-op over
    // whatever the user aimed at.
    val movable = enabled && output != null
    var keyDownAt by remember { mutableLongStateOf(0L) }
    // Did THIS tile see the press that this release belongs to? A held activate button keeps
    // auto-repeating while focus moves, so the release lands on whatever tile focus ended on —
    // and without this, that tile opened its menu for a press the user never made on it.
    var sawOwnKeyDown by remember { mutableStateOf(false) }
    LaunchedEffect(keyDownAt) {
        if (keyDownAt == 0L) return@LaunchedEffect
        delay(viewConfiguration.longPressTimeoutMillis)
        if (keyDownAt != 0L && !moveState.active) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            moveState.pickUp(cellKey, byPointer = false)
            onControllerLift(LiftPress.Held)
            // The lift SPENDS the press. From here the button belongs to the move — its release
            // is the host's to read ([moveModeKeyEvent]), and this tile must not still be
            // holding a claim on it. Leaving the timestamp armed is what made "release to place"
            // look broken: you lifted a tile, released over its own slot, which placed it — and
            // then the next direction you pressed found this timer still set and coyote-lifted
            // the tile all over again (Dylan, 2026-09-26). Clearing the claim also keeps a
            // cancelled move (B, then let go of activate) from opening the menu on the way out.
            keyDownAt = 0L
            sawOwnKeyDown = false
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
            // Activating any tile while a controller move is in flight is a DROP — including
            // the origin, which is how you put a tile back where you found it.
            moveState.moveTargetTo(cellKey)
            onCommitMove(moveState.commit())
            return
        }
        menuOpen = true
    }

    // OUTER: the cell's natural layout slot. Hosts the gesture, the bounds registration, the
    // focus target and the z-order — and carries NO graphicsLayer. That separation is the
    // whole trick, and it is not optional: pointerInput reports positions in POST-transform
    // local coordinates, so translating the same node that detects the drag makes the tile
    // chase a finger that appears stationary to it — a feedback loop of lag, flicker and
    // wrong drops. `ReorderableTabBar` and the keyboard button grid are both built this way
    // for exactly this reason; this tile got it wrong once already.
    //
    // It also means the registered bounds are the RESTING slot, which is what a grid wants:
    // "which cell is under the finger" must not change as tiles animate around.
    Box(
        modifier = modifier
            .testTag(cellTestTag(cellKey))
            .width(look.width)
            .height(look.height)
            // Lifted tiles ride above their neighbours. zIndex orders SIBLINGS only, so the
            // owning Row carries one too (see AdvancedTable).
            .zIndex(if (isPreviewOrigin) 10f else if (isTarget) 5f else 0f)
            .moveModeCell(moveState, cellKey)
            // A pending hold dies with focus. Without this, holding the activate button and
            // then d-padding away left the timer running on the tile behind you: it lifted a
            // tile you were no longer looking at, and the release — now delivered to whatever
            // had focus — dropped it there.
            .onFocusChanged {
                if (!it.isFocused) {
                    keyDownAt = 0L
                    sawOwnKeyDown = false
                }
            }
            // Every tile is a focus stop, editable or not — a read-only layer view still
            // needs controller navigation to reach its menus.
            .focusable(interactionSource = focusInteraction)
            .onKeyEvent { event ->
                // While a CONTROLLER move is in flight the table owns the activate keys; see
                // its handler. Returning false lets them bubble up to it.
                if (moveState.active && !moveState.pointerDriven) return@onKeyEvent false
                if (event.key !in TileActivateKeys) {
                    // ── Coyote time (Dylan, 2026-09-26) ──
                    //
                    // A DIRECTION pressed while the hold is still ripening lifts the tile NOW
                    // rather than throwing the gesture away. Waiting out the long-press before
                    // you are allowed to start moving is the kind of delay that teaches people
                    // to distrust a control, so a user who has clearly committed — button down,
                    // already steering — gets the lift they were heading for.
                    //
                    // The lift is an ordinary one — the press that caused it is still down, and
                    // what its release means is the user's setting, exactly as for a lift they
                    // did wait out ([MoveCommitGesture]). Returning false hands this very
                    // keystroke on to the host's move handler, which walks the drop target, so
                    // the first press both lifts and steps: what it would have done had the hold
                    // already ripened.
                    val steering = event.type == KeyEventType.KeyDown && event.key in MoveStepKeys
                    if (steering && movable && keyDownAt != 0L && !moveState.active) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        moveState.pickUp(cellKey, byPointer = false)
                        onControllerLift(LiftPress.Held)
                        keyDownAt = 0L
                        sawOwnKeyDown = false
                        return@onKeyEvent false
                    }
                    // ANY other key while the activate button is held abandons the press
                    // entirely — both the pending hold and the claim on the eventual release. A
                    // half-committed lift is the worst state this control can be in, so the
                    // gesture is treated as fragile on purpose: it survives holding still and
                    // nothing else. (Focus loss disarms it too, below.)
                    keyDownAt = 0L
                    sawOwnKeyDown = false
                    return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        // Arm only on the INITIAL press. Held keys auto-repeat their KeyDown,
                        // so without this check a press that began on another tile re-armed the
                        // hold here the moment focus arrived — lifting a tile the user had
                        // merely navigated onto, mid-hold.
                        val initialPress = event.nativeKeyEvent.repeatCount == 0
                        if (initialPress) sawOwnKeyDown = true
                        if (movable && initialPress && !moveState.active && keyDownAt == 0L) {
                            keyDownAt = System.currentTimeMillis()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        keyDownAt = 0L
                        val ownPress = sawOwnKeyDown
                        sawOwnKeyDown = false
                        // Only a release whose press this tile actually saw activates it.
                        if (ownPress) activate()
                        true
                    }
                    else -> false
                }
            }
            .then(
                if (movable) {
                    Modifier.moveModeLongPressSource(
                        state = moveState,
                        key = cellKey,
                        onCommit = onCommitMove,
                    )
                } else Modifier,
            ),
    ) {
    // INNER: visual transform only, decoupled from gesture detection. Also where the tap
    // lives, so the ripple is clipped to the tile's shape and travels with it; its own
    // focusability is switched off so the OUTER stays the single focus target (which is what
    // the call site's focusRequester attaches to).
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                when {
                    // Pointer path: raw, unanimated — the tile belongs under the finger.
                    isOrigin && moveState.pointerDriven -> {
                        translationX = moveState.dragOffset.x
                        translationY = moveState.dragOffset.y
                    }
                    // Nothing in flight (and nothing committing): rest. Reaching this state
                    // is a SNAP, never an animation — a tween back to the resting slot would
                    // read exactly like the move being rejected.
                    !previewing -> {
                        translationX = 0f
                        translationY = 0f
                    }
                    else -> {
                        translationX = slide.value.x
                        translationY = slide.value.y
                    }
                }
                chrome?.let { alpha = it() }
                if (carried) alpha = 0f
            }
            .minputInteractiveMotion(pressInteraction)
            .clip(shape)
            .background(container, shape)
            .then(tileOutline(container, output != null, shape, look.corner))
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = pressInteraction,
                indication = minputIndication(),
                onClickLabel = if (output == null) "Assign command" else "Command options",
                onClick = ::activate,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // **The "+" arrives WITH the outline it sits in** (Dylan, 2026-09-27: the pluses "are
        // appearing a moment after the tile outlines fade in, and don't seem to have an animate in
        // at all"). A defined tile's label is the one the host is walking into place, so drawing it
        // here too would double it — but an EMPTY slot has no travelling label: its "+" is part of
        // the face, exactly as it is in [TileChrome], and it fades in on the same alpha because it
        // is inside the same layer.
        if (chrome == null || output == null) {
            TileContent(
                colors = colors,
                pressType = pressType,
                output = output,
                label = label,
                outputText = outputText,
                showDeviceIcon = showDeviceIcon,
            )
        }

        // Beside the tile, not over it: the cell IS the thing being acted on, and a menu
        // dropped on top of it hides the command you're deciding about. Mirrors to the start
        // side automatically for the rightmost columns. The menu measures this Box (its
        // anchor) itself — nothing to pass.
        MinputActionMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            // Built only when the menu is actually up. Every card's table composes at once now
            // (see RemapStage's `live`), and a closed menu's eight verbs — each with its own
            // callback — were being allocated for every tile in the scene on every composition.
            actions = if (menuOpen) actions() else emptyList(),
            placement = MinputMenuPlacement.End,
            caret = true,
        )
    }

    // The FOCUS layer: pinned to the slot (no transform), clipped to the tile's shape, drawn
    // over the tile. Focus marks a POSITION in the grid, not the object being carried —
    // rendering it on the moving node is what made the ring ride along with a lifted tile and
    // snap back with it.
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .indication(focusInteraction, minputIndication()),
    )
    }
}

/**
 * **Does the HOST draw the tile in flight?** (Dylan, 2026-09-21.)
 *
 * A card clips: its body scrolls in both directions and its rows sit beside a frozen glyph
 * column, so a tile lifted out of the top-right slot had its swollen corner sliced off by the
 * viewport's edge, and a tile carried toward another group simply stopped at its own card's rim —
 * the move committed correctly, but nothing on screen said so.
 *
 * The fix is to draw the tile being carried OUTSIDE every card, at the top of the stage, where
 * nothing clips it and the whole scene is one coordinate space ([MoveOverlay]). Where that
 * happens, the tiles in the grid that the overlay is standing in for hide themselves rather than
 * rendering twice. A host that doesn't draw the overlay keeps the old in-grid slide, which is
 * correct as far as its own card's edges.
 */
internal val LocalMoveOverlay = staticCompositionLocalOf { false }

/**
 * The tiles IN FLIGHT, drawn above the whole stage.
 *
 * Up to two: the tile being CARRIED, and — when it is hovering over a command rather than a row's
 * "+" — the tile it would displace, sliding the other way. Both are drawn here rather than in
 * their own cards because either end of the exchange may be in a different card, and a card
 * clips (see [LocalMoveOverlay]).
 *
 * Positions come from the move state's own cell registry, which is in WINDOW space — the one
 * space every card shares — converted into this overlay by [stageOrigin], and re-read every
 * frame: the registry is a plain map, and both ends of a flight keep moving (the camera pans
 * toward the group a command is carried into, and that card scrolls the destination slot into
 * view).
 *
 * **A tile in flight is positioned RELATIVE TO THE SLOT IT IS AIMED AT, never in absolute stage
 * coordinates** (Dylan, 2026-09-21). [carriedResidual] is how far it still has to go; it decays
 * to nothing over [MoveSlideMillis], and the slot it is measured from is re-read each frame. So
 * the tile lands exactly on its slot however far the scene has travelled underneath it, instead
 * of animating toward where the slot USED to be and then shuffling onto it once the pan
 * finished. Re-aiming mid-flight (the d-pad walking the target on) re-anchors the residual
 * rather than restarting the journey, so the tile never jumps back to where it was lifted from.
 */
@Composable
internal fun MoveOverlay(
    moveState: MoveModeState<CellKey>,
    /** The stage's own top-left in window space. */
    stageOrigin: Offset,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    modifier: Modifier = Modifier,
    /** The size the tiles in flight are drawn at. */
    look: TileLook,
) {
    val live = moveState.origin
    // A move that has been called off is still on screen: its tiles fly home rather than
    // teleporting, and the overlay keeps drawing them until `settled()` says they have arrived.
    val settling = moveState.returning
    val origin = live ?: settling?.first ?: return
    val hovered = (if (live != null) moveState.target else settling?.second) ?: origin
    val order = LocalCommandOrder.current
    val lifted = rowCommandsFor(viewingSet, viewingLayer, origin.row, order).getOrNull(origin.slot)
        ?: return
    fun commandAt(key: CellKey): RowCommand? =
        rowCommandsFor(viewingSet, viewingLayer, key.row, order).getOrNull(key.slot)

    fun homeOf(key: CellKey): Offset? =
        moveState.boundsOf(key)?.takeIf { !it.isEmpty }?.topLeft?.minus(stageOrigin)

    val start = homeOf(origin) ?: Offset.Zero
    var originHome by remember(origin) { mutableStateOf(start) }
    /** Where the carried tile is aimed — the slot it is measured FROM. */
    var aimHome by remember(origin) { mutableStateOf(start) }
    /** How far the carried tile still is from that slot. Always decaying toward zero. */
    val carriedResidual = remember(origin) { Animatable(Offset.Zero, Offset.VectorConverter) }

    /**
     * **Every cell this move has displaced, and how far each one is into the vacated slot** —
     * 1 sitting in it, 0 back where it belongs (Dylan, 2026-09-25).
     *
     * Only the CURRENT target used to be drawn, so walking the carry from one occupied slot to
     * the next made the previous tile teleport home the instant it stopped being the target. A
     * cell that stops being the target retreats instead, and the same Animatable is re-aimed
     * rather than restarted, so re-entering a slot mid-retreat reverses it from where it is.
     */
    val retreat = remember(origin) { mutableStateMapOf<CellKey, Animatable<Float, AnimationVector1D>>() }
    /** Their slots, re-read every frame exactly as [originHome] is. */
    val homes = remember(origin) { mutableStateMapOf<CellKey, Offset>() }

    LaunchedEffect(hovered, origin) {
        // A row's "+" displaces nothing: that is an ADD, and nothing comes back the other way.
        val displaced = hovered.takeIf { it != origin && commandAt(it) != null }
        if (displaced != null) {
            moveState.holdInFlight(displaced)
            // Seed its slot before the first frame: the grid tile is already hidden by the hold,
            // so a frame where this one has no position to draw at is a blink.
            homeOf(displaced)?.let { homes[displaced] = it }
            val slide = retreat.getOrPut(displaced) { Animatable(0f) }
            launch { slide.animateTo(1f, tween(MoveSlideMillis)) }
        }
        retreat.keys.toList().forEach { key ->
            if (key == displaced) return@forEach
            val slide = retreat.getValue(key)
            launch {
                slide.animateTo(0f, tween(MoveSlideMillis))
                retreat.remove(key)
                moveState.releaseInFlight(key)
            }
        }
    }
    // Whatever is still held when the overlay stops drawing has nobody left to release it, and a
    // cell held in flight forever is a permanently invisible tile.
    DisposableEffect(moveState) { onDispose { moveState.clearInFlight() } }

    LaunchedEffect(origin, stageOrigin) {
        var aimed = origin
        var wasLive = true
        while (isActive) {
            withFrameNanos { }
            val liveNow = moveState.origin
            val hoveredNow =
                (if (liveNow != null) moveState.target else moveState.returning?.second) ?: origin
            // Once the move is called off the tile is aimed at its OWN slot: the same flight,
            // run backwards.
            val aimNow = if (liveNow != null) hoveredNow else origin
            // A cell scrolled entirely out of its viewport registers an empty rect; keeping the
            // last real one stops a tile in flight from snapping to the stage's corner.
            homeOf(origin)?.let { originHome = it }
            retreat.keys.toList().forEach { key -> homeOf(key)?.let { homes[key] = it } }
            val next = homeOf(aimNow) ?: aimHome
            // **The move ENDING is a hand-off too.** Released back onto its own slot, the aim
            // never changed, so nothing launched the decay: the tile hung in mid-air for the
            // length of the slide and then teleported home when the overlay stopped drawing it
            // (Dylan, 2026-09-25).
            val ended = wasLive && liveNow == null
            wasLive = liveNow != null
            when {
                // Under the finger: the residual is simply whatever separates the finger from
                // the slot it happens to be over, so releasing anywhere leaves the tile exactly
                // where it is and the decay below carries it from there.
                //
                // Measured from the FINGER, not from the slot the tile was lifted out of — see
                // [MoveModeState.carriedTopLeft]. The lifted slot moves while the body
                // edge-scrolls under the carry, and can leave the viewport altogether.
                liveNow != null && moveState.pointerDriven -> {
                    aimed = aimNow
                    carriedResidual.snapTo(moveState.carriedTopLeft(stageOrigin) - next)
                }
                aimNow != aimed || ended -> {
                    // Re-anchor onto the new slot WITHOUT moving the tile: everything it still
                    // has to travel becomes residual.
                    carriedResidual.snapTo(aimHome + carriedResidual.value - next)
                    aimed = aimNow
                    launch { carriedResidual.animateTo(Offset.Zero, tween(MoveSlideMillis)) }
                }
            }
            aimHome = next
        }
    }

    // The tiles have arrived; let the state machine forget the move.
    LaunchedEffect(settling) {
        if (settling == null) return@LaunchedEffect
        delay(MoveSlideMillis.toLong())
        moveState.settled()
    }

    // Where the exchange's two ends are marked, in this overlay's own space. The stage draws
    // them here rather than leaving them to the cards because this layer is ABOVE every card:
    // a marker inside one would sit under the tiles it is describing (Dylan, 2026-09-25).
    val extras = LocalMappoExtraColors.current
    val outOfRange = live != null && moveState.outOfRange
    Box(modifier.fillMaxSize()) {
        // The displaced tiles first, then the markers, then the carried one. That order is the
        // whole point: a tile sliding into the vacated slot must read as going UNDER the blue
        // marker, so which slot it is heading for is visible — while the tile in your hand stays
        // on top of everything, because it is the thing you are holding.
        //
        // A PROGRESS value rather than an animated offset, so both endpoints can keep moving
        // (the camera pans, the cards scroll) without the animation restarting.
        retreat.forEach { (key, slide) ->
            val command = commandAt(key) ?: return@forEach
            val home = homes[key] ?: return@forEach
            FloatingTile(
                command = command,
                config = config,
                position = lerp(home, originHome, slide.value),
                look = look,
            )
        }
        FloatingTile(
            command = lifted,
            config = config,
            position = aimHome + carriedResidual.value,
            look = look,
            // Carried out past the grid's reach: letting go now puts it back, and saying so on
            // the tile itself is the only place the user is actually looking.
            wash = extras.dropZoneInvalid.takeIf { outOfRange },
        )
        // BOTH markers over the carried tile, drawn last (Dylan, 2026-09-25 / 09-26).
        //
        // The tile in your hand sits right on the slot it is landing in, so underneath it the
        // green was the one marker you could never see. And the blue over the ORIGIN slot is what
        // now says "this is the tile you just picked up" — at the instant of a lift the carried
        // tile is still on that slot, so it wears the blue. That replaced a slight upscale on
        // the lifted tile, which read oddly once the washes went on top of it.
        if (live != null) {
            MoveMarker(extras.dropZoneOrigin, originHome, look)
            // No destination to mark while the move is out of range: it is going home, and it is
            // wearing red instead.
            if (!outOfRange && hovered != origin) MoveMarker(extras.dropZoneValid, aimHome, look)
        }
    }
}

/** One of the exchange's ends, washed over whatever is standing in it. */
@Composable
private fun MoveMarker(color: Color, position: Offset, look: TileLook) {
    Box(
        Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(look.width)
            .height(look.height)
            .clip(RoundedCornerShape(look.corner))
            .background(color.copy(alpha = MoveMarkerAlpha)),
    )
}

/** One tile in flight: the same face it wears in the grid, placed in the overlay's own space. */
@Composable
private fun FloatingTile(
    command: RowCommand,
    config: ControllerConfig?,
    position: Offset,
    look: TileLook,
    /** Painted over the tile — the cancel signal, when the carry has left the grid's reach. */
    wash: Color? = null,
) {
    val display = commandDisplay(command.binding, listOf(command.output), config)
    val colors = command.type.columnColors()
    val container = colors.tile.compositeOver(MinputElevatedContainer)
    val shape = RoundedCornerShape(look.corner)
    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(look.width)
            .height(look.height)
            .clip(shape)
            .background(container, shape)
            .then(tileOutline(container, defined = true, shape = shape, corner = look.corner))
            .then(
                if (wash != null) {
                    // Over the content, inside the clip: the tile itself goes the wash's colour
                    // rather than wearing a badge.
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(wash.copy(alpha = MoveWashAlpha))
                    }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TileContent(
            colors = colors,
            pressType = command.type,
            output = command.output,
            label = display.label,
            outputText = display.text,
            showDeviceIcon = display.glyph != null,
        )
    }
}

/**
 * **A tile's CHROME with nothing on it** — the fill, the press-type tint, the ring, and the "+"
 * where the slot is still empty.
 *
 * It exists for the basic view's morph (Dylan, 2026-09-24), which fades the button in behind a
 * label the row is already drawing: the label must not be drawn twice, so the arriving tile
 * brings only its face. It is also, deliberately, the same face [CommandTile] wears — same
 * fill, same ring, same plus — so the handover to the real tile at the end of the travel shows
 * nothing at all.
 */
@Composable
internal fun TileChrome(
    pressType: ActivatorType?,
    /** Does this slot hold a command? A "+" is unfilled and wears the hairline ring instead. */
    defined: Boolean,
    look: TileLook,
    modifier: Modifier = Modifier,
) {
    val colors = (pressType ?: ActivatorType.FULL_PRESS).columnColors()
    val container = if (!defined) Color.Transparent else colors.tile.compositeOver(MinputElevatedContainer)
    val shape = RoundedCornerShape(look.corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(container, shape)
            .then(tileOutline(container, defined, shape, look.corner)),
        contentAlignment = Alignment.Center,
    ) {
        if (!defined) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(RowTilePlusSize),
                tint = colors.plus,
            )
        }
    }
}

/**
 * A tile's ring: the family bevel on a command, a hairline outline on the row's "+".
 *
 * The "+" used to be strokeless — chrome-less by spec, so it wouldn't compete with the commands
 * beside it. It went the other way (Dylan, 2026-09-21): with nothing but a glyph, the slot had no
 * footprint, so neither the size of the thing you were about to create nor the fact that it is a
 * drop target read at all. The ring is faint enough to keep the hierarchy it was avoiding.
 */
@Composable
private fun tileOutline(
    container: Color,
    defined: Boolean,
    shape: RoundedCornerShape,
    corner: Dp,
): Modifier =
    if (defined) {
        Modifier.border(minputBevelBorder(container, corner), shape)
    } else {
        Modifier.border(
            MinputBoxStroke,
            MaterialTheme.colorScheme.outline.copy(alpha = EmptyTileOutlineAlpha),
            shape,
        )
    }

/**
 * What a tile shows — the "+", or a command's press glyph, label and name.
 *
 * Shared by the tile in the grid and by the floating copy the stage carries during a move
 * ([MoveOverlay]), so a tile in flight is the same object the user picked up.
 *
 * **The press glyph is positioned, not packed** (Dylan, 2026-09-21). It used to lead a Row, which
 * made it part of the tile's flex: a Long Press tile centred its name in what was left of the
 * tile rather than in the tile, so the same command sat at two different x-offsets depending on
 * its press type. The glyph is now pinned to the start edge and the name block is centred in the
 * tile, with the glyph's width reserved on BOTH sides so the two can never collide.
 */
@Composable
private fun TileContent(
    colors: PressTypeColors,
    pressType: ActivatorType?,
    output: BindingOutput?,
    label: String?,
    outputText: String,
    showDeviceIcon: Boolean,
) {
    if (output == null) {
        // The row's create affordance. A plus and nothing else: it is a slot, not a command.
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(RowTilePlusSize),
            // Alpha rides in the palette color itself — no extra .alpha() here, or the
            // value in Theme.kt would stop being what renders.
            tint = colors.plus,
        )
        return
    }
    RowTileContent(
        colors = colors,
        pressType = pressType,
        output = output,
        label = label,
        outputText = outputText,
        showDeviceIcon = showDeviceIcon,
    )
}

/**
 * A tile's face at ROW scale — the basic view's edit mode (Dylan, 2026-09-22).
 *
 * Deliberately says less than the table's tile, in two ways:
 *
 *  - **One line, not a stack.** A labelled command shows its label INSTEAD of its output name
 *    rather than above it, which is exactly what the resting row it replaces already showed
 *    ([CommandDisplay.line]) — so entering edit mode puts a capsule around the row's own words
 *    instead of rewriting them. The device glyph follows the same rule it does at rest: it
 *    qualifies an output NAME, so a command the user has named goes bare
 *    ([CommandDisplay.lineGlyph]).
 *  - **No press-type glyph.** With this little tile to spend, the colour carries the press type
 *    on its own, and the text goes white to hold against it ([RowTileTintedText]).
 */
@Composable
private fun RowTileContent(
    colors: PressTypeColors,
    pressType: ActivatorType?,
    output: BindingOutput,
    label: String?,
    outputText: String,
    showDeviceIcon: Boolean,
) {
    // Regular Press has no tint — its tile IS the ordinary container — so it keeps the resting
    // view's own content colour rather than shouting in white.
    val tinted = pressType != null && pressType != ActivatorType.FULL_PRESS
    val content = if (tinted) RowTileTintedText else MaterialTheme.colorScheme.onSurface
    Row(
        // The stroke rule: every tile wears a ring (bevel, or the "+" slot's hairline), so the
        // text's margin starts inside it.
        modifier = Modifier.fillMaxSize().minputStrokeInset().padding(horizontal = RowTileContentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (label == null && showDeviceIcon) {
            InputGlyphs.outputPainter(output)?.let { painter ->
                Icon(
                    painter,
                    contentDescription = null,
                    modifier = Modifier.size(TileOutputGlyphSize),
                    tint = content,
                )
                Spacer(Modifier.width(MinputGlyphLabelGap))
            }
        }
        Text(
            text = label ?: outputText,
            style = minputMiniTextStyle(),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The tile menu's verbs. Icons, no helper text (per the design) — these are self-evident, and
 * the two-line icon + title + helper form is reserved for actions that genuinely need
 * tutorializing.
 *
 * Paste stays LISTED but greyed when nothing has been copied, so the menu's shape doesn't
 * shift between cells.
 *
 * **Label, Type and Settings are additions to the specified list** (Edit / Copy / Paste / Move
 * / Clear): the table dropped the row's label field and cog, and without these the tile's label
 * would be unsettable, activator settings — crucially the CHORD PARTNER, without which a Chord
 * command can't function — would be unreachable, and with the press-type columns gone there
 * would be nowhere to say what a command fires on. Type is explicitly a stop-gap until the
 * per-command configuration page lands (Dylan, 2026-09-20).
 */
internal fun tileActions(
    defined: Boolean,
    clipboardOccupied: Boolean,
    onEdit: () -> Unit,
    onLabel: () -> Unit,
    onType: () -> Unit,
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
        MinputAction("Type", Icons.Filled.TouchApp, onClick = onType),
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
internal fun layerTileActions(
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
/**
 * Addressable per cell: "cell:<GROUP>:<SOURCE>:<sub-input key>:<slot>".
 *
 * The GROUP is in the tag because the zoomed scene holds every group's table at once
 * (2026-09-17) and sub-input keys repeat across groups — "dpad_up" belongs to the d-pad and to
 * both sticks. Without it, three cells answer to one tag. The SLOT replaced the activator type
 * when rows became stacks (2026-09-20), so a tag names a POSITION in a row, not a press type.
 */
internal fun cellTestTag(key: CellKey): String =
    "cell:${key.group.name}:${key.source.name}:${key.inputKey}:${key.slot}"

/**
 * The cell one grid step from [from], staying inside its own group: the single-table stepping
 * every editor had before the zoomed scene, and what a standalone editor still does. A step off
 * an edge goes nowhere. (The scene's own stepper crosses into the neighbouring group instead —
 * see RemapZoomScene.)
 *
 * [slots] reports how many tiles a row has, INCLUDING its trailing "+". Rows are no longer the
 * same length as each other (2026-09-20), so the stepper has to ask rather than assume: a row
 * with one command has two stops, the row under it may have five, and a step between them
 * clamps to what is actually there.
 */
internal fun stepCellWithinGroup(
    from: CellKey,
    dRow: Int,
    dCol: Int,
    slots: (RemapSimpleGroup, SimpleRowSpec) -> Int,
): CellKey? {
    val rows = from.group.rows
    val row = rows.indexOf(from.row).takeIf { it >= 0 } ?: return null
    val nextRow = (row + dRow).coerceIn(0, rows.lastIndex)
    val spec = rows[nextRow]
    val lastSlot = (slots(from.group, spec) - 1).coerceAtLeast(0)
    // A screen-space step, converted into the row's own slot direction — see [slotsRunLeftward].
    val nextSlot = from.slot + dCol * from.group.slotStepFor(from.row)
    return CellKey(from.group, spec, nextSlot.coerceIn(0, lastSlot))
}

/**
 * Walk a controller-driven move's drop target one step, taking focus with it.
 *
 * Focus FOLLOWS the target because focus is cell-anchored: leaving the ring on the cell the tile
 * was lifted from strands it behind the move, and it means the tile the user then activates IS
 * the destination, so confirming needs no focus change of its own. The handle comes from the
 * host, so a destination in ANOTHER group is reachable the same way.
 *
 * [owns] lets a handler that only speaks for part of the view (one table in the zoomed scene)
 * keep out of a move whose target has been carried elsewhere.
 */
internal fun stepMoveTargetBy(
    moveState: MoveModeState<CellKey>,
    stepTarget: (CellKey, Int, Int) -> CellKey?,
    focusHandle: (CellKey) -> FocusRequester,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    dRow: Int,
    dCol: Int,
    owns: (CellKey) -> Boolean = { true },
) {
    val current = moveState.target ?: return
    if (!owns(current)) return
    val next = stepTarget(current, dRow, dCol) ?: return
    if (next == current) return
    // A tick per cell crossed: with no finger on the screen the haptic is the only confirmation
    // that the drop target actually moved.
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    moveState.moveTargetTo(next)
    runCatching { focusHandle(next).requestFocus() }
}

/**
 * **Whether the activate button that LIFTED the current tile is still down.**
 *
 * There are three ways into a move — hold until it lifts and keep holding, hold until it lifts and
 * let go, or start steering before the hold ripens, which lifts the tile under you — and for a day
 * each implied its own way out. Dylan settled that 2026-09-26: the way IN no longer decides, a
 * single setting does ([MoveCommitGesture]), and all this has to remember is whether there is a
 * lifting press still outstanding, because that is the one press whose release might not mean
 * "place it".
 */
internal enum class LiftPress { None, Held }

/**
 * The keyboard while a CONTROLLER move is in flight: arrows walk the drop target, B / Escape
 * calls it off, the activate keys confirm.
 *
 * It belongs to whoever HOSTS the tiles rather than to the focused tile, because a held button
 * auto-repeats while focus moves — a press and its release can land on different tiles, so no
 * single tile sees both ends of the gesture. Pointer-driven moves never take this path; the
 * finger is already saying where to land.
 *
 * Returns whether the event was consumed. Consuming the arrows is what stops ordinary focus
 * traversal, leaving focus to track the drop target instead.
 */
internal fun moveModeKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    moveState: MoveModeState<CellKey>,
    /** Is the current drop target one this handler speaks for? */
    owns: (CellKey) -> Boolean,
    /** Is the activate button that lifted the tile still down? */
    liftPress: LiftPress,
    onLiftPress: (LiftPress) -> Unit,
    /** The user's choice of how a move is confirmed. */
    gesture: MoveCommitGesture,
    onStep: (dRow: Int, dCol: Int) -> Unit,
    onCommit: (Pair<CellKey, CellKey>?) -> Unit,
): Boolean {
    if (!moveState.active || moveState.pointerDriven) return false
    val target = moveState.target ?: return false
    if (!owns(target)) return false

    // Activate FIRST, and on the key's RELEASE — which is why this sits above the key-down
    // filter below.
    if (event.key in TileActivateKeys) {
        if (event.type == KeyEventType.KeyUp) {
            val press = liftPress
            onLiftPress(LiftPress.None)
            // Under "release to place", EVERY release places — including a release straight back
            // onto the tile's own slot, which puts it down where it started and ends the move
            // (Dylan, 2026-09-26). An earlier version made that case stay live, on the theory
            // that it read as picking a tile up to look around with; but that is the OTHER
            // setting's job, and having one mode quietly behave like the other is exactly the
            // inconsistency the setting was added to remove.
            //
            // Under "press again to place", the lifting press's own release never places
            // anything, which is what lets a carry survive any amount of looking around. Any
            // LATER press does.
            val confirms = press == LiftPress.None || gesture == MoveCommitGesture.ON_RELEASE
            if (confirms) onCommit(moveState.commit())
        }
        return true
    }

    if (event.type != KeyEventType.KeyDown) return true
    return when (event.key) {
        Key.DirectionUp -> { onStep(-1, 0); true }
        Key.DirectionDown -> { onStep(1, 0); true }
        Key.DirectionLeft -> { onStep(0, -1); true }
        Key.DirectionRight -> { onStep(0, 1); true }
        Key.Back, Key.Escape, Key.ButtonB -> {
            moveState.cancel()
            onLiftPress(LiftPress.None)
            true
        }
        else -> true // swallow the rest so focus can't wander mid-move
    }
}

/**
 * Is the stage's [MoveOverlay] standing in for the tile at [key] — and should the one in the
 * grid therefore go invisible?
 *
 * The tile being carried, the one it would displace, and any the carry has ALREADY displaced and
 * is still flying home ([MoveModeState.inFlight]) — walk a tile across three occupied slots and
 * all three of the tiles it disturbed are in the air at once. A move that has been CALLED OFF
 * still counts: its tiles are flying home and would flash back into their slots the instant the
 * state cleared.
 *
 * The current target is named explicitly as well as being held in flight, because the drawer
 * takes hold of it a frame after the target changes and a one-frame double is a flicker.
 */
internal fun MoveModeState<CellKey>.carriedByOverlay(
    key: CellKey,
    hasCommand: Boolean,
    hosted: Boolean,
): Boolean {
    if (!hosted) return false
    val flightOrigin = origin ?: returning?.first ?: return false
    val flightHovered = target ?: returning?.second
    return key == flightOrigin ||
        (hasCommand && key in inFlight) ||
        (hasCommand && key == flightHovered && flightHovered != flightOrigin)
}

/** Keys that activate a focused tile. Mirrors what Compose's own `clickable` accepts, plus the
 *  gamepad A button so a controller's primary action works without a d-pad center. */
/** The keys that walk a move's drop target — and, before a hold has ripened, the ones that mean
 *  "I have started moving, lift it now". See the coyote branch in [CommandTile]. */
internal val MoveStepKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
)

internal val TileActivateKeys = setOf(
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
    // One utility group per side since 2026-09-26 — Select on the left, Start on the right,
    // with room for the back paddles and other per-side extras to come. Named for the SIDE
    // rather than for the one button each currently holds, because that is what they are.
    RemapSimpleGroup.LEFT_UTILITY -> "Left Utility"
    RemapSimpleGroup.RIGHT_UTILITY -> "Right Utility"
}



// ── Shared press-type vocabulary (moved from the retired detail-pane editor) ─────────────────

/** Canonical Steam render order for press types. The advanced table uses [pressTypeSortOrder]
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
 * The table's column names. User-specified wording (Dylan, 2026-09-18), replacing the one-word
 * abbreviations the columns carried while they also had an icon apiece.
 *
 * NB: START_PRESS reads "Down Press" and RELEASE_PRESS "Up Release" — Mappo wording, for the
 * activators that fire on the down / up edge. VDF import/export must map Steam's "Start Press" ↔
 * "Down Press" and "Release Press" ↔ "Up Release".
 */
internal fun ActivatorType.columnLabel(): String = when (this) {
    ActivatorType.FULL_PRESS -> "Regular Press"
    ActivatorType.LONG_PRESS -> "Long Press"
    ActivatorType.DOUBLE_PRESS -> "Double Press"
    ActivatorType.START_PRESS -> "Down Press"
    ActivatorType.RELEASE_PRESS -> "Up Release"
    ActivatorType.CHORDED_PRESS -> "Chord Press"
    ActivatorType.SOFT_PRESS -> "Soft Press"
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

/**
 * The glyph a press type wears on its tiles and in the type picker (Dylan's set, 2026-09-20).
 *
 * **Regular Press has none** — it is the default case, and marking it would put an icon on
 * nearly every tile in the view saying only "ordinary". Material icons throughout, for the same
 * reason the dropdown arrow is Material's: iconography is platform chrome, not minput's.
 */
internal fun ActivatorType.pressIcon(): ImageVector? = when (this) {
    ActivatorType.FULL_PRESS -> null
    ActivatorType.LONG_PRESS -> Icons.Filled.HourglassEmpty
    ActivatorType.DOUBLE_PRESS -> Icons.Filled.KeyboardDoubleArrowDown
    ActivatorType.CHORDED_PRESS -> Icons.Filled.Workspaces
    ActivatorType.START_PRESS -> Icons.Filled.ArrowDownward
    ActivatorType.RELEASE_PRESS -> Icons.Filled.ArrowUpward
    ActivatorType.SOFT_PRESS -> Icons.Filled.Adjust
}

// ── Table metrics ────────────────────────────────────────────────────────────────────────────

private val TileOutputGlyphSize = 14.dp

/** The PRESS-TYPE glyph leading a tile. Larger than the output's device glyph (Dylan,
 *  2026-09-20): it identifies the tile, where the device glyph only qualifies its name. */
private val TilePressGlyphSize = 19.dp

/** The press-type picker's row rhythm. */
private val TypeDialogTitleGap = 10.dp
private val TypeDialogRowCorner = 8.dp
private val TypeDialogRowPadding = 8.dp

/** How strongly the "+" tile's outline reads. A hairline ring (Dylan, 2026-09-21) that gives the
 *  create affordance a FOOTPRINT — a bare glyph floating in the row didn't say how big the thing
 *  you were about to make would be, nor that the slot was a drop target. Deliberately far below
 *  the bevel the commands wear: it marks an empty slot, not another command. */
private const val EmptyTileOutlineAlpha = 0.22f

/** How long a displaced tile takes to slide aside during a swap preview. */
private const val MoveSlideMillis = 200

/**
 * How far a carried tile may stray from every cell before the move reads as ABANDONED — carry it
 * further and the tile goes red, and letting go puts it back (Dylan, 2026-09-25).
 *
 * **Deliberately smaller than a tile**, so the open middle of the grid — the controller's own
 * column — is the cancel. That is the gesture: carry a tile out over the controller and let go.
 * Dylan asked for it explicitly after a first pass at 160dp, which was wide enough to reach
 * across that column and so left touch moves with no way to call them off at all.
 *
 * Measured from the tile's EDGES, not its centre, so "close-ish to a tile" still lands on it: a
 * tile whose rim is within this of a slot is claimed by that slot even while most of it hangs
 * over the gap.
 */
internal val MoveCancelDistance = 23.dp
/** How strongly the origin / landing markers wash their cell. Low enough to read as a marked
 *  SLOT rather than a filled tile. */
internal const val MoveMarkerAlpha = 0.3f

/** How strongly the cancel wash covers the tile it is refusing. Stronger than a slot marker —
 *  it has to change what the tile READS as, not annotate the space around it. */
private const val MoveWashAlpha = 0.55f

/** How close to the viewport edge a dragging finger must get before the table scrolls under
 *  it, and how far it scrolls per frame while it stays there. */
private val EdgeScrollZone = 28.dp
private val EdgeScrollStep = 6.dp

// ── The basic view's tiles (edit mode, 2026-09-22) ───────────────────────────────────────────

/**
 * A row tile's width. Every tile in the view is this wide, exactly as the table's are: equal
 * widths are what let a column of commands be scanned rather than read.
 *
 * Sized to carry the same run of text the resting rows were floored at (`AssignmentMinChars`)
 * plus a device glyph, so entering edit mode widens a box but doesn't transform it.
 */
private val RowTileWidth = 88.dp

/**
 * The gap between a group's tiles, **in PHYSICAL pixels** — Dylan's own measurement off the
 * device (2026-09-23), converted at runtime exactly as the layouts drawer's width is. It is the
 * air between two buttons of one group, horizontally between tiles and vertically between rows
 * alike.
 *
 * The tile HEIGHT was measured the same way (42px, stroke inclusive) and has since become the
 * library's [MinputSize.Small] variant (Dylan, 2026-10-05: the tiles ARE the small controls) —
 * a dp value like every other control size, equal to those 42px at the test device's density.
 */
private const val RowTileGapPx = 6

/** The row height of BOTH modes — the [MinputSize.Small] control height. See [rowTileLook]. */
internal fun rowTileHeight(): Dp = MinputSize.Small.height

/** [RowTileGapPx] in dp on this device: between tiles on a row, and between a group's rows. */
@Composable
internal fun rowTileGap(): Dp = with(LocalDensity.current) { RowTileGapPx.toDp() }

/** A row tile's text inset. Tighter than a Small button's [MinputSize.contentPadding]: a tile is
 *  a fixed-width cell that centres its label, so this is only the ellipsis margin, not a frame. */
private val RowTileContentPadding = 6.dp

/** The "+" on a row tile, scaled to it the way [EmptyTilePlusSize] is to the table's. */
private val RowTilePlusSize = 14.dp

/**
 * The text on a TINTED row tile (Dylan, 2026-09-22).
 *
 * Edit mode drops the press-type glyph and lets the tile's own colour say which press type it
 * is, so the text has to hold against six saturated fills rather than one neutral surface —
 * hence white, explicitly, rather than a content token that tracks the surface underneath.
 * Regular Press keeps `onSurface`: its tile is the ordinary container, and white there would
 * make the commonest command the loudest thing on the view.
 */
private val RowTileTintedText = Color.White

/**
 * **How big a tile is** — one shape, asked for by every surface that draws one.
 *
 * There used to be two: the advanced table's (two lines, a press-type glyph, room for both) and
 * the row's, the experiment rendering the SAME [CommandTile] at a smaller size with less on its
 * face. The advanced view is gone (2026-09-27), so what is left is the row's, and its face says
 * ONE line — the user's label if there is one, else the command's name
 * ([CommandDisplay.line], which is what the resting row already showed) — with no press-type
 * glyph, the press type being carried by the tile's colour alone.
 *
 * Kept as a value rather than folded into constants because the tile is drawn in three places
 * (the grid, the move overlay's floating copy, the move marker) and they must never disagree.
 */
internal class TileLook(
    val width: Dp,
    val height: Dp,
    val corner: Dp,
)

/** The one tile shape: a row's, at the row height both the resting rows and the tiles share. */
@Composable
internal fun rowTileLook(): TileLook {
    val height = rowTileHeight()
    return remember(height) { TileLook(RowTileWidth, height, height / 2) }
}

