package com.mappo.data.repository

import com.mappo.data.db.AppLayoutBindingDao
import com.mappo.data.db.KeyLayoutDao
import com.mappo.data.db.LayoutDao
import com.mappo.data.model.Layout
import com.mappo.data.settings.ActiveApplicationStore
import com.mappo.data.model.toGridLayout
import com.mappo.data.model.toKeyLayout
import com.mappo.data.model.toJson
import com.mappo.data.model.toSnapshot
import com.mappo.data.model.withFreshButtonIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LayoutRepository @Inject constructor(
    private val layoutDao: LayoutDao,
    private val keyLayoutDao: KeyLayoutDao,
    private val keyLayoutRepository: KeyLayoutRepository,
    private val controllerConfigRepository: ControllerConfigRepository,
    private val appLayoutBindingDao: AppLayoutBindingDao,
    private val activeApplicationStore: ActiveApplicationStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeLayout = MutableStateFlow<Layout?>(null)
    val activeLayout: StateFlow<Layout?> = _activeLayout.asStateFlow()

    init {
        scope.launch {
            // Cold-start restore: the active APPLICATION's active layout (2026-08-26 —
            // the seeded "default profile" concept is retired; a fresh install starts
            // with no applications, no layouts, and nothing active).
            val pkg = activeApplicationStore.activeAppPackage.value ?: return@launch
            val bound = appLayoutBindingDao.getForPackageOnce(pkg) ?: return@launch
            val restored = layoutDao.getById(bound.layoutId) ?: return@launch
            if (_activeLayout.value == null) {
                _activeLayout.value = restored
            }
        }
    }

    fun getAllLayouts(): Flow<List<Layout>> = layoutDao.getAll()

    fun setActiveLayout(layout: Layout) {
        _activeLayout.value = layout
    }

    /** Deleting the active layout leaves nothing active — no default-layout fallback. */
    fun clearActiveLayout() {
        _activeLayout.value = null
    }

    suspend fun setActiveLayoutById(id: Long): Layout? {
        val layout = layoutDao.getById(id) ?: return null
        _activeLayout.value = layout
        return layout
    }

    suspend fun addLayout(name: String, packageName: String? = null): Long {
        val newId = layoutDao.insert(Layout(name = name, packageName = packageName))
        keyLayoutRepository.seedDefaults(newId)
        return newId
    }

    suspend fun duplicateLayout(source: Layout, newName: String) {
        // The copy stays in the source's application family and keeps its description;
        // author/likes reset — a duplicate is the device owner's own layout.
        val newId = layoutDao.insert(
            Layout(name = newName, packageName = source.packageName, description = source.description)
        )
        val sourceLayouts = keyLayoutDao.getByLayoutOnce(source.id)
        if (sourceLayouts.isEmpty()) {
            // The Default layout may have been created via the SQL seed callback before any
            // layouts were persisted; fall back to seeding so the duplicate isn't blank.
            keyLayoutRepository.seedDefaults(newId)
        } else {
            sourceLayouts.forEach { layout ->
                // Fresh UUIDs per button so the duplicated layout's keyboards don't share
                // button ids with the source layout's keyboards. The reset-to-original
                // snapshot is regenerated from the fresh-id state to match.
                val fresh = layout.toGridLayout().withFreshButtonIds()
                val snapshotJson = fresh.toSnapshot().toJson()
                keyLayoutDao.insert(
                    fresh.toKeyLayout(
                        layoutId = newId,
                        position = layout.position,
                        originalSnapshotJson = snapshotJson
                    )
                )
            }
        }
        controllerConfigRepository.copyConfig(source.id, newId)
    }

    suspend fun deleteLayout(layout: Layout) {
        layoutDao.delete(layout)
    }
}
