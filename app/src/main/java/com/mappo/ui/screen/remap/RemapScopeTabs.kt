package com.mappo.ui.screen.remap

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
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
 * The shared browse-chain top bar (2026-08-15): every view of the profiles → layouts →
 * controls chain wears the same bar — Back arrow, the viewed profile's application icon
 * (generic glyph on the profiles home), then the header text: an [overline] label with an
 * optional value line beneath it (the controls view stacks the viewed layout's name there).
 * Trailing [actions] are the view's utilities (Add/Tune on layouts, the Options pill on
 * controls). A surfaceContainer strip over a divider on the shared [MinputBarHeight] /
 * [MinputBarEdgePadding] anatomy, mirrored by the frame's bottom bar — the icon and stack
 * metrics deliberately match the bottom bar's active-context widget.
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
                MinputIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    modifier = if (backFocusRequester != null) {
                        Modifier.focusRequester(backFocusRequester)
                    } else Modifier,
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MinputBarStackGap),
                ) {
                    Text(
                        text = overline.uppercase(),
                        style = minputOverlineTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (title != null) {
                        Text(
                            text = title,
                            style = minputMiniTextStyle(),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                actions()
            }
        }
        HorizontalDivider()
    }
}
