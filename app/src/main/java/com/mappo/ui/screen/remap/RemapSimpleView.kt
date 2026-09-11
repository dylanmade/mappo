package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.BindingGroupGraph
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.InputSource
import com.mappo.data.model.steam.displayLabel
import com.mappo.data.model.steam.displayNameFor
import com.mappo.ui.glyph.InputGlyphs
import com.mappo.ui.screen.softDropShadow
import kotlin.math.roundToInt
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputPod
import com.mappo.ui.minput.MinputPodGap
import com.mappo.ui.minput.MinputPodPlateCorner
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle

/**
 * The simplified remap view: a controller diagram in the middle flanked by one tappable box per
 * input group, each row showing just the input glyph + what its **standard press** currently
 * does. Tapping a box animates it into the center of the screen (over the controller), morphing
 * into the in-place advanced editor ([RemapGroupEditor]); an invisible stand-in holds the
 * group's home position and the box animates back on close (or when another group is picked). The
 * top-center **Map** button is the future home of the input-mapping wizard (UI-only for now).
 *
 * Box styling: accent-tinted rounded boxes + bevel border (the treatment born on the retired
 * d-pad flower home's petal cards, now owned by the remap chrome). The whole band rides one
 * rectangular [MinputPod] plate (2026-08-30).
 *
 * The Inherit / Overlay / Gyro strip that used to sit beneath the band was REMOVED 2026-08-30
 * (Dylan). **Inherit is gone for good** — set-to-set inheritance is being replaced by a
 * "Switch set" (swap the whole action set) / "Stack set" (Steam-style action-set layering)
 * pair, neither built yet. Overlay and Gyro are only homeless: both get new homes shortly.
 * Re-creating the gyro picker is a `ModePillDropdown` over
 * `SourceModeCatalog.modesValidFor(InputSource.GYRO)` — see RemapPills.kt, which still
 * carries the pill and its width constant.
 */
