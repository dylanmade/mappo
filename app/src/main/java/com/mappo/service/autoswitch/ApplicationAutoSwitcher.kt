package com.mappo.service.autoswitch

import android.util.Log
import com.mappo.data.repository.AppLayoutBindingRepository
import com.mappo.data.repository.LayoutRepository
import com.mappo.data.settings.ActiveApplicationStore
import com.mappo.data.settings.AutoSwitchSettings
import com.mappo.di.ApplicationScope
import com.mappo.service.foreground.ForegroundAppFilter
import com.mappo.service.foreground.ForegroundAppMonitor
import com.mappo.service.input.InputDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens to foreground-app changes and auto-switches the active layout when a binding
 * exists. An unbound app is simply skipped (2026-08-26: the create-layout PROMPT flow —
 * banner, overlay prompt, throttle, ignore-list gate — is retired, and NOTHING is
 * auto-created either: an application without layouts is a first-class state, served by
 * the controls screen's no-layout view).
 */
@Singleton
class ApplicationAutoSwitcher @Inject constructor(
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val bindingRepo: AppLayoutBindingRepository,
    private val layoutRepo: LayoutRepository,
    private val settings: AutoSwitchSettings,
    private val activeApplicationStore: ActiveApplicationStore,
    private val filter: ForegroundAppFilter,
    private val inputDispatcher: InputDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {

    sealed class UiEvent {
        data class Switched(val pkg: String, val appLabel: String, val layoutName: String) : UiEvent()
    }

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    @Volatile private var startJob: Job? = null

    fun start() {
        if (startJob != null) return
        startJob = scope.launch {
            foregroundAppMonitor.currentPackage
                .filterNotNull()
                .distinctUntilChanged()
                .collect { pkg -> handleForegroundChange(pkg) }
        }
        Log.i(TAG, "ApplicationAutoSwitcher started")
    }

    internal suspend fun handleForegroundChange(pkg: String) {
        if (!settings.autoSwitchEnabled.value) return
        if (!filter.isInteresting(pkg)) return
        if (pkg in settings.ignoredPackages.value) {
            // The detection blocklist (seeded launchers): never the active application.
            Log.d(TAG, "$pkg is blocklisted; ignoring")
            return
        }

        // Detection moves the ACTIVE APPLICATION pointer regardless of layouts
        // (2026-08-26): shortcut-opening Mappo over a fresh game must land on that
        // game's context — its no-layout state when nothing exists yet.
        activeApplicationStore.setActiveApplication(pkg)

        val binding = bindingRepo.getForPackageOnce(pkg)
        if (binding != null) {
            val current = layoutRepo.activeLayout.value
            if (current?.id == binding.layoutId) {
                Log.d(TAG, "binding for $pkg already matches active layout; no switch")
                return
            }
            val switched = layoutRepo.setActiveLayoutById(binding.layoutId)
            if (switched != null) {
                Log.i(TAG, "auto-switched layout to '${switched.name}' for $pkg")
                _events.tryEmit(UiEvent.Switched(pkg, filter.appLabel(pkg), switched.name))
            } else {
                Log.w(TAG, "binding for $pkg references missing layout id=${binding.layoutId}")
            }
            return
        }

        // No binding → nothing to switch to; leave the active layout alone. (No prompt,
        // no auto-create — an app without layouts is a first-class state.)
        Log.d(TAG, "no binding for $pkg; skipping")
    }

    /**
     * Force a re-check against the foreground package, bypassing `distinctUntilChanged`.
     * Called from the activity on `ON_RESUME` so opening Mappo while a bound app is already
     * running on another display switches the layout, even though no fresh
     * `WINDOW_STATE_CHANGED` arrives. Prefers the live primary-display query (handles the
     * dual-screen case where Mappo on the bottom screen never causes the top screen's
     * active window to change), falls back to the cached package.
     */
    fun reevaluate() {
        val pkg = inputDispatcher.queryPrimaryDisplayForegroundPackage()
            ?: foregroundAppMonitor.currentPackage.value
        if (pkg.isNullOrBlank()) {
            Log.d(TAG, "reevaluate skipped: no foreground package found")
            return
        }
        Log.d(TAG, "reevaluate firing for $pkg")
        scope.launch { handleForegroundChange(pkg) }
    }

    companion object {
        private const val TAG = "ApplicationAutoSwitcher"
    }
}
