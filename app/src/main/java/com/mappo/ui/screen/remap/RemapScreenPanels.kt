package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.Profile
import com.mappo.ui.compact.scaledLayout
import com.mappo.ui.control.MappoIconButton
import com.mappo.ui.control.MappoElevatedContainer
import com.mappo.ui.control.MappoGlyphLabelGap
import com.mappo.ui.control.MappoPillContentPadding
import com.mappo.ui.control.MappoPillIconSize
import com.mappo.ui.control.mappoBevelBorder
import com.mappo.ui.control.mappoBoxContainer
import com.mappo.ui.control.mappoMiniTextStyle
import com.mappo.ui.control.mappoOverlineTextStyle
import com.mappo.ui.screen.softDropShadow
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/**
 * The two full-screen views summoned from the top bar's corner pills (or their physical
 * buttons: Select → profile, Start → options).
 */
internal enum class RemapPanel { PROFILE, OPTIONS }

/**
 * One tappable row of the options panel — the destinations that used to live in the old home
 * screen's options fly-out. The caller owns navigation; the panel closes itself before firing.
 */
data class RemapOptionEntry(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Full-screen morphing overlay for the profile / options panels. Same treatment as the
 * group editor's in-place morph ([RemapSimpleView]): the summoning pill's captured bounds
 * rect-lerp to the whole screen minus [EditorMargin] — but hosted at the SCREEN root, so
 * unlike the group editor this covers the top tab bar too. Content fades in with the morph.
 *
 * The caller (RemapControlsScreen) hosts this as the last child of its root Box with
 * `Modifier.matchParentSize()` and owns [openPanel] (user intent, rememberSaveable there).
 */
@Composable
internal fun RemapPanelOverlay(
    openPanel: RemapPanel?,
    onClose: () -> Unit,
    buttonBounds: (RemapPanel) -> Rect?,
    rootSize: IntSize,
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    onSelectProfile: (Profile) -> Unit,
    powerOn: Boolean,
    onPowerChange: (Boolean) -> Unit,
    optionsEntries: List<RemapOptionEntry>,
    modifier: Modifier = Modifier,
) {
    // User intent (openPanel) vs. what's on screen mid-animation (visiblePanel) — the same
    // state pair as the group editor morph. Switching panels collapses the current one back
    // into its pill before expanding the other.
    var visiblePanel by remember { mutableStateOf(openPanel) }
    val progress = remember { Animatable(if (openPanel != null) 1f else 0f) }
    LaunchedEffect(openPanel) {
        val target = openPanel
        if (target == visiblePanel) {
            if (target != null && progress.value < 1f) {
                progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
            }
            return@LaunchedEffect
        }
        if (visiblePanel != null) {
            progress.animateTo(0f, tween(CollapseMillis, easing = FastOutSlowInEasing))
            visiblePanel = null
        }
        if (target != null) {
            visiblePanel = target
            progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
        }
    }

    // Composed after the screen's (and the group editor's) BackHandlers, so this wins while a
    // panel is up — back closes the panel, not the screen.
    BackHandler(enabled = openPanel != null) { onClose() }

    val vp = visiblePanel
    val origin = vp?.let { buttonBounds(it) }
    if (vp == null || origin == null || rootSize == IntSize.Zero) return

    val marginPx = with(LocalDensity.current) { EditorMargin.toPx() }
    val target = Rect(
        offset = Offset(marginPx, marginPx),
        size = Size(rootSize.width - marginPx * 2, rootSize.height - marginPx * 2),
    )
    val shape = RoundedCornerShape(GroupCorner)
    val container = mappoBoxContainer()
    Box(modifier) {
        Box(
            modifier = Modifier
                // Per-frame rect is read in the LAYOUT phase; fades in the DRAW phase — the
                // no-recompose-per-frame lesson from the group-editor morph.
                .layout { measurable, constraints ->
                    val rect = lerp(origin, target, progress.value)
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            rect.width.roundToInt().coerceAtLeast(1),
                            rect.height.roundToInt().coerceAtLeast(1),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                }
                .softDropShadow(cornerRadius = GroupCorner)
                .clip(shape)
                .background(container)
                .border(mappoBevelBorder(container, GroupCorner), shape)
                .testTag("remap-panel:" + vp.name),
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }) {
                when (vp) {
                    RemapPanel.PROFILE -> ProfilePanelContent(
                        profiles = profiles,
                        activeProfileId = activeProfileId,
                        onSelectProfile = onSelectProfile,
                        onClose = onClose,
                    )
                    RemapPanel.OPTIONS -> OptionsPanelContent(
                        powerOn = powerOn,
                        onPowerChange = onPowerChange,
                        entries = optionsEntries,
                        onClose = onClose,
                    )
                }
            }
        }
    }
}

