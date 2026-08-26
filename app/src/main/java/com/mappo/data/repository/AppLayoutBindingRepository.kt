package com.mappo.data.repository

import com.mappo.data.db.AppLayoutBindingDao
import com.mappo.data.model.AppLayoutBinding
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An application's ACTIVE LAYOUT pointer (2026-08-26 model: one row per package — the
 * multi-bind `subId`/`bindMany` machinery retired with the multi-app profile concept).
 * The binding doubles as the app's functional default: it always names the app's last
 * activated layout, and auto-detection activates it when the app foregrounds.
 */
@Singleton
class AppLayoutBindingRepository @Inject constructor(
    private val dao: AppLayoutBindingDao
) {

    fun getAll(): Flow<List<AppLayoutBinding>> = dao.getAll()

    suspend fun getForPackageOnce(packageName: String): AppLayoutBinding? =
        dao.getForPackageOnce(packageName)

    suspend fun bind(packageName: String, layoutId: Long) {
        dao.upsert(AppLayoutBinding(packageName = packageName, layoutId = layoutId))
    }

    suspend fun unbind(packageName: String) {
        dao.delete(packageName)
    }
}
