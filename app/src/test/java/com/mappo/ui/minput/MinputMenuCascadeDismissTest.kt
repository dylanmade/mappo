package com.mappo.ui.minput

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Closing a cascade is the same at every depth** (2026-10-16). A choice — a verb, an option, or
 * an outside tap that misses the parent — closes EVERY menu up to the root, however deep it was
 * made; stepping out (a tap on the parent) closes one level. Each level used to call its parent's
 * step-out instead of passing the close up, so from three deep the root stayed open.
 *
 * Three levels here — one more than any menu in the app has — so the rule is tested as a rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MinputMenuCascadeDismissTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var rootOpen by mutableStateOf(false)
    private var picked: String? = null
    private var verbs = 0

    private fun setCascade() {
        composeRule.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(top = 300.dp, start = 100.dp).size(40.dp)) {
                        MinputButton(leadingIcon = Lucide.Menu, contentDescription = "root", onClick = { rootOpen = true })
                        MinputActionMenu(
                            expanded = rootOpen,
                            onDismissRequest = { rootOpen = false },
                            actions = listOf(
                                MinputAction(label = "Level two", icon = Lucide.Menu, submenu = { two ->
                                    MinputActionMenu(
                                        expanded = two.expanded,
                                        onDismissRequest = two.onDismissRequest,
                                        actions = listOf(
                                            MinputAction(label = "Level three", icon = Lucide.Menu, submenu = { three ->
                                                MinputDropdownMenu(
                                                    expanded = three.expanded,
                                                    onDismissRequest = three.onDismissRequest,
                                                    current = "Alpha",
                                                    options = listOf("Alpha", "Beta"),
                                                    optionLabel = { it },
                                                    onPick = { picked = it },
                                                    actionGroups = listOf(
                                                        listOf(MinputAction(label = "Deep verb", icon = Lucide.Menu, onClick = { verbs++ })),
                                                    ),
                                                )
                                            }),
                                        ),
                                    )
                                }),
                                MinputAction(label = "Root verb", icon = Lucide.Menu),
                            ),
                        )
                    }
                }
            }
        }
        rootOpen = true
        settle()
        click("Level two")
        click("Level three")
        assert(popupWindows().size == 3) { "expected three open menus, got ${popupWindows().size}" }
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun click(label: String) {
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText(label)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** The menu popup windows, oldest (the root) first. */
    private fun popupWindows(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (views.get(global) as List<View>).filter { it.javaClass.simpleName == "PopupLayout" }
    }

    /** A tap at a SCREEN point, delivered to the topmost menu — it receives every touch. */
    private fun tapAt(screenX: Float, screenY: Float) {
        val window = popupWindows().last()
        val location = IntArray(2).also(window::getLocationOnScreen)
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time, action, screenX, screenY, 0)
            event.setLocation(screenX - location[0], screenY - location[1])
            window.dispatchTouchEvent(event)
            event.recycle()
        }
        settle()
    }

    private fun assertAllClosed() {
        assert(!rootOpen) { "the root menu is still open" }
        assert(popupWindows().isEmpty()) { "${popupWindows().size} menus still up" }
    }

    @Test
    fun optionPickedThreeDeep_closesEveryMenu() {
        setCascade()
        click("Beta")
        assert(picked == "Beta") { "picked $picked" }
        assertAllClosed()
    }

    @Test
    fun verbThreeDeep_closesEveryMenu() {
        setCascade()
        click("Deep verb")
        assert(verbs == 1) { "verb ran $verbs times" }
        assertAllClosed()
    }

    @Test
    fun outsideTapThreeDeep_closesEveryMenu() {
        setCascade()
        tapAt(1_200f, 750f)
        assertAllClosed()
    }

    @Test
    fun tapOnTheParentMenu_stepsOutOneLevel() {
        setCascade()
        val parent = popupWindows()[1]
        val at = IntArray(2).also(parent::getLocationOnScreen)
        tapAt(at[0] + 10f, at[1] + parent.height / 2f)
        assert(popupWindows().size == 2) { "expected the root + level two, got ${popupWindows().size}" }
        assert(rootOpen)
    }
}
