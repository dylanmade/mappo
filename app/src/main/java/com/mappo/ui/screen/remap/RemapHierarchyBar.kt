package com.mappo.ui.screen.remap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.AppWindow
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.SendToBack
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.mappo.data.model.Layout
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ActionSetKind
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.data.settings.BarPin
import com.mappo.ui.component.rememberAppIconBitmap
import com.mappo.ui.icons.PinSpot
import com.mappo.ui.minput.MinputBreadcrumbJoin
import com.mappo.ui.minput.MinputBreadcrumbJoinDefault
import com.mappo.ui.minput.MinputBreadcrumbMode
import com.mappo.ui.minput.MinputBreadcrumbPosition
import com.mappo.ui.minput.MinputBreadcrumbRow
import com.mappo.ui.minput.MinputBreadcrumbSegment
import com.mappo.ui.minput.MinputBreadcrumbSize
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputDropdownMenu
import com.mappo.ui.minput.MinputDropdownPanel
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIcon
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputIconSize
import com.mappo.ui.minput.MinputSegmentGap
import com.mappo.ui.minput.MinputSize
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputMiniTextStyle
import kotlinx.collections.immutable.ImmutableList

/**
 * **The editors' hierarchy bar** (Dylan, 2026-10-07) — replaced the full-width top bar, the
 * layouts drawer (and its applications mode), the layout-set switch + kebab, and the
 * physical/virtual editor switch with ONE compact group that floats at a pinned spot on the
 * screen.
 *
 * Anatomy, an M3 button group's: a [MinputBreadcrumbRow] of four joined crumbs reading the
 * hierarchy left to right — **Application › Layout › Buttons › Button set** (2026-10-12: the set
 * moved under the buttons when physical and virtual buttons got sets of their own — see
 * [ActionSetKind]) — each an overline over the current value; then, as its own circular button,
 * the PIN button choosing which of six spots the bar sits in ([BarPin]). Every crumb drops a
 * [MinputDropdownPanel] holding that level's picker:
 *  - Application — search · sort over the applications list (was the drawer's applications mode);
 *  - Layout — search · sort over the layouts list with "+ New layout" (was the drawer's body);
 *  - Buttons — Physical / Virtual (was the editor switch);
 *  - Button set — "+ New button set" over the [editor]'s kind of sets (was the group-button
 *    switch + kebab).
 *
 * The crumb whose panel is open wears the highlight plane. Which crumb is open is the CALLER's
 * state ([openCrumb]) because closing one is not a single thing: [onDismissCrumb] is a close with
 * no choice made (the caller reverts any focus-previews the panel caused), [onCommitCrumb] a close
 * that keeps them. Rows preview on FOCUS and commit on TAP, as the drawer's did.
 *
 * Editor-agnostic on purpose: it carries no screen state of its own beyond the pin menu, so the
 * virtual (overlay) editor can mount the same bar with [editor] = VIRTUAL.
 */
