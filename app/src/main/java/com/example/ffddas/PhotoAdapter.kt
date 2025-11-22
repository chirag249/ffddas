package com.example.ffddas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class PhotoAdapter(
    private val photoItems: List<PhotoItem>,
    private val onDeleteClick: (PhotoItem) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photoImageView: ImageView = view.findViewById(R.id.photoImageView)
        val photoNameText: TextView = view.findViewById(R.id.photoNameText)
        val deleteButton: Button = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoItem = photoItems[position]
        
        // Load image using Glide
        Glide.with(holder.photoImageView.context)
            .load(File(photoItem.path))
            .centerCrop()
            .into(holder.photoImageView)
        
        // Set photo name
        holder.photoNameText.text = photoItem.name
        
        // Set delete button click listener
        holder.deleteButton.setOnClickListener {
            onDeleteClick(photoItem)
        }
    }

    override fun getItemCount() = photoItems.size
}