@Composable
internal fun RemapSimpleView(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    onMap: () -> Unit,
    editorCallbacks: RemapGroupEditorCallbacks,
    modifier: Modifier = Modifier,
    // Gates the controller-focus seat below. While a side drawer is open, focus belongs to
    // the drawer cards — but browsing can swap this view in and out of composition (the
    // no-layout state ↔ controls flip), and an ungated entry-seat on remount stole focus
    // from the drawer mid-scroll. The pending seat is NOT consumed while gated, so it still
    // fires once the drawers close and the screen becomes controller-ready then.
    focusSeatEnabled: Boolean = true,
) {
    // The group whose editor should be open (user intent — survives the command-picker
    // round-trip) vs. the group currently on screen mid-animation.
    var expandedGroup by rememberSaveable { mutableStateOf<RemapSimpleGroup?>(null) }
    var visibleGroup by remember { mutableStateOf(expandedGroup) }
    val progress = remember { Animatable(if (expandedGroup != null) 1f else 0f) }
    val boxBounds = remember { mutableStateMapOf<RemapSimpleGroup, Rect>() }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // Focus target for the expanded editor — without it, the tapped box's disappearance sends
    // focus hunting to the first focusable on screen (the top-left back button). Attached to
    // the editor's Close button (NOT the overlay container: a focused container that spatially
    // contains every child is a directional-search dead end — no child is "in a direction"
    // from it, so controller focus could never step inside).
    val editorFocus = remember { FocusRequester() }
    // Which group box should reclaim controller focus once the editor collapses back into it.
    // Starts non-null on a fresh entry (no editor restoring): seating focus on the top-left
    // group box makes the screen controller-ready immediately — the Select/Start panel
    // summons are preview key handlers that only fire while focus sits in this subtree, and
    // an unseated screen's first d-pad press used to default-hunt into the frame chrome.
    var returnFocusGroup by remember {
        mutableStateOf(if (expandedGroup == null) RemapSimpleGroup.LEFT_SHOULDER else null)
    }
    val inputModeManager = LocalInputModeManager.current

    // Basic-view flavor of the tap-focus-recovery below: a tap anywhere clears Compose focus
    // (touch-mode entry); with no editor open, re-seat the controller cursor on the top-left
    // group box (Left Trigger) so the next d-pad press navigates from a known home. Deferred
    // + touch-gated for the same reasons as the editor's recovery.
    var viewHadFocus by remember { mutableStateOf(false) }
    var baseRefocusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(baseRefocusTick) {
        if (baseRefocusTick > 0 && inputModeManager.inputMode == InputMode.Touch &&
            expandedGroup == null && visibleGroup == null
        ) {
            returnFocusGroup = RemapSimpleGroup.LEFT_SHOULDER
        }
    }

    // Drives expand / collapse / switch-to-another-group. Switching collapses the current
    // editor back to its home box before expanding the next one.
    LaunchedEffect(expandedGroup) {
        val target = expandedGroup
        if (target == visibleGroup) {
            if (target != null && progress.value < 1f) {
                progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
            }
            return@LaunchedEffect
        }
        if (visibleGroup != null) {
            val closing = visibleGroup
            progress.animateTo(0f, tween(CollapseMillis, easing = FastOutSlowInEasing))
            visibleGroup = null
            // Backing out (not switching groups): hand controller focus back to the home box.
            if (target == null) returnFocusGroup = closing
        }
        if (target != null) {
            visibleGroup = target
            progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
        }
    }

    BackHandler(enabled = expandedGroup != null) { expandedGroup = null }

    // Move controller focus into the editor as it opens (see editorFocus above) — it lands on
    // the first command row's input button (Close when that isn't focusable), and the d-pad
    // walks the rows and header controls from there.
    // focusSeatEnabled is a key (not just a guard) so a seat deferred while a drawer held
    // focus fires when the drawers close — expandedGroup is saveable state, so a drawer-scroll
    // remount can land here with the editor already open.
    LaunchedEffect(visibleGroup, focusSeatEnabled) {
        if (visibleGroup != null && focusSeatEnabled) runCatching { editorFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { rootCoords = it; rootSize = it.size }
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    viewHadFocus = true
                } else if (viewHadFocus && expandedGroup == null) {
                    baseRefocusTick++
                }
            },
    ) {
        // The band rides ONE plate (2026-08-30): every group box plus the controller image
        // between them sits on a single rectangular [MinputPod], so the input map reads as one
        // object floating over the view's lowest plane rather than eight boxes scattered on it.
        // The plate is centered in whatever height the view has — the flexed Gyro/Overlay strip
        // that used to claim the space below it retired the same day (see the file KDoc).
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The plate's own inset from the screen edges. Horizontally it matches the top
                // bar's, so the plate's rim lines up with the identity pod's above it;
                // vertically it is the pod-to-pod gap, which is the whole distance to the
                // flush-bottomed top bar (that bar gives no vertical air by design).
                .padding(horizontal = MinputBarEdgePadding, vertical = MinputPodGap)
                // While the editor overlay is up, directional focus must not wander into
                // the plate underneath it — cancel any attempt to enter this subtree.
                .then(
                    if (visibleGroup != null) {
                        Modifier
                            .focusProperties { onEnter = { cancelFocusChange() } }
                            .focusGroup()
                    } else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
                // The three-column band, now the plate's content and sized BY it.
                MinputPod(
                    // A plate, not a capsule: the pill default would round this to a lozenge.
                    corner = MinputPodPlateCorner,
                    // Wide gutter keeps the side group boxes off the controller image.
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    // FILLS the content section rather than hugging the band (2026-08-30,
                    // Dylan): this plate is the middle region, not chrome floating in it.
                    // That retires the IntrinsicSize.Min the band used to measure itself by
                    // — the columns take their height from the plate now, so the middle
                    // column's Map button pins to the plate's top edge and the utility box
                    // to its bottom, which is what the intrinsic pass was arranging by hand.
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val box: @Composable (RemapSimpleGroup, Modifier) -> Unit = { group, boxModifier ->
                        GroupBox(
                            group = group,
                            viewingSet = viewingSet,
                            viewingLayer = viewingLayer,
                            config = config,
                            placeholder = group == visibleGroup,
                            placeholderSize = boxBounds[group]?.size,
                            onPositioned = { coords ->
                                rootCoords?.let { root -> boxBounds[group] = root.localBoundingBoxOf(coords) }
                            },
                            onOpenGroup = { expandedGroup = it },
                            requestFocus = focusSeatEnabled && group == returnFocusGroup,
                            onFocusHandled = { returnFocusGroup = null },
                            modifier = boxModifier,
                        )
                    }
                    // Left column, counterclockwise start: shoulder → d-pad → stick. Boxes anchor
                    // toward the screen center (the controller), i.e. this column's END edge, and
                    // cluster toward the vertical center.
                    Column(
                        // start gutter reserves room for the boxes' outside-left +N badges (they're
                        // zero-footprint overlays — without this they'd clip off the view edge).
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = BadgeGutter),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.End,
                    ) {
                        box(RemapSimpleGroup.LEFT_SHOULDER, Modifier)
                        box(RemapSimpleGroup.DPAD, Modifier)
                        box(RemapSimpleGroup.LEFT_STICK, Modifier)
                    }
                    // Middle column: Map CTA top-aligned with the flanking columns' topmost boxes, the
                    // utility box bottom-aligned with their bottommost, controller between. Weight
                    // trimmed from 1.25 to hand the flanking group boxes a little more inner width
                    // (the controller image shrinks with its column).
                    Column(
                        modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(
                            onClick = onMap,
                            modifier = Modifier.testTag("map-button"),
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Map")
                        }
                        // sizeToIntrinsics=false: the image contributes no intrinsic height, so
                        // it never drives the band's measurement — it takes the slack the
                        // flanking group boxes leave (weight(1f) below).
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .paint(
                                    painter = painterResource(R.drawable.controller_placeholder),
                                    sizeToIntrinsics = false,
                                    contentScale = ContentScale.Fit,
                                ),
                        )
                        // SYMMETRIC gutter on the box only (not the column), at HALF depth: the box's
                        // right-side +N badge can also spill into the 18dp inter-column gap, so a half
                        // gutter keeps enough clearance without squeezing the box (full BadgeGutter ate
                        // too much width once the middle column narrowed to 1.1).
                        box(RemapSimpleGroup.UTILITY, Modifier.padding(horizontal = BadgeGutter / 2))
                    }
                    // Right column: shoulder → face buttons → stick. Boxes anchor toward the screen
                    // center (this column's START edge).
                    Column(
                        // end gutter reserves room for the outside-right +N badges (see left column).
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(end = BadgeGutter),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        box(RemapSimpleGroup.RIGHT_SHOULDER, Modifier)
                        box(RemapSimpleGroup.FACE, Modifier)
                        box(RemapSimpleGroup.RIGHT_STICK, Modifier)
                    }
                }
        }

        // ── The morphing editor overlay ───────────────────────────────────
        val vg = visibleGroup
        val origin = vg?.let { boxBounds[it] }
        if (vg != null && origin != null && rootSize != IntSize.Zero) {
            // Target: full width minus a slim margin (the table scrolls sideways, so it wants
            // every pixel of width), but only as TALL as the editor's content needs —
            // [advancedEditorHeight] is exact because every part of the table is a fixed size.
            // Filling the height left a two-row group (a shoulder, the utility pair) sitting
            // in most of a screen of dead space. Clamped to the space available, and centred
            // vertically so a short panel reads as floating rather than top-stuck.
            val density = LocalDensity.current
            val marginPx = with(density) { EditorMargin.toPx() }
            val availableH = rootSize.height - marginPx * 2
            val wantedH = with(density) { advancedEditorHeight(vg).toPx() }
            val targetH = wantedH.coerceAtMost(availableH)
            val target = Rect(
                offset = Offset(marginPx, marginPx + (availableH - targetH) / 2f),
                size = Size(rootSize.width - marginPx * 2, targetH),
            )
            val shape = RoundedCornerShape(GroupCorner)
            val container = minputBoxContainer()
            // Recompose only when the animation starts/ends; the per-frame rect is read in the
            // LAYOUT phase (the layout modifier below) and the fades in the DRAW phase
            // (graphicsLayer) — recomposing every frame is what made the morph jitter.
            val midFlight by remember { derivedStateOf { progress.value < 1f } }
            // TopStart-anchored overlay layer: the panel is placed at absolute root coords.
            // (Placing it straight in the center-aligned root Box offset the rect from the
            // CENTER slot — every morph appeared to launch from the bottom-right.)
            //
            // Focus recovery: any TAP flips the window into touch mode, which CLEARS Compose
            // focus — after that, d-pad navigation inside the editor was dead until it was
            // reopened. When the editor subtree loses all focus while still open, re-request
            // the editor's default target so the controller always has a live cursor. The
            // request is DEFERRED through state + LaunchedEffect — focus-loss also fires while
            // the composition is being disposed, and a synchronous requestFocus mid-detach
            // corrupts the node lifecycle ("Must run runDetachLifecycle()..."); an effect
            // simply never runs on a disposing composition. (Guarded on expandedGroup so a
            // closing editor doesn't fight the return-focus-to-box handoff.)
            var editorHadFocus by remember(vg) { mutableStateOf(false) }
            var refocusTick by remember(vg) { mutableIntStateOf(0) }
            LaunchedEffect(refocusTick) {
                // Touch mode only: a tap is the thing that CLEARS focus wholesale. In key
                // mode a subtree loss means focus legitimately moved elsewhere (e.g. up to
                // the set/layer tabs) — recovering would yank it straight back.
                if (refocusTick > 0 && inputModeManager.inputMode == InputMode.Touch) {
                    runCatching { editorFocus.requestFocus() }
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .onFocusChanged { state ->
                        if (state.hasFocus) {
                            editorHadFocus = true
                        } else if (editorHadFocus && expandedGroup == vg) {
                            refocusTick++
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val rect = lerp(origin, target, progress.value)
                            val placeable = measurable.measure(
                                androidx.compose.ui.unit.Constraints.fixed(
                                    rect.width.roundToInt().coerceAtLeast(1),
                                    rect.height.roundToInt().coerceAtLeast(1),
                                ),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                            }
                        }
                        // offsetY 0: the offset shadow paints a dark band hugging the bottom
                        // edge that reads as the surface overextending.
                        .softDropShadow(cornerRadius = GroupCorner, offsetY = 0.dp)
                        .clip(shape)
                        .background(container)
                        .border(minputBevelBorder(container, GroupCorner), shape)
                        .testTag("group-editor"),
                ) {
                    // Crossfade: the box's summary rows dissolve into the editor as it grows.
                    if (midFlight) {
                        Column(
                            modifier = Modifier
                                .graphicsLayer { alpha = 1f - progress.value }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(SummaryRowSpacing),
                        ) {
                            GroupSummaryRows(vg, viewingSet, viewingLayer, config)
                        }
                    }
                    RemapGroupEditor(
                        group = vg,
                        viewingSet = viewingSet,
                        viewingLayer = viewingLayer,
                        config = config,
                        callbacks = editorCallbacks,
                        onClose = { expandedGroup = null },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = progress.value },
                        focusRequester = editorFocus,
                    )
                }
            }
        }
    }
}