@Composable
internal fun RemapHierarchyBar(
    openCrumb: HierarchyCrumb?,
    onOpenCrumb: (HierarchyCrumb) -> Unit,
    onDismissCrumb: () -> Unit,
    onCommitCrumb: () -> Unit,
    // ── Application ──
    appPackage: String?,
    appLabel: String?,
    apps: List<InstalledApp>,
    activeAppPackage: String?,
    onPreviewApplication: (String) -> Unit,
    // ── Layout ──
    layoutTitle: String,
    /** True while the layout on screen is only being PREVIEWED — it is not the active one. */
    previewing: Boolean,
    layouts: ImmutableList<Layout>,
    activeLayoutId: Long?,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Layout) -> Unit,
    onNewLayout: () -> Unit,
    // ── Buttons ──
    editor: EditorKind,
    onSelectEditor: (EditorKind) -> Unit,
    // ── Button set — the [editor]'s kind ──
    config: ControllerConfig?,
    viewingSet: ActionSetGraph?,
    onSelectSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    // ── Pin ──
    pin: BarPin,
    onPinChange: (BarPin) -> Unit,
    modifier: Modifier = Modifier,
    // ── Presentation (2026-10-07: tunable while the design settles) ──
    /** The crumbs' and pin button's control variant. */
    size: MinputSize = MinputBreadcrumbSize,
    /** Whether the crumbs carry their small-caps level labels over the titles. */
    showOverlines: Boolean = true,
    mode: MinputBreadcrumbMode = MinputBreadcrumbMode.Expanded,
    join: MinputBreadcrumbJoin = MinputBreadcrumbJoinDefault,
) {
    val crumbs = HierarchyCrumb.entries
    fun toggle(crumb: HierarchyCrumb) {
        if (openCrumb == crumb) onDismissCrumb() else onOpenCrumb(crumb)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MinputSegmentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MinputBreadcrumbRow(size = size, join = join, mode = mode) {
            crumbs.forEachIndexed { index, crumb ->
                val position = MinputBreadcrumbPosition.of(index, crumbs.size)
                // Each crumb is its panel's anchor, so the two share a Box.
                Box {
                    when (crumb) {
                        HierarchyCrumb.APPLICATION -> {
                            MinputBreadcrumbSegment(
                                position = position,
                                overline = "Application".takeIf { showOverlines },
                                title = appLabel ?: "None",
                                selected = openCrumb == crumb,
                                onClick = { toggle(crumb) },
                                onClickLabel = "Choose application",
                                icon = { iconSize -> CrumbAppIcon(appPackage, iconSize) },
                                modifier = Modifier.testTag("bar:application"),
                            )
                            MinputDropdownPanel(
                                expanded = openCrumb == crumb,
                                onDismissRequest = onDismissCrumb,
                            ) {
                                ApplicationPanel(
                                    apps = apps,
                                    activeAppPackage = activeAppPackage,
                                    onPreviewApplication = onPreviewApplication,
                                    onSelectApplication = { app ->
                                        onPreviewApplication(app.packageName)
                                        onCommitCrumb()
                                    },
                                )
                            }
                        }
                        HierarchyCrumb.LAYOUT -> {
                            MinputBreadcrumbSegment(
                                position = position,
                                // A non-active layout on screen says so where it is named.
                                overline = (if (previewing) "Layout · preview" else "Layout")
                                    .takeIf { showOverlines },
                                title = layoutTitle,
                                icon = { iconSize -> CrumbIcon(Icons.Outlined.SpaceDashboard, iconSize) },
                                selected = openCrumb == crumb,
                                onClick = { toggle(crumb) },
                                onClickLabel = "Choose layout",
                                modifier = Modifier.testTag("bar:layout"),
                            )
                            MinputDropdownPanel(
                                expanded = openCrumb == crumb,
                                onDismissRequest = onDismissCrumb,
                            ) {
                                LayoutPanel(
                                    appPackage = appPackage,
                                    layouts = layouts,
                                    activeLayoutId = activeLayoutId,
                                    onPreviewLayout = onPreviewLayout,
                                    onActivateLayout = onActivateLayout,
                                    onNewLayout = onNewLayout,
                                )
                            }
                        }
                        HierarchyCrumb.SET -> {
                            MinputBreadcrumbSegment(
                                position = position,
                                overline = "Button set".takeIf { showOverlines },
                                icon = { iconSize -> CrumbIcon(Lucide.SendToBack, iconSize) },
                                title = viewingSet?.actionSet?.title
                                    ?: config?.setsOf(editor.setKind)?.firstOrNull()?.actionSet?.title
                                    ?: "None",
                                selected = openCrumb == crumb,
                                onClick = { toggle(crumb) },
                                onClickLabel = "Choose button set",
                                modifier = Modifier.testTag("bar:set"),
                            )
                            MinputDropdownPanel(
                                expanded = openCrumb == crumb,
                                onDismissRequest = onDismissCrumb,
                                width = NarrowPanelWidth,
                            ) {
                                SetPanel(
                                    sets = config?.setsOf(editor.setKind),
                                    viewingSet = viewingSet,
                                    onPreviewSet = onSelectSet,
                                    onSelectSet = { id ->
                                        onSelectSet(id)
                                        onCommitCrumb()
                                    },
                                    onAddSet = {
                                        onCommitCrumb()
                                        onAddSet()
                                    },
                                )
                            }
                        }
                        HierarchyCrumb.BUTTONS -> {
                            MinputBreadcrumbSegment(
                                position = position,
                                overline = "Buttons".takeIf { showOverlines },
                                title = editor.label,
                                icon = { iconSize -> CrumbIcon(editor.icon, iconSize) },
                                selected = openCrumb == crumb,
                                onClick = { toggle(crumb) },
                                onClickLabel = "Choose editor",
                                modifier = Modifier.testTag("bar:buttons"),
                            )
                            MinputDropdownPanel(
                                expanded = openCrumb == crumb,
                                onDismissRequest = onDismissCrumb,
                                width = NarrowPanelWidth,
                            ) {
                                EditorPanel(
                                    current = editor,
                                    onSelect = { kind ->
                                        onCommitCrumb()
                                        if (kind != editor) onSelectEditor(kind)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        BarPinButton(pin = pin, onPinChange = onPinChange, size = size)
    }
}

/** The bar's crumbs, in hierarchy order — the order they read in the bar. */
internal enum class HierarchyCrumb { APPLICATION, LAYOUT, BUTTONS, SET }

/** The two editors the Buttons crumb switches between. Virtual is Material's (outlined) Layers —
 *  two stacked shapes, where Lucide's has three (Dylan, 2026-10-12). */
internal enum class EditorKind(val label: String, val icon: ImageVector, val setKind: ActionSetKind) {
    PHYSICAL("Physical", Lucide.Gamepad2, ActionSetKind.PHYSICAL),
    VIRTUAL("Virtual", Icons.Outlined.Layers, ActionSetKind.VIRTUAL),
}

/** A crumb's concept glyph at the size the crumb hands it (the variant's glyph size). */
@Composable
private fun CrumbIcon(icon: ImageVector, size: MinputIconSize) {
    MinputIcon(icon, contentDescription = null, size = size)
}

/** The viewed application's launcher icon, cropped to its opaque pixels and sized like the
 *  other crumbs' glyphs (the bitmap [MinputIcon]) — or a generic app glyph when there is no
 *  application or no icon. */
@Composable
private fun CrumbAppIcon(appPackage: String?, size: MinputIconSize) {
    val icon = rememberAppIconBitmap(appPackage)
    if (icon != null) {
        MinputIcon(icon, contentDescription = null, size = size)
    } else {
        CrumbIcon(Lucide.AppWindow, size)
    }
}

// ── The crumbs' panels ────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.ApplicationPanel(
    apps: List<InstalledApp>,
    activeAppPackage: String?,
    onPreviewApplication: (String) -> Unit,
    onSelectApplication: (InstalledApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ApplicationSort.RECENT) }
    SearchSortRow(
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search apps",
        sortDescription = "Sort applications",
        sort = sort,
        sortOptions = ApplicationSort.entries,
        sortLabel = { it.label },
        onSort = { sort = it },
    )
    Spacer(Modifier.height(DrawerControlGap))
    ApplicationsList(
        apps = apps,
        activeAppPackage = activeAppPackage,
        query = query,
        sort = sort,
        onPreviewApplication = onPreviewApplication,
        onSelectApplication = onSelectApplication,
        // Bounded by the panel's height cap; the list scrolls inside it.
        modifier = Modifier.weight(1f, fill = false),
    )
}

@Composable
private fun ColumnScope.LayoutPanel(
    appPackage: String?,
    layouts: ImmutableList<Layout>,
    activeLayoutId: Long?,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Layout) -> Unit,
    onNewLayout: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LayoutSort.RECENT) }
    SearchSortRow(
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search layouts",
        sortDescription = "Sort layouts",
        sort = sort,
        sortOptions = LayoutSort.entries,
        sortLabel = { it.label },
        onSort = { sort = it },
    )
    Spacer(Modifier.height(DrawerControlGap))
    LayoutsList(
        appPackage = appPackage,
        layouts = layouts,
        activeLayoutId = activeLayoutId,
        query = query,
        sort = sort,
        onPreviewLayout = onPreviewLayout,
        onActivateLayout = onActivateLayout,
        onNewLayout = onNewLayout,
        modifier = Modifier.weight(1f, fill = false),
    )
}

/**
 * Search · sort, heading the application and layout panels — the drawer's control row. The
 * search pill takes controller focus when the panel opens, so the d-pad starts above the list
 * rather than on a row (focusing a row PREVIEWS it).
 */
@Composable
private fun <T> SearchSortRow(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    sortDescription: String,
    sort: T,
    sortOptions: List<T>,
    sortLabel: (T) -> String,
    onSort: (T) -> Unit,
) {
    val seat = rememberControllerFocusSeat()
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DrawerControlGap),
    ) {
        MinputTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            leadingIcon = Lucide.Search,
            clearable = true,
            // Top of a panel hanging off the bar — the sanctioned modal-less variant.
            inlineEdit = true,
            modifier = Modifier.weight(1f).focusRequester(seat),
        )
        Box {
            MinputIconButton(
                icon = Lucide.ArrowUpDown,
                contentDescription = sortDescription,
                onClick = { sortMenuOpen = true },
            )
            MinputDropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
                current = sort,
                options = sortOptions,
                optionLabel = sortLabel,
                onPick = onSort,
            )
        }
    }
}

