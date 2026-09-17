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
 * The in-memory twin of `ControllerConfigRepository.moveInputCell`: move the command at
 * ([fromKey], [fromType]) onto ([toKey], [toType]) within the binding group [bindingGroupId],
 * swapping with whatever is already there.
 *
 * Mirrors the repository by REPARENTING the activator rather than copying its contents, so
 * activator settings and every binding under it travel with the command and no identifier is
 * regenerated for something that isn't a copy.
 *
 * Returns the config unchanged when the source cell doesn't exist or the move is a no-op.
 */
fun ControllerConfig.withInputCellMoved(
    bindingGroupId: Long,
    fromKey: String,
    fromType: ActivatorType,
    toKey: String,
    toType: ActivatorType,
    toBindingGroupId: Long = bindingGroupId,
): ControllerConfig {
    if (toBindingGroupId != bindingGroupId) {
        return withInputCellMovedAcrossGroups(bindingGroupId, fromKey, fromType, toBindingGroupId, toKey, toType)
    }
    if (fromKey == toKey && fromType == toType) return this
    return mapBindingGroup(bindingGroupId) { group ->
        group.movingCell(fromKey, fromType, toKey, toType)
    }
}

/**
 * The cross-group half of [withInputCellMoved]: the moving activator leaves its row in one
 * binding group and lands in another (materializing the destination row bare, as the
 * repository does), and whatever it displaces goes back the other way.
 */
private fun ControllerConfig.withInputCellMovedAcrossGroups(
    fromGroupId: Long,
    fromKey: String,
    fromType: ActivatorType,
    toGroupId: Long,
    toKey: String,
    toType: ActivatorType,
): ControllerConfig {
    val fromGroup = findBindingGroup(fromGroupId) ?: return this
    val toGroup = findBindingGroup(toGroupId) ?: return this
    val moving = fromGroup.inputByKey(fromKey)?.firstActivatorOfType(fromType) ?: return this
    val displaced = toGroup.inputByKey(toKey)?.firstActivatorOfType(toType)
    val movedIn = moving.copy(activator = moving.activator.copy(type = toType))
    val movedOut = displaced?.copy(activator = displaced.activator.copy(type = fromType))
    return this
        .mapBindingGroup(fromGroupId) { group ->
            group.replacingOnRow(fromKey, removeId = moving.activator.id, add = movedOut)
        }
        .mapBindingGroup(toGroupId) { group ->
            group.replacingOnRow(toKey, removeId = displaced?.activator?.id, add = movedIn)
        }
}

/** On row [inputKey]: drop the activator [removeId] (if any) and add [add] (if any),
 *  materializing the row bare when it doesn't exist yet and there's something to add. */
private fun BindingGroupGraph.replacingOnRow(
    inputKey: String,
    removeId: Long?,
    add: ActivatorGraph?,
): BindingGroupGraph {
    val existing = inputByKey(inputKey)
    if (existing == null) {
        if (add == null) return this
        return copy(
            inputs = inputs + GroupInputGraph(
                input = GroupInput(
                    bindingGroupId = group.id,
                    inputKey = inputKey,
                    orderIndex = (inputs.maxOfOrNull { it.input.orderIndex } ?: -1) + 1,
                ),
                activators = listOf(add),
            ),
        )
    }
    return copy(
        inputs = inputs.map { inputGraph ->
            if (inputGraph.input.inputKey != inputKey) return@map inputGraph
            val kept = inputGraph.activators.filterNot { removeId != null && it.activator.id == removeId }
            inputGraph.copy(activators = if (add != null) kept + add else kept)
        },
    )
}

/** The [BindingGroupGraph] with [bindingGroupId], searched everywhere [mapBindingGroup] maps. */
private fun ControllerConfig.findBindingGroup(bindingGroupId: Long): BindingGroupGraph? {
    actionSets.forEach { set ->
        set.preset.firstOrNull { it.group.group.id == bindingGroupId }?.let { return it.group }
        set.modeShifts.firstOrNull { it.group.group.id == bindingGroupId }?.let { return it.group }
        set.layers.forEach { layer ->
            layer.preset.firstOrNull { it.group.group.id == bindingGroupId }?.let { return it.group }
            layer.bindingGroups.firstOrNull { it.group.id == bindingGroupId }?.let { return it }
            layer.modeShifts.firstOrNull { it.group.group.id == bindingGroupId }?.let { return it.group }
        }
    }
    return null
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

/** The cell move, within one binding group. */
private fun BindingGroupGraph.movingCell(
    fromKey: String,
    fromType: ActivatorType,
    toKey: String,
    toType: ActivatorType,
): BindingGroupGraph {
    val fromInput = inputs.firstOrNull { it.input.inputKey == fromKey } ?: return this
    val moving = fromInput.activators.firstOrNull { it.activator.type == fromType } ?: return this

    // The destination row may not exist yet — the repository creates it bare (no seeded
    // activator), so do the same here or the two sides would disagree by one empty Press cell.
    val existingTo = inputs.firstOrNull { it.input.inputKey == toKey }
    val displaced = existingTo?.activators?.firstOrNull { it.activator.type == toType }

    val movedIn = moving.copy(activator = moving.activator.copy(type = toType))
    val movedOut = displaced?.copy(activator = displaced.activator.copy(type = fromType))

    val updated = inputs.map { inputGraph ->
        when (inputGraph.input.inputKey) {
            // Both ends on one row: drop both originals, add both replacements.
            fromKey, toKey -> {
                var activators = inputGraph.activators
                    .filterNot { it.activator.id == moving.activator.id }
                    .filterNot { displaced != null && it.activator.id == displaced.activator.id }
                if (inputGraph.input.inputKey == toKey) activators = activators + movedIn
                if (inputGraph.input.inputKey == fromKey && movedOut != null) {
                    activators = activators + movedOut
                }
                inputGraph.copy(activators = activators)
            }
            else -> inputGraph
        }
    }

    val withDestination = if (existingTo != null) {
        updated
    } else {
        // Materialize the destination row, carrying the moved activator.
        updated + GroupInputGraph(
            input = GroupInput(
                bindingGroupId = group.id,
                inputKey = toKey,
                orderIndex = (inputs.maxOfOrNull { it.input.orderIndex } ?: -1) + 1,
            ),
            activators = listOf(movedIn),
        )
    }
    return copy(inputs = withDestination)
}
