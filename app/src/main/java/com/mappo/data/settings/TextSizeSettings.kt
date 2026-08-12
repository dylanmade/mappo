package com.mappo.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level text size, decoupled from the OS font-scale setting. The whole UI is tuned
 * against the OS "Small" scale, so [SMALL] is enforced by default regardless of the
 * device setting; the options are the OS's own size steps, plus [SYSTEM] to opt back
 * into inheriting the device value.
 */
enum class TextSize(val label: String, val scale: Float?) {
    SMALL("Small", 0.85f),
    DEFAULT("Default", 1.00f),
    LARGE("Large", 1.15f),
    LARGEST("Largest", 1.30f),
    /** Inherit the device font scale (null = leave the configuration untouched). */
    SYSTEM("System", null),
}

/**
 * Persistence for [TextSize] plus the context chokepoint that applies it: [wrap] rewrites a
 * context's `fontScale`, and EVERY UI root must pass through it — activities via
 * `attachBaseContext`, overlay windows at ComposeView creation — so that all windows
 * (including dialogs and popups, which derive their configuration from their host context)
 * resolve `sp` identically. [wrap] reads prefs synchronously because `attachBaseContext`
 * runs before Hilt injection.
 */
@Singleton
class TextSizeSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _size = MutableStateFlow(read(prefs))
    val size: StateFlow<TextSize> = _size.asStateFlow()

    fun set(size: TextSize) {
        prefs.edit().putString(KEY_TEXT_SIZE, size.name).apply()
        _size.value = size
    }

    companion object {
        private const val PREFS_NAME = "mappo_settings"
        private const val KEY_TEXT_SIZE = "text_size"

        val DEFAULT = TextSize.SMALL

        fun read(context: Context): TextSize =
            read(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

        private fun read(prefs: SharedPreferences): TextSize {
            val stored = prefs.getString(KEY_TEXT_SIZE, null) ?: return DEFAULT
            return runCatching { TextSize.valueOf(stored) }.getOrDefault(DEFAULT)
        }

        /** The chosen size baked into [base]'s configuration; [base] itself for [TextSize.SYSTEM]. */
        fun wrap(base: Context): Context {
            val scale = read(base).scale ?: return base
            if (base.resources.configuration.fontScale == scale) return base
            val config = Configuration(base.resources.configuration)
            config.fontScale = scale
            return base.createConfigurationContext(config)
        }
    }
}
