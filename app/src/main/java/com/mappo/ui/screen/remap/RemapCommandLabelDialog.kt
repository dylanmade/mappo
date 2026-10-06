package com.mappo.ui.screen.remap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.displayLabel
import com.mappo.data.model.steam.displayName
import com.mappo.ui.glyph.InputGlyphs
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputCheckbox
import com.mappo.ui.minput.MinputDialog
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputSize
import com.mappo.ui.minput.MinputSwitch
import com.mappo.ui.minput.MinputSwitchTallHeight
import com.mappo.ui.minput.MinputSwitchTallWidth
import com.mappo.ui.minput.MinputTextWell
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle

/**
 * What one command in the advanced table is CALLED, and how it prints.
 *
 * **AUTO is the resting state** (Dylan, 2026-09-19): a command names itself after its output, so
 * the dialog opens with no text field at all — just the name the cell will show, live, under the
 * two checkboxes that shape it. Turning AUTO off swaps in the text well and the user's own
 * wording takes over; the checkboxes go with it, since they describe the name the user has just
 * replaced.
 *
 * A bespoke dialog rather than [com.mappo.ui.minput.MinputTextEditDialog] because the generic
 * one-shot editor is a title + a well + Cancel/Save, and this needs a switch in the title's
 * corner and a field that isn't always there. It is still a [MinputDialog] — the chrome, the
 * dim, the dismissal and the focus contract are all the library's.
 *
 * Future: the bottom-left corner is also where a symbol keyboard's summon will go, for users
 * putting Material symbols inside their own labels (Dylan).
 */
@Composable
internal fun CommandLabelDialog(
    /** The command's stored label; blank means it has none, which is what AUTO means. */
    label: String,
    /** Every output the cell fires — one today, a cycle later. Joined with "+" when several. */
    outputs: List<BindingOutput>,
    config: ControllerConfig?,
    showDeviceIcon: Boolean,
    showDeviceInitials: Boolean,
    onCommit: (label: String, showDeviceIcon: Boolean, showDeviceInitials: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var auto by remember { mutableStateOf(label.isBlank()) }
    var draft by remember { mutableStateOf(label) }
    var icons by remember { mutableStateOf(showDeviceIcon) }
    var initials by remember { mutableStateOf(showDeviceInitials) }
    val wellFocus = remember { FocusRequester() }
    fun save() {
        // AUTO commits a BLANK label — the absence of an override is what auto IS, so the
        // command keeps naming itself as its output changes.
        onCommit(if (auto) "" else draft.trim(), icons, initials)
        onClose()
    }
    // Seat the keyboard on the well whenever there is one: turning AUTO off is the typing
    // intent, exactly as opening the generic editor is. Auto mode has nothing to type into, so
    // it opens (and returns) without summoning a keyboard.
    LaunchedEffect(auto) { if (!auto) runCatching { wellFocus.requestFocus() } }

    MinputDialog(onDismissRequest = onClose) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "COMMAND LABEL",
                style = minputOverlineTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "AUTO",
                style = minputOverlineTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(MinputGlyphLabelGap))
            MinputSwitch(
                checked = auto,
                onCheckedChange = { auto = it },
                width = MinputSwitchTallWidth,
                height = MinputSwitchTallHeight,
                stateIcons = true,
            )
        }
        Spacer(Modifier.height(LabelDialogTitleGap))

        if (auto) {
            // The name itself, not a placeholder and not in a well: there is nothing to type
            // here, and dressing it as an empty field would say there is.
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinputSize.Standard.height),
                contentAlignment = Alignment.CenterStart,
            ) {
                AutoCommandName(outputs = outputs, config = config, icons = icons, initials = initials)
            }
        } else {
            MinputTextWell(
                value = draft,
                onValueChange = { draft = it },
                // The command's own name — what it is called with no label at all, so the field
                // shows what typing nothing leaves it as.
                placeholder = commandsText(outputs, config, initials = false).takeIf { it.isNotEmpty() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                focusRequester = wellFocus,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(LabelDialogFooterGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (auto) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Row(horizontalArrangement = Arrangement.spacedBy(LabelDialogCheckboxGap)) {
                        MinputCheckbox(
                            checked = icons,
                            onCheckedChange = { icons = it },
                            label = "Show icons",
                        )
                        MinputCheckbox(
                            checked = initials,
                            onCheckedChange = { initials = it },
                            label = "Show initials",
                        )
                    }
                }
            }
            MinputButton(text = "Cancel", onClick = onClose)
            MinputButton(text = "Save", onClick = ::save, filled = true, elevated = true)
        }
    }
}

/**
 * The auto name, drawn the way the cell will draw it — device glyph and initials included or
 * not, per the checkboxes below it. The dialog is the preview.
 */
@Composable
private fun AutoCommandName(
    outputs: List<BindingOutput>,
    config: ControllerConfig?,
    icons: Boolean,
    initials: Boolean,
) {
    val color = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        outputs.forEachIndexed { index, output ->
            if (index > 0) {
                Text(
                    text = CommandJoin,
                    style = minputMiniTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (icons) {
                InputGlyphs.outputPainter(output)?.let { painter ->
                    Icon(
                        painter,
                        contentDescription = null,
                        modifier = Modifier.size(AutoNameGlyphSize),
                        tint = color,
                    )
                    Spacer(Modifier.width(MinputGlyphLabelGap))
                }
            }
            Text(
                text = if (initials) output.displayLabel(config) else output.displayName(config),
                style = minputMiniTextStyle(),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Device glyph scale in the auto name — the tile's, since this previews the tile. */
private val AutoNameGlyphSize = 14.dp

/** Air under the title row, and above the footer. */
private val LabelDialogTitleGap = 10.dp
private val LabelDialogFooterGap = 14.dp

/** Between the two display checkboxes. Wider than the gap inside one (box to its own label),
 *  so each box reads as belonging to the words on its right. */
private val LabelDialogCheckboxGap = 14.dp
