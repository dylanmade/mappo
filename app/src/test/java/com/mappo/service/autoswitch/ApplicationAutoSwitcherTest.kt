package com.mappo.service.autoswitch

import app.cash.turbine.test
import com.mappo.data.model.Layout
import com.mappo.data.repository.AppLayoutBindingRepository
import com.mappo.data.repository.LayoutRepository
import com.mappo.data.settings.ActiveApplicationStore
import com.mappo.data.settings.AutoSwitchSettings
import com.mappo.service.foreground.ForegroundAppFilter
import com.mappo.service.foreground.ForegroundAppMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Drives [ApplicationAutoSwitcher.handleForegroundChange] directly with mocked
 * collaborators. The dispatcher-bound start() collector isn't exercised here —
 * its only logic is `filterNotNull().distinctUntilChanged().collect(::handleForegroundChange)`,
 * which is trivial. Branch coverage of handleForegroundChange is the goal.
 */
class ApplicationAutoSwitcherTest {

    private val foregroundAppMonitor = ForegroundAppMonitor(
        mockk { every { packageName } returns "com.mappo" }
    )
    private lateinit var bindingRepo: AppLayoutBindingRepository
    private lateinit var layoutRepo: LayoutRepository
    private lateinit var settings: AutoSwitchSettings
    private lateinit var activeAppStore: ActiveApplicationStore
    private lateinit var filter: ForegroundAppFilter
    private lateinit var inputDispatcher: com.mappo.service.input.InputDispatcher
    private lateinit var subject: ApplicationAutoSwitcher

    private val autoSwitchEnabled = MutableStateFlow(true)
    private val ignoredPackages = MutableStateFlow<Set<String>>(emptySet())
    private val activeLayout = MutableStateFlow<Layout?>(
        Layout(id = 1L, name = "Default"),
    )

    @Before
    fun setUp() {
        bindingRepo = mockk(relaxed = true)
        layoutRepo = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        filter = mockk(relaxed = true)
        inputDispatcher = mockk(relaxed = true)

        every { settings.autoSwitchEnabled } returns autoSwitchEnabled
        activeAppStore = mockk(relaxed = true)
        every { settings.ignoredPackages } returns ignoredPackages
        every { layoutRepo.activeLayout } returns activeLayout
        every { filter.isInteresting(any()) } returns true
        every { filter.appLabel(any()) } answers { firstArg<String>().substringAfterLast('.') }
        every { inputDispatcher.queryPrimaryDisplayForegroundPackage() } returns null

        coEvery { bindingRepo.getForPackageOnce(any()) } returns null

        subject = ApplicationAutoSwitcher(
            foregroundAppMonitor = foregroundAppMonitor,
            bindingRepo = bindingRepo,
            layoutRepo = layoutRepo,
            settings = settings,
            activeApplicationStore = activeAppStore,
            filter = filter,
            inputDispatcher = inputDispatcher,
            scope = TestScope(),
        )
    }

    @Test
    fun emitsNothing_whenAutoSwitchDisabled() = runTest {
        autoSwitchEnabled.value = false

        subject.events.test {
            subject.handleForegroundChange("com.example.game")
            expectNoEvents()
        }
    }

    @Test
    fun emitsNothing_whenFilterRejectsPackage() = runTest {
        every { filter.isInteresting("com.android.systemui") } returns false

        subject.events.test {
            subject.handleForegroundChange("com.android.systemui")
            expectNoEvents()
        }
    }

    @Test
    fun emitsNothing_whenBindingMatchesActiveProfile() = runTest {
        coEvery { bindingRepo.getForPackageOnce("com.example.game") } returns
            com.mappo.data.model.AppLayoutBinding(
                packageName = "com.example.game",
                layoutId = 1L,
            )

        subject.events.test {
            subject.handleForegroundChange("com.example.game")
            expectNoEvents()
        }
    }

