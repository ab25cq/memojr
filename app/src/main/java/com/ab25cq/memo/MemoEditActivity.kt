package com.ab25cq.memo

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.webkit.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ab25cq.memo.data.Memo
import com.ab25cq.memo.databinding.ActivityMemoEditBinding
import com.ab25cq.memo.viewmodel.MemoViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MemoEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMemoEditBinding
    private val viewModel: MemoViewModel by viewModels()
    private var memoId: Long = -1
    private var hasUnsavedChanges = false
    private val editorBridge = EditorBridge()
    private var isPageLoaded = false
    private var pendingCropIndex = -1
    private var isSaving = false

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { insertImageFromUri(it) }
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { insertImageFromUri(it) }
    }
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("cropped_path") ?: return@registerForActivityResult
            val idx = pendingCropIndex
            if (idx >= 0) {
                lifecycleScope.launch {
                    runCatching {
                        val bytes = File(path).readBytes()
                        val newSrc = "data:image/webp;base64," +
                                Base64.encodeToString(bytes, Base64.NO_WRAP)
                        editorBridge.setPendingImage(newSrc)
                        binding.webEditor.evaluateJavascript(
                            "updateImageAtIndex($idx, Android.consumePendingImage())", null)
                        hasUnsavedChanges = true
                    }
                }
            }
        }
        pendingCropIndex = -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // IME インセットをアプリ側で受け取るために必要
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMemoEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // キーボード高さ分だけ root の bottom padding を増やす → WebView(weight=1)がリサイズ・再描画
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime  = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            if (ime.bottom > bars.bottom) {
                // WebView のリサイズ・再描画が完了してから JS スクロールを実行
                // WebView の実ピクセル高を dp (CSS px) に変換して JS へ渡す
                binding.webEditor.postDelayed({
                    val vh = (binding.webEditor.height / resources.displayMetrics.density).toInt()
                    binding.webEditor.evaluateJavascript("scrollCaretIntoView($vh)", null)
                }, 400)
            }
            WindowInsetsCompat.CONSUMED
        }

        memoId = intent.getLongExtra("memo_id", -1)
        setupWebView()
        setupToolbar()

        if (memoId != -1L) {
            lifecycleScope.launch {
                viewModel.getAllMemosSync().find { it.id == memoId }?.let { memo ->
                    binding.etTitle.setText(memo.title)
                    editorBridge.setPendingContent(memo.content)
                    if (isPageLoaded) {
                        binding.webEditor.evaluateJavascript(JS_LOAD_CONTENT, null)
                    }
                    hasUnsavedChanges = false
                }
            }
        }

        binding.etTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { hasUnsavedChanges = true }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges) {
                    AlertDialog.Builder(this@MemoEditActivity)
                        .setTitle(getString(R.string.discard_changes))
                        .setMessage(getString(R.string.discard_message))
                        .setPositiveButton(getString(R.string.discard)) { _, _ -> finish() }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing && memoId == -1L && hasUnsavedChanges) {
            saveMemo(finishAfterSave = false, showSavedMessage = false)
        }
        super.onStop()
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webEditor.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            addJavascriptInterface(editorBridge, "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    isPageLoaded = true
                    view.evaluateJavascript(JS_LOAD_CONTENT, null)
                    hasUnsavedChanges = false
                    view.postDelayed({
                        view.requestFocus()
                        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                            .showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }, 400)
                }
            }
            loadUrl("file:///android_asset/editor.html")
        }
    }

    private fun setupToolbar() {
        binding.btnUndo.setOnClickListener { undoEdit() }
        binding.btnBold.setOnClickListener { execFormat("bold") }
        binding.btnItalic.setOnClickListener { execFormat("italic") }
        binding.btnUnderline.setOnClickListener { execFormat("underline") }
        binding.btnH1.setOnClickListener { execFormat("formatBlock", "h2") }
        binding.btnUl.setOnClickListener { execFormat("insertUnorderedList") }
        binding.btnOl.setOnClickListener { execFormat("insertOrderedList") }
        binding.btnCode.setOnClickListener { execFormat("formatBlock", "pre") }
        binding.btnMath.setOnClickListener {
            MathWizard.show(
                context = this,
                onInsert = { latex, isDisplay ->
                    binding.webEditor.evaluateJavascript("insertMath(${latex.toJsString()}, $isDisplay)", null)
                    hasUnsavedChanges = true
                },
                onDirect = { showMathDialog() }
            )
        }
        binding.btnImage.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.insert_image))
                .setItems(arrayOf(getString(R.string.pick_gallery), getString(R.string.pick_file))) { _, which ->
                    when (which) {
                        0 -> galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        1 -> filePicker.launch(arrayOf("image/*"))
                    }
                }
                .show()
        }
        binding.btnClear.setOnClickListener { execFormat("removeFormat") }
        binding.btnPageUp.setOnClickListener {
            binding.webEditor.scrollBy(0, -(binding.webEditor.height * 3 / 4))
        }
        binding.btnPageDown.setOnClickListener {
            binding.webEditor.scrollBy(0, binding.webEditor.height * 3 / 4)
        }
    }

    private fun undoEdit() {
        binding.webEditor.evaluateJavascript("document.execCommand('undo')") { result ->
            if (result == "true") {
                hasUnsavedChanges = true
            }
        }
        binding.webEditor.requestFocus()
    }

    private fun execFormat(cmd: String, arg: String = "") {
        val js = if (arg.isEmpty()) "document.execCommand('$cmd')"
                 else "document.execCommand('$cmd', false, '$arg')"
        binding.webEditor.evaluateJavascript(js, null)
        binding.webEditor.requestFocus()
    }

    private fun showMathDialog() {
        val et = EditText(this).apply {
            hint = getString(R.string.math_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.insert_math))
            .setView(et)
            .setPositiveButton(getString(R.string.math_inline)) { _, _ ->
                val latex = et.text.toString().trim()
                if (latex.isNotEmpty()) {
                    binding.webEditor.evaluateJavascript("insertMath(${latex.toJsString()}, false)", null)
                    hasUnsavedChanges = true
                }
            }
            .setNeutralButton(getString(R.string.math_block)) { _, _ ->
                val latex = et.text.toString().trim()
                if (latex.isNotEmpty()) {
                    binding.webEditor.evaluateJavascript("insertMath(${latex.toJsString()}, true)", null)
                    hasUnsavedChanges = true
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun insertImageFromUri(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val stream = contentResolver.openInputStream(uri)!!
                val bitmap = BitmapFactory.decodeStream(stream)
                stream.close()
                val scaled = scaleBitmap(bitmap, MAX_IMAGE_DIM)
                val out = ByteArrayOutputStream()
                scaled.compress(WEBP_FORMAT, IMAGE_QUALITY, out)
                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                // Pass data URL through bridge — avoids huge JS string literals
                editorBridge.setPendingImage("data:image/webp;base64,$base64")
                binding.webEditor.evaluateJavascript("insertPendingImage()", null)
                hasUnsavedChanges = true
            }.onFailure {
                Toast.makeText(this@MemoEditActivity, getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 画像編集 ──

    private fun getImageSrc(index: Int, callback: (String) -> Unit) {
        binding.webEditor.evaluateJavascript("getImageSrcAtIndex($index)") { raw ->
            val src = if (raw.startsWith("\"") && raw.endsWith("\""))
                raw.substring(1, raw.length - 1) else raw
            if (src.isNotEmpty() && src != "null" && src.contains("base64,")) callback(src)
        }
    }

    fun showImageEditDialog(index: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_image))
            .setItems(arrayOf(getString(R.string.rotate_90), getString(R.string.resize), getString(R.string.crop))) { _, which ->
                when (which) {
                    0 -> processImage(index) { bmp ->
                        val m = Matrix().apply { postRotate(90f) }
                        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    }
                    1 -> showResizeDialog(index)
                    2 -> startCrop(index)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun processImage(index: Int, transform: (Bitmap) -> Bitmap) {
        getImageSrc(index) { src ->
            lifecycleScope.launch {
                runCatching {
                    val bytes = Base64.decode(src.substringAfter("base64,"), Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val out = ByteArrayOutputStream()
                    transform(bmp).compress(WEBP_FORMAT, IMAGE_QUALITY, out)
                    val newSrc = "data:image/webp;base64," +
                            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    editorBridge.setPendingImage(newSrc)
                    binding.webEditor.evaluateJavascript(
                        "updateImageAtIndex($index, Android.consumePendingImage())", null)
                    hasUnsavedChanges = true
                }.onFailure {
                    Toast.makeText(this@MemoEditActivity, getString(R.string.image_process_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showResizeDialog(index: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.resize))
            .setItems(arrayOf("25%", "50%", "75%", getString(R.string.resize_custom_px))) { _, which ->
                when (which) {
                    0, 1, 2 -> {
                        val pct = listOf(25, 50, 75)[which]
                        processImage(index) { bmp ->
                            val w = (bmp.width * pct / 100).coerceAtLeast(1)
                            val h = (bmp.height * pct / 100).coerceAtLeast(1)
                            Bitmap.createScaledBitmap(bmp, w, h, true)
                        }
                    }
                    3 -> {
                        val et = EditText(this).apply {
                            inputType = InputType.TYPE_CLASS_NUMBER
                            hint = getString(R.string.width_px_hint)
                            setPadding(48, 32, 48, 32)
                        }
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.set_width_title))
                            .setView(et)
                            .setPositiveButton(getString(R.string.resize)) { _, _ ->
                                val w = et.text.toString().toIntOrNull() ?: return@setPositiveButton
                                if (w > 0) processImage(index) { bmp ->
                                    val h = (bmp.height.toLong() * w / bmp.width).toInt().coerceAtLeast(1)
                                    Bitmap.createScaledBitmap(bmp, w, h, true)
                                }
                            }
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun startCrop(index: Int) {
        getImageSrc(index) { src ->
            lifecycleScope.launch {
                runCatching {
                    val bytes = Base64.decode(src.substringAfter("base64,"), Base64.NO_WRAP)
                    val tempFile = File(cacheDir, "crop_input.webp")
                    tempFile.writeBytes(bytes)
                    pendingCropIndex = index
                    cropLauncher.launch(
                        Intent(this@MemoEditActivity, CropActivity::class.java)
                            .putExtra("image_path", tempFile.absolutePath)
                    )
                }.onFailure {
                    Toast.makeText(this@MemoEditActivity, getString(R.string.crop_start_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        if (src.width <= maxDim && src.height <= maxDim) return src
        val scale = minOf(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt(),
            (src.height * scale).toInt(),
            true
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_save -> { saveMemo(); true }
            R.id.action_preview -> { previewMemo(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveMemo(finishAfterSave: Boolean = true, showSavedMessage: Boolean = true) {
        if (isSaving || !isPageLoaded) return
        isSaving = true
        binding.webEditor.evaluateJavascript("getContent()") { rawContent ->
            val content = rawContent.run {
                if (startsWith("\"") && endsWith("\"")) substring(1, length - 1) else this
            }.replace("\\u003C", "<").replace("\\u003E", ">").replace("\\\"", "\"").replace("\\n", "\n")
             .replace("\\\\", "\\")

            val title = binding.etTitle.text.toString().trim()
            lifecycleScope.launch {
                if (memoId == -1L) {
                    val folderId = intent.getLongExtra("folder_id", -1L).let { if (it == -1L) null else it }
                    val newMemo = Memo(title = title, content = content, folderId = folderId)
                    memoId = viewModel.insertMemoSync(newMemo)
                } else {
                    val existing = viewModel.getAllMemosSync().find { it.id == memoId }
                    existing?.let {
                        viewModel.updateMemoSync(it.copy(title = title, content = content, updatedAt = System.currentTimeMillis()))
                    }
                }
                hasUnsavedChanges = false
                isSaving = false
                if (showSavedMessage) {
                    Toast.makeText(this@MemoEditActivity, getString(R.string.saved), Toast.LENGTH_SHORT).show()
                }
                if (finishAfterSave) finish()
            }
        }
    }

    private fun previewMemo() {
        binding.webEditor.evaluateJavascript("getContent()") { rawContent ->
            val content = rawContent.run {
                if (startsWith("\"") && endsWith("\"")) substring(1, length - 1) else this
            }.replace("\\u003C", "<").replace("\\u003E", ">").replace("\\\"", "\"").replace("\\n", "\n")
             .replace("\\\\", "\\")

            val intent = android.content.Intent(this, MemoViewActivity::class.java)
            intent.putExtra("preview_title", binding.etTitle.text.toString())
            intent.putExtra("preview_content", content)
            startActivity(intent)
        }
    }

    inner class EditorBridge {
        private val _pendingContent = AtomicReference("")
        private val _pendingImage   = AtomicReference("")

        fun setPendingContent(content: String) { _pendingContent.set(content) }
        fun setPendingImage(dataUrl: String)   { _pendingImage.set(dataUrl) }

        /** Called from JS on page load — returns content and clears it atomically. */
        @JavascriptInterface
        fun consumeInitialContent(): String = _pendingContent.getAndSet("")

        /** Called from JS insertPendingImage() — returns data URL and clears it. */
        @JavascriptInterface
        fun consumePendingImage(): String = _pendingImage.getAndSet("")

        @JavascriptInterface
        fun onChanged() { hasUnsavedChanges = true }

        @JavascriptInterface
        fun onImageLongPress(index: Int) {
            runOnUiThread { showImageEditDialog(index) }
        }
    }

    private fun String.toJsString(): String {
        val escaped = this
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "'$escaped'"
    }

    companion object {
        private const val MAX_IMAGE_DIM = 1280
        private const val IMAGE_QUALITY = 75
        private const val JS_LOAD_CONTENT =
            "(function(){var c=Android.consumeInitialContent();if(c){setContent(c);window.scrollTo(0,document.body.scrollHeight);}editor.focus();})()"
    }
}
