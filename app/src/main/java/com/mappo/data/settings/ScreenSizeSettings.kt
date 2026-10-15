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
 * Whether Mappo's screen canvas (`ScreenFrame`) fills the display or rests as the 1:1 compact
 * preview square. It was a per-session toggle on the frame's bottom bar until the bar retired
 * (2026-10-15); it lives in the wordmark drawer now, and persists. One class per setting, sharing
 * the app's one settings file, like [TileRevealSettings].
 */
@Singleton
class ScreenSizeSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _fullscreen = MutableStateFlow(prefs.getBoolean(KEY_FULLSCREEN, DEFAULT_FULLSCREEN))
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == KEY_FULLSCREEN) _fullscreen.value = sp.getBoolean(KEY_FULLSCREEN, DEFAULT_FULLSCREEN)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setFullscreen(fullscreen: Boolean) {
        prefs.edit().putBoolean(KEY_FULLSCREEN, fullscreen).apply()
    }

    companion object {
        /** Fullscreen by default (2026-10-15, Dylan) — the virtual editor needs the whole display. */
        const val DEFAULT_FULLSCREEN = true

        private const val PREFS_NAME = "mappo_settings"
        private const val KEY_FULLSCREEN = "screen_fullscreen"
    }
}
