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
 * **How far opening an input group must pan the view, counted in TILES** (Dylan, 2026-09-27).
 *
 * Opening a group frames that group — and a group already fully on screen needs no framing at
 * all, so the ones with a single assigned command and its trailing "+" moved the camera not one
 * pixel: "those groups don't shift the camera at all when activated (again on a 4:3 screen or
 * larger)", which reads as the view not having responded. This is the floor under that: the
 * region brought into view is at least this many tiles wide, measured outward from the group's
 * own input glyph, whether or not the group HAS that many.
 *
 * So it is a minimum, never a cap: a group wider than this is still framed whole.
 */
enum class MinVisibleTiles(val label: String, private val tiles: Int?) {
    /** The screen decides — see [countFor]. */
    AUTOMATIC("Automatic", null),

    /** No floor at all: a group is framed only as far as it has to be, which on the layouts
     *  this was reported against is no pan whatsoever. */
    NONE("No minimum", 0),
    ONE("1 tile", 1),
    TWO("2 tiles", 2),
    THREE("3 tiles", 3),
    FOUR("4 tiles", 4),
    ;

    /**
     * **Automatic is 3 tiles, and 2 on a square screen** — Dylan's own numbers off the device.
     *
     * A default layout's group is two tiles wide (one command, one "+"), so 3 pans it by an extra
     * tile's width and 2 leaves it exactly where the plain "frame the group" rule already put it.
     * A 1:1 window has that much less width to spend, and there the plain rule already feels
     * right, so the floor there is the one that changes nothing.
     */
    fun countFor(squareScreen: Boolean): Int = tiles ?: if (squareScreen) 2 else 3
}

/**
 * Persisted home for [MinVisibleTiles] — one class per setting over the app's one settings file,
 * exactly like [TileRevealSettings] and [MoveSettings] next door.
 */
@Singleton
class MinVisibleTilesSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _minVisibleTiles = MutableStateFlow(read())
    val minVisibleTiles: StateFlow<MinVisibleTiles> = _minVisibleTiles.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_MIN_VISIBLE_TILES) _minVisibleTiles.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setMinVisibleTiles(value: MinVisibleTiles) {
        prefs.edit().putString(KEY_MIN_VISIBLE_TILES, value.name).apply()
    }

    /** Unknown or missing falls back to the default rather than throwing — a stored name can
     *  outlive its enum constant across versions. */
    private fun read(): MinVisibleTiles {
        val stored = prefs.getString(KEY_MIN_VISIBLE_TILES, null) ?: return Default
        return MinVisibleTiles.entries.firstOrNull { it.name == stored } ?: Default
    }

    companion object {
        val Default = MinVisibleTiles.AUTOMATIC

        private const val PREFS_NAME = "mappo_settings"
        private const val KEY_MIN_VISIBLE_TILES = "min_visible_tiles"
    }
}