/** One display row in a group box: which sub-input to summarize. */
internal data class SimpleRowSpec(val source: InputSource, val subInputKey: String)

/**
 * The simple view's input groups. Rows summarize the standard-press assignment only
 * (hold/double/etc. live in the expanded editor); the same specs drive the editor's rows.
 */
internal enum class RemapSimpleGroup(val rows: List<SimpleRowSpec>) {
    LEFT_SHOULDER(
        listOf(
            SimpleRowSpec(InputSource.LEFT_TRIGGER, "full_pull"),
            SimpleRowSpec(InputSource.LEFT_BUMPER, "click"),
        ),
    ),
    DPAD(
        listOf(
            SimpleRowSpec(InputSource.DPAD, "dpad_up"),
            SimpleRowSpec(InputSource.DPAD, "dpad_left"),
            SimpleRowSpec(InputSource.DPAD, "dpad_right"),
            SimpleRowSpec(InputSource.DPAD, "dpad_down"),
        ),
    ),
    LEFT_STICK(
        listOf(
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "dpad_up"),
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "dpad_left"),
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "dpad_right"),
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "dpad_down"),
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "click"),
        ),
    ),
    UTILITY(
        listOf(
            SimpleRowSpec(InputSource.SWITCH_START, "click"),
            SimpleRowSpec(InputSource.SWITCH_SELECT, "click"),
        ),
    ),
    RIGHT_STICK(
        listOf(
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "dpad_up"),
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "dpad_left"),
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "dpad_right"),
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "dpad_down"),
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "click"),
        ),
    ),
    FACE(
        listOf(
            SimpleRowSpec(InputSource.BUTTON_DIAMOND, "button_y"),
            SimpleRowSpec(InputSource.BUTTON_DIAMOND, "button_x"),
            SimpleRowSpec(InputSource.BUTTON_DIAMOND, "button_b"),
            SimpleRowSpec(InputSource.BUTTON_DIAMOND, "button_a"),
        ),
    ),
    RIGHT_SHOULDER(
        listOf(
            SimpleRowSpec(InputSource.RIGHT_TRIGGER, "full_pull"),
            SimpleRowSpec(InputSource.RIGHT_BUMPER, "click"),
        ),
    ),
}

