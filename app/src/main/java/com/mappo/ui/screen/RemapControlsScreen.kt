package com.mappo.ui.screen

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.InputSource
import com.mappo.data.model.steam.displayName
import com.mappo.data.model.steam.displayNameFor
import com.mappo.data.model.steam.requiresShizuku as outputRequiresShizuku
import com.mappo.service.input.modes.requiresShizuku
import com.mappo.service.input.modes.requiresShizukuOnSource
import com.mappo.ui.compact.scaledLayout
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputBarStackGap
import com.mappo.ui.minput.MinputDialog
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import com.mappo.ui.screen.remap.RemapBottomRow
import com.mappo.ui.screen.remap.RemapGroupEditorCallbacks
import com.mappo.ui.screen.remap.RemapOptionEntry
import com.mappo.ui.screen.remap.RemapPanel
import com.mappo.ui.screen.remap.RemapPanelOverlay
import com.mappo.ui.screen.remap.RemapSections
import com.mappo.ui.screen.remap.RemapSimpleView
import com.mappo.ui.screen.remap.RemapTopBar
import com.mappo.ui.screen.remap.settings.SourceModeSettingsSchema

private const val REMAP_SCREEN_TAG = "RemapControlsScreen"

/** True if any binding in [group] has a Shizuku-requiring output (e.g. analog stick directions). */
private fun shizukuOutputInGroup(group: com.mappo.data.model.steam.BindingGroupGraph): Boolean =
    group.inputs.any { gi ->
        gi.activators.any { ag ->
            ag.bindings.any { b -> BindingOutput.fromEntity(b.outputType, b.args).outputRequiresShizuku() }
        }
    }

