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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.mappo.data.model.Profile
import com.mappo.ui.minput.MinputElevatedContainer
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputMorphCorner
import com.mappo.ui.minput.MinputPanelDividerContentGap
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputHighlightContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMicroTextStyle
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import kotlinx.collections.immutable.ImmutableList

/**
 * The layouts drawer (2026-08-21) — the layouts view rebuilt as a push pane in the
 * controls home: the top bar's change button slides it in from the left, COMPRESSING the
 * controls content beside it (no scrim, no modality — the controls view stays fully
 * interactive) so scrolling the layout cards live-previews each one in the controls view
 * behind (via [onPreviewLayout] → the VM viewing pointer). The pane opens BETWEEN the
 * bars (2026-08-24): the top bar and the frame's bottom bar both keep their full width
 * above/below it.
 *
 * Anatomy: a New · Search · Sort controls row (New/Sort are unwired icon-button
 * placeholders — Dylan wants their fit reviewed before behavior lands), then the card
 * list in two categories: **Installed** (on-device layouts for this app) and **Community**
 * (published, not-yet-installed layouts — empty until community sharing lands). The
 * active layout's card wears the highlight plane — no separate default-layout concept:
 * the active layout IS its application's functional default.
 *
 * Tapping a card runs the standard activate flow (the caller wraps [onActivateLayout] in
 * the auto-detection warning gate); a future submenu replaces the direct activation.
 */
@Composable
internal fun LayoutsDrawerPane(
    open: Boolean,
    onClose: () -> Unit,
    appPackage: String?,
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
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
                        appPackage = appPackage,
                        profiles = profiles,
                        activeProfileId = activeProfileId,
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
    appPackage: String?,
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Profile) -> Unit,
) {
    // Live name filter — local so a fresh open starts clean isn't wanted here: the drawer
    // stays composed across open/close, which conveniently keeps the query while browsing.
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        // New · Search · Sort — the pane's first row since the identity header retired
        // (2026-08-24: the top bar above the pane already carries the context). New and
        // Sort are icon-only so the search field gets the width; both deliberate no-ops
        // for now.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DrawerControlGap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PanelContentPadding,
                    top = PanelContentPadding,
                    end = PanelContentPadding,
                    bottom = MinputPanelDividerContentGap,
                ),
        ) {
            MinputPillButton(
                onClick = { /* new-layout form — next brick */ },
                leadingIcon = rememberVectorPainter(Lucide.Plus),
                contentDescription = "New layout",
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
                onClick = { /* sort menu — next brick */ },
                leadingIcon = rememberVectorPainter(Lucide.ArrowUpDown),
                contentDescription = "Sort layouts",
            )
        }

        // Category assembly. Membership = Profile.packageName (2026-08-21 model). No app
        // context → every layout under Installed. Community stays empty until sharing
        // lands. (The Default category retired with the default-layout concept —
        // 2026-08-24: the active layout IS the app's functional default.)
        val children = if (appPackage != null) {
            profiles.filter { it.packageName == appPackage }
        } else profiles
        val trimmed = query.trim()
        val installed = if (trimmed.isEmpty()) children
        else children.filter { it.name.contains(trimmed, ignoreCase = true) }
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
    profile: Profile,
    active: Boolean,
    onPreview: () -> Unit,
    onActivate: () -> Unit,
    showDescription: Boolean = true,
) {
    // surface 2 resting / highlight when active — the selection plane's content is
    // onPrimary, its secondary text the same role softened (no onPrimaryVariant exists).
    val container = if (active) minputHighlightContainer() else MinputElevatedContainer
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
                        color = primaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profile.author.ifEmpty { "You" },
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
                        text = profile.likeCount.toString(),
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
                    text = profile.description,
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

/** Gap between the drawer's control-row members and between list cards (the filter rows'
 *  6dp rhythm). */
private val DrawerControlGap = 6.dp

/** Interior padding of a layout card. */
private val CardPadding = 8.dp

/** The like heart, sized to the micro text line beside it. */
private val LikeIconSize = 10.dp

/** Fixed gap between the heart and the count's first digit. */
private val LikeCountGap = 4.dp

/** Gap above the description strip. */
private val DescriptionGap = 2.dp

/** Secondary text on the highlight plane: onPrimary softened, since the scheme has no
 *  dedicated secondary-on-primary role. */
private const val SecondaryOnHighlightAlpha = 0.8f
