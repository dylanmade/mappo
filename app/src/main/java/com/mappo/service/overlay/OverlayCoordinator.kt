package com.mappo.service.overlay

import android.util.Log
import com.mappo.service.autoswitch.ApplicationAutoSwitcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes [ApplicationAutoSwitcher] events to the system-overlay surface so prompts and
 * confirmations appear on whichever screen the foreground app is on. No-ops if
 * overlay permission is missing — MainScreen's in-app snackbar listens to the same
 * event stream as a defensive fallback.
 */
@Singleton
class OverlayCoordinator @Inject constructor(
    private val autoSwitcher: ApplicationAutoSwitcher,
    private val overlayManager: OverlayManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var startJob: Job? = null

    fun start() {
        if (startJob != null) return
        startJob = scope.launch {
            autoSwitcher.events.collect { event ->
                if (!overlayManager.canShow()) {
                    Log.d(TAG, "skip event ${event::class.simpleName}: overlay permission denied")
                    return@collect
                }
                when (event) {
                    is ApplicationAutoSwitcher.UiEvent.Switched -> {
                        overlayManager.showToast("Loaded layout “${event.layoutName}” for ${event.appLabel}")
                    }
                }
            }
        }
        Log.i(TAG, "OverlayCoordinator started")
    }

    companion object {
        private const val TAG = "OverlayCoordinator"
    }
}