/**
 * The Remap Controls screen (2026-07): set/layer tabs on top, then the simplified view — group
 * boxes around the controller image, with the tapped box morphing in place into the advanced
 * group editor (`RemapGroupEditor`) — and the Gyro/Overlay strip at the bottom. The planned
 * mapping wizard (the "Map" CTA) becomes the primary mapping flow later.
 *
 * Command picking still navigates to the full-screen picker and pops back with [pickerResult];
 * binding edits ride the same callbacks the previous incarnations used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemapControlsScreen(
    config: ControllerConfig?,
    onOpenInputEditor: (inputSource: com.mappo.data.model.steam.InputSource, groupInputKey: String, label: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    profileName: String? = null,
    viewingActionSetId: Long? = null,
    onSelectActionSet: (Long) -> Unit = {},
    onAddActionSet: (title: String, inheritFromSetId: Long?) -> Unit = { _, _ -> },
    onRenameActionSet: (actionSetId: Long, newTitle: String) -> Unit = { _, _ -> },
    onDuplicateActionSet: (sourceSetId: Long, newTitle: String) -> Unit = { _, _ -> },
    onDeleteActionSet: (actionSetId: Long) -> Unit = {},
    viewingLayerId: Long? = null,
    onSelectLayer: (Long?) -> Unit = {},
    onAddLayer: (actionSetId: Long, title: String) -> Unit = { _, _ -> },
    onRenameLayer: (layerId: Long, newTitle: String) -> Unit = { _, _ -> },
    onDuplicateLayer: (sourceLayerId: Long, newTitle: String) -> Unit = { _, _ -> },
    onDeleteLayer: (layerId: Long) -> Unit = {},
    onClearLayerOverride: (layerId: Long, inputSource: com.mappo.data.model.steam.InputSource, groupInputKey: String) -> Unit = { _, _, _ -> },
    onSetBindingGroupMode: (bindingGroupId: Long, mode: BindingMode) -> Unit = { _, _ -> },
    onOpenModeSettings: (bindingGroupId: Long, source: InputSource) -> Unit = { _, _ -> },
    onAddModeShift: (actionSetId: Long?, actionLayerId: Long?, ownerSource: InputSource) -> Unit = { _, _, _ -> },
    onRemoveModeShift: (modeShiftId: Long) -> Unit = {},
    onSetModeShiftTrigger: (modeShiftId: Long, triggerSource: InputSource?, triggerSubInput: String?) -> Unit = { _, _, _ -> },
    onOpenModeShiftInputEditor: (modeShiftId: Long, ownerSource: InputSource, groupInputKey: String, label: String) -> Unit = { _, _, _, _ -> },
    shizukuRequiredAcknowledged: Boolean = true,
    shizukuReady: Boolean = true,
    shizukuState: com.mappo.service.shizuku.ShizukuState = com.mappo.service.shizuku.ShizukuState.Granted,
    onAcknowledgeShizukuRequired: () -> Unit = {},
    onOpenShizukuSetup: () -> Unit = {},
    // Inline input-assignment editing. The command picker still navigates away (full screen) and
    // pops back here, delivering its result via [pickerResult].
    pickerResult: BindingOutput? = null,
    onConsumePickerResult: () -> Unit = {},
    onPickResult: (bindingId: Long, output: BindingOutput) -> Unit = { _, _ -> },
    onOpenPicker: (title: String, current: BindingOutput) -> Unit = { _, _ -> },
    // Activator-level callbacks. The group editor drives the binding-level set below; these
    // remain in the signature because MainScreen wires them and future surfaces (the wizard,
    // mode-shift editing) will re-consume them.
    onAddActivator: (groupInputId: Long, type: ActivatorType) -> Unit = { _, _ -> },
    onRemoveActivator: (activatorId: Long) -> Unit = {},
    onSetActivatorType: (activatorId: Long, type: ActivatorType) -> Unit = { _, _ -> },
    onOpenActivatorSettings: (activatorId: Long, label: String) -> Unit = { _, _ -> },
    onAddCommand: (activatorId: Long) -> Unit = {},
    onRemoveCommand: (bindingId: Long) -> Unit = {},
    onAddInputRow: (groupInputId: Long, type: ActivatorType) -> Unit = { _, _ -> },
    onSetInputRowPressType: (bindingId: Long, type: ActivatorType) -> Unit = { _, _ -> },
    onSetInputRowLabel: (bindingId: Long, label: String) -> Unit = { _, _ -> },
    onDeleteInputRow: (bindingId: Long) -> Unit = {},
    // Row duplication and whole-group reset need dedicated repo ops (duplicates must own their
    // data); default no-ops until those land — the menu items render but do nothing.
    onDuplicateInputRow: (bindingId: Long) -> Unit = {},
    onResetBindingGroup: (bindingGroupId: Long) -> Unit = {},
    // ── The viewed application context (rides the route from the layouts view; the home
    // instance derives it from the active layout's binding) ────────────────────────
    viewedAppLabel: String? = null,
    viewedAppPackage: String? = null,
    // ── Layout settings panel (the top-bar summon; physical Start) ─────────────────
    optionsEntries: List<RemapOptionEntry> = emptyList(),
    // ── 2026-08-20 flow re-imagining: viewing vs active ────────────────────────────
    // True when the layout on screen IS the runtime-active layout (the home state): the
    // bar wears the Auto switch and drops Back/Activate. False = a layout under
    // inspection from the layouts view.
    isActiveLayout: Boolean = true,
    autoSwitchEnabled: Boolean = false,
    onAutoSwitchChange: (Boolean) -> Unit = {},
    onActivateLayout: () -> Unit = {},
    // Sticky "Don't show again" on the activate warning (activating turns auto off).
    activateWarningSuppressed: Boolean = false,
    onSuppressActivateWarning: () -> Unit = {},
    onViewLayouts: () -> Unit = {},
    // "Set as <application> default": shown when the viewed layout isn't its parent
    // application's bound default. The caller owns the condition AND the bind.
    showSetDefaultAction: Boolean = false,
    onSetAppDefault: () -> Unit = {},
) {
    // Physical/gesture back returns to the layouts view. The expanded group editor and the
    // options panel overlay install their own (more-recent) BackHandlers while open, so this
    // only fires when nothing else is dismissable.
    BackHandler { onBack() }

    // Whether the options panel is open. User intent — survives the navigation round-trips
    // the options entries launch.
    var openPanel by rememberSaveable { mutableStateOf<RemapPanel?>(null) }
    // Which management dialog is currently open. Plain `remember` — dialogs are short-lived;
    // rotation-survival isn't worth a custom Saver.
    var dialog by remember { mutableStateOf<ActionSetDialogState>(ActionSetDialogState.None) }
    var layerDialog by remember { mutableStateOf<LayerDialogState>(LayerDialogState.None) }
    // The activate-layout warning (shown when Auto is on and the warning isn't
    // sticky-dismissed): activating manually turns auto detection off.
    var activateWarningOpen by remember { mutableStateOf(false) }

    // Stash an analog-mode pick if Shizuku isn't ready AND the explainer hasn't been
    // acknowledged. `Pair(bindingGroupId, mode)`. Once Shizuku is Granted OR the user has acked,
    // picks proceed silently.
    var pendingAnalogPick by remember { mutableStateOf<Pair<Long, BindingMode>?>(null) }

    val gatedSetBindingGroupMode: (Long, BindingMode) -> Unit = { bindingGroupId, mode ->
        // Resolve the source for this binding group so the gate can be source-aware: NONE on a
        // stick / trigger / dpad needs Shizuku (EVIOCGRAB silences); NONE on a button doesn't.
        val source: InputSource? = config?.actionSets?.firstNotNullOfOrNull { set ->
            set.preset.firstOrNull { it.group.group.id == bindingGroupId }?.inputSource
                ?: set.layers.firstNotNullOfOrNull { layer ->
                    layer.preset.firstOrNull { it.group.group.id == bindingGroupId }?.inputSource
                }
        }
        val needsShizuku = if (source != null) {
            mode.requiresShizukuOnSource(source)
        } else {
            mode.requiresShizuku()
        }
        if (needsShizuku && !shizukuReady && !shizukuRequiredAcknowledged) {
            pendingAnalogPick = bindingGroupId to mode
        } else {
            onSetBindingGroupMode(bindingGroupId, mode)
        }
    }

    // Resolve which set is currently being viewed. The viewing pointer is user-driven (tab tap);
    // when null, fall back to the controller_profile default so the screen always renders
    // something sensible. Independent of the runtime active set.
    val viewingSet = config?.let { cfg ->
        viewingActionSetId
            ?.let { id -> cfg.actionSets.firstOrNull { it.actionSet.id == id } }
            ?: cfg.activeActionSet
    }
    val viewingLayer = viewingSet?.layers?.firstOrNull { it.layer.id == viewingLayerId }

    // Inline editor: which command (Binding) is awaiting a picker result. Survives the
    // full-screen picker round-trip.
    var editingBindingId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(pickerResult) {
        val output = pickerResult ?: return@LaunchedEffect
        editingBindingId?.let { onPickResult(it, output) }
        editingBindingId = null
        onConsumePickerResult()
    }
    val onEditCommand: (Long, BindingOutput, String) -> Unit = { bindingId, current, title ->
        editingBindingId = bindingId
        onOpenPicker(title, current)
    }

    // Walk the current config for any binding whose (source, mode) pair requires Shizuku. If one
    // exists AND Shizuku isn't ready, the banner surfaces the gap inline — covers configs whose
    // analog modes went inert after Shizuku flipped away from Granted.
    val hasAnalogModeInConfig = config?.actionSets?.any { set ->
        set.preset.any { it.group.group.mode.requiresShizukuOnSource(it.inputSource) || shizukuOutputInGroup(it.group) } ||
            set.layers.any { layer ->
                layer.preset.any { it.group.group.mode.requiresShizukuOnSource(it.inputSource) || shizukuOutputInGroup(it.group) }
            }
    } == true

    val editorCallbacks = RemapGroupEditorCallbacks(
        onSetBindingGroupMode = gatedSetBindingGroupMode,
        onOpenModeSettings = onOpenModeSettings,
        onEditCommand = onEditCommand,
        onOpenInputEditor = onOpenInputEditor,
        onClearOverride = { inputSource, groupInputKey ->
            viewingLayer?.layer?.id?.let { onClearLayerOverride(it, inputSource, groupInputKey) }
        },
        onAddInputRow = onAddInputRow,
        onSetPressType = onSetInputRowPressType,
        onSetLabel = onSetInputRowLabel,
        onDeleteRow = onDeleteInputRow,
        onDuplicateRow = onDuplicateInputRow,
        onResetRow = { bindingId ->
            // "Reset input" composed from the existing row-level ops: back to a default
            // Press with no label and no output. (Activator settings — turbo, delays —
            // keep their values until a dedicated reset op exists.)
            onSetInputRowPressType(bindingId, ActivatorType.FULL_PRESS)
            onSetInputRowLabel(bindingId, "")
            onPickResult(bindingId, BindingOutput.Unbound)
        },
        onResetGroup = onResetBindingGroup,
        onConfigure = onOpenActivatorSettings,
    )

    // Controller-focus plumbing. Focus is ALWAYS seated on a real button, never a container
    // (a focused container that spatially contains everything is a directional-search dead
    // end — and worse, d-pad moves from it search OUTWARD, past the screen into the frame
    // chrome; a focusable root Box shipped exactly that bug). The initial seat lands on the
    // top-left group box inside RemapSimpleView; this requester hands focus back to the
    // summoning corner pill when the options panel closes (the return-to-home-box pattern).
    val optionsPillFocus = remember { FocusRequester() }
    var lastOpenPanel by remember { mutableStateOf(openPanel) }
    LaunchedEffect(openPanel) {
        val closed = lastOpenPanel
        lastOpenPanel = openPanel
        if (openPanel == null && closed != null) {
            runCatching { optionsPillFocus.requestFocus() }
        }
    }

    // Root Box: the Scaffold plus the options panel overlay, which must cover the top bar —
    // hence hosted HERE rather than inside the Scaffold content. The Box also owns the
    // physical-button shortcuts (Select → back to the layouts view, its old layout-panel
    // muscle memory; Start → options, the button whose glyph the corner pill wears; B
    // closes an open panel).
    Box(
        modifier = modifier
            // Preview handlers fire along the focus path, so this is live whenever focus
            // sits anywhere in the screen subtree — which the seat-on-entry in
            // RemapSimpleView (plus its tap-recovery) keeps true.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || e.nativeKeyEvent.repeatCount != 0) {
                    return@onPreviewKeyEvent false
                }
                when (e.key) {
                    Key.ButtonSelect -> {
                        if (openPanel != null) {
                            // A panel is the topmost dismissable — close it rather than
                            // navigating away underneath it.
                            Log.d(REMAP_SCREEN_TAG, "key: Select -> close panel")
                            openPanel = null
                        } else {
                            // Layouts view when viewing; "leave Mappo" on the home.
                            Log.d(REMAP_SCREEN_TAG, "key: Select -> back")
                            onBack()
                        }
                        true
                    }
                    Key.ButtonStart -> {
                        Log.d(REMAP_SCREEN_TAG, "key: Start -> toggle options panel")
                        openPanel = if (openPanel == RemapPanel.OPTIONS) null else RemapPanel.OPTIONS
                        true
                    }
                    Key.ButtonB -> {
                        if (openPanel != null) {
                            Log.d(REMAP_SCREEN_TAG, "key: B -> close panel")
                            openPanel = null
                            true
                        } else false
                    }
                    else -> false
                }
            },
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                // While a panel is up it behaves modally: directional focus must not
                // wander into the screen content underneath it — refuse entry into this
                // whole subtree (the editor's containment pattern). Gated on INTENT so
                // the block lifts the moment a close starts and the summoning pill can
                // take the return focus.
                .then(
                    if (openPanel != null) {
                        Modifier
                            .focusProperties { onEnter = { cancelFocusChange() } }
                            .focusGroup()
                    } else Modifier,
                ),
            topBar = {
                // The 2026-08-20 bar: Auto switch (home) ↔ Back arrow (viewing), the
                // layout identity, Activate/View-layouts on the left; Set-default and
                // Layout settings on the right. The action-set tabs that lived here
                // moved into RemapSimpleView's set row.
                RemapTopBar(
                    overline = if (isActiveLayout) "Active layout" else "Viewing layout",
                    title = profileName ?: "Layout",
                    appPackage = viewedAppPackage,
                    onBack = onBack,
                    navigation = {
                        // Activating the viewed layout completes the selection flow: the
                        // Back arrow morphs into the Auto switch in place.
                        AnimatedContent(targetState = isActiveLayout, label = "topBarNav") { active ->
                            if (active) {
                                AutoSwitchStack(
                                    enabled = autoSwitchEnabled,
                                    onChange = onAutoSwitchChange,
                                )
                            } else {
                                MinputIconButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = onBack,
                                )
                            }
                        }
                    },
                    leadingActions = {
                        AnimatedVisibility(
                            visible = !isActiveLayout,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            // Gap rides inside the visibility wrapper so it animates away
                            // with the pill.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MinputPillButton(
                                    text = "Activate layout",
                                    onClick = {
                                        if (autoSwitchEnabled && !activateWarningSuppressed) {
                                            activateWarningOpen = true
                                        } else {
                                            onActivateLayout()
                                        }
                                    },
                                    leadingIcon = rememberVectorPainter(Icons.Filled.Check),
                                    leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(TopBarPillGap))
                            }
                        }
                        MinputPillButton(
                            text = "View layouts",
                            onClick = onViewLayouts,
                            leadingIcon = rememberVectorPainter(Icons.AutoMirrored.Filled.List),
                            leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    actions = {
                        if (showSetDefaultAction && viewedAppLabel != null) {
                            MinputPillButton(
                                text = "Set as ${shortChromeLabel(viewedAppLabel)} default",
                                onClick = onSetAppDefault,
                                // The application's own icon — untinted, like the bar's
                                // identity icon.
                                leadingIcon = rememberAppIconPainter(viewedAppPackage),
                            )
                            Spacer(Modifier.width(TopBarPillGap))
                        }
                        MinputPillButton(
                            text = "Layout settings",
                            onClick = {
                                openPanel =
                                    if (openPanel == RemapPanel.OPTIONS) null else RemapPanel.OPTIONS
                            },
                            leadingIcon = rememberVectorPainter(Icons.Filled.Settings),
                            leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.focusRequester(optionsPillFocus),
                        )
                    },
                )
            },
        ) { innerPadding ->
            // surface — the screen's content plane beneath the group boxes.
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (hasAnalogModeInConfig && !shizukuReady) {
                        ShizukuUnavailableBanner(onOpenSetup = onOpenShizukuSetup)
                    }
                    RemapSimpleView(
                        viewingSet = viewingSet,
                        viewingLayer = viewingLayer,
                        config = config,
                        onMap = { /* input-mapping wizard — UI-only CTA for now */ },
                        editorCallbacks = editorCallbacks,
                        onSelectActionSet = { id ->
                            onSelectActionSet(id)
                            onSelectLayer(null)
                        },
                        onAddSet = { dialog = ActionSetDialogState.Add },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        bottomContent = {
                            RemapBottomRow(
                                viewingSet = viewingSet,
                                viewingLayerSelected = viewingLayer != null,
                                onSetGyroMode = gatedSetBindingGroupMode,
                            )
                        },
                    )
                }
            }
        }

        RemapPanelOverlay(
            openPanel = openPanel,
            onClose = { openPanel = null },
            optionsEntries = optionsEntries,
            modifier = Modifier.matchParentSize(),
        )
    }

    if (activateWarningOpen) {
        ActivateLayoutWarningDialog(
            onCancel = { activateWarningOpen = false },
            onConfirm = { dontShowAgain ->
                if (dontShowAgain) onSuppressActivateWarning()
                activateWarningOpen = false
                onActivateLayout()
            },
        )
    }

    val pendingPick = pendingAnalogPick
    if (pendingPick != null) {
        com.mappo.ui.screen.dialog.ShizukuRequiredDialog(
            shizukuState = shizukuState,
            onSetUp = {
                // Apply the mode + ack + navigate to Setup. If the user bails mid-setup, the
                // binding stays — the ShizukuKeyInjector gate keeps it inert until Granted.
                onSetBindingGroupMode(pendingPick.first, pendingPick.second)
                onAcknowledgeShizukuRequired()
                pendingAnalogPick = null
                onOpenShizukuSetup()
            },
            onDismiss = { pendingAnalogPick = null },
        )
    }

    // Management dialogs. Each dialog state carries the target id, so a tab's long-press menu
    // can act on ANY set or layer.
    when (val d = dialog) {
        ActionSetDialogState.None -> Unit
        ActionSetDialogState.Add -> AddSetDialog(
            existingSets = config?.actionSets.orEmpty(),
            onConfirm = { title, inheritFromSetId ->
                onAddActionSet(title, inheritFromSetId)
                dialog = ActionSetDialogState.None
            },
            onDismiss = { dialog = ActionSetDialogState.None },
        )
        is ActionSetDialogState.Rename -> setEntityById(config, d.setId)?.let { target ->
            RenameSetDialog(
                target = target,
                onConfirm = { newTitle ->
                    onRenameActionSet(target.id, newTitle)
                    dialog = ActionSetDialogState.None
                },
                onDismiss = { dialog = ActionSetDialogState.None },
            )
        } ?: run { dialog = ActionSetDialogState.None }
        is ActionSetDialogState.Duplicate -> setEntityById(config, d.setId)?.let { source ->
            DuplicateSetDialog(
                source = source,
                onConfirm = { newTitle ->
                    onDuplicateActionSet(source.id, newTitle)
                    dialog = ActionSetDialogState.None
                },
                onDismiss = { dialog = ActionSetDialogState.None },
            )
        } ?: run { dialog = ActionSetDialogState.None }
        is ActionSetDialogState.Delete -> setEntityById(config, d.setId)?.let { target ->
            DeleteSetConfirmDialog(
                target = target,
                onConfirm = {
                    onDeleteActionSet(target.id)
                    dialog = ActionSetDialogState.None
                },
                onDismiss = { dialog = ActionSetDialogState.None },
            )
        } ?: run { dialog = ActionSetDialogState.None }
    }

    // Layer management dialogs. Same hoisting + id-targeting pattern as action sets.
    when (val d = layerDialog) {
        LayerDialogState.None -> Unit
        is LayerDialogState.Add -> setEntityById(config, d.parentSetId)?.let { parentSet ->
            AddLayerDialog(
                onConfirm = { title ->
                    onAddLayer(parentSet.id, title)
                    layerDialog = LayerDialogState.None
                },
                onDismiss = { layerDialog = LayerDialogState.None },
            )
        } ?: run { layerDialog = LayerDialogState.None }
        is LayerDialogState.Rename -> layerEntityById(config, d.layerId)?.let { target ->
            RenameLayerDialog(
                target = target,
                onConfirm = { newTitle ->
                    onRenameLayer(target.id, newTitle)
                    layerDialog = LayerDialogState.None
                },
                onDismiss = { layerDialog = LayerDialogState.None },
            )
        } ?: run { layerDialog = LayerDialogState.None }
        is LayerDialogState.Duplicate -> layerEntityById(config, d.layerId)?.let { source ->
            DuplicateLayerDialog(
                source = source,
                onConfirm = { newTitle ->
                    onDuplicateLayer(source.id, newTitle)
                    layerDialog = LayerDialogState.None
                },
                onDismiss = { layerDialog = LayerDialogState.None },
            )
        } ?: run { layerDialog = LayerDialogState.None }
        is LayerDialogState.Delete -> layerEntityById(config, d.layerId)?.let { target ->
            DeleteLayerConfirmDialog(
                target = target,
                onConfirm = {
                    onDeleteLayer(target.id)
                    layerDialog = LayerDialogState.None
                },
                onDismiss = { layerDialog = LayerDialogState.None },
            )
        } ?: run { layerDialog = LayerDialogState.None }
    }
}

