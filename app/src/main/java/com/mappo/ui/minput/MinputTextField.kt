package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mappo.ui.imeActivation
import com.mappo.ui.mappoKeyboardOptions
import kotlinx.coroutines.delay

/**
 * The minput input field: a **tap-to-edit pill** — the group editor's label-field pattern,
 * which is the library's standard for text input. The pill itself is display-only (the
 * input-well fill, [minputInputFieldContainer], FLAT — an input is a well, not a button);
 * activating it (touch tap, or gamepad A while focused) opens a [MinputModal] edit dialog
 * where the actual typing happens. Text is committed through [onValueChange] on Save (and
 * by the clear ×); Cancel / scrim / back / gamepad B discard the draft.
 *
 * Why not an inline text field: an inline field captures d-pad focus (arrows move the
 * cursor, not the focus) and stalls gamepad navigation, and the overlaying keyboard can
 * cover the very field being typed into. As a pill the field is an ordinary focus stop the
 * d-pad flows past, and the edit dialog floats clear of the keyboard — behavior hammered
 * out on the group editor's label field and promoted here.
 *
 * @param placeholder shown dimmed in the empty pill, and as the hint inside the edit modal.
 * @param leadingIcon optional glyph at the pill's start (e.g. Search).
 * @param clearable shows a clear (×) affordance while the field holds text; tapping it
 *   empties the field via [onValueChange] without opening the edit modal.
 * @param editTitle overline title of the edit modal; defaults to [placeholder].
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
) {
    var editing by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }

    Surface(
        shape = shape,
        color = minputInputFieldContainer(),
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
        MinputTextFieldEditModal(
            title = editTitle ?: placeholder ?: "Edit",
            initial = value,
            placeholder = placeholder,
            onCommit = onValueChange,
            onClose = { editing = false },
        )
    }
}

/**
 * The field's edit dialog: a [MinputModal] card (title, text well, Cancel/Save) hosted in
 * its own platform [Dialog] window so the primitive can float it full-screen from any
 * composition depth — the same window mechanism AlertDialog uses, but with minput chrome.
 * The window itself is a transparent host: its platform dim and animation are disabled so
 * the modal's own scrim and fade+settle are the only chrome and motion. Being a separate
 * window also keeps the host surface's focus traps out of play and lets the dialog float
 * above the soft keyboard (the label-field behavior).
 */
@Composable
private fun MinputTextFieldEditModal(
    title: String,
    initial: String,
    placeholder: String?,
    onCommit: (String) -> Unit,
    onClose: () -> Unit,
) {
    // MinputModal snaps (no enter animation) when first composed already-open, so the
    // window opens closed and flips open one frame in; closing waits out the exit fade
    // before the window is torn down.
    var open by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(initial) }
    val cancelFocus = remember { FocusRequester() }
    fun dismiss() {
        if (!closing) {
            closing = true
            open = false
        }
    }
    fun save() {
        onCommit(draft.trim())
        dismiss()
    }
    LaunchedEffect(Unit) { open = true }
    LaunchedEffect(closing) {
        if (closing) {
            delay(MinputModalExitMillis + 20L)
            onClose()
        }
    }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0f)
            dialogWindow?.setWindowAnimations(0)
        }
        Box(
            Modifier
                .fillMaxSize()
                // Physical B cancels, matching the panels' close affordance — the dialog
                // window owns key input while up, so the screen's handlers can't see it.
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.ButtonB) {
                        if (event.type == KeyEventType.KeyDown) dismiss()
                        true
                    } else {
                        false
                    }
                },
        ) {
            MinputModal(
                open = open,
                onDismiss = ::dismiss,
                height = EditModalHeight,
                focusSeat = cancelFocus,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize().padding(EditModalPadding)) {
                    Text(
                        text = title.uppercase(),
                        style = minputOverlineTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    // Never auto-focused (IME doctrine) — the seat is on Cancel; reaching
                    // the well and activating it opens the keyboard.
                    MinputTextWell(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = placeholder,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    ) {
                        MinputPillButton(
                            text = "Cancel",
                            onClick = ::dismiss,
                            modifier = Modifier.focusRequester(cancelFocus),
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
        }
    }
}

/**
 * The library's INTERNAL inline text well: the pill-scale flat input treatment carrying a
 * live [BasicTextField]. Not a public primitive — inline fields capture d-pad focus and
 * break gamepad navigation, so app surfaces use the tap-to-edit [MinputTextField]. This
 * exists for minput-internal editing contexts where inline typing is the point and the
 * keyboard can't cover the field: the edit modal above, [MinputSlider]'s value field.
 */
@Composable
internal fun MinputTextWell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = MaterialTheme.colorScheme
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    // The minput translation of M3's focused-border state.
    val focusRing by animateColorAsState(
        targetValue = if (focused && enabled) colors.primary.copy(alpha = 0.55f) else Color.Transparent,
        label = "minputWellFocusRing",
    )
    val textStyle = minputMiniTextStyle().copy(color = colors.onSurface)

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
            // App-wide IME policy helpers; note the value-based BasicTextField ignores
            // showKeyboardOnFocus, so gamepad focus CAN spawn the keyboard here — accepted,
            // because this well only appears where typing is the surface's whole point.
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
                }
            },
        )
    }
}

/** Height of the field's edit-modal card: title + well + footer buttons. */
private val EditModalHeight = 112.dp

/** Content inset of the edit-modal card. */
private val EditModalPadding = 8.dp
