package com.ab25cq.memo.data

import androidx.lifecycle.LiveData

class MemoRepository(private val memoDao: MemoDao, private val folderDao: FolderDao) {

    // ── メモ ──
    fun getMemosByFolder(folderId: Long?): LiveData<List<Memo>> = memoDao.getByFolder(folderId)
    suspend fun getAllMemosSync(): List<Memo> = memoDao.getAllSync()
    suspend fun getMemoById(id: Long): Memo? = memoDao.getById(id)
    suspend fun insertMemo(memo: Memo): Long = memoDao.insert(memo)
    suspend fun updateMemo(memo: Memo) = memoDao.update(memo)
    suspend fun updateMemos(memos: List<Memo>) = memoDao.updateAll(memos)
    suspend fun deleteMemo(memo: Memo) = memoDao.delete(memo)
    suspend fun deleteMemos(memos: List<Memo>) = memoDao.deleteAll(memos)

    // ── フォルダ ──
    fun getFoldersByParent(parentId: Long?): LiveData<List<Folder>> = folderDao.getByParent(parentId)
    suspend fun getAllFoldersSync(): List<Folder> = folderDao.getAllSync()
    suspend fun getFolderById(id: Long): Folder? = folderDao.getById(id)
    suspend fun insertFolder(folder: Folder): Long = folderDao.insert(folder)
    suspend fun updateFolder(folder: Folder) = folderDao.update(folder)

    // フォルダ削除：中身を親フォルダへ移動してから削除
    suspend fun deleteFolder(folder: Folder) {
        folderDao.moveMemosToParen(folder.id, folder.parentFolderId)
        folderDao.moveSubFoldersToParent(folder.id, folder.parentFolderId)
        folderDao.delete(folder)
    }

    // ── バックアップ用一括インポート（重複スキップ） ──
    suspend fun importAll(folders: List<Folder>, memos: List<Memo>): Int {
        val idMap = mutableMapOf<Long, Long>()

        // フォルダ: 同名・同親が既存なら再利用、なければ新規作成
        val existingFolders = folderDao.getAllSync()
        sortTopological(folders).forEach { f ->
            val newParentId = f.parentFolderId?.let { idMap[it] }
            val match = existingFolders.find { it.name == f.name && it.parentFolderId == newParentId }
            val newId = match?.id ?: folderDao.insert(f.copy(id = 0, parentFolderId = newParentId))
            idMap[f.id] = newId
        }

        // メモ: createdAt が同じものは同一メモとみなしてスキップ
        val existingCreatedAts = memoDao.getAllSync().map { it.createdAt }.toSet()
        var imported = 0
        memos.forEach { m ->
            if (m.createdAt !in existingCreatedAts) {
                val newFolderId = m.folderId?.let { idMap[it] }
                memoDao.insert(m.copy(id = 0, folderId = newFolderId))
                imported++
            }
        }
        return imported
    }

    private fun sortTopological(folders: List<Folder>): List<Folder> {
        val result = mutableListOf<Folder>()
        val remaining = folders.toMutableList()
        val done = mutableSetOf<Long?>()
        done.add(null)
        repeat(folders.size + 1) {
            val batch = remaining.filter { it.parentFolderId in done }
            result.addAll(batch)
            batch.forEach { done.add(it.id) }
            remaining.removeAll(batch.toSet())
        }
        result.addAll(remaining)
        return result
    }
}
