package com.mappo.ui.screen.remap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.ui.component.LocalRightStick
import com.mappo.ui.component.RightStickDeadzone
import com.mappo.ui.component.rememberMoveModeState
import androidx.compose.material3.MaterialTheme
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputOverflowScroll
import com.mappo.ui.minput.MinputScrollbar
import com.mappo.ui.minput.MinputPodGap
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputPressIndication
import com.mappo.ui.screen.home.LocalScreenAspect
import com.mappo.ui.screen.softDropShadow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import com.mappo.ui.minput.MinputAction
import com.mappo.ui.minput.MinputActionMenu
import com.mappo.ui.minput.MinputMenuPlacement
import com.mappo.ui.screen.remap.settings.SourceModeSettingsSchema

/**
 * The remap controls view: the controller's own 3 × 3 grid, and every group's commands edited in
 * place on it.
 *
 * **There is one view now** (Dylan, 2026-09-27: "I am all in on this experimental view/edit mode,
 * which means I think we can finally ditch any code related to the former Advanced view"). Until
 * then this stage was a real ZOOM — every element carrying a REST rect in the grid and a ZOOM rect
 * in a scene under a camera parked on one group's card, with its live rect the two interpolated —
 * because the basic view and a separate advanced editor were being compared against each other on
 * the device. Edit mode won, so the zoom, the camera, the scene geometry (RemapZoomScene.kt), the
 * cards and the advanced tables are all gone; what the zoom taught the stage stayed, because edit
 * mode needed exactly the same things: one move state spanning every group, one focus handle per
 * cell, and a stepper that crosses between them.
 *
 * ```
 *  ┌──────────────────────────┐        ┌──────────────────────────┐
 *  │  LT│RT      ▲       LB│RB│        │ LT│RT     ▲      LB│RB   │
 *  │ dpad│face  ███  face│   │   tap   │ ▢▢▢│     ███         │   │
 *  │  LS│ util │RS        │   │   ⟶     │ ▢▢▢│ (tiles)         │   │
 *  └──────────────────────────┘        └──────────────────────────┘
 * ```
 *
 * The grid is a 3 × 3 matrix: the two flanks, the controller between them, the utility group under
 * it. Every cell ANCHORS TOWARD THE CENTRE one (Dylan, 2026-09-17) — the top band sits on the
 * bottom of its row, the bottom band on the top of its own, and each flank hugs its inner edge —
 * so the eight boxes cluster around the controller instead of being flung to the four corners.
 *
 * What a group's rows turn into, and how, is [EditReveal] and the rows themselves
 * (RemapSimpleView.kt); what this file owns is the GRID they sit in, the one scroller under it,
 * and the camera over both: [StageCamera] (where the view goes, and how the layout is built around
 * it), [PanReach] (the rules that decide it), [EditMorphPlan] (the columns through a reveal) and
 * [ReframePlan] (a group changing shape with the view settled).
 */