/**
 * The editor's kind of sets: "+ New button set" first (as "+ New layout" heads the layouts), then
 * a row per set — the viewed set on the highlight plane. Focus previews a set, tap keeps it.
 * [sets] null = no config yet (no "new" row either).
 */
@Composable
private fun ColumnScope.SetPanel(
    sets: List<ActionSetGraph>?,
    viewingSet: ActionSetGraph?,
    onPreviewSet: (Long) -> Unit,
    onSelectSet: (Long) -> Unit,
    onAddSet: () -> Unit,
) {
    val hasConfig = sets != null
    val rows = sets.orEmpty()
    val viewingId = viewingSet?.actionSet?.id ?: rows.firstOrNull()?.actionSet?.id
    val seat = rememberControllerFocusSeat()
    Column(
        verticalArrangement = Arrangement.spacedBy(DrawerRowGap),
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
    ) {
        if (hasConfig) {
            PanelActionRow(label = "New button set", onClick = onAddSet)
        }
        rows.forEach { set ->
            val id = set.actionSet.id
            PanelChoiceRow(
                label = set.actionSet.title,
                active = id == viewingId,
                onFocus = { if (id != viewingId) onPreviewSet(id) },
                onClick = { onSelectSet(id) },
                modifier = if (id == viewingId) Modifier.focusRequester(seat) else Modifier,
            )
        }
        if (rows.isEmpty()) {
            Text(
                text = "No button sets",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(CardPadding),
            )
        }
    }
}

