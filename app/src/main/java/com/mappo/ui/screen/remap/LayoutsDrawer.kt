package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.mappo.data.model.Profile
import com.mappo.ui.component.AppIconImage
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputBarHeight
import com.mappo.ui.minput.MinputBarIconTextGap
import com.mappo.ui.minput.MinputBarStackGap
import com.mappo.ui.minput.MinputBarWidgetIconSize
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputMorphCorner
import com.mappo.ui.minput.MinputPanelDividerContentGap
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.MinputPillIconSize
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMicroTextStyle
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import kotlinx.collections.immutable.ImmutableList

/**
 * The layouts drawer (2026-08-21) — the layouts view rebuilt as a push pane over the
 * controls home: the top bar's change button slides it in from the left, COMPRESSING the
 * controls view beside it (no scrim, no modality — the controls view stays fully
 * interactive) so scrolling the layout cards live-previews each one in the controls view
 * behind (via [onPreviewLayout] → the VM viewing pointer). The frame's bottom bar sits
 * outside the route content, so it keeps its full width underneath.
 *
 * Anatomy: a header row reusing the top bar's identity cluster (change button · app icon ·
 * overline "Layouts" / app name), a New · Search · Sort controls row (New/Sort are
 * unwired placeholders — Dylan wants their fit reviewed before behavior lands), then the
 * card list in three categories: **Default** (the app's bound default layout), **Installed**
 * (on-device layouts for this app), **Community** (published, not-yet-installed layouts —
 * empty until community sharing lands).
 *
 * Tapping a card runs the standard activate flow (the caller wraps [onActivateLayout] in
 * the auto-detection warning gate); a future submenu replaces the direct activation.
 */
@Composable
internal fun LayoutsDrawerPane(
    open: Boolean,
    onClose: () -> Unit,
    appPackage: String?,
    appLabel: String?,
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    defaultProfileId: Long?,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Absolute width, specified in PHYSICAL pixels (not dp): 320px = exactly 25% of the
    // 1280×960 test device, pushing the controls view there into a perfect 1:1 square.
    val drawerWidth = with(LocalDensity.current) { LayoutsDrawerWidthPx.toDp() }
    val animatedWidth by animateDpAsState(
        targetValue = if (open) drawerWidth else 0.dp,
        animationSpec = tween(DrawerSlideMillis, easing = FastOutSlowInEasing),
        label = "layoutsDrawer",
    )
    // Physical/gesture back closes the drawer. Registered here — after the screen's base
    // BackHandler, before the content's own dismissables (group editor, panels), which
    // compose later and rightly win while open.
    BackHandler(enabled = open) { onClose() }

    // The pane clips a fixed-width sheet whose right edge rides the animated width — the
    // conventional drawer slide, while the Row neighbor (the controls view) resizes.
    Box(modifier.fillMaxHeight().width(animatedWidth).clipToBounds()) {
        if (animatedWidth > 0.dp) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .offset { IntOffset((animatedWidth - drawerWidth).roundToPx(), 0) },
            ) {
                // surfaceContainer — chrome pane, the bars' plane (a non-modal side panel,
                // not an M3 modal drawer — no scrim, content beside it stays live).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    LayoutsDrawerContent(
                        onClose = onClose,
                        appPackage = appPackage,
                        appLabel = appLabel,
                        profiles = profiles,
                        activeProfileId = activeProfileId,
                        defaultProfileId = defaultProfileId,
                        onPreviewLayout = onPreviewLayout,
                        onActivateLayout = onActivateLayout,
                    )
                }
                VerticalDivider()
            }
        }
    }
}