/** Resolve an [com.mappo.data.model.steam.ActionSet] entity by id across the config. */
private fun setEntityById(config: ControllerConfig?, id: Long) =
    config?.actionSets?.firstOrNull { it.actionSet.id == id }?.actionSet

/** Resolve an [com.mappo.data.model.steam.ActionLayer] entity by id across all sets. */
private fun layerEntityById(config: ControllerConfig?, id: Long) =
    config?.actionSets?.firstNotNullOfOrNull { s -> s.layers.firstOrNull { it.layer.id == id }?.layer }

/** Which management dialog is currently open. Hoisted in [RemapControlsScreen]; carries the
 *  target set id so the tab menus can act on any set. */
private sealed class ActionSetDialogState {
    object None : ActionSetDialogState()
    object Add : ActionSetDialogState()
    data class Rename(val setId: Long) : ActionSetDialogState()
    data class Duplicate(val setId: Long) : ActionSetDialogState()
    data class Delete(val setId: Long) : ActionSetDialogState()
}

/** Layer-management dialog state. Parallel to [ActionSetDialogState]; Add carries the parent
 *  set id, the rest the target layer id. */
private sealed class LayerDialogState {
    object None : LayerDialogState()
    data class Add(val parentSetId: Long) : LayerDialogState()
    data class Rename(val layerId: Long) : LayerDialogState()
    data class Duplicate(val layerId: Long) : LayerDialogState()
    data class Delete(val layerId: Long) : LayerDialogState()
}

