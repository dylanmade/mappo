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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.mappo.data.model.Layout
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.ui.minput.MinputDropdownMenu
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputMorphCorner
import com.mappo.ui.minput.MinputSize
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputHighlightContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputBarHeight
import com.mappo.ui.minput.MinputBoxStroke
import com.mappo.ui.minput.MinputEdge
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.minputBevelEdge
import com.mappo.ui.minput.minputBevelHighlight
import com.mappo.ui.minput.MinputIcon
import com.mappo.ui.minput.MinputIconSize
import com.mappo.ui.minput.MinputRoundEndBias
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
 * Anatomy (2026-09-28 retool): the pane is the **hidden left edge of the screen frame** — it
 * wears the bars' plane and lights only its content-facing edge, so with the top and bottom
 * bars it reads as three sides of one bezel around the controls view. It spans from the top of
 * the screen down to the bottom bar, PUSHING the top bar right (the bottom bar keeps its full
 * width, and the Mappo button its corner). Top to bottom:
 *  - the **header row**, level with the top bar and the same height: the application
 *    dropdown ([BarStackButton], the top bar's own identity widget — here the app's name alone
 *    as the overline, a dropdown arrow in the same colour, the application rows' icon size) —
 *    chrome-less, so the header reads as the bar continuing;
 *  - the Search · Sort row (the sort button is a bare icon button like the layout-set kebab,
 *    opening the standard minput option menu — Recent/Likes/A to Z/Z to A);
 *  - the list: "+ New layout", then **Installed** (on-device layouts for this app) and
 *    **Community** (published, not-yet-installed — empty until sharing lands) as borderless
 *    two-line rows. The active layout's row wears the highlight plane — no separate
 *    default-layout concept: the active layout IS its application's functional default.
 *
 * **Applications mode** (2026-08-27, replacing the retired right-side applications
 * drawer): pressing the application dropdown TRANSITIONS this same pane into the
 * applications list — the higher surface color radiates outward from the dropdown until it
 * covers the drawer's body (the header stays on the bar plane), the dropdown goes accent and
 * flips its arrow (the bars' open marking), and the same controls re-target: search filters apps, sort offers
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
        // The bar plane, lit along the content-facing edge — the bars' own treatment
        // (MinputBar), turned on its side. The line starts at the top bar's lit edge rather
        // than the top of the screen: above that, this edge sits beside the bar on the same
        // plane, not beside the content, so lighting it there would split the two. Starting a
        // stroke early fills the corner the two lines would otherwise leave unlit.
        val plane = MaterialTheme.colorScheme.surfaceContainer
        Box(
            Modifier
                .fillMaxSize()
                .background(plane)
                .minputBevelEdge(
                    color = minputBevelHighlight(plane),
                    edge = MinputEdge.END,
                    leadInset = MinputBarHeight - MinputBoxStroke,
                ),
        ) {
            // A raw background() isn't a Surface, so the content color has to be provided.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
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
    }
}

/**
 * The shared side-pane skeleton behind BOTH drawers (layouts left, applications right):
 * an animated-width clipping box whose fixed-width sheet slides in from the pane's own
 * screen edge. The neighbor in the parent Row resizes as the pane animates — a push pane,
 * not an overlay.
 *
 * The shell itself is unpainted — the pane dresses its own sheet (the layouts drawer wears
 * the bar plane and a lit edge since 2026-09-28; before that it was transparent, its contents
 * riding [com.mappo.ui.minput.MinputPod]s).
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
            Box(
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
                content()
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
    // application dropdown until it covers the drawer's body (and retreats back into
    // the dropdown on the way out) — the mode visibly GROWS out of the control that owns it.
    val revealProgress by animateFloatAsState(
        targetValue = if (appsMode) 1f else 0f,
        animationSpec = tween(AppsRevealMillis, easing = FastOutSlowInEasing),
        label = "appsReveal",
    )
    val revealColor = MaterialTheme.colorScheme.surfaceContainerHigh
    var bodyCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var revealCenter by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize()) {
        // ── Header: the application dropdown, level with the top bar ──────────────
        // Same height and edge inset as the bar, so the dropdown sits on the bar's line as
        // though the bar ran on to the screen edge (which, visually, it does).
        val viewedApp = appPackage?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(MinputBarHeight)
                .padding(horizontal = MinputBarEdgePadding),
        ) {
            BarStackButton(
                appPackage = appPackage,
                overline = viewedApp?.label ?: appPackage ?: "No application",
                value = null,
                iconSize = AppCardIconSize,
                highlighted = appsMode,
                onClick = {
                    appsMode = !appsMode
                    query = ""
                },
                dropdownArrow = true,
                // The pane is the cap here, not the bar's name budget.
                valueMaxWidth = Dp.Infinity,
                modifier = Modifier
                    .testTag("drawer:application")
                    // The reveal radiates from the dropdown's centre.
                    .onGloballyPositioned { coords ->
                        bodyCoords?.let { revealCenter = it.localBoundingBoxOf(coords).center }
                    },
            )
        }

        // ── Body: search · sort over the list ─────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { bodyCoords = it }
                // The reveal is the BODY's: the header stays on the bar plane, so the frame
                // doesn't change colour at its seam with the top bar. Clipped, because its
                // centre sits up in the header and drawBehind isn't bounded by the node.
                .clipToBounds()
                .drawBehind {
                    if (revealProgress > 0f) {
                        // Radius runs to the corner FARTHEST from the centre, so full
                        // progress always covers the whole body.
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
                }
                .padding(horizontal = MinputBarEdgePadding),
            verticalArrangement = Arrangement.spacedBy(DrawerControlGap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SearchSortGap),
            ) {
                MinputTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = if (appsMode) "Search apps" else "Search layouts",
                    leadingIcon = Lucide.Search,
                    clearable = true,
                    // Top-of-screen field — the sanctioned modal-less variant.
                    inlineEdit = true,
                    modifier = Modifier.weight(1f),
                )
                // A bare icon button, like the layout-set kebab (2026-09-28): the drawer is a
                // bar surface now, and its utility glyphs follow the bar's.
                Box {
                    MinputIconButton(
                        icon = Lucide.ArrowUpDown,
                        contentDescription = if (appsMode) "Sort applications" else "Sort layouts",
                        onClick = { sortMenuOpen = true },
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

            // The two lists crossfade on the reveal's clock, so the applications list
            // resolves in as the radiating plane covers the body (and out as it retreats).
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
        verticalArrangement = Arrangement.spacedBy(DrawerRowGap),
        contentPadding = PaddingValues(bottom = DrawerControlGap),
    ) {
        // FIRST in the list (2026-08-30, Dylan — it was the Installed section's last
        // card): the create-layout route, in the layout cards' chrome.
        item(key = "new-layout", contentType = "new") {
            NewLayoutCard(onClick = onNewLayout)
        }
        // Installed then community, headerless (2026-08-30: the INSTALLED/COMMUNITY
        // overlines retired — the download marker on a card says "installed" instead,
        // and its absence says "community", so the two read apart without a header
        // splitting the list). Keys keep their prefix for the card scan.
        items(installed, key = { CardKeyPrefix + it.id }, contentType = { "card" }) { layout ->
            LayoutCard(
                layout = layout,
                active = layout.id == activeLayoutId,
                installed = true,
                onPreview = { onPreviewLayout(layout.id) },
                onActivate = { onActivateLayout(layout) },
            )
        }
        items(community, key = { CardKeyPrefix + it.id }, contentType = { "card" }) { layout ->
            LayoutCard(
                layout = layout,
                active = layout.id == activeLayoutId,
                installed = false,
                onPreview = { onPreviewLayout(layout.id) },
                onActivate = { onActivateLayout(layout) },
            )
        }
    }
}

/**
 * One layout row (2026-09-28 retool: borderless, two lines, the description strip gone):
 *  - line 1 — the layout's name (the controls view's input-label colour), the like count at
 *    the end in the same variant AND colour (filled heart + count; the count grows LEFT while
 *    the icon→digit gap holds);
 *  - line 2 — "by <author>" in the same text variant, in the bar overline's colour (the
 *    "Active layout" line), with the [installed] marker at the end.
 *
 * No fill and no ring at rest — the drawer is a bar surface, and its rows read as a list on it
 * rather than a stack of buttons. The ACTIVE layout's row wears the highlight plane — the
 * design language's selected/active marking.
 *
 * [installed] draws the download glyph (2026-08-30): it replaced the INSTALLED/COMMUNITY
 * section headers, so the marker on the row — not a header above a run of them — is what
 * separates a layout already on this device from one that isn't.
 */
@Composable
private fun LayoutCard(
    layout: Layout,
    active: Boolean,
    installed: Boolean,
    onPreview: () -> Unit,
    onActivate: () -> Unit,
) {
    // The selection plane's content is onPrimary, its secondary text the same role softened
    // (no onPrimaryVariant exists).
    val primaryContent = if (active) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val secondaryContent = if (active) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = SecondaryOnHighlightAlpha)
    } else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        verticalArrangement = Arrangement.spacedBy(RowLineGap),
        modifier = Modifier
            .drawerRow(
                active = active,
                onClickLabel = "Activate ${layout.name}",
                onFocus = onPreview,
                onClick = onActivate,
            )
            .padding(CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = layout.name,
                style = minputMiniTextStyle(),
                color = primaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(MinputGlyphLabelGap))
            // The count is LINE 1's trailing item, so it wears line 1's colour as well as its
            // text variant (2026-10-05): in the dimmer secondary grey the same face read as a
            // lighter, smaller one beside the title.
            MinputIcon(
                FilledHeartIcon,
                contentDescription = "Likes",
                size = MinputIconSize.Xxs,
                tint = primaryContent,
            )
            Spacer(Modifier.width(LikeCountGap))
            Text(
                text = layout.likeCount.toString(),
                style = minputMiniTextStyle(),
                color = primaryContent,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "by ${layout.author.ifEmpty { "you" }}",
                style = minputMiniTextStyle(),
                color = secondaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (installed) {
                Spacer(Modifier.width(MinputGlyphLabelGap))
                // Ink-measured: the glyph's own margin used to hold it 2.5dp off the end.
                MinputIcon(
                    Icons.Filled.Download,
                    contentDescription = "Installed",
                    size = InstalledIconSize,
                    tint = secondaryContent,
                )
            }
        }
    }
}

