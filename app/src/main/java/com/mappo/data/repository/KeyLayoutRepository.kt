package com.mappo.data.repository

import com.mappo.data.db.KeyLayoutDao
import com.mappo.data.defaults.DefaultLayouts
import com.mappo.data.model.KeyLayout
import com.mappo.data.model.toKeyLayout
import com.mappo.data.model.toJson
import com.mappo.data.model.toSnapshot
import com.mappo.data.model.withFreshButtonIds
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyLayoutRepository @Inject constructor(private val dao: KeyLayoutDao) {

    fun getKeyLayoutsByLayout(layoutId: Long): Flow<List<KeyLayout>> = dao.getByLayout(layoutId)

    suspend fun getKeyLayoutsByLayoutOnce(layoutId: Long): List<KeyLayout> =
        dao.getByLayoutOnce(layoutId)

    suspend fun getById(id: Long): KeyLayout? = dao.getById(id)

    suspend fun saveLayout(layout: KeyLayout): Long = dao.insert(layout)

    suspend fun updateLayout(layout: KeyLayout) = dao.update(layout)

    suspend fun deleteLayout(layout: KeyLayout) = dao.delete(layout)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun reorder(layoutId: Long, idToPosition: Map<Long, Int>) =
        dao.reorder(layoutId, idToPosition)

    /**
     * Idempotent: only seeds when the layout has no persisted layouts. Used on first observation
     * of a layout created via the SQL seed callback (which doesn't insert default layouts).
     */
    suspend fun seedDefaultsIfEmpty(layoutId: Long) {
        if (dao.getByLayoutOnce(layoutId).isEmpty()) seedDefaults(layoutId)
    }

    /**
     * Insert the built-in default layouts for [layoutId] with stable positions and a populated
     * originalSnapshotJson so a future Reset can revert to the as-seeded state.
     */
    suspend fun seedDefaults(layoutId: Long) {
        DefaultLayouts.all.forEachIndexed { index, layout ->
            // DefaultLayouts.all holds singleton GridLayouts whose buttons get the same
            // UUIDs at app start; seeding from them as-is means every layout shares button
            // ids with every other layout and with the in-memory templates. Regenerate
            // per-layout so each seed is independent.
            val fresh = layout.withFreshButtonIds()
            val snapshotJson = fresh.toSnapshot().toJson()
            dao.insert(
                fresh.toKeyLayout(
                    layoutId = layoutId,
                    position = index,
                    originalSnapshotJson = snapshotJson
                )
            )
        }
    }
}
