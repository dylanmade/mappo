package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.mappo.data.model.Layout
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputDropdownMenu
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputMorphCorner
import com.mappo.ui.minput.MinputPanelDividerContentGap
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputHighlightContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMicroTextStyle
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.hypot
import kotlin.math.max

/**
 * The layouts drawer (2026-08-21) — the layouts view rebuilt as a push pane in the
 * controls home: the top bar's change button slides it in from the left, COMPRESSING the
 * controls content beside it (no scrim, no modality — the controls view stays fully
 * interactive). Focusing a card (d-pad) or tapping previews it in the controls view
 * behind (via [onPreviewLayout] → the VM viewing pointer; scroll-position-driven preview
 * retired 2026-08-26). The pane opens BETWEEN the
 * bars (2026-08-24): the top bar and the frame's bottom bar both keep their full width
 * above/below it.
 *
 * Anatomy: the full-width **Applications button** (the viewed application's icon + name),
 * then a Search · Sort controls row (the sort button opens the standard minput option
 * menu — Recent/Likes/A to Z/Z to A), then the card list in two categories: **Installed**
 * (on-device layouts for this app, ending with the "+ New layout" card) and **Community**
 * (published, not-yet-installed layouts — empty until community sharing lands). The
 * active layout's card wears the highlight plane — no separate default-layout concept:
 * the active layout IS its application's functional default.
 *
 * **Applications mode** (2026-08-27, replacing the retired right-side applications
 * drawer): pressing the Applications button TRANSITIONS this same pane into the
 * applications list — the higher surface color radiates outward from the button until it
 * covers the drawer, the button wears the highlight plane (the design language's open
 * marking), and the same controls re-target: search filters apps, sort offers
 * [ApplicationSort], and Installed/Community list applications. Focusing or tapping an
 * application previews it (tap also radiates back into layouts mode, now scoped to the
 * picked app) — selection is a VIEWING move only; auto detection is only disabled when a
 * LAYOUT of a non-active app is actually activated (the caller's cross-app gate).
 *
 * Tapping a layout card runs the standard activate flow (the caller wraps
 * [onActivateLayout] in the auto-detection warning gate); a future submenu replaces the
 * direct activation.
 *
 * Back handling is layered (2026-08-27): while applications mode is open, back steps
 * OUT of it first (the drawer's own [BackHandler] — the reveal retreats, the layouts
 * list returns); the next back reaches the caller's hoisted handler and dismisses the
 * drawer whole, and the caller reverts the viewing context to the active application,
 * which resets the Applications button.
 */
@Composable
internal fun LayoutsDrawerPane(
    open: Boolean,
    appPackage: String?,
    apps: List<InstalledApp>,
    activeAppPackage: String?,
    layouts: ImmutableList<Layout>,
    activeLayoutId: Long?,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Layout) -> Unit,
    onNewLayout: () -> Unit,
    onPreviewApplication: (String) -> Unit,
    onSelectApplication: (InstalledApp) -> Unit,
    onFullyClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SideDrawerShell(
        open = open,
        fromEnd = false,
        onFullyClosed = onFullyClosed,
        modifier = modifier,
    ) {
        LayoutsDrawerContent(
            appPackage = appPackage,
            apps = apps,
            activeAppPackage = activeAppPackage,
            layouts = layouts,
            activeLayoutId = activeLayoutId,
            onPreviewLayout = onPreviewLayout,
            onActivateLayout = onActivateLayout,
            onNewLayout = onNewLayout,
            onPreviewApplication = onPreviewApplication,
            onSelectApplication = onSelectApplication,
        )
    }
}

/**
 * The shared side-pane skeleton behind BOTH drawers (layouts left, applications right):
 * an animated-width clipping box whose fixed-width sheet slides in from the pane's own
 * screen edge, on the chrome plane with a divider on its content-facing side. The
 * neighbor in the parent Row resizes as the pane animates — a push pane, not an overlay.
 *
 * [onFullyClosed] fires when the CLOSE animation completes (not at close intent): callers
 * revert transient preview state there, so the drawer's content stays scoped and stable
 * while it slides away (reverting at intent time re-filtered the still-visible list —
 * the 2026-08-25 close-flash bug). A reopen mid-close retargets the animation and the
 * callback never fires.
 */