    @Test
    fun emitsSwitched_whenBindingPointsToDifferentProfile() = runTest {
        val gameProfile = Layout(id = 7L, name = "Racing")
        coEvery { bindingRepo.getForPackageOnce("com.example.game") } returns
            com.mappo.data.model.AppLayoutBinding(
                packageName = "com.example.game",
                layoutId = 7L,
            )
        coEvery { layoutRepo.setActiveLayoutById(7L) } returns gameProfile

        subject.events.test {
            subject.handleForegroundChange("com.example.game")
            val event = awaitItem()
            assertEquals(
                ApplicationAutoSwitcher.UiEvent.Switched(
                    pkg = "com.example.game",
                    appLabel = "game",
                    layoutName = "Racing",
                ),
                event,
            )
        }
    }

    @Test
    fun emitsNothing_whenBindingReferencesMissingProfile() = runTest {
        coEvery { bindingRepo.getForPackageOnce("com.example.game") } returns
            com.mappo.data.model.AppLayoutBinding(
                packageName = "com.example.game",
                layoutId = 999L,
            )
        coEvery { layoutRepo.setActiveLayoutById(999L) } returns null

        subject.events.test {
            subject.handleForegroundChange("com.example.game")
            expectNoEvents()
        }
    }

    @Test
    fun emitsNothing_andCreatesNothing_whenNoBinding() = runTest {
        // 2026-08-26: an unbound app is skipped outright — no prompt, no auto-create;
        // "no layouts yet" is a first-class state served by the controls screen.
        subject.events.test {
            subject.handleForegroundChange("com.example.game")
            expectNoEvents()
        }
        coVerify(exactly = 0) { layoutRepo.addLayout(any(), any()) }
        coVerify(exactly = 0) { bindingRepo.bind(any(), any()) }
    }

    @Test
    fun reevaluate_noCachedAndNoLiveQuery_isNoOp() = runTest {
        every { inputDispatcher.queryPrimaryDisplayForegroundPackage() } returns null
        // foregroundAppMonitor's currentPackage is null initially.
        subject.reevaluate()
        coVerify(exactly = 0) { bindingRepo.getForPackageOnce(any()) }
    }

    @Test
    fun reevaluate_cachedPackage_firesHandleForegroundChange() = runTest {
        foregroundAppMonitor.reportForegroundPackage("com.example.foo")
        every { inputDispatcher.queryPrimaryDisplayForegroundPackage() } returns null
        coEvery { bindingRepo.getForPackageOnce("com.example.foo") } returns null

        // Use a TestScope so launch in reevaluate() runs against the test dispatcher.
        val testScope = this
        val testSubject = ApplicationAutoSwitcher(
            foregroundAppMonitor = foregroundAppMonitor,
            bindingRepo = bindingRepo,
            layoutRepo = layoutRepo,
            settings = settings,
            activeApplicationStore = activeAppStore,
            filter = filter,
            inputDispatcher = inputDispatcher,
            scope = testScope,
        )
        testSubject.reevaluate()
        testScope.testScheduler.advanceUntilIdle()

        coVerify { bindingRepo.getForPackageOnce("com.example.foo") }
    }

    @Test
    fun reevaluate_liveQuery_winsOverCachedPackage() = runTest {
        // Cache holds an old / stale package; the live query knows the current primary
        // display foreground app. The live result should win.
        foregroundAppMonitor.reportForegroundPackage("com.example.stale")
        every { inputDispatcher.queryPrimaryDisplayForegroundPackage() } returns "com.example.live"
        coEvery { bindingRepo.getForPackageOnce(any()) } returns null

        val testScope = this
        val testSubject = ApplicationAutoSwitcher(
            foregroundAppMonitor = foregroundAppMonitor,
            bindingRepo = bindingRepo,
            layoutRepo = layoutRepo,
            settings = settings,
            activeApplicationStore = activeAppStore,
            filter = filter,
            inputDispatcher = inputDispatcher,
            scope = testScope,
        )
        testSubject.reevaluate()
        testScope.testScheduler.advanceUntilIdle()

        coVerify { bindingRepo.getForPackageOnce("com.example.live") }
        coVerify(exactly = 0) { bindingRepo.getForPackageOnce("com.example.stale") }
    }
}
