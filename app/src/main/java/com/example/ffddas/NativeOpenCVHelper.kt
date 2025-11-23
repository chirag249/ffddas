package com.example.ffddas

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer

class NativeOpenCVHelper {
    companion object {
        @JvmStatic external fun processPhotoFrame(bitmapInput: Bitmap): Bitmap?
        @JvmStatic external fun processPreviewFrame(yuvImageBuffer: ByteBuffer, width: Int, height: Int): ByteArray?
        @JvmStatic external fun bitmapToMat(bitmap: Bitmap): Long
        @JvmStatic external fun matToBitmap(matAddr: Long, outputBitmap: Bitmap): Boolean
        @JvmStatic external fun applyCannyDetection(matAddr: Long, lowThreshold: Double, highThreshold: Double): Long
        @JvmStatic external fun convertToGrayscaleNative(matAddr: Long): Long
        @JvmStatic external fun releaseMatNative(matAddr: Long)

        fun processPhoto(bitmapInput: Bitmap): Bitmap? = try { processPhotoFrame(bitmapInput) } catch (_: Exception) { null }
        fun processPreview(yuvImageBuffer: ByteBuffer, width: Int, height: Int): ByteArray? = try { processPreviewFrame(yuvImageBuffer, width, height) } catch (_: Exception) { null }
        fun convertBitmapToMat(bitmap: Bitmap): Long = try { bitmapToMat(bitmap) } catch (_: Exception) { 0 }
        fun convertMatToBitmap(matAddr: Long, outputBitmap: Bitmap): Boolean = try { matToBitmap(matAddr, outputBitmap) } catch (_: Exception) { false }
        fun applyCanny(matAddr: Long, lowThreshold: Double = 50.0, highThreshold: Double = 150.0): Long = try { applyCannyDetection(matAddr, lowThreshold, highThreshold) } catch (_: Exception) { 0 }
        fun convertToGrayscale(matAddr: Long): Long = try { convertToGrayscaleNative(matAddr) } catch (_: Exception) { 0 }
        fun releaseMat(matAddr: Long) { try { releaseMatNative(matAddr) } catch (_: Exception) { } }
    }
}