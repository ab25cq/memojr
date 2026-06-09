package com.ab25cq.memo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.ab25cq.memo.data.Folder
import com.ab25cq.memo.data.Memo
import com.ab25cq.memo.R
import com.ab25cq.memo.data.MemoDatabase
import com.ab25cq.memo.data.MemoRepository
import kotlinx.coroutines.launch

data class FolderNavEntry(val id: Long?, val name: String)

class MemoViewModel(app: Application) : AndroidViewModel(app) {
    private val db   = MemoDatabase.getInstance(app)
    private val repo = MemoRepository(db.memoDao(), db.folderDao())

    private val _folderPath = MutableLiveData<List<FolderNavEntry>>(listOf(FolderNavEntry(null, app.getString(R.string.home))))
    val folderPath: LiveData<List<FolderNavEntry>> = _folderPath

    private val _currentFolderId = MutableLiveData<Long?>(null)

    val currentFolders: LiveData<List<Folder>> = _currentFolderId.switchMap { id ->
        repo.getFoldersByParent(id)
    }
    val currentMemos: LiveData<List<Memo>> = _currentFolderId.switchMap { id ->
        repo.getMemosByFolder(id)
    }

    fun navigateTo(folder: Folder) {
        val path = _folderPath.value!!.toMutableList()
        path.add(FolderNavEntry(folder.id, folder.name))
        _folderPath.value = path
        _currentFolderId.value = folder.id
    }

    fun navigateToIndex(index: Int) {
        val path = _folderPath.value!!.take(index + 1)
        _folderPath.value = path
        _currentFolderId.value = path.last().id
    }

    fun navigateUp(): Boolean {
        val path = _folderPath.value!!
        if (path.size <= 1) return false
        val newPath = path.dropLast(1)
        _folderPath.value = newPath
        _currentFolderId.value = newPath.last().id
        return true
    }

    fun isAtRoot() = (_folderPath.value?.size ?: 1) <= 1

    fun currentFolderId(): Long? = _currentFolderId.value

    fun insertMemo(memo: Memo, onResult: (Long) -> Unit = {}) = viewModelScope.launch {
        onResult(repo.insertMemo(memo))
    }

    suspend fun insertMemoSync(memo: Memo): Long = repo.insertMemo(memo)

    fun updateMemo(memo: Memo) = viewModelScope.launch { repo.updateMemo(memo) }

    suspend fun updateMemoSync(memo: Memo) = repo.updateMemo(memo)

    suspend fun updateMemosSync(memos: List<Memo>) = repo.updateMemos(memos)

    fun deleteMemo(memo: Memo) = viewModelScope.launch { repo.deleteMemo(memo) }

    suspend fun deleteMemoSync(memo: Memo) = repo.deleteMemo(memo)

    suspend fun deleteMemosSync(memos: List<Memo>) = repo.deleteMemos(memos)

    suspend fun getAllMemosSync(): List<Memo> = repo.getAllMemosSync()

    fun insertFolder(name: String) = viewModelScope.launch {
        repo.insertFolder(Folder(name = name, parentFolderId = _currentFolderId.value))
    }

    fun renameFolder(folder: Folder, name: String) = viewModelScope.launch {
        repo.updateFolder(folder.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    fun deleteFolder(folder: Folder) = viewModelScope.launch { repo.deleteFolder(folder) }

    suspend fun getAllFoldersSync(): List<Folder> = repo.getAllFoldersSync()

    suspend fun importAll(folders: List<Folder>, memos: List<Memo>): Int = repo.importAll(folders, memos)
}
