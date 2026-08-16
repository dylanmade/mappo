package com.mappo

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.themestudio.core.ThemeStudioProvider
import com.themestudio.persistence.SharedPrefsThemeOverridesStorage
import dagger.hilt.android.AndroidEntryPoint
import com.mappo.data.settings.TextSizeSettings
import com.mappo.ui.screen.MainScreen
import com.mappo.ui.theme.MappoTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Deep-route request from the toolbar overlay (OVERLAY_TOOLBAR_PLAN.md, Brick 2). The
    // overlay launches us with EXTRA_ROUTE naming a NavHost destination; MainScreen navigates
    // there off the nonce. The nonce (not the route string) keys the navigation so re-tapping
    // the same destination after backing out re-navigates — a plain String wouldn't re-fire.
    private var pendingRoute by mutableStateOf<String?>(null)
    private var routeNonce by mutableStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        // App-level text size: the whole UI is tuned against the OS "Small" font scale, so
        // the app enforces its own scale instead of inheriting the device setting. Applied
        // at the context so every window this activity spawns (dialogs, popups) agrees.
        super.attachBaseContext(TextSizeSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeRouteExtra(intent)
        // A normal opaque app window (2026-08-16 — the drawer-over-the-game concept is
        // retired; only the run-mode overlay windows and OverlayEditActivity render over the
        // game, and only the latter is immersive). The system bars stay visible with their
        // own layout space: enableEdgeToEdge un-fits the decor, so the compose root paints
        // the whole window (bar strips included) and pads content by the systemBars insets —
        // deliberately NOT safeDrawing, which includes the IME and would break the
        // keyboard-overlay policy. Transparent bar styles let our fill show through the bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            val themeStorage = remember { SharedPrefsThemeOverridesStorage(applicationContext) }
            ThemeStudioProvider(storage = themeStorage) {
                MappoTheme {
                    // surfaceContainerLowest — the app's background plane, painted across the
                    // FULL window so the system-bar strips wear it too (theme-aware, unlike
                    // the theme's static windowBackground).
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        // The system bars' reserved layout space: everything lays out between
                        // them so bar content never occludes app content.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                        ) {
                            MainScreen(deepLinkRoute = pendingRoute, deepLinkNonce = routeNonce)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a re-launch (e.g. the overlay firing a new deep-route intent while we're
        // already alive) arrives here, not onCreate. Re-point getIntent() and consume the route.
        setIntent(intent)
        consumeRouteExtra(intent)
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
    }

    override fun onPause() {
        super.onPause()
        inForeground = false
    }

    private fun consumeRouteExtra(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return
        pendingRoute = route
        routeNonce++
    }

    companion object {
        /** Intent extra (a [com.mappo.ui.nav.MappoRoute] string) launching us straight to a deep screen. */
        const val EXTRA_ROUTE = "com.mappo.extra.ROUTE"

        /**
         * True while this activity is resumed. The accessibility service's Select+A home chord
         * reads it to decide between revealing the home (launching the activity) and toggling
         * the frame in place (emitting on [homeToggleRequests]).
         */
        @Volatile
        var inForeground: Boolean = false
            private set

        private val homeToggleFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Chord-driven requests to toggle the home frame while foreground; MainScreen collects. */
        val homeToggleRequests: SharedFlow<Unit> = homeToggleFlow.asSharedFlow()

        fun requestHomeToggle() {
            homeToggleFlow.tryEmit(Unit)
        }
    }
}