@Composable
internal fun SideDrawerShell(
    open: Boolean,
    fromEnd: Boolean,
    onFullyClosed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Absolute width, specified in PHYSICAL pixels (not dp): 320px = exactly 25% of the
    // 1280×960 test device, pushing the controls view there into a perfect 1:1 square
    // (a perfect 1:2 with both drawers open).
    val drawerWidth = with(LocalDensity.current) { LayoutsDrawerWidthPx.toDp() }
    val animatedWidth by animateDpAsState(
        targetValue = if (open) drawerWidth else 0.dp,
        animationSpec = tween(DrawerSlideMillis, easing = FastOutSlowInEasing),
        label = "sideDrawer",
        finishedListener = { landed -> if (landed == 0.dp) onFullyClosed() },
    )
    // The pane clips a fixed-width sheet anchored to its screen edge — the conventional
    // drawer slide, while the Row neighbor (the controls view) resizes. A start pane's
    // sheet pins its right edge to the pane's right edge (sliding in from the left); an
    // end pane's sheet pins its left edge to the pane's left edge, which itself rides
    // leftward as the pane widens (sliding in from the right).
    Box(modifier.fillMaxHeight().width(animatedWidth).clipToBounds()) {
        if (animatedWidth > 0.dp) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .offset {
                        IntOffset(
                            if (fromEnd) 0 else (animatedWidth - drawerWidth).roundToPx(),
                            0,
                        )
                    },
            ) {
                if (fromEnd) VerticalDivider()
                // surfaceContainer — chrome pane, the bars' plane (a non-modal side panel,
                // not an M3 modal drawer — no scrim, content beside it stays live).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    content()
                }
                if (!fromEnd) VerticalDivider()
            }
        }
    }
}