@Composable
internal fun RemapStage(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    /** A group box was activated — enter EDIT MODE on it. */
    onOpenGroup: (RemapSimpleGroup) -> Unit,
    /** The cursor entered this group — in edit mode, what makes it the group revealed. */
    onGroupFocused: (RemapSimpleGroup) -> Unit = {},
    /** A tile was lifted, or put down. */
    onCarrying: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * **EDIT MODE: which groups' rows are command tiles, and how far into being them** — see
     * [EditReveal], which is the whole of it (Dylan, 2026-09-22 for the mode, 2026-09-27 for
     * revealing one group at a time).
     *
     * The mode is view-WIDE however many groups show tiles: one move state, one cursor, one
     * keyboard, and a command can be carried from any group to any other. What the reveal
     * settles is only which groups have tiles to carry it between right now.
     */
    reveal: EditReveal = EditReveal.Rest,
    /** One-shot: seat the cursor on this group's first tile now that the tiles are real. */
    editSeatGroup: RemapSimpleGroup? = null,
    onEditSeated: () -> Unit = {},
    /** Bumped when the view loses focus in edit mode, to re-seat the cursor on a tile. */
    editFocusTick: Int = 0,
    /**
     * A command the cursor should land on AS SOON AS IT EXISTS, named by its binding id.
     *
     * Seating by GROUP can only ever mean "that group's first tile", which is where the cursor
     * went after every add and every move — make a command on the button pad while editing from
     * the right trigger and you were returned to the right trigger (Dylan, 2026-09-24). A
     * command keeps its id through both operations, so the id is the one handle that says
     * exactly which tile the user just acted on, wherever the row's sort order puts it. Adding
     * goes out through the full-screen picker and back, which is why the claim is held saveably
     * by the screen rather than here.
     */
    seatCommand: Long? = null,
    /** Claim a command for the cursor, or clear the claim with null. */
    onSeatCommand: (Long?) -> Unit = {},
    // One-shot: the box that should reclaim controller focus (edit mode was just left, or the
    // screen is being seated for the first time).
    focusSeatGroup: RemapSimpleGroup? = null,
    onFocusSeated: () -> Unit = {},
) {
    val groups = remember { RemapSimpleGroup.values().toList() }
    // Aliases for the three things the whole stage asks of the reveal. `editGroup` is the group
    // the CURSOR is in — which, when only one group reveals its tiles, is the group revealed.
    val editing = reveal.editing
    val editSettled = reveal.settled
    val editGroup = reveal.focus

    // ONE move state for every table: a command lifted in any of them can be carried to any
    // other. Every cell registers its window bounds here, so a finger crossing from one card to
    // the next resolves targets as it goes.
    val moveState = rememberMoveModeState<CellKey>()
    // Frames a group when its tiles change — a tile added, cleared or carried in. Declared up
    // here because the cursor-seating effects below are what claim it. See [ReframePlan].
    val reframe = remember { ReframePlan() }
    // How far the pan reaches outboard of the group being worked on — the one piece of camera
    // state that outlives a travel. Up here because a committed move is one of the things that
    // sets it. See [PanReach].
    val panReach = remember { PanReach() }
    /** Which group's action menu is up, if any — one at a time, so it lives here rather than in
     *  each box. Summoned by HOLDING a group, which is what used to open the advanced view. */
    var menuGroup by remember { mutableStateOf<RemapSimpleGroup?>(null) }
    val cellFocus = remember { mutableStateMapOf<CellKey, FocusRequester>() }
    val focusHandle: (CellKey) -> FocusRequester = { key ->
        cellFocus.getOrPut(key) { FocusRequester() }
    }
    val order = LocalCommandOrder.current
    // How many tiles a row shows — its commands plus the "+". The stepper asks for this because
    // rows are variable-length stacks now (2026-09-20), not six fixed press-type columns.
    val slotsOf: (RemapSimpleGroup, SimpleRowSpec) -> Int = { _, spec ->
        rowSlotCount(rowCommandsFor(viewingSet, viewingLayer, spec, order).size)
    }
    val stepTarget: (CellKey, Int, Int) -> CellKey? = { key, dRow, dCol ->
        stepCellAcrossGroups(key, dRow, dCol, slotsOf)
    }
    /**
     * **A carried command has been written but not yet seen** (Dylan, 2026-09-27).
     *
     * The reveal is held open across this window, which is what keeps the camera's motion the
     * animation it has always been: the write lands a frame or several after the carry ends, the
     * group it lands in changes shape, and that change is [ReframePlan]'s job — a travel of its own
     * with its own easing, the very one that frames a group when a command is added to it. Let the
     * reveal start collapsing first and the reframe stands down for the length of it, leaving the
     * shape change to be applied raw ("the camera movement is now instantaneous").
     */
    var landing by remember { mutableStateOf(false) }
    /**
     * **Where a claimed command was when it was claimed** — for a MOVE, the cell it is leaving.
     *
     * A claim exists so the cursor can follow a command that does not exist YET (an add goes out to
     * the picker and back). A MOVED command exists all along, at its old address, so the claim would
     * otherwise resolve on the very next frame against the config as it stands BEFORE the write
     * lands — seating the cursor straight back where the command came from and spending the claim,
     * which is precisely what claiming by id was introduced to stop (Dylan, 2026-09-24: "very
     * unintuitive"). So a claim ignores the command sitting where it already was.
     */
    var claimedFrom by remember { mutableStateOf<CellKey?>(null) }
    val onMoveCommitted: (CellKey, CellKey) -> Unit = { from, to ->
        // Rows resolve INDIVIDUALLY: one card can span two sources (the shoulder is a trigger
        // plus a bumper), and those are separate binding groups. A command only points at its
        // row, so carrying one across groups is the same operation as moving it within one.
        val lifted = rowCommandsFor(viewingSet, viewingLayer, from.row, order).getOrNull(from.slot)
        val landedOn = rowCommandsFor(viewingSet, viewingLayer, to.row, order).getOrNull(to.slot)
        val toId = viewingSet?.presetFor(to.source)?.group?.group?.id
        if (lifted != null && toId != null) {
            // A null landing command means the row's "+": an ADD, not a swap.
            callbacks.onMoveCommand(lifted.id, toId, to.inputKey, landedOn?.id)
            // The group the tile lands in is shown whole, as if it had just been opened.
            panReach.committed = to.group
            landing = true
            claimedFrom = from
            // The cursor goes WITH the command. Two steps, because the move is a round trip
            // through the repository: the destination cell exists NOW, so take it immediately
            // and keep the cursor inside the body while the write comes back — otherwise focus
            // has nowhere to be and the window lights its first focusable, the layouts button
            // top left. Then the claim below lands it on the command itself, wherever the row's
            // sort order has put it (Dylan, 2026-09-24).
            runCatching { focusHandle(to).requestFocus() }
            onSeatCommand(lifted.id)
        }
    }

    // ── EDIT MODE (2026-09-22) ───────────────────────────────────────────────────────────────
    //
    // The basic view's rows become the same tiles the advanced tables are made of, everywhere at
    // once. Everything they need already exists on the stage — one move state spanning every
    // group, one focus handle per cell, one stepper that crosses between groups — because the
    // zoomed scene needed exactly the same things. What is new here is only the part a card
    // normally owns: the two dialogs a tile's menu summons, and the keyboard while a carried
    // tile is in flight.
    var labelTarget by remember { mutableStateOf<LabelEdit?>(null) }
    var typeTarget by remember { mutableStateOf<TypeEdit?>(null) }
    var liftPress by remember { mutableStateOf(LiftPress.None) }
    val commitGesture = LocalMoveCommitGesture.current
    val haptic = LocalHapticFeedback.current
    val commitMove: (Pair<CellKey, CellKey>?) -> Unit = { pair ->
        liftPress = LiftPress.None
        pair?.let { (from, to) -> onMoveCommitted(from, to) }
    }
    // Whatever exists of edit mode, including through a collapse that has outlived the intent.
    // Where the cursor last was, in WINDOW space (the space the cells register themselves in).
    // A plain holder rather than snapshot state on purpose: it is written from a focus callback and
    // read only inside the seating effect, and a state write in that callback would recompose the
    // whole stage on every d-pad step — the same reason [onFocusWithin] writes nothing either.
    val cursorTrace = remember { CursorTrace() }
    val editHost = if (editing) {
        RowEditHost(
            moveState = moveState,
            focusHandle = focusHandle,
            onCommitMove = commitMove,
            onControllerLift = { liftPress = it },
            callbacks = callbacks,
            editable = viewingLayer == null,
            onLabel = { labelTarget = it },
            onType = { typeTarget = it },
            onCellFocused = { key ->
                cursorTrace.slot = key.slot
                moveState.boundsOf(key)?.takeIf { !it.isEmpty }?.let { cursorTrace.y = it.center.y }
            },
        )
    } else null
    // A tile in flight is the reveal's one exception — every group opens for as long as one is
    // being carried, so it can be taken anywhere (see [EditReveal]).
    LaunchedEffect(moveState.active, landing) { onCarrying(moveState.active || landing) }
    // The claim is the window's end: it is cleared the moment the command turns up and the cursor
    // has been put on it (below), and cleared as stale if edit mode is left first. Keyed on the
    // claim rather than set from that one place so a claim that never resolves cannot wedge the
    // reveal open.
    LaunchedEffect(seatCommand) {
        if (seatCommand == null) {
            landing = false
            claimedFrom = null
        }
    }
    // ...and the window's other end: a new config IS the write, whatever it turns out to say. A
    // move that changes nothing would otherwise hold the reveal open on a claim that never resolves.
    LaunchedEffect(viewingSet, viewingLayer) { landing = false }
    // ...and that reveal moves every cell out from under a finger that has not itself moved, so the
    // drop target has to be re-resolved once it lands. The pointer path only re-resolves on pointer
    // events, which is why carrying to the window's edge does the same thing (see the edge-scroll).
    LaunchedEffect(reveal.settled) {
        if (reveal.settled && moveState.active && moveState.pointerDriven) {
            moveState.refreshTargetAtPointer()
        }
    }
    // Seat the cursor on the group the user selected — without it, entering edit mode leaves
    // focus on the box that is no longer a focus target. It waits for the morph to land: a tile
    // mid-travel is an inert ghost with no focus to take. Re-runs on [editFocusTick] too, since
    // a tap clears Compose focus wholesale and in edit mode there is no box to recover onto.
    /**
     * **The tile of [group] nearest to where the cursor came from** (Dylan, 2026-09-27: "walking
     * into a collapsed group should focus the tile closest to where you came from").
     *
     * Nearest is answered in the two dimensions separately, because only one of them is measurable
     * at the moment it is asked — the seat happens on the frame the tiles appear, while they are
     * still collapsed on top of one another at the start of their travel:
     *
     *  - the ROW comes from geometry, the y of its own registered cell rect against the y the cursor
     *    left. Rows keep their height and their place throughout the morph, so this is stable from
     *    the first frame — and it is the dimension that matters, since a group's rows are what a
     *    hop up or down chooses between.
     *  - the SLOT is carried over by index, clamped to what this row has. Tiles are one fixed width
     *    ([TileLook]), so slot n sits at the same x in every row of a column; and a hop ACROSS the
     *    columns can only ever start from slot 0, the inner edge, because everything outboard of it
     *    is reached by stepping along the row first.
     *
     * Falls back to the group's first tile, which is where every seat used to land: with no trace
     * yet (edit mode opened from the resting view) there is nothing to be near.
     */
    fun nearestCell(group: RemapSimpleGroup, from: CursorTrace): CellKey {
        val fallback = CellKey(group, group.rows.first(), 0)
        val y = from.y ?: return fallback
        val rows = group.summaryRows.filter { it in group.rows }
        val row = rows.minByOrNull { spec ->
            val rect = moveState.boundsOf(CellKey(group, spec, 0))?.takeIf { !it.isEmpty }
                ?: return@minByOrNull Float.MAX_VALUE
            abs(rect.center.y - y)
        } ?: return fallback
        val slots = slotsOf(group, row)
        if (slots <= 0) return fallback
        return CellKey(group, row, from.slot.coerceIn(0, slots - 1))
    }
    LaunchedEffect(editSeatGroup, editFocusTick) {
        // A named command outranks a group: it says which tile, not merely which neighbourhood,
        // and it is the whole reason the cursor stopped being returned to where it came in.
        if (seatCommand != null) return@LaunchedEffect
        val group = editSeatGroup ?: editGroup.takeIf { editFocusTick > 0 && editSettled }
            ?: return@LaunchedEffect
        // The tiles compose on this frame; their requesters attach with them — and so do the cell
        // rects [nearestCell] measures against, which is why it is asked for after the wait.
        withFrameNanos { }
        runCatching { focusHandle(nearestCell(group, cursorTrace)).requestFocus() }
        onEditSeated()
    }
    // Where a command sits in the tiled rows right now, if it is on screen at all.
    fun locate(commandId: Long): CellKey? = groups.firstNotNullOfOrNull { group ->
        group.rows.firstNotNullOfOrNull { row ->
            rowCommandsFor(viewingSet, viewingLayer, row, order)
                .indexOfFirst { it.id == commandId }
                .takeIf { it >= 0 }
                ?.let { slot -> CellKey(group, row, slot) }
        }
    }
    // The claimed command, seated the moment it turns up. Re-runs on the config precisely
    // because the command is NOT there yet when the claim is made: an add and a move are both
    // writes that come back a frame or several later — an add having gone out to the picker and
    // returned in between. The claim is cleared when it lands, so an ordinary edit elsewhere
    // never drags the cursor back here.
    LaunchedEffect(seatCommand, editGroup, editSettled, viewingSet, viewingLayer) {
        val claimed = seatCommand ?: return@LaunchedEffect
        // No tiles to seat on: the claim is stale (edit mode was left while it was outstanding).
        if (editGroup == null) return@LaunchedEffect onSeatCommand(null)
        if (!editSettled) return@LaunchedEffect
        val cell = locate(claimed) ?: return@LaunchedEffect
        // Still where it was: the write behind the move has not come back yet, and the cursor is
        // already waiting at the destination (see [claimedFrom]).
        if (cell == claimedFrom) return@LaunchedEffect
        // Bring the whole group into view, "+" tile and all — not merely the tile itself, which
        // is all focus does on its own (Dylan, 2026-09-25: "the camera and scroll migrate to the
        // very edge of that group, including the empty tiles"). The travel overrides that minimal
        // scroll rather than racing it: while it runs, the view's position is its own function of
        // the travel, whatever the scroller is doing underneath. Claimed BEFORE the frame wait —
        // it reads the layout, not the focus requesters, so it need not wait for those to attach.
        reframe.frameGroup = cell.group
        reframe.frameTick++
        withFrameNanos { }
        runCatching { focusHandle(cell).requestFocus() }
        onSeatCommand(null)
    }

    val painter = painterResource(R.drawable.controller_placeholder)
    // The image's own proportions, used by BOTH geometries so the picture scales uniformly
    // across the zoom instead of re-fitting inside cells of different shapes.
    val aspect = remember(painter) {
        val size = painter.intrinsicSize
        if (size.isSpecified && size.width > 0f) size.height / size.width else DefaultControllerAspect
    }
    val interactions = remember { groups.associateWith { MutableInteractionSource() } }
    // One focus handle per group BOX, held here rather than inside the box: its backing rectangle
    // opens the same group and has to be able to seat the cursor on it the same way (see
    // [StageGroupBacking]).
    val boxFocus = remember { groups.associateWith { FocusRequester() } }
    // How lit each group's backing rectangle is — 0 resting, 1 with the cursor in it (its box at
    // rest, one of its tiles in edit mode). One Animatable per group rather than one "which group"
    // state, because each group animates its own way independently: focus arrives at the next
    // group before it leaves the last, and both travels overlap.
    val backingLight = remember { groups.associateWith { Animatable(0f) } }
    val backingScope = rememberCoroutineScope()

    // Where the stage itself sits in the window. The move state registers cells in WINDOW space
    // — the one space every card shares — so this is what converts a cell's rect into a position
    // in the overlay that draws the tile in flight (see [MoveOverlay]).
    var stageOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .onGloballyPositioned { stageOrigin = it.positionInWindow() },
    ) {
        val viewportW = maxWidth
        val viewportH = maxHeight
        val density = LocalDensity.current
        // The window's own size in pixels. The stage measures against THIS rather than its
        // incoming constraints, which the body scroller leaves unbounded across.
        val viewportWPx = with(density) { viewportW.roundToPx() }
        val viewportHPx = with(density) { viewportH.roundToPx() }
        /**
         * **The TARGET pan, in pixels: [PanTargetTiles] tiles' worth of box** (Dylan's numbers off
         * the device; not a setting since 2026-09-28). What a group is given beyond its own glyph
         * when nothing wider has been asked for — see [PanReach] for when that is.
         *
         * The SCREEN CANVAS's shape decides it ([LocalScreenAspect]), not this box's: the stage is
         * always wider than it is tall once the bars have taken their share, so its own aspect says
         * nothing about whether the app is running 1:1 or expanded. Nor the window's — the compact
         * 1:1 screen is a square drawn inside the full display, so the window never changes shape;
         * the configuration is only the fallback outside a frame. It is a plus-the-box-padding
         * figure because what it is compared against — a column's width, a group's own width — is
         * made of box widths.
         */
        val windowAspect = LocalScreenAspect.current ?: LocalConfiguration.current.let { config ->
            if (config.screenHeightDp <= 0) 1f else config.screenWidthDp.toFloat() / config.screenHeightDp
        }
        val panTarget = with(density) {
            val tiles = if (windowAspect <= SquareWindowAspect) PanTargetTilesSquare else PanTargetTiles
            tiledTableWidth(tiles) + GroupBoxPaddingX.roundToPx() * 2
        }
        // The BODY's one scroller. Whether the right stick belongs to it is not asked here any
        // more: every scroller that can scroll puts itself forward and the arbiter picks (see
        // [com.mappo.ui.component.StickScrollArbiter]). Zoomed, this measures exactly one
        // viewport and so isn't a candidate at all, which is what the old `!zoomed` said by
        // hand; at rest with nothing focused it is the only candidate, which is the case the
        // old focus-only gate got wrong.
        val bodyScroll = rememberScrollState()
        // **A scroll the USER made gives the pan back to the target** (see [PanReach]). Counted
        // only while a finger is on the stage or the right stick is pushed — never from focus
        // alone, so the d-pad walking a long row (whose bring-into-view scrolls the body) is not
        // mistaken for the user taking the view over.
        val rightStick = LocalRightStick.current
        LaunchedEffect(bodyScroll) {
            snapshotFlow { bodyScroll.value }.collect {
                if (panReach.pointerDown || abs(rightStick.value.x) > RightStickDeadzone) {
                    panReach.userScrolled = true
                }
            }
        }
        // The grid's columns through a reveal's travel, and the camera over them — see
        // [EditMorphPlan] and [StageCamera].
        val editMorph = remember { EditMorphPlan() }
        val camera = remember { StageCamera() }
        val reframeTravel = remember { Animatable(1f) }
        // What a travel's DRAWN displacement looks like to the scroll cues — only the part of it
        // inside the real range (see [StageCamera.place]). The cues read it because the scroller's
        // own value says nothing about a displacement the scroller is not the one applying (see
        // MinputScrollbar); handed a raw displacement they lit up over a grid with nothing to scroll
        // (Dylan, 2026-09-28: "the phantom scroll").
        val contentShift = remember { mutableFloatStateOf(0f) }
        val shiftProbe = LocalBodyShiftProbe.current
        // Bumped when a travel hands its shift over to the scroller. The hand-off often scrolls to
        // exactly where the scroller already is, which invalidates nothing — leaving the travel's
        // last frame on screen, and its last shift in the cues, until something unrelated happens
        // to lay the view out again. Read by the layout so a hand-off always lays it out once more.
        val landedTick = remember { mutableIntStateOf(0) }
        // Where the resting grid wants the view to sit — the scroll that puts the controller
        // dead centre (see [restSpan]). Published from the layout, because only the layout knows
        // how wide the boxes came out; -1 until the first pass has run.
        val restCentreScroll = remember { mutableIntStateOf(-1) }
        LaunchedEffect(reframe.request.intValue) {
            if (reframe.request.intValue == 0) return@LaunchedEffect
            reframe.arm(reframe.request.intValue)
            reframeTravel.snapTo(0f)
            reframeTravel.animateTo(1f, tween(EditMorphMillis, easing = FastOutSlowInEasing))
            val landed = camera.land(byReframe = true) ?: return@LaunchedEffect
            bodyScroll.scrollTo(landed)
            camera.settle(byReframe = true)
            landedTick.intValue++
        }
        // The travel is over: the scroller takes the value the camera planned, and the camera
        // settles into the layout built around it. The travel draws independently of the scroller,
        // so nothing on screen moves in between.
        LaunchedEffect(editSettled) {
            if (!editSettled) return@LaunchedEffect
            withFrameNanos { }
            val landed = camera.land(byReframe = false) ?: return@LaunchedEffect
            bodyScroll.scrollTo(landed)
            camera.settle(byReframe = false)
            landedTick.intValue++
        }

        // ── Carrying a tile to the edge scrolls the body under it (Dylan, 2026-09-25) ────────
        //
        // With the whole grid one scroller and no card clipping anything, a command can only be
        // taken somewhere off screen if the view follows the finger there. The controller path
        // gets this free from focus bring-into-view; the pointer path had nothing, so a tile
        // could only ever be dropped within the window it was lifted in.
        //
        // The scroll re-resolves the drop target as it goes: the finger can sit still while the
        // content moves beneath it, and what is under the finger changes without the finger.
        val carryingByPointer = moveState.pointerDriven && moveState.active
        val carryBandPx = with(density) { CarryEdgeBand.toPx() }
        val carryPlateauPx = with(density) { CarryEdgePlateau.toPx() }
        val carrySpeedPx = with(density) { CarryEdgeSpeed.toPx() }
        LaunchedEffect(carryingByPointer) {
            if (!carryingByPointer) return@LaunchedEffect
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // Per SECOND, off the frame clock — the first cut moved a fixed step per frame,
                // which is both frame-rate dependent and, at 60fps, a sprint (Dylan: "scrolls
                // too quickly"). SQUARED across the band as well, so most of the band creeps and
                // only the last few pixels of the rim travel at speed.
                val seconds = (now - previous) / 1_000_000_000f
                previous = now
                val ramp = carryEdgePush(
                    x = moveState.pointerWindow.x - stageOrigin.x,
                    viewportW = viewportWPx.toFloat(),
                    band = carryBandPx,
                    plateau = carryPlateauPx,
                )
                if (ramp != 0f) {
                    // Squared over the ramp so the outer part of the band still creeps; the
                    // plateau is already saturated, so this costs nothing at speed.
                    bodyScroll.scrollBy(ramp * abs(ramp) * carrySpeedPx * seconds)
                    // The cells moved under a stationary finger, so the drop target has to be
                    // re-resolved. The tile itself needs nothing: it is drawn from the finger's
                    // own position (see MoveModeState.carriedTopLeft).
                    moveState.refreshTargetAtPointer()
                }
            }
        }

        // The resting view opens with the controller centred. The grid says which scroll that
        // is ([RestSpan.centreScroll]) — it is NOT the middle of the range, since the range is
        // only symmetric when the two flanks are — and a scroller opens at its start, so the
        // view has to be put there. Once only: after that the view is the user's, and the
        // scrollbar and right stick move it like any other.
        var restSeeded by remember { mutableStateOf(false) }
        LaunchedEffect(bodyScroll.maxValue, restCentreScroll.intValue, editing) {
            if (restSeeded || editing) return@LaunchedEffect
            val max = bodyScroll.maxValue
            if (max <= 0 || max == Int.MAX_VALUE) return@LaunchedEffect
            val centre = restCentreScroll.intValue
            if (centre < 0) return@LaunchedEffect
            restSeeded = true
            // Only a view nobody has touched. The range is not known until the body has been
            // measured, so this necessarily runs a frame or more after the first one — long
            // enough that a quick flick can beat it here, and being re-centred out from under
            // a scroll already in progress is worse than opening off-centre once.
            if (bodyScroll.value == 0) bodyScroll.scrollTo(centre.coerceIn(0, max))
        }

        val slots = buildList<@Composable () -> Unit> {
            add {
                Box(
                    Modifier.fillMaxSize().testTag(ControllerImageTestTag).paint(
                        painter = painter,
                        sizeToIntrinsics = false,
                        contentScale = ContentScale.Fit,
                    ),
                )
            }
            groups.forEach { group ->
                add {
                    StageGroupBacking(
                        group = group,
                        light = backingLight.getValue(group),
                        // The panel is the group's own tap target, exactly as its box is — the
                        // same gate, the same two gestures, the same cursor seating.
                        interactive = reveal.phaseOf(group) != EditPhase.EDIT,
                        onOpenGroup = {
                            runCatching { boxFocus.getValue(group).requestFocus() }
                            onOpenGroup(group)
                        },
                        onOpenMenu = { menuGroup = group },
                    )
                }
            }
            groups.forEach { group ->
                add {
                    StageBasicContent(
                        group = group,
                        viewingSet = viewingSet,
                        viewingLayer = viewingLayer,
                        config = config,
                        callbacks = callbacks,
                        interaction = interactions.getValue(group),
                        focusRequester = boxFocus.getValue(group),
                        // A zoomed-out box is the control; a zoomed-in one is just the ghost
                        // under its own table, and must not answer to taps or hold focus. In
                        // EDIT MODE the tiles inside it are the controls, so the box steps back
                        // the same way — otherwise it would swallow taps meant for a tile and
                        // sit in the d-pad's path between them.
                        // **A box stays a focus target until its tiles are real, and becomes one
                        // again the moment they stop being** (Dylan, 2026-09-24). Switched off
                        // the instant edit mode opened, the boxes left the whole body with
                        // nothing focusable for the length of the morph — 260ms in which the
                        // window falls back to its first focusable and the layouts button, top
                        // left, visibly lights up before the cursor arrives on a tile.
                        //
                        // Said per GROUP, it is also what makes revealing one group at a time
                        // navigable (Dylan, 2026-09-27): a group showing rows rather than tiles
                        // keeps its box, so the d-pad walking out of one group's tiles has
                        // something in the next group to land ON — and landing there is what
                        // opens it. Without that the collapsed groups would be unreachable.
                        interactive = reveal.phaseOf(group) != EditPhase.EDIT,
                        // Straight into the animation, in the focus pass itself: no state to
                        // write, nothing to recompose, and a later travel on the same group
                        // cancels the one before it (see [StageGroupBacking]).
                        onFocusWithin = { within ->
                            // The cursor arriving is what reveals a group, so it is said first
                            // and unconditionally — the reveal policy decides what to do with it.
                            if (within) onGroupFocused(group)
                            backingScope.launch {
                                backingLight.getValue(group).animateTo(
                                    targetValue = if (within) 1f else 0f,
                                    animationSpec = tween(
                                        durationMillis = if (within) {
                                            GroupBackingLightMillis
                                        } else GroupBackingDarkMillis,
                                        easing = LinearEasing,
                                    ),
                                )
                            }
                        },
                        editPhase = reveal.phaseOf(group),
                        editProgress = { reveal.progressOf(group) },
                        seatFocus = focusSeatGroup == group,
                        onFocusSeated = onFocusSeated,
                        onOpenGroup = onOpenGroup,
                        onOpenMenu = { menuGroup = it },
                        menuOpen = menuGroup == group,
                        onDismissMenu = { menuGroup = null },
                        // No host where there are no tiles: a resting group resolves no tiles at
                        // all, which is what keeps its rows exactly the rows they always were.
                        edit = editHost?.takeIf { reveal.phaseOf(group) != EditPhase.REST },
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                // Whether a finger is down anywhere on the stage — observed, never consumed, so
                // every gesture underneath works exactly as before. See [PanReach.pointerDown].
                .pointerInput(panReach) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        panReach.pointerDown = true
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                        } finally {
                            panReach.pointerDown = false
                        }
                    }
                }
                // While a CONTROLLER move is in flight in EDIT MODE, the stage owns the whole
                // keyboard: arrows walk the drop target, B/Escape calls it off, the activate
                // keys confirm. It sits at the stage rather than on a group because a carried
                // tile crosses groups freely — there is no card here to hand the keys to, which
                // is the point of the experiment.
                .onKeyEvent { event ->
                    if (!editing) return@onKeyEvent false
                    moveModeKeyEvent(
                        event = event,
                        moveState = moveState,
                        owns = { true },
                        liftPress = liftPress,
                        onLiftPress = { liftPress = it },
                        gesture = commitGesture,
                        onStep = { dRow, dCol ->
                            stepMoveTargetBy(moveState, stepTarget, focusHandle, haptic, dRow, dCol)
                        },
                        onCommit = commitMove,
                    )
                },
        ) {
        // ── The BODY is the scroller (Dylan, 2026-09-22) ─────────────────────────────────────
        //
        // The group cards are gone, and with them the seven little scrollers that lived inside
        // them: a box no longer clips its own rows, it simply IS as wide as they are, and what
        // overruns the window is scrolled here, once, for the whole grid. The cues are the ones
        // the boxes used to wear (edge fade + chevrons), plus the bar beneath.
        //
        CompositionLocalProvider(LocalMoveOverlay provides true) {
            MinputOverflowScroll(
                state = bodyScroll,
                orientation = Orientation.Horizontal,
                chevronOutset = BodyChevronOutset,
                // On the SCROLLING node, not the container: scroll semantics live where the
                // scroll modifier is, which is what a test drives the body through.
                scrollModifier = Modifier.testTag(ControlsBodyTestTag),
                modifier = Modifier.fillMaxSize(),
                // The morph moves the content without moving the scroller; say so, or the
                // fade and chevron describe a view that isn't on screen.
                contentShift = { contentShift.floatValue },
            ) {
            Layout(contents = slots) { measurables, _ ->
                // Read so a hand-off lays the view out again — see [landedTick].
                landedTick.intValue
                // The VIEWPORT, not the incoming constraints: the stage now sits inside a
                // horizontal scroller (see the body scroll below), which hands its child an
                // unbounded width. Both geometries are framed against the window either way —
                // the camera's, and the grid's centring.
                val viewport = viewportWPx
                val height = viewportHPx
                val count = groups.size

                val controllerM = measurables[0].single()
                val backingM = List(count) { measurables[1 + it].single() }
                val basicM = List(count) { measurables[1 + count + it].single() }

                // ── REST: the 3 × 3 grid, inside the plate's inset ────────────────────────────
                val edgeX = MinputBarEdgePadding.roundToPx()
                val edgeY = MinputPodGap.roundToPx()
                val viewportGridW = (viewport - edgeX * 2).coerceAtLeast(0)
                val gridH = (height - edgeY * 2).coerceAtLeast(0)
                val columnGap = GridColumnGap.roundToPx()
                val rowGap = GridRowGap.roundToPx()
                // The controller column's width from the grid's HEIGHT — the dimension that
                // doesn't change with the screen's aspect or the drawer, so the picture is one
                // size everywhere (see [ControllerColumnHeightRatio]).
                val centreMax = (gridH * ControllerColumnHeightRatio).roundToInt()
                    .coerceAtMost(viewportGridW / 2)

                val restBasic = arrayOfNulls<androidx.compose.ui.layout.Placeable>(count)
                // **Every box now WRAPS its content, with no width cap at all** (Dylan,
                // 2026-09-22). The group cards are gone, so there is no card left to clip
                // against and nothing to scroll inside: the BODY scrolls as one instead, and a
                // box is simply as wide as what it holds. That includes the centre column's
                // utility box, which used to be pinned to the column and scroll — accepting
                // that a wide one now reaches across its neighbours, which Dylan called for
                // explicitly while this is being tried out.
                groups.forEach { group ->
                    val index = groups.indexOf(group)
                    restBasic[index] = basicM[index].measure(Constraints(maxHeight = gridH))
                }
                fun restOf(group: RemapSimpleGroup) = restBasic[groups.indexOf(group)]!!

                    // The image is INSET in its column (Dylan, 2026-09-21): drawn at the column's
                // full width it ran flush into the gutters either side and, being the tallest
                // thing in its band, into the band gaps above and below — reading as artwork
                // jammed against the grid rather than sitting in it. The COLUMN keeps its width
                // (it also sizes the utility box); only the picture inside it shrinks.
                // The BANDS are measured against the picture at its full height-derived size,
                // never the squeezed one: band heights must not change while a travel is running,
                // and the squeeze only ever binds in the shape where the tile rows are the tallest
                // thing in the band anyway.
                val controllerBandH = (centreMax * ControllerImageFraction * aspect).roundToInt()
                val bandHeights = GridBands.mapIndexed { index, band ->
                    val tallest = maxOf(restOf(band.left).height, restOf(band.right).height)
                    if (index == ControllerBand) maxOf(tallest, controllerBandH) else tallest
                }
                // The whole matrix is CENTRED in the plate and its bands are packed: the grid used
                // to hand every spare pixel to the middle band, which pushed the shoulder row to the
                // top of the screen and the stick row to the bottom, far from the controller they
                // belong to.
                val bandTotal = bandHeights.sum() + rowGap * (GridBands.size - 1)
                val bandTop = IntArray(GridBands.size)
                bandTop[0] = edgeY + ((gridH - bandTotal) / 2).coerceAtLeast(0)
                for (band in 1 until GridBands.size) {
                    bandTop[band] = bandTop[band - 1] + bandHeights[band - 1] + rowGap
                }
                // ── The grid's COLUMNS, at both ends of a morph (Dylan, 2026-09-24) ──────────
                //
                // Each side column is as wide as its widest box. Both ends of a travel are worked out
                // once, at its start, and every frame in between is a straight interpolation of the
                // two: deriving each frame from the one before it chased its own tail into a shake.
                val gridTravel = reveal.gridTravel()
                val measuredWidths = IntArray(count) { restBasic[it]!!.width }
                val replanned = editMorph.begin(
                    settled = editSettled,
                    token = reveal.tick,
                    travel = gridTravel,
                    measured = measuredWidths,
                )
                if (replanned) {
                    // The OTHER end, asked of the rows themselves: their two widths are
                    // intrinsics (see AssignmentTable), so the end being travelled to is known
                    // before a single frame of it has been drawn. Per GROUP, because one box can
                    // grow into tiles as another gives them up in the same travel.
                    editMorph.captureOtherEnd(
                        IntArray(count) { index ->
                            val measurable = basicM[index]
                            if (groups[index] in reveal.expanded) {
                                measurable.maxIntrinsicWidth(gridH)
                            } else {
                                measurable.minIntrinsicWidth(gridH)
                            }
                        },
                    )
                }
                /** A column of the grid, as wide as its widest box in [widths]. */
                fun columnIn(widths: IntArray, pick: (GridBand) -> RemapSimpleGroup) =
                    GridBands.maxOf { widths[groups.indexOf(pick(it))] }
                // **The PICTURE yields to the content** (Dylan, 2026-09-27): the controller's column
                // is sized from the grid's height, which says nothing about how much width the flanks
                // need, so it gives up what they need — down to [ControllerColumnSqueezeFloor] of its
                // natural width, and only if that lets the grid be CENTRED in the window (each half
                // inside half the window). Decided per END of a travel and interpolated, never asked
                // afresh mid-travel, where its "only if" could flip.
                val centreFloor = (centreMax * ControllerColumnSqueezeFloor).roundToInt()
                fun restFor(leftW: Int, rightW: Int, centreW: Int) =
                    restSpan(leftW, rightW, centreW, columnGap, viewportGridW)
                fun centreBeside(leftW: Int, rightW: Int): Int {
                    if (restFor(leftW, rightW, centreMax).centred) return centreMax
                    val fitting = (viewportGridW / 2 - columnGap - maxOf(leftW, rightW)) * 2
                    val squeezed = centreMax.coerceAtMost(fitting).coerceAtLeast(centreFloor)
                    return if (restFor(leftW, rightW, squeezed).centred) squeezed else centreMax
                }
                val fromWidths = editMorph.fromWidths ?: measuredWidths
                val toWidths = editMorph.toWidths ?: measuredWidths
                val fromLeft = columnIn(fromWidths) { it.left }
                val fromRight = columnIn(fromWidths) { it.right }
                val toLeft = columnIn(toWidths) { it.left }
                val toRight = columnIn(toWidths) { it.right }
                val fromCentre = centreBeside(fromLeft, fromRight)
                val toCentre = centreBeside(toLeft, toRight)
                val travel = editMorph.at(gridTravel)
                // The columns ON SCREEN this frame: each as wide as its widest box actually is,
                // FLOORED by the interpolation of its two ends — when one box in a column opens as
                // another closes, the two cross at about four fifths of their width, and a column
                // that dipped there would move the whole grid one way and back (Dylan, 2026-09-27).
                val leftColumnW = maxOf(columnIn(measuredWidths) { it.left }, lerpInt(fromLeft, toLeft, travel))
                val rightColumnW = maxOf(columnIn(measuredWidths) { it.right }, lerpInt(fromRight, toRight, travel))
                val centreColumnW = lerpInt(fromCentre, toCentre, travel)
                val contentW = leftColumnW + columnGap + centreColumnW + columnGap + rightColumnW

                // ── THE CAMERA (rebuilt 2026-09-28 — see [StageCamera]) ──────────────────────
                //
                // Whenever a travel begins — the reveal changing (a group opened, walked into,
                // closed), or a group changing shape with the view settled — the camera plans where
                // the content will be drawn at its end, ONCE, and interpolates the controller's
                // on-screen position there along the travel's curve.
                /** The widest box showing TILES in [group]'s column — the room its tiles already
                 *  have beyond its glyph. A text row is not room tiles are shown in. */
                fun tiledRoom(group: RemapSimpleGroup, widths: IntArray): Int {
                    val column = if (group in LeftColumnGroups) LeftColumnGroups else RightColumnGroups
                    return column.filter { it in reveal.expanded }
                        .maxOfOrNull { widths[groups.indexOf(it)] } ?: 0
                }
                /** Where, in the window, the view is right now — the controller's column — and
                 *  where it is headed, which is what a new travel plans from. */
                val drawnCtrl = camera.lastDrawnCtrl
                val aimCtrl = camera.aimCtrl
                if (replanned) {
                    val focus = reveal.focus
                    val framing = panReach.plan(
                        group = focus,
                        width = focus?.let { toWidths[groups.indexOf(it)] } ?: 0,
                        target = panTarget,
                    )
                    camera.plan(
                        byReframe = false,
                        destination = cameraDestination(
                            focus = focus,
                            reach = panReach.reach,
                            framing = framing,
                            tiledRoom = focus?.let { tiledRoom(it, toWidths) } ?: 0,
                            leftW = toLeft,
                            rightW = toRight,
                            centreW = toCentre,
                            columnGap = columnGap,
                            viewportGridW = viewportGridW,
                            edgeX = edgeX,
                            viewCtrl = aimCtrl,
                        ),
                        drawnCtrl = drawnCtrl,
                    )
                }
                // A group changed shape with the view settled — a tile cleared, pasted, or landed
                // in it: frame it whole again, as though it had just been opened (see [PanReach]).
                if (editSettled && editing && !camera.travellingByMorph) {
                    reframe.detect(
                        widths = measuredWidths,
                        busy = camera.travellingByReframe,
                        groupAt = { groups[it] },
                    )?.let { group ->
                        panReach.reframe(group, measuredWidths[groups.indexOf(group)], panTarget)
                        camera.plan(
                            byReframe = true,
                            destination = cameraDestination(
                                focus = group,
                                reach = panReach.reach,
                                framing = true,
                                tiledRoom = tiledRoom(group, measuredWidths),
                                leftW = leftColumnW,
                                rightW = rightColumnW,
                                centreW = centreColumnW,
                                columnGap = columnGap,
                                viewportGridW = viewportGridW,
                                edgeX = edgeX,
                                viewCtrl = aimCtrl,
                            ),
                            drawnCtrl = drawnCtrl,
                        )
                    }
                } else {
                    reframe.detect(widths = null, busy = false, groupAt = { groups[it] })
                }
                // A screen that comes back ALREADY in edit mode (the command picker's round trip
                // rebuilds it) has had no travel to plan its camera: it simply holds the view the
                // resting layout would give it until something moves it.
                if (editing && editSettled) {
                    camera.adoptIfResting(
                        restFor(leftColumnW, rightColumnW, centreColumnW).lead.coerceAtMost(
                            (viewportGridW - contentW).coerceAtLeast(0),
                        ),
                    )
                }
                val rest = restFor(leftColumnW, rightColumnW, centreColumnW)
                val placement = camera.place(
                    progress = if (camera.travellingByReframe) {
                        reframe.progressOf(reframeTravel.value)
                    } else travel,
                    rest = rest,
                    contentW = contentW,
                    leftW = leftColumnW,
                    columnGap = columnGap,
                    viewportGridW = viewportGridW,
                    edgeX = edgeX,
                    scroll = bodyScroll.value,
                )
                // The resting view's centring scroll, for the one-time seeding below.
                if (!editing) restCentreScroll.intValue = rest.centreScroll
                // What the scroll CUES are told: the displacement within the real range only (see
                // [contentShift]).
                contentShift.floatValue = placement.cueShift.toFloat()
                shiftProbe?.invoke(contentShift.floatValue)
                val restTotalW = placement.gridW + edgeX * 2
                val startX = placement.startX
                val centreX = startX + leftColumnW + columnGap
                val rightX = centreX + centreColumnW + columnGap
                camera.lastDrawnCtrl = (centreX - placement.effectiveScroll).toFloat()

                // Anchored toward the centre cell: the top band sits on the FLOOR of its row, the
                // bottom band on the CEILING of its own, and the middle band centres on the
                // controller. With the flanks already hugging their inner edges, that makes each
                // corner box point at the controller.
                fun restTop(band: Int, itemHeight: Int): Int = when {
                    band < ControllerBand -> bandTop[band] + bandHeights[band] - itemHeight
                    band > ControllerBand -> bandTop[band]
                    else -> bandTop[band] + (bandHeights[band] - itemHeight) / 2
                }
                val restRects = HashMap<RemapSimpleGroup, StageRect>(count)
                GridBands.forEachIndexed { band, row ->
                    val left = restOf(row.left)
                    restRects[row.left] = StageRect(
                        left = startX + leftColumnW - left.width,
                        top = restTop(band, left.height),
                        width = left.width,
                        height = left.height,
                    )
                    val right = restOf(row.right)
                    restRects[row.right] = StageRect(rightX, restTop(band, right.height), right.width, right.height)
                }
                val controllerRestW = (centreColumnW * ControllerImageFraction).roundToInt()
                val controllerRestH = (controllerRestW * aspect).roundToInt()
                val controllerRest = StageRect(
                    left = centreX + (centreColumnW - controllerRestW) / 2,
                    top = restTop(ControllerBand, controllerRestH),
                    width = controllerRestW,
                    height = controllerRestH,
                )
                // ── The group BACKINGS: a rectangle per box, running off its own side of
                // the screen (Dylan, 2026-09-26) ──────────────────────────────────────────────
                //
                // The single plate under the whole grid is gone. Each group's box now sits on
                // its own sharp-cornered rectangle that starts at the box and runs OUTWARD,
                // past the window's edge — left-column groups off the left edge, right-column
                // groups off the right — so the view reads as content mounted on the panel's
                // flanks rather than as boxes floating on one tray.
                //
                // The overhang is a whole viewport wide, which is the cheapest thing that
                // cannot fall short: a box visible at all has its inner edge inside the
                // viewport, so a viewport's worth of rectangle from there always reaches the
                // screen edge, at any scroll position and any box width. The stage clips to
                // its bounds, so the surplus costs a fill and nothing else.
                val backingOverhang = viewport.coerceAtLeast(0)
                // **The rectangle is exactly the line's panel** (Dylan, 2026-09-26, twice): it
                // ends where the line ends, both along the edge and across it, so the line reads
                // as the panel's own border rather than as a second shape beside it. Both numbers
                // come off the line's geometry ([groupEdgeLine]) — never guessed here:
                //
                //  - ALONG the edge, the line's visible extent is [GroupOutlineEndInset] from each
                //    end of the box (its round caps reach half a stroke past the trim).
                //  - ACROSS it, the line's inner face sits [GroupOutlineInset] inside the box's
                //    inner edge, and the rectangle stops there — it used to run the whole way to
                //    the box's edge, past the line.
                val backingTrim = GroupOutlineEndInset.roundToPx()
                val backingInnerInset = GroupOutlineInset.roundToPx()
                val backingPlaceables = groups.map { group ->
                    val rect = restRects.getValue(group)
                    backingM[groups.indexOf(group)].measure(
                        Constraints.fixed(
                            (rect.width + backingOverhang - backingInnerInset).coerceAtLeast(0),
                            (rect.height - backingTrim * 2).coerceAtLeast(0),
                        ),
                    )
                }
                val controllerPlaceable = controllerM.measure(
                    Constraints.fixed(
                        controllerRest.width.coerceAtLeast(1),
                        controllerRest.height.coerceAtLeast(1),
                    ),
                )

                // The stage is as wide as the grid, which may overrun the window — that surplus is
                // what the body scroller scrolls.
                layout(restTotalW, height) {
                    // Backings first: everything else in the view sits ON them.
                    groups.forEach { group ->
                        val rect = restRects.getValue(group)
                        val index = groups.indexOf(group)
                        backingPlaceables[index].place(
                            // Left column: the overhang runs off the left edge and the rectangle
                            // stops short of the box's RIGHT edge, where its line is. Right
                            // column: the mirror image.
                            x = if (group in LeftColumnGroups) {
                                rect.left - backingOverhang
                            } else {
                                rect.left + backingInnerInset
                            },
                            y = rect.top + backingTrim,
                        )
                    }
                    controllerPlaceable.place(
                        x = controllerRest.centerX - controllerPlaceable.width / 2,
                        y = controllerRest.centerY - controllerPlaceable.height / 2,
                    )

                    groups.forEachIndexed { index, group ->
                        val rect = restRects.getValue(group)
                        restBasic[index]!!.place(rect.left, rect.top)
                    }
                }
            }
            }
        }

        // HOW MUCH more and WHERE, which a fade at the rim can't say.
        MinputScrollbar(
            state = bodyScroll,
            orientation = Orientation.Horizontal,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = MinputBarEdgePadding),
            contentShift = { contentShift.floatValue },
        )

        // ABOVE everything: the tile being carried, and the one it would displace. Outside every
        // card on purpose — a card clips, so a tile lifted from a slot at its rim was sliced by
        // the viewport's edge, and one carried toward another group stopped dead at its own
        // card's border even though the move itself crossed (Dylan, 2026-09-21).
        MoveOverlay(
            moveState = moveState,
            stageOrigin = stageOrigin,
            viewingSet = viewingSet,
            viewingLayer = viewingLayer,
            config = config,
            look = rowTileLook(),
        )

        // Edit mode's tiles have no card to host the dialogs their menus summon, so the stage
        // does. Both outlive the menu, and the label editor outlives a tile that has just moved.
        CommandTileDialogs(
            labelTarget = labelTarget,
            typeTarget = typeTarget,
            config = config,
            callbacks = callbacks,
            onCloseLabel = { labelTarget = null },
            onCloseType = { typeTarget = null },
        )
        }
    }
}

