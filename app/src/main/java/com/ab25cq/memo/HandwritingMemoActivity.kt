package com.ab25cq.memo

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ab25cq.memo.data.Memo
import com.ab25cq.memo.databinding.ActivityHandwritingMemoBinding
import com.ab25cq.memo.viewmodel.MemoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class HandwritingMemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHandwritingMemoBinding
    private val viewModel: MemoViewModel by viewModels()
    private var hasUnsavedChanges = false
    private var isSaving = false
    private var memoId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHandwritingMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.new_handwriting_memo)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupStrokeWidth()
        binding.btnClearHandwriting.setOnClickListener {
            binding.handwritingView.clear()
        }
        binding.handwritingView.onContentChanged = {
            hasUnsavedChanges = true
        }
        binding.etTitle.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                hasUnsavedChanges = true
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing && memoId == -1L && hasUnsavedChanges) {
            saveHandwritingMemo(finishAfterSave = false, showSavedMessage = false)
        }
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_handwriting, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_save -> { saveHandwritingMemo(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupStrokeWidth() {
        updateStrokeWidth(1)
        binding.seekStrokeWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateStrokeWidth(progress + 1)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun updateStrokeWidth(widthDp: Int) {
        binding.handwritingView.setStrokeWidthDp(widthDp)
        binding.tvStrokeWidth.text = getString(R.string.stroke_width_format, widthDp)
    }

    private fun saveHandwritingMemo(
        finishAfterSave: Boolean = true,
        showSavedMessage: Boolean = true
    ) {
        if (isSaving) return
        isSaving = true
        lifecycleScope.launch {
        runCatching {
            val bitmap = binding.handwritingView.exportBitmap()
            val base64 = withContext(Dispatchers.IO) {
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
            }
            val title = binding.etTitle.text.toString().trim()
            val folderId = intent.getLongExtra("folder_id", -1L).let { if (it == -1L) null else it }
            val content = "<img src=\"data:image/png;base64,$base64\" style=\"max-width:100%;height:auto\">"
            if (memoId == -1L) {
                memoId = viewModel.insertMemoSync(Memo(title = title, content = content, folderId = folderId))
            } else {
                viewModel.getAllMemosSync().find { it.id == memoId }?.let { memo ->
                    viewModel.updateMemoSync(
                        memo.copy(title = title, content = content, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        }.onSuccess {
            isSaving = false
            hasUnsavedChanges = false
            if (showSavedMessage) {
                Toast.makeText(this@HandwritingMemoActivity, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
            if (finishAfterSave) finish()
        }.onFailure {
            isSaving = false
            Toast.makeText(this@HandwritingMemoActivity, getString(R.string.handwriting_save_failed), Toast.LENGTH_SHORT).show()
        }
        }
    }
}
