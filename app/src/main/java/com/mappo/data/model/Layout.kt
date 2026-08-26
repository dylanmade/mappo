package com.mappo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A layout (UI term; the code keeps the Layout names) — a full remap + overlay
 * configuration.
 *
 * 2026-08-21 model adjustment for the layouts drawer: a layout belongs to ONE parent
 * application ([packageName]; null = unassigned, listed under every app context as a
 * fallback), and an application may own MANY layouts. Which of them is the app's
 * *default* is NOT stored here — the [AppLayoutBinding] row for the package is the
 * default pointer (it doubles as the auto-switch target: with auto detection on, the
 * bound layout activates when its app foregrounds).
 *
 * [description]/[author]/[likeCount] feed the drawer's layout cards. Local layouts have
 * an empty author (rendered as "You") and zero likes until community sharing lands.
 */
@Entity(tableName = "layouts")
data class Layout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String? = null,
    val description: String = "",
    val author: String = "",
    val likeCount: Int = 0,
)
