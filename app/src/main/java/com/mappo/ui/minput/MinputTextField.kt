package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappo.ui.imeActivation
import com.mappo.ui.mappoKeyboardOptions

/**
 * The minput input field: a **tap-to-edit pill** — the group editor's label-field pattern,
 * which is the library's standard for text input. The pill itself is display-only (the
 * input-well fill, [minputInputFieldContainer], FLAT — an input is a well, not a button);
 * activating it (touch tap, or gamepad A while focused) opens a [MinputDialog] editor
 * where the actual typing happens. Text is committed through [onValueChange] on Save (and
 * by the clear ×); Cancel / scrim / back / gamepad B discard the draft.
 *
 * Why not an inline text field: an inline field captures d-pad focus (arrows move the
 * cursor, not the focus) and stalls gamepad navigation, and the overlaying keyboard can
 * cover the very field being typed into. As a pill the field is an ordinary focus stop the
 * d-pad flows past, and the editor — a platform dialog window — floats clear of the
 * keyboard by stock behavior. All hammered out on the group editor's label field and
 * promoted here.
 *
 * @param placeholder shown dimmed in the empty pill, and as the hint inside the editor.
 * @param leadingIcon optional glyph at the pill's start (e.g. Search).
 * @param clearable shows a clear (×) affordance while the field holds text; tapping it
 *   empties the field via [onValueChange] without opening the editor.
 * @param editTitle overline title of the editor; defaults to [placeholder]. Unused with
 *   [inlineEdit].
 * @param inlineEdit alternative editing UX for fields the caller KNOWS sit high on screen
 *   (top-of-screen filters, dropdown search): activation swaps the pill in place for a live
 *   [MinputTextWell] instead of opening the modal editor, and the IME spawns as an overlay
 *   above all app content — nothing dodges it, so only use this where the keyboard cannot
 *   cover the field. Keystrokes commit live through [onValueChange] (filter semantics — no
 *   Save/Cancel draft); IME Done or focus loss ends editing. Activation stays tap-to-edit,
 *   so the resting pill remains an ordinary d-pad focus stop.
 * @param light the lighter well fill ([minputInputFieldContainerLight]) for fields sitting
 *   directly on the BACKGROUND plane, where the standard well — built on the lowest surface
 *   plane — would vanish into the screen background. Fields on a box/card plane keep the
 *   default.
 * @param outlined wraps the well in a solid ring derived from the field's own fill variant
 *   ([minputInputFieldOutline]), rendered like the button outlines — an inner stroke of
 *   [MinputBoxStroke] width — so outlined and plain primitives measure identically. The
 *   fill stays flat (an outline is not a bevel; a well is still not a button).
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
    editTitle: String? = null,
    inlineEdit: Boolean = false,
    light: Boolean = false,
    outlined: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val container = if (light) minputInputFieldContainerLight() else minputInputFieldContainer()

    if (inlineEdit && editing) {
        MinputTextFieldInlineWell(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            clearable = clearable,
            container = container,
            outlined = outlined,
            onDone = { editing = false },
            modifier = modifier,
        )
        return
    }

    Surface(
        shape = shape,
        color = container,
        border = if (outlined) BorderStroke(MinputBoxStroke, minputInputFieldOutline(container)) else null,
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .height(MinputPillHeight)
            .then(
                if (enabled) {
                    Modifier.clip(shape).clickable(
                        interactionSource = interaction,
                        indication = minputIndication(),
                        role = Role.Button,
                        onClickLabel = "Edit",
                    ) { editing = true }
                } else Modifier.alpha(0.6f),
            ),
    ) {
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
            Text(
                text = value.ifEmpty { placeholder.orEmpty() },
                style = minputMiniTextStyle(),
                color = if (value.isEmpty()) {
                    colors.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    colors.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (clearable && enabled && value.isNotEmpty()) {
                Spacer(Modifier.width(MinputGlyphLabelGap))
                // Bare clipped-clickable glyph, no 48dp halo — it has to live inside the
                // 24dp pill; the family's sub-touch-target trade-off. Nested inside the
                // pill's own clickable: the × consumes its taps, the rest opens the editor.
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
    }

    if (editing) {
        MinputTextFieldEditDialog(
            title = editTitle ?: placeholder ?: "Edit",
            initial = value,
            placeholder = placeholder,
            onCommit = onValueChange,
            onClose = { editing = false },
        )
    }
}

/**
 * The [inlineEdit][MinputTextField] editing state: the pill swapped in place for a live
 * well — same shape, fill, and decor, plus the focus ring — bound straight to the caller's
 * [value]/[onValueChange]. Seated as it appears (activating the pill IS the typing intent —
 * the same sanctioned auto-focus exception as the edit dialog), which spawns the IME as an
 * overlay above all app content. Editing ends on IME Done or when focus leaves the well
 * (tap elsewhere); the value is already committed keystroke-by-keystroke.
 */
