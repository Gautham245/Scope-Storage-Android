package com.example.scopestorageandroid.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scopestorageandroid.databinding.ItemPhotoBinding
import com.example.scopestorageandroid.model.SharedStoragePhoto
import com.example.scopestorageandroid.ui.ImageOnClick

class ImageAdapter(val imageOnClick: (SharedStoragePhoto)-> Unit) : ListAdapter<SharedStoragePhoto, ImageAdapter.PhotoViewHolder>(Companion) {

    companion object: DiffUtil.ItemCallback<SharedStoragePhoto>() {
        override fun areItemsTheSame(
            oldItem: SharedStoragePhoto,
            newItem: SharedStoragePhoto
        ): Boolean {
            return oldItem.contentUri==newItem.contentUri
        }

        override fun areContentsTheSame(
            oldItem: SharedStoragePhoto,
            newItem: SharedStoragePhoto
        ): Boolean {
            return oldItem==newItem
        }
    }


    inner class PhotoViewHolder(val itemPhotoBinding: ItemPhotoBinding):RecyclerView.ViewHolder(itemPhotoBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val inflater=LayoutInflater.from(parent.context)
        return PhotoViewHolder(ItemPhotoBinding.inflate(inflater,parent,false))
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo=getItem(position)
        holder.itemPhotoBinding.apply {
            image.setImageURI(photo.contentUri)
            image.setOnClickListener {
                imageOnClick(photo)
            }
        }

    }
}