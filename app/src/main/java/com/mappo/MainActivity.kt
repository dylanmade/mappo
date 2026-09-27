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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import android.view.MotionEvent
import com.mappo.ui.component.LocalRightStick
import com.mappo.ui.component.LocalStickScrollArbiter
import com.mappo.ui.component.StickScrollArbiter
import com.mappo.ui.component.rightStickFrom
import com.themestudio.core.ThemeStudioProvider
import com.themestudio.persistence.SharedPrefsThemeOverridesStorage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.mappo.data.settings.TextSizeSettings
import com.mappo.ui.screen.MainScreen
import com.mappo.ui.theme.MappoTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var inputDispatcher: com.mappo.service.input.InputDispatcher


    // Deep-route request from the toolbar overlay (OVERLAY_TOOLBAR_PLAN.md, Brick 2). The
    // overlay launches us with EXTRA_ROUTE naming a NavHost destination; MainScreen navigates
    // there off the nonce. The nonce (not the route string) keys the navigation so re-tapping
    // the same destination after backing out re-navigates — a plain String wouldn't re-fire.
    private var pendingRoute by mutableStateOf<String?>(null)
    private var routeNonce by mutableStateOf(0)

    // The right stick's live deflection, for UI that scrolls from it (see LocalRightStick).
    // The window is the only place joystick axes are observable — they arrive as generic
    // motion events, which Compose's pointer pipeline never sees.
    private val rightStick = mutableStateOf(Offset.Zero)

    // Which scroller that stick means, arbitrated across this window's whole tree.
    private val stickScroll = StickScrollArbiter()

    override fun attachBaseContext(newBase: Context) {
        // App-level text size: the whole UI is tuned against the OS "Small" font scale, so
        // the app enforces its own scale instead of inheriting the device setting. Applied
        // at the context so every window this activity spawns (dialogs, popups) agrees.
        super.attachBaseContext(TextSizeSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeRouteExtra(intent)
        // **IMMERSIVE (Dylan, 2026-09-26).** Mappo takes the whole display again: no status
        // bar, no navigation bar, and the views lay out edge to edge with no insets reserved
        // for either. The screen is a device's own front panel — chrome of ours at the top and
        // bottom of it, not the system's — and on a handheld running a game underneath, the
        // notification strip was never ours to give away a bar's height to.
        //
        // This reverses 2026-08-16, which dropped immersive along with the drawer-over-the-game
        // concept. Those two were only ever bundled: what was retired that day was rendering
        // OVER the game (translucency, the frozen backdrop, the true over-the-game drawer), and
        // NONE of that comes back here. The window stays opaque `Theme.Mappo`; only the bars
        // are gone. `Theme.Mappo.Translucent` still serves OverlayEditActivity alone.
        //
        // Bars return transiently on a swipe, so nothing becomes unreachable, and re-hide when
        // the window takes focus again (see onWindowFocusChanged) — returning from another app
        // or dismissing a dialog otherwise leaves them up for good.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hideSystemBars()

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
                        // NO inset padding: the window is immersive, so the whole display IS
                        // the app's. (It used to pad by `WindowInsets.systemBars` to keep
                        // content clear of the bars — deliberately not `safeDrawing`, which
                        // includes the IME and would have broken the keyboard-overlay policy.
                        // Should a bar ever need reserving again, that is the inset to use.)
                        //
                        // The stick, and the arbiter that decides which scroller it means.
                        // Scoped to this window: an overlay composes its own tree and must
                        // not be weighed against the activity's scrollers.
                        CompositionLocalProvider(
                            LocalRightStick provides rightStick,
                            LocalStickScrollArbiter provides stickScroll,
                        ) {
                            MainScreen(deepLinkRoute = pendingRoute, deepLinkNonce = routeNonce)
                        }
                    }
                }
            }
        }
    }

    /**
     * Observe the right stick on its way through the window, without consuming it: this is
     * `dispatch`, not `on…`, so the reading lands whether or not a view claims the event, and
     * the return value is untouched so nothing about existing input handling changes.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        rightStickFrom(event)?.let { if (it != rightStick.value) rightStick.value = it }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a re-launch (e.g. the overlay firing a new deep-route intent while we're
        // already alive) arrives here, not onCreate. Re-point getIntent() and consume the route.
        setIntent(intent)
        consumeRouteExtra(intent)
    }

    /** Transiently-revealed bars go away again as soon as the window is ours (see onCreate). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        // Mappo's own UI runs on the device's own controls while it is in front — see
        // InputDispatcher.mappoInForeground. Published here rather than read off [inForeground]
        // so the service side has one gate to consult instead of a static reach-around.
        inputDispatcher.setMappoInForeground(true)
    }

    override fun onPause() {
        super.onPause()
        inForeground = false
        inputDispatcher.setMappoInForeground(false)
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