/**
 * Bottom sheet for an added mode's (mode shift's) settings. RETAINED-FOR-REUSE: mode-shift
 * editing lost its UI surface when the rail/detail advanced view was retired (2026-07-10);
 * this sheet + [TriggerInputPickerSheet] return with the wizard / group-editor mode-shift work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModeShiftSettingsSheet(
    modeShiftId: Long,
    modeBindingGroupId: Long,
    ownerSource: InputSource,
    mode: BindingMode,
    currentTriggerSource: InputSource?,
    currentTriggerSubInput: String?,
    onSetTrigger: (modeShiftId: Long, triggerSource: InputSource?, triggerSubInput: String?) -> Unit,
    onOpenModeSettings: (bindingGroupId: Long, source: InputSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingTrigger by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "Mode settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            // Activation button — what the user holds to make this added mode active.
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickingTrigger = true },
                headlineContent = { Text("Activation button", style = MaterialTheme.typography.bodyLarge) },
                supportingContent = {
                    val label = if (currentTriggerSource != null && currentTriggerSubInput != null) {
                        val match = RemapSections.TRIGGER_INPUT_CATALOG.firstOrNull {
                            it.source == currentTriggerSource && it.subInput == currentTriggerSubInput
                        }
                        match?.label ?: "${currentTriggerSource.displayName()} / $currentTriggerSubInput"
                    } else "Not assigned — the mode won't activate until you pick one"
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            // The added mode's own behavior settings (deadzones, curves…), when the mode has any.
            if (SourceModeSettingsSchema.hasSettings(ownerSource, mode)) {
                HorizontalDivider()
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss(); onOpenModeSettings(modeBindingGroupId, ownerSource) },
                    headlineContent = { Text("${mode.displayNameFor(ownerSource)} settings", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("Deadzones, curves, and other tuning for this mode.", style = MaterialTheme.typography.bodyMedium) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (currentTriggerSource != null) {
                HorizontalDivider()
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetTrigger(modeShiftId, null, null) },
                    headlineContent = {
                        Text(
                            "Clear trigger",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    if (pickingTrigger) {
        TriggerInputPickerSheet(
            currentSource = currentTriggerSource,
            currentSubInput = currentTriggerSubInput,
            onPick = { source, subInput ->
                onSetTrigger(modeShiftId, source, subInput)
                pickingTrigger = false
            },
            onDismiss = { pickingTrigger = false },
        )
    }
}

/**
 * Trigger input picker — a flat list of physical digital sub-inputs from
 * [RemapSections.TRIGGER_INPUT_CATALOG], grouped by category. Selecting a row commits via
 * [onPick] and dismisses. Retained-for-reuse alongside [ModeShiftSettingsSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerInputPickerSheet(
    currentSource: InputSource?,
    currentSubInput: String?,
    onPick: (InputSource, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pick trigger input",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            HorizontalDivider()
            val grouped = RemapSections.TRIGGER_INPUT_CATALOG.groupBy { it.groupTitle }
            for ((groupTitle, options) in grouped) {
                Text(
                    text = groupTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                for (opt in options) {
                    val isSelected = opt.source == currentSource && opt.subInput == currentSubInput
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(opt.source, opt.subInput) },
                        headlineContent = { Text(opt.label, style = MaterialTheme.typography.bodyLarge) },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        ),
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * Full-width banner under the top bar surfacing the "you have analog modes configured but
 * Shizuku isn't ready" gap. errorContainer — actionable broken state: those bindings are inert
 * until Shizuku is fixed.
 */
