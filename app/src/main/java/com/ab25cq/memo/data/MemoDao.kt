package com.ab25cq.memo.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE folderId IS :folderId ORDER BY updatedAt DESC")
    fun getByFolder(folderId: Long?): LiveData<List<Memo>>

    @Query("SELECT * FROM memos ORDER BY updatedAt DESC")
    suspend fun getAllSync(): List<Memo>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: Long): Memo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: Memo): Long

    @Update
    suspend fun update(memo: Memo)

    @Update
    suspend fun updateAll(memos: List<Memo>)

    @Delete
    suspend fun delete(memo: Memo)

    @Delete
    suspend fun deleteAll(memos: List<Memo>)

    @Query("DELETE FROM memos")
    suspend fun deleteAll()
}
