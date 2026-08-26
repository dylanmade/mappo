package com.mappo.data.repository

import com.mappo.data.db.KeyLayoutDao
import com.mappo.data.db.LayoutDao
import com.mappo.data.model.KeyLayout
import com.mappo.data.model.Layout
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Hand-rolled fakes for the DAOs (interfaces). [KeyLayoutRepository] is real,
 * wrapping the fake KeyLayoutDao, so [LayoutRepository.addLayout]'s seedDefaults
 * call exercises the real seeding logic. [ControllerConfigRepository] is mocked
 * since its internals aren't relevant to these tests.
 */
class LayoutRepositoryTest {

    private lateinit var layoutDao: FakeProfileDao
    private lateinit var keyLayoutDao: FakeLayoutDao
    private lateinit var keyLayoutRepo: KeyLayoutRepository
    private lateinit var controllerConfigRepo: ControllerConfigRepository
    private lateinit var subject: LayoutRepository

    @Before
    fun setUp() {
        layoutDao = FakeProfileDao()
        keyLayoutDao = FakeLayoutDao()
        keyLayoutRepo = KeyLayoutRepository(keyLayoutDao)
        controllerConfigRepo = mockk(relaxed = true)
        coEvery { controllerConfigRepo.copyConfig(any(), any()) } returns Unit

        val bindingDao = mockk<com.mappo.data.db.AppLayoutBindingDao>(relaxed = true)
        val activeAppStore = mockk<com.mappo.data.settings.ActiveApplicationStore>(relaxed = true)
        io.mockk.every { activeAppStore.activeAppPackage } returns
            kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        subject = LayoutRepository(
            layoutDao = layoutDao,
            keyLayoutDao = keyLayoutDao,
            keyLayoutRepository = keyLayoutRepo,
            controllerConfigRepository = controllerConfigRepo,
            appLayoutBindingDao = bindingDao,
            activeApplicationStore = activeAppStore,
        )
    }

    @Test
    fun addProfile_insertsAndSeedsDefaultLayouts() = runTest {
        val id = subject.addLayout("New Layout")

        assertNotNull(layoutDao.byId(id))
        val seeded = keyLayoutDao.getByLayoutOnce(id)
        assertTrue(
            "addLayout should seed default layouts; found ${seeded.size}",
            seeded.isNotEmpty(),
        )
    }

    @Test
    fun setActiveProfileById_existingId_setsAndReturnsProfile() = runTest {
        val id = subject.addLayout("Test")
        val active = subject.setActiveLayoutById(id)
        assertEquals(id, active?.id)
        assertEquals(id, subject.activeLayout.value?.id)
    }

    @Test
    fun setActiveProfileById_missingId_returnsNullAndDoesNotChangeActive() = runTest {
        val id = subject.addLayout("Test")
        subject.setActiveLayoutById(id) // sets active

        val result = subject.setActiveLayoutById(9999L)

        assertNull(result)
        assertEquals(id, subject.activeLayout.value?.id)
    }

    @Test
    fun setActiveProfile_directly_updatesFlow() = runTest {
        val id = subject.addLayout("Test")
        val layout = layoutDao.byId(id)!!
        subject.setActiveLayout(layout)
        assertEquals(layout, subject.activeLayout.value)
    }

    @Test
    fun duplicateProfile_copiesLayoutsFromSource() = runTest {
        val sourceId = subject.addLayout("Source")
        val seededCount = keyLayoutDao.getByLayoutOnce(sourceId).size
        val source = layoutDao.byId(sourceId)!!

        subject.duplicateLayout(source, "Copy")

        val copyId = layoutDao.allOnce().first { it.name == "Copy" }.id
        assertEquals(seededCount, keyLayoutDao.getByLayoutOnce(copyId).size)
        coVerify { controllerConfigRepo.copyConfig(sourceProfileId = sourceId, destProfileId = copyId) }
    }

    @Test
    fun duplicateProfile_emptySource_seedsDefaults() = runTest {
        // Layout inserted directly so it has zero layouts (mimics the SQL seed callback path).
        val sourceId = layoutDao.insert(Layout(name = "Bare"))
        val source = layoutDao.byId(sourceId)!!

        subject.duplicateLayout(source, "FromBare")

        val copyId = layoutDao.allOnce().first { it.name == "FromBare" }.id
        assertTrue(
            "empty-source duplicate should fall back to seeding defaults",
            keyLayoutDao.getByLayoutOnce(copyId).isNotEmpty(),
        )
    }

    @Test
    fun deleteLayout_removes() = runTest {
        val id = subject.addLayout("Removable")
        val layout = layoutDao.byId(id)!!
        subject.deleteLayout(layout)
        assertNull(layoutDao.byId(id))
    }
}

private class FakeProfileDao : LayoutDao {
    private val rows = MutableStateFlow<Map<Long, Layout>>(emptyMap())
    private var nextId = 1L

    fun byId(id: Long): Layout? = rows.value[id]
    fun allOnce(): List<Layout> = rows.value.values.toList()

    override fun getAll(): Flow<List<Layout>> =
        rows.map { it.values.sortedBy(Layout::name) }

    override suspend fun getById(id: Long): Layout? = rows.value[id]

    override suspend fun insert(layout: Layout): Long {
        val assigned = if (layout.id == 0L) nextId++ else layout.id
        rows.value = rows.value + (assigned to layout.copy(id = assigned))
        return assigned
    }

    override suspend fun delete(layout: Layout) {
        rows.value = rows.value - layout.id
    }
}

private class FakeLayoutDao : KeyLayoutDao {
    private val rows = MutableStateFlow<Map<Long, KeyLayout>>(emptyMap())
    private var nextId = 1L

    override fun getByLayout(layoutId: Long): Flow<List<KeyLayout>> =
        rows.map { it.values.filter { row -> row.layoutId == layoutId } }

    override suspend fun getByLayoutOnce(layoutId: Long): List<KeyLayout> =
        rows.value.values.filter { it.layoutId == layoutId }
            .sortedWith(compareBy({ it.position }, { it.id }))

    override suspend fun getById(id: Long): KeyLayout? = rows.value[id]

    override suspend fun insert(layout: KeyLayout): Long {
        val assigned = if (layout.id == 0L) nextId++ else layout.id
        rows.value = rows.value + (assigned to layout.copy(id = assigned))
        return assigned
    }

    override suspend fun update(layout: KeyLayout) {
        rows.value = rows.value + (layout.id to layout)
    }

    override suspend fun updateAll(layouts: List<KeyLayout>) {
        rows.value = rows.value + layouts.associateBy { it.id }
    }

    override suspend fun delete(layout: KeyLayout) {
        rows.value = rows.value - layout.id
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value - id
    }
}