/**
 * Resolve what a row's standard press currently does, as a short label:
 * `(Device default)` mode or a present-but-unbound input → the input's own resting name
 * ([defaultRowLabel]); `None` → "None"; a bound FULL_PRESS command → its display label; an
 * active mode with no per-input row (analog modes) → the mode's name. Layer view resolves the
 * override first and falls through to the base set (ghost semantics).
 */
internal fun simpleRowLabel(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    spec: SimpleRowSpec,
): String {
    val layerGroup: BindingGroupGraph? = viewingLayer?.presetFor(spec.source)?.group
    val baseGroup: BindingGroupGraph? = viewingSet?.presetFor(spec.source)?.group
    val effective = layerGroup ?: baseGroup ?: return defaultRowLabel(spec)
    when (effective.group.mode) {
        BindingMode.DEVICE_DEFAULT -> return defaultRowLabel(spec)
        BindingMode.NONE -> return "None"
        else -> Unit
    }
    val groupInput = layerGroup?.inputByKey(spec.subInputKey)
        ?: baseGroup?.inputByKey(spec.subInputKey)
    val primary = groupInput?.activators?.firstOrNull { it.activator.type == ActivatorType.FULL_PRESS }
        ?: groupInput?.activators?.firstOrNull()
    // A user-given label always wins over the raw assignment in the basic view.
    val userLabel = primary?.bindings?.firstOrNull()?.label
    if (!userLabel.isNullOrBlank()) return userLabel
    val output = primary?.primaryOutput
    return when {
        // Unbound displays as "(Device default)" in the editor; the summary shows the
        // input's resting name.
        output != null && output != BindingOutput.Unbound -> output.displayLabel(config)
        groupInput != null -> defaultRowLabel(spec)
        else -> effective.group.mode.displayNameFor(spec.source)
    }
}