/**
 * Profile selection, v1: a basic list of the current profiles in the group-editor's
 * dimensionality. Selecting a profile activates it and closes the panel. (Add / duplicate /
 * delete and richer layout return in a later pass — this view is deliberately minimal.)
 */
@Composable
private fun ProfilePanelContent(
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    onSelectProfile: (Profile) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(glyphRes = R.drawable.xbox_button_view, title = "Profiles", onClose = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = PanelContentPadding, vertical = 2.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                PanelRow(
                    onClick = { onClose(); onSelectProfile(profile) },
                    active = profile.id == activeProfileId,
                ) {
                    // Leading slot keeps labels aligned whether or not the check shows.
                    Box(Modifier.size(PanelRowIconSize), contentAlignment = Alignment.Center) {
                        if (profile.id == activeProfileId) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Active profile",
                                modifier = Modifier.size(PanelRowIconSize),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(MappoGlyphLabelGap))
                    Text(
                        text = profile.name,
                        style = mappoMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The options panel: the master Power switch (rehomed from the old home screen) followed by
 * the destination rows that used to live in the home options fly-out.
 */
@Composable
private fun OptionsPanelContent(
    powerOn: Boolean,
    onPowerChange: (Boolean) -> Unit,
    entries: List<RemapOptionEntry>,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(glyphRes = R.drawable.xbox_button_menu, title = "Options", onClose = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = PanelContentPadding, vertical = 2.dp),
        ) {
            item(key = "power") {
                PowerRow(powerOn = powerOn, onPowerChange = onPowerChange)
            }
            items(entries, key = { it.id }) { entry ->
                PanelRow(onClick = { onClose(); entry.onClick() }) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(PanelRowIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(MappoGlyphLabelGap))
                    Text(
                        text = entry.label,
                        style = mappoMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Sticky panel header, matching the group editor's header anatomy: the summoning physical
 * button's prompt glyph as identity, overline title, Close. Prompts render UNTINTED (fixed
 * hardware colors).
 */
@Composable
private fun PanelHeader(glyphRes: Int, title: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelHeaderHeight)
            .padding(horizontal = PanelContentPadding),
    ) {
        Icon(
            painterResource(glyphRes),
            contentDescription = null,
            modifier = Modifier.size(MappoPillIconSize),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(MappoGlyphLabelGap))
        Text(
            text = title.uppercase(),
            style = mappoOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        MappoIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close",
            onClick = onClose,
        )
    }
}

/**
 * One compact panel row. Hand-rolled (not ListItem) to match the group editor's sub-Material
 * row dimensionality — a deliberate, annotated deviation shared by the whole remap chrome.
 * [active] raises the row to the topmost plane (the selection treatment on this surface).
 */
@Composable
private fun PanelRow(
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelRowHeight)
            .clip(shape)
            .then(if (active) Modifier.background(MappoElevatedContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = MappoPillContentPadding),
    ) {
        content()
    }
}

/**
 * Master power row (the old home's PowerControl reshaped as a settings row): LED + label
 * left, the halo-stripped scaled switch right. Drives remap AND the button overlay in
 * lockstep via the caller.
 */
@Composable
private fun PowerRow(powerOn: Boolean, onPowerChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelPowerRowHeight)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onPowerChange(!powerOn) }
            .padding(horizontal = MappoPillContentPadding),
    ) {
        // Deliberate fixed LED green — a power LED is green regardless of theme.
        val led by animateColorAsState(
            targetValue = if (powerOn) Color(0xFF52E07C)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            label = "powerLed",
        )
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(led),
        )
        Spacer(Modifier.width(MappoGlyphLabelGap))
        Text(
            text = "Power",
            style = mappoMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        // Strip the 48dp interactive halo + scale down so the switch fits the compact row.
        // The row owns the tap target; the switch itself stays interactive for thumb drags.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Switch(
                checked = powerOn,
                onCheckedChange = onPowerChange,
                modifier = Modifier.scaledLayout(0.8f),
                thumbContent = {
                    Icon(
                        imageVector = if (powerOn) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = if (powerOn) "Mappo features on" else "Mappo features off",
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/** Header height — matches the group editor's sticky header. */
private val PanelHeaderHeight = 42.dp

/** Horizontal content inset of the panel's header + list. */
private val PanelContentPadding = 8.dp

/** Compact list-row height (between the editor's 38dp rows and the 32dp tabs). */
private val PanelRowHeight = 32.dp

/** The power row runs slightly taller so the scaled switch keeps breathing room. */
private val PanelPowerRowHeight = 36.dp

/** Leading glyph edge inside panel rows. */
private val PanelRowIconSize = 16.dp
