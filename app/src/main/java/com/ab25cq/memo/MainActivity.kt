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
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MemoViewModel by viewModels()

    private lateinit var folderAdapter: FolderListAdapter
    private lateinit var memoAdapter: MemoListAdapter

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportBackup(it) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importBackup(it) }
    }
    private val imageMemoGalleryPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { createImageMemo(it) }
    }
    private val imageMemoFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { createImageMemo(it) }
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
            memoAdapter.submitList(memos)
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
                startActivity(Intent(this, MemoViewActivity::class.java).putExtra("memo_id", memo.id))
            },
            onLongClick = { memo ->
                val options = arrayOf(getString(R.string.delete), getString(R.string.move_memo))
                val title = getString(R.string.item_title_format, memo.title.ifEmpty { getString(R.string.untitled) })
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> confirmDeleteMemo(memo)
                            1 -> showMoveMemoDialog(memo)
                        }
                    }
                    .show()
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
                .setItems(arrayOf(getString(R.string.pick_gallery), getString(R.string.pick_file))) { _, which ->
                    when (which) {
                        0 -> imageMemoGalleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        1 -> imageMemoFilePicker.launch(arrayOf("image/*"))
                    }
                }
                .show()
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!viewModel.navigateUp()) finish()
            }
        })
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
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "image"
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        if (src.width <= maxDim && src.height <= maxDim) return src
        val scale = minOf(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    }

    private fun createImageMemo(uri: Uri) = lifecycleScope.launch {
        runCatching {
            val fileName = getFileName(uri).substringBeforeLast(".")
            val stream = contentResolver.openInputStream(uri)!!
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            val scaled = scaleBitmap(bitmap, 1280)
            val out = ByteArrayOutputStream()
            scaled.compress(WEBP_FORMAT, 75, out)
            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            val content = "<img src=\"data:image/webp;base64,$base64\" style=\"max-width:100%;height:auto\">"
            val folderId = viewModel.currentFolderId()
            val memo = Memo(title = fileName, content = content, folderId = folderId)
            viewModel.insertMemo(memo) { id ->
                startActivity(Intent(this@MainActivity, MemoViewActivity::class.java).putExtra("memo_id", id))
            }
        }.onFailure {
            Toast.makeText(this@MainActivity, getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show()
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