/**
 * The resting label for an unchanged/unlabeled input — its own physical name rather than a
 * generic "Default". User-specified wording; Title Case button names are a deliberate
 * exception to the sentence-case doctrine (they read as proper nouns).
 */
internal fun defaultRowLabel(spec: SimpleRowSpec): String = when (spec.source) {
    InputSource.LEFT_TRIGGER -> "Left Trigger"
    InputSource.RIGHT_TRIGGER -> "Right Trigger"
    InputSource.LEFT_BUMPER -> "Left Bumper"
    InputSource.RIGHT_BUMPER -> "Right Bumper"
    InputSource.SWITCH_START -> "Start Button"
    InputSource.SWITCH_SELECT -> "Select Button"
    InputSource.DPAD -> when (spec.subInputKey) {
        "dpad_up" -> "D-Pad Up"
        "dpad_left" -> "D-Pad Left"
        "dpad_right" -> "D-Pad Right"
        "dpad_down" -> "D-Pad Down"
        else -> "D-Pad"
    }
    InputSource.LEFT_JOYSTICK, InputSource.RIGHT_JOYSTICK -> {
        val stick = if (spec.source == InputSource.LEFT_JOYSTICK) "L-Stick" else "R-Stick"
        when (spec.subInputKey) {
            "dpad_up" -> "$stick Up"
            "dpad_left" -> "$stick Left"
            "dpad_right" -> "$stick Right"
            "dpad_down" -> "$stick Down"
            "click" -> "$stick Click"
            else -> stick
        }
    }
    InputSource.BUTTON_DIAMOND -> when (spec.subInputKey) {
        "button_y" -> "Y Button"
        "button_x" -> "X Button"
        "button_b" -> "B Button"
        "button_a" -> "A Button"
        else -> "Button"
    }
    else -> "Default"
}

