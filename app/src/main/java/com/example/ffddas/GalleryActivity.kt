package com.example.ffddas

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ffddas.databinding.ActivityGalleryBinding
import java.io.File

class GalleryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var photoAdapter: PhotoAdapter
    private val photoItems = mutableListOf<PhotoItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        loadPhotos()
    }
    
    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(photoItems) { photoItem ->
            deletePhoto(photoItem)
        }
        
        binding.photosRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GalleryActivity)
            adapter = photoAdapter
        }
    }
    
    private fun loadPhotos() {
        val photoDir = getOutputDirectory()
        if (photoDir.exists() && photoDir.isDirectory) {
            val photos = photoDir.listFiles { file -> 
                file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") || file.name.endsWith(".png")
            }
            
            if (photos != null) {
                photoItems.clear()
                photos.forEach { file ->
                    photoItems.add(PhotoItem(file, file.name, file.absolutePath))
                }
                
                photoAdapter.notifyDataSetChanged()
                
                if (photoItems.isEmpty()) {
                    binding.noPhotosText.visibility = android.view.View.VISIBLE
                } else {
                    binding.noPhotosText.visibility = android.view.View.GONE
                }
                
                Log.d(TAG, "Loaded ${photoItems.size} photos")
            }
        }
    }
    
    private fun deletePhoto(photoItem: PhotoItem) {
        val file = File(photoItem.path)
        if (file.exists()) {
            if (file.delete()) {
                photoItems.remove(photoItem)
                photoAdapter.notifyDataSetChanged()
                
                if (photoItems.isEmpty()) {
                    binding.noPhotosText.visibility = android.view.View.VISIBLE
                }
                
                Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Photo deleted: ${photoItem.path}")
            } else {
                Toast.makeText(this, "Failed to delete photo", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to delete photo: ${photoItem.path}")
            }
        }
    }
    
    private fun getOutputDirectory(): File {
        // Use the same directory logic as MainActivity
        val mediaDir = getExternalMediaDirs().firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }
    
    companion object {
        private const val TAG = "GalleryActivity"
    }
}