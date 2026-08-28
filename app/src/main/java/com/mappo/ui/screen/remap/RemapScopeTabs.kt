package com.mappo.ui.screen.remap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappo.ui.component.AppIconImage
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputBarHeight
import com.mappo.ui.minput.MinputBarIconTextGap
import com.mappo.ui.minput.MinputBarStackGap
import com.mappo.ui.minput.MinputBarWidgetIconSize
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputPillContentPadding
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputHighlightContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle

/**
 * The shared browse-chain top bar (2026-08-15; slots widened for the 2026-08-20 flow
 * re-imagining): every view of the chain wears the same bar — a [navigation] slot at the
 * start (defaults to the Back arrow; the controls home swaps in the Auto switch stack),
 * the viewed layout's application icon (generic glyph when none), the header text — an
 * [overline] label with an optional value line beneath it — then [leadingActions] sitting
 * WITH the identity cluster (the controls view's Activate / View layouts pills) and
 * right-aligned trailing [actions] (Add/Tune on layouts, Layout settings on controls).
 * A surfaceContainer strip over a divider on the shared [MinputBarEdgePadding] anatomy;
 * runs TALLER than the frame's bottom bar ([TopBarHeight] vs the shared
 * [MinputBarHeight]) while the identity-button experiment is live.
 */
@Composable
internal fun RemapTopBar(
    overline: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    appPackage: String? = null,
    // Focus-return target: when a summoned surface closes, controller focus hands back here.
    backFocusRequester: FocusRequester? = null,
    // EXPERIMENTAL (2026-08-27, Dylan): the identity widget — app icon + overline/title
    // stack — as a BUTTON (the controls bar wires it to toggle the layouts drawer). Bare
    // at rest; [identityHighlighted] puts it on the highlight plane, the design
    // language's open/selected marking (pass the drawer's open state).
    onIdentityClick: (() -> Unit)? = null,
    identityHighlighted: Boolean = false,
    // Start-slot override; null renders the default Back arrow wired to [onBack].
    navigation: (@Composable () -> Unit)? = null,
    leadingActions: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    // surfaceContainer — app-bar plane, one step up from the screen surface.
    Column(modifier = modifier) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // Taller than the shared MinputBarHeight (2026-08-27, identity-button
                    // experiment): the chromed identity capsule needs air the 38dp strip
                    // couldn't give it. The frame's bottom bar keeps the shared height —
                    // re-unify (or promote this to MinputDefaults) once the experiment
                    // settles.
                    .height(TopBarHeight)
                    .padding(horizontal = MinputBarEdgePadding),
            ) {
                if (navigation != null) {
                    navigation()
                } else {
                    MinputIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        modifier = if (backFocusRequester != null) {
                            Modifier.focusRequester(backFocusRequester)
                        } else Modifier,
                    )
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                if (onIdentityClick != null) {
                    // The identity as a button, wearing the full box chrome — family
                    // fill + bevel ring like every other chromed button (2026-08-27,
                    // replacing the bare-at-rest first cut; the bar runs taller to give
                    // the visible capsule its air) — highlight plane while open.
                    val interaction = remember { MutableInteractionSource() }
                    val shape = RoundedCornerShape(50)
                    val container = if (identityHighlighted) {
                        minputHighlightContainer()
                    } else minputBoxContainer()
                    Surface(
                        shape = shape,
                        color = container,
                        border = minputBevelBorder(container, IdentityButtonCorner),
                        modifier = Modifier
                            .minputInteractiveMotion(interaction)
                            .clip(shape)
                            .clickable(
                                interactionSource = interaction,
                                indication = minputIndication(),
                                onClick = onIdentityClick,
                            ),
                    ) {
                        BarIdentityStack(
                            appPackage = appPackage,
                            overline = overline,
                            title = title,
                            highlighted = identityHighlighted,
                            modifier = Modifier.padding(
                                horizontal = MinputPillContentPadding,
                                vertical = IdentityButtonVerticalPadding,
                            ),
                        )
                    }
                } else {
                    BarIdentityStack(
                        appPackage = appPackage,
                        overline = overline,
                        title = title,
                        highlighted = false,
                    )
                }
                Spacer(Modifier.width(MinputBarIconTextGap))
                leadingActions()
                Spacer(Modifier.width(MinputGlyphLabelGap))
                Spacer(Modifier.weight(1f))
                actions()
            }
        }
        HorizontalDivider()
    }
}

/**
 * The bar's identity cluster: the viewed application's icon (generic glyph fallback;
 * nothing with no [appPackage] — 2026-08-25) beside the overline/title stack. On the
 * highlight plane ([highlighted] — the identity-button experiment's open state) the
 * text roles flip to onPrimary, secondary lines softened by [SecondaryOnHighlightAlpha]
 * (the drawer cards' treatment).
 */
@Composable
private fun BarIdentityStack(
    appPackage: String?,
    overline: String,
    title: String?,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val overlineColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = SecondaryOnHighlightAlpha)
    } else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimary
    } else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (appPackage != null) {
            val icon = rememberAppIconPainter(appPackage)
            if (icon != null) {
                AppIconImage(icon, size = MinputBarWidgetIconSize)
            } else {
                Icon(
                    Icons.Filled.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(MinputBarWidgetIconSize),
                    tint = overlineColor,
                )
            }
            Spacer(Modifier.width(MinputBarIconTextGap))
        }
        // Intrinsic width under a chrome cap (names are unbounded — the
        // NameableText rule), so leading actions can sit WITH the identity
        // cluster instead of being pushed to the far edge.
        Column(
            modifier = Modifier.widthIn(max = TopBarStackMaxWidth),
            verticalArrangement = Arrangement.spacedBy(MinputBarStackGap),
        ) {
            Text(
                text = overline.uppercase(),
                style = minputOverlineTextStyle(),
                color = overlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (title != null) {
                Text(
                    text = title,
                    style = minputMiniTextStyle(),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Width cap for the bar's overline/title stack — layout and application names are
 *  unbounded, and the bar has actions to fit on both sides of them. */
private val TopBarStackMaxWidth = 200.dp

/** Bar-strip height for the top bar — taller than the shared [MinputBarHeight] (38dp) so
 *  the chromed identity capsule sits with air (2026-08-27 experiment; the bottom bar
 *  keeps the shared height until this settles). */
private val TopBarHeight = 46.dp

/** Corner passed to the identity button's bevel — the capsule's half-height (the stack
 *  runs taller than a pill). */
private val IdentityButtonCorner = 16.dp

/** Vertical breathing room inside the identity button around the stack. */
private val IdentityButtonVerticalPadding = 3.dp
