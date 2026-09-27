package com.mappo.data.repository

import com.mappo.data.db.steam.ActionLayerDao
import com.mappo.data.db.steam.ActionSetDao
import com.mappo.data.db.steam.ActivatorDao
import com.mappo.data.db.steam.BindingDao
import com.mappo.data.db.steam.BindingGroupDao
import com.mappo.data.db.steam.ControllerProfileDao
import com.mappo.data.db.steam.GameActionDao
import com.mappo.data.db.steam.GroupInputDao
import com.mappo.data.db.steam.PresetBindingDao
import com.mappo.data.io.vdf.ImportedCommand
import com.mappo.data.io.vdf.ImportedConfig
import com.mappo.data.io.vdf.ImportedPresetGroup
import com.mappo.data.io.vdf.VdfMappings
import com.mappo.data.model.steam.ActionLayer
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSet
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.Activator
import com.mappo.data.model.steam.ActivatorGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.Binding
import com.mappo.data.model.steam.BindingGroup
import com.mappo.data.model.steam.BindingGroupGraph
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.BindingOutputType
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.ControllerProfile
import com.mappo.data.model.steam.ControllerType
import com.mappo.data.model.steam.GroupInput
import com.mappo.data.model.steam.GroupInputGraph
import com.mappo.data.model.steam.InputSource
import com.mappo.data.model.steam.LayerPresetBinding
import com.mappo.data.model.steam.PresetBinding
import com.mappo.data.model.steam.PresetEntry
import com.mappo.data.model.steam.SourceModeShift
import com.mappo.service.input.modes.handler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads + mutates the Steam-Input-style binding graph for a given [com.mappo.data.model.Layout].
 *
 * Lifecycle:
 *  - [observeActiveConfig] auto-seeds a default config if none exists, so the UI
 *    can subscribe without separately calling [ensureSeeded].
 *  - Mutations bump [configDirtyTick] so observers re-load. We don't observe every
 *    DAO Flow individually because the materialized graph spans 7 tables;
 *    re-loading on a tick is simpler and the snapshot is cheap to recompute.
 *
 * Clone invariant (per feedback_duplicates_own_their_data): every seeded child
 * gets a fresh auto-generated PK from Room — we never copy ids from a template.
 * When duplicate-action-set lands in Phase 4, it must follow the same rule.
 */
