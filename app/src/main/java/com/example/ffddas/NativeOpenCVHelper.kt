package com.example.ffddas

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer

class NativeOpenCVHelper {
    
    companion object {
        private const val TAG = "NativeOpenCVHelper"
        
        // Native method declarations
        @JvmStatic
        external fun processPhotoFrame(bitmapInput: Bitmap): Bitmap?
        
        @JvmStatic
        external fun processPreviewFrame(yuvImageBuffer: ByteBuffer, width: Int, height: Int): ByteArray?
        
        @JvmStatic
        external fun bitmapToMat(bitmap: Bitmap): Long
        
        @JvmStatic
        external fun matToBitmap(matAddr: Long, outputBitmap: Bitmap): Boolean
        
        @JvmStatic
        external fun applyCannyDetection(matAddr: Long, lowThreshold: Double, highThreshold: Double): Long
        
        @JvmStatic
        external fun convertToGrayscaleNative(matAddr: Long): Long
        
        @JvmStatic
        external fun releaseMatNative(matAddr: Long)
        
        /**
         * Process a photo frame using native OpenCV
         * @param bitmapInput The input bitmap to process
         * @return The processed bitmap or null if processing failed
         */
        fun processPhoto(bitmapInput: Bitmap): Bitmap? {
            try {
                return processPhotoFrame(bitmapInput)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing photo frame: ${e.message}", e)
                return null
            }
        }
        
        /**
         * Process a preview frame using native OpenCV
         * @param yuvImageBuffer The YUV image buffer
         * @param width The width of the image
         * @param height The height of the image
         * @return The processed image data as a byte array or null if processing failed
         */
        fun processPreview(yuvImageBuffer: ByteBuffer, width: Int, height: Int): ByteArray? {
            try {
                return processPreviewFrame(yuvImageBuffer, width, height)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing preview frame: ${e.message}", e)
                return null
            }
        }
        
        /**
         * Convert a Bitmap to OpenCV Mat
         * @param bitmap The bitmap to convert
         * @return The address of the Mat object or 0 if conversion failed
         */
        fun convertBitmapToMat(bitmap: Bitmap): Long {
            try {
                return bitmapToMat(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error converting bitmap to Mat: ${e.message}", e)
                return 0
            }
        }
        
        /**
         * Convert an OpenCV Mat to Bitmap
         * @param matAddr The address of the Mat object
         * @param outputBitmap The output bitmap
         * @return True if conversion was successful, false otherwise
         */
        fun convertMatToBitmap(matAddr: Long, outputBitmap: Bitmap): Boolean {
            try {
                return matToBitmap(matAddr, outputBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error converting Mat to bitmap: ${e.message}", e)
                return false
            }
        }
        
        /**
         * Apply Canny edge detection to an image
         * @param matAddr The address of the input Mat object
         * @param lowThreshold The low threshold for edge detection
         * @param highThreshold The high threshold for edge detection
         * @return The address of the resulting Mat object or 0 if processing failed
         */
        fun applyCanny(matAddr: Long, lowThreshold: Double = 50.0, highThreshold: Double = 150.0): Long {
            try {
                return applyCannyDetection(matAddr, lowThreshold, highThreshold)
            } catch (e: Exception) {
                Log.e(TAG, "Error applying Canny detection: ${e.message}", e)
                return 0
            }
        }
        
        /**
         * Convert an image to grayscale
         * @param matAddr The address of the input Mat object
         * @return The address of the resulting grayscale Mat object or 0 if processing failed
         */
        fun convertToGrayscale(matAddr: Long): Long {
            try {
                return convertToGrayscaleNative(matAddr)
            } catch (e: Exception) {
                Log.e(TAG, "Error converting to grayscale: ${e.message}", e)
                return 0
            }
        }
        
        /**
         * Release a Mat object to free memory
         * @param matAddr The address of the Mat object to release
         */
        fun releaseMat(matAddr: Long) {
            try {
                releaseMatNative(matAddr)
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing Mat: ${e.message}", e)
            }
        }
    }
}