@Composable
private fun LayoutsDrawerContent(
    onClose: () -> Unit,
    appPackage: String?,
    appLabel: String?,
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    defaultProfileId: Long?,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Profile) -> Unit,
) {
    // Live name filter — local so a fresh open starts clean isn't wanted here: the drawer
    // stays composed across open/close, which conveniently keeps the query while browsing.
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        // The header: the top bar's identity-cluster anatomy verbatim (change button ·
        // app icon · overline/value stack), no divider beneath (per spec).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(MinputBarHeight)
                .padding(horizontal = MinputBarEdgePadding),
        ) {
            MinputIconButton(
                icon = Lucide.ArrowLeftRight,
                contentDescription = "Close layouts",
                onClick = onClose,
            )
            Spacer(Modifier.width(MinputGlyphLabelGap))
            val icon = rememberAppIconPainter(appPackage)
            if (icon != null) {
                AppIconImage(icon, size = MinputBarWidgetIconSize)
            } else {
                Icon(
                    Icons.Filled.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(MinputBarWidgetIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(MinputBarIconTextGap))
            Column(verticalArrangement = Arrangement.spacedBy(MinputBarStackGap)) {
                Text(
                    text = "Layouts".uppercase(),
                    style = minputOverlineTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = appLabel ?: "All layouts",
                    style = minputMiniTextStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // New · Search · Sort. New and Sort are deliberate no-ops for now — placed to
        // review how the trio fits the pane width before wiring behavior.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DrawerControlGap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PanelContentPadding,
                    end = PanelContentPadding,
                    bottom = MinputPanelDividerContentGap,
                ),
        ) {
            MinputPillButton(
                text = "New",
                onClick = { /* new-layout form — next brick */ },
                leadingIcon = rememberVectorPainter(Lucide.Plus),
                leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MinputTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search",
                leadingIcon = Lucide.Search,
                clearable = true,
                // Top-of-screen field — the sanctioned modal-less variant.
                inlineEdit = true,
                modifier = Modifier.weight(1f),
            )
            MinputPillButton(
                text = "Sort",
                onClick = { /* sort menu — next brick */ },
                leadingIcon = rememberVectorPainter(Lucide.ArrowUpDown),
                leadingIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Category assembly. Membership = Profile.packageName (2026-08-21 model);
        // the app's default = its auto-switch binding. No app context → every layout
        // under Installed. Community stays empty until sharing lands.
        val children = if (appPackage != null) {
            profiles.filter { it.packageName == appPackage }
        } else profiles
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) children
        else children.filter { it.name.contains(trimmed, ignoreCase = true) }
        val default = filtered.filter { it.id == defaultProfileId }
        val installed = filtered.filterNot { it.id == defaultProfileId }
        val community = emptyList<Profile>()

        val listState = rememberLazyListState()
        // Scroll-driven live preview: the topmost card whose center has cleared the
        // viewport top is "current" — as cards scroll past, the controls view behind the
        // drawer re-renders each layout in turn. Card focus (d-pad) previews too, via
        // each card's own focus observer.
        val previewedId by remember {
            derivedStateOf { listState.topmostCardId() }
        }
        LaunchedEffect(previewedId) { previewedId?.let(onPreviewLayout) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(DrawerControlGap),
            contentPadding = PaddingValues(
                start = PanelContentPadding,
                end = PanelContentPadding,
                bottom = PanelContentPadding,
            ),
        ) {
            drawerSection("Default", default, emptyHint = "No default layout") { profile ->
                LayoutCard(
                    profile = profile,
                    active = profile.id == activeProfileId,
                    onPreview = { onPreviewLayout(profile.id) },
                    onActivate = { onActivateLayout(profile) },
                )
            }
            drawerSection("Installed", installed, emptyHint = "No layouts yet") { profile ->
                LayoutCard(
                    profile = profile,
                    active = profile.id == activeProfileId,
                    onPreview = { onPreviewLayout(profile.id) },
                    onActivate = { onActivateLayout(profile) },
                )
            }
            drawerSection("Community", community, emptyHint = "No community layouts yet") { profile ->
                LayoutCard(
                    profile = profile,
                    active = profile.id == activeProfileId,
                    onPreview = { onPreviewLayout(profile.id) },
                    onActivate = { onActivateLayout(profile) },
                )
            }
        }
    }
}

/** One category of the card list: overline header, then cards (or a muted empty hint). */
private fun androidx.compose.foundation.lazy.LazyListScope.drawerSection(
    title: String,
    sectionProfiles: List<Profile>,
    emptyHint: String,
    card: @Composable (Profile) -> Unit,
) {
    item(key = "header:$title", contentType = "header") {
        Text(
            text = title.uppercase(),
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MinputPanelDividerContentGap),
        )
    }
    if (sectionProfiles.isEmpty()) {
        item(key = "empty:$title", contentType = "empty") {
            Text(
                text = emptyHint,
                style = minputMicroTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = MinputGlyphLabelGap),
            )
        }
    } else {
        items(sectionProfiles, key = { CardKeyPrefix + it.id }, contentType = { "card" }) { card(it) }
    }
}

/**
 * One layout card: name over author on the left; the right stack reserves the width of
 * its "ACTIVE" overline (shown only on the active layout, in the highlight color) above
 * the heart + like count; then the two-line description strip. [showDescription] is the
 * seam for a future compact/full drawer density setting — compact hides the strip.
 */
@Composable
private fun LayoutCard(
    profile: Profile,
    active: Boolean,
    onPreview: () -> Unit,
    onActivate: () -> Unit,
    showDescription: Boolean = true,
) {
    val container = minputBoxContainer()
    val interaction = remember { MutableInteractionSource() }
    // surface 1 — a control tile on the pane, wearing the family box treatment.
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
                onClickLabel = "Activate ${profile.name}",
                onClick = onActivate,
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(CardPadding)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profile.author.ifEmpty { "You" },
                        style = minputMicroTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Always composed so the stack's width (and the row's height rhythm)
                    // never changes with activation — invisible when inactive.
                    Text(
                        text = "Active".uppercase(),
                        style = minputOverlineTextStyle(),
                        // Highlight plane color — selection marking.
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.alpha(if (active) 1f else 0f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Lucide.Heart,
                            contentDescription = "Likes",
                            modifier = Modifier.size(MinputPillIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(MinputBarStackGap * 2))
                        Text(
                            text = profile.likeCount.toString(),
                            style = minputMicroTextStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (showDescription) {
                Spacer(Modifier.height(MinputBarStackGap * 2))
                Text(
                    // Two lines are always reserved (minLines) so card heights stay
                    // uniform whether or not a description exists.
                    text = profile.description,
                    style = minputMicroTextStyle().copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The id of the topmost visible layout card whose vertical center has cleared the
 *  viewport top — the scroll-preview's "current" card (null while no cards are visible). */
private fun LazyListState.topmostCardId(): Long? {
    val info = layoutInfo
    val cards = info.visibleItemsInfo.filter {
        (it.key as? String)?.startsWith(CardKeyPrefix) == true
    }
    val topmost = cards.firstOrNull { it.offset + it.size / 2 >= info.viewportStartOffset }
        ?: cards.lastOrNull()
    return (topmost?.key as? String)?.removePrefix(CardKeyPrefix)?.toLongOrNull()
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

/** Gap between the drawer's control-row members and between list cards (the filter rows'
 *  6dp rhythm). */
private val DrawerControlGap = 6.dp

/** Interior padding of a layout card. */
private val CardPadding = 8.dp
