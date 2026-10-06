package com.mappo.ui.component

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUpToLine
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Star
import com.mappo.ui.minput.minputStrokeInset
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputMorphCorner
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputSize
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputHighlightContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMicroTextStyle
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import com.mappo.ui.screen.softDropShadow
import com.themestudio.core.LocalFontRegistry
import com.themestudio.core.LocalThemeStudioController
import com.themestudio.core.UmbrellaRoles
import com.themestudio.core.rememberGoogleFontsCatalog
import com.themestudio.core.rememberThemeFontResolver
import kotlinx.coroutines.launch

/**
 * Floating font-debug widget (dev tooling): a collapsed chip pinned wherever the caller
 * aligns it (bottom-right by convention) that expands UPWARD into a minput-styled font
 * list — search field + jump-to-top, "(Theme default)", free-text apply, then starred
 * favorites pinned above local fonts above the Google Fonts catalog, every row's sample
 * rendered in its own family. The panel opens SCROLLED TO the currently applied font;
 * favorites persist in SharedPreferences.
 *
 * Data + application ride the Theme Studio plumbing ([LocalThemeStudioController],
 * [rememberGoogleFontsCatalog]); the chrome is minput's (the widget floats over Mappo
 * surfaces, so it must read as Mappo, not as the studio's stock-M3 dev pages). Picking a
 * font applies it to BOTH umbrellas in one tap — exactly "select a Display family, then
 * press Copy Display → Body" in the studio: both family names move to the pick and the
 * body umbrella copies the display umbrella's overrides. Because the app theme observes
 * the controller as Compose state, the whole UI re-renders in the pick immediately.
 *
 * The caller owns visibility (the wordmark drawer's toggle) and alignment; the widget
 * owns its own expand/collapse state.
 */
