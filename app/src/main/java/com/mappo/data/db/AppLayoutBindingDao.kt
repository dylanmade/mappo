package com.mappo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mappo.data.model.AppLayoutBinding
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLayoutBindingDao {

    @Query("SELECT * FROM app_layout_bindings ORDER BY packageName ASC")
    fun getAll(): Flow<List<AppLayoutBinding>>

    @Query("SELECT * FROM app_layout_bindings WHERE packageName = :packageName LIMIT 1")
    suspend fun getForPackageOnce(packageName: String): AppLayoutBinding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: AppLayoutBinding)

    @Query("DELETE FROM app_layout_bindings WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
