package com.mappo.service.autoswitch

import app.cash.turbine.test
import com.mappo.data.model.Profile
import com.mappo.data.repository.AppProfileBindingRepository
import com.mappo.data.repository.ProfileRepository
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
 * Drives [ProfileAutoSwitcher.handleForegroundChange] directly with mocked
 * collaborators. The dispatcher-bound start() collector isn't exercised here —
 * its only logic is `filterNotNull().distinctUntilChanged().collect(::handleForegroundChange)`,
 * which is trivial. Branch coverage of handleForegroundChange is the goal.
 */
class ProfileAutoSwitcherTest {

    private val foregroundAppMonitor = ForegroundAppMonitor(
        mockk { every { packageName } returns "com.mappo" }
    )
    private lateinit var bindingRepo: AppProfileBindingRepository
    private lateinit var profileRepo: ProfileRepository
    private lateinit var settings: AutoSwitchSettings
    private lateinit var filter: ForegroundAppFilter
    private lateinit var inputDispatcher: com.mappo.service.input.InputDispatcher
    private lateinit var subject: ProfileAutoSwitcher

    private val autoSwitchEnabled = MutableStateFlow(true)
    private val autoCreateEnabled = MutableStateFlow(false)
    private val ignoredPackages = MutableStateFlow<Set<String>>(emptySet())
    private val activeProfile = MutableStateFlow<Profile?>(
        Profile(id = 1L, name = "Default", isDefault = true),
    )

    @Before
    fun setUp() {
        bindingRepo = mockk(relaxed = true)
        profileRepo = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        filter = mockk(relaxed = true)
        inputDispatcher = mockk(relaxed = true)

        every { settings.autoSwitchEnabled } returns autoSwitchEnabled
        every { settings.autoCreateProfilesEnabled } returns autoCreateEnabled
        every { settings.ignoredPackages } returns ignoredPackages
        every { profileRepo.activeProfile } returns activeProfile
        every { filter.isInteresting(any()) } returns true
        every { filter.appLabel(any()) } answers { firstArg<String>().substringAfterLast('.') }
        every { inputDispatcher.queryPrimaryDisplayForegroundPackage() } returns null

        coEvery { bindingRepo.getForPackageOnce(any(), any()) } returns null

        subject = ProfileAutoSwitcher(
            foregroundAppMonitor = foregroundAppMonitor,
            bindingRepo = bindingRepo,
            profileRepo = profileRepo,
            settings = settings,
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
        coEvery { bindingRepo.getForPackageOnce("com.example.game", any()) } returns
            com.mappo.data.model.AppProfileBinding(
                packageName = "com.example.game",
                profileId = 1L,
            )

        subject.events.test {
            subject.handleForegroundChange("com.example.game")
            expectNoEvents()
        }
    }

    @Test
    fun emitsSwitched_whenBindingPointsToDifferentProfile() = runTest {
        val gameProfile = Profile(id = 7L, name = "Racing", isDefault = false)
        coEvery { bindingRepo.getForPackageOnce("com.example.game", any()) } returns
            com.mappo.data.model.AppProfileBinding(
                packageName = "com.example.game",
                profileId = 7L,
            )
        coEvery { profileRepo.setActiveProfileById(7L) } returns gameProfile

        subject.events.test {
            subject.handleForegroundChange("com.example.game")
            val event = awaitItem()
            assertEquals(
                ProfileAutoSwitcher.UiEvent.Switched(
                    pkg = "com.example.game",
                    appLabel = "game",
                    profileName = "Racing",
                ),
                event,
            )
        }
    }

    @Test
    fun emitsNothing_whenBindingReferencesMissingProfile() = runTest {
        coEvery { bindingRepo.getForPackageOnce("com.example.game", any()) } returns
            com.mappo.data.model.AppProfileBinding(
                packageName = "com.example.game",
                profileId = 999L,
            )
        coEvery { profileRepo.setActiveProfileById(999L) } returns null

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
        coVerify(exactly = 0) { profileRepo.addProfile(any(), any()) }
        coVerify(exactly = 0) { bindingRepo.bind(any(), any(), any()) }
    }

    @Test
    fun reevaluate_noCachedAndNoLiveQuery_isNoOp() = runTest {
        every { inputDispatcher.queryPrimaryDisplayForegroundPackage() } returns null
        // foregroundAppMonitor's currentPackage is null initially.
        subject.reevaluate()
        coVerify(exactly = 0) { bindingRepo.getForPackageOnce(any(), any()) }
    }

    @Test
    fun reevaluate_cachedPackage_firesHandleForegroundChange() = runTest {
        foregroundAppMonitor.reportForegroundPackage("com.example.foo")
        every { inputDispatcher.queryPrimaryDisplayForegroundPackage() } returns null
        coEvery { bindingRepo.getForPackageOnce("com.example.foo", any()) } returns null

        // Use a TestScope so launch in reevaluate() runs against the test dispatcher.
        val testScope = this
        val testSubject = ProfileAutoSwitcher(
            foregroundAppMonitor = foregroundAppMonitor,
            bindingRepo = bindingRepo,
            profileRepo = profileRepo,
            settings = settings,
            filter = filter,
            inputDispatcher = inputDispatcher,
            scope = testScope,
        )
        testSubject.reevaluate()
        testScope.testScheduler.advanceUntilIdle()

        coVerify { bindingRepo.getForPackageOnce("com.example.foo", any()) }
    }

    @Test
    fun reevaluate_liveQuery_winsOverCachedPackage() = runTest {
        // Cache holds an old / stale package; the live query knows the current primary
        // display foreground app. The live result should win.
        foregroundAppMonitor.reportForegroundPackage("com.example.stale")
        every { inputDispatcher.queryPrimaryDisplayForegroundPackage() } returns "com.example.live"
        coEvery { bindingRepo.getForPackageOnce(any(), any()) } returns null

        val testScope = this
        val testSubject = ProfileAutoSwitcher(
            foregroundAppMonitor = foregroundAppMonitor,
            bindingRepo = bindingRepo,
            profileRepo = profileRepo,
            settings = settings,
            filter = filter,
            inputDispatcher = inputDispatcher,
            scope = testScope,
        )
        testSubject.reevaluate()
        testScope.testScheduler.advanceUntilIdle()

        coVerify { bindingRepo.getForPackageOnce("com.example.live", any()) }
        coVerify(exactly = 0) { bindingRepo.getForPackageOnce("com.example.stale", any()) }
    }
}
