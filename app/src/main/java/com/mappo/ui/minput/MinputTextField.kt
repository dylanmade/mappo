package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.mappo.ui.imeActivation
import com.mappo.ui.mappoKeyboardOptions

/**
 * The minput text field: a pill-scale input "well" — the treatment born as the group
 * editor's label field, promoted to a proper inline-editing component. Flat fill
 * ([minputInputFieldContainer], darker than the surface it sits on) with NO bevel: an
 * input is a well, not a button. A thin accent ring fades in while focused (the minput
 * translation of M3's focused-border state).
 *
 * Base functionality comes from foundation's [BasicTextField] — the same base M3's own
 * fields build on. The stock M3 field can't be wrapped here: its ~56dp decoration box and
 * floating-label reservation are baked in, unreachable from pill scale. Carries the
 * app-wide IME policy (no keyboard on gamepad focus; never auto-focus a field on open).
 *
 * @param clearable shows a clear (×) affordance while the field holds text; tapping it
 *   empties the field via [onValueChange] (M3 search-field convention).
 */
@Composable
fun MinputTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    clearable: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = MaterialTheme.colorScheme
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    val focusRing by animateColorAsState(
        targetValue = if (focused && enabled) colors.primary.copy(alpha = 0.55f) else Color.Transparent,
        label = "minputFieldFocusRing",
    )
    val contentColor = colors.onSurface
    val textStyle = minputMiniTextStyle().copy(color = contentColor)

    Surface(
        shape = shape,
        color = minputInputFieldContainer(),
        modifier = modifier
            .height(MinputPillHeight)
            .border(MinputBoxStroke, focusRing, shape)
            .then(if (enabled) Modifier else Modifier.alpha(0.6f)),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            // App-wide IME policy: no keyboard on gamepad focus; activator key opens it.
            modifier = Modifier.imeActivation(),
            enabled = enabled,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = mappoKeyboardOptions(keyboardOptions),
            keyboardActions = keyboardActions,
            singleLine = true,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = MinputPillContentPadding),
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(MinputPillIconSize),
                            tint = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(MinputGlyphLabelGap))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    if (clearable && enabled && value.isNotEmpty()) {
                        Spacer(Modifier.width(MinputGlyphLabelGap))
                        // Bare clipped-clickable glyph, no 48dp halo — it has to live inside
                        // the 24dp pill; the family's sub-touch-target trade-off.
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear text",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier
                                .size(MinputPillIconSize)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = minputIndication(),
                                    role = Role.Button,
                                ) { onValueChange("") },
                        )
                    }
                }
            },
        )
    }
}
