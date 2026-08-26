package com.mappo.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mappo.data.model.AppLayoutBinding
import com.mappo.data.model.Layout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room/DAO instrumented smoke check. Builds an in-memory [AppDatabase] and
 * round-trips an [AppLayoutBinding] through [AppLayoutBindingDao] to confirm
 * the schema, FK to [Layout], and the test harness all work end-to-end.
 */
@RunWith(AndroidJUnit4::class)
class AppLayoutBindingDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var layoutDao: LayoutDao
    private lateinit var bindingDao: AppLayoutBindingDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        layoutDao = db.layoutDao()
        bindingDao = db.appLayoutBindingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertAndQuery_roundTrip() = runTest {
        val layoutId = layoutDao.insert(Layout(name = "Test Layout"))
        val binding = AppLayoutBinding(packageName = "com.example.app", layoutId = layoutId)

        bindingDao.upsert(binding)

        val fetched = bindingDao.getForPackageOnce("com.example.app")
        assertEquals(binding, fetched)
    }

    @Test
    fun delete_removesBinding() = runTest {
        val layoutId = layoutDao.insert(Layout(name = "Test Layout"))
        val binding = AppLayoutBinding(packageName = "com.example.app", layoutId = layoutId)
        bindingDao.upsert(binding)

        bindingDao.delete("com.example.app")

        assertNull(bindingDao.getForPackageOnce("com.example.app"))
    }

    @Test
    fun upsert_replacesBindingForSamePackageAndSubId() = runTest {
        val profileA = layoutDao.insert(Layout(name = "A"))
        val profileB = layoutDao.insert(Layout(name = "B"))

        bindingDao.upsert(AppLayoutBinding(packageName = "com.example.app", layoutId = profileA))
        bindingDao.upsert(AppLayoutBinding(packageName = "com.example.app", layoutId = profileB))

        assertEquals(profileB, bindingDao.getForPackageOnce("com.example.app")?.layoutId)
    }

    @Test
    fun deletingProfile_cascadesToBindings() = runTest {
        val layoutId = layoutDao.insert(Layout(name = "Test"))
        bindingDao.upsert(AppLayoutBinding(packageName = "com.example.app", layoutId = layoutId))

        layoutDao.delete(Layout(id = layoutId, name = "Test"))

        assertNull(bindingDao.getForPackageOnce("com.example.app"))
    }
}
