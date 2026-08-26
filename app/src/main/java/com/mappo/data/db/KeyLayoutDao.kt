package com.mappo.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mappo.data.model.KeyLayout
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyLayoutDao {

    @Query("SELECT * FROM key_layouts WHERE layoutId = :layoutId ORDER BY position ASC, id ASC")
    fun getByLayout(layoutId: Long): Flow<List<KeyLayout>>

    @Query("SELECT * FROM key_layouts WHERE layoutId = :layoutId ORDER BY position ASC, id ASC")
    suspend fun getByLayoutOnce(layoutId: Long): List<KeyLayout>

    @Query("SELECT * FROM key_layouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KeyLayout?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(layout: KeyLayout): Long

    @Update
    suspend fun update(layout: KeyLayout)

    @Update
    suspend fun updateAll(layouts: List<KeyLayout>)

    @Delete
    suspend fun delete(layout: KeyLayout)

    @Query("DELETE FROM key_layouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Apply a new (id → position) mapping atomically. Positions never overlap mid-update because
     * the entire pass runs in one transaction.
     */
    @Transaction
    suspend fun reorder(layoutId: Long, idToPosition: Map<Long, Int>) {
        val current = getByLayoutOnce(layoutId)
        val updated = current.mapNotNull { row ->
            val newPos = idToPosition[row.id] ?: return@mapNotNull null
            if (row.position == newPos) null else row.copy(position = newPos)
        }
        if (updated.isNotEmpty()) updateAll(updated)
    }
}