/**
 * Per-row +N counts: how many command rows each displayed sub-input carries beyond its
 * primary, plus BOUND rows on sub-inputs the box doesn't summarize (soft pulls, outer rings —
 * their seeded-but-unbound rows don't count), attributed to that source's FIRST displayed row.
 * Each nonzero entry surfaces as a +N badge beside its own summary row; the values sum to the
 * group's total extras.
 */
internal fun rowExtraInputCounts(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
): Map<SimpleRowSpec, Int> {
    val counts = mutableMapOf<SimpleRowSpec, Int>()
    val displayedKeysBySource = group.rows.groupBy({ it.source }, { it.subInputKey })
    for ((source, displayedKeys) in displayedKeysBySource) {
        val layerGroup = viewingLayer?.presetFor(source)?.group
        val baseGroup = viewingSet?.presetFor(source)?.group
        val effective = layerGroup ?: baseGroup ?: continue
        val mode = effective.group.mode
        if (mode == BindingMode.DEVICE_DEFAULT || mode == BindingMode.NONE) continue
        for ((subKey, _) in RemapSections.bindableSubInputsFor(source, mode)) {
            val groupInput = layerGroup?.inputByKey(subKey) ?: baseGroup?.inputByKey(subKey) ?: continue
            val rows = groupInput.activators.flatMap { ag -> ag.bindings }
            val spec: SimpleRowSpec
            val extra: Int
            if (subKey in displayedKeys) {
                spec = SimpleRowSpec(source, subKey)
                extra = (rows.size - 1).coerceAtLeast(0)
            } else {
                // Hidden sub-inputs have no row of their own — they surface on their
                // source's first displayed row (soft pull → the trigger's full-pull row).
                spec = group.rows.first { it.source == source }
                extra = rows.count { BindingOutput.fromEntity(it.outputType, it.args) != BindingOutput.Unbound }
            }
            if (extra > 0) counts[spec] = (counts[spec] ?: 0) + extra
        }
    }
    return counts
}

