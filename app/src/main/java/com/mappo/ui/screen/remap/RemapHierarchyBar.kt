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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.AppWindow
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.ChevronsLeftRight
import com.composables.icons.lucide.ChevronsRightLeft
import com.composables.icons.lucide.FoldHorizontal
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.UnfoldHorizontal
import com.mappo.data.model.Layout
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ActionSetKind
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.data.settings.BarPin
import com.mappo.ui.component.rememberAppIconBitmap
import com.mappo.ui.icons.PinEdge
import com.mappo.ui.minput.MinputAction
import com.mappo.ui.minput.MinputBreadcrumbJoin
import com.mappo.ui.minput.MinputBreadcrumbJoinDefault
import com.mappo.ui.minput.MinputBreadcrumbMode
import com.mappo.ui.minput.MinputBreadcrumbOrientation
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
import com.mappo.ui.minput.MinputMenuPlacement
import com.mappo.ui.minput.MinputSegmentGap
import com.mappo.ui.minput.MinputSize
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputMiniTextStyle
import kotlinx.collections.immutable.ImmutableList

/**
 * **The editors' hierarchy bar** (Dylan, 2026-10-07) — replaced the full-width top bar, the
 * layouts drawer (and its applications mode), the layout-set switch + kebab, and the
 * physical/virtual editor switch with ONE compact group that floats at a pinned edge of the
 * screen.
 *
 * Anatomy, an M3 button group's: a [MinputBreadcrumbRow] of three joined crumbs reading the
 * hierarchy left to right — **Application › Layout › Buttons** — each an overline over the
 * current value; then, as its own circular button, the PIN button choosing which screen edge the
 * bar sits on ([BarPin]) — a row on the top or bottom; down a side, a VERTICAL breadcrumb of
 * always-collapsed crumbs, its menus opening beside it.
 * Every crumb drops a [MinputDropdownPanel] holding that level's picker:
 *  - Application — search · sort over the applications list (was the drawer's applications mode);
 *  - Layout — search · sort over the layouts list with "+ New layout" (was the drawer's body);
 *  - Buttons — the button SET being edited, both kinds in one list: the physical sets, a divider,
 *    the virtual sets, each kind headed by its own "+ New … set" (2026-10-14: folded the separate
 *    Buttons (editor switch) and Button set crumbs into one — picking a set of the other kind IS
 *    the editor switch; see [ActionSetKind]).
 *
 * Each crumb is EXPANDED (glyph + overline + title) or COLLAPSED to its glyph — an icon button
 * still wearing its join ([collapsed], 2026-10-14). The pin button's menu toggles each crumb and
 * collapses / expands them all, under the four edges.
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
    // ── Buttons — the button set, either kind ──
    /** The editor hosting the bar: its kind of sets preview on focus; the other kind's are
     *  tap-only, because picking one switches editors (leaves the screen). */
    editor: EditorKind,
    config: ControllerConfig?,
    viewingSet: ActionSetGraph?,
    /** Focus-preview of a set of the [editor]'s own kind. */
    onPreviewSet: (Long) -> Unit,
    /** A tapped set, either kind — the caller switches editors when the kind isn't [editor]'s. */
    onSelectSet: (id: Long, kind: ActionSetKind) -> Unit,
    onAddSet: (ActionSetKind) -> Unit,
    // ── Pin ──
    pin: BarPin,
    onPinChange: (BarPin) -> Unit,
    // ── Collapse ──
    /** The crumbs collapsed to their glyph; the rest show [mode]. */
    collapsed: Set<HierarchyCrumb>,
    onCollapsedChange: (Set<HierarchyCrumb>) -> Unit,
    modifier: Modifier = Modifier,
    // ── Presentation (2026-10-07: tunable while the design settles) ──
    /** The crumbs' and pin button's control variant. */
    size: MinputSize = MinputBreadcrumbSize,
    /** Whether the crumbs carry their small-caps level labels over the titles. */
    showOverlines: Boolean = true,
    /** Every crumb's mode unless it is [collapsed]. */
    mode: MinputBreadcrumbMode = MinputBreadcrumbMode.Expanded,
    join: MinputBreadcrumbJoin = MinputBreadcrumbJoinDefault,
) {
    val crumbs = HierarchyCrumb.entries
    // Down a side the crumbs are ALWAYS collapsed — a glyph stack. [collapsed] is left untouched
    // (and the pin menu's toggles disabled), so it is still the top / bottom bar's when the bar
    // goes back to an edge it can spread along.
    val shownCollapsed = if (pin.sideways) crumbs.toSet() else collapsed
    // The panels open AWAY from the edge the bar is pinned to: beside a side-pinned crumb, below
    // (or, by its own flip, above) a top / bottom one.
    val panelPlacement = pin.panelPlacement()
    fun toggle(crumb: HierarchyCrumb) {
        if (openCrumb == crumb) onDismissCrumb() else onOpenCrumb(crumb)
    }
    // Each crumb is its panel's anchor, so the two share a Box.
    @Composable
    fun crumbButton(crumb: HierarchyCrumb, position: MinputBreadcrumbPosition) {
        val crumbMode = if (crumb in shownCollapsed) MinputBreadcrumbMode.Collapsed else mode
        Box {
            when (crumb) {
                HierarchyCrumb.APPLICATION -> {
                    MinputBreadcrumbSegment(
                        position = position,
                        mode = crumbMode,
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
                        placement = panelPlacement,
                        caret = true,
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
                        mode = crumbMode,
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
                        placement = panelPlacement,
                        caret = true,
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
                HierarchyCrumb.BUTTONS -> {
                    MinputBreadcrumbSegment(
                        position = position,
                        mode = crumbMode,
                        overline = "Buttons".takeIf { showOverlines },
                        icon = { iconSize -> CrumbIcon(editor.icon, iconSize) },
                        title = viewingSet?.actionSet?.title
                            ?: config?.setsOf(editor.setKind)?.firstOrNull()?.actionSet?.title
                            ?: "None",
                        selected = openCrumb == crumb,
                        onClick = { toggle(crumb) },
                        onClickLabel = "Choose button set",
                        modifier = Modifier.testTag("bar:buttons"),
                    )
                    MinputDropdownPanel(
                        expanded = openCrumb == crumb,
                        onDismissRequest = onDismissCrumb,
                        width = SetPanelWidth,
                        placement = panelPlacement,
                        caret = true,
                    ) {
                        SetPanel(
                            editor = editor,
                            config = config,
                            viewingSet = viewingSet,
                            onPreviewSet = onPreviewSet,
                            onSelectSet = { id, kind ->
                                onCommitCrumb()
                                onSelectSet(id, kind)
                            },
                            onAddSet = { kind ->
                                onCommitCrumb()
                                onAddSet(kind)
                            },
                        )
                    }
                }
            }
        }
    }
    val pinButton: @Composable () -> Unit = {
        BarPinButton(
            pin = pin,
            onPinChange = onPinChange,
            collapsed = collapsed,
            onCollapsedChange = onCollapsedChange,
            size = size,
        )
    }
    if (pin.sideways) {
        // Pinned to a side: the crumbs stack down the edge as one VERTICAL breadcrumb — collapsed,
        // still joined, each bulge leading down into the next — then the pin button.
        Column(
            modifier = modifier.then(BlockTouches),
            verticalArrangement = Arrangement.spacedBy(MinputSegmentGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MinputBreadcrumbRow(
                size = size,
                join = join,
                mode = MinputBreadcrumbMode.Collapsed,
                orientation = MinputBreadcrumbOrientation.Vertical,
            ) {
                crumbs.forEachIndexed { index, crumb ->
                    crumbButton(crumb, MinputBreadcrumbPosition.of(index, crumbs.size))
                }
            }
            pinButton()
        }
    } else {
        Row(
            modifier = modifier.then(BlockTouches),
            horizontalArrangement = Arrangement.spacedBy(MinputSegmentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MinputBreadcrumbRow(size = size, join = join, mode = mode) {
                crumbs.forEachIndexed { index, crumb ->
                    crumbButton(crumb, MinputBreadcrumbPosition.of(index, crumbs.size))
                }
            }
            pinButton()
        }
    }
}

/** The bar FLOATS over the view (2026-10-14): a touch anywhere on it — the gaps between its
 *  controls included — is the bar's, never the view's beneath, as an M3 Surface blocks touches.
 *  Also what keeps the crumbs tappable under Robolectric's legacy graphics, which can't hit-test
 *  their join-cut path outlines: a near miss there lost to the content plane's direct hit. */
private val BlockTouches = Modifier.pointerInput(Unit) {}

/** The bar's crumbs, in hierarchy order — the order they read in the bar. [label] names one in
 *  the pin menu's collapse toggles. */
internal enum class HierarchyCrumb(val label: String) {
    APPLICATION("application"),
    LAYOUT("layout"),
    BUTTONS("buttons"),
}

/** The two editors, one per kind of button set — picking a set in the Buttons crumb picks its
 *  editor. Virtual is Material's (outlined) Layers — two stacked shapes, where Lucide's has three
 *  (Dylan, 2026-10-12). */
internal enum class EditorKind(val label: String, val icon: ImageVector, val setKind: ActionSetKind) {
    PHYSICAL("Physical", Lucide.Gamepad2, ActionSetKind.PHYSICAL),
    VIRTUAL("Virtual", Icons.Outlined.Layers, ActionSetKind.VIRTUAL),
    ;

    companion object {
        fun of(kind: ActionSetKind): EditorKind = entries.first { it.setKind == kind }
    }
}

/** A crumb's concept glyph at the size the crumb hands it (the variant's glyph size). */
@Composable
private fun CrumbIcon(icon: ImageVector, size: MinputIconSize) {
    // Optical: the crumbs' glyphs are read side by side (the wide gamepad read a step small).
    MinputIcon(icon, contentDescription = null, size = size, optical = true)
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
 * Every button set, by kind: the physical sets, a divider, the virtual sets — each kind headed
 * by its "+ New … set" (as "+ New layout" heads the layouts), each row wearing its kind's glyph.
 * The viewed set sits on the highlight plane. Sets of the [editor]'s own kind preview on focus and
 * keep on tap; the other kind's are tap-only — picking one opens the other editor, which a
 * focus-preview must not do. No config yet = no rows, no "new" rows either.
 */
@Composable
private fun ColumnScope.SetPanel(
    editor: EditorKind,
    config: ControllerConfig?,
    viewingSet: ActionSetGraph?,
    onPreviewSet: (Long) -> Unit,
    onSelectSet: (id: Long, kind: ActionSetKind) -> Unit,
    onAddSet: (ActionSetKind) -> Unit,
) {
    val viewingId = viewingSet?.actionSet?.id
        ?: config?.setsOf(editor.setKind)?.firstOrNull()?.actionSet?.id
    val seat = rememberControllerFocusSeat()
    Column(
        verticalArrangement = Arrangement.spacedBy(DrawerRowGap),
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
    ) {
        if (config == null) {
            Text(
                text = "No button sets",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(CardPadding),
            )
            return@Column
        }
        EditorKind.entries.forEachIndexed { index, kind ->
            if (index > 0) PanelDivider()
            PanelActionRow(
                label = "New ${kind.label.lowercase()} set",
                onClick = { onAddSet(kind.setKind) },
            )
            config.setsOf(kind.setKind).forEach { set ->
                val id = set.actionSet.id
                val ownKind = kind == editor
                PanelChoiceRow(
                    label = set.actionSet.title,
                    icon = kind.icon,
                    active = id == viewingId,
                    onFocus = { if (ownKind && id != viewingId) onPreviewSet(id) },
                    onClick = { onSelectSet(id, kind.setKind) },
                    modifier = if (id == viewingId) Modifier.focusRequester(seat) else Modifier,
                )
            }
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
            // A slot, so labels align past glyphs of different shapes; optical, so a wide glyph
            // carries the same weight as the square ones in the rows around it.
            MinputIcon(icon, contentDescription = null, size = iconSize, tint = content, slot = true, optical = true)
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
 * Where the bar sits, and how much of it shows: a circular button wearing the CURRENT edge's
 * glyph, opening the four edges as a single-choice menu — then, under a divider, a
 * collapse/expand toggle per crumb, and under another, collapse all (or expand all, once every
 * crumb is collapsed). Its own button after the crumbs — the M3 button group's convention for a
 * control that is not part of the group's one silhouette. Pinned to a side, the crumbs are always
 * collapsed, so the collapse rows are shown DISABLED — still there, so the menu doesn't change
 * shape — and the top / bottom bar's [collapsed] set is kept for when the bar goes back.
 */
@Composable
private fun BarPinButton(
    pin: BarPin,
    onPinChange: (BarPin) -> Unit,
    collapsed: Set<HierarchyCrumb>,
    onCollapsedChange: (Set<HierarchyCrumb>) -> Unit,
    size: MinputSize,
) {
    var open by remember { mutableStateOf(false) }
    val canCollapse = !pin.sideways
    val perCrumb = HierarchyCrumb.entries.map { crumb ->
        val isCollapsed = crumb in collapsed
        MinputAction(
            label = (if (isCollapsed) "Expand " else "Collapse ") + crumb.label,
            icon = if (isCollapsed) Lucide.UnfoldHorizontal else Lucide.FoldHorizontal,
            enabled = canCollapse,
            onClick = { onCollapsedChange(if (isCollapsed) collapsed - crumb else collapsed + crumb) },
        )
    }
    val allCollapsed = collapsed.containsAll(HierarchyCrumb.entries)
    val all = MinputAction(
        label = if (allCollapsed) "Expand all" else "Collapse all",
        icon = if (allCollapsed) Lucide.ChevronsLeftRight else Lucide.ChevronsRightLeft,
        enabled = canCollapse,
        onClick = { onCollapsedChange(if (allCollapsed) emptySet() else HierarchyCrumb.entries.toSet()) },
    )
    Box {
        MinputButton(
            leadingIcon = pin.icon(),
            contentDescription = "Bar pinned to ${pin.edge}",
            highlighted = open,
            size = size,
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
            actionGroups = listOf(perCrumb, listOf(all)),
            placement = pin.panelPlacement(),
            caret = true,
        )
    }
}

/** Where the bar's menus open from its controls: away from the edge it is pinned to. */
private fun BarPin.panelPlacement(): MinputMenuPlacement = when (this) {
    BarPin.START -> MinputMenuPlacement.End
    BarPin.END -> MinputMenuPlacement.Start
    BarPin.TOP, BarPin.BOTTOM -> MinputMenuPlacement.Below
}

/** The edge's glyph: a square off a line on that edge ([PinEdge], 2026-10-14 — replaced the
 *  dot-grid spots). */
private fun BarPin.icon(): ImageVector = when (this) {
    BarPin.TOP -> PinEdge.Top
    BarPin.START -> PinEdge.Start
    BarPin.END -> PinEdge.End
    BarPin.BOTTOM -> PinEdge.Bottom
}.icon

/** Width of the button-set panel — short labels, no search row; room for "New physical set". */
private val SetPanelWidth = 180.dp