/**
 * **The rectangle a group's box sits on** (Dylan, 2026-09-26) — sharp-cornered, on the surface
 * plane one step above the screen, running off its own side of the display (the stage's layout
 * places it; see the backing block there).
 *
 * It replaced the single [com.mappo.ui.minput.MinputPod] plate that used to sit under the whole
 * grid: one tray under everything said nothing about which content belonged where, while a
 * rectangle per group that runs off the screen edge reads as the panel's own flanks — the shape
 * a handheld's face actually has.
 *
 * **It is also the group's FOCUS affordance.** Holding the controller cursor steps the fill one
 * plane up (`surfaceContainerLow` → `surfaceContainer`), which replaces the lift the box used to
 * take. A lit surface says "the cursor is here" at any distance, where a two-pixel rise had to be
 * looked for; the group's inner edge line still goes accent alongside it, which Dylan kept
 * explicitly. The fill animates, so arriving and leaving read as movement rather than as a jump.
 *
 * **In EDIT MODE it follows the cursor's TILE** (Dylan, 2026-09-26) — the group holding the
 * focused tile lights exactly as a focused box does in view mode, so the view keeps saying which
 * part of the controller you are working on. That is why the signal is "focus is somewhere in this
 * group" (`onFocusChanged { it.hasFocus }` on the group's content, which the tiles live inside)
 * rather than the box's own focus state: one signal covers both modes, and the box stops being a
 * focus target the moment its tiles become one.
 *
 * **It has to be as quick off the mark as the state layer it replaced** (Dylan, 2026-09-26 — the
 * first cut visibly trailed the cursor by a frame or two). Three things buy that back, and all
 * three matter:
 *
 *  - the animation is started INSIDE the focus callback rather than by a state write that has to
 *    be recomposed first — the same way the ripple's own node starts its state layer off its
 *    interaction collector;
 *  - its value is read in the DRAW phase, so the frames in between repaint a rectangle and
 *    recompose nothing (`animateColorAsState` + `background` recomposed this composable on every
 *    animation frame);
 *  - lighting UP is quick and linear ([GroupBackingLightMillis], the state-layer scale) while
 *    going dark is slower ([GroupBackingDarkMillis]). The old 140ms ease-both-ways spent its
 *    first frames barely moving, which on so subtle a colour step is indistinguishable from lag.
 *
 * **The whole rectangle is the group's tap target** (Dylan, 2026-09-27): tapping anywhere on the
 * panel opens that group in edit mode, holding it opens the advanced view — the box's own two
 * gestures, on the panel's far larger area, because a box only as wide as its own text left most
 * of a visibly clickable surface dead. It is deliberately NOT a focus target: the box stays the
 * group's single stop for the d-pad, and the panel seats the cursor there before handing over,
 * exactly as a tap on the box does.
 *
 * Alpha is the complement of the card chrome's: at rest this IS the group's surface, and by the
 * time the zoom has landed the card has taken the job over. Read in the DRAW phase, so the
 * travel repaints without recomposing.
 */
