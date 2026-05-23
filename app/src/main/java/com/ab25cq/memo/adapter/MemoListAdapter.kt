package com.ab25cq.memo.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ab25cq.memo.R
import com.ab25cq.memo.data.Memo
import com.ab25cq.memo.databinding.ItemMemoBinding
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MemoListAdapter(
    private val onClick: (Memo) -> Unit,
    private val onLongClick: (Memo) -> Boolean
) : ListAdapter<Memo, MemoListAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Memo>() {
            override fun areItemsTheSame(a: Memo, b: Memo) = a.id == b.id
            override fun areContentsTheSame(a: Memo, b: Memo) = a == b
        }
        private val DATE_FMT = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        private val IMG_SRC_RE = Regex("""src="data:image/[^;]+;base64,([^"]+)"""")
        private const val THUMB_PX = 128
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val thumbCache = HashMap<Long, Bitmap?>()

    inner class ViewHolder(val binding: ItemMemoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemMemoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val memo = getItem(position)
        holder.binding.apply {
            tvTitle.text = memo.title.ifEmpty { "(無題)" }
            tvDate.text  = DATE_FMT.format(Date(memo.updatedAt))
            root.setOnClickListener { onClick(memo) }
            root.setOnLongClickListener { onLongClick(memo) }

            if (memo.content.contains("base64,")) {
                // ── 画像あり: サムネイル表示、テキストプレビュー非表示 ──
                tvPreview.visibility = View.GONE
                imgThumb.visibility  = View.VISIBLE
                imgThumb.tag = memo.id

                val cached = thumbCache[memo.id]
                if (cached != null) {
                    imgThumb.setImageBitmap(cached)
                } else {
                    imgThumb.setImageResource(R.drawable.ic_image)
                    scope.launch {
                        val bmp = withContext(Dispatchers.IO) { decodeThumbnail(memo.content) }
                        thumbCache[memo.id] = bmp
                        if (imgThumb.tag == memo.id && bmp != null) {
                            imgThumb.setImageBitmap(bmp)
                        }
                    }
                }
            } else {
                // ── テキストメモ: プレビュー表示 ──
                imgThumb.visibility  = View.GONE
                tvPreview.visibility = View.VISIBLE
                tvPreview.text = Html.fromHtml(memo.content, Html.FROM_HTML_MODE_COMPACT)
                    .toString().trim().take(120)
            }
        }
    }

    private fun decodeThumbnail(content: String): Bitmap? = runCatching {
        val b64 = IMG_SRC_RE.find(content)?.groupValues?.get(1) ?: return null
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var s = 1
        while (opts.outWidth / (s * 2) >= THUMB_PX && opts.outHeight / (s * 2) >= THUMB_PX) s *= 2
        opts.inSampleSize = s
        opts.inJustDecodeBounds = false
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()
}
