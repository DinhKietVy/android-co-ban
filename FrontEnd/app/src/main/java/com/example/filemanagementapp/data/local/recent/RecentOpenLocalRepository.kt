package com.example.filemanagementapp.data.local.recent

class RecentOpenLocalRepository(
    private val dao: RecentOpenDao
) {
    suspend fun recordOpen(username: String, path: String) {
        val entity = RecentOpenEntity(
            username = username,
            path = path,
            openedAtEpochMillis = System.currentTimeMillis()
        )
        dao.upsert(entity)
    }

    suspend fun getRecentOpens(username: String, limit: Int = 10): List<RecentOpenEntity> {
        return dao.getRecentOpens(username, limit)
    }
}
