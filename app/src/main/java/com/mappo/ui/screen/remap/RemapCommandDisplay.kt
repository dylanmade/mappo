package com.mappo.ui.screen.remap

import com.mappo.data.model.steam.Binding
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.displayLabel
import com.mappo.data.model.steam.displayName

/**
 * **What a command READS AS — the one resolution both remap views share** (Dylan, 2026-09-20).
 *
 * The basic view is the advanced table's compacted twin, so a command must print the same way
 * in both: same glyph, same initials, same user label. It didn't — the basic rows resolved
 * their own text and drew no device glyph at all — so the two views disagreed about the same
 * binding. Anything that decides how a command is named now lives here, and both call sites
 * read it off [commandDisplay] rather than re-deriving it.
 *
 * @property glyph the output whose device icon leads the command, or null when this command
 *   hides it (`Binding.showDeviceIcon`, from the label editor).
 * @property text the command's own name — the advanced tile's OUTPUT line.
 * @property label the user's own wording, or null when the command has none worth a line.
 */
internal data class CommandDisplay(
    val glyph: BindingOutput?,
    val text: String,
    val label: String?,
) {
    /**
     * The command on ONE line: the user's label when it has one, else its own name — what the
     * basic view's rows show, where there is only ever one line per assignment.
     */
    val line: String get() = label ?: text

    /**
     * The glyph on that one line — **none once the command has a label** (Dylan, 2026-09-20).
     *
     * A label exists to call the command something of the user's own INSTEAD of naming the
     * output it fires, so the device info that qualifies an output name has nothing to qualify:
     * the same reasoning that put the glyph and the initials on the tile's command line only,
     * and that hides both checkboxes in the label editor the moment AUTO goes off. (Users who
     * do want a symbol in their own wording will get the symbol keyboard, which puts it in the
     * label text where they chose to.)
     */
    val lineGlyph: BindingOutput? get() = glyph.takeIf { label == null }
}

/** Resolve one command (an activator's binding + its outputs) to how it prints. */
internal fun commandDisplay(
    binding: Binding?,
    outputs: List<BindingOutput>,
    config: ControllerConfig?,
): CommandDisplay = CommandDisplay(
    glyph = outputs.firstOrNull { it != BindingOutput.Unbound }
        ?.takeIf { binding?.showDeviceIcon != false },
    text = commandsText(outputs, config, initials = binding?.showDeviceInitials != false),
    label = tileLabelFor(binding?.label, commandsText(outputs, config, initials = false)),
)

/**
 * What a cell's command is CALLED: each of its outputs' names, joined with a plus.
 *
 * A cell holds one command today, so this is almost always one name; a `cycle_binding`
 * activator (Phase 3) fires several in turn, and "A + B" is how that reads (Dylan, 2026-09-19).
 * [initials] keeps the device prefix the output's own name carries ("KB: Escape" / "Escape") —
 * per command, from its Binding.
 */
internal fun commandsText(
    outputs: List<BindingOutput>,
    config: ControllerConfig?,
    initials: Boolean,
): String = outputs
    .filter { it != BindingOutput.Unbound }
    .joinToString(CommandJoin) { if (initials) it.displayLabel(config) else it.displayName(config) }

/** What separates the names of a cycling command's outputs. */
internal const val CommandJoin = " + "

/**
 * A tile's SECONDARY label: the user's own, or null when there is nothing to add.
 *
 * Null covers both "no label" and "a label that just repeats the command's own name" (Dylan,
 * 2026-09-19) — the label editor offers that name as its placeholder, so typing it back
 * verbatim means the command is called what it was always called, not that it wants a second
 * line saying so.
 */
internal fun tileLabelFor(label: String?, outputName: String): String? =
    label?.trim()?.takeIf { it.isNotEmpty() && !it.equals(outputName.trim(), ignoreCase = true) }
