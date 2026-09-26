package com.mappo.data.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **How a controller move is confirmed** (Dylan, 2026-09-26).
 *
 * A tile can be picked up three ways — hold the activate button until it lifts and keep holding;
 * hold until it lifts and let go; or start steering before the hold has even ripened, which lifts
 * it under you. Those are three ways IN, and they used to imply different ways out, which meant
 * the same button meant different things depending on how you had got there. The way in no longer
 * decides: this does, once, for all of them.
 */
enum class MoveCommitGesture(val label: String) {
    /**
     * Let go of the activate button and the tile lands. The default, because it is what the
     * touchscreen already does — lift a finger and the tile is placed — so the two input methods
     * agree.
     *
     * EVERY release places, including one straight back onto the tile's own slot: that puts it
     * down where it started and ends the move. Picking a tile up to carry it around while you
     * look is what the other setting is for.
     */
    ON_RELEASE("Release to place"),

    /** Press the activate button again to land the tile. Letting go of the press that lifted it
     *  does nothing, so the carry survives an unlimited amount of looking around. */
    ON_PRESS("Press again to place"),
}

/**
 * Persisted home for [MoveCommitGesture]. Shares the app's one settings file, like its
 * neighbours; lives here rather than in the remap UI so the setting outlives whatever menu
 * happens to be showing it (today the wordmark drawer, until a real options screen exists).
 */
@Singleton
class MoveSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _commitGesture = MutableStateFlow(read())
    val commitGesture: StateFlow<MoveCommitGesture> = _commitGesture.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_COMMIT_GESTURE) _commitGesture.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setCommitGesture(gesture: MoveCommitGesture) {
        prefs.edit().putString(KEY_COMMIT_GESTURE, gesture.name).apply()
    }

    /** Unknown or missing falls back to the default rather than throwing — a stored name can
     *  outlive its enum constant across versions. */
    private fun read(): MoveCommitGesture {
        val stored = prefs.getString(KEY_COMMIT_GESTURE, null) ?: return Default
        return MoveCommitGesture.entries.firstOrNull { it.name == stored } ?: Default
    }

    companion object {
        /** The touchscreen's own behaviour, so the two input methods agree. */
        val Default = MoveCommitGesture.ON_RELEASE

        private const val PREFS_NAME = "mappo_settings"
        private const val KEY_COMMIT_GESTURE = "move_commit_gesture"
    }
}
