package com.mappo.data.model.steam

/**
 * Pure, in-memory equivalents of the repository's write operations, for OPTIMISTIC UI.
 *
 * ## Why these exist
 *
 * A mutation normally travels VM → repository → DB → flow → recomposition, which costs at
 * least a frame. For anything the user is directly manipulating — dragging a tile from one
 * cell to another — that frame is visible: the gesture ends, the UI drops its drag preview,
 * and for one frame the OLD arrangement renders, so the thing you just moved flicks back to
 * where it started before reappearing at its destination.
 *
 * The established fix in this codebase is to update the rendered source of truth immediately
 * and let the DB emission reconcile afterwards — see `KeyboardController.replaceLayoutById`
 * ("update the source of truth optimistically before the DB roundtrip"), which is how the
 * keyboard editor's button drag and the overlay editor's element placement avoid the same
 * flash. These functions are that same idea for the controller binding graph.
 *
 * ## The contract
 *
 * Each function here MUST produce the same graph the repository's matching write would
 * produce on the next reload. They're covered by tests that assert exactly that, because a
 * divergence is nasty in a specific way: the UI shows the optimistic result, then visibly
 * corrects itself when the real data lands. If you change one side, change both.
 */

/**
 * The in-memory twin of `ControllerConfigRepository.moveRowCommand`: carry the command
 * [bindingId] onto the row ([toBindingGroupId], [toInputKey]), keeping its own press type.
 *
 * [swapWithBindingId] is the command it landed ON, which goes back the other way; null means it
 * landed on the row's "+" and is simply ADDED there. Within a group or across them is one
 * operation, since a command only points at its row.
 *
 * The repository exchanges whole ACTIVATORS when each holds a single command, and reparents
 * bare bindings otherwise. The difference is invisible here on purpose: what the two sides must
 * agree on is the RENDERED state — which commands sit on which row, firing on which press type
 * — and that is what the parity tests compare.
 *
 * Returns the config unchanged when the command doesn't exist or the move is a no-op.
 */
fun ControllerConfig.withRowCommandMoved(
    bindingId: Long,
    toBindingGroupId: Long,
    toInputKey: String,
    swapWithBindingId: Long? = null,
): ControllerConfig {
    if (swapWithBindingId == bindingId) return this
    val from = findCommandSite(bindingId) ?: return this
    val swap = swapWithBindingId?.let { findCommandSite(it) }
    val landingOnItsOwnRow = from.bindingGroupId == toBindingGroupId && from.inputKey == toInputKey
    val swapStaysPut = swap == null ||
        (swap.bindingGroupId == from.bindingGroupId && swap.inputKey == from.inputKey)
    // Landing on the row it already belongs to changes nothing the user can see: where a
    // command sits in its row is the sort's business, not the move's.
    if (landingOnItsOwnRow && swapStaysPut) return this

    // Both commands alone in their buckets: the two ACTIVATORS exchange rows, as the repository
    // does, so each keeps the settings it was tuned with. Both leave before either lands, so a
    // swap between two rows can't see a half-applied state.
    if (swap != null && from.activator.bindings.size == 1 && swap.activator.bindings.size == 1) {
        return this
            .withoutActivator(from.bindingGroupId, from.inputKey, from.activator.activator.id)
            .withoutActivator(swap.bindingGroupId, swap.inputKey, swap.activator.activator.id)
            .withActivatorOnRow(toBindingGroupId, toInputKey, from.activator)
            .withActivatorOnRow(from.bindingGroupId, from.inputKey, swap.activator)
    }

    // Otherwise one command moves at a time, each re-reading the graph the previous one left —
    // the repository's sequential behaviour, including which buckets exist by then.
    var result = withOneCommandMoved(bindingId, toBindingGroupId, toInputKey)
    if (swap != null) {
        result = result.withOneCommandMoved(swap.binding.id, from.bindingGroupId, from.inputKey)
    }
    return result
}

/**
 * One command onto one row: the activator travels when the command is alone in it and the
 * destination has no bucket of its press type, otherwise the bare binding does — the same
 * branch `ControllerConfigRepository.reparentBinding` takes.
 */
private fun ControllerConfig.withOneCommandMoved(
    bindingId: Long,
    toBindingGroupId: Long,
    toInputKey: String,
): ControllerConfig {
    val site = findCommandSite(bindingId) ?: return this
    val type = site.activator.activator.type
    val bucket = allBindingGroups().firstOrNull { it.group.id == toBindingGroupId }
        ?.inputByKey(toInputKey)
        ?.activators
        ?.firstOrNull { it.activator.type == type }
    return if (bucket == null && site.activator.bindings.size == 1) {
        withoutActivator(site.bindingGroupId, site.inputKey, site.activator.activator.id)
            .withActivatorOnRow(toBindingGroupId, toInputKey, site.activator)
    } else {
        withoutBinding(site.bindingGroupId, site.inputKey, bindingId)
            .withBindingOnRow(toBindingGroupId, toInputKey, site.activator.activator, site.binding)
    }
}

/** Where a command lives: its binding group, its row, the activator giving it its press type. */
private data class CommandSite(
    val bindingGroupId: Long,
    val inputKey: String,
    val activator: ActivatorGraph,
    val binding: Binding,
)

/** Find a command anywhere in the config — every set, layer, preset and mode-shift group. */
private fun ControllerConfig.findCommandSite(bindingId: Long): CommandSite? {
    allBindingGroups().forEach { group ->
        group.inputs.forEach { input ->
            input.activators.forEach { activator ->
                activator.bindings.firstOrNull { it.id == bindingId }?.let { binding ->
                    return CommandSite(group.group.id, input.input.inputKey, activator, binding)
                }
            }
        }
    }
    return null
}

