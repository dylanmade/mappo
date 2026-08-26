package com.mappo.data.repository

import com.mappo.data.db.AppLayoutBindingDao
import com.mappo.data.model.AppLayoutBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Repository is a thin wrapper over the DAO; tests use a hand-rolled fake DAO
 * (the DAO is an interface, so no MockK or Robolectric needed). One binding per
 * package (2026-08-26 — the multi-bind subId machinery retired).
 */
class AppLayoutBindingRepositoryTest {

    private lateinit var dao: FakeBindingDao
    private lateinit var subject: AppLayoutBindingRepository

    @Before
    fun setUp() {
        dao = FakeBindingDao()
        subject = AppLayoutBindingRepository(dao)
    }

    @Test
    fun bind_createsNewBinding() = runTest {
        subject.bind(packageName = "com.example", layoutId = 1L)
        val stored = subject.getForPackageOnce("com.example")
        assertEquals(AppLayoutBinding(packageName = "com.example", layoutId = 1L), stored)
    }

    @Test
    fun bind_replacesExistingBindingForSamePackage() = runTest {
        subject.bind(packageName = "com.example", layoutId = 1L)
        subject.bind(packageName = "com.example", layoutId = 2L)
        assertEquals(2L, subject.getForPackageOnce("com.example")?.layoutId)
    }

    @Test
    fun unbind_removesBinding() = runTest {
        subject.bind(packageName = "com.example", layoutId = 1L)
        subject.unbind(packageName = "com.example")
        assertNull(subject.getForPackageOnce("com.example"))
    }

    @Test
    fun unbind_missingPackage_isNoOp() = runTest {
        subject.unbind(packageName = "com.example")
        assertNull(subject.getForPackageOnce("com.example"))
    }

    @Test
    fun getAll_emitsCurrentSnapshot() = runTest {
        subject.bind(packageName = "com.alpha", layoutId = 1L)
        subject.bind(packageName = "com.beta", layoutId = 2L)

        val all = subject.getAll().first()
        assertEquals(
            setOf(
                AppLayoutBinding(packageName = "com.alpha", layoutId = 1L),
                AppLayoutBinding(packageName = "com.beta", layoutId = 2L),
            ),
            all.toSet(),
        )
    }
}

/**
 * In-memory implementation of [AppLayoutBindingDao] keyed by packageName — mirrors
 * the Room schema's primary key. Backs Flow-returning queries with a StateFlow so
 * collectors see updates after upsert/delete.
 */
private class FakeBindingDao : AppLayoutBindingDao {
    private val rows = MutableStateFlow<Map<String, AppLayoutBinding>>(emptyMap())

    override fun getAll(): Flow<List<AppLayoutBinding>> =
        rows.map { it.values.sortedBy { row -> row.packageName } }

    override suspend fun getForPackageOnce(packageName: String): AppLayoutBinding? =
        rows.value[packageName]

    override suspend fun upsert(binding: AppLayoutBinding) {
        rows.value = rows.value + (binding.packageName to binding)
    }

    override suspend fun delete(packageName: String) {
        rows.value = rows.value - packageName
    }
}
