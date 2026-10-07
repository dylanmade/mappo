package com.mappo.ui.screen.remap

import com.mappo.ui.minput.MinputIconSize
import com.mappo.ui.minput.MinputIcon
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * A surfaceContainer strip over a divider on the shared [MinputBarEdgePadding] anatomy.
 *
 * The CONTROLS view left this bar on 2026-08-29 for its own transparent, pod-based
 * [RemapControlsTopBar]; what remains here serves the (dormant) browse chain, so the
 * identity-button experiment and its taller strip retired with the move.
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
                    .height(MinputBarHeight)
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
                BarIdentityStack(
                    appPackage = appPackage,
                    overline = overline,
                    title = title,
                )
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
 * nothing with no [appPackage] — 2026-08-25) beside the overline/title stack.
 */
@Composable
private fun BarIdentityStack(
    appPackage: String?,
    overline: String,
    title: String?,
    modifier: Modifier = Modifier,
) {
    val overlineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = MaterialTheme.colorScheme.onSurface
    val iconSize = MinputBarWidgetIconSize
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (appPackage != null) {
            val icon = rememberAppIconPainter(appPackage)
            if (icon != null) {
                AppIconImage(icon, size = iconSize)
            } else {
                MinputIcon(
                    Icons.Filled.Apps,
                    contentDescription = null,
                    size = MinputIconSize.M,
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