/** Drop one binding from a row, and the activator with it if that empties it. */
private fun ControllerConfig.withoutBinding(
    bindingGroupId: Long,
    inputKey: String,
    bindingId: Long,
): ControllerConfig = mapBindingGroup(bindingGroupId) { group ->
    group.copy(
        inputs = group.inputs.map { inputGraph ->
            if (inputGraph.input.inputKey != inputKey) return@map inputGraph
            inputGraph.copy(
                activators = inputGraph.activators
                    .map { it.copy(bindings = it.bindings.filterNot { binding -> binding.id == bindingId }) }
                    .filter { it.bindings.isNotEmpty() },
            )
        },
    )
}

/** Drop a whole activator from a row. */
private fun ControllerConfig.withoutActivator(
    bindingGroupId: Long,
    inputKey: String,
    activatorId: Long,
): ControllerConfig = mapBindingGroup(bindingGroupId) { group ->
    group.copy(
        inputs = group.inputs.map { inputGraph ->
            if (inputGraph.input.inputKey != inputKey) return@map inputGraph
            inputGraph.copy(activators = inputGraph.activators.filterNot { it.activator.id == activatorId })
        },
    )
}

/**
 * Put one binding on a row, under that row's bucket for [activator]'s press type — created from
 * the source activator when the row has none, exactly as the repository does (so a command
 * carried onto a fresh row keeps the settings it was firing with).
 */
private fun ControllerConfig.withBindingOnRow(
    bindingGroupId: Long,
    inputKey: String,
    activator: Activator,
    binding: Binding,
): ControllerConfig = withRow(bindingGroupId, inputKey, ActivatorGraph(activator, listOf(binding))) { inputGraph, incoming ->
    val bucket = inputGraph.activators.firstOrNull { it.activator.type == activator.type }
    if (bucket == null) {
        inputGraph.copy(activators = inputGraph.activators + incoming)
    } else {
        inputGraph.copy(
            activators = inputGraph.activators.map {
                if (it.activator.id == bucket.activator.id) it.copy(bindings = it.bindings + binding) else it
            },
        )
    }
}

/** Put a whole activator on a row, materializing the row if it doesn't exist yet. */
private fun ControllerConfig.withActivatorOnRow(
    bindingGroupId: Long,
    inputKey: String,
    activator: ActivatorGraph,
): ControllerConfig = withRow(bindingGroupId, inputKey, activator) { inputGraph, incoming ->
    inputGraph.copy(activators = inputGraph.activators + incoming)
}

/** Apply [place] to a row's activators, materializing the row (carrying [incoming]) when the
 *  binding group doesn't have it yet — as the repository's `ensureGroupInputId` does. */
private fun ControllerConfig.withRow(
    bindingGroupId: Long,
    inputKey: String,
    incoming: ActivatorGraph,
    place: (GroupInputGraph, ActivatorGraph) -> GroupInputGraph,
): ControllerConfig = mapBindingGroup(bindingGroupId) { group ->
    val existingRow = group.inputByKey(inputKey)
        ?: return@mapBindingGroup group.copy(
            inputs = group.inputs + GroupInputGraph(
                input = GroupInput(
                    bindingGroupId = group.group.id,
                    inputKey = inputKey,
                    orderIndex = (group.inputs.maxOfOrNull { it.input.orderIndex } ?: -1) + 1,
                ),
                activators = listOf(incoming),
            ),
        )
    group.copy(
        inputs = group.inputs.map { inputGraph ->
            if (inputGraph.input.inputKey != existingRow.input.inputKey) inputGraph
            else place(inputGraph, incoming)
        },
    )
}

/** Every binding group in the config, wherever it lives. */
private fun ControllerConfig.allBindingGroups(): List<BindingGroupGraph> = buildList {
    actionSets.forEach { set ->
        set.preset.forEach { add(it.group) }
        set.modeShifts.forEach { add(it.group) }
        set.layers.forEach { layer ->
            layer.preset.forEach { add(it.group) }
            addAll(layer.bindingGroups)
            layer.modeShifts.forEach { add(it.group) }
        }
    }
}

/** Apply [transform] to the [BindingGroupGraph] with [bindingGroupId], wherever it lives —
 *  a set's preset, a layer's preset or binding groups, or a mode shift's target. */
private fun ControllerConfig.mapBindingGroup(
    bindingGroupId: Long,
    transform: (BindingGroupGraph) -> BindingGroupGraph,
): ControllerConfig {
    fun mapGroup(g: BindingGroupGraph): BindingGroupGraph =
        if (g.group.id == bindingGroupId) transform(g) else g

    fun mapPreset(entries: List<PresetEntry>): List<PresetEntry> =
        entries.map { it.copy(group = mapGroup(it.group)) }

    return copy(
        actionSets = actionSets.map { set ->
            set.copy(
                preset = mapPreset(set.preset),
                modeShifts = set.modeShifts.map { it.copy(group = mapGroup(it.group)) },
                layers = set.layers.map { layer ->
                    layer.copy(
                        preset = mapPreset(layer.preset),
                        bindingGroups = layer.bindingGroups.map(::mapGroup),
                        modeShifts = layer.modeShifts.map { it.copy(group = mapGroup(it.group)) },
                    )
                },
            )
        },
    )
}