@Composable
private fun StageGroupBacking(
    group: RemapSimpleGroup,
    /** 0 = resting plane, 1 = the cursor is in this group. Driven straight from the focus
     *  callback (see the stage), and read here in the DRAW phase. */
    light: Animatable<Float, AnimationVector1D>,
    interactive: Boolean,
    onOpenGroup: () -> Unit,
    /** Held — the group's own action menu (see [GroupActionMenu]). */
    onOpenMenu: () -> Unit,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val lit = MaterialTheme.colorScheme.surfaceContainer
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .testTag(groupBackingTestTag(group))
            // Everything here is a DRAW-phase read — the plane, and the zoom's fade — so a
            // group lighting up repaints one rectangle and recomposes nothing.
            .drawBehind { drawRect(color = lerp(base, lit, light.value)) }
            .then(
                if (interactive) {
                    Modifier
                        // NOT a focus target: the BOX is the group's one stop for the d-pad, and a
                        // `clickable` brings its own focusTarget along unless told otherwise. The
                        // panel is a touch affordance only — which is also why the stock
                        // indication is safe here, with no focus state for it to draw.
                        .focusProperties { canFocus = false }
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = minputIndication(),
                            onLongClick = onOpenMenu,
                            onClick = onOpenGroup,
                        )
                } else Modifier,
            ),
    )
}