/**
 * The list's permanent FIRST row (2026-08-30 — it used to close the Installed section),
 * routing into the create-layout flow. Borderless like the layout rows beneath it
 * (2026-09-28).
 *
 * A real leading [Icon] — the action-set group button's Lucide plus — replaces the
 * 2026-08-25 TEXT-glyph "+ ": that note (a single string self-centers on both axes, where
 * an icon + gap + label row is only geometrically centered, so the eye reads the label
 * (icon + gap)/2 right of true center) still holds, and Dylan chose the proper glyph over
 * it. If the offset reads badly on device, bias the Row's end padding rather than going
 * back to the text plus.
 */
@Composable
private fun NewLayoutCard(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .drawerRow(active = false, onClickLabel = "New layout", onClick = onClick)
            .height(NewLayoutCardHeight),
    ) {
        Icon(
            Lucide.Plus,
            contentDescription = null,
            modifier = Modifier.size(MinputSize.Standard.iconSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = "New layout",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * **One row of the drawer's lists** — layouts, the new-layout row, applications (2026-09-28).
 * Full width, borderless: no fill at rest, the highlight plane when [active]; the family's
 * press lift, ripple and focus state; [onFocus] fires as controller focus lands (browsing
 * previews). One helper so the three row kinds can't drift apart.
 */
@Composable
internal fun Modifier.drawerRow(
    active: Boolean,
    onClickLabel: String,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(MinputMorphCorner)
    return this
        .fillMaxWidth()
        .minputInteractiveMotion(interaction)
        .onFocusChanged { if (it.isFocused) onFocus() }
        .clip(shape)
        .background(if (active) minputHighlightContainer() else Color.Transparent)
        .clickable(
            interactionSource = interaction,
            indication = minputIndication(),
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
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


/** Interior padding of a drawer list row. */
internal val CardPadding = 8.dp


/** The installed (download) marker under the like count — shared with the application rows. */
internal val InstalledIconSize = MinputIconSize.Xs

/** Fixed gap between the heart and the count's first digit. */
private val LikeCountGap = 4.dp

/**
 * The air between the search field and the sort button — set so the sort glyph reads CENTRED
 * between the field and the drawer's lit edge (Dylan, 2026-10-06: it read further from the
 * field than from the edge, and measured so — 11.5dp against 10.5dp).
 *
 * Two corrections off the control rhythm: the drawer's END edge is its lit line, which sits a
 * stroke inside the pane, so the edge side was a stroke short; and the field's end is a full
 * pill arc, whose visual mass sits inboard of its geometric edge — the library's round-end rule
 * ([com.mappo.ui.minput.minputRoundEndBias]) — so the gap beside it reads wider than it measures.
 */
private val SearchSortGap = DrawerControlGap - MinputBoxStroke - MinputRoundEndBias / 2

/** Gap between a layout row's two lines. */
private val RowLineGap = 2.dp

/** Gap between the drawer's list rows — tighter than the control rhythm: the rows are
 *  borderless, so they read as one list rather than a stack of cards. */
internal val DrawerRowGap = 2.dp

/** Height of the [NewLayoutCard] — slimmer than a populated card, tall enough to read as
 *  a card slot rather than a button. */
private val NewLayoutCardHeight = 36.dp

/** Secondary text on the highlight plane: onPrimary softened, since the scheme has no
 *  dedicated secondary-on-primary role. */
internal const val SecondaryOnHighlightAlpha = 0.8f