/** The glyph + standard-press summary rows of one group (shared by the box and the morph). */
@Composable
private fun GroupSummaryRows(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
) {
    group.rows.forEach { spec ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.height(SummaryRowHeight),
        ) {
            InputGlyphs.SubInputGlyph(spec.source, spec.subInputKey, size = 14.dp)
            Text(
                text = simpleRowLabel(viewingSet, viewingLayer, config, spec),
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One tappable group box (the accent-tinted petal-card treatment). While the group is
 * expanded into the editor, [placeholder] renders a same-size invisible stand-in instead — the
 * spot the editor animates back to.
 */
@Composable
private fun GroupBox(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    placeholder: Boolean,
    placeholderSize: Size?,
    onPositioned: (LayoutCoordinates) -> Unit,
    onOpenGroup: (RemapSimpleGroup) -> Unit,
    // One-shot: reclaim controller focus (the editor just collapsed back into this box).
    requestFocus: Boolean = false,
    onFocusHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(GroupCorner)
    val accent = MaterialTheme.colorScheme.primary
    if (placeholder && placeholderSize != null) {
        // Invisible same-size stand-in, sized to the box's last measured bounds: holds the
        // home position (and the animate-back rect) while the group lives in the editor. The
        // old dashed outline moved to ui/component/DashedPlaceholderBox — the expanded editor
        // covers the whole band now, so drawing the dashes bought nothing.
        val density = LocalDensity.current
        Box(
            modifier = modifier
                .onGloballyPositioned(onPositioned)
                .size(
                    width = with(density) { placeholderSize.width.toDp() },
                    height = with(density) { placeholderSize.height.toDp() },
                ),
        )
        return
    }
    // Shared box treatment (same identity as the home flower's petal cards) — also the basis
    // the pill controls now copy, via the minputBoxContainer/remapBoxOutline helpers.
    val container = minputBoxContainer()
    val rowExtras = rowExtraInputCounts(group, viewingSet, viewingLayer)
    // Each +N hangs OUTSIDE the box as a ZERO-FOOTPRINT overlay, aligned with ITS OWN summary
    // row (per-row extras, not one group total). Zero-footprint: it reports no layout size, so
    // it can never shift the box (the centered utility box drifted left when the badge took
    // real width) and never wraps (measured unconstrained). It draws into the column gutter.
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) {
        // LaunchedEffect (not an inline call): the box may be freshly recomposed from its
        // placeholder branch, and the focus target only exists after this composition lands.
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
            onFocusHandled()
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Box(modifier = modifier) {
        Column(
            // Full column width regardless of label content — every box in a column reads as
            // the same fixed-width card.
            modifier = Modifier
                .fillMaxWidth()
                // Bounds capture must sit OUTSIDE the lift layer: localBoundingBoxOf maps
                // through graphicsLayer transforms, so capturing inside it would bake the
                // focus offset (historically the 1.05× focus grow) into the placeholder/
                // morph-origin rect — the stand-in came out displaced/oversized and the
                // neighboring boxes jumped during the morph.
                .onGloballyPositioned(onPositioned)
                .minputInteractiveMotion(interaction)
                .clip(shape)
                .background(container)
                .border(minputBevelBorder(container, GroupCorner), shape)
                .focusRequester(focusRequester)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ) { onOpenGroup(group) }
                .testTag("simple-group:${group.name}")
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(SummaryRowSpacing),
        ) {
            GroupSummaryRows(group, viewingSet, viewingLayer, config)
        }
        if (rowExtras.isNotEmpty()) {
            val onLeft = group.badgeOnLeft
            val gapPx = with(LocalDensity.current) { 4.dp.toPx() }
            val topPx = with(LocalDensity.current) { BadgeFirstRowAlignPadding.toPx() }
            val pitchPx = with(LocalDensity.current) { (SummaryRowHeight + SummaryRowSpacing).toPx() }
            group.rows.forEachIndexed { index, spec ->
                val extra = rowExtras[spec] ?: return@forEachIndexed
                Text(
                    // Hair spaces: between the plus and the count (thin space read a touch too
                    // wide), and on the box-facing side to pad the badge off the box border.
                    text = if (onLeft) "+\u200A$extra\u200A" else "\u200A+\u200A$extra",
                    style = minputMiniTextStyle(),
                    color = accent,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(if (onLeft) Alignment.TopStart else Alignment.TopEnd)
                        .layout { measurable, _ ->
                            // Measure at intrinsic size, report ZERO size to the parent, and
                            // hang the text off the box's outer edge, at this row's height.
                            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints())
                            layout(0, 0) {
                                val x = if (onLeft) -(placeable.width + gapPx) else gapPx
                                placeable.place(x.roundToInt(), (topPx + pitchPx * index).roundToInt())
                            }
                        },
                )
            }
        }
    }
}

/** Left column boxes carry their +N badge on the left (outside edge toward the screen center
 *  is already occupied by the box anchor); middle + right columns carry it on the right. */
private val RemapSimpleGroup.badgeOnLeft: Boolean
    get() = this == RemapSimpleGroup.LEFT_SHOULDER ||
        this == RemapSimpleGroup.DPAD ||
        this == RemapSimpleGroup.LEFT_STICK

/** Height of one glyph + label summary row inside a group box. */
private val SummaryRowHeight = 17.dp

/** Vertical gap between summary rows — with [SummaryRowHeight], sets the +N badges' row pitch. */
private val SummaryRowSpacing = 4.dp

/** Aligns a +N badge's text with its summary row (6dp box padding + the 17dp row against the
 *  badge's 14sp line height → 6 + (17−14)/2); each subsequent row adds one row pitch. */
private val BadgeFirstRowAlignPadding = 7.5.dp

/** Column-edge reserve for the zero-footprint +N badges (badge width + its 4dp gap) — kept as
 *  tight as the badge allows so the group boxes get the widest possible footprint. */
private val BadgeGutter = 18.dp

// The group editor's morph values — canonical in the library (MinputDefaults.kt); these
// are the remap package's aliases. The layout/options panels no longer morph (they're
// MinputModals now, fade + settle) but still share GroupCorner/EditorMargin framing so
// the remap surfaces read as one family.
internal val GroupCorner = com.mappo.ui.minput.MinputMorphCorner
internal const val ExpandMillis = com.mappo.ui.minput.MinputMorphExpandMillis
internal const val CollapseMillis = com.mappo.ui.minput.MinputMorphCollapseMillis
/** Inset between the expanded editor (or full-screen panel) and its host's edges. */
internal val EditorMargin = 10.dp
