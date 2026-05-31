package com.example.filemanagementapp.data.local.favorite

import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoriteLocalRepository(
    private val favoriteItemDao: FavoriteItemDao
) {
    suspend fun getFavoritePaths(
        username: String,
        paths: List<String>
    ): Set<String> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) {
            return@withContext emptySet()
        }

        favoriteItemDao.getByPaths(username, paths)
            .mapTo(linkedSetOf()) { it.itemPath }
    }

    suspend fun toggleFavorite(
        username: String,
        item: ExplorerItem
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = favoriteItemDao.getByPaths(username, listOf(item.path)).isNotEmpty()
        if (existing) {
            favoriteItemDao.deleteByPath(username, item.path)
            false
        } else {
            favoriteItemDao.upsert(
                FavoriteItemEntity(
                    username = username,
                    itemPath = item.path,
                    itemType = item.type.name,
                    createdAt = System.currentTimeMillis()
                )
            )
            true
        }
    }

    suspend fun updatePath(
        username: String,
        oldPath: String,
        newPath: String,
        isFolder: Boolean
    ) = withContext(Dispatchers.IO) {
        if (isFolder) {
            favoriteItemDao.replacePathPrefix(
                username = username,
                oldPrefix = oldPath,
                oldPrefixWithSlash = "$oldPath/%",
                newPrefix = newPath
            )
        } else {
            favoriteItemDao.updateExactPath(
                username = username,
                oldPath = oldPath,
                newPath = newPath
            )
        }
    }

    suspend fun deletePath(
        username: String,
        path: String,
        isFolder: Boolean
    ) = withContext(Dispatchers.IO) {
        if (isFolder) {
            favoriteItemDao.deleteByPrefix(
                username = username,
                pathPrefix = path,
                pathPrefixWithSlash = "$path/%"
            )
        } else {
            favoriteItemDao.deleteByPath(username, path)
        }
    }
}