/** The rectangle under one group's box — the handle a test measures its reach by. */
internal fun groupBackingTestTag(group: RemapSimpleGroup): String = "group-backing:${group.name}"

/**
 * The single vector line that marks a group at rest (Dylan, 2026-09-22, corrected 2026-09-23).
 *
 * **One line, on the edge that FACES THE CONTROLLER** — not a border around the group. A full
 * ring was the first cut and read as the card it had just replaced, drawn in outline; the point
 * is a mark that says "this group belongs to that part of the controller", which is why it is a
 * drawn stroke rather than a border modifier: the connector lines that will eventually run from
 * here to the buttons themselves start at this edge.
 *
 * **It is drawn with the group's CONTENT**, which is also where the box's own focus tracking
 * lived. That mattered while the box LIFTED on focus: the chrome (never focusable) and the
 * content (focusable) moved by different amounts, and a hairline slid out from under the rows.
 * The lift retired 2026-09-26 in favour of the backing's fill ([StageGroupBacking]) — but keep
 * the line with the content anyway, so any motion the group ever takes moves both as one.
 */
private fun Modifier.groupEdgeLine(
    group: RemapSimpleGroup,
    color: Color,
    alpha: () -> Float,
): Modifier = drawBehind {
    val strength = alpha()
    if (strength <= 0f) return@drawBehind
    val stroke = GroupOutlineWidth.toPx()
    val inset = GroupOutlineInset.toPx() + stroke / 2f
    // How far the line stops short of each end, so it reads as a deliberate mark rather than
    // a wall boxing the group in.
    val trim = GroupOutlineEndTrim.toPx()
    val (from, to) = when (group.controllerEdge()) {
        GroupEdge.TOP -> Offset(trim, inset) to Offset(size.width - trim, inset)
        GroupEdge.START -> Offset(inset, trim) to Offset(inset, size.height - trim)
        GroupEdge.END ->
            Offset(size.width - inset, trim) to Offset(size.width - inset, size.height - trim)
    }
    if (to.x < from.x || to.y < from.y) return@drawBehind
    drawLine(
        color = color.copy(alpha = strength),
        start = from,
        end = to,
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

/**
 * Which edge of a group faces the controller image — where its [groupEdgeLine] is drawn. Every
 * group faces INWARD, because every group is on a flank: the left column's mark sits on its
 * right edge and the right column's on its left.
 *
 * [GroupEdge.TOP] is what the centre-column utility group used before it split per side
 * (2026-09-26). Kept for the next group that sits under the controller rather than beside it.
 */
private enum class GroupEdge { START, END, TOP }

private fun RemapSimpleGroup.controllerEdge(): GroupEdge =
    if (this in LeftColumnGroups) GroupEdge.END else GroupEdge.START

/** One group's content: its glyph + assignment rows (tiles in edit mode), the tap target that
 *  opens the group, and the group's own action menu. */
@Composable
private fun StageBasicContent(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    interaction: MutableInteractionSource,
    /** This box's focus handle — held by the stage, since its backing rectangle seats it too. */
    focusRequester: FocusRequester,
    interactive: Boolean,
    /** Focus entered or left this group — its box, or (in edit mode) any of its tiles. */
    onFocusWithin: (Boolean) -> Unit,
    seatFocus: Boolean,
    onFocusSeated: () -> Unit,
    onOpenGroup: (RemapSimpleGroup) -> Unit,
    /** The box was HELD — what used to open the advanced view now opens the group's own menu. */
    onOpenMenu: (RemapSimpleGroup) -> Unit,
    /** Non-null once edit mode owns this box's rows, mid-morph included. */
    edit: RowEditHost?,
    editPhase: EditPhase,
    editProgress: () -> Float,
    /** Is this group's own action menu up? One at a time, so the stage holds which. */
    menuOpen: Boolean = false,
    onDismissMenu: () -> Unit = {},
) {
    if (seatFocus && interactive) {
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
            onFocusSeated()
        }
    }
    // The group's own line goes ACCENT where it holds the controller cursor. With no card there
    // is no plate left to carry a focus indication, and a view navigated by d-pad has to say
    // where the cursor is. In EDIT MODE the group is not the thing being navigated — its tiles
    // are, each with its own focus ring — so the line stays quiet and the cursor is read off
    // whichever tile is lit.
    val focused by interaction.collectIsFocusedAsState()
    val lit = focused && edit == null
    val lineColor = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val lineAlpha = if (lit) GroupOutlineFocusAlpha else GroupOutlineAlpha
    Box(
        modifier = Modifier
            // **No lift here** (Dylan, 2026-09-26). The box used to rise on focus/hover
            // (`minputInteractiveMotion`); its backing rectangle now takes a plane instead — see
            // [StageGroupBacking] — and two affordances for one state is one too many. It also
            // retires an old hazard: the lift tracked focus on THIS node only, so the group's
            // hairline edge line (drawn here) and the chrome beside it moved by different amounts
            // the moment a group took focus.
            // Focus ANYWHERE in this group — the box itself at rest, one of its tiles in edit
            // mode (they compose inside this node) — which is what the group's backing rectangle
            // lights from. Kept at the top of the chain so it covers the whole subtree.
            .onFocusChanged { onFocusWithin(it.hasFocus) }
            // Clipped so the tap ripple takes the card's shape: the fill and the bevel belong to
            // the chrome sibling, but the indication is drawn here.
            .clip(RoundedCornerShape(GroupCorner))
            // **Press and hover only** (Dylan, 2026-09-26). The stock ripple also paints a FOCUS
            // state layer, and with the backing rectangle now lighting on focus that read as a
            // second, differently-shaped highlight sitting on top of the first. The group's focus
            // is the backing's plane and its accent line; nothing here draws it.
            .minputPressIndication(interaction)
            .groupEdgeLine(group, lineColor) { lineAlpha }
            .then(
                if (interactive) {
                    Modifier
                        .focusRequester(focusRequester)
                        // HOLD opens the advanced view, TAP edits in place (Dylan, 2026-09-22 —
                        // the experiment). A hold going one level deeper is the gesture this
                        // view already teaches on its tiles, where holding lifts one.
                        .combinedClickable(
                            interactionSource = interaction,
                            // Drawn by minputPressIndication above, so that there is one state
                            // layer and it is the one without a focus treatment.
                            indication = null,
                            onLongClick = { onOpenMenu(group) },
                            onClick = {
                                // Take the cursor before handing it on. A tap flips the window
                                // into touch mode, which clears Compose focus outright, so
                                // without this there is nothing focused to hand over FROM and
                                // the morph runs with the cursor parked outside the body.
                                runCatching { focusRequester.requestFocus() }
                                onOpenGroup(group)
                            },
                        )
                        // The gamepad's half of the same gesture. `clickable` has no notion of a
                        // held KEY — it fires on release — so the hold is timed here, and both
                        // ends of the press are consumed so the release can't also count as a
                        // tap. Sits AFTER the clickable in the chain, which makes it the inner
                        // node and so the first to see a key.
                        .holdToOpen(
                            onHold = { onOpenMenu(group) },
                            onTap = { onOpenGroup(group) },
                        )
                } else Modifier,
            )
            .testTag("simple-group:${group.name}")
            // **The panel's air above and below its rows is the air BETWEEN them** (Dylan,
            // 2026-09-27). The box wraps its rows exactly, and its rectangle is inset from the box
            // by the line's own end trim ([GroupOutlineEndInset], see [StageGroupBacking]) — so a
            // hand-picked padding left the outermost tiles all but touching the panel's ends while
            // their neighbours inside had a clear gap. Carrying the trim in the padding is what
            // makes the VISIBLE air equal [rowTileGap] at any density, rather than at the one this
            // number was chosen on.
            .padding(horizontal = GroupBoxPaddingX, vertical = rowTileGap() + GroupOutlineEndInset),
        // Every box wraps its own rows now, so centring costs nothing and covers the case where
        // one is ever given more room than it asked for.
        contentAlignment = Alignment.Center,
    ) {
        GroupRows(group, viewingSet, viewingLayer, config, edit = edit, phase = editPhase, progress = editProgress)
        GroupActionMenu(
            group = group,
            viewingSet = viewingSet,
            viewingLayer = viewingLayer,
            callbacks = callbacks,
            expanded = menuOpen,
            onDismissRequest = onDismissMenu,
        )
    }
}

/**
 * **A group's own action menu** (Dylan, 2026-09-27) — summoned by HOLDING the group, which is the
 * gesture that used to open the advanced view.
 *
 * With that view retired, this is where the two things it carried in its card header live: the
 * group's mode settings, and resetting the group to the layout's own defaults. Deliberately the
 * same [MinputActionMenu] a tile wears, in the same place beside its anchor and with the same
 * caret — a group and a command are two things you hold to get options on, so they answer alike.
 *
 * It names the GROUP rather than the mode ("Button Pad settings", not "Configure Button Pad"),
 * because the thing being held is the group.
 *
 * Settings resolve against the group's PRIMARY source (its first row's) — the same rule the card
 * header used, which leaves a multi-source group's secondary modes (a shoulder's bumper beside its
 * trigger) reachable only through a command's own editor for now.
 */
@Composable
private fun BoxScope.GroupActionMenu(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    callbacks: RemapGroupEditorCallbacks,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val primarySource = group.rows.first().source
    val primaryGroup = viewingLayer?.presetFor(primarySource)?.group?.group
        ?: viewingSet?.presetFor(primarySource)?.group?.group
    // A LAYER's bindings are read-only here; edits route through the command's own editor.
    val editable = viewingLayer == null
    val name = group.headerLabel()
    MinputActionMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        actions = if (!expanded) {
            emptyList()
        } else {
            listOf(
                MinputAction(
                    label = "$name settings",
                    icon = Icons.Filled.Settings,
                    enabled = primaryGroup != null &&
                        SourceModeSettingsSchema.hasSettings(primarySource, primaryGroup.mode),
                    onClick = {
                        primaryGroup?.let { callbacks.onOpenModeSettings(it.id, primarySource) }
                    },
                ),
                MinputAction(
                    label = "Reset $name to default",
                    icon = Icons.Filled.RestartAlt,
                    enabled = editable && primaryGroup != null,
                    destructive = true,
                    onClick = { primaryGroup?.let { callbacks.onResetGroup(it.id) } },
                ),
            )
        },
        placement = MinputMenuPlacement.End,
        caret = true,
    )
}