@Composable
private fun LayoutsDrawerContent(
    appPackage: String?,
    apps: List<InstalledApp>,
    activeAppPackage: String?,
    layouts: ImmutableList<Layout>,
    activeLayoutId: Long?,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Layout) -> Unit,
    onNewLayout: () -> Unit,
    onPreviewApplication: (String) -> Unit,
    onSelectApplication: (InstalledApp) -> Unit,
) {
    // Applications mode (the Applications button pressed) — plain remember on purpose:
    // the content decomposes when the drawer fully closes, so a reopened drawer always
    // starts back in layouts mode.
    var appsMode by remember { mutableStateOf(false) }
    // The query is shared between modes but resets on every mode flip — a layouts filter
    // has no meaning over the apps list and vice versa. Each mode keeps its OWN sort.
    var query by remember { mutableStateOf("") }
    var layoutSort by remember { mutableStateOf(LayoutSort.RECENT) }
    var appSort by remember { mutableStateOf(ApplicationSort.RECENT) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    // Back steps OUT of applications mode first (2026-08-27) — the radiating plane
    // retreats into the button and the layouts list returns; only the NEXT back
    // dismisses the drawer whole. Registered when this content mounts, so it wins over
    // the caller's close-the-drawer handler while the drawer is open (and unregisters
    // with the content when the drawer fully closes).
    BackHandler(enabled = appsMode) {
        appsMode = false
        query = ""
    }

    // The apps-mode reveal: the higher surface color radiates outward from the
    // Applications button until it covers the drawer background (and retreats back into
    // the button on the way out) — the mode visibly GROWS out of the control that owns it.
    val revealProgress by animateFloatAsState(
        targetValue = if (appsMode) 1f else 0f,
        animationSpec = tween(AppsRevealMillis, easing = FastOutSlowInEasing),
        label = "appsReveal",
    )
    val revealColor = MaterialTheme.colorScheme.surfaceContainerHigh
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var revealCenter by remember { mutableStateOf(Offset.Zero) }

    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootCoords = it }
            .drawBehind {
                if (revealProgress > 0f) {
                    // Radius runs to the drawer corner FARTHEST from the button center so
                    // full progress always covers the whole pane.
                    val maxRadius = hypot(
                        max(revealCenter.x, size.width - revealCenter.x),
                        max(revealCenter.y, size.height - revealCenter.y),
                    )
                    drawCircle(
                        color = revealColor,
                        radius = revealProgress * maxRadius,
                        center = revealCenter,
                    )
                }
            },
    ) {
        // The Applications button: the viewed application's identity (icon + name) and
        // the way into applications mode; wears the highlight plane while open — the
        // design language's open/selected marking (the drawer summons' treatment).
        val viewedApp = appPackage?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
        val appIcon = rememberAppIconPainter(appPackage)
        MinputPillButton(
            text = viewedApp?.label ?: appPackage ?: "No application",
            onClick = {
                appsMode = !appsMode
                query = ""
            },
            leadingIcon = appIcon ?: rememberVectorPainter(Lucide.LayoutGrid),
            // Launcher icons carry fixed colors (never re-tint); the Lucide fallback
            // glyph tints like any concept icon.
            leadingIconTint = if (appIcon != null) {
                Color.Unspecified
            } else MaterialTheme.colorScheme.onSurfaceVariant,
            // Identity packed to the start, dropdown arrow pinned to the far end
            // (2026-08-27 trial vs the centered stack — flip alignStart to compare).
            trailingIcon = rememberVectorPainter(Lucide.ChevronDown),
            alignStart = true,
            highlighted = appsMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PanelContentPadding,
                    top = PanelContentPadding,
                    end = PanelContentPadding,
                )
                // AFTER the placement-shifting padding, so the captured bounds are the
                // button's real ones. The reveal radiates from the button's center.
                .onGloballyPositioned { coords ->
                    rootCoords?.let { revealCenter = it.localBoundingBoxOf(coords).center }
                },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DrawerControlGap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PanelContentPadding,
                    top = DrawerControlGap,
                    end = PanelContentPadding,
                    bottom = MinputPanelDividerContentGap,
                ),
        ) {
            MinputTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = if (appsMode) "Search apps" else "Search layouts",
                leadingIcon = Lucide.Search,
                clearable = true,
                // Top-of-screen field — the sanctioned modal-less variant.
                inlineEdit = true,
                // Experimental bevel-on-fields trial (2026-08-27) — judged here first.
                bevel = true,
                modifier = Modifier.weight(1f),
            )
            Box {
                // Wears the highlight plane while its menu is up — the design language's
                // selected/active marking (the change button's drawer-open treatment).
                MinputPillButton(
                    onClick = { sortMenuOpen = true },
                    leadingIcon = rememberVectorPainter(Lucide.ArrowUpDown),
                    contentDescription = if (appsMode) "Sort applications" else "Sort layouts",
                    // Standard box chrome (2026-08-27, replacing the bare trial): the
                    // family fill + bevel ring, matching the buttons around it.
                    highlighted = sortMenuOpen,
                )
                if (appsMode) {
                    MinputDropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                        current = appSort,
                        options = ApplicationSort.entries,
                        optionLabel = { it.label },
                        onPick = { appSort = it },
                    )
                } else {
                    MinputDropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                        current = layoutSort,
                        options = LayoutSort.entries,
                        optionLabel = { it.label },
                        onPick = { layoutSort = it },
                    )
                }
            }
        }

        // Inset divider under the controls block (2026-08-27) — separates the fixed
        // search/sort chrome from the scrolling card list in BOTH modes (it sits
        // outside the crossfade, over the radiating reveal plane).
        HorizontalDivider(Modifier.padding(horizontal = PanelContentPadding))

        // The two lists crossfade on the reveal's clock, so the applications list
        // resolves in as the radiating plane covers the drawer (and out as it retreats).
        Crossfade(
            targetState = appsMode,
            animationSpec = tween(AppsRevealMillis),
            label = "drawerList",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { showApps ->
            if (showApps) {
                ApplicationsList(
                    apps = apps,
                    activeAppPackage = activeAppPackage,
                    query = query,
                    sort = appSort,
                    onPreviewApplication = onPreviewApplication,
                    onSelectApplication = { app ->
                        // Radiate back into layouts mode — the layouts list rescopes to
                        // the picked application "behind" the retreating plane.
                        appsMode = false
                        query = ""
                        onSelectApplication(app)
                    },
                )
            } else {
                LayoutsList(
                    appPackage = appPackage,
                    layouts = layouts,
                    activeLayoutId = activeLayoutId,
                    query = query,
                    sort = layoutSort,
                    onPreviewLayout = onPreviewLayout,
                    onActivateLayout = onActivateLayout,
                    onNewLayout = onNewLayout,
                )
            }
        }
    }
}

