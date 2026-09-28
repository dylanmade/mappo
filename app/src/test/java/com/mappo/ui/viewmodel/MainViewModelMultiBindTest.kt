package com.mappo.ui.viewmodel

import com.mappo.data.model.AppLayoutBinding
import com.mappo.data.model.KeyLayout
import com.mappo.data.model.Layout
import com.mappo.data.model.TemplateRef
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.repository.AppLayoutBindingRepository
import com.mappo.data.repository.ControllerConfigRepository
import com.mappo.data.repository.InstalledAppsRepository
import com.mappo.data.repository.KeyboardTemplateRepository
import com.mappo.data.repository.KeyLayoutRepository
import com.mappo.data.repository.LayoutRepository
import com.mappo.data.settings.ShizukuRequiredPreferences
import com.mappo.data.settings.ActiveApplicationStore
import com.mappo.data.settings.AutoSwitchSettings
import com.mappo.data.settings.FrameSettings
import com.mappo.data.settings.FrameStyle
import com.mappo.data.settings.TextSize
import com.mappo.data.settings.MoveSettings
import com.mappo.data.settings.TileRevealSettings
import com.mappo.data.settings.TextSizeSettings
import com.mappo.service.shizuku.ShizukuConnection
import com.mappo.service.autoswitch.ApplicationAutoSwitcher
import com.mappo.service.foreground.ForegroundAppFilter
import com.mappo.service.foreground.ForegroundAppMonitor
import com.mappo.service.input.InputDispatcher
import com.mappo.service.keyboard.KeyboardController
import com.mappo.service.overlay.element.OverlayLiveEditController
import com.mappo.service.overlay.element.ToolbarOverlayManager
import com.mappo.service.overlay.element.OverlayPresenter
import com.mappo.steam.auth.SteamCredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `loadInstalledApps` coverage (one-shot PackageManager pass into the drawer's state
 * flow). The multi-package bind tests that shared this file retired with the multi-app
 * layout concept (2026-08-26 — a layout belongs to ONE application).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelMultiBindTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var keyLayoutRepo: KeyLayoutRepository
    private lateinit var layoutRepo: LayoutRepository
    private lateinit var controllerConfigRepo: ControllerConfigRepository
    private lateinit var bindingRepo: AppLayoutBindingRepository
    private lateinit var installedAppsRepo: InstalledAppsRepository
    private lateinit var settings: AutoSwitchSettings
    private lateinit var activeAppStore: ActiveApplicationStore
    private lateinit var frameSettings: FrameSettings
    private lateinit var textSizeSettings: TextSizeSettings
    private lateinit var shizukuRequiredPrefs: ShizukuRequiredPreferences
    private lateinit var shizukuConnection: ShizukuConnection
    private lateinit var autoSwitcher: ApplicationAutoSwitcher
    private lateinit var filter: ForegroundAppFilter
    private lateinit var foregroundAppMonitor: ForegroundAppMonitor
    private lateinit var templateRepo: KeyboardTemplateRepository
    private lateinit var inputDispatcher: InputDispatcher
    private lateinit var overlayPresenter: OverlayPresenter
    private lateinit var overlayLiveEditController: OverlayLiveEditController
    private lateinit var toolbarOverlayManager: ToolbarOverlayManager
    private lateinit var steamCredentialStore: SteamCredentialStore
    private lateinit var keyboardController: KeyboardController

    private val activeLayout = MutableStateFlow<Layout?>(null)
    private val allProfiles = MutableStateFlow<List<Layout>>(emptyList())
    private val allBindings = MutableStateFlow<List<AppLayoutBinding>>(emptyList())
    private val allLayouts = MutableStateFlow<List<KeyLayout>>(emptyList())
    private val allTemplates = MutableStateFlow<List<TemplateRef>>(emptyList())
    private val autoSwitchEvents = MutableSharedFlow<ApplicationAutoSwitcher.UiEvent>(
        replay = 0, extraBufferCapacity = 4,
    )
    private val autoSwitchEnabled = MutableStateFlow(true)
    private val ignoredPackages = MutableStateFlow<Set<String>>(emptySet())

    private lateinit var subject: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        keyLayoutRepo = mockk(relaxed = true)
        layoutRepo = mockk(relaxed = true)
        controllerConfigRepo = mockk(relaxed = true)
        bindingRepo = mockk(relaxed = true)
        installedAppsRepo = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        frameSettings = mockk(relaxed = true)
        textSizeSettings = mockk(relaxed = true)
        shizukuRequiredPrefs = mockk(relaxed = true)
        shizukuConnection = mockk(relaxed = true)
        autoSwitcher = mockk(relaxed = true)
        filter = mockk(relaxed = true)
        foregroundAppMonitor = mockk(relaxed = true)
        every { foregroundAppMonitor.currentPackage } returns MutableStateFlow(null)
        templateRepo = mockk(relaxed = true)
        inputDispatcher = mockk(relaxed = true)
        overlayPresenter = mockk(relaxed = true)
        overlayLiveEditController = mockk(relaxed = true)
        toolbarOverlayManager = mockk(relaxed = true)
        steamCredentialStore = mockk(relaxed = true)
        // SharedFlow.collect returns Nothing — a relaxed mock throws on collect.
        every { overlayPresenter.errorMessages } returns MutableSharedFlow()
        keyboardController = KeyboardController(
            inputDispatcher = inputDispatcher,
            keyLayoutRepository = keyLayoutRepo,
            layoutRepository = layoutRepo,
            ioDispatcher = testDispatcher,
        )

        every { layoutRepo.activeLayout } returns activeLayout
        every { layoutRepo.getAllLayouts() } returns allProfiles
        every { bindingRepo.getAll() } returns allBindings
        every { keyLayoutRepo.getKeyLayoutsByLayout(any()) } returns allLayouts
        every { frameSettings.style } returns MutableStateFlow(FrameStyle())
        every { textSizeSettings.size } returns MutableStateFlow(TextSize.SMALL)
        every { settings.autoSwitchEnabled } returns autoSwitchEnabled
        every { settings.ignoredPackages } returns ignoredPackages
        activeAppStore = mockk(relaxed = true)
        every { activeAppStore.activeAppPackage } returns MutableStateFlow<String?>(null)
        every { autoSwitcher.events } returns autoSwitchEvents
        every { templateRepo.builtIns } returns emptyList()
        every { templateRepo.allTemplates } returns allTemplates
        every { controllerConfigRepo.observeActiveConfig(any()) } returns
            MutableStateFlow<ControllerConfig?>(null)

        subject = MainViewModel(
            keyLayoutRepository = keyLayoutRepo,
            layoutRepository = layoutRepo,
            controllerConfigRepository = controllerConfigRepo,
            appLayoutBindingRepository = bindingRepo,
            installedAppsRepository = installedAppsRepo,
            autoSwitchSettings = settings,
            activeApplicationStore = activeAppStore,
            frameSettings = frameSettings,
            textSizeSettings = textSizeSettings,
            moveSettings = mockk(relaxed = true) {
                every { commitGesture } returns MutableStateFlow(MoveSettings.Default)
            },
            tileRevealSettings = mockk(relaxed = true) {
                every { reveal } returns MutableStateFlow(TileRevealSettings.Default)
            },
            shizukuRequiredPreferences = shizukuRequiredPrefs,
            shizukuConnection = shizukuConnection,
            autoSwitcher = autoSwitcher,
            foregroundAppFilter = filter,
            foregroundAppMonitor = foregroundAppMonitor,
            keyboardTemplateRepository = templateRepo,
            inputDispatcher = inputDispatcher,
            overlayPresenter = overlayPresenter,
            overlayLiveEditController = overlayLiveEditController,
            toolbarOverlayManager = toolbarOverlayManager,
            steamCredentialStore = steamCredentialStore,
            keyboardController = keyboardController,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadInstalledApps_populatesStateFlowFromRepository() = runTest(testDispatcher) {
        val apps = listOf(
            InstalledAppsRepository.InstalledApp("com.a", "Alpha"),
            InstalledAppsRepository.InstalledApp("com.b", "Beta"),
        )
        coEvery { installedAppsRepo.launchableApps() } returns apps

        assertTrue(
            "installedApps starts empty before load",
            subject.installedApps.value.isEmpty(),
        )

        subject.loadInstalledApps()
        advanceUntilIdle()

        assertEquals(apps, subject.installedApps.value)
    }
}
