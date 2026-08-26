package com.mappo.data.model.steam

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mappo.data.model.Layout

/**
 * A controller-specific binding configuration belonging to a [Layout].
 * One [Layout] can hold multiple [ControllerProfile]s (e.g., separate configs
 * for the device's built-in pad and an attached Xbox controller). Per-layout
 * "active" controller is tracked separately at runtime (Phase 1 ships one per layout).
 *
 * `legacySet=true` (the common case) means action sets contain concrete output bindings.
 * `legacySet=false` means action sets reference an action manifest; Mappo defers
 * action-manifest hosting (see project_steam_input_features_we_must_reimplement.md).
 */
@Entity(
    tableName = "controller_profile",
    foreignKeys = [
        ForeignKey(
            entity = Layout::class,
            parentColumns = ["id"],
            childColumns = ["layoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("layoutId")],
)
data class ControllerProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val layoutId: Long,
    val controllerType: ControllerType,
    val name: String,
    val legacySet: Boolean = true,
)
