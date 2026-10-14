package com.mappo.data.model.steam

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A mutually-exclusive grouping of bindings within a [ControllerProfile] — the UI's "button set".
 *
 * **Two kinds** (Dylan, 2026-10-12 — a deliberate step off Steam Input's model): a layout's
 * buttons are either PHYSICAL (the controller's inputs, remapped — what Steam calls an action
 * set) or VIRTUAL (the overlay's free-positioned on-screen buttons), and each kind has its OWN
 * sets. The hierarchy reads Application › Layout › Buttons › Button set, and at any moment one
 * set of EACH kind is active, independently — so one input can switch only the physical set, only
 * the virtual one, or (two bindings on the same press) both. Steam has no virtual buttons, so a
 * Steam config maps onto the physical sets alone.
 *
 * A PHYSICAL set carries the binding graph (preset groups, layers, mode shifts); switching one
 * (via a `CHANGE_PRESET` binding — Phase 4) clears all active layers. A VIRTUAL set carries no
 * bindings of its own — it owns [com.mappo.data.model.OverlayElement]s (directly, or through its
 * layers). Ordering ([orderIndex]) is per kind: each kind's first set is its starting set.
 */
@Entity(
    tableName = "action_set",
    foreignKeys = [
        ForeignKey(
            entity = ControllerProfile::class,
            parentColumns = ["id"],
            childColumns = ["controllerProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("controllerProfileId")],
)
data class ActionSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controllerProfileId: Long,
    val name: String,
    val title: String,
    val legacy: Boolean = true,
    val orderIndex: Int = 0,
    val kind: ActionSetKind = ActionSetKind.PHYSICAL,
)

/** Which buttons an [ActionSet] holds — see the class doc. */
enum class ActionSetKind {
    /** The controller's own inputs, remapped. */
    PHYSICAL,

    /** The overlay's on-screen buttons. */
    VIRTUAL,
}