@Singleton
class ControllerConfigRepository @Inject constructor(
    private val controllerProfileDao: ControllerProfileDao,
    private val actionSetDao: ActionSetDao,
    private val actionLayerDao: ActionLayerDao,
    private val gameActionDao: GameActionDao,
    private val bindingGroupDao: BindingGroupDao,
    private val groupInputDao: GroupInputDao,
    private val activatorDao: ActivatorDao,
    private val bindingDao: BindingDao,
    private val presetBindingDao: PresetBindingDao,
    private val layerPresetBindingDao: com.mappo.data.db.steam.LayerPresetBindingDao,
    private val sourceModeShiftDao: com.mappo.data.db.steam.SourceModeShiftDao,
) {

    private val configDirtyTick = MutableStateFlow(0L)

    /**
     * Ensures the given layout has a [ControllerProfile]. Returns the active
     * controller_profile's id (either pre-existing or newly seeded).
     */
    suspend fun ensureSeeded(layoutId: Long): Long {
        controllerProfileDao.getByLayout(layoutId).firstOrNull()?.let { return it.id }
        return seedDefaultConfig(layoutId)
    }

    /**
     * Seeds a default Steam-Input-style config under [layoutId]:
     * one Generic Android controller_profile, one "Default" action_set,
     * one binding_group per default input source, default activators (FULL_PRESS)
     * with [BindingOutputType.UNBOUND] bindings.
     *
     * Caller is responsible for ensuring no controller_profile exists for [layoutId]
     * already; otherwise this creates a second one.
     */
    suspend fun seedDefaultConfig(layoutId: Long): Long {
        val controllerProfileId = controllerProfileDao.insert(
            ControllerProfile(
                layoutId = layoutId,
                controllerType = ControllerType.GENERIC_ANDROID,
                name = "Default",
                legacySet = true,
            )
        )

        val actionSetId = actionSetDao.insert(
            ActionSet(
                controllerProfileId = controllerProfileId,
                name = "default",
                // What the controls bar's switcher shows. "Layout Set 1" (Dylan, 2026-09-27) —
                // it was "Default Map", which named a Steam concept nobody here uses and read as
                // a fallback rather than as the first of however many the user goes on to make.
                title = "Layout Set 1",
                legacy = true,
                orderIndex = 0,
            )
        )
        seedDefaultSetContents(actionSetId)

        configDirtyTick.value = configDirtyTick.value + 1
        return controllerProfileId
    }

    /**
     * Populate [actionSetId] with one [BindingGroup] per [DEFAULT_INPUT_SOURCE_SEEDS]
     * entry, each carrying a default FULL_PRESS activator with a single Unbound
     * binding, plus the matching `active`-state [PresetBinding]. Shared by
     * [seedDefaultConfig] and [addActionSet] when no inherit-from set is supplied.
     */
    private suspend fun seedDefaultSetContents(actionSetId: Long) {
        for ((inputSource, spec) in DEFAULT_INPUT_SOURCE_SEEDS) {
            seedSingleInputSource(actionSetId, inputSource, spec)
        }
    }

    /**
     * Insert a [BindingGroup] (+ child GroupInputs / Activators / Bindings) and
     * the matching `active`-state [PresetBinding] for [inputSource] under
     * [actionSetId]. Extracted from [seedDefaultSetContents] so
     * [ensureSeededInputSources] can call it for missing seeds without
     * re-creating already-present groups.
     */
    private suspend fun seedSingleInputSource(
        actionSetId: Long,
        inputSource: InputSource,
        spec: InputSourceSeed,
    ) {
        val groupId = bindingGroupDao.insert(
            BindingGroup(
                actionSetId = actionSetId,
                actionLayerId = null,
                name = spec.groupName,
                mode = spec.mode,
                // The MODE's own defaults, not an empty object: a seeded layout now starts in a
                // real mode (see DEFAULT_INPUT_SOURCE_SEEDS), and a real mode with `{}` settings
                // is a mode running on whatever its reader falls back to rather than on the
                // values its author tuned — the d-pad's deadzone and overlap, the trigger's
                // thresholds, the stick's response curve.
                settingsJson = spec.mode.handler().defaultSettingsJson(),
            )
        )

        seedGroupRows(groupId, spec)

        presetBindingDao.insert(
            PresetBinding(
                actionSetId = actionSetId,
                inputSource = inputSource,
                state = "active",
                bindingGroupId = groupId,
            )
        )
    }

    /**
     * Lay a binding group's rows out as [spec] describes them: one GroupInput per sub-input,
     * each with a FULL_PRESS activator holding one binding — the sub-input's own hardware
     * equivalent where the seed names one, Unbound where it doesn't.
     *
     * Shared by the seeder and by [resetBindingGroup], which is the whole point: "reset this
     * group to its defaults" has to mean the SAME defaults a new layout gets, and the two used
     * to be separate copies of this loop that had already drifted apart (the reset rebuilt every
     * row Unbound).
     */
    private suspend fun seedGroupRows(bindingGroupId: Long, spec: InputSourceSeed) {
        spec.inputKeys.forEachIndexed { index, inputKey ->
            val groupInputId = groupInputDao.insert(
                GroupInput(
                    bindingGroupId = bindingGroupId,
                    inputKey = inputKey,
                    orderIndex = index,
                )
            )
            val activatorId = activatorDao.insert(
                Activator(
                    groupInputId = groupInputId,
                    type = ActivatorType.FULL_PRESS,
                    settingsJson = "{}",
                    orderIndex = 0,
                )
            )
            val (outputType, args) = (spec.defaults[inputKey] ?: BindingOutput.Unbound).toEntity()
            bindingDao.insert(
                Binding(
                    activatorId = activatorId,
                    outputType = outputType,
                    args = args,
                    orderIndex = 0,
                )
            )
        }
    }

    // ── VDF import persistence (Phase 8a) ────────────────────────────────────

    /**
     * Persist a translated [ImportedConfig] (from `com.mappo.data.io.vdf.VdfImporter`)
     * as a new [ControllerProfile] under [layoutId]. Returns the new
     * controller_profile id.
     *
     * Walks the id-free import model inserting entities in dependency order, so every
     * row gets a fresh Room id (`feedback_duplicates_own_their_data`) — the imported
     * config is independently addressable and never shares ids with another layout.
     *
     * **Mode shifts** are wired in a second pass: a `mode_shift` VDF command became an
     * [ImportedCommand.ModeShiftTrigger]; we collect those during the walk and, once
     * every group has an id, resolve each one's `targetVdfGroupId` to the inserted
     * `modeshift`-state group and create the [SourceModeShift]. Per
     * `project_mode_shift_per_source_architecture` a mode shift is its own entity, NOT
     * a binding — so `modeshift`-state preset groups are inserted as addressable groups
     * but get no [PresetBinding] / [LayerPresetBinding]; they exist only as shift targets.
     *
     * **Absent sources** are intentionally not back-filled here: a source the VDF never
     * binds simply has no group, which the runtime treats as `DEVICE_DEFAULT`, and
     * [ensureSeededInputSources] (run on startup) materializes the visible default rows.
     *
     * **Deferred** (already surfaced as `ImportWarning`s at translate time): `switches`
     * fan-out, VDF setting-key schema translation (carried under a namespaced `_vdf`
     * key the runtime ignores), and remapping `CHANGE_PRESET` / `add_layer` argument ids
     * from VDF-preset space to Mappo Room ids.
     */
    suspend fun importConfig(layoutId: Long, imported: ImportedConfig): Long {
        val controllerProfileId = controllerProfileDao.insert(
            ControllerProfile(
                layoutId = layoutId,
                controllerType = imported.controllerType,
                name = imported.title,
                legacySet = imported.isLegacyRawBindings,
            )
        )

        // VDF group id → inserted binding_group id, for mode-shift target resolution.
        val groupIdByVdfId = HashMap<String, Long>()
        val pendingShifts = ArrayList<PendingModeShift>()

        for (set in imported.sets) {
            val actionSetId = actionSetDao.insert(
                ActionSet(
                    controllerProfileId = controllerProfileId,
                    name = set.name,
                    title = set.title,
                    legacy = set.legacy,
                    orderIndex = set.orderIndex,
                )
            )
            for (pg in set.groups) {
                insertImportedPresetGroup(actionSetId, null, pg, groupIdByVdfId, pendingShifts)
            }
            set.layers.forEachIndexed { layerIndex, layer ->
                val layerId = actionLayerDao.insert(
                    ActionLayer(
                        parentActionSetId = actionSetId,
                        name = layer.name,
                        title = layer.title,
                        orderIndex = layerIndex,
                    )
                )
                for (pg in layer.groups) {
                    insertImportedPresetGroup(actionSetId, layerId, pg, groupIdByVdfId, pendingShifts)
                }
            }
        }

        // Second pass — wire mode shifts now that every binding_group has an id.
        val shiftOrderCounters = HashMap<Triple<Long?, Long?, InputSource>, Int>()
        for (ps in pendingShifts) {
            val targetGroupId = groupIdByVdfId[ps.targetVdfGroupId]
            if (targetGroupId == null) {
                android.util.Log.w(
                    "ControllerConfigRepo",
                    "importConfig: mode_shift target group '${ps.targetVdfGroupId}' not materialized; skipping",
                )
                continue
            }
            val orderKey = Triple(ps.ownerSetId, ps.ownerLayerId, ps.ownerSource)
            val order = shiftOrderCounters.getOrDefault(orderKey, 0)
            shiftOrderCounters[orderKey] = order + 1
            sourceModeShiftDao.insert(
                SourceModeShift(
                    actionSetId = ps.ownerSetId,
                    actionLayerId = ps.ownerLayerId,
                    ownerSource = ps.ownerSource,
                    triggerSource = ps.triggerSource,
                    triggerSubInput = ps.triggerSubInput,
                    bindingGroupId = targetGroupId,
                    displayOrder = order,
                )
            )
        }

        configDirtyTick.value = configDirtyTick.value + 1
        return controllerProfileId
    }

    /**
     * Insert one [ImportedPresetGroup] — the binding_group plus its inputs /
     * activators / bindings — under either an action set (when [actionLayerId] is null)
     * or an action layer. Records the group in [groupIdByVdfId] and collects any
     * mode-shift triggers into [pendingShifts] for the caller's second pass.
     */
    private suspend fun insertImportedPresetGroup(
        actionSetId: Long,
        actionLayerId: Long?,
        pg: ImportedPresetGroup,
        groupIdByVdfId: MutableMap<String, Long>,
        pendingShifts: MutableList<PendingModeShift>,
    ) {
        val groupId = bindingGroupDao.insert(
            BindingGroup(
                actionSetId = if (actionLayerId == null) actionSetId else null,
                actionLayerId = actionLayerId,
                name = pg.group.name.ifBlank { pg.sourceToken },
                mode = pg.group.mode,
                settingsJson = wrapVdfSettings(pg.group.settingsJson),
            )
        )
        pg.group.vdfId?.let { groupIdByVdfId[it] = groupId }

        for ((inputIdx, input) in pg.group.inputs.withIndex()) {
            val groupInputId = groupInputDao.insert(
                GroupInput(bindingGroupId = groupId, inputKey = input.inputKey, orderIndex = inputIdx),
            )
            for ((actIdx, activator) in input.activators.withIndex()) {
                val activatorId = activatorDao.insert(
                    Activator(
                        groupInputId = groupInputId,
                        type = activator.type,
                        settingsJson = wrapVdfSettings(activator.settingsJson),
                        orderIndex = actIdx,
                    ),
                )
                var bindingOrder = 0
                for (command in activator.commands) {
                    when (command) {
                        is ImportedCommand.Output -> {
                            val (outputType, args) = command.output.toEntity()
                            bindingDao.insert(
                                Binding(
                                    activatorId = activatorId,
                                    outputType = outputType,
                                    args = args,
                                    label = command.label,
                                    iconRef = command.icon,
                                    orderIndex = bindingOrder++,
                                ),
                            )
                        }
                        is ImportedCommand.ModeShiftTrigger -> {
                            val ownerSource = VdfMappings.inputSource(command.ownerSourceToken)
                            if (ownerSource == null) {
                                android.util.Log.w(
                                    "ControllerConfigRepo",
                                    "importConfig: mode_shift owner source '${command.ownerSourceToken}' unmapped; skipping",
                                )
                            } else {
                                pendingShifts += PendingModeShift(
                                    ownerSetId = if (actionLayerId == null) actionSetId else null,
                                    ownerLayerId = actionLayerId,
                                    ownerSource = ownerSource,
                                    triggerSource = pg.inputSource,
                                    triggerSubInput = input.inputKey,
                                    targetVdfGroupId = command.targetVdfGroupId,
                                )
                            }
                        }
                    }
                }
                // Keep every activator with at least one binding (matches the seed
                // convention) — e.g. an activator whose only command was a mode shift.
                if (bindingOrder == 0) {
                    bindingDao.insert(
                        Binding(activatorId = activatorId, outputType = BindingOutputType.UNBOUND, args = "", orderIndex = 0),
                    )
                }
            }
        }

        // A `modeshift`-state group is a mode-shift *target*, not a live preset binding
        // (its trigger lives on another source's button). Insert the group as an
        // addressable shift target but no preset row.
        if (pg.state != "modeshift") {
            if (actionLayerId == null) {
                presetBindingDao.insert(
                    PresetBinding(
                        actionSetId = actionSetId,
                        inputSource = pg.inputSource,
                        state = pg.state,
                        bindingGroupId = groupId,
                    ),
                )
            } else {
                layerPresetBindingDao.insert(
                    LayerPresetBinding(
                        actionLayerId = actionLayerId,
                        inputSource = pg.inputSource,
                        state = pg.state,
                        bindingGroupId = groupId,
                    ),
                )
            }
        }
    }

    /** Wrap raw VDF group/activator settings under a namespaced `_vdf` key so they
     *  survive import losslessly without the runtime mis-reading a VDF key as a Mappo
     *  setting (schema-key translation is a later brick). Empty → "{}". */
    private fun wrapVdfSettings(rawJson: String): String =
        if (rawJson.isBlank() || rawJson == "{}") "{}" else """{"_vdf":$rawJson}"""

    /** A mode shift awaiting its target group's id (resolved in [importConfig]'s second pass). */
    private data class PendingModeShift(
        val ownerSetId: Long?,
        val ownerLayerId: Long?,
        val ownerSource: InputSource,
        val triggerSource: InputSource,
        val triggerSubInput: String,
        val targetVdfGroupId: String,
    )

    /**
     * Back-fill any [DEFAULT_INPUT_SOURCE_SEEDS] entry that's missing from
     * each existing action set. Idempotent — runs on every app start and is a
     * no-op once every set has been retrofitted. Generic for the next time we
     * add an input source to the seed table (gyro was the first such addition
     * after D.3 enabled the runtime), so we don't repeatedly rediscover the
     * "user's pre-existing layout shows no picker for the new source"
     * problem.
     *
     * Compares each action set's `active`-state PresetBindings to the seed
     * table's keys and seeds the missing ones. Existing rows are untouched.
     */
    suspend fun ensureSeededInputSources() {
        val sets = actionSetDao.getAll()
        if (sets.isEmpty()) return
        val setIds = sets.map { it.id }
        val activePresetsBySetId = presetBindingDao.getByActionSets(setIds)
            .filter { it.state == "active" }
            .groupBy { it.actionSetId }
        var retrofittedAny = false
        for (set in sets) {
            val present = activePresetsBySetId[set.id]?.map { it.inputSource }?.toSet().orEmpty()
            val missing = DEFAULT_INPUT_SOURCE_SEEDS.keys - present
            if (missing.isEmpty()) continue
            android.util.Log.i(
                "ControllerConfigRepo",
                "ensureSeededInputSources: set ${set.id} missing $missing — retrofitting",
            )
            for (inputSource in missing) {
                val spec = DEFAULT_INPUT_SOURCE_SEEDS.getValue(inputSource)
                seedSingleInputSource(set.id, inputSource, spec)
            }
            retrofittedAny = true
        }
        if (retrofittedAny) {
            // Bump the dirty tick so any live config subscriber refreshes.
            configDirtyTick.value = configDirtyTick.value + 1
        }
    }

    /**
     * Deep-clone every group / input / activator / binding / preset / layer
     * under [sourceSetId] into [destSetId], using a fresh autogenerated id for
     * every new row (per feedback_duplicates_own_their_data). The destination
     * set is assumed to already exist and be empty.
     */
    private suspend fun cloneSetContents(sourceSetId: Long, destSetId: Long) {
        val sourceLayers = actionLayerDao.getByActionSets(listOf(sourceSetId))
        val layerIdMap = HashMap<Long, Long>(sourceLayers.size)
        for (sourceLayer in sourceLayers) {
            val newLayerId = actionLayerDao.insert(
                sourceLayer.copy(id = 0, parentActionSetId = destSetId)
            )
            layerIdMap[sourceLayer.id] = newLayerId
        }

        for (sourceGameAction in gameActionDao.getByActionSets(listOf(sourceSetId))) {
            gameActionDao.insert(sourceGameAction.copy(id = 0, actionSetId = destSetId))
        }

        val groupsUnderSet = bindingGroupDao.getByActionSets(listOf(sourceSetId))
        val groupsUnderLayers = if (sourceLayers.isNotEmpty()) {
            bindingGroupDao.getByActionLayers(sourceLayers.map { it.id })
        } else emptyList()
        val groupIdMap = HashMap<Long, Long>(groupsUnderSet.size + groupsUnderLayers.size)
        for (sourceGroup in groupsUnderSet + groupsUnderLayers) {
            val newGroupId = bindingGroupDao.insert(
                sourceGroup.copy(
                    id = 0,
                    actionSetId = sourceGroup.actionSetId?.let { destSetId },
                    actionLayerId = sourceGroup.actionLayerId?.let(layerIdMap::getValue),
                )
            )
            groupIdMap[sourceGroup.id] = newGroupId
        }

        val allGroupIds = (groupsUnderSet + groupsUnderLayers).map { it.id }
        val sourceInputs = if (allGroupIds.isNotEmpty()) {
            groupInputDao.getByGroups(allGroupIds)
        } else emptyList()
        val inputIdMap = HashMap<Long, Long>(sourceInputs.size)
        for (sourceInput in sourceInputs) {
            val newInputId = groupInputDao.insert(
                sourceInput.copy(
                    id = 0,
                    bindingGroupId = groupIdMap.getValue(sourceInput.bindingGroupId),
                )
            )
            inputIdMap[sourceInput.id] = newInputId
        }

        val sourceActivators = if (sourceInputs.isNotEmpty()) {
            activatorDao.getByGroupInputs(sourceInputs.map { it.id })
        } else emptyList()
        val activatorIdMap = HashMap<Long, Long>(sourceActivators.size)
        for (sourceActivator in sourceActivators) {
            val newActivatorId = activatorDao.insert(
                sourceActivator.copy(
                    id = 0,
                    groupInputId = inputIdMap.getValue(sourceActivator.groupInputId),
                )
            )
            activatorIdMap[sourceActivator.id] = newActivatorId
        }

        if (sourceActivators.isNotEmpty()) {
            for (sourceBinding in bindingDao.getByActivators(sourceActivators.map { it.id })) {
                bindingDao.insert(
                    sourceBinding.copy(
                        id = 0,
                        activatorId = activatorIdMap.getValue(sourceBinding.activatorId),
                    )
                )
            }
        }

        for (sourcePreset in presetBindingDao.getByActionSets(listOf(sourceSetId))) {
            presetBindingDao.insert(
                sourcePreset.copy(
                    id = 0,
                    actionSetId = destSetId,
                    bindingGroupId = groupIdMap.getValue(sourcePreset.bindingGroupId),
                )
            )
        }
    }

    /**
     * Append a new [ActionSet] to [controllerProfileId]. When [inheritFromSetId] is
     * null the new set is seeded with the same default groups/inputs/activators as
     * [seedDefaultConfig]; when non-null the named source set is deep-cloned into
     * the new set (groups, inputs, activators, bindings, preset_bindings, layers).
     * Returns the new set id.
     */
    suspend fun addActionSet(
        controllerProfileId: Long,
        name: String,
        title: String,
        inheritFromSetId: Long? = null,
    ): Long {
        val existing = actionSetDao.getByControllerProfile(controllerProfileId)
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val newSetId = actionSetDao.insert(
            ActionSet(
                controllerProfileId = controllerProfileId,
                name = name,
                title = title,
                legacy = true,
                orderIndex = nextOrder,
            )
        )
        if (inheritFromSetId != null) {
            cloneSetContents(inheritFromSetId, newSetId)
        } else {
            seedDefaultSetContents(newSetId)
        }
        configDirtyTick.value = configDirtyTick.value + 1
        return newSetId
    }

    /** Update [actionSetId]'s human-facing [name] / [title]. Other fields are unaffected. */
    suspend fun renameActionSet(actionSetId: Long, name: String, title: String) {
        val existing = actionSetDao.getById(actionSetId) ?: return
        if (existing.name == name && existing.title == title) return
        actionSetDao.update(existing.copy(name = name, title = title))
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Deep-clone [sourceSetId] under the same controller_profile with [name] / [title].
     * Returns the new set id. Convenience wrapper around [addActionSet] with
     * `inheritFromSetId = sourceSetId`.
     */
    suspend fun duplicateActionSet(sourceSetId: Long, name: String, title: String): Long {
        val source = actionSetDao.getById(sourceSetId)
            ?: error("Source action set $sourceSetId not found")
        return addActionSet(source.controllerProfileId, name, title, inheritFromSetId = sourceSetId)
    }

    /**
     * Delete [actionSetId]. Guards against deleting the last remaining set under a
     * controller_profile (returns false). The starting set is implicitly the first
     * set by orderIndex on whatever remains, so no reassignment is needed.
     * Returns true on successful deletion.
     */
    suspend fun deleteActionSet(actionSetId: Long): Boolean {
        val existing = actionSetDao.getById(actionSetId) ?: return false
        val siblings = actionSetDao.getByControllerProfile(existing.controllerProfileId)
        if (siblings.size <= 1) return false

        actionSetDao.deleteById(actionSetId)
        configDirtyTick.value = configDirtyTick.value + 1
        return true
    }

    /**
     * Append a new empty [ActionLayer] under [actionSetId] (Brick 5.2). Layers carry no
     * overlay binding_groups at create time — the user fills overlays in by overriding
     * specific bindings from the parent set (Brick 5.5). The new layer's [ActionLayer.orderIndex]
     * is the next slot after existing siblings; returns the new layer id.
     */
    suspend fun addLayer(actionSetId: Long, name: String, title: String): Long {
        val existing = actionLayerDao.getByActionSets(listOf(actionSetId))
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val newLayerId = actionLayerDao.insert(
            ActionLayer(
                parentActionSetId = actionSetId,
                name = name,
                title = title,
                orderIndex = nextOrder,
            )
        )
        configDirtyTick.value = configDirtyTick.value + 1
        return newLayerId
    }

    /** Update [layerId]'s human-facing [name] / [title]. No-op when [layerId] is unknown. */
    suspend fun renameLayer(layerId: Long, name: String, title: String) {
        val existing = actionLayerDao.getById(layerId) ?: return
        if (existing.name == name && existing.title == title) return
        actionLayerDao.update(existing.copy(name = name, title = title))
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Deep-clone [sourceLayerId] (and any binding_groups / inputs / activators / bindings
     * beneath it) into a sibling layer with [name] / [title] on the same parent action set.
     * Per `feedback_duplicates_own_their_data`, every cloned row gets a fresh autogenerated
     * id so the duplicate is independently addressable. Returns the new layer id.
     *
     * Today layers carry no overlay groups (5.1 + 5.2 only create empty layers); the
     * cloning logic exists so 5.5's overlay-authoring writes don't have to retrofit it.
     */
    suspend fun duplicateLayer(sourceLayerId: Long, name: String, title: String): Long {
        val source = actionLayerDao.getById(sourceLayerId)
            ?: error("Source layer $sourceLayerId not found")

        val siblings = actionLayerDao.getByActionSets(listOf(source.parentActionSetId))
        val nextOrder = (siblings.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val newLayerId = actionLayerDao.insert(
            ActionLayer(
                parentActionSetId = source.parentActionSetId,
                name = name,
                title = title,
                orderIndex = nextOrder,
            )
        )

        val sourceGroups = bindingGroupDao.getByActionLayers(listOf(sourceLayerId))
        val groupIdMap = HashMap<Long, Long>(sourceGroups.size)
        for (sourceGroup in sourceGroups) {
            val newGroupId = bindingGroupDao.insert(
                sourceGroup.copy(id = 0, actionLayerId = newLayerId)
            )
            groupIdMap[sourceGroup.id] = newGroupId
        }

        if (sourceGroups.isNotEmpty()) {
            val sourceInputs = groupInputDao.getByGroups(sourceGroups.map { it.id })
            val inputIdMap = HashMap<Long, Long>(sourceInputs.size)
            for (sourceInput in sourceInputs) {
                val newInputId = groupInputDao.insert(
                    sourceInput.copy(
                        id = 0,
                        bindingGroupId = groupIdMap.getValue(sourceInput.bindingGroupId),
                    )
                )
                inputIdMap[sourceInput.id] = newInputId
            }

            if (sourceInputs.isNotEmpty()) {
                val sourceActivators = activatorDao.getByGroupInputs(sourceInputs.map { it.id })
                val activatorIdMap = HashMap<Long, Long>(sourceActivators.size)
                for (sourceActivator in sourceActivators) {
                    val newActivatorId = activatorDao.insert(
                        sourceActivator.copy(
                            id = 0,
                            groupInputId = inputIdMap.getValue(sourceActivator.groupInputId),
                        )
                    )
                    activatorIdMap[sourceActivator.id] = newActivatorId
                }

                if (sourceActivators.isNotEmpty()) {
                    for (sourceBinding in bindingDao.getByActivators(sourceActivators.map { it.id })) {
                        bindingDao.insert(
                            sourceBinding.copy(
                                id = 0,
                                activatorId = activatorIdMap.getValue(sourceBinding.activatorId),
                            )
                        )
                    }
                }
            }
        }

        // 5.5.a: clone layer preset entries so the duplicate's overrides point at the
        // cloned binding_groups. Without this, the copy would have orphan layer rows
        // and no actual override mapping.
        for (sourcePreset in layerPresetBindingDao.getByActionLayers(listOf(sourceLayerId))) {
            val mappedGroupId = groupIdMap[sourcePreset.bindingGroupId] ?: continue
            layerPresetBindingDao.insert(
                sourcePreset.copy(
                    id = 0,
                    actionLayerId = newLayerId,
                    bindingGroupId = mappedGroupId,
                )
            )
        }

        configDirtyTick.value = configDirtyTick.value + 1
        return newLayerId
    }

    /**
     * Delete [layerId]. Schema cascades drop any binding_groups (→ group_inputs →
     * activators → bindings) beneath it. Unlike `deleteActionSet`, there's no "last
     * layer must remain" guard — an action set with zero layers is valid. Returns
     * true if a row was deleted.
     */
    suspend fun deleteLayer(layerId: Long): Boolean {
        actionLayerDao.getById(layerId) ?: return false
        actionLayerDao.deleteById(layerId)
        configDirtyTick.value = configDirtyTick.value + 1
        return true
    }

    // ── Brick 5.5.b: layer override materialization ──────────────────────────

    /**
     * Find-or-create the override chain for `(layerId, inputSource, groupInputKey)`.
     *
     * When the user taps a "ghost" row in overlay-editing mode, the row has no
     * persisted override yet — only the parent set's binding inherits through. This
     * call materializes the scaffolding: an overlay [BindingGroup] on the layer (one
     * per input source, shared across that source's sub-inputs to mirror the base
     * preset grain), a [GroupInput] for the requested key, a default `FULL_PRESS`
     * activator, and a single unbound [Binding]. Subsequent `setBinding` calls write
     * the real output.
     *
     * **Inherits from base**: the new overlay group copies the base set's group's
     * `mode` and `settingsJson` for the same input source — so e.g. an overlay on a
     * trackpad starts with the same deadzone settings the base has. The user can
     * diverge later (Phase 6).
     *
     * Idempotent on the sub-input level: calling twice with identical args returns
     * the same group_input id. Returns the [GroupInput.id] of the materialized row —
     * callers can immediately use it for further wiring (e.g., opening the picker).
     */
    suspend fun materializeLayerOverride(
        layerId: Long,
        inputSource: InputSource,
        groupInputKey: String,
    ): Long {
        val layer = actionLayerDao.getById(layerId)
            ?: error("Unknown layer $layerId")

        // 1. Find or create the overlay binding_group for this input source on the layer.
        val existingPreset = layerPresetBindingDao.getByActionLayers(listOf(layerId))
            .firstOrNull { it.inputSource == inputSource && it.state == "active" }

        val overlayGroupId: Long = if (existingPreset != null) {
            existingPreset.bindingGroupId
        } else {
            // Inherit mode + settings from the base set's group for the same input source.
            // Falls back to BUTTON_PAD + "{}" if the base has no preset entry — shouldn't
            // normally happen for seeded sources, but defensive.
            val basePresetRow = presetBindingDao.getByActionSets(listOf(layer.parentActionSetId))
                .firstOrNull { it.inputSource == inputSource && it.state == "active" }
            val baseGroup = basePresetRow?.let { bindingGroupDao.getById(it.bindingGroupId) }
            val newGroupId = bindingGroupDao.insert(
                BindingGroup(
                    actionSetId = null,
                    actionLayerId = layerId,
                    name = baseGroup?.name ?: inputSource.name.lowercase(),
                    mode = baseGroup?.mode ?: BindingMode.BUTTON_PAD,
                    settingsJson = baseGroup?.settingsJson ?: "{}",
                )
            )
            layerPresetBindingDao.insert(
                LayerPresetBinding(
                    actionLayerId = layerId,
                    inputSource = inputSource,
                    state = "active",
                    bindingGroupId = newGroupId,
                )
            )
            newGroupId
        }

        // 2. Idempotency on the sub-input — return the existing row if already materialized.
        groupInputDao.getByGroups(listOf(overlayGroupId))
            .firstOrNull { it.inputKey == groupInputKey }
            ?.let { return it.id }

        // 3. Fresh group_input + default activator + unbound binding.
        val nextOrder = groupInputDao.getByGroups(listOf(overlayGroupId))
            .maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
        val newInputId = groupInputDao.insert(
            GroupInput(
                bindingGroupId = overlayGroupId,
                inputKey = groupInputKey,
                orderIndex = nextOrder,
            )
        )
        val activatorId = activatorDao.insert(
            Activator(
                groupInputId = newInputId,
                type = ActivatorType.FULL_PRESS,
                settingsJson = "{}",
                orderIndex = 0,
            )
        )
        bindingDao.insert(
            Binding(
                activatorId = activatorId,
                outputType = BindingOutputType.UNBOUND,
                args = "",
                orderIndex = 0,
            )
        )

        configDirtyTick.value = configDirtyTick.value + 1
        return newInputId
    }

    /**
     * Reverse of [materializeLayerOverride]. Drops the [GroupInput] chain on the
     * layer for `(inputSource, groupInputKey)`, returning the row to inheritance
     * from the parent set. If the overlay [BindingGroup] is left with no remaining
     * inputs, the group itself is also deleted — FK cascade on `layer_preset_binding`
     * drops the orphan preset row. Sibling sub-inputs (e.g., other buttons on the
     * same diamond) are left untouched.
     *
     * No-op when no override exists at `(layerId, inputSource, groupInputKey)`.
     */
    suspend fun clearLayerOverride(
        layerId: Long,
        inputSource: InputSource,
        groupInputKey: String,
    ) {
        val presetRow = layerPresetBindingDao.getByActionLayers(listOf(layerId))
            .firstOrNull { it.inputSource == inputSource && it.state == "active" }
            ?: return

        val target = groupInputDao.getByGroups(listOf(presetRow.bindingGroupId))
            .firstOrNull { it.inputKey == groupInputKey }
            ?: return

        groupInputDao.deleteById(target.id)

        // If we just deleted the last sub-input on this overlay group, drop the group
        // and its preset pointer — the layer no longer has any reason to hold an
        // overlay for this source. Explicit cleanup (rather than relying on FK
        // cascade) so the path stays portable to test fakes and the intent is
        // obvious at the call site.
        val remaining = groupInputDao.getByGroups(listOf(presetRow.bindingGroupId))
        if (remaining.isEmpty()) {
            layerPresetBindingDao.deleteById(presetRow.id)
            bindingGroupDao.deleteById(presetRow.bindingGroupId)
        }

        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Observe the active config for [layoutId]. Auto-seeds if none exists.
     * Emits null only while seeding is in flight on first subscription
     * (transient — the next emission carries the seeded config).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeActiveConfig(layoutId: Long): Flow<ControllerConfig?> = flow {
        ensureSeeded(layoutId)
        // Retrofit any DEFAULT_INPUT_SOURCE_SEEDS entries that were added to the
        // table after this layout's action sets were first seeded (e.g. GYRO,
        // added 2026-05-31 after D.3 lit up the gyro runtime). Idempotent —
        // no-op once every set has every seed. Runs before the first emission
        // so the consumer's compiled config sees the retrofitted groups on the
        // very first frame.
        ensureSeededInputSources()
        emitAll(
            combine(
                controllerProfileDao.observeByProfile(layoutId)
                    .map { layouts -> layouts.firstOrNull() }
                    .distinctUntilChanged { a, b -> a?.id == b?.id },
                configDirtyTick,
            ) { activeLayout, _ -> activeLayout }
                .flatMapLatest { activeLayout ->
                    if (activeLayout == null) flowOf<ControllerConfig?>(null)
                    else flow { emit(loadConfigSnapshot(activeLayout)) }
                }
        )
    }

    /** One-shot read of the active config, or null if none exists yet. */
    suspend fun getActiveConfigOnce(layoutId: Long): ControllerConfig? {
        val cp = controllerProfileDao.getByLayout(layoutId).firstOrNull() ?: return null
        return loadConfigSnapshot(cp)
    }

    /**
     * Deep-clone every [ControllerProfile] under [sourceProfileId] into [destProfileId].
     * No-op when the source has no controller_profile (e.g., fresh layout that hasn't been
     * observed yet — the dest will auto-seed on first observation, same as the source would).
     *
     * Per feedback_duplicates_own_their_data, each cloned row gets a fresh autogenerated id
     * via `.copy(id = 0)` so the duplicate is independently addressable.
     */
    suspend fun copyConfig(sourceProfileId: Long, destProfileId: Long) {
        val sourceControllerProfiles = controllerProfileDao.getByLayout(sourceProfileId)
        if (sourceControllerProfiles.isEmpty()) return

        for (sourceCp in sourceControllerProfiles) {
            val newCpId = controllerProfileDao.insert(
                sourceCp.copy(id = 0, layoutId = destProfileId)
            )

            val sourceSets = actionSetDao.getByControllerProfile(sourceCp.id)
            if (sourceSets.isEmpty()) continue
            val setIdMap = HashMap<Long, Long>(sourceSets.size)
            for (sourceSet in sourceSets) {
                val newSetId = actionSetDao.insert(
                    sourceSet.copy(id = 0, controllerProfileId = newCpId)
                )
                setIdMap[sourceSet.id] = newSetId
            }

            val sourceSetIds = sourceSets.map { it.id }

            for (sourceGameAction in gameActionDao.getByActionSets(sourceSetIds)) {
                gameActionDao.insert(
                    sourceGameAction.copy(
                        id = 0,
                        actionSetId = setIdMap.getValue(sourceGameAction.actionSetId),
                    )
                )
            }

            val sourceLayers = actionLayerDao.getByActionSets(sourceSetIds)
            val layerIdMap = HashMap<Long, Long>(sourceLayers.size)
            for (sourceLayer in sourceLayers) {
                val newLayerId = actionLayerDao.insert(
                    sourceLayer.copy(
                        id = 0,
                        parentActionSetId = setIdMap.getValue(sourceLayer.parentActionSetId),
                    )
                )
                layerIdMap[sourceLayer.id] = newLayerId
            }

            val groupsUnderSets = bindingGroupDao.getByActionSets(sourceSetIds)
            val groupsUnderLayers = if (sourceLayers.isNotEmpty()) {
                bindingGroupDao.getByActionLayers(sourceLayers.map { it.id })
            } else emptyList()
            val groupIdMap = HashMap<Long, Long>(groupsUnderSets.size + groupsUnderLayers.size)
            for (sourceGroup in groupsUnderSets + groupsUnderLayers) {
                val newGroupId = bindingGroupDao.insert(
                    sourceGroup.copy(
                        id = 0,
                        actionSetId = sourceGroup.actionSetId?.let(setIdMap::getValue),
                        actionLayerId = sourceGroup.actionLayerId?.let(layerIdMap::getValue),
                    )
                )
                groupIdMap[sourceGroup.id] = newGroupId
            }

            val allGroupIds = (groupsUnderSets + groupsUnderLayers).map { it.id }
            val sourceInputs = if (allGroupIds.isNotEmpty()) {
                groupInputDao.getByGroups(allGroupIds)
            } else emptyList()
            val inputIdMap = HashMap<Long, Long>(sourceInputs.size)
            for (sourceInput in sourceInputs) {
                val newInputId = groupInputDao.insert(
                    sourceInput.copy(
                        id = 0,
                        bindingGroupId = groupIdMap.getValue(sourceInput.bindingGroupId),
                    )
                )
                inputIdMap[sourceInput.id] = newInputId
            }

            val sourceActivators = if (sourceInputs.isNotEmpty()) {
                activatorDao.getByGroupInputs(sourceInputs.map { it.id })
            } else emptyList()
            val activatorIdMap = HashMap<Long, Long>(sourceActivators.size)
            for (sourceActivator in sourceActivators) {
                val newActivatorId = activatorDao.insert(
                    sourceActivator.copy(
                        id = 0,
                        groupInputId = inputIdMap.getValue(sourceActivator.groupInputId),
                    )
                )
                activatorIdMap[sourceActivator.id] = newActivatorId
            }

            if (sourceActivators.isNotEmpty()) {
                for (sourceBinding in bindingDao.getByActivators(sourceActivators.map { it.id })) {
                    bindingDao.insert(
                        sourceBinding.copy(
                            id = 0,
                            activatorId = activatorIdMap.getValue(sourceBinding.activatorId),
                        )
                    )
                }
            }

            for (sourcePreset in presetBindingDao.getByActionSets(sourceSetIds)) {
                presetBindingDao.insert(
                    sourcePreset.copy(
                        id = 0,
                        actionSetId = setIdMap.getValue(sourcePreset.actionSetId),
                        bindingGroupId = groupIdMap.getValue(sourcePreset.bindingGroupId),
                    )
                )
            }
        }

        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Insert a new [Activator] of [type] on [groupInputId], seeded with one Unbound
     * binding so the activator is immediately editable in the UI. Returns the new id.
     *
     * Order is appended to the end of the group_input's existing activators.
     * Multi-binding (cycle_binding) is Brick 3.3 territory — for now every activator
     * carries one binding.
     */
    suspend fun addActivator(groupInputId: Long, type: ActivatorType): Long {
        val existing = activatorDao.getByGroupInputs(listOf(groupInputId))
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val newActivatorId = activatorDao.insert(
            Activator(
                groupInputId = groupInputId,
                type = type,
                settingsJson = "{}",
                orderIndex = nextOrder,
            )
        )
        bindingDao.insert(
            Binding(
                activatorId = newActivatorId,
                outputType = BindingOutputType.UNBOUND,
                args = "",
                orderIndex = 0,
            )
        )
        configDirtyTick.value = configDirtyTick.value + 1
        return newActivatorId
    }

    /** Delete [activatorId]. Bindings cascade-delete via the schema foreign key. */
    suspend fun removeActivator(activatorId: Long) {
        activatorDao.deleteById(activatorId)
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Change [activatorId]'s [ActivatorType] without disturbing its bindings or
     * settingsJson. Type-specific settings that no longer apply stay in the JSON blob
     * for forward-compat — the evaluator only reads fields its current type cares about.
     */
    suspend fun updateActivatorType(activatorId: Long, type: ActivatorType) {
        val existing = activatorDao.getById(activatorId) ?: return
        if (existing.type == type) return
        activatorDao.update(existing.copy(type = type))
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Change [bindingGroupId]'s [BindingMode] without touching its group inputs or
     * activator wiring. Phase 6 Brick 1: the Remap Controls subheader's mode dropdown
     * routes here. Sub-input keys that aren't valid for the new mode are silently
     * filtered by the compile step's `SourceMode.accepts()` check rather than deleted
     * — leaves the rows intact in case the user picks back to the original mode.
     */
    suspend fun updateBindingGroupMode(bindingGroupId: Long, mode: BindingMode) {
        val existing = bindingGroupDao.getById(bindingGroupId) ?: return
        if (existing.mode == mode) return
        bindingGroupDao.update(existing.copy(mode = mode))
        // 2026-05-31 — auto-seed any sub-inputs the new mode needs that the
        // group doesn't already carry. Without this, the dynamic-row UI
        // renders sub-inputs (from validInputsFor) that have no backing
        // GroupInput record, and BindingRow's `ready` gate disables tap.
        // Most visible failure: GYRO source switched to DPAD or
        // DIRECTIONAL_SWIPE — GYRO's seed has no sub-inputs at all, so
        // every dpad direction row would otherwise be inert.
        //
        // Scoped to set-owned (base) groups; layer-owned overlays keep the
        // existing materialize-on-tap pattern (creating sub-inputs eagerly
        // on a layer would turn every layer into a full mode replacement
        // rather than the targeted overlay Steam treats them as).
        if (existing.actionSetId != null) {
            val source = findInputSourceForSetBindingGroup(bindingGroupId)
            if (source != null) {
                ensureSubInputsForMode(bindingGroupId, source, mode)
            }
        }
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Replace [bindingGroupId]'s mode-specific `settingsJson` blob. The settings
     * cog on each source row in Remap Controls routes here; the runtime mode
     * handlers parse this JSON (tolerant of missing keys). No-op when unchanged.
     */
    suspend fun updateBindingGroupSettings(bindingGroupId: Long, settingsJson: String) {
        val existing = bindingGroupDao.getById(bindingGroupId) ?: return
        if (existing.settingsJson == settingsJson) return
        bindingGroupDao.update(existing.copy(settingsJson = settingsJson))
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Find the [InputSource] that owns [bindingGroupId] when it's a
     * set-owned (base) group. Walks the active `preset_binding` rows whose
     * bindingGroupId matches. Returns null if the group isn't preset-linked
     * (shouldn't happen for normally-seeded groups but is defensive).
     */
    private suspend fun findInputSourceForSetBindingGroup(bindingGroupId: Long): InputSource? {
        val group = bindingGroupDao.getById(bindingGroupId) ?: return null
        val actionSetId = group.actionSetId ?: return null
        val presets = presetBindingDao.getByActionSets(listOf(actionSetId))
        return presets.firstOrNull { it.bindingGroupId == bindingGroupId && it.state == "active" }
            ?.inputSource
    }

    /**
     * Create any missing [GroupInput] rows under [bindingGroupId] for each
     * sub-input that the (source, mode) pair surfaces. Each new GroupInput
     * gets a default FULL_PRESS Activator + Unbound Binding so the row is
     * immediately editable from the binding picker. Sub-inputs that already
     * exist on the group are untouched.
     */
    private suspend fun ensureSubInputsForMode(
        bindingGroupId: Long,
        source: InputSource,
        mode: BindingMode,
    ) {
        val wanted = com.mappo.service.input.modes.validInputsFor(source, mode)
        if (wanted.isEmpty()) return
        val existing = groupInputDao.getByGroups(listOf(bindingGroupId))
            .map { it.inputKey }
            .toSet()
        val missing = wanted - existing
        if (missing.isEmpty()) return
        val nextOrderStart = groupInputDao.getByGroups(listOf(bindingGroupId))
            .maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
        for ((idx, inputKey) in missing.withIndex()) {
            val newInputId = groupInputDao.insert(
                GroupInput(
                    bindingGroupId = bindingGroupId,
                    inputKey = inputKey,
                    orderIndex = nextOrderStart + idx,
                )
            )
            val activatorId = activatorDao.insert(
                Activator(
                    groupInputId = newInputId,
                    type = ActivatorType.FULL_PRESS,
                    settingsJson = "{}",
                    orderIndex = 0,
                )
            )
            bindingDao.insert(
                Binding(
                    activatorId = activatorId,
                    outputType = BindingOutputType.UNBOUND,
                    args = "",
                    orderIndex = 0,
                )
            )
        }
    }

    /**
     * Replace [activatorId]'s settingsJson with [settingsJson] verbatim. Called from the
     * activator settings editor; the editor builds the JSON via
     * `CompiledActivatorSettings.toJson()` so the keys stay in sync with the parser.
     */
    suspend fun updateActivatorSettings(activatorId: Long, settingsJson: String) {
        val existing = activatorDao.getById(activatorId) ?: return
        if (existing.settingsJson == settingsJson) return
        activatorDao.update(existing.copy(settingsJson = settingsJson))
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Replace the bindings on [activatorId] with a single binding from [output]. Convenience
     * for the legacy single-command-per-activator path; still used by callers that haven't
     * been migrated to the multi-command UI. Prefer [setCommand] when you have a
     * specific bindingId.
     */
    suspend fun setBinding(activatorId: Long, output: BindingOutput) {
        bindingDao.deleteByActivator(activatorId)
        val (type, args) = output.toEntity()
        bindingDao.insert(
            Binding(
                activatorId = activatorId,
                outputType = type,
                args = args,
                orderIndex = 0,
            )
        )
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Update a specific [Binding] row in place. Used by the multi-command UI (Brick 3.6):
     * each command row carries its bindingId, so the picker writes through to that exact
     * row instead of replacing all bindings on the activator.
     */
    suspend fun setCommand(bindingId: Long, output: BindingOutput) {
        val existing = bindingDao.getById(bindingId) ?: return
        val (type, args) = output.toEntity()
        if (existing.outputType == type && existing.args == args) return
        bindingDao.update(existing.copy(outputType = type, args = args))
        resolveGroupIdForBinding(bindingId)?.let { syncAuxButtonMode(it) }
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Append a new command (Binding row) to [activatorId] at the next orderIndex. The new
     * command is created as Unbound — the user picks its output via the standard picker
     * flow on the freshly-added row. Returns the new bindingId so the UI can scroll to /
     * focus the new row if desired.
     */
    suspend fun addCommand(activatorId: Long): Long {
        val existing = bindingDao.getByActivators(listOf(activatorId))
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val newId = bindingDao.insert(
            Binding(
                activatorId = activatorId,
                outputType = BindingOutputType.UNBOUND,
                args = "",
                orderIndex = nextOrder,
            )
        )
        configDirtyTick.value = configDirtyTick.value + 1
        return newId
    }

    /**
     * Delete a specific command (Binding row). The UI guards against removing the last
     * command on an activator — at least one binding per activator is an invariant the
     * `primaryOutput` accessor and `addActivator` setup both assume.
     */
    suspend fun removeCommand(bindingId: Long) {
        val groupId = resolveGroupIdForBinding(bindingId)
        bindingDao.deleteById(bindingId)
        groupId?.let { syncAuxButtonMode(it) }
        configDirtyTick.value = configDirtyTick.value + 1
    }

    // ── Phase 6: unified "input rows" ────────────────────────────────────────
    // An input row in the UI = one Binding, bucketed under an Activator by press type. Same-type
    // rows are multiple Bindings under one Activator (the runtime fires them together or cycles per
    // the activator's settings). This replaces the separate "extra command" / "sub command" model
    // with a single "add additional input" affordance — no schema change.

    /**
     * Add a new input row of [type] to [groupInputId]. Appends a Binding to the existing same-type
     * Activator, or creates that Activator (with its seeded Unbound binding) if absent. Returns the
     * new bindingId.
     */
    suspend fun addInputRow(groupInputId: Long, type: ActivatorType): Long {
        val existing = activatorDao.getByGroupInputs(listOf(groupInputId)).firstOrNull { it.type == type }
        return if (existing != null) {
            addCommand(existing.id)
        } else {
            val newActivatorId = addActivator(groupInputId, type)
            bindingDao.getByActivators(listOf(newActivatorId)).first().id
        }
    }

    /**
     * Change an input row's press type by reparenting its Binding into the [newType] Activator
     * bucket for the same group input (created without a seed when absent). The old Activator is
     * deleted if the move leaves it empty.
     */
    suspend fun setInputRowPressType(bindingId: Long, newType: ActivatorType) {
        val binding = bindingDao.getById(bindingId) ?: return
        val oldActivator = activatorDao.getById(binding.activatorId) ?: return
        if (oldActivator.type == newType) return
        val groupInputId = oldActivator.groupInputId
        val siblings = activatorDao.getByGroupInputs(listOf(groupInputId))
        val targetId = siblings.firstOrNull { it.type == newType }?.id ?: run {
            val nextOrder = (siblings.maxOfOrNull { it.orderIndex } ?: -1) + 1
            // Create the bucket directly (no seeded binding — the moved binding fills it).
            activatorDao.insert(
                Activator(groupInputId = groupInputId, type = newType, settingsJson = "{}", orderIndex = nextOrder)
            )
        }
        val nextBindingOrder = (bindingDao.getByActivators(listOf(targetId)).maxOfOrNull { it.orderIndex } ?: -1) + 1
        bindingDao.update(binding.copy(activatorId = targetId, orderIndex = nextBindingOrder))
        if (bindingDao.getByActivators(listOf(oldActivator.id)).isEmpty()) {
            activatorDao.deleteById(oldActivator.id)
        }
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /** Set an input row's user label ([Binding.label]); a blank value clears it. */
    suspend fun setInputRowLabel(bindingId: Long, label: String) {
        val binding = bindingDao.getById(bindingId) ?: return
        val normalized = label.trim().ifEmpty { null }
        if (binding.label == normalized) return
        bindingDao.update(binding.copy(label = normalized))
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Set everything the label editor owns in ONE write: the user label plus how the command
     * prints ([Binding.showDeviceIcon] / [Binding.showDeviceInitials]).
     *
     * One call rather than three because they are edited together in one dialog — three writes
     * would be three config reloads, and the table would repaint mid-save.
     */
    suspend fun setInputRowDisplay(
        bindingId: Long,
        label: String,
        showDeviceIcon: Boolean,
        showDeviceInitials: Boolean,
    ) {
        val binding = bindingDao.getById(bindingId) ?: return
        val normalized = label.trim().ifEmpty { null }
        if (binding.label == normalized &&
            binding.showDeviceIcon == showDeviceIcon &&
            binding.showDeviceInitials == showDeviceInitials
        ) {
            return
        }
        bindingDao.update(
            binding.copy(
                label = normalized,
                showDeviceIcon = showDeviceIcon,
                showDeviceInitials = showDeviceInitials,
            ),
        )
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Delete an input row (Binding); its Activator is removed too if now empty. Callers disable this
     * when it's the group input's last remaining row.
     */
    suspend fun deleteInputRow(bindingId: Long) {
        val binding = bindingDao.getById(bindingId) ?: return
        val activatorId = binding.activatorId
        val groupId = resolveGroupIdForBinding(bindingId)
        bindingDao.deleteById(bindingId)
        if (bindingDao.getByActivators(listOf(activatorId)).isEmpty()) {
            activatorDao.deleteById(activatorId)
        }
        groupId?.let { syncAuxButtonMode(it) }
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /** Resolve the owning binding_group id for a [bindingId] (binding → activator → group input). */
    private suspend fun resolveGroupIdForBinding(bindingId: Long): Long? {
        val binding = bindingDao.getById(bindingId) ?: return null
        val activator = activatorDao.getById(binding.activatorId) ?: return null
        return groupInputDao.getById(activator.groupInputId)?.bindingGroupId
    }

    // ── Advanced-table CELL ops ──────────────────────────────────────────────────────────────
    //
    // The remap advanced view is a table: one row per sub-input, one column per press type, so a
    // "cell" is exactly a `(GroupInput.inputKey, ActivatorType)` pair — which is to say, one
    // Activator. The table renders at most ONE command per cell, deliberately: the old row-list
    // editor could stack several same-type Bindings under one Activator and the user cut that
    // affordance as extraneous.
    //
    // The SCHEMA still permits multi-binding activators (cycle_binding needs it, and VDF import
    // produces it), so these ops treat the whole Activator as the unit — clear deletes it
    // outright, move/swap reparents it, and copy/paste carries only the first binding. Extra
    // bindings on an imported activator stay in the database and keep firing; they're just not
    // addressable from this table. That's the intended trade, not an oversight.

    /** Find a group input by key under [bindingGroupId], or null. */
    private suspend fun findGroupInputId(bindingGroupId: Long, inputKey: String): Long? =
        groupInputDao.getByGroups(listOf(bindingGroupId)).firstOrNull { it.inputKey == inputKey }?.id

    /**
     * Find or create the group input for [inputKey] under [bindingGroupId]. Creation is bare — no
     * seeded activator — because every caller here goes on to create the activator it actually
     * wants; seeding a FULL_PRESS would make an empty Press cell appear as a side effect of
     * assigning a Long one.
     */
    private suspend fun ensureGroupInputId(bindingGroupId: Long, inputKey: String): Long {
        findGroupInputId(bindingGroupId, inputKey)?.let { return it }
        val nextOrder = (groupInputDao.getByGroups(listOf(bindingGroupId))
            .maxOfOrNull { it.orderIndex } ?: -1) + 1
        return groupInputDao.insert(
            GroupInput(bindingGroupId = bindingGroupId, inputKey = inputKey, orderIndex = nextOrder)
        )
    }

    /**
     * Ensure the cell at ([inputKey], [type]) exists and return its editable bindingId — creating
     * the group input, the activator, and an Unbound binding as needed. This is what an empty
     * tile's "New" runs before handing off to the command picker.
     */
    suspend fun ensureInputCell(bindingGroupId: Long, inputKey: String, type: ActivatorType): Long {
        val groupInputId = ensureGroupInputId(bindingGroupId, inputKey)
        val existing = activatorDao.getByGroupInputs(listOf(groupInputId)).firstOrNull { it.type == type }
        val activatorId = existing?.id ?: run {
            val nextOrder = (activatorDao.getByGroupInputs(listOf(groupInputId))
                .maxOfOrNull { it.orderIndex } ?: -1) + 1
            activatorDao.insert(
                Activator(groupInputId = groupInputId, type = type, settingsJson = "{}", orderIndex = nextOrder)
            )
        }
        val bindingId = bindingDao.getByActivators(listOf(activatorId)).firstOrNull()?.id
            ?: bindingDao.insert(
                Binding(activatorId = activatorId, outputType = BindingOutputType.UNBOUND, args = "", orderIndex = 0)
            )
        configDirtyTick.value = configDirtyTick.value + 1
        return bindingId
    }

    /**
     * Add a command of [type] to the row ([bindingGroupId], [inputKey]) and return its binding
     * id — what the advanced view's trailing "+" tile creates before the command picker opens.
     *
     * An input row is a STACK of commands now (Dylan, 2026-09-20), not one slot per press type,
     * so this APPENDS rather than ensuring a single cell: several commands may share a press
     * type, which in the schema means several bindings under one activator. A press type that
     * has no bucket yet gets one.
     *
     * An UNBOUND binding already sitting in that bucket is reused instead of a second being
     * stacked beside it: a cancelled "New" leaves one behind, and two empties would be two
     * invisible commands.
     */
    suspend fun addRowCommand(bindingGroupId: Long, inputKey: String, type: ActivatorType): Long {
        val groupInputId = ensureGroupInputId(bindingGroupId, inputKey)
        val siblings = activatorDao.getByGroupInputs(listOf(groupInputId))
        val activatorId = siblings.firstOrNull { it.type == type }?.id ?: activatorDao.insert(
            Activator(
                groupInputId = groupInputId,
                type = type,
                settingsJson = "{}",
                orderIndex = (siblings.maxOfOrNull { it.orderIndex } ?: -1) + 1,
            ),
        )
        val existing = bindingDao.getByActivators(listOf(activatorId))
        val bindingId = existing.firstOrNull { it.outputType == BindingOutputType.UNBOUND }?.id
            ?: bindingDao.insert(
                Binding(
                    activatorId = activatorId,
                    outputType = BindingOutputType.UNBOUND,
                    args = "",
                    orderIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1,
                ),
            )
        configDirtyTick.value = configDirtyTick.value + 1
        return bindingId
    }

    /**
     * Carry the command [bindingId] onto the row ([toBindingGroupId], [toInputKey]), keeping its
     * own press type.
     *
     * [swapWithBindingId] is the command it landed ON, which goes back the other way; null means
     * it landed on the row's "+" and is simply ADDED there (Dylan, 2026-09-20). Within a group
     * or across them is the same operation — a command only points at its row.
     *
     * When both commands are the only ones in their activator, the two ACTIVATORS exchange rows
     * instead of their bindings being reparented: that keeps each command's activator settings
     * (long-press time, chord partner, turbo) attached to the command they were tuned for, and
     * regenerates no identifier for something that isn't a copy. Otherwise — a bucket holding
     * more than one command — only the binding travels, since its neighbours must stay put.
     */
    suspend fun moveRowCommand(
        bindingId: Long,
        toBindingGroupId: Long,
        toInputKey: String,
        swapWithBindingId: Long? = null,
    ) {
        if (swapWithBindingId == bindingId) return
        val binding = bindingDao.getById(bindingId) ?: return
        val fromActivator = activatorDao.getById(binding.activatorId) ?: return
        val fromInputId = fromActivator.groupInputId
        val fromGroupId = resolveGroupIdForBinding(bindingId)
        val toInputId = ensureGroupInputId(toBindingGroupId, toInputKey)
        val swap = swapWithBindingId?.let { bindingDao.getById(it) }
        val swapActivator = swap?.let { activatorDao.getById(it.activatorId) }
        // Landing on the row it already belongs to changes nothing the user can see: where a
        // command sits in its row is the sort's business, not the move's.
        if (toInputId == fromInputId && (swapActivator == null || swapActivator.groupInputId == fromInputId)) {
            return
        }

        val fromAlone = bindingDao.getByActivators(listOf(fromActivator.id)).size == 1
        val swapAlone = swapActivator != null &&
            bindingDao.getByActivators(listOf(swapActivator.id)).size == 1
        if (swap != null && swapActivator != null && fromAlone && swapAlone) {
            activatorDao.update(fromActivator.copy(groupInputId = toInputId))
            activatorDao.update(swapActivator.copy(groupInputId = fromInputId))
        } else {
            reparentBinding(binding, fromActivator, toInputId, fromActivator.type)
            if (swap != null && swapActivator != null) {
                reparentBinding(swap, swapActivator, fromInputId, swapActivator.type)
            }
        }

        fromGroupId?.let { syncAuxButtonMode(it) }
        if (toBindingGroupId != fromGroupId) syncAuxButtonMode(toBindingGroupId)
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Move one command onto [toGroupInputId], under its bucket for [type].
     *
     * When the command is the ONLY one in its activator and the destination has no bucket of
     * that type, the whole ACTIVATOR travels: its id and its settings (long-press time, chord
     * partner, turbo) stay attached to the command they were tuned for, and nothing is
     * re-created for something that isn't a copy. Otherwise the bare binding moves — its
     * neighbours in the old bucket must stay put — and a bucket created for it inherits the old
     * one's settings so a command split out of a tuned bucket keeps the timings it was firing
     * with. The vacated activator goes once it holds nothing.
     */
    private suspend fun reparentBinding(
        binding: Binding,
        fromActivator: Activator,
        toGroupInputId: Long,
        type: ActivatorType,
    ) {
        val siblings = activatorDao.getByGroupInputs(listOf(toGroupInputId))
        val bucket = siblings.firstOrNull { it.type == type }
        val nextActivatorOrder = (siblings.maxOfOrNull { it.orderIndex } ?: -1) + 1
        if (bucket == null && bindingDao.getByActivators(listOf(fromActivator.id)).size == 1) {
            activatorDao.update(
                fromActivator.copy(groupInputId = toGroupInputId, type = type, orderIndex = nextActivatorOrder),
            )
            return
        }
        val targetId = bucket?.id ?: activatorDao.insert(
            Activator(
                groupInputId = toGroupInputId,
                type = type,
                settingsJson = fromActivator.settingsJson,
                orderIndex = nextActivatorOrder,
            ),
        )
        if (targetId == fromActivator.id) return
        val nextOrder =
            (bindingDao.getByActivators(listOf(targetId)).maxOfOrNull { it.orderIndex } ?: -1) + 1
        bindingDao.update(binding.copy(activatorId = targetId, orderIndex = nextOrder))
        if (bindingDao.getByActivators(listOf(fromActivator.id)).isEmpty()) {
            activatorDao.deleteById(fromActivator.id)
        }
    }

    /** One command on the clipboard: what it fires, what it's called, how it prints, the press
     *  type it fires on, and the settings of the bucket it came from. */
    data class CommandSnapshot(
        val type: ActivatorType,
        val outputType: BindingOutputType,
        val args: String,
        val label: String?,
        val showDeviceIcon: Boolean,
        val showDeviceInitials: Boolean,
        val activatorSettingsJson: String,
    )

    /** Read one command for the clipboard. */
    suspend fun readRowCommand(bindingId: Long): CommandSnapshot? {
        val binding = bindingDao.getById(bindingId) ?: return null
        val activator = activatorDao.getById(binding.activatorId) ?: return null
        return CommandSnapshot(
            type = activator.type,
            outputType = binding.outputType,
            args = binding.args,
            label = binding.label,
            showDeviceIcon = binding.showDeviceIcon,
            showDeviceInitials = binding.showDeviceInitials,
            activatorSettingsJson = activator.settingsJson,
        )
    }

    /**
     * Paste [snapshot] onto the row ([bindingGroupId], [inputKey]) — overwriting [targetBindingId]
     * when the paste was aimed at a command, ADDING when it was aimed at the row's "+".
     *
     * The pasted command keeps the press type it was COPIED with, in both cases: with the press
     * type columns gone there is no destination column to inherit one from, and a copied Long
     * press that pasted as a Regular press would be a surprise.
     */
    suspend fun pasteRowCommand(
        targetBindingId: Long?,
        bindingGroupId: Long,
        inputKey: String,
        snapshot: CommandSnapshot,
    ) {
        val bindingId = targetBindingId
            ?: addRowCommand(bindingGroupId, inputKey, snapshot.type)
        val binding = bindingDao.getById(bindingId) ?: return
        bindingDao.update(
            binding.copy(
                outputType = snapshot.outputType,
                args = snapshot.args,
                label = snapshot.label,
                showDeviceIcon = snapshot.showDeviceIcon,
                showDeviceInitials = snapshot.showDeviceInitials,
            ),
        )
        // Onto an existing command, the press type comes across too — the clipboard carries a
        // whole command, not just its output.
        if (targetBindingId != null) setInputRowPressType(bindingId, snapshot.type)
        activatorDao.getById(bindingDao.getById(bindingId)?.activatorId ?: 0L)?.let {
            activatorDao.update(it.copy(settingsJson = snapshot.activatorSettingsJson))
        }
        syncAuxButtonMode(bindingGroupId)
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /** Snapshot of a cell for the copy/paste clipboard. Carries the activator's settings so a
     *  pasted Long press keeps its tuned threshold, and the first binding's output + label. */
    data class InputCellSnapshot(
        val outputType: BindingOutputType,
        val args: String,
        val label: String?,
        val activatorSettingsJson: String,
    )

/**
     * The basic view group menu's clipboard (2026-09-16): a whole input group's commands and/or
     * its mode + settings. Either half may be absent — "Copy inputs" leaves [mode] and
     * [settingsJson] null, "Copy settings" leaves [rows] null, "Copy both" fills all three.
     *
     * [rows] are POSITIONAL, one entry per row of the copied group in display order, so a paste
     * maps row i onto the target's row i whatever its sub-input keys — face Y/X/B/A lands on
     * D-pad Up/Left/Right/Down, which is also where those buttons sit.
     */
    data class InputGroupSnapshot(
        val rows: List<Map<ActivatorType, InputCellSnapshot>>?,
        val mode: BindingMode?,
        val settingsJson: String?,
    )

    /**
     * Paste [snapshot] into the cell at ([inputKey], [type]), creating it if absent and
     * overwriting whatever was there. The destination keeps its own press type — pasting a
     * copied Long command into a Double column makes it a Double command, which is the whole
     * point of copying across columns.
     */
    suspend fun writeInputCell(
        bindingGroupId: Long,
        inputKey: String,
        type: ActivatorType,
        snapshot: InputCellSnapshot,
    ) {
        val bindingId = ensureInputCell(bindingGroupId, inputKey, type)
        val binding = bindingDao.getById(bindingId) ?: return
        bindingDao.update(
            binding.copy(
                outputType = snapshot.outputType,
                args = snapshot.args,
                label = snapshot.label,
            )
        )
        activatorDao.getById(binding.activatorId)?.let {
            activatorDao.update(it.copy(settingsJson = snapshot.activatorSettingsJson))
        }
        syncAuxButtonMode(bindingGroupId)
        configDirtyTick.value = configDirtyTick.value + 1
    }

    // ── Whole-row / whole-group ops (the basic view's group menu, 2026-09-16) ────────────────

    /** Every bound cell on one row, keyed by press type. Empty when the row has none. Same
     *  snapshot shape, and only the FIRST command of each press type — the group-level copy
     *  predates rows holding stacks (2026-09-20) and still carries one command per type. */
    suspend fun readRowCells(bindingGroupId: Long, inputKey: String): Map<ActivatorType, InputCellSnapshot> {
        val groupInputId = findGroupInputId(bindingGroupId, inputKey) ?: return emptyMap()
        return buildMap {
            activatorDao.getByGroupInputs(listOf(groupInputId)).forEach { activator ->
                val binding = bindingDao.getByActivators(listOf(activator.id)).firstOrNull()
                    ?: return@forEach
                if (binding.outputType == BindingOutputType.UNBOUND) return@forEach
                put(
                    activator.type,
                    InputCellSnapshot(binding.outputType, binding.args, binding.label, activator.settingsJson),
                )
            }
        }
    }

    /**
     * Make one row hold EXACTLY [cells]: every activator currently on it is deleted, then each
     * snapshot is written into its press type. The paste half of the group menu's "Copy inputs"
     * — a replace, not a merge, so the pasted group reads the same as the copied one.
     */
    suspend fun replaceRowCells(
        bindingGroupId: Long,
        inputKey: String,
        cells: Map<ActivatorType, InputCellSnapshot>,
    ) {
        findGroupInputId(bindingGroupId, inputKey)?.let { groupInputId ->
            activatorDao.getByGroupInputs(listOf(groupInputId)).forEach { activator ->
                bindingDao.deleteByActivator(activator.id)
                activatorDao.deleteById(activator.id)
            }
        }
        cells.forEach { (type, snapshot) -> writeInputCell(bindingGroupId, inputKey, type, snapshot) }
        syncAuxButtonMode(bindingGroupId)
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Return a set-owned binding group to exactly what a fresh layout seeds for its source: the
     * seed's mode, empty settings, and the seed's sub-inputs each holding one unbound Press. Every
     * command and setting in the group is discarded.
     *
     * No-op for a layer-owned group or a source with no seed (nothing to reset TO).
     */
    suspend fun resetBindingGroup(bindingGroupId: Long) {
        val group = bindingGroupDao.getById(bindingGroupId) ?: return
        val source = findInputSourceForSetBindingGroup(bindingGroupId) ?: return
        val seed = DEFAULT_INPUT_SOURCE_SEEDS[source] ?: return
        groupInputDao.getByGroups(listOf(bindingGroupId)).forEach { input ->
            activatorDao.getByGroupInputs(listOf(input.id)).forEach { activator ->
                bindingDao.deleteByActivator(activator.id)
                activatorDao.deleteById(activator.id)
            }
            groupInputDao.deleteById(input.id)
        }
        bindingGroupDao.update(
            group.copy(mode = seed.mode, settingsJson = seed.mode.handler().defaultSettingsJson()),
        )
        seedGroupRows(bindingGroupId, seed)
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Auto-manage the passthrough↔intercept mode for the single-button "other" sources (bumpers +
     * Start/Select), which the UI renders as ordinary input rows with no mode control. A source with
     * any bound command intercepts (`SINGLE_BUTTON`, digital remap — no Shizuku); a fully-unbound
     * source passes through (`DEVICE_DEFAULT`). No-op for any other source or mode, so binding
     * other inputs is untouched. Does not bump the dirty tick — the caller does.
     */
    private suspend fun syncAuxButtonMode(bindingGroupId: Long) {
        val group = bindingGroupDao.getById(bindingGroupId) ?: return
        if (group.mode != BindingMode.DEVICE_DEFAULT && group.mode != BindingMode.SINGLE_BUTTON) return
        val source = findInputSourceForSetBindingGroup(bindingGroupId) ?: return
        if (source !in AUX_BUTTON_SOURCES) return
        val inputs = groupInputDao.getByGroups(listOf(bindingGroupId))
        val activators = activatorDao.getByGroupInputs(inputs.map { it.id })
        val bindings = bindingDao.getByActivators(activators.map { it.id })
        val hasBound = bindings.any { it.outputType != BindingOutputType.UNBOUND }
        val desired = if (hasBound) BindingMode.SINGLE_BUTTON else BindingMode.DEVICE_DEFAULT
        if (group.mode != desired) bindingGroupDao.update(group.copy(mode = desired))
    }

    // ── Phase 7 Brick B.5: Source Mode Shifts ────────────────────────────────

    /**
     * Add a new mode shift to [ownerSource] on the action set identified by
     * [actionSetId]. Creates a fresh target [BindingGroup] in
     * [BindingMode.DEVICE_DEFAULT] (configurable by the user via the UI) and
     * the matching [com.mappo.data.model.steam.SourceModeShift] row with no
     * trigger assigned yet (user assigns via the mode-shift settings UI).
     * Returns the new mode shift's id so the UI can scroll to / open its row.
     */
    suspend fun addModeShiftToSet(actionSetId: Long, ownerSource: InputSource): Long {
        val newGroupId = bindingGroupDao.insert(
            BindingGroup(
                actionSetId = actionSetId,
                actionLayerId = null,
                name = "mode_shift_${ownerSource.name.lowercase()}",
                mode = BindingMode.DEVICE_DEFAULT,
                settingsJson = "{}",
            )
        )
        val order = sourceModeShiftDao.nextDisplayOrderForSet(actionSetId, ownerSource)
        val id = sourceModeShiftDao.insert(
            com.mappo.data.model.steam.SourceModeShift(
                actionSetId = actionSetId,
                actionLayerId = null,
                ownerSource = ownerSource,
                bindingGroupId = newGroupId,
                displayOrder = order,
            )
        )
        configDirtyTick.value = configDirtyTick.value + 1
        return id
    }

    /**
     * Add a new mode shift owned by an action layer (only active while that
     * layer is in the stack). Same shape as [addModeShiftToSet].
     */
    suspend fun addModeShiftToLayer(actionLayerId: Long, ownerSource: InputSource): Long {
        val newGroupId = bindingGroupDao.insert(
            BindingGroup(
                actionSetId = null,
                actionLayerId = actionLayerId,
                name = "mode_shift_${ownerSource.name.lowercase()}",
                mode = BindingMode.DEVICE_DEFAULT,
                settingsJson = "{}",
            )
        )
        val order = sourceModeShiftDao.nextDisplayOrderForLayer(actionLayerId, ownerSource)
        val id = sourceModeShiftDao.insert(
            com.mappo.data.model.steam.SourceModeShift(
                actionSetId = null,
                actionLayerId = actionLayerId,
                ownerSource = ownerSource,
                bindingGroupId = newGroupId,
                displayOrder = order,
            )
        )
        configDirtyTick.value = configDirtyTick.value + 1
        return id
    }

    /**
     * Delete a mode shift and its target binding group (cascade-delete via the
     * schema FK). The action set / layer is untouched.
     */
    suspend fun removeModeShift(modeShiftId: Long) {
        val existing = sourceModeShiftDao.getById(modeShiftId) ?: return
        // Schema declares ON DELETE CASCADE from binding_group → source_mode_shift;
        // deleting the group cleans both rows. Doing it this way (vs deleting the
        // mode-shift row directly) also frees the orphaned target group's child
        // bindings, which is the user-expected outcome.
        bindingGroupDao.deleteById(existing.bindingGroupId)
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Assign or clear the physical input that triggers [modeShiftId]. Pass a
     * non-null `(source, subInput)` to assign; pass nulls to clear. The
     * trigger is the input whose press activates the shift and whose release
     * deactivates it.
     */
    suspend fun setModeShiftTrigger(
        modeShiftId: Long,
        triggerSource: InputSource?,
        triggerSubInput: String?,
    ) {
        val existing = sourceModeShiftDao.getById(modeShiftId) ?: return
        if (existing.triggerSource == triggerSource && existing.triggerSubInput == triggerSubInput) return
        sourceModeShiftDao.update(
            existing.copy(triggerSource = triggerSource, triggerSubInput = triggerSubInput)
        )
        configDirtyTick.value = configDirtyTick.value + 1
    }

    /**
     * Phase 7 Brick B.6 — materialize a sub-input row on [modeShiftId]'s target
     * binding group. Mode-shift target groups are created empty by
     * [addModeShiftToSet]/[addModeShiftToLayer]; their per-sub-input rows are
     * created on demand the first time the user taps one in the editor. Same
     * pattern as [materializeLayerOverride] for layer overrides — keeps the
     * shift's group small until the user actually configures bindings.
     *
     * Idempotent: returns the existing row if already materialized. Returns 0L
     * if [modeShiftId] doesn't resolve (e.g. just deleted).
     */
    suspend fun materializeModeShiftInput(
        modeShiftId: Long,
        groupInputKey: String,
    ): Long {
        val shift = sourceModeShiftDao.getById(modeShiftId) ?: return 0L
        val targetGroupId = shift.bindingGroupId

        // Idempotency: if the sub-input already exists on the group, return it.
        groupInputDao.getByGroups(listOf(targetGroupId))
            .firstOrNull { it.inputKey == groupInputKey }
            ?.let { return it.id }

        val nextOrder = groupInputDao.getByGroups(listOf(targetGroupId))
            .maxOfOrNull { it.orderIndex }?.plus(1) ?: 0
        val newInputId = groupInputDao.insert(
            GroupInput(
                bindingGroupId = targetGroupId,
                inputKey = groupInputKey,
                orderIndex = nextOrder,
            )
        )
        val activatorId = activatorDao.insert(
            Activator(
                groupInputId = newInputId,
                type = ActivatorType.FULL_PRESS,
                settingsJson = "{}",
                orderIndex = 0,
            )
        )
        bindingDao.insert(
            Binding(
                activatorId = activatorId,
                outputType = BindingOutputType.UNBOUND,
                args = "",
                orderIndex = 0,
            )
        )
        configDirtyTick.value = configDirtyTick.value + 1
        return newInputId
    }

    private suspend fun loadConfigSnapshot(controllerProfile: ControllerProfile): ControllerConfig {
        val sets = actionSetDao.getByControllerProfile(controllerProfile.id)
        if (sets.isEmpty()) return ControllerConfig(controllerProfile, emptyList())

        val setIds = sets.map { it.id }
        val layersByActionSet = actionLayerDao.getByActionSets(setIds).groupBy { it.parentActionSetId }
        val presetsByActionSet = presetBindingDao.getByActionSets(setIds).groupBy { it.actionSetId }

        val layerIds = layersByActionSet.values.flatten().map { it.id }
        val layerPresetsByLayer = if (layerIds.isNotEmpty()) {
            layerPresetBindingDao.getByActionLayers(layerIds).groupBy { it.actionLayerId }
        } else emptyMap()
        val groupsByActionSet = bindingGroupDao.getByActionSets(setIds).groupBy { it.actionSetId!! }
        val groupsByActionLayer = if (layerIds.isNotEmpty()) {
            bindingGroupDao.getByActionLayers(layerIds).groupBy { it.actionLayerId!! }
        } else emptyMap()

        val allGroups = groupsByActionSet.values.flatten() + groupsByActionLayer.values.flatten()
        val groupsById = allGroups.associateBy { it.id }
        val groupIds = allGroups.map { it.id }
        val inputsByGroup = if (groupIds.isNotEmpty()) {
            groupInputDao.getByGroups(groupIds).groupBy { it.bindingGroupId }
        } else emptyMap()

        val inputIds = inputsByGroup.values.flatten().map { it.id }
        val activatorsByInput = if (inputIds.isNotEmpty()) {
            activatorDao.getByGroupInputs(inputIds).groupBy { it.groupInputId }
        } else emptyMap()

        val activatorIds = activatorsByInput.values.flatten().map { it.id }
        val bindingsByActivator = if (activatorIds.isNotEmpty()) {
            bindingDao.getByActivators(activatorIds).groupBy { it.activatorId }
        } else emptyMap()

        // Phase 7 Brick B.5 — mode shifts (per-source while-held overlays).
        // One pass per owner (set vs. layer); they're queried separately because
        // each row is either set- or layer-owned, never both.
        val modeShiftsByActionSet = sourceModeShiftDao.getByActionSets(setIds)
            .groupBy { it.actionSetId!! }
        val modeShiftsByActionLayer = if (layerIds.isNotEmpty()) {
            sourceModeShiftDao.getByActionLayers(layerIds).groupBy { it.actionLayerId!! }
        } else emptyMap()

        fun buildGroup(group: BindingGroup): BindingGroupGraph {
            val inputs = (inputsByGroup[group.id] ?: emptyList()).map { input ->
                val activators = (activatorsByInput[input.id] ?: emptyList()).map { activator ->
                    ActivatorGraph(activator, bindingsByActivator[activator.id] ?: emptyList())
                }
                GroupInputGraph(input, activators)
            }
            return BindingGroupGraph(group, inputs)
        }

        // Resolve a SourceModeShift to its graph form. Drops rows whose target
        // bindingGroup vanished (defensive against stale FKs in dev).
        fun resolveModeShift(shift: com.mappo.data.model.steam.SourceModeShift): com.mappo.data.model.steam.SourceModeShiftGraph? {
            val targetGroup = groupsById[shift.bindingGroupId] ?: return null
            return com.mappo.data.model.steam.SourceModeShiftGraph(shift, buildGroup(targetGroup))
        }

        val actionSetGraphs = sets.map { actionSet ->
            val layerGraphs = (layersByActionSet[actionSet.id] ?: emptyList()).map { layer ->
                val groupsForLayer = (groupsByActionLayer[layer.id] ?: emptyList()).map(::buildGroup)
                val layerPresetEntries = (layerPresetsByLayer[layer.id] ?: emptyList()).mapNotNull { lpb ->
                    val group = groupsById[lpb.bindingGroupId] ?: return@mapNotNull null
                    PresetEntry(lpb.inputSource, lpb.state, buildGroup(group))
                }
                val layerModeShifts = (modeShiftsByActionLayer[layer.id] ?: emptyList())
                    .mapNotNull(::resolveModeShift)
                ActionLayerGraph(layer, groupsForLayer, layerPresetEntries, layerModeShifts)
            }
            val presetEntries = (presetsByActionSet[actionSet.id] ?: emptyList()).mapNotNull { pb ->
                val group = groupsById[pb.bindingGroupId] ?: return@mapNotNull null
                PresetEntry(pb.inputSource, pb.state, buildGroup(group))
            }
            val setModeShifts = (modeShiftsByActionSet[actionSet.id] ?: emptyList())
                .mapNotNull(::resolveModeShift)
            ActionSetGraph(actionSet, layerGraphs, presetEntries, setModeShifts)
        }

        return ControllerConfig(controllerProfile, actionSetGraphs)
    }

    private data class InputSourceSeed(
        val groupName: String,
        val mode: BindingMode,
        val inputKeys: List<String>,
        /**
         * What each sub-input FIRES on a fresh layout, keyed by sub-input. A key absent here is
         * seeded Unbound — a real row the user can fill, which is what the trigger's analog
         * soft-pull and a stick's outer ring are.
         */
        val defaults: Map<String, BindingOutput> = emptyMap(),
    )

    companion object {
        /**
         * Single-button "other" sources whose intercept mode is auto-managed by
         * [syncAuxButtonMode] (bound → SINGLE_BUTTON, cleared → DEVICE_DEFAULT) so the UI can
         * show them as plain rows with no mode picker of their own.
         */
        private val AUX_BUTTON_SOURCES: Set<InputSource> = setOf(
            InputSource.LEFT_BUMPER,
            InputSource.RIGHT_BUMPER,
            InputSource.SWITCH_START,
            InputSource.SWITCH_SELECT,
        )

        /**
         * **A fresh layout is Mappo-handled, and every input starts mapped to itself**
         * (Dylan, 2026-09-21).
         *
         * It used to seed almost everything to [BindingMode.DEVICE_DEFAULT] with Unbound
         * bindings, and the basic view papered over the emptiness with hardcoded physical names
         * ("A Button", "L-Stick Click") that corresponded to no binding at all — so opening the
         * advanced view on a new layout showed a grid of nothing, and the two views disagreed.
         *
         * That caution had a reason and the reason expired. Mappo used to auto-generate a layout
         * for every application that came to the foreground, so a layout appearing was not a
         * statement of intent and taking over the pad by default would have been presumptuous
         * (and it needs Shizuku). Layouts are now created deliberately, by the user, for an
         * application they have chosen — and "active application with no layout" is a
         * first-class state that means exactly "don't touch this one". A layout that exists is
         * consent.
         *
         * So each source starts in the mode that source is FOR, and each sub-input fires its own
         * hardware equivalent: the pad behaves exactly as it did before, but every key of it is
         * now a real binding sitting in a real mode, visible and editable in both views and
         * ready to be re-pointed at anything.
         */
        private val DEFAULT_INPUT_SOURCE_SEEDS: Map<InputSource, InputSourceSeed> = linkedMapOf(
            InputSource.BUTTON_DIAMOND to InputSourceSeed(
                "face_buttons", BindingMode.BUTTON_PAD,
                listOf("button_a", "button_b", "button_x", "button_y"),
                defaults = mapOf(
                    "button_a" to BindingOutput.XInputButton("BUTTON_A"),
                    "button_b" to BindingOutput.XInputButton("BUTTON_B"),
                    "button_x" to BindingOutput.XInputButton("BUTTON_X"),
                    "button_y" to BindingOutput.XInputButton("BUTTON_Y"),
                ),
            ),
            InputSource.DPAD to InputSourceSeed(
                "dpad", BindingMode.DPAD,
                listOf("dpad_up", "dpad_down", "dpad_left", "dpad_right"),
                defaults = mapOf(
                    "dpad_up" to BindingOutput.XInputButton("DPAD_UP"),
                    "dpad_down" to BindingOutput.XInputButton("DPAD_DOWN"),
                    "dpad_left" to BindingOutput.XInputButton("DPAD_LEFT"),
                    "dpad_right" to BindingOutput.XInputButton("DPAD_RIGHT"),
                ),
            ),
            // Bumpers and switches keep their mode AUTO-MANAGED by [syncAuxButtonMode] (bound →
            // SINGLE_BUTTON, cleared → DEVICE_DEFAULT), which is why they have no mode dropdown
            // of their own. Seeding them bound means they simply start on the bound side of that
            // rule rather than being switched there by the user's first edit.
            InputSource.LEFT_BUMPER to InputSourceSeed(
                "left_bumper", BindingMode.SINGLE_BUTTON, listOf("click"),
                defaults = mapOf("click" to BindingOutput.XInputButton("BUTTON_L1")),
            ),
            InputSource.RIGHT_BUMPER to InputSourceSeed(
                "right_bumper", BindingMode.SINGLE_BUTTON, listOf("click"),
                defaults = mapOf("click" to BindingOutput.XInputButton("BUTTON_R1")),
            ),
            // Triggers start DIGITAL (Dylan): "full_pull" is the hardware threshold every pad
            // reports without help, so the digital mode is the one that works on any install.
            // The analog "soft_pull" row is still seeded — switching to Trigger (Analog) reveals
            // it already there — but it stays unbound, because it needs Shizuku to ever fire.
            InputSource.LEFT_TRIGGER to InputSourceSeed(
                "left_trigger", BindingMode.SINGLE_BUTTON, listOf("full_pull", "soft_pull"),
                defaults = mapOf("full_pull" to BindingOutput.XInputButton("AXIS_L2")),
            ),
            InputSource.RIGHT_TRIGGER to InputSourceSeed(
                "right_trigger", BindingMode.SINGLE_BUTTON, listOf("full_pull", "soft_pull"),
                defaults = mapOf("full_pull" to BindingOutput.XInputButton("AXIS_R2")),
            ),
            // A stick's MOVEMENT is its mode, not a binding — Joystick mode is what makes the
            // stick a stick. Only the click is a bindable sub-input; the outer ring is a real
            // row left empty.
            InputSource.LEFT_JOYSTICK to InputSourceSeed(
                "left_joystick", BindingMode.JOYSTICK_MOVE, listOf("click", "outer_ring"),
                defaults = mapOf("click" to BindingOutput.XInputButton("BUTTON_THUMBL")),
            ),
            InputSource.RIGHT_JOYSTICK to InputSourceSeed(
                "right_joystick", BindingMode.JOYSTICK_MOVE, listOf("click", "outer_ring"),
                defaults = mapOf("click" to BindingOutput.XInputButton("BUTTON_THUMBR")),
            ),
            InputSource.SWITCH_START to InputSourceSeed(
                "switch_start", BindingMode.SINGLE_BUTTON, listOf("click"),
                defaults = mapOf("click" to BindingOutput.XInputButton("BUTTON_START")),
            ),
            InputSource.SWITCH_SELECT to InputSourceSeed(
                "switch_select", BindingMode.SINGLE_BUTTON, listOf("click"),
                defaults = mapOf("click" to BindingOutput.XInputButton("BUTTON_SELECT")),
            ),
            // Gyro: no sub-inputs (gyro modes emit continuous output, not bindable directional
            // rows) and no default mode — a gyro that started steering something would be a
            // genuine surprise, and half the target devices don't have one. The user opts in.
            InputSource.GYRO to InputSourceSeed(
                "gyro", BindingMode.DEVICE_DEFAULT, emptyList(),
            ),
        )
    }
}
