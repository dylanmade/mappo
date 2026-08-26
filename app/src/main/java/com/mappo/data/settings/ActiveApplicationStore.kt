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
 * The ACTIVE APPLICATION — a first-class, persisted pointer (2026-08-26): which
 * application/game Mappo currently serves, independent of the active layout. An
 * application with no layouts can still be active (the controls home then shows the
 * no-layout state with its create/browse routes); the pointer moves on explicit
 * selection in the applications drawer, on manual layout activation (a layout carries
 * its parent application), and on foreground detection while auto-switch is enabled.
 *
 * This replaces deriving "current app" from the active layout's package — the old
 * layout-centric model where applications only existed through their layouts.
 */
@Singleton
class ActiveApplicationStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeAppPackage = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_APP_PACKAGE, null),
    )
    val activeAppPackage: StateFlow<String?> = _activeAppPackage.asStateFlow()

    fun setActiveApplication(packageName: String?) {
        _activeAppPackage.value = packageName
        prefs.edit().putString(KEY_ACTIVE_APP_PACKAGE, packageName).apply()
    }

    companion object {
        private const val PREFS_NAME = "active_application"
        private const val KEY_ACTIVE_APP_PACKAGE = "active_app_package"
    }
}
