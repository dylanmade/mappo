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
 * **How much of the controls view turns into tiles when a group is opened** (Dylan, 2026-09-27,
 * as an experiment on top of the edit-mode experiment).
 *
 * Edit mode itself is view-wide either way — one move state, one cursor, and a command can be
 * carried from any group to any other. What this settles is only how many groups SHOW their
 * tiles at once: every one of them, or just the one being worked on. The question behind it is
 * whether seven groups' worth of tiles arriving together is more than the eye wants ("I'd like
 * to see if it feels a little less overwhelming"), and the honest way to find out is to be able
 * to flip between the two on the device.
 */
enum class TileReveal(val label: String) {
    /**
     * Only the group the cursor is in. Navigating into another group reveals that one and
     * collapses the one left behind; lifting a tile reveals them all for as long as it is in
     * flight, since a carried command has to be able to see where it can land.
     *
     * The default while the experiment runs.
     */
    FOCUSED_GROUP("Selected group"),

    /** Every group at once — the behaviour edit mode was born with (2026-09-22). */
    ALL_GROUPS("Every group"),
}

/**
 * Persisted home for [TileReveal]. One class per setting, sharing the app's one settings file,
 * exactly like [MoveSettings] next door — and here rather than in the remap UI so the setting
 * outlives whatever menu happens to be showing it (today the wordmark drawer).
 */
@Singleton
class TileRevealSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _reveal = MutableStateFlow(read())
    val reveal: StateFlow<TileReveal> = _reveal.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_TILE_REVEAL) _reveal.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setReveal(reveal: TileReveal) {
        prefs.edit().putString(KEY_TILE_REVEAL, reveal.name).apply()
    }

    /** Unknown or missing falls back to the default rather than throwing — a stored name can
     *  outlive its enum constant across versions. */
    private fun read(): TileReveal {
        val stored = prefs.getString(KEY_TILE_REVEAL, null) ?: return Default
        return TileReveal.entries.firstOrNull { it.name == stored } ?: Default
    }

    companion object {
        /** The experiment's own answer, so it is what the device shows without being asked for. */
        val Default = TileReveal.FOCUSED_GROUP

        private const val PREFS_NAME = "mappo_settings"
        private const val KEY_TILE_REVEAL = "tile_reveal"
    }
}
