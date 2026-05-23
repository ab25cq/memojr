package com.ab25cq.memo.backup

import com.ab25cq.memo.data.Folder
import com.ab25cq.memo.data.Memo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.InputStream
import java.io.OutputStream

data class BackupData(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<Folder>? = null,
    val memos: List<Memo>
)

object BackupManager {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun export(folders: List<Folder>, memos: List<Memo>, outputStream: OutputStream) {
        val data = BackupData(folders = folders, memos = memos)
        outputStream.writer(Charsets.UTF_8).use { it.write(gson.toJson(data)) }
    }

    fun import(inputStream: InputStream): BackupData {
        val json = inputStream.reader(Charsets.UTF_8).use { it.readText() }
        return gson.fromJson(json, BackupData::class.java)
    }
}
