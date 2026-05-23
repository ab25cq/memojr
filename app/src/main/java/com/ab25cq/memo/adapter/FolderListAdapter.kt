package com.ab25cq.memo.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ab25cq.memo.data.Folder
import com.ab25cq.memo.databinding.ItemFolderBinding

class FolderListAdapter(
    private val onClick: (Folder) -> Unit,
    private val onLongClick: (Folder) -> Boolean
) : ListAdapter<Folder, FolderListAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Folder>() {
            override fun areItemsTheSame(a: Folder, b: Folder) = a.id == b.id
            override fun areContentsTheSame(a: Folder, b: Folder) = a == b
        }
    }

    inner class ViewHolder(val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = getItem(position)
        holder.binding.apply {
            tvFolderName.text = folder.name
            root.setOnClickListener { onClick(folder) }
            root.setOnLongClickListener { onLongClick(folder) }
        }
    }
}