@Composable
fun FontDebugOverlay(modifier: Modifier = Modifier) {
    val controller = LocalThemeStudioController.current
    val displayName = controller.overrides.typography.displayFontFamilyName
    var expanded by remember { mutableStateOf(false) }
    // Persists across open/close like the layouts drawer's filter — reopening mid-hunt
    // keeps the narrowed list.
    var query by remember { mutableStateOf("") }

    val resolve = rememberThemeFontResolver()
    val catalog = rememberGoogleFontsCatalog()
    val locals = remember { LocalFontRegistry.all }

    // Favorites survive process death (font hunts span sessions) — plain prefs, matching
    // the studio's own SharedPrefs persistence weight class for dev tooling.
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet(FavoritesKey, emptySet()).orEmpty().toSet())
    }
    val toggleFavorite: (String) -> Unit = { name ->
        val next = if (name in favorites) favorites - name else favorites + name
        favorites = next
        prefs.edit().putStringSet(FavoritesKey, next).apply()
    }

    val applyFont: (String) -> Unit = { name ->
        // One tap = pick Display + "Copy Display → Body": read the display umbrella
        // BEFORE mutating so the copy mirrors the studio button.
        val displayUmbrella = controller.overrides.typography.displayUmbrella
        controller.setDisplayFontFamilyName(name)
        controller.setBodyFontFamilyName(name)
        controller.setTypographyRole(UmbrellaRoles.body, displayUmbrella)
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier.padding(EdgeMargin),
    ) {
        AnimatedVisibility(
            visible = expanded,
            // Bottom-anchored, so vertical expansion grows the panel upward off the chip.
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            val container = minputBoxContainer()
            val shape = RoundedCornerShape(MinputMorphCorner)
            // Minput floating-card chrome (the MinputDialog card's recipe): box fill +
            // bevel + soft shadow; raw background, so provide the content color a
            // Surface would.
            Column(
                modifier = Modifier
                    .padding(bottom = ChipPanelGap)
                    .width(PanelWidth)
                    .heightIn(max = PanelMaxHeight)
                    .softDropShadow(cornerRadius = MinputMorphCorner, offsetY = 0.dp)
                    .clip(shape)
                    .background(container)
                    .border(minputBevelBorder(container, MinputMorphCorner), shape)
                    .minputStrokeInset()
                    .padding(PanelPadding),
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // High-on-screen field (the panel top): the sanctioned modal-less
                        // variant, so filtering is keystroke-live under the IME overlay.
                        MinputTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search fonts",
                            leadingIcon = Lucide.Search,
                            clearable = true,
                            inlineEdit = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(SearchRowGap))
                        // Back to the list head — where favorites live.
                        MinputButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            leadingIcon = rememberVectorPainter(Lucide.ArrowUpToLine),
                            contentDescription = "Jump to top",
                        )
                    }

                    val trimmed = query.trim()
                    val favoriteNames = remember(favorites) { favorites.sorted() }
                    val filteredFavorites = if (trimmed.isEmpty()) favoriteNames
                    else favoriteNames.filter { it.contains(trimmed, ignoreCase = true) }
                    val filteredLocals = if (trimmed.isEmpty()) locals
                    else locals.filter { it.displayName.contains(trimmed, ignoreCase = true) }
                    val filteredCatalog = if (trimmed.isEmpty()) catalog
                    else catalog.filter { it.contains(trimmed, ignoreCase = true) }
                    val exactMatch = catalog.any { it.equals(trimmed, ignoreCase = true) } ||
                        locals.any { it.displayName.equals(trimmed, ignoreCase = true) }
                    val customRow = trimmed.isNotEmpty() && !exactMatch

                    // Open scrolled to the applied font, not the list top. Runs on every
                    // panel entrance (the content leaves composition while collapsed).
                    LaunchedEffect(Unit) {
                        val favoriteHit = filteredFavorites.indexOfFirst {
                            it.equals(displayName, ignoreCase = true)
                        }
                        val localHit = filteredLocals.indexOfFirst {
                            it.displayName.equals(displayName, ignoreCase = true)
                        }
                        val catalogHit = filteredCatalog.indexOfFirst {
                            it.equals(displayName, ignoreCase = true)
                        }
                        // Emission order: default row · custom row? · favorites header+rows ·
                        // local header+rows · catalog header+rows — indices must mirror the
                        // LazyColumn below.
                        val prefix = 1 + (if (customRow) 1 else 0)
                        val favoriteBlock =
                            if (filteredFavorites.isEmpty()) 0 else 1 + filteredFavorites.size
                        val localBlock = if (filteredLocals.isEmpty()) 0 else 1 + filteredLocals.size
                        val target = when {
                            displayName == null -> 0
                            favoriteHit >= 0 -> prefix + 1 + favoriteHit
                            localHit >= 0 -> prefix + favoriteBlock + 1 + localHit
                            catalogHit >= 0 -> prefix + favoriteBlock + localBlock + 1 + catalogHit
                            else -> return@LaunchedEffect // filtered out — stay put
                        }
                        listState.scrollToItem(target)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(RowGap),
                    ) {
                        item(key = "default", contentType = "row") {
                            FontOptionRow(
                                name = "(Theme default)",
                                sampleFamilyName = null,
                                resolve = { null },
                                selected = displayName == null,
                                onClick = {
                                    // Drop only what this widget sets — both family names.
                                    controller.setDisplayFontFamilyName(null)
                                    controller.setBodyFontFamilyName(null)
                                },
                            )
                        }
                        if (customRow) {
                            item(key = "custom", contentType = "row") {
                                FontOptionRow(
                                    name = "Apply \"$trimmed\"",
                                    sampleFamilyName = trimmed,
                                    resolve = { resolve(it) },
                                    selected = false,
                                    onClick = { applyFont(trimmed) },
                                )
                            }
                        }
                        if (filteredFavorites.isNotEmpty()) {
                            item(key = "header:favorites", contentType = "header") { FontSectionHeader("Favorites") }
                            items(filteredFavorites, key = { "favorite:$it" }, contentType = { "row" }) { name ->
                                FontOptionRow(
                                    name = name,
                                    sampleFamilyName = name,
                                    resolve = { resolve(it) },
                                    selected = name.equals(displayName, ignoreCase = true),
                                    onClick = { applyFont(name) },
                                    favorited = true,
                                    onToggleFavorite = { toggleFavorite(name) },
                                )
                            }
                        }
                        if (filteredLocals.isNotEmpty()) {
                            item(key = "header:local", contentType = "header") { FontSectionHeader("Local") }
                            items(filteredLocals, key = { "local:${it.displayName}" }, contentType = { "row" }) { spec ->
                                FontOptionRow(
                                    name = spec.displayName,
                                    sampleFamilyName = spec.displayName,
                                    resolve = { resolve(it) },
                                    selected = spec.displayName.equals(displayName, ignoreCase = true),
                                    onClick = { applyFont(spec.displayName) },
                                    favorited = spec.displayName in favorites,
                                    onToggleFavorite = { toggleFavorite(spec.displayName) },
                                )
                            }
                        }
                        if (filteredCatalog.isNotEmpty()) {
                            item(key = "header:google", contentType = "header") { FontSectionHeader("Google Fonts") }
                            items(filteredCatalog, key = { "google:$it" }, contentType = { "row" }) { name ->
                                FontOptionRow(
                                    name = name,
                                    sampleFamilyName = name,
                                    resolve = { resolve(it) },
                                    selected = name.equals(displayName, ignoreCase = true),
                                    onClick = { applyFont(name) },
                                    favorited = name in favorites,
                                    onToggleFavorite = { toggleFavorite(name) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // The collapsed chip, in the pill family's chrome: "Aa" rendered in the CURRENT
        // display family (a live one-glyph specimen) plus the active family name.
        val chipContainer = minputBoxContainer()
        val chipFamily = displayName?.let { remember(it, resolve) { resolve(it) } }
        val interaction = remember { MutableInteractionSource() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .minputInteractiveMotion(interaction)
                .softDropShadow(cornerRadius = MinputSize.Standard.corner, offsetY = 0.dp)
                .clip(RoundedCornerShape(50))
                .background(chipContainer)
                .border(minputBevelBorder(chipContainer, MinputSize.Standard.corner), RoundedCornerShape(50))
                .clickable(
                    interactionSource = interaction,
                    indication = minputIndication(),
                    onClickLabel = if (expanded) "Collapse font debug" else "Expand font debug",
                    onClick = { expanded = !expanded },
                )
                .height(MinputSize.Standard.height)
                .minputStrokeInset()
                .padding(horizontal = MinputSize.Standard.contentPadding),
        ) {
            Text(
                text = "Aa",
                style = minputMiniTextStyle().copy(fontFamily = chipFamily),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(MinputGlyphLabelGap))
            Text(
                text = displayName ?: "Theme default",
                style = minputMicroTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = ChipNameMaxWidth),
            )
        }
    }
}

/** Section overline, matching the layouts drawer's card-section headers. */
@Composable
private fun FontSectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = minputOverlineTextStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = SectionHeaderGap),
    )
}

