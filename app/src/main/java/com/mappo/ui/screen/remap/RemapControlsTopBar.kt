package com.mappo.ui.screen.remap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.ui.compact.scaledLayout
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputGroupButton
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.MinputPod
import com.mappo.ui.minput.MinputPodGap
import com.mappo.ui.minput.MinputPodHeight
import com.mappo.ui.minput.minputOverlineTextStyle

/**
 * The controls view's top bar (2026-08-29 redesign). The bar itself is now **transparent** —
 * a layout tool, not a surface: no strip fill, no divider. Every cluster it carries instead
 * rides its own [MinputPod] — the pill plate that generalized out of this bar — so the chrome
 * reads as discrete capsules floating over the content plane rather than one banded strip.
 *
 * Three slots, laid over a [Box] so the center pod is centered in the BAR (not in the slack
 * between its neighbors):
 *  - **start** — the identity pill: the viewed application's icon leading its layout name,
 *    wired to toggle the layouts drawer and wearing the highlight plane while it is open.
 *  - **center** — the action-set group button ([ActionSetPod]), moved up out of
 *    `RemapSimpleView`'s content column.
 *  - **end** — the Auto-detect toggle, then Edit overlay.
 *
 * Pod-borne buttons sit on the pod's plane, so they wear `elevated` (surface 2) — the same
 * fill as the group button's segments.
 */
@Composable
internal fun RemapControlsTopBar(
    layoutLabel: String,
    appPackage: String?,
    identityHighlighted: Boolean,
    onIdentityClick: () -> Unit,
    config: ControllerConfig?,
    viewingSet: ActionSetGraph?,
    onSelectActionSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    autoDetectEnabled: Boolean,
    onAutoDetectChange: (Boolean) -> Unit,
    onEditOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = MinputBarEdgePadding, vertical = TopBarVerticalPadding)
    ) {
        // ── start: the identity pill (the layouts drawer's summon) ──
        MinputPod(modifier = Modifier.align(Alignment.CenterStart)) {
            MinputPillButton(
                text = layoutLabel,
                onClick = onIdentityClick,
                // Sits ON the pod, so it takes the plane above it — the same fill the
                // group button's segments wear. Highlighted while the drawer is open
                // (the design language's open/selected marking).
                elevated = true,
                highlighted = identityHighlighted,
                // The application's launcher icon, untinted (leadingIconTint defaults to
                // Unspecified, which Icon renders as "no color filter").
                leadingIcon = rememberAppIconPainter(appPackage),
                modifier = Modifier.testTag("bar:identity"),
            )
        }

        // ── center: the action-set switcher ──
        ActionSetPod(
            config = config,
            viewingSet = viewingSet,
            onSelectActionSet = onSelectActionSet,
            onAddSet = onAddSet,
            modifier = Modifier.align(Alignment.Center),
        )

        // ── end: Auto-detect, then Edit overlay ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MinputPodGap),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            MinputPod { AutoDetectRow(enabled = autoDetectEnabled, onChange = onAutoDetectChange) }
            MinputPod {
                MinputPillButton(
                    text = "Edit overlay",
                    onClick = onEditOverlay,
                    elevated = true,
                    leadingIcon = rememberVectorPainter(Icons.Outlined.Layers),
                    // Follows the label's strength (2026-08-30): chromed button labels
                    // moved to onSurface, so a pinned onSurfaceVariant glyph would lag.
                    leadingIconTint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The action-set switcher, rehomed twice: out of the retired top-bar tabs into
 * `RemapSimpleView`'s content column (2026-08), and back into the bar as a pod (2026-08-29).
 * One segment per set on a [MinputGroupButton], closed by the "+" action segment (add a set
 * — the library's sanctioned convention break), then a dormant cog for future set management
 * (rename / duplicate / delete / layers return there). Layers are deliberately absent.
 */
@Composable
private fun ActionSetPod(
    config: ControllerConfig?,
    viewingSet: ActionSetGraph?,
    onSelectActionSet: (Long) -> Unit,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sets = config?.actionSets.orEmpty()
    if (sets.isEmpty()) return
    MinputPod(modifier = modifier) {
        MinputGroupButton(
            options = sets.map { it.actionSet.id },
            selected = viewingSet?.actionSet?.id ?: sets.first().actionSet.id,
            onSelect = onSelectActionSet,
            optionLabel = { id -> sets.firstOrNull { it.actionSet.id == id }?.actionSet?.title.orEmpty() },
            trailingActionIcon = Lucide.Plus,
            trailingActionDescription = "Add action set",
            onTrailingAction = onAddSet,
        )
        // The cog wears the pill family's chromed icon-button form on the segments' plane
        // (2026-08-29 — it was a bare utility glyph, which read as unfinished beside them).
        MinputPillButton(
            onClick = {},
            enabled = false,
            elevated = true,
            leadingIcon = rememberVectorPainter(Lucide.Settings),
            leadingIconTint = MaterialTheme.colorScheme.onSurface,
            contentDescription = "Manage action sets",
        )
    }
}

/**
 * The bar's Auto row (2026-08-25, replacing the 2026-08-21 stacked "AUTO-DETECT" +
 * hand-rolled [com.mappo.ui.minput.MinputSwitch], which never sat right): a compact
 * horizontal overline "AUTO" beside a stock M3 switch — the drawer settings rows'
 * halo-stripped scaled-switch treatment, at bar scale.
 */
@Composable
private fun AutoDetectRow(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MinputGlyphLabelGap),
        modifier = Modifier.padding(horizontal = AutoRowInset),
    ) {
        Text(
            text = "Auto".uppercase(),
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                modifier = Modifier.scaledLayout(AutoSwitchScale),
            )
        }
    }
}

/** Vertical inset between the bar's bounds and its pods. */
private val TopBarVerticalPadding = 5.dp

/** Bar-strip height: a pod plus that air above and below. DERIVED, so raising the pod's own
 *  padding can't leave the pods overflowing a hardcoded strip (which is what the 44dp
 *  literal did once MinputPodPadding went to 6dp) — tune the bar via the inset above. */
private val TopBarHeight = MinputPodHeight + TopBarVerticalPadding * 2

/** The Auto row carries no chrome of its own, so it needs a little inset from the pod's
 *  rim to sit like the chromed pills beside it. */
private val AutoRowInset = 2.dp

/** The bar-scale M3 switch: half size (see [scaledLayout] — layout scale, not a redraw). */
private const val AutoSwitchScale = 0.5f