@Composable
private fun ShizukuUnavailableBanner(onOpenSetup: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.remap_shizuku_unavailable_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onOpenSetup,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.remap_shizuku_unavailable_banner_cta))
            }
        }
    }
}

/**
 * The bar's Auto stack — the home-state occupant of the navigation slot: overline "AUTO"
 * over the auto-detection switch (Mappo activating each foreground application's default
 * layout). The switch is halo-stripped and scaled to bar scale, the PowerRow treatment.
 */
@Composable
private fun AutoSwitchStack(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MinputBarStackGap),
    ) {
        Text(
            text = "Auto".uppercase(),
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                modifier = Modifier.scaledLayout(AutoSwitchScale),
            )
        }
    }
}

/**
 * The activate-layout warning ([MinputDialog]): manual activation turns auto detection
 * off. Action row per spec — "Don't show again" checkbox, Cancel, then the filled
 * Activate layout commit.
 */
@Composable
private fun ActivateLayoutWarningDialog(
    onCancel: () -> Unit,
    onConfirm: (dontShowAgain: Boolean) -> Unit,
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    MinputDialog(onDismissRequest = onCancel) {
        Text(
            text = "Activate layout".uppercase(),
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Activating a layout manually turns off auto detection. " +
                "You can re-enable it anytime with the Auto switch in the top-left corner.",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Settings-row contract: the row owns the toggle; the checkbox itself is
            // display-only (onCheckedChange = null) with its 48dp halo stripped.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { dontShowAgain = !dontShowAgain }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = null,
                        modifier = Modifier.scaledLayout(WarningCheckboxScale),
                    )
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                Text(
                    text = "Don't show again",
                    style = minputMiniTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            MinputPillButton(text = "Cancel", onClick = onCancel)
            Spacer(Modifier.width(TopBarPillGap))
            MinputPillButton(
                text = "Activate layout",
                onClick = { onConfirm(dontShowAgain) },
                filled = true,
                elevated = true,
            )
        }
    }
}

/**
 * Chrome cap for an application label interpolated into a pill's text ("Set as X
 * default") — pill text is a plain string, so the NameableText width-cap rule is applied
 * at the character level instead.
 */
private fun shortChromeLabel(label: String, max: Int = 14): String =
    if (label.length <= max) label else label.take(max - 1).trimEnd() + "…"

/** Gap between adjacent pills in the top bar (the filter rows' 6dp rhythm). */
private val TopBarPillGap = 6.dp

/** Bar-scale factor for the Auto switch (fits under its overline in [MinputBarHeight]). */
private const val AutoSwitchScale = 0.35f

/** Scale for the warning dialog's halo-stripped checkbox. */
private const val WarningCheckboxScale = 0.75f
