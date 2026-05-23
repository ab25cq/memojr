package com.ab25cq.memo.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE parentFolderId IS :parentId ORDER BY name ASC")
    fun getByParent(parentId: Long?): LiveData<List<Folder>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    suspend fun getAllSync(): List<Folder>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    @Query("UPDATE memos SET folderId = :newParentId WHERE folderId = :oldFolderId")
    suspend fun moveMemosToParen(oldFolderId: Long, newParentId: Long?)

    @Query("UPDATE folders SET parentFolderId = :newParentId WHERE parentFolderId = :oldFolderId")
    suspend fun moveSubFoldersToParent(oldFolderId: Long, newParentId: Long?)
}
