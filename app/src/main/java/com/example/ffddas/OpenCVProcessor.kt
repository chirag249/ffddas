package com.example.ffddas

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File

class OpenCVProcessor(cascadeFile: File) {
    
    private var cascadeClassifier: CascadeClassifier? = null
    private var grayMat: Mat? = null
    private var faces: MatOfRect? = null
    
    init {
        Log.d(TAG, "Initializing OpenCV processor with cascade file: ${cascadeFile.absolutePath}")
        Log.d(TAG, "Cascade file exists: ${cascadeFile.exists()}")
        initializeOpenCV(cascadeFile)
        Log.d(TAG, "OpenCV processor initialization completed")
    }
    
    private fun initializeOpenCV(cascadeFile: File) {
        try {
            Log.d(TAG, "Loading cascade classifier from: ${cascadeFile.absolutePath}")
            Log.d(TAG, "Cascade file size: ${cascadeFile.length()} bytes")
            
            // Check if the cascade file exists and is valid
            if (!cascadeFile.exists() || cascadeFile.length() == 0L) {
                Log.e(TAG, "Cascade file does not exist or is empty: ${cascadeFile.absolutePath}")
                return
            }
            
            // Load the cascade classifier
            Log.d(TAG, "Creating CascadeClassifier")
            cascadeClassifier = CascadeClassifier(cascadeFile.absolutePath)
            Log.d(TAG, "Cascade classifier created")
            grayMat = Mat()
            faces = MatOfRect()
            Log.d(TAG, "OpenCV matrices created")
            
            if (cascadeClassifier?.empty() == true) {
                Log.e(TAG, "Failed to load cascade classifier - classifier is empty")
                cascadeClassifier = null
            } else {
                Log.i(TAG, "Loaded cascade classifier from ${cascadeFile.absolutePath}")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "OpenCV native library not loaded: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OpenCV: ${e.message}", e)
        }
    }
    
    /**
     * Process a bitmap image with OpenCV for face detection
     * @param bitmap The input bitmap to process
     * @return The processed bitmap with face detection overlays
     */
    fun processImage(bitmap: Bitmap): Bitmap {
        // If classifier is not loaded, return original bitmap
        if (cascadeClassifier == null) {
            Log.d(TAG, "Cascade classifier not loaded, returning original bitmap")
            return bitmap
        }
        
        try {
            Log.d(TAG, "Processing image with OpenCV")
            // Convert bitmap to Mat
            val rgbaMat = Mat()
            Utils.bitmapToMat(bitmap, rgbaMat)
            
            // Convert to grayscale
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            
            // Detect faces with adjusted parameters for better detection
            val faceDetections = MatOfRect()
            cascadeClassifier?.detectMultiScale(
                grayMat,
                faceDetections,
                1.1,  // scaleFactor
                3,    // minNeighbors
                0,    // flags
                android.graphics.Point(30, 30),   // minSize
                android.graphics.Point(300, 300)  // maxSize
            )
            
            // Draw rectangles around detected faces
            val facesArray = faceDetections.toArray()
            Log.d(TAG, "Detected ${facesArray.size} faces")
            for (rect: Rect in facesArray) {
                Imgproc.rectangle(rgbaMat, rect.tl(), rect.br(), FACE_RECT_COLOR, 3)
            }
            
            // Convert back to bitmap
            val processedBitmap = Bitmap.createBitmap(
                rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(rgbaMat, processedBitmap)
            
            // Release Mats to free memory
            rgbaMat.release()
            faceDetections.release()
            
            Log.d(TAG, "Image processing completed")
            return processedBitmap
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "OpenCV native library not loaded during image processing: ${e.message}", e)
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image with OpenCV: ${e.message}", e)
            return bitmap
        }
    }
    
    fun release() {
        Log.d(TAG, "Releasing OpenCV resources")
        grayMat?.release()
        faces?.release()
    }
    
    companion object {
        private const val TAG = "OpenCVProcessor"
        private val FACE_RECT_COLOR = Scalar(255.0, 0.0, 0.0) // Blue color in BGR
    }
}