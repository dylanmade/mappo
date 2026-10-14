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
 * **Where the editors' hierarchy bar is pinned on the screen** (Dylan, 2026-10-07) — one of six
 * spots: the top or bottom edge, at its start, middle or end. The bar is a compact floating group
 * rather than a full-width strip, so it can get out of the way of whatever sits under it; the
 * choice is global (the physical and virtual editors share it), not a layout property.
 */
enum class BarPin(val label: String, val top: Boolean, val horizontal: Horizontal) {
    TOP_START("Top left", top = true, Horizontal.START),
    TOP_CENTER("Top middle", top = true, Horizontal.CENTER),
    TOP_END("Top right", top = true, Horizontal.END),
    BOTTOM_START("Bottom left", top = false, Horizontal.START),
    BOTTOM_CENTER("Bottom middle", top = false, Horizontal.CENTER),
    BOTTOM_END("Bottom right", top = false, Horizontal.END);

    enum class Horizontal { START, CENTER, END }
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
        /** The corner the old top bar's identity widget held — the hierarchy reads from there. */
        val Default = BarPin.TOP_START

        private const val PREFS_NAME = "mappo_settings"
        private const val KEY_BAR_PIN = "bar_pin"
        private const val KEY_COLLAPSED_CRUMBS = "bar_collapsed_crumbs"
    }
}
