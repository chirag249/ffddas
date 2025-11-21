package com.example.ffddas

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class OpenCVHelper {
    
    companion object {
        private const val TAG = "OpenCVHelper"
        
        /**
         * Copy cascade file from assets to app's internal storage
         */
        fun copyCascadeFile(context: Context, cascadeName: String): File {
            Log.d(TAG, "Copying cascade file: $cascadeName")
            val cascadeDir = File(context.filesDir, "cascade")
            Log.d(TAG, "Cascade directory: ${cascadeDir.absolutePath}")
            if (!cascadeDir.exists()) {
                val created = cascadeDir.mkdirs()
                Log.d(TAG, "Created cascade directory: $created")
            }
            
            val cascadeFile = File(cascadeDir, cascadeName)
            Log.d(TAG, "Cascade file: ${cascadeFile.absolutePath}")
            
            try {
                // Check if file already exists
                if (cascadeFile.exists()) {
                    Log.d(TAG, "Cascade file already exists: ${cascadeFile.absolutePath}")
                    return cascadeFile
                }
                
                // Copy file from assets
                Log.d(TAG, "Copying cascade file from assets")
                val inputStream: InputStream = context.assets.open(cascadeName)
                val outputStream = FileOutputStream(cascadeFile)
                
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalBytesRead = 0
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                }
                
                inputStream.close()
                outputStream.close()
                
                Log.d(TAG, "Cascade file copied to: ${cascadeFile.absolutePath}, size: $totalBytesRead bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying cascade file: ${e.message}", e)
            }
            
            return cascadeFile
        }
        
        /**
         * Get the default face cascade file
         */
        fun getFaceCascadeFile(context: Context): File {
            Log.d(TAG, "Getting face cascade file")
            val result = copyCascadeFile(context, "haarcascade_frontalface_default.xml")
            Log.d(TAG, "Got face cascade file: ${result.absolutePath}")
            return result
        }
    }
}