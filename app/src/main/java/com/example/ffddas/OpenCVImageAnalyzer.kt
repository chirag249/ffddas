package com.example.ffddas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

class OpenCVImageAnalyzer(private val opencvProcessor: OpenCVProcessor, private val onFrameProcessed: (Bitmap) -> Unit) :
    ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            Log.d(TAG, "Analyzing image")
            // Convert ImageProxy to Bitmap
            val bitmap = imageToBitmap(image)
            
            // Process the bitmap with OpenCV
            val processedBitmap = opencvProcessor.processImage(bitmap)
            
            // Notify that frame has been processed
            onFrameProcessed(processedBitmap)
            
            // Close the image to free resources
            image.close()
            Log.d(TAG, "Image analysis completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image", e)
            image.close()
        }
    }

    private fun imageToBitmap(image: ImageProxy): Bitmap {
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
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            Log.d(TAG, "Image converted to bitmap")
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to bitmap", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "OpenCVImageAnalyzer"
    }
}