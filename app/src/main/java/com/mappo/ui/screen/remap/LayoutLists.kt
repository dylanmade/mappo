package com.mappo.ui.screen.remap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.mappo.data.model.Layout
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputMorphCorner
import com.mappo.ui.minput.MinputSize
import com.mappo.ui.minput.minputHighlightContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import androidx.compose.foundation.background
import com.mappo.ui.minput.MinputIcon
import com.mappo.ui.minput.MinputIconSize
import kotlinx.collections.immutable.ImmutableList

/**
 * The layout and application PICKERS (2026-10-07) — the lists the hierarchy bar's Application and
 * Layout crumbs drop down ([RemapHierarchyBar]). They were the layouts drawer's body until the
 * drawer retired the same day; the rows, sorts and preview semantics carried over unchanged:
 *  - [LayoutsList]: "+ New layout", then **Installed** (on-device layouts for the viewed
 *    application) and **Community** (empty until sharing lands) as borderless two-line rows. The
 *    active layout's row wears the highlight plane — the active layout IS its application's
 *    functional default.
 *  - focusing a row (d-pad) previews it in the controls view behind; tapping commits (a layout
 *    activates through the caller's auto-detection gate).
 */
@Composable
internal fun LayoutsList(
    appPackage: String?,
    layouts: ImmutableList<Layout>,
    activeLayoutId: Long?,
    query: String,
    sort: LayoutSort,
    onPreviewLayout: (Long) -> Unit,
    onActivateLayout: (Layout) -> Unit,
    onNewLayout: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth(),
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
        MinputIcon(
            Lucide.Plus,
            contentDescription = null,
            size = MinputSize.Standard.iconSize,
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

/** Gap between the drawer's control-row members and between list cards (the filter rows'
 *  6dp rhythm). */
internal val DrawerControlGap = 6.dp


/** Interior padding of a drawer list row. */
internal val CardPadding = 8.dp


/** The installed (download) marker under the like count — shared with the application rows. */
internal val InstalledIconSize = MinputIconSize.Xs

/** Fixed gap between the heart and the count's first digit. */
private val LikeCountGap = 4.dp

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