@Composable
private fun MinputTextFieldInlineWell(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    leadingIcon: ImageVector?,
    clearable: Boolean,
    container: Color,
    outlined: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wellFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Exit only on LOSING focus — the state starts unfocused while the seat request lands.
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { wellFocus.requestFocus() } }
    LaunchedEffect(focused) {
        if (focused) hadFocus = true else if (hadFocus) onDone()
    }
    MinputTextWell(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        onClear = if (clearable && value.isNotEmpty()) {
            { onValueChange("") }
        } else null,
        container = container,
        outlined = outlined,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interaction,
        focusRequester = wellFocus,
        modifier = modifier,
    )
}

/**
 * The field's editor: a [MinputDialog] (overline title, text well, Cancel/Save). A dialog
 * WINDOW on purpose — centering, dim, back / outside-tap dismissal, and floating above the
 * soft keyboard are all stock platform behavior, exactly like the label field's original
 * AlertDialog. The well is seated as the dialog opens so the keyboard spawns with it:
 * opening the editor IS the typing intent (the sanctioned exception to never-auto-focus —
 * this surface exists only to type).
 */
@Composable
private fun MinputTextFieldEditDialog(
    title: String,
    initial: String,
    placeholder: String?,
    onCommit: (String) -> Unit,
    onClose: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val wellFocus = remember { FocusRequester() }
    fun save() {
        onCommit(draft.trim())
        onClose()
    }
    LaunchedEffect(Unit) { runCatching { wellFocus.requestFocus() } }

    MinputDialog(onDismissRequest = onClose) {
        Text(
            text = title.uppercase(),
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(EditDialogTitleGap))
        MinputTextWell(
            value = draft,
            onValueChange = { draft = it },
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            focusRequester = wellFocus,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(EditDialogFooterGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            MinputPillButton(
                text = "Cancel",
                onClick = onClose,
            )
            MinputPillButton(
                text = "Save",
                onClick = ::save,
                filled = true,
                elevated = true,
            )
        }
    }
}

/**
 * The library's INTERNAL inline text well: the pill-scale flat input treatment carrying a
 * live [BasicTextField]. Not a public primitive — inline fields capture d-pad focus and
 * break gamepad navigation, so app surfaces use the tap-to-edit [MinputTextField]. This
 * exists for minput-internal editing contexts where inline typing is the point and the
 * keyboard can't cover the field: the edit dialog above, the field's inline-edit state,
 * [MinputSlider]'s value field.
 *
 * @param leadingIcon optional glyph at the well's start, mirroring the display pill's.
 * @param onClear when non-null, shows the clear (×) glyph, which invokes it.
 * @param container the well fill variant the well wears — the summoning pill's, so the
 *   inline-edit swap keeps the resting field's coloration.
 * @param outlined mirrors the display pill's outline: the same fill-derived ring, sharing
 *   the focus ring's border slot (the focus color simply wins while focused).
 */
@Composable
internal fun MinputTextWell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null,
    container: Color = minputInputFieldContainer(),
    outlined: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester? = null,
) {
    val colors = MaterialTheme.colorScheme
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    val restingRing = if (outlined) minputInputFieldOutline(container) else Color.Transparent
    // The minput translation of M3's focused-border state; doubles as the outline's slot so
    // an outlined well never wears two strokes.
    val focusRing by animateColorAsState(
        targetValue = if (focused && enabled) colors.primary.copy(alpha = 0.55f) else restingRing,
        label = "minputWellFocusRing",
    )
    val textStyle = minputMiniTextStyle().copy(color = colors.onSurface)

    Surface(
        shape = shape,
        color = container,
        modifier = modifier
            .height(MinputPillHeight)
            .border(MinputBoxStroke, focusRing, shape)
            .then(if (enabled) Modifier else Modifier.alpha(0.6f)),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            // App-wide IME policy helpers; note the value-based BasicTextField ignores
            // showKeyboardOnFocus, so gamepad focus CAN spawn the keyboard here — accepted,
            // because this well only appears where typing is the surface's whole point.
            // (The edit dialog leans on exactly that: seating [focusRequester] is what
            // spawns the keyboard.)
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .imeActivation(),
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
                    if (onClear != null) {
                        Spacer(Modifier.width(MinputGlyphLabelGap))
                        // Same sub-touch-target × as the display pill's — it has to live
                        // inside the 24dp well.
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
                                    onClick = onClear,
                                ),
                        )
                    }
                }
            },
        )
    }
}

/** Gap between the editor's overline title and the text well. */
private val EditDialogTitleGap = 8.dp

/** Gap between the text well and the editor's footer buttons. */
private val EditDialogFooterGap = 10.dp
