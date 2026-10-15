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
 * **Where the editors' hierarchy bar is pinned on the screen** (Dylan, 2026-10-07; four edges
 * since 2026-10-14, was six top/bottom spots) — the middle of one screen edge. Top and bottom lay
 * the bar out as a row; the sides ([sideways]) stack its crumbs in a column down the edge. The
 * bar is a compact floating group rather than a full-width strip, so it can get out of the way of
 * whatever sits under it; the choice is global (the physical and virtual editors share it), not a
 * layout property. START / END follow the layout direction, as the bar's placement does.
 */
enum class BarPin(
    /** The edge in words — "top", "left" … — for the menu option and the button's description. */
    val edge: String,
) {
    TOP("top"),
    START("left"),
    END("right"),
    BOTTOM("bottom");

    /** The pin menu's option. */
    val label: String get() = "Pin to $edge"

    /** Pinned to a side edge — the bar is a vertical stack. */
    val sideways: Boolean get() = this == START || this == END
}

/** Persisted home for [BarPin] — one class per setting in the app's one settings file, like
 *  [TileRevealSettings] — and for which of the bar's crumbs are COLLAPSED to their glyph
 *  (2026-10-14; set from the pin button's menu). Crumbs are stored by name, so this layer stays
 *  ignorant of the UI's crumb enum. */
@Singleton
class BarPinSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _pin = MutableStateFlow(read())
    val pin: StateFlow<BarPin> = _pin.asStateFlow()

    private val _collapsedCrumbs = MutableStateFlow(readCollapsed())
    /** Names of the collapsed crumbs; empty = every crumb expanded (the default). */
    val collapsedCrumbs: StateFlow<Set<String>> = _collapsedCrumbs.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_BAR_PIN -> _pin.value = read()
            KEY_COLLAPSED_CRUMBS -> _collapsedCrumbs.value = readCollapsed()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setPin(pin: BarPin) {
        prefs.edit().putString(KEY_BAR_PIN, pin.name).apply()
    }

    fun setCollapsedCrumbs(names: Set<String>) {
        // A copy: SharedPreferences must not be handed a set the caller may mutate.
        prefs.edit().putStringSet(KEY_COLLAPSED_CRUMBS, names.toSet()).apply()
    }

    private fun readCollapsed(): Set<String> =
        prefs.getStringSet(KEY_COLLAPSED_CRUMBS, null)?.toSet() ?: emptySet()

    /** Unknown or missing falls back to the default rather than throwing. */
    private fun read(): BarPin {
        val stored = prefs.getString(KEY_BAR_PIN, null) ?: return Default
        return BarPin.entries.firstOrNull { it.name == stored } ?: Default
    }

    companion object {
        /** The edge the old top bar held. */
        val Default = BarPin.TOP

        private const val PREFS_NAME = "mappo_settings"
        private const val KEY_BAR_PIN = "bar_pin"
        private const val KEY_COLLAPSED_CRUMBS = "bar_collapsed_crumbs"
    }
}