/**
 * One pickable font: name over a sample line rendered in the family itself (resolving is
 * what triggers the on-demand GMS download for catalog entries; only visible rows
 * compose, so the list never requests everything). Selection wears the highlight plane —
 * the design language's selected marking. When [onToggleFavorite] is non-null, a star
 * rides the row's right edge (filled = favorited) with its own tap target, separate from
 * the row's apply tap.
 */
@Composable
private fun FontOptionRow(
    name: String,
    sampleFamilyName: String?,
    resolve: (String) -> androidx.compose.ui.text.font.FontFamily?,
    selected: Boolean,
    onClick: () -> Unit,
    favorited: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val family = sampleFamilyName?.let { remember(it) { resolve(it) } }
    val nameColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val sampleColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCorner))
            .then(
                if (selected) Modifier.background(minputHighlightContainer()) else Modifier,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = minputIndication(),
                onClick = onClick,
            )
            .padding(horizontal = RowPadding, vertical = RowPadding / 2),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = minputMicroTextStyle(),
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sampleFamilyName != null) {
                Text(
                    // bodyLarge, not a minput mini style: judging a font needs real text size.
                    text = SAMPLE,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = family),
                    color = sampleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onToggleFavorite != null) {
            val starTint = when {
                selected -> MaterialTheme.colorScheme.onPrimary
                favorited -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(FavoriteHitSize)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = minputIndication(),
                        onClickLabel =
                            if (favorited) "Remove from favorites" else "Add to favorites",
                        onClick = onToggleFavorite,
                    ),
            ) {
                Icon(
                    imageVector = if (favorited) FilledStarIcon else Lucide.Star,
                    contentDescription = null,
                    tint = starTint,
                    modifier = Modifier.size(FavoriteIconSize),
                )
            }
        }
    }
}

/** Lucide's star polygon, filled — the shipped glyph set is stroke-only; the fill paints
 *  with the Icon tint (same trick as the layouts drawer's FilledHeart). */
private val FilledStarIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilledStar",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25" +
                "L7 14.14 2 9.27l6.91-1.01L12 2z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

private const val SAMPLE = "The quick brown fox jumps over 0123"

/** Prefs file for the widget's own state (favorites). */
private const val PrefsName = "font_debug_overlay"

/** Prefs key: the favorited font names, verbatim catalog/local display names. */
private const val FavoritesKey = "favorites"

/** Margin between the widget and the screen edges. */
private val EdgeMargin = 12.dp

/** Gap between the expanded panel and the chip beneath it. */
private val ChipPanelGap = 8.dp

/** Interior padding of the expanded panel. */
private val PanelPadding = 8.dp

/** Expanded panel width — enough for the search field + font rows. */
private val PanelWidth = 300.dp

/** Expanded panel height cap; shorter screens clamp it via the incoming constraints. */
private val PanelMaxHeight = 380.dp

/** Width cap for the chip's family-name label so long font names can't grow the chip
 *  across the screen. */
private val ChipNameMaxWidth = 120.dp

/** Corner radius of a font row's selection/ripple clip. */
private val RowCorner = 6.dp

/** Font-row interior padding (halved vertically — the sample line carries the height). */
private val RowPadding = 8.dp

/** Gap between rows / above section headers (the filter rows' rhythm, halved). */
private val RowGap = 3.dp

/** Gap between the search field and the jump-to-top button (the layouts drawer's
 *  controls-row rhythm). */
private val SearchRowGap = 6.dp

/** Tap target of a row's favorite star. */
private val FavoriteHitSize = 24.dp

/** Glyph size of a row's favorite star. */
private val FavoriteIconSize = 14.dp

/** Extra breathing room above a section overline. */
private val SectionHeaderGap = 4.dp
