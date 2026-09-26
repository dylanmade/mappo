package com.mappo.ui.screen.remap

import androidx.compose.runtime.staticCompositionLocalOf
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.Activator
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.Binding
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.GroupInputGraph

/**
 * **An input row is a STACK of commands the user defines** (Dylan, 2026-09-20) — not a fixed
 * grid of one slot per press type.
 *
 * The advanced table used to pre-expose every (input × press type) intersection: six columns,
 * six tiles per row, most of them empty. That drew a lot of nothing, made the table tiring to
 * navigate, and capped an input at ONE command per press type — a limit the schema never had.
 * A row now holds exactly the commands that exist, each carrying its own press type, with a
 * single "+" tile at the end.
 *
 * **A command is a [Binding]**, and its press type is the [Activator] bucket it hangs under
 * (`ControllerConfigRepository`'s "input row" model: same-type commands share one activator, so
 * they also share its settings — long-press time, chord partner). That is what makes "more than
 * one command of the same press type" expressible: two bindings in one bucket.
 */
internal data class RowCommand(
    val binding: Binding,
    val activator: Activator,
    val output: BindingOutput,
) {
    /** The command's identity everywhere in the UI — the tile, the menus, the move. */
    val id: Long get() = binding.id
    val type: ActivatorType get() = activator.type
}

/**
 * How a row's commands are ordered.
 *
 * Tied to a setting from the start, at Dylan's instruction: auto-sorting by press type is the
 * behaviour today and the default, but it is a CHOICE the user will be given — so the order is
 * a value that flows into the resolution ([LocalCommandOrder]), never a `sortedBy` inlined at a
 * call site. When the user-facing toggle lands, it provides this local from a settings store
 * and nothing else changes.
 */
internal enum class CommandOrder {
    /** Auto-sorted into [pressTypeSortOrder] order — the table's old column order, now a sort. */
    PRESS_TYPE,

    /** The order the commands were created in; the user arranges them. Not yet reachable. */
    MANUAL,
}

/** The order rows are sorted in. See [CommandOrder]; the default is the auto-sort. */
internal val LocalCommandOrder = staticCompositionLocalOf { CommandOrder.PRESS_TYPE }

/**
 * **How a controller move is confirmed** — the user's setting, carried down to the tiles.
 *
 * A CompositionLocal for the same reason [LocalCommandOrder] is one: it is read deep inside the
 * tile hierarchy by code that has no other business knowing about settings, and threading it
 * through every composable between here and there would be a parameter per layer. Provided by
 * `MainScreen` from [com.mappo.data.settings.MoveSettings]; the default matches that store's, so a
 * surface composed outside the app window still behaves like the app.
 */
internal val LocalMoveCommitGesture =
    staticCompositionLocalOf { com.mappo.data.settings.MoveSettings.Default }

/**
 * The commands on one input row, in display order.
 *
 * Unbound bindings are left out: "New" creates the binding BEFORE the picker opens, so a
 * cancelled pick leaves an empty one behind, and an empty command is not something to show.
 */
internal fun GroupInputGraph?.rowCommands(order: CommandOrder): List<RowCommand> {
    val input = this ?: return emptyList()
    val commands = input.activators.flatMap { activatorGraph ->
        activatorGraph.bindings.map { binding ->
            RowCommand(
                binding = binding,
                activator = activatorGraph.activator,
                output = BindingOutput.fromEntity(binding.outputType, binding.args),
            )
        }
    }.filter { it.output != BindingOutput.Unbound }
    return when (order) {
        // Ties broken by the stored order on both levels, so the sort is total and a row can't
        // reshuffle between recompositions.
        CommandOrder.PRESS_TYPE -> commands.sortedWith(
            compareBy(
                { pressTypeSortOrder.indexOf(it.type).takeIf { i -> i >= 0 } ?: pressTypeSortOrder.size },
                { it.activator.orderIndex },
                { it.binding.orderIndex },
            ),
        )
        CommandOrder.MANUAL -> commands.sortedWith(
            compareBy({ it.activator.orderIndex }, { it.binding.orderIndex }),
        )
    }
}

/**
 * One row's commands, resolved the way both views resolve everything else: the layer's own
 * group input when it has one, else the base set's showing through (ghost semantics).
 */
internal fun rowCommandsFor(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    spec: SimpleRowSpec,
    order: CommandOrder,
): List<RowCommand> {
    val groupInput = viewingLayer?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
        ?: viewingSet?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
    return groupInput.rowCommands(order)
}

/**
 * How many TILES a row shows: its commands plus the trailing "+".
 *
 * The "+" is a real slot — focusable, a drop target, the row's create affordance — so every
 * row has at least one, and a row's slot count is what the d-pad's stepping clamps against.
 */
internal fun rowSlotCount(commands: Int): Int = commands + 1