/**
 * Hold the activate button to go one level deeper; release it without holding to act normally.
 *
 * The gamepad counterpart to `combinedClickable`'s `onLongClick`, which only knows about
 * fingers. Modelled on the tile's own hold-to-lift, down to arming only on the INITIAL key-down
 * (hardware auto-repeat re-delivers it) and to disarming on any other key — a half-committed
 * hold is the worst state this control can be in, so the gesture survives holding still and
 * nothing else.
 */
private fun Modifier.holdToOpen(onHold: () -> Unit, onTap: () -> Unit): Modifier = composed {
    val viewConfiguration = LocalViewConfiguration.current
    val haptic = LocalHapticFeedback.current
    var downAt by remember { mutableLongStateOf(0L) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(downAt) {
        if (downAt == 0L) return@LaunchedEffect
        delay(viewConfiguration.longPressTimeoutMillis)
        if (downAt != 0L) {
            held = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onHold()
        }
    }
    onKeyEvent { event ->
        if (event.key !in TileActivateKeys) {
            downAt = 0L
            held = false
            return@onKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount == 0) {
                    downAt = System.currentTimeMillis()
                    held = false
                }
                true
            }
            KeyEventType.KeyUp -> {
                downAt = 0L
                // A hold has already acted; the release that ends it is not also a tap.
                if (!held) onTap()
                held = false
                true
            }
            else -> false
        }
    }
}

/**
 * How hard a carried tile at [x] pulls the body along, in [-1, 1] — negative toward the start.
 *
 * Zero outside [band]. Inside it, the pull grows from nothing at the band's inner lip to FULL by
 * the time the finger is within [plateau] of the edge, and stays there — including past the edge
 * entirely, which is where a finger that has run out of glass ends up. See [CarryEdgeBand] for
 * why the plateau exists.
 */
internal fun carryEdgePush(x: Float, viewportW: Float, band: Float, plateau: Float): Float {
    val fromEdge = minOf(x, viewportW - x)
    if (fromEdge >= band) return 0f
    // A plateau as wide as the band would leave no ramp at all to divide by.
    val reach = (band - plateau).coerceAtLeast(1f)
    val depth = ((band - fromEdge) / reach).coerceIn(0f, 1f)
    return if (x < viewportW - x) -depth else depth
}

/**
 * The carry edge-scroll's shape (see [RemapStage]).
 *
 * [CarryEdgeBand] is how near the window's edge a carried tile starts pulling the body along;
 * [CarryEdgePlateau] is the inner strip of that band where the pull is already at FULL speed,
 * so topping out doesn't mean putting the finger on the glass at the very rim — which, with a
 * tile held under it, is somewhere between awkward and impossible (Dylan, 2026-09-25: "it feels
 * a little difficult to achieve full drag scroll speed"). [CarryEdgeSpeed] is that full speed,
 * per second.
 */
private val CarryEdgeBand = 120.dp
private val CarryEdgePlateau = 44.dp
private val CarryEdgeSpeed = 400.dp

/**
 * **Test hook: every value the scroll cues are handed**, as the layout publishes it. The cues
 * themselves draw and expose no semantics, so this is how a test asserts that they are never told
 * about a scroll that does not exist. Null — and free — everywhere else.
 */
internal val LocalBodyShiftProbe =
    androidx.compose.runtime.staticCompositionLocalOf<((Float) -> Unit)?> { null }

/** The body's one scroller — the handle a test drives it by. */
internal const val ControlsBodyTestTag = "controls-body"

/** The picture of the device, which the resting view is arranged around — the handle a test
 *  asks "is the controller centred" by. */
internal const val ControllerImageTestTag = "controller-image"

/**
 * **The VIEW MODE's horizontal layout: the controller dead centre** (Dylan, 2026-09-25, and again
 * 2026-09-28: "In the physical controls' view mode, the controller genuinely should be centered by
 * default even if the labels of input rows begin to exit the screen width").
 *
 * Whichever side has less than half a window between the controller's middle and its outer edge is
 * padded up to half a window, so the controller can sit in the middle whatever the two flanks
 * measure. Where that makes the grid wider than the window it scrolls like any other window, and the
 * resting view opens at [centreScroll].
 *
 * Edit mode never uses this: its layout is built around its camera instead (see [StageCamera]).
 */
private class RestSpan(
    /** The room before the content, inside the grid. */
    val lead: Int,
    /** The grid's width, the edge margins not included — at least the window's. */
    val gridW: Int,
    /** The scroll value at which the controller sits dead centre in the window. */
    val centreScroll: Int,
    /** Whether the controller is centred with NO scroll range — each half of the content inside
     *  half the window. What the controller column's squeeze aims for. */
    val centred: Boolean,
)

private fun restSpan(
    leftW: Int,
    rightW: Int,
    centreW: Int,
    columnGap: Int,
    viewportGridW: Int,
): RestSpan {
    val contentW = leftW + columnGap + centreW + columnGap + rightW
    // The controller's middle, as a distance from the content's leading edge.
    val toCentre = leftW + columnGap + centreW / 2
    val half = viewportGridW / 2
    val lead = (half - toCentre).coerceAtLeast(0)
    val trail = (half - (contentW - toCentre)).coerceAtLeast(0)
    // The floor covers rounding when both pads apply: two halves of an odd window are a pixel short.
    val gridW = maxOf(contentW + lead + trail, viewportGridW)
    return RestSpan(
        lead = lead,
        gridW = gridW,
        centreScroll = (lead + toCentre - half).coerceIn(0, gridW - viewportGridW),
        centred = lead + trail + contentW <= viewportGridW,
    )
}

/**
 * **Where edit mode's camera goes at the end of a travel**, as the controller column's x in the
 * WINDOW, plus how the layout is to be built around it ([CameraDestination]).
 *
 * Everything is worked out in GRID coordinates — x from the window's left edge less the grid's own
 * margin, the content's leading edge at `x` — against the columns the travel ENDS at.
 *
 *  - **LEAVING edit mode** ([focus] null): back to the view mode's own layout ([restSpan]) at its
 *    default, the controller centred (Dylan, 2026-09-28: in view mode "the controller genuinely
 *    should be centered by default").
 *  - **HOLDING** (not [framing] — a hop up or down the same column): the view does not move. The
 *    controller stays exactly where it is drawn, whatever the column does around it: that is "the
 *    pan location will also be maintained if the user navigates up and down" (Dylan, 2026-09-28).
 *  - **FRAMING** (an open, a cross-column hop, a committed tile, or any move after the user has
 *    scrolled — see [PanReach]): the region [reach] wide from the group's glyph outward is brought
 *    into view by the shortest distance. Where the whole grid FITS the window there is nothing to
 *    scroll, so the view is NUDGED toward the group from its centred placement by however much of
 *    the reach its tiles do not already cover ([tiledRoom]) — the pan Dylan called "solid" for a
 *    default layout that fits (2026-09-28).
 *
 * Two rules over all three. **Content that fits the window stays inside it** — nothing is ever
 * pushed off screen to make room, so a grid with nothing to scroll never gains any. And where the
 * content overflows, **never empty on the far side while the content is cut off on the near one**:
 * room past the content only ever appears on the FOCUSED group's side, where the reach asked for it.
 */
