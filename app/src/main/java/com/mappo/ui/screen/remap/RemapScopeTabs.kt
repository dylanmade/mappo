package com.mappo.ui.screen.remap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.ui.component.AppIconImage
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputBarHeight
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle

/**
 * The Remap Controls top bar (2026-08-14 flow rebuild): the controls view is the END of the
 * applications → layouts → controls browse chain now, so the bar leads with a Back arrow
 * (returning to the application's layouts view), the viewed application's icon, then the
 * stacked context text — overline "Viewing <application> layout:" over the layout's name.
 * The options pill keeps the trailing corner (physical Start's glyph). The former centered
 * application|layout context button moved to the frame's bottom bar as the active-layout
 * shortcut.
 */
@Composable
internal fun RemapTopBar(
    appLabel: String?,
    appPackage: String?,
    layoutLabel: String?,
    onBack: () -> Unit,
    onOpenOptions: () -> Unit,
    modifier: Modifier = Modifier,
    // Focus-return targets: when the options panel closes, controller focus hands back to
    // the pill that summoned it (the editor's return-to-home-box pattern).
    backFocusRequester: FocusRequester? = null,
    optionsFocusRequester: FocusRequester? = null,
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
                    AppIconImage(icon, size = TopBarAppIconSize)
                } else {
                    Icon(
                        Icons.Filled.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(TopBarAppIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = (if (appLabel != null) "Viewing $appLabel layout:" else "Viewing layout:")
                            .uppercase(),
                        style = minputOverlineTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = layoutLabel ?: "Layout",
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                MinputPillButton(
                    text = "Options",
                    onClick = onOpenOptions,
                    leadingIcon = painterResource(R.drawable.xbox_button_menu),
                    modifier = if (optionsFocusRequester != null) {
                        Modifier.focusRequester(optionsFocusRequester)
                    } else Modifier,
                )
            }
        }
        HorizontalDivider()
    }
}

/** The viewed application's icon in the bar — sized to sit beside the two-line text stack. */
private val TopBarAppIconSize = 16.dp
