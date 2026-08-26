package com.mappo.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mappo.data.model.Layout
import kotlinx.coroutines.flow.Flow

@Dao
interface LayoutDao {

    @Query("SELECT * FROM layouts ORDER BY name ASC")
    fun getAll(): Flow<List<Layout>>

    @Query("SELECT * FROM layouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Layout?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(layout: Layout): Long

    @Delete
    suspend fun delete(layout: Layout)
}