private fun cameraDestination(
    focus: RemapSimpleGroup?,
    reach: Int,
    framing: Boolean,
    tiledRoom: Int,
    leftW: Int,
    rightW: Int,
    centreW: Int,
    columnGap: Int,
    viewportGridW: Int,
    edgeX: Int,
    /** The controller column's x in the window where the view is (or is headed); null before
     *  anything has been drawn. */
    viewCtrl: Float?,
): CameraDestination {
    val contentW = leftW + columnGap + centreW + columnGap + rightW
    val vg = viewportGridW
    // Where the content's leading edge would be with the controller held exactly where it is drawn.
    val restLead = restSpan(leftW, rightW, centreW, columnGap, vg).lead
    val here = viewCtrl?.let { it - edgeX - leftW - columnGap }?.roundToInt() ?: restLead
    if (focus == null) {
        val rest = restSpan(leftW, rightW, centreW, columnGap, vg)
        val scroll = rest.centreScroll
        return CameraDestination(
            ctrl = (edgeX + rest.lead - scroll + leftW + columnGap).toFloat(),
            lead = rest.lead,
            gridW = rest.gridW,
            scroll = scroll,
            resting = true,
        )
    }
    val onLeft = focus in LeftColumnGroups
    var x = here
    if (framing) {
        if (contentW <= vg) {
            // Centred as the window allows, then toward the group by the reach its tiles lack.
            val centred = (vg / 2 - (leftW + columnGap + centreW / 2)).coerceIn(0, vg - contentW)
            val nudge = (reach - tiledRoom).coerceAtLeast(0)
            x = (centred + if (onLeft) nudge else -nudge).coerceIn(0, vg - contentW)
        }
        // The glyph is the group's inner edge — the column's — and the region runs outward from it.
        if (onLeft) {
            when {
                reach >= vg -> x = vg - leftW
                x + leftW - reach < 0 -> x = reach - leftW
                x + leftW > vg -> x = vg - leftW
            }
        } else {
            val glyph = contentW - rightW
            when {
                reach >= vg -> x = -glyph
                x + glyph + reach > vg -> x = vg - reach - glyph
                x + glyph < 0 -> x = -glyph
            }
        }
    }
    x = if (contentW <= vg && framing) {
        // **Framing never pushes content that fits off the window** (Dylan, 2026-09-28: focusing a
        // group of single commands was "somehow adding additional content to the other side of the
        // screen"). Reaching the target past a narrow group would push the far column off screen and
        // give it scroll it never had; the reach gives way to the window instead. A HOLD is exempt:
        // it moves nothing, so whatever was off screen before stays exactly as it was.
        x.coerceIn(0, vg - contentW)
    } else if (contentW <= vg) {
        // Holding, content that fits is never left cut off on the focused group's own side.
        if (onLeft) x.coerceAtLeast(0) else x.coerceAtMost(vg - contentW)
    } else if (onLeft) {
        // Never empty past the far column while the content is cut off on the near side.
        if (x + contentW < vg) maxOf(x, vg - contentW) else x
    } else {
        if (x > 0) minOf(x, 0) else x
    }
    // The layout is built around the camera: room before the content where the view shows some
    // there, room after it where the view shows some THERE (the reach past a right-column group),
    // and otherwise exactly the content.
    val lead = x.coerceAtLeast(0)
    val scroll = (-x).coerceAtLeast(0)
    return CameraDestination(
        ctrl = (edgeX + x + leftW + columnGap).toFloat(),
        lead = lead,
        gridW = maxOf(lead + contentW, scroll + vg),
        scroll = scroll,
        resting = false,
    )
}

/** Where a travel leaves the camera: see [cameraDestination]. */
private class CameraDestination(
    /** The controller column's x in the WINDOW. */
    val ctrl: Float,
    /** The room before the content inside the grid, and the grid's width (margins excluded). */
    val lead: Int,
    val gridW: Int,
    /** What the scroller is left at. Always inside `0..gridW - window`. */
    val scroll: Int,
    /** The view mode's layout rather than edit mode's. */
    val resting: Boolean,
)

/** What the layout draws this pass (see [StageCamera.place]). */
private class CameraPlacement(
    /** The content's leading edge, in the LAYOUT's own coordinates. */
    val startX: Int,
    /** The grid's width, margins excluded — what the scroller scrolls over. */
    val gridW: Int,
    /** The scroll value as the scroller will clamp it against [gridW]. */
    val effectiveScroll: Int,
    /** What the scroll cues are handed: the part of any drawn displacement inside the real range. */
    val cueShift: Int,
)

/**
 * **The stage's camera** (rebuilt from scratch 2026-09-28, after a run of fixes had grown it into
 * five interlocking mechanisms — centring pads, reserved room, a drawn bias, a re-plan for shape
 * changes and a scroll plan — each of which broke the others).
 *
 * **Edit mode's layout is built AROUND the camera.** The camera is one number, where the content is
 * drawn; the layout then puts before the content exactly the empty room that position leaves inside
 * the window ([lead]) and makes the grid just wide enough for the rest. So the scroll range is only
 * ever content that is really off screen — plus, at the focused group's own end, the room its reach
 * asked for — and never anything past the far column (Dylan: empty scroll space "serves no purpose
 * anyway, and should be eliminated entirely"). The view mode keeps its own centred layout
 * ([restSpan]).
 *
 * **A travel interpolates where the controller is DRAWN**, from where it last was to the planned
 * destination, along the travel's own eased curve ([place]) — never a scroll value, whose range
 * changes shape under it mid-travel (the "starting lag" and cut-short ease-out, Dylan 2026-09-28).
 * Drawn that way it does not matter what the scroller holds meanwhile, so the hand-off at the end
 * ([land]/[settle]) can move the scroller to its new value without anything on screen moving.
 *
 * Planned only on the frame a travel begins, never from the live focus, so the frame between a click
 * and the travel it starts cannot move anything (Dylan, 2026-09-28: the view "flashing inward towards
 * the center column").
 */
private class StageCamera {
    /** Settled in the view mode's centred layout, rather than edit mode's. */
    private var resting = true
    /** Edit mode, settled: the room before the content inside the grid, and after it. */
    private var lead = 0
    private var trail = 0
    private var destination: CameraDestination? = null
    private var ctrlFrom = 0f
    private var leadFrom = 0
    private var gridWFrom = 0
    private var byReframe = false
    /** The last pass's grid width, where a travel's own starts from. */
    private var lastGridW = 0

    /** The controller column's x in the window, as the last pass drew it. */
    var lastDrawnCtrl: Float? = null

    /**
     * **Where the view IS, for planning** — where a travel under way is taking it, or else where it
     * was drawn. A new travel is planned from this, while it starts from what is on screen: a HOLD
     * that interrupts a re-frame must keep the view the re-frame is heading for, not the one it
     * happened to be passing through (an add seats the cursor on its new tile a frame after the
     * re-frame toward it starts, and that seat is a hop of its own).
     */
    val aimCtrl: Float? get() = destination?.ctrl ?: lastDrawnCtrl

    val travellingByMorph: Boolean get() = destination != null && !byReframe
    val travellingByReframe: Boolean get() = destination != null && byReframe

    /** A travel begins — or is re-planned in flight — from what is on screen right now. */
    fun plan(byReframe: Boolean, destination: CameraDestination, drawnCtrl: Float?) {
        ctrlFrom = drawnCtrl ?: destination.ctrl
        // The room and width the layout had last pass — mid-travel, whatever that travel had reached.
        leadFrom = lastLead
        gridWFrom = lastGridW
        this.byReframe = byReframe
        this.destination = destination
    }

    /** A screen rebuilt already in edit mode starts from the resting placement, held (see the stage). */
    fun adoptIfResting(lead: Int) {
        if (!resting || destination != null) return
        resting = false
        this.lead = lead
        trail = 0
    }

    private var lastLead = 0
    private var lastContentW = 0

    fun place(
        progress: Float,
        rest: RestSpan,
        contentW: Int,
        leftW: Int,
        columnGap: Int,
        viewportGridW: Int,
        edgeX: Int,
        scroll: Int,
    ): CameraPlacement {
        val to = destination
        if (to == null) {
            val leadNow = if (resting) rest.lead else lead
            val gridW = if (resting) rest.gridW else maxOf(lead + contentW + trail, viewportGridW)
            lastLead = leadNow
            lastGridW = gridW
            return CameraPlacement(
                startX = edgeX + leadNow,
                gridW = gridW,
                effectiveScroll = scroll.coerceIn(0, gridW - viewportGridW),
                cueShift = 0,
            )
        }
        val at = progress.coerceIn(0f, 1f)
        val ctrl = ctrlFrom + (to.ctrl - ctrlFrom) * at
        val leadNow = lerpInt(leadFrom, to.lead, at)
        val gridW = maxOf(lerpInt(gridWFrom, to.gridW, at), viewportGridW)
        val max = gridW - viewportGridW
        val effective = scroll.coerceIn(0, max)
        val contentAt = (ctrl - leftW - columnGap).roundToInt()
        lastContentW = contentW
        lastLead = leadNow
        lastGridW = gridW
        // As if scrolled: how far the content is from where this layout would put it unscrolled.
        val virtual = edgeX + leadNow - contentAt
        return CameraPlacement(
            startX = contentAt + effective,
            gridW = gridW,
            effectiveScroll = effective,
            cueShift = virtual.coerceIn(0, max) - effective,
        )
    }

    /** The scroll a travel of this kind lands on, or null if none of that kind is running. */
    fun land(byReframe: Boolean): Int? {
        val to = destination ?: return null
        if (this.byReframe != byReframe) return null
        return to.scroll
    }

    /** The scroller holds [land]'s value: the camera settles where the travel left it. */
    fun settle(byReframe: Boolean) {
        val to = destination ?: return
        if (this.byReframe != byReframe) return
        resting = to.resting
        lead = to.lead
        trail = (to.gridW - to.lead - lastContentW).coerceAtLeast(0)
        destination = null
    }
}

/**
 * **Keeps the grid's COLUMNS still while the rows change shape underneath them** (Dylan, 2026-09-24).
 *
 * At the first frame of a reveal's travel this captures both ends of it — the box widths it is
 * leaving (measured) and the ones it is heading for (the rows' INTRINSICS) — and every frame after
 * is a pure function of one number, the travel. The camera is [StageCamera]'s; this is only widths.
 */
private class EditMorphPlan {
    /** The box widths this travel is leaving — measured, whatever shape each box was in. */
    var fromWidths: IntArray? = null
        private set
    /** The ones it is heading for — each group's own intrinsic at the end it is going to. */
    var toWidths: IntArray? = null
        private set
    private var token: Any? = null
    /** The travel value this plan's ends were captured at — 0 from its start, wherever it had got to
     *  for one re-planned in flight. See [at]. */
    private var base = 0f

    /**
     * True on the one frame a travel begins — or is re-planned — when the other end needs capturing.
     * Every travel goes 0 → 1 and [token] says which one it is; a travel is also re-planned when a
     * box has [outgrown] the journey it was given.
     */
    fun begin(settled: Boolean, token: Any?, travel: Float, measured: IntArray): Boolean {
        if (settled) {
            fromWidths = null
            toWidths = null
            this.token = null
            base = 0f
            return false
        }
        val fresh = fromWidths == null || token != this.token
        if (!fresh && !outgrown(measured)) return false
        this.token = token
        // A re-plan starts HERE rather than back at the travel's beginning (see [at]).
        base = if (fresh) 0f else travel.coerceIn(0f, 1f)
        fromWidths = measured
        toWidths = null
        return true
    }

    /**
     * **Has a box changed shape for a reason this travel does not own?** Every box interpolates
     * monotonically between its two ends, so a width outside that interval is an intrinsic changing
     * underneath it — a command landing in a row mid-collapse, on every cross-group MOVE (Dylan,
     * 2026-09-27: "the camera movement is now instantaneous and has no animation/easing when moving a
     * tile to a new input group").
     */
    private fun outgrown(measured: IntArray): Boolean {
        val from = fromWidths ?: return false
        val to = toWidths ?: return false
        if (measured.size != from.size || measured.size != to.size) return true
        return measured.indices.any { index ->
            val low = minOf(from[index], to[index]) - WidthSlack
            val high = maxOf(from[index], to[index]) + WidthSlack
            measured[index] < low || measured[index] > high
        }
    }

    /** The travel as THIS plan sees it: 0 where its ends were captured, 1 at the end. */
    fun at(travel: Float): Float {
        if (base <= 0f) return travel.coerceIn(0f, 1f)
        if (base >= 1f) return 1f
        return ((travel.coerceIn(0f, 1f) - base) / (1f - base)).coerceIn(0f, 1f)
    }

    fun captureOtherEnd(widths: IntArray) {
        toWidths = widths
    }

    private companion object {
        /** A pixel of rounding either way before a width counts as having outgrown its journey. */
        const val WidthSlack = 1
    }
}

/**
 * **A tile arriving or leaving re-frames the view, the same way opening a group does** (Dylan,
 * 2026-09-24). A group's box is as wide as its tiles, so adding or clearing one re-widths the grid;
 * treated as a travel of its own ([StageCamera]), the view holds still on the frame it changes and
 * eases to the group framed whole.
 *
 * The change is detected in the LAYOUT, by comparing this pass's box widths against the last one's,
 * because an effect would only notice a frame later — and that frame is the jump.
 */
private class ReframePlan {
    /** Every group's box width, last layout. */
    private var widths: IntArray? = null
    private var armedFor = 0

