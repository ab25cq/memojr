package com.ab25cq.memo

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import com.ab25cq.memo.adapter.FolderListAdapter
import com.ab25cq.memo.adapter.MemoListAdapter
import com.ab25cq.memo.backup.BackupManager
import com.ab25cq.memo.data.Folder
import com.ab25cq.memo.data.Memo
import com.ab25cq.memo.databinding.ActivityMainBinding
import com.ab25cq.memo.viewmodel.FolderNavEntry
import com.ab25cq.memo.viewmodel.MemoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val MENU_COPY_SELECTED = 1001
private const val MENU_MOVE_SELECTED = 1002
private const val MENU_DELETE_SELECTED = 1003
private const val MENU_CLEAR_SELECTION = 1004
private const val MENU_COPY_TO_FOLDER_SELECTED = 1005

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MemoViewModel by viewModels()

    private lateinit var folderAdapter: FolderListAdapter
    private lateinit var memoAdapter: MemoListAdapter
    private val selectedMemoIds = mutableSetOf<Long>()
    private var currentMemoSnapshot: List<Memo> = emptyList()
    private var lastSelectionCount = 0

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportBackup(it) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importBackup(it) }
    }
    private val imageMemoGalleryPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        createImageMemosFromFiles(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupAdapters()
        setupFab()
        setupBackPress()

        viewModel.currentFolders.observe(this) { folders ->
            folderAdapter.submitList(folders)
            updateEmptyView()
        }
        viewModel.currentMemos.observe(this) { memos ->
            currentMemoSnapshot = memos
            val visibleMemoIds = memos.map { it.id }.toSet()
            val selectionChanged = selectedMemoIds.retainAll(visibleMemoIds)
            memoAdapter.submitList(memos)
            memoAdapter.setSelectedIds(selectedMemoIds)
            if (selectionChanged) updateSelectionChrome()
            updateEmptyView()
        }
        viewModel.folderPath.observe(this) { path ->
            updateBreadcrumb(path)
        }
    }

    private fun setupAdapters() {
        folderAdapter = FolderListAdapter(
            onClick = { folder -> viewModel.navigateTo(folder) },
            onLongClick = { folder ->
                val options = arrayOf(getString(R.string.rename_folder), getString(R.string.delete_folder))
                AlertDialog.Builder(this)
                    .setTitle(folder.name)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> showRenameFolderDialog(folder)
                            1 -> confirmDeleteFolder(folder)
                        }
                    }
                    .show()
                true
            }
        )

        memoAdapter = MemoListAdapter(
            onClick = { memo ->
                if (isMemoSelectionActive()) {
                    toggleMemoSelection(memo)
                } else {
                    startActivity(Intent(this, MemoViewActivity::class.java).putExtra("memo_id", memo.id))
                }
            },
            onLongClick = { memo ->
                toggleMemoSelection(memo)
                true
            }
        )

        binding.recyclerView.adapter = ConcatAdapter(folderAdapter, memoAdapter)
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            val intent = Intent(this, MemoEditActivity::class.java)
            viewModel.currentFolderId()?.let { id -> intent.putExtra("folder_id", id) }
            startActivity(intent)
        }
        binding.fabImage.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.create_image_memo))
                .setItems(arrayOf(getString(R.string.pick_device_images))) { _, _ ->
                    imageMemoGalleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                .show()
        }
        binding.fabHandwriting.setOnClickListener {
            val intent = Intent(this, HandwritingMemoActivity::class.java)
            viewModel.currentFolderId()?.let { id -> intent.putExtra("folder_id", id) }
            startActivity(intent)
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isMemoSelectionActive()) {
                    clearMemoSelection()
                } else if (!viewModel.navigateUp()) {
                    finish()
                }
            }
        })
    }

    private fun isMemoSelectionActive() = selectedMemoIds.isNotEmpty()

    private fun toggleMemoSelection(memo: Memo) {
        if (!selectedMemoIds.add(memo.id)) selectedMemoIds.remove(memo.id)
        updateSelectionUi()
    }

    private fun clearMemoSelection() {
        if (selectedMemoIds.isEmpty()) return
        selectedMemoIds.clear()
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        memoAdapter.setSelectedIds(selectedMemoIds)
        updateSelectionChrome()
    }

    private fun updateSelectionChrome() {
        val selectionCount = selectedMemoIds.size
        supportActionBar?.title = if (isMemoSelectionActive()) {
            getString(R.string.selected_memo_count, selectionCount)
        } else {
            getString(R.string.app_name)
        }
        if (lastSelectionCount != selectionCount) {
            lastSelectionCount = selectionCount
            invalidateOptionsMenu()
        }
    }

    private fun updateEmptyView() {
        val isEmpty = (folderAdapter.itemCount == 0) && (memoAdapter.itemCount == 0)
        binding.tvEmpty.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateBreadcrumb(path: List<FolderNavEntry>) {
        val container = binding.breadcrumbContainer
        container.removeAllViews()
        path.forEachIndexed { index, entry ->
            if (index > 0) {
                val sep = TextView(this).apply {
                    text = " › "
                    setTextColor(Color.parseColor("#B0BEC5"))
                    textSize = 14f
                }
                container.addView(sep)
            }
            val tv = TextView(this).apply {
                text = entry.name
                setTextColor(Color.WHITE)
                textSize = 14f
                if (index == path.lastIndex) setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { viewModel.navigateToIndex(index) }
            }
            container.addView(tv)
        }
        binding.breadcrumbScroll.post {
            binding.breadcrumbScroll.fullScroll(android.view.View.FOCUS_RIGHT)
        }
    }

    private fun showNewFolderDialog() {
        val et = EditText(this).apply {
            hint = getString(R.string.folder_name_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.new_folder))
            .setView(et)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) viewModel.insertFolder(name)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRenameFolderDialog(folder: Folder) {
        val et = EditText(this).apply {
            setText(folder.name)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename_folder))
            .setView(et)
            .setPositiveButton(getString(R.string.rename)) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) viewModel.renameFolder(folder, name)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDeleteFolder(folder: Folder) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_folder))
            .setMessage(getString(R.string.confirm_delete_folder_msg, folder.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> viewModel.deleteFolder(folder) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDeleteMemo(memo: Memo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_memo_title))
            .setMessage(getString(R.string.confirm_delete_memo_msg, memo.title.ifEmpty { getString(R.string.untitled) }))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> viewModel.deleteMemo(memo) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showMoveMemoDialog(memo: Memo) = lifecycleScope.launch {
        val allFolders = viewModel.getAllFoldersSync()
        val items = mutableListOf(getString(R.string.root_folder))
        items.addAll(allFolders.map { it.name })
        AlertDialog.Builder(this@MainActivity)
            .setTitle(getString(R.string.move_memo))
            .setItems(items.toTypedArray()) { _, which ->
                val targetFolderId = if (which == 0) null else allFolders[which - 1].id
                viewModel.updateMemo(memo.copy(folderId = targetFolderId, updatedAt = System.currentTimeMillis()))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        if (isMemoSelectionActive()) {
            menu.add(Menu.NONE, MENU_MOVE_SELECTED, Menu.NONE, R.string.move_memo)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(Menu.NONE, MENU_COPY_TO_FOLDER_SELECTED, Menu.NONE, R.string.copy_to_folder)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.add(Menu.NONE, MENU_COPY_SELECTED, Menu.NONE, R.string.copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.add(Menu.NONE, MENU_DELETE_SELECTED, Menu.NONE, R.string.delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(Menu.NONE, MENU_CLEAR_SELECTION, Menu.NONE, R.string.cancel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        } else {
            menuInflater.inflate(R.menu.menu_main, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_MOVE_SELECTED -> { showMoveSelectedMemosDialog(); true }
            MENU_COPY_TO_FOLDER_SELECTED -> { showCopySelectedMemosToFolderDialog(); true }
            MENU_COPY_SELECTED -> { copySelectedMemos(); true }
            MENU_DELETE_SELECTED -> { confirmDeleteSelectedMemos(); true }
            MENU_CLEAR_SELECTION -> { clearMemoSelection(); true }
            R.id.action_new_folder -> { showNewFolderDialog(); true }
            R.id.action_export -> {
                val name = "memojr_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                exportLauncher.launch(name)
                true
            }
            R.id.action_import -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.import_backup))
                    .setMessage(getString(R.string.import_backup_message))
                    .setPositiveButton(getString(R.string.select)) { _, _ -> importLauncher.launch(arrayOf("application/json")) }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun selectedMemos(): List<Memo> {
        return currentMemoSnapshot.filter { selectedMemoIds.contains(it.id) }
    }

    private fun copySelectedMemos() = lifecycleScope.launch {
        val memos = selectedMemos()
        clearMemoSelection()
        memos.forEach { memo ->
            val now = System.currentTimeMillis()
            viewModel.insertMemoSync(
                memo.copy(
                    id = 0,
                    title = getString(R.string.copy_title_format, memo.title.ifEmpty { getString(R.string.untitled) }),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        Toast.makeText(this@MainActivity, getString(R.string.copy_memo_success, memos.size), Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteSelectedMemos() {
        val memos = selectedMemos()
        if (memos.isEmpty()) {
            clearMemoSelection()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_memo_title))
            .setMessage(getString(R.string.confirm_delete_selected_memos_msg, memos.size))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    clearMemoSelection()
                    viewModel.deleteMemosSync(memos)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showMoveSelectedMemosDialog() = lifecycleScope.launch {
        val memos = selectedMemos()
        if (memos.isEmpty()) {
            clearMemoSelection()
            return@launch
        }
        val allFolders = viewModel.getAllFoldersSync()
        val items = mutableListOf(getString(R.string.root_folder))
        items.addAll(allFolders.map { it.name })
        AlertDialog.Builder(this@MainActivity)
            .setTitle(getString(R.string.move_memo))
            .setItems(items.toTypedArray()) { _, which ->
                val targetFolderId = if (which == 0) null else allFolders[which - 1].id
                lifecycleScope.launch {
                    clearMemoSelection()
                    val now = System.currentTimeMillis()
                    viewModel.updateMemosSync(memos.map { it.copy(folderId = targetFolderId, updatedAt = now) })
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showCopySelectedMemosToFolderDialog() = lifecycleScope.launch {
        val memos = selectedMemos()
        if (memos.isEmpty()) {
            clearMemoSelection()
            return@launch
        }
        val allFolders = viewModel.getAllFoldersSync()
        val items = mutableListOf(getString(R.string.root_folder))
        items.addAll(allFolders.map { it.name })
        AlertDialog.Builder(this@MainActivity)
            .setTitle(getString(R.string.copy_to_folder))
            .setItems(items.toTypedArray()) { _, which ->
                val targetFolderId = if (which == 0) null else allFolders[which - 1].id
                lifecycleScope.launch {
                    clearMemoSelection()
                    memos.forEach { memo ->
                        val now = System.currentTimeMillis()
                        viewModel.insertMemoSync(
                            memo.copy(
                                id = 0,
                                folderId = targetFolderId,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.copy_to_folder_success, memos.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "image"
    }

    private fun getTitleFromFileName(fileName: String): String {
        return fileName.ifBlank { "image" }
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        if (src.width <= maxDim && src.height <= maxDim) return src
        val scale = minOf(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    }

    private suspend fun insertImageMemo(uri: Uri, fileName: String, folderId: Long?): Long = withContext(Dispatchers.IO) {
        val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: error("Could not decode image")
        val scaled = scaleBitmap(bitmap, 1280)
        val out = ByteArrayOutputStream()
        scaled.compress(WEBP_FORMAT, 75, out)
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        val content = "<img src=\"data:image/webp;base64,$base64\" style=\"max-width:100%;height:auto\">"
        val memo = Memo(title = getTitleFromFileName(fileName), content = content, folderId = folderId)
        viewModel.insertMemoSync(memo)
    }

    private fun createImageMemo(uri: Uri) = lifecycleScope.launch {
        runCatching {
            val fileName = getFileName(uri)
            val folderId = viewModel.currentFolderId()
            val id = insertImageMemo(uri, fileName, folderId)
            startActivity(Intent(this@MainActivity, MemoViewActivity::class.java).putExtra("memo_id", id))
        }.onFailure {
            Toast.makeText(this@MainActivity, getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageMemosFromFiles(uris: List<Uri>) = lifecycleScope.launch {
        runCatching {
            val folderId = viewModel.currentFolderId()
            var imported = 0
            uris.forEach { uri ->
                runCatching {
                    insertImageMemo(uri, getFileName(uri), folderId)
                    imported++
                }
            }
            showImageImportResult(imported)
        }.onFailure {
            Toast.makeText(this@MainActivity, getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImageImportResult(imported: Int) {
        if (imported > 0) {
            Toast.makeText(this, getString(R.string.image_folder_import_success, imported), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.image_folder_import_empty), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportBackup(uri: Uri) = lifecycleScope.launch {
        runCatching {
            val memos = viewModel.getAllMemosSync()
            val folders = viewModel.getAllFoldersSync()
            contentResolver.openOutputStream(uri)!!.use { BackupManager.export(folders, memos, it) }
            Toast.makeText(this@MainActivity, getString(R.string.export_success, memos.size), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this@MainActivity, getString(R.string.export_failed, it.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun importBackup(uri: Uri) = lifecycleScope.launch {
        runCatching {
            val data = contentResolver.openInputStream(uri)!!.use { BackupManager.import(it) }
            val folders = data.folders ?: emptyList()
            val imported = viewModel.importAll(folders, data.memos)
            val skipped = data.memos.size - imported
            val msg = if (skipped > 0) getString(R.string.import_success_skipped, imported, skipped)
                      else getString(R.string.import_success, imported)
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this@MainActivity, getString(R.string.import_failed, it.message), Toast.LENGTH_LONG).show()
        }
    }
}