@Composable
private fun LayoutsList(
    appPackage: String?,
    layouts: ImmutableList<Layout>,
    activeLayoutId: Long?,
    query: String,
    sort: LayoutSort,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Layout) -> Unit,
    onNewLayout: () -> Unit,
) {
    // Category assembly. Membership = Layout.packageName (2026-08-21 model). No app
    // context → every layout under Installed. Community stays empty until sharing
    // lands. (The Default category retired with the default-layout concept —
    // 2026-08-24: the active layout IS the app's functional default.)
    val children = if (appPackage != null) {
        layouts.filter { it.packageName == appPackage }
    } else layouts
    val trimmed = query.trim()
    val filtered = if (trimmed.isEmpty()) children
    else children.filter { it.name.contains(trimmed, ignoreCase = true) }
    val installed = filtered.sortedWith(sort.comparator)
    val community = emptyList<Layout>()

    // Preview triggers are DELIBERATE only (2026-08-26): card focus (d-pad, via
    // each card's focus observer) or tap — the scroll-position-driven preview
    // (topmost visible card = previewed) is retired; merely scrolling past cards
    // churned the controls view and read as phantom focus.
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DrawerControlGap),
        contentPadding = PaddingValues(
            start = PanelContentPadding,
            end = PanelContentPadding,
            bottom = PanelContentPadding,
        ),
    ) {
        // No empty hint (2026-08-27): the ever-present "+ New layout" card below IS the
        // empty section's affordance — a "no layouts" line above it just restated it.
        drawerSection("Installed layouts", installed, emptyHint = null) { layout ->
            LayoutCard(
                layout = layout,
                active = layout.id == activeLayoutId,
                onPreview = { onPreviewLayout(layout.id) },
                onActivate = { onActivateLayout(layout) },
            )
        }
        // Always the Installed section's last card — the empty "+ New layout" card
        // routing into the create flow. Key deliberately outside CardKeyPrefix so the
        // scroll-preview scan skips it.
        item(key = "new-layout", contentType = "new") {
            NewLayoutCard(onClick = onNewLayout)
        }
        drawerSection("Community layouts", community, emptyHint = "No community layouts yet") { layout ->
            LayoutCard(
                layout = layout,
                active = layout.id == activeLayoutId,
                onPreview = { onPreviewLayout(layout.id) },
                onActivate = { onActivateLayout(layout) },
            )
        }
    }
}

/** One category of the card list: overline header, then cards (or a muted empty hint —
 *  null for a section whose empty state is carried by a permanent card instead). */
private fun androidx.compose.foundation.lazy.LazyListScope.drawerSection(
    title: String,
    sectionLayouts: List<Layout>,
    emptyHint: String?,
    card: @Composable (Layout) -> Unit,
) {
    item(key = "header:$title", contentType = "header") {
        Text(
            text = title.uppercase(),
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MinputPanelDividerContentGap),
        )
    }
    if (sectionLayouts.isEmpty()) {
        if (emptyHint != null) {
            item(key = "empty:$title", contentType = "empty") {
                Text(
                    text = emptyHint,
                    style = minputMicroTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = MinputGlyphLabelGap),
                )
            }
        }
    } else {
        items(sectionLayouts, key = { CardKeyPrefix + it.id }, contentType = { "card" }) { card(it) }
    }
}

/**
 * One layout card: name over author on the left; the like count alone in the top-right
 * corner (filled heart + count, right edge pinned to the card padding so a growing count
 * expands LEFT while the icon→digit gap stays fixed); then the two-line description
 * strip. Cards are ELEVATED by default; the ACTIVE layout's card wears the highlight
 * plane — the design language's selected/active marking (which replaced the old "ACTIVE"
 * overline). [showDescription] is the seam for a future compact/full drawer density
 * setting — compact hides the strip.
 */