    /** Bumped when a shape change needs animating; the stage runs the travel in answer. */
    val request = mutableIntStateOf(0)

    /**
     * **The group to frame, said out loud** (Dylan, 2026-09-25). Inferring it from which box changed
     * fails both ways that matter: a MOVE changes two boxes, and the first is as likely as not the one
     * it left; an ADD goes out to the full-screen picker and back, so there is no previous layout to
     * compare against at all. The stage names the group a command lands in, by binding id.
     */
    var frameGroup: RemapSimpleGroup? by mutableStateOf(null)
    var frameTick: Int by mutableIntStateOf(0)
    private var servedTick = 0

    /** The travel is running only once it has been reset for THIS request. */
    fun arm(id: Int) { armedFor = id }

    fun progressOf(progress: Float): Float = if (armedFor == request.intValue) progress else 0f

    /**
     * The group to frame afresh this pass, if any — a claim, or a box that changed shape ([widths]
     * null: nothing to watch). A claim PRE-EMPTS a travel under way ([busy]) rather than queueing:
     * a move's shape change tends to start one toward the row a command LEFT a frame or two before
     * the claim naming the row it joined arrives.
     */
    fun detect(widths: IntArray?, busy: Boolean, groupAt: (Int) -> RemapSimpleGroup): RemapSimpleGroup? {
        val was = this.widths
        this.widths = widths
        if (widths == null) return null
        val claimed = frameGroup.takeIf { frameTick != servedTick }
        val changed = if (was != null && !was.contentEquals(widths)) {
            widths.indices.first { widths[it] != was[it] }
        } else null
        val group = claimed ?: changed?.takeIf { !busy }?.let(groupAt) ?: return null
        servedTick = frameTick
        request.intValue++
        return group
    }
}

/**
 * **How far the pan reaches outboard of the group being worked on** (Dylan, 2026-09-28) — the one
 * piece of camera state that outlives a travel, and the whole of the pan's rules. In edit mode, in
 * both reveal settings:
 *
 *  1. **Opening a group** (entering edit mode) reaches to the END of it, "+" tile included — or to
 *     the target, [PanTargetTiles], if the group is narrower than that. Opening a group always shows
 *     its last tile.
 *  2. **Walking up or down the same column HOLDS the view** — the reach is kept and the camera does
 *     not move, however narrow the group walked into. A wide group opened and then left for a narrow
 *     neighbour does not pull the view back in.
 *  3. **Crossing to the other column frames the target**, however wide the group crossed into — a
 *     pan sized to its widest row, from a view already stretched to the widest row of THIS one, was
 *     one huge swing (Dylan: "the resultant camera pan is massive and jarring").
 *  4. **A tile committed to a group** — carried in, or added through the picker — frames that group
 *     whole again, as opening it would; from there rule 2 holds it. A tile cleared or pasted is
 *     framed the same way. While a tile is being CARRIED, rules 1–3 apply as ever.
 *  5. **A scroll the user makes themselves** gives the reach back to the target, and the next move
 *     frames rather than holds: the view is theirs now.
 *
 * Decided once per travel — on the frame it is planned ([plan]) or a re-frame is claimed
 * ([reframe]) — and never in between.
 */
private class PanReach {
    /** The room beyond the focused group's glyph, in pixels. */
    var reach = 0
        private set
    /** Which column [reach] was set for; null outside edit mode, which is what makes the next
     *  travel an OPEN. */
    private var leftColumn: Boolean? = null
    /** The group a tile has just been committed to — framed whole by the next travel into it. */
    var committed: RemapSimpleGroup? = null
    /** Whether the user has scrolled the body themselves since the reach was last decided. */
    var userScrolled = false
    /** Whether a finger is on the stage right now — what tells a user's scroll from the camera's. */
    var pointerDown = false

    /**
     * A travel toward [group] (null: leaving edit mode) whose box will be [width] wide at its end.
     * True if the camera should FRAME it, false if it should HOLD (rule 2).
     */
    fun plan(group: RemapSimpleGroup?, width: Int, target: Int): Boolean {
        if (group == null) {
            leftColumn = null
            committed = null
            userScrolled = false
            reach = target
            return false
        }
        val left = group in LeftColumnGroups
        val opening = leftColumn == null || group == committed
        val crossing = left != leftColumn
        reach = when {
            opening -> maxOf(target, width)
            crossing || userScrolled -> target
            else -> maxOf(reach, target)
        }
        val framing = opening || crossing || userScrolled
        if (group != committed) committed = null
        leftColumn = left
        userScrolled = false
        return framing
    }

    /** [group] changed shape and is being framed afresh — whole, as if it had just been opened. */
    fun reframe(group: RemapSimpleGroup, width: Int, target: Int) {
        reach = maxOf(target, width)
        leftColumn = group in LeftColumnGroups
        userScrolled = false
    }
}

/** A placed rectangle in stage (viewport) space, in pixels. */
internal data class StageRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}


private fun lerpInt(a: Int, b: Int, p: Float): Int = a + ((b - a) * p).roundToInt()

/**
 * Where the cursor last was: which ROW it was on, as a y in window space, and how far along that
 * row. A one-slot holder, deliberately not snapshot state — see its use in [RemapStage].
 */
private class CursorTrace {
    var y: Float? = null
    var slot: Int = 0
}

/** One band of the rest grid: the flank group on each side of the controller. */
internal class GridBand(val left: RemapSimpleGroup, val right: RemapSimpleGroup)

/**
 * The rest grid's bands, top to bottom — the flank groups, two per band.
 *
 * FOUR of them since 2026-09-26, when Select and Start stopped being one centre-column group
 * and became the utility group of each side (Dylan). The centre column is now the controller
 * image and nothing else, and every group in the view belongs to a flank.
 */
internal val GridBands = listOf(
    GridBand(RemapSimpleGroup.LEFT_SHOULDER, RemapSimpleGroup.RIGHT_SHOULDER),
    GridBand(RemapSimpleGroup.DPAD, RemapSimpleGroup.FACE),
    GridBand(RemapSimpleGroup.LEFT_STICK, RemapSimpleGroup.RIGHT_STICK),
    GridBand(RemapSimpleGroup.LEFT_UTILITY, RemapSimpleGroup.RIGHT_UTILITY),
)

/** The groups in the grid's LEFT column — which is the side their backing rectangle runs off
 *  (see [StageGroupBacking]). Taken from the bands so the two can't disagree. */
internal val LeftColumnGroups: Set<RemapSimpleGroup> = GridBands.map { it.left }.toSet()

/** The groups in the grid's RIGHT column. */
private val RightColumnGroups: Set<RemapSimpleGroup> = GridBands.map { it.right }.toSet()

/** Which band the controller image occupies. Bands above it anchor to their floor and bands
 *  below it to their ceiling, so every box points at the controller (see `restTop`). */
private const val ControllerBand = 1

/**
 * The group outline that replaced the basic view's cards (Dylan, 2026-09-22) — how far inside
 * the group's bounds it sits, how thick it draws, and how strongly it reads.
 *
 * Faint on purpose: it is there to say where one group ends and the next begins, not to rebuild
 * the card it replaced out of line work.
 */
// GroupOutlineInset is also where the backing rectangle stops: the line's inner face sits
// exactly this far inside the box's inner edge (the stroke is CENTRED on
// `GroupOutlineInset + GroupOutlineWidth / 2`, so its inner face lands back on the inset).
internal val GroupOutlineInset = 3.dp
private val GroupOutlineWidth = 1.dp

/** How far the group's line stops short of each end of its edge. */
private val GroupOutlineEndTrim = 4.dp

/**
 * **The line's VISIBLE extent along its edge, as an inset from each end of the box** — the trim,
 * less the half-stroke its round caps reach back out over. The backing rectangle is inset by
 * exactly this, so the two start and finish together ([StageGroupBacking]).
 *
 * Insetting by the bare trim instead left the line standing a hair past the rectangle at both
 * ends, which is precisely what Dylan saw (2026-09-26).
 */
internal val GroupOutlineEndInset = GroupOutlineEndTrim - GroupOutlineWidth / 2
private const val GroupOutlineAlpha = 0.55f

/**
 * How long a group's backing takes to light up, and how long to go dark again.
 *
 * Deliberately asymmetric (Dylan, 2026-09-26): arriving must feel like a response, so it is the
 * scale of a Material state layer's fade-in and runs LINEARLY — an eased 140ms both ways spent its
 * first frames barely moving, and on a one-plane colour step that is indistinguishable from the
 * highlight lagging the cursor. Leaving can take its time; a slower decay is what stops a column
 * walked quickly from strobing.
 */
private const val GroupBackingLightMillis = 70
private const val GroupBackingDarkMillis = 150

/** The same line where the group holds the controller cursor — the focus affordance the card
 *  used to carry. Drawn in the accent, so it reads as "you are here" rather than "heavier". */
private const val GroupOutlineFocusAlpha = 0.9f

/** How far the body scroller's chevrons sit out past the grid, into the plate's own inset. */
private val BodyChevronOutset = 2.dp

/** Fallback shape for the controller artwork, if its intrinsic size is ever unavailable. */
private const val DefaultControllerAspect = 0.62f

/** A group box's own inset around its rows. Named because the pan target is counted in BOX widths
 *  and so has to add it (see the stage's `panTarget`). */
private val GroupBoxPaddingX = 8.dp

/**
 * **The pan's TARGET, in tiles: 3, and 2 on a square screen** — Dylan's own numbers off the device.
 * See [PanReach] for where it applies.
 *
 * A default layout's group is two tiles wide (one command, one "+"), so 3 leaves a tile's worth of
 * room beyond it and 2 sits it flush. A 1:1 window has that much less width to spend. It was a
 * user setting briefly (2026-09-27/28); the rules in [PanReach] replaced the need for one.
 */
private const val PanTargetTiles = 3
private const val PanTargetTilesSquare = 2

/**
 * Where "a 1:1 screen" ends and a wider one begins, as a width/height ratio of the screen canvas.
 *
 * Only the pan target asks, and it is a fraction off square rather than exactly 1 so that a window
 * a few dp out of square — insets, a status bar, rounding — still counts as the square one Dylan
 * measured on.
 */
private const val SquareWindowAspect = 1.1f

/** Gutter between the rest grid's columns — wide enough to keep the flank boxes off the
 *  controller image between them. */
private val GridColumnGap = 18.dp

/** Gutter between the rest grid's bands. */
private val GridRowGap = 12.dp

/**
 * The controller column's width at rest, as a fraction of the grid's HEIGHT.
 *
 * **It is sized from the height on purpose.** The height is the one dimension that does NOT
 * change between the 1:1 screen and the expanded one (or with the layouts drawer open), so
 * taking the width from it pins the controller at the size it has in 1:1 — Dylan's ask — on any
 * device, and hands every extra pixel of a wider screen to the flanking columns instead. A
 * hardcoded dp would have done the "doesn't scale" half and got the size wrong on anything but
 * one device.
 *
 * 0.385 reproduced the weight share it replaced; Dylan widened it to 0.40 on 2026-09-16. **This
 * is the knob for the controller's resting COLUMN** — the picture inside it is
 * [ControllerImageFraction] of this.
 */
private const val ControllerColumnHeightRatio = 0.40f

/**
 * How much of its column the resting controller image fills (Dylan, 2026-09-21).
 *
 * Separate from [ControllerColumnHeightRatio] because the two do different jobs: the column's
 * width also sizes the utility box beneath the image and the gutters the flanks sit against, so
 * giving the picture breathing room by narrowing the column would have narrowed the box with it.
 * **This is the knob for the controller's resting size**; the column is the knob for the grid's
 * proportions.
 */
private const val ControllerImageFraction = 0.85f

/**
 * How far the controller's column may be SQUEEZED to keep the grid inside the window, as a
 * fraction of its natural (height-derived) width — see `spanOf` in the stage's layout.
 *
 * The picture is what the view is arranged around, but it is still decoration; the flanks are the
 * content. When the two can't both fit, the picture gives ground first, and only so far: past this
 * the screen is genuinely too narrow for the layout and the body scrolls, which is honest. 0.8
 * covers the case this was added for (a 4:3 screen, a few dp short) several times over without
 * letting the artwork collapse on a phone in landscape.
 */
private const val ControllerColumnSqueezeFloor = 0.8f
