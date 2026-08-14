package com.mappo.ui.screen.remap

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputBarHeight
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle

/**
 * The Remap Controls top bar (2026-08-13 rework): the action-set tabs moved down into the
 * content view (the set row above the group boxes — see [RemapSimpleView]), and the whole
 * flexible middle now belongs to ONE large centered summon for the layout (profile) panel —
 * the current application and layout context at a glance: app title (right-aligned, overline
 * label) | app icon (dead center) | layout name (left-aligned, overline label). The options
 * pill keeps the trailing corner (physical Start's glyph); physical Select still summons the
 * layout panel.
 */
@Composable
internal fun RemapTopBar(
    appLabel: String?,
    appPackage: String?,
    layoutLabel: String?,
    onOpenProfile: () -> Unit,
    onOpenOptions: () -> Unit,
    modifier: Modifier = Modifier,
    // Focus-return targets: when a summoned panel closes, controller focus hands back to
    // the control that summoned it (the editor's return-to-home-box pattern).
    profileFocusRequester: FocusRequester? = null,
    optionsFocusRequester: FocusRequester? = null,
) {
    // surfaceContainer — app-bar plane, one step up from the screen surface.
    Column(modifier = modifier) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MinputBarHeight)
                    .padding(horizontal = MinputBarEdgePadding),
            ) {
                ProfileContextButton(
                    appLabel = appLabel,
                    appPackage = appPackage,
                    layoutLabel = layoutLabel,
                    onClick = onOpenProfile,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(
                            if (profileFocusRequester != null) {
                                Modifier.focusRequester(profileFocusRequester)
                            } else Modifier,
                        ),
                )
                MinputPillButton(
                    text = "Options",
                    onClick = onOpenOptions,
                    leadingIcon = painterResource(R.drawable.xbox_button_menu),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .then(
                            if (optionsFocusRequester != null) {
                                Modifier.focusRequester(optionsFocusRequester)
                            } else Modifier,
                        ),
                )
            }
        }
        HorizontalDivider()
    }
}

/**
 * The layout-panel summon: a double-height pill at a FIXED width — the fixed footprint plus
 * the equal-weight text flanks are what pin the app icon to the exact horizontal center
 * regardless of how long either name runs (both truncate with ellipses). Application block
 * right-aligns toward the icon, layout block left-aligns away from it, each under its own
 * overline label.
 */
@Composable
private fun ProfileContextButton(
    appLabel: String?,
    appPackage: String?,
    layoutLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = minputBoxContainer()
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = container,
        border = minputBevelBorder(container, ProfileButtonHeight / 2),
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .width(ProfileButtonWidth)
            .height(ProfileButtonHeight)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                role = Role.Button,
                onClickLabel = "Open layouts",
                onClick = onClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = ProfileButtonContentPadding),
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = "Application".uppercase(),
                    style = minputOverlineTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = appLabel ?: "None detected",
                    style = minputMiniTextStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(MinputGlyphLabelGap))
            val icon = rememberAppIconPainter(appPackage)
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(ProfileButtonIconSize))
            } else {
                Icon(
                    Icons.Filled.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(ProfileButtonIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(MinputGlyphLabelGap))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Layout".uppercase(),
                    style = minputOverlineTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = layoutLabel ?: "Layout",
                    style = minputMiniTextStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Fixed footprint of the context button — load-bearing for the icon's exact centering. */
private val ProfileButtonWidth = 280.dp
private val ProfileButtonHeight = 32.dp

/** Content inset inside the context button (wider than a mini pill — it's a bigger surface). */
private val ProfileButtonContentPadding = 14.dp

/** The application icon's edge — the button's centerpiece. */
private val ProfileButtonIconSize = 20.dp