/** Physical / virtual — the two editors, the current one on the highlight plane. Picking is a
 *  tap only: opening the virtual editor leaves this screen, which a focus-preview must not do. */
@Composable
private fun EditorPanel(
    current: EditorKind,
    onSelect: (EditorKind) -> Unit,
) {
    val seat = rememberControllerFocusSeat()
    Column(verticalArrangement = Arrangement.spacedBy(DrawerRowGap)) {
        EditorKind.entries.forEach { kind ->
            PanelChoiceRow(
                label = kind.label,
                icon = kind.icon,
                active = kind == current,
                onClick = { onSelect(kind) },
                modifier = if (kind == current) Modifier.focusRequester(seat) else Modifier,
            )
        }
    }
}

/** One choice in a panel: a borderless [drawerRow] with an optional leading glyph. */
@Composable
private fun PanelChoiceRow(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSize: MinputIconSize = MinputSize.Standard.iconSize,
    onFocus: () -> Unit = {},
) {
    val content = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .drawerRow(active = active, onClickLabel = label, onClick = onClick, onFocus = onFocus)
            .padding(CardPadding),
    ) {
        if (icon != null) {
            // A slot, so labels align past glyphs of different shapes.
            MinputIcon(icon, contentDescription = null, size = iconSize, tint = content, slot = true)
            Spacer(Modifier.width(MinputGlyphLabelGap))
        }
        Text(
            text = label,
            style = minputMiniTextStyle(),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A verb heading a panel's list — the "+ New layout" row's anatomy. */
@Composable
private fun PanelActionRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .drawerRow(active = false, onClickLabel = label, onClick = onClick)
            .padding(CardPadding),
    ) {
        MinputIcon(
            Lucide.Plus,
            contentDescription = null,
            size = MinputSize.Standard.iconSize,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = label,
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A focus seat for a panel opened from the CONTROLLER: requested once as the panel composes, and
 * only in keyboard (d-pad) input mode — a touch-opened panel shouldn't show a focus ring nobody
 * asked for. Attach it to the control the d-pad should start on.
 */
@Composable
private fun rememberControllerFocusSeat(): FocusRequester {
    val requester = remember { FocusRequester() }
    val keyboardMode = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(Unit) {
        if (keyboardMode) runCatching { requester.requestFocus() }
    }
    return requester
}

// ── The pin button ────────────────────────────────────────────────────────────────────────────

/**
 * Where the bar sits: a circular button wearing the CURRENT spot's border glyph, opening the six
 * spots as a single-choice menu. Its own button after the crumbs — the M3 button group's
 * convention for a control that is not part of the group's one silhouette.
 */
@Composable
private fun BarPinButton(pin: BarPin, onPinChange: (BarPin) -> Unit, size: MinputSize) {
    var open by remember { mutableStateOf(false) }
    Box {
        MinputButton(
            leadingIcon = pin.icon(),
            contentDescription = "Bar position: ${pin.label}",
            highlighted = open,
            size = size,
            // The grid is solid and square-inked — more mass than the crumbs' stroked glyphs at
            // the same size — so it sits one step under them.
            iconSize = size.iconSize.stepDown(),
            onClick = { open = !open },
            modifier = Modifier.testTag("bar:pin"),
        )
        MinputDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            current = pin,
            options = BarPin.entries,
            optionLabel = { it.label },
            onPick = onPinChange,
            optionIcon = { it.icon() },
        )
    }
}

/** The spot's glyph: the dot grid with the spot drawn as a line ([PinSpot], 2026-10-12 —
 *  replaced Material's border-style family). */
private fun BarPin.icon(): ImageVector = when (this) {
    BarPin.TOP_START -> PinSpot.TopStart
    BarPin.TOP_CENTER -> PinSpot.TopCenter
    BarPin.TOP_END -> PinSpot.TopEnd
    BarPin.BOTTOM_START -> PinSpot.BottomStart
    BarPin.BOTTOM_CENTER -> PinSpot.BottomCenter
    BarPin.BOTTOM_END -> PinSpot.BottomEnd
}.icon

/** The next size down the icon scale (the smallest stays put). */
private fun MinputIconSize.stepDown(): MinputIconSize =
    MinputIconSize.entries[(ordinal - 1).coerceAtLeast(0)]

/** Width of the set and editor panels — short labels, no search row. */
private val NarrowPanelWidth = 160.dp
