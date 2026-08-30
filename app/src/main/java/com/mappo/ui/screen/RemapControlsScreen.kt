package com.mappo.ui.screen

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.mappo.R
import com.mappo.data.model.Layout
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
import com.mappo.ui.minput.MinputDialog
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputModal
import com.mappo.ui.minput.MinputMorphCorner
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import com.mappo.ui.screen.remap.AddLayoutModalContent
import com.mappo.ui.screen.remap.AddLayoutModalHeight
import com.mappo.ui.screen.remap.LayoutsDrawerPane
import com.mappo.ui.screen.remap.RemapBottomRow
import com.mappo.ui.screen.remap.RemapGroupEditorCallbacks
import com.mappo.ui.screen.remap.RemapOptionEntry
import com.mappo.ui.screen.remap.RemapPanel
import com.mappo.ui.screen.remap.RemapPanelOverlay
import com.mappo.ui.screen.remap.RemapSections
import com.mappo.ui.screen.remap.RemapSimpleView
import com.mappo.ui.screen.remap.RemapControlsTopBar
import com.mappo.ui.screen.remap.settings.SourceModeSettingsSchema
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
    layoutName: String? = null,
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
    viewedAppPackage: String? = null,
    // ── Layout settings panel (physical Start) ─────────────────────────────────────
    optionsEntries: List<RemapOptionEntry> = emptyList(),
    // The bar's Edit overlay button (2026-08-29): enters live overlay editing, which
    // returns HERE on exit (see OverlayLiveEditController.requestEdit).
    onEditOverlay: () -> Unit = {},
    // ── 2026-08-20 flow re-imagining: viewing vs active ────────────────────────────
    // True when the layout on screen IS the runtime-active layout (the home state);
    // false = a layout under inspection — the bar grows the Activate pill.
    isActiveLayout: Boolean = true,
    autoSwitchEnabled: Boolean = false,
    onAutoSwitchChange: (Boolean) -> Unit = {},
    onActivateLayout: () -> Unit = {},
    // Sticky "Don't show again" on the activate warning (activating turns auto off).
    activateWarningSuppressed: Boolean = false,
    onSuppressActivateWarning: () -> Unit = {},
    // ── Layouts drawer (2026-08-21: the layouts view is a push pane in this screen;
    // the bar's change button slides it in) ────────────────────────────────────────
    layouts: ImmutableList<Layout> = persistentListOf(),
    activeLayoutId: Long? = null,
    onPreviewLayout: (Long) -> Unit = {},
    onActivateLayoutCard: (Layout) -> Unit = {},
    // Fired when the LAST open drawer finishes its close animation — the caller reverts
    // the controls view to the active layout/application (2026-08-25: at animation END,
    // not close intent, so the sliding-away drawer's content stays scoped — reverting
    // early re-filtered the still-visible list, the close-flash bug).
    onDrawersClosed: () -> Unit = {},
    // ── Applications drawer (2026-08-25: the layouts drawer's right-side mirror) ────
    // Previews the application's ACTIVE layout in the controls view (the caller resolves
    // the app's binding to a layout id).
    onPreviewApplication: (String) -> Unit = {},
    // ── New-layout flow (2026-08-25: the drawer's "+ New layout" card summons the same
    // ADD modal the dormant layouts view hosts) ────────────────────────────────────
    installedApps: List<com.mappo.data.repository.InstalledAppsRepository.InstalledApp> = emptyList(),
    onLoadInstalledApps: () -> Unit = {},
    appBindings: Map<String, Long> = emptyMap(),
    onCreateLayout: (name: String, packageName: String?) -> Unit = { _, _ -> },
    // ── Active application (2026-08-26: first-class, layout-independent — an app with
    // no layouts can be the current application, its home = the no-layout state) ───
    activeAppPackage: String? = null,
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
    // The activate warning gate (2026-08-25 refinement): the dialog shows only for
    // CROSS-application selections — picking an app (or a layout belonging to an app)
    // other than the auto-detected one is what conflicts with auto detection. Same-app
    // layout switches just repoint the app's functional default, so they commit silently
    // (and keep auto detection on — see MainViewModel.activateLayoutManually). All
    // activation paths (the bar pill, either drawer's cards) stash their commit here and
    // share the dialog. The auto-detected application = the active layout's parent.
    val detectedAppPackage = activeAppPackage
        ?: layouts.firstOrNull { it.id == activeLayoutId }?.packageName
    var pendingActivate by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requestActivate: (targetPackage: String?, commit: () -> Unit) -> Unit =
        { targetPackage, commit ->
            val crossApp = targetPackage != detectedAppPackage
            if (autoSwitchEnabled && !activateWarningSuppressed && crossApp) {
                pendingActivate = commit
            } else commit()
        }
    // The new-layout modal (the drawer's "+ New layout" card). Plain remember — the form
    // content resets on close by design (see AddLayoutModalContent).
    var addLayoutOpen by remember { mutableStateOf(false) }
    val addModalCloseFocus = remember { FocusRequester() }
    // The layouts drawer (the bar's corner button). Survives the sub-editor round-trips.
    // (2026-08-27: the right-side applications drawer retired — application selection is
    // the layouts drawer's APPLICATIONS MODE, see LayoutsDrawerPane.)
    var layoutsDrawerOpen by rememberSaveable { mutableStateOf(false) }
    // The application the user is browsing via the drawer's applications mode —
    // overrides the viewed layout's own application for the drawer's Applications
    // button, the layouts scope, and new-layout association. Cleared when the drawer
    // finishes closing.
    var viewingAppOverride by rememberSaveable { mutableStateOf<String?>(null) }
    // Home (viewing the active layout) shows the ACTIVE APPLICATION — which may have no
    // layouts at all; a previewed layout shows its own parent application.
    val effectiveAppPackage = viewingAppOverride
        ?: (if (isActiveLayout) detectedAppPackage ?: viewedAppPackage else viewedAppPackage)
    // The viewed application has no layout — the ACTIVE app fresh from detection (the
    // core flow: shortcut-open Mappo over a new game), an apps-drawer preview/pick of an
    // unbound app, or one whose bound layout was deleted. Drives both the content
    // plane's no-layout state and the bar's truthful "None" layout title.
    val effectiveAppHasNoLayout = effectiveAppPackage != null &&
        effectiveAppPackage.let { pkg ->
            appBindings[pkg]?.let { id -> layouts.firstOrNull { it.id == id } }
        } == null
    // The viewed application's display label (launcher label, package-name fallback) —
    // feeds the bar's "<application> layout" overline and the no-layout state.
    val effectiveAppLabel = installedApps
        .firstOrNull { it.packageName == effectiveAppPackage }?.label
        ?: effectiveAppPackage
    // Revert fires when the drawer's close animation completes (the guard covers a
    // reopen racing the animation): the viewing context returns to the active
    // application — which also resets the drawer's Applications button for next open.
    val onDrawerFullyClosed = {
        if (!layoutsDrawerOpen) {
            viewingAppOverride = null
            onDrawersClosed()
        }
    }
    // Back dismisses the drawer whole — apps mode and all (registered after the screen's
    // base BackHandler, before the group editor/panels, which compose later and win).
    BackHandler(enabled = layoutsDrawerOpen) {
        layoutsDrawerOpen = false
    }
    // The device app list loads at entry — the bar's application widget needs labels
    // (and the applications drawer its cards) from the first frame.
    LaunchedEffect(Unit) { onLoadInstalledApps() }

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
    // top-left group box inside RemapSimpleView. (The options panel's return-to-summoning-
    // pill focus hand-back retired 2026-08-27 with the bar's Layout settings pill — the
    // panel is Start-key-only until Edit Overlay and friends get their new home.)

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
                        if (addLayoutOpen) {
                            // The modal is the topmost dismissable while open.
                            Log.d(REMAP_SCREEN_TAG, "key: Select -> close new-layout modal")
                            addLayoutOpen = false
                        } else if (openPanel != null) {
                            // A panel is the topmost dismissable — close it rather than
                            // navigating away underneath it.
                            Log.d(REMAP_SCREEN_TAG, "key: Select -> close panel")
                            openPanel = null
                        } else if (layoutsDrawerOpen) {
                            // Mirrors back: one press dismisses the drawer whole.
                            Log.d(REMAP_SCREEN_TAG, "key: Select -> close drawer")
                            layoutsDrawerOpen = false
                        } else {
                            // "Leave Mappo" on the home.
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
                // wander into the screen underneath it (bar, drawer, and content alike)
                // — refuse entry into this whole subtree (the editor's containment
                // pattern). Gated on INTENT so the block lifts the moment a close starts
                // and the summoning pill can take the return focus.
                .then(
                    if (openPanel != null) {
                        Modifier
                            .focusProperties { onEnter = { cancelFocusChange() } }
                            .focusGroup()
                    } else Modifier,
                ),
            // The bar's ground matches the content plane beneath it: the redesigned
            // top bar paints no strip of its own, so a Scaffold container in the
            // default `background` role would band across the top.
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                // The 2026-08-29 bar: transparent, with each cluster on its own pill pod
                // (see RemapControlsTopBar) — the identity pill (the layouts drawer's
                // summon) at the start, the action-set switcher centered (up out of
                // RemapSimpleView's content column), Auto-detect + Edit overlay at the
                // end. Edit overlay is the options panel's lone entry given a real home
                // (the panel stays on the Start key).
                RemapControlsTopBar(
                    // The layout being viewed, "(Preview)"-suffixed while inspecting a
                    // non-active one; an application with no layout reads "None" — not
                    // the stale name of another app's layout (2026-08-26 audit). The
                    // application itself is carried by the leading launcher icon since
                    // the two-line identity stack collapsed into a pill (2026-08-29).
                    layoutLabel = buildString {
                        append(if (effectiveAppHasNoLayout) "None" else layoutName ?: "Layout")
                        if (!isActiveLayout) append(" (Preview)")
                    },
                    appPackage = effectiveAppPackage,
                    identityHighlighted = layoutsDrawerOpen,
                    onIdentityClick = { layoutsDrawerOpen = !layoutsDrawerOpen },
                    config = config,
                    viewingSet = viewingSet,
                    onSelectActionSet = { id ->
                        onSelectActionSet(id)
                        onSelectLayer(null)
                    },
                    onAddSet = { dialog = ActionSetDialogState.Add },
                    autoDetectEnabled = autoSwitchEnabled,
                    onAutoDetectChange = onAutoSwitchChange,
                    onEditOverlay = onEditOverlay,
                )
            },
        ) { innerPadding ->
            // The drawer opens BETWEEN the bars (2026-08-24): the full-width top bar sits
            // above it, the frame's bottom bar below. It PUSHES the controls content (its
            // Row neighbor) rather than overlaying it — both stay interactive, and the
            // content live-previews the card the drawer is scrolled to.
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                LayoutsDrawerPane(
                    open = layoutsDrawerOpen,
                    appPackage = effectiveAppPackage,
                    apps = installedApps,
                    activeAppPackage = detectedAppPackage,
                    layouts = layouts,
                    activeLayoutId = activeLayoutId,
                    onPreviewLayout = onPreviewLayout,
                    onActivateLayout = { layout ->
                        requestActivate(layout.packageName) { onActivateLayoutCard(layout) }
                    },
                    onNewLayout = { addLayoutOpen = true },
                    // Both focus-preview and tap-select in applications mode are VIEWING
                    // moves only (2026-08-27): they repoint the browsing context (and the
                    // controls view behind), never the active application — auto
                    // detection only turns off when a non-active app's LAYOUT is
                    // actually activated (requestActivate above).
                    onPreviewApplication = { pkg ->
                        viewingAppOverride = pkg
                        onPreviewApplication(pkg)
                    },
                    onSelectApplication = { app ->
                        viewingAppOverride = app.packageName
                        onPreviewApplication(app.packageName)
                    },
                    onFullyClosed = onDrawerFullyClosed,
                )
                // surface — the screen's content plane beneath the group boxes.
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    // The viewed application has no layout — the ACTIVE app fresh from
                    // detection (the core flow: shortcut-open Mappo over a new game), an
                    // apps-drawer preview/pick of an unbound app, or one whose bound
                    // layout was deleted: the content plane shows the no-layout state
                    // instead of another app's controls — tiles route into layout
                    // creation and the layouts drawer (which holds the Community section
                    // once sharing lands).
                    if (effectiveAppHasNoLayout) {
                        NoLayoutAssignedView(
                            appLabel = effectiveAppLabel.orEmpty(),
                            onCreateLayout = { addLayoutOpen = true },
                            onBrowseLayouts = { layoutsDrawerOpen = true },
                        )
                    } else Column(modifier = Modifier.fillMaxSize()) {
                        if (hasAnalogModeInConfig && !shizukuReady) {
                            ShizukuUnavailableBanner(onOpenSetup = onOpenShizukuSetup)
                        }
                        RemapSimpleView(
                            viewingSet = viewingSet,
                            viewingLayer = viewingLayer,
                            config = config,
                            onMap = { /* input-mapping wizard — UI-only CTA for now */ },
                            editorCallbacks = editorCallbacks,
                            // While the drawer is open, controller focus lives on its
                            // cards; browsing can remount this view (no-layout ↔ controls
                            // flip), and an ungated entry-seat stole focus from the drawer.
                            focusSeatEnabled = !layoutsDrawerOpen,
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
        }

        RemapPanelOverlay(
            openPanel = openPanel,
            onClose = { openPanel = null },
            optionsEntries = optionsEntries,
            modifier = Modifier.matchParentSize(),
        )

        // The new-layout modal — the SAME form the dormant layouts view hosts, summoned
        // by the drawer's "+ New layout" card. Composed last so its BackHandler wins.
        MinputModal(
            open = addLayoutOpen,
            onDismiss = { addLayoutOpen = false },
            height = AddLayoutModalHeight,
            focusSeat = addModalCloseFocus,
            testTag = "controls-modal:ADD",
            modifier = Modifier.matchParentSize(),
        ) {
            AddLayoutModalContent(
                // The layout's ONE application = the viewed application context (the
                // apps-drawer override wins while browsing); null = unassigned.
                applicationLabel = effectiveAppPackage?.let { pkg ->
                    installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                },
                onCreate = { name -> onCreateLayout(name, effectiveAppPackage) },
                onClose = { addLayoutOpen = false },
                closeFocusRequester = addModalCloseFocus,
            )
        }
    }

    pendingActivate?.let { commit ->
        ActivateLayoutWarningDialog(
            onCancel = { pendingActivate = null },
            onConfirm = { dontShowAgain ->
                if (dontShowAgain) onSuppressActivateWarning()
                pendingActivate = null
                commit()
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
 * The content plane's empty state for an application with no layout (2026-08-25): shown
 * while the apps drawer previews or picks an app whose binding resolves to nothing.
 * Two tile routes out: create a layout (the "+ New layout" modal, pre-associated with
 * the app) or browse the layouts drawer (whose Community section carries shared layouts
 * once sharing lands — later this tile may gate on Mappo-server reachability).
 */
@Composable
private fun NoLayoutAssignedView(
    appLabel: String,
    onCreateLayout: () -> Unit,
    onBrowseLayouts: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No layout assigned for $appLabel",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = NoLayoutMessagePadding),
            )
            Spacer(Modifier.height(NoLayoutMessageTileGap))
            Row(horizontalArrangement = Arrangement.spacedBy(NoLayoutTileGap)) {
                NoLayoutTile(
                    icon = Lucide.Plus,
                    label = "Create a layout",
                    onClick = onCreateLayout,
                )
                NoLayoutTile(
                    // The change button's glyph — this tile IS "change layout" for an
                    // app that has none yet.
                    icon = Lucide.ArrowLeftRight,
                    label = "Browse layouts",
                    onClick = onBrowseLayouts,
                )
            }
        }
    }
}

/** One route tile of [NoLayoutAssignedView]: glyph over label on the card chrome. */
@Composable
private fun NoLayoutTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val container = minputBoxContainer()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(MinputMorphCorner),
        color = container,
        border = minputBevelBorder(container, MinputMorphCorner),
        modifier = Modifier
            .minputInteractiveMotion(interaction)
            .clip(RoundedCornerShape(MinputMorphCorner))
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                onClick = onClick,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.size(NoLayoutTileWidth, NoLayoutTileHeight),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(NoLayoutTileIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MinputGlyphLabelGap))
            Text(
                text = label,
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurface,
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
                "You can re-enable it anytime with the Auto-detect switch in the top-right corner.",
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

/** Gap between adjacent pills in the top bar (the filter rows' 6dp rhythm). */
private val TopBarPillGap = 6.dp

/** No-layout state: route-tile footprint (glyph over label with air around both). */
private val NoLayoutTileWidth = 132.dp
private val NoLayoutTileHeight = 76.dp

/** No-layout state: gap between the two route tiles. */
private val NoLayoutTileGap = 8.dp

/** No-layout state: glyph edge inside a route tile. */
private val NoLayoutTileIconSize = 16.dp

/** No-layout state: air between the message line and the tile row. */
private val NoLayoutMessageTileGap = 12.dp

/** No-layout state: side padding keeping a long app name off the pane edges. */
private val NoLayoutMessagePadding = 16.dp

/** Scale for the warning dialog's halo-stripped checkbox. */
private const val WarningCheckboxScale = 0.75f

/** Scale for the bar's halo-stripped Auto switch (M3's 52×32 shrunk well under the bar
 *  height — Dylan sized it down from 0.8, 2026-08-26). */
