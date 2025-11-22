package com.example.ffddas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpenCVImageAnalyzer(
    private val opencvProcessor: OpenCVProcessor, 
    private val onFrameProcessed: (Bitmap) -> Unit,
    private val minFrameInterval: Long = 150 // Configurable frame interval in milliseconds
) : ImageAnalysis.Analyzer {

    // Frame rate control - process every N milliseconds
    private var lastProcessedTime: Long = 0

    override fun analyze(image: ImageProxy) {
        try {
            Log.d(TAG, "Analyzing image")
            
            // Frame rate control - check if enough time has passed since last processing
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessedTime < minFrameInterval) {
                Log.d(TAG, "Skipping frame to control frame rate")
                image.close()
                return
            }
            
            // For live preview, use Kotlin OpenCV processor which respects filter state
            // Native methods don't have filter context, so use fallback path
            val bitmap = imageToBitmap(image)
            
            // Check if bitmap is valid
            if (bitmap == null || bitmap.isRecycled) {
                Log.e(TAG, "Failed to convert image to valid bitmap")
                image.close()
                return
            }
            
            // Process the bitmap with OpenCV processor (respects filter selection)
            val processedBitmap = opencvProcessor.processImage(bitmap)
            
            // Check if processed bitmap is valid
            if (processedBitmap == null || processedBitmap.isRecycled) {
                Log.e(TAG, "Failed to process image with OpenCV")
                image.close()
                return
            }
            
            // Update last processed time
            lastProcessedTime = currentTime
            
            // Notify that frame has been processed
            onFrameProcessed(processedBitmap)
            
            // Close the image to free resources
            image.close()
            Log.d(TAG, "Image analysis completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image", e)
            try {
                image.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "Error closing image", closeException)
            }
        }
    }

    private fun imageToBitmap(image: ImageProxy): Bitmap? {
        try {
            Log.d(TAG, "Converting image to bitmap")
            val planes = image.planes
            val yBuffer = planes[0].buffer // Y
            val uBuffer = planes[1].buffer // U
            val vBuffer = planes[2].buffer // V

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // U and V are swapped
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            // Increased JPEG quality to reduce compression artifacts that can affect face detection
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 80, out)
            val imageBytes = out.toByteArray()
            Log.d(TAG, "Image converted to bitmap")
            var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            // Check if bitmap is valid
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from image bytes")
                return null
            }
            
            // Handle image rotation based on imageInfo
            val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
            Log.d(TAG, "Image rotation degrees: $rotationDegrees")
            
            // Only rotate if needed
            if (rotationDegrees != 0f) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees)
                
                // Create rotated bitmap
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                
                // Recycle original bitmap if it's different from rotated one
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }
                
                Log.d(TAG, "Bitmap rotated, new size: ${rotatedBitmap.width}x${rotatedBitmap.height}")
                return rotatedBitmap
            }
            
            Log.d(TAG, "Bitmap size: ${bitmap.width}x${bitmap.height}, no rotation needed")
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to bitmap", e)
            return null
        }
    }

    companion object {
        private const val TAG = "OpenCVImageAnalyzer"
    }
}