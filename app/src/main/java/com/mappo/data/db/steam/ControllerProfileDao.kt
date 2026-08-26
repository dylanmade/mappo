package com.mappo.data.db.steam

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mappo.data.model.steam.ControllerProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ControllerProfileDao {

    @Query("SELECT * FROM controller_profile WHERE layoutId = :layoutId")
    fun observeByProfile(layoutId: Long): Flow<List<ControllerProfile>>

    @Query("SELECT * FROM controller_profile WHERE layoutId = :layoutId")
    suspend fun getByLayout(layoutId: Long): List<ControllerProfile>

    @Query("SELECT * FROM controller_profile WHERE id = :id")
    suspend fun getById(id: Long): ControllerProfile?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(layout: ControllerProfile): Long

    @Update
    suspend fun update(layout: ControllerProfile)

    @Query("DELETE FROM controller_profile WHERE id = :id")
    suspend fun deleteById(id: Long)
}