@Composable
private fun LayoutCard(
    layout: Layout,
    active: Boolean,
    onPreview: () -> Unit,
    onActivate: () -> Unit,
    showDescription: Boolean = true,
) {
    // surface 2 resting / highlight when active — the selection plane's content is
    // onPrimary, its secondary text the same role softened (no onPrimaryVariant exists).
    val container = if (active) minputHighlightContainer() else minputBoxContainer()
    val primaryContent = if (active) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val secondaryContent = if (active) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = SecondaryOnHighlightAlpha)
    } else MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(MinputMorphCorner),
        color = container,
        border = minputBevelBorder(container, MinputMorphCorner),
        modifier = Modifier
            .fillMaxWidth()
            .minputInteractiveMotion(interaction)
            // Controller browsing previews as focus lands, mirroring touch scroll.
            .onFocusChanged { if (it.isFocused) onPreview() }
            .clip(RoundedCornerShape(MinputMorphCorner))
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                onClickLabel = "Activate ${layout.name}",
                onClick = onActivate,
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(CardPadding)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = layout.name,
                        style = minputMiniTextStyle(),
                        color = primaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = layout.author.ifEmpty { "You" },
                        style = minputMicroTextStyle(),
                        color = secondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                // Right edge rides the card padding; the count digits grow leftward.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FilledHeartIcon,
                        contentDescription = "Likes",
                        modifier = Modifier.size(LikeIconSize),
                        tint = secondaryContent,
                    )
                    Spacer(Modifier.width(LikeCountGap))
                    Text(
                        text = layout.likeCount.toString(),
                        style = minputMicroTextStyle(),
                        color = secondaryContent,
                        maxLines = 1,
                    )
                }
            }
            if (showDescription) {
                Spacer(Modifier.height(DescriptionGap))
                Text(
                    // Two lines are always reserved (minLines) so card heights stay
                    // uniform whether or not a description exists.
                    text = layout.description.ifBlank { PlaceholderDescription },
                    style = minputMicroTextStyle().copy(fontStyle = FontStyle.Italic),
                    color = secondaryContent,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The Installed section's permanent last card: an empty card in the layout cards' chrome,
 * carrying only the "+ New layout" affordance and routing into the create-layout flow.
 * The plus is a TEXT glyph, not a leading [Icon]: a single string self-centers on both
 * axes, where an icon + gap + label row is only geometrically centered — the eye anchors
 * on the label, which sits (icon + gap)/2 right of true center (device report,
 * 2026-08-25).
 */
@Composable
private fun NewLayoutCard(onClick: () -> Unit) {
    val container = minputBoxContainer()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(MinputMorphCorner),
        color = container,
        border = minputBevelBorder(container, MinputMorphCorner),
        modifier = Modifier
            .fillMaxWidth()
            .minputInteractiveMotion(interaction)
            .clip(RoundedCornerShape(MinputMorphCorner))
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                onClickLabel = "New layout",
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(NewLayoutCardHeight),
        ) {
            Text(
                text = "+ New layout",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Sort orders for the drawer's card list — the sort button's option menu. [RECENT] is the
 * default; with no last-used tracking yet it approximates with creation recency (newest
 * first — the dormant layouts view's precedent), and [LIKES] ties at zero until community
 * sharing brings real counts, so recency breaks the tie.
 */
internal enum class LayoutSort(val label: String, val comparator: Comparator<Layout>) {
    RECENT("Recent", compareByDescending<Layout> { it.id }),
    LIKES("Likes", compareByDescending<Layout> { it.likeCount }.thenByDescending { it.id }),
    A_TO_Z("A to Z", compareBy<Layout> { it.name.lowercase() }),
    Z_TO_A("Z to A", compareByDescending<Layout> { it.name.lowercase() }),
}

/** Lucide's heart, FILLED: the lucide-icons port ships stroke-only glyphs, so the filled
 *  variant is authored here from the same lucide.dev path data. Fill color is a
 *  placeholder — `Icon` tint paints it. */
private val FilledHeartIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilledHeart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2" +
                "-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** LazyColumn key prefix distinguishing layout cards from header/hint rows for the
 *  scroll-preview scan. */
private const val CardKeyPrefix = "layout:"

/** Drawer width in PHYSICAL pixels — deliberately absolute across screen sizes and aspect
 *  ratios (Dylan, 2026-08-21): 320px is exactly 25% of the 1280×960 (4:3) test device,
 *  which leaves the controls view a perfect 1:1 square while open. */
private const val LayoutsDrawerWidthPx = 320

/** Slide duration for the pane's open/close resize. */
private const val DrawerSlideMillis = 300

/** Duration of the applications-mode radial reveal (and the list crossfade riding it). */
private const val AppsRevealMillis = 300

/** Gap between the drawer's control-row members and between list cards (the filter rows'
 *  6dp rhythm). */
internal val DrawerControlGap = 6.dp

/** Interior padding of a layout card. */
internal val CardPadding = 8.dp

/** The like heart, sized to the micro text line beside it. */
private val LikeIconSize = 10.dp

/** Fixed gap between the heart and the count's first digit. */
private val LikeCountGap = 4.dp

/** Gap above the description strip. */
private val DescriptionGap = 2.dp

/** Height of the [NewLayoutCard] — slimmer than a populated card, tall enough to read as
 *  a card slot rather than a button. */
private val NewLayoutCardHeight = 36.dp

/** TEMPORARY (2026-08-25, Dylan): stand-in description so the drawer's card density and
 *  typography can be judged before real descriptions exist — every card whose layout has
 *  no description renders this. Remove once descriptions are editable. */
private const val PlaceholderDescription =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor " +
        "incididunt ut labore et dolore magna aliqua."

/** Secondary text on the highlight plane: onPrimary softened, since the scheme has no
 *  dedicated secondary-on-primary role. */
internal const val SecondaryOnHighlightAlpha = 0.8f
