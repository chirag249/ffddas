package com.example.ffddas

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.opencv.video.Video
import java.io.File
import java.util.ArrayList

class OpenCVProcessor(cascadeFile: File) {
    
    private var cascadeClassifier: CascadeClassifier? = null
    private var previousGrayMat: Mat? = null // For motion blur reduction
    
    init {
        Log.d(TAG, "Initializing OpenCV processor with cascade file: ${cascadeFile.absolutePath}")
        Log.d(TAG, "Cascade file exists: ${cascadeFile.exists()}")
        Log.d(TAG, "Cascade file size: ${cascadeFile.length()} bytes")
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
            Log.d(TAG, "Input bitmap size: ${bitmap.width}x${bitmap.height}")
            
            // Convert bitmap to Mat
            val rgbaMat = Mat()
            Utils.bitmapToMat(bitmap, rgbaMat)
            Log.d(TAG, "Converted to Mat: ${rgbaMat.cols()}x${rgbaMat.rows()}")
            
            // Convert to grayscale
            val grayMat = Mat()
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Log.d(TAG, "Converted to grayscale")
            
            // Motion blur reduction techniques
            val processedGrayMat = reduceMotionBlur(grayMat)
            
            // Detect faces with optimized parameters for performance
            val faceDetections = MatOfRect()
            cascadeClassifier?.detectMultiScale(
                processedGrayMat,
                faceDetections,
                1.2,   // scaleFactor - larger for better performance
                3,     // minNeighbors - balanced for accuracy
                0,     // flags
                Size(80.0, 80.0),   // minSize - larger minimum to reduce false positives and improve performance
                Size()              // maxSize - empty means no maximum size limit
            )
            
            // Draw rectangles around detected faces
            val facesArray = faceDetections.toArray()
            Log.d(TAG, "Detected ${facesArray.size} faces")
            
            // Only draw rectangles if faces were detected
            if (facesArray.isNotEmpty()) {
                for (rect: Rect in facesArray) {
                    Log.d(TAG, "Face detected at: (${rect.x}, ${rect.y}) size: ${rect.width}x${rect.height}")
                    Imgproc.rectangle(rgbaMat, rect.tl(), rect.br(), FACE_RECT_COLOR, 2)
                }
            } else {
                Log.d(TAG, "No faces detected in this frame")
            }
            
            // Convert back to bitmap
            val processedBitmap = Bitmap.createBitmap(
                rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(rgbaMat, processedBitmap)
            
            // Release Mats to free memory
            rgbaMat.release()
            grayMat.release()
            processedGrayMat.release()
            faceDetections.release()
            
            Log.d(TAG, "Image processing completed, output bitmap size: ${processedBitmap.width}x${processedBitmap.height}")
            return processedBitmap
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "OpenCV native library not loaded during image processing: ${e.message}", e)
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image with OpenCV: ${e.message}", e)
            return bitmap
        }
    }
    
    /**
     * Apply motion blur reduction techniques to the grayscale image
     * @param grayMat The grayscale image Mat
     * @return Processed Mat with motion blur reduced
     */
    private fun reduceMotionBlur(grayMat: Mat): Mat {
        val processedMat = Mat()
        grayMat.copyTo(processedMat)
        
        // Store current frame for next comparison and use previous frame for stabilization
        val prevMat = previousGrayMat
        if (prevMat != null && !prevMat.empty()) {
            // Calculate motion level using frame difference
            val diffMat = Mat()
            Core.absdiff(grayMat, prevMat, diffMat)
            
            // Apply threshold to highlight significant changes
            val thresholdMat = Mat()
            Imgproc.threshold(diffMat, thresholdMat, 30.0, 255.0, Imgproc.THRESH_BINARY)
            
            // Calculate motion ratio
            val motionLevel = Core.countNonZero(thresholdMat)
            val totalPixels = diffMat.cols() * diffMat.rows()
            val motionRatio = motionLevel.toDouble() / totalPixels
            
            // Apply stabilization based on motion level
            if (motionRatio > 0.05) { // High motion
                Log.d(TAG, "High motion detected (ratio: $motionRatio), applying strong stabilization")
                // Strong temporal filtering for high motion
                val filtered = Mat()
                Core.addWeighted(processedMat, 0.3, prevMat, 0.7, 0.0, filtered)
                filtered.copyTo(processedMat)
                filtered.release()
            } else if (motionRatio > 0.01) { // Moderate motion
                Log.d(TAG, "Moderate motion detected (ratio: $motionRatio), applying stabilization")
                // Moderate temporal filtering
                val filtered = Mat()
                Core.addWeighted(processedMat, 0.6, prevMat, 0.4, 0.0, filtered)
                filtered.copyTo(processedMat)
                filtered.release()
            } else { // Low motion
                Log.d(TAG, "Low motion detected (ratio: $motionRatio), applying light stabilization")
                // Light temporal filtering for stability
                val filtered = Mat()
                Core.addWeighted(processedMat, 0.8, prevMat, 0.2, 0.0, filtered)
                filtered.copyTo(processedMat)
                filtered.release()
            }
            
            // Clean up
            diffMat.release()
            thresholdMat.release()
        }
        
        // Update previous frame
        if (previousGrayMat == null) {
            previousGrayMat = Mat()
        }
        grayMat.copyTo(previousGrayMat)
        
        return processedMat
    }
    
    fun release() {
        Log.d(TAG, "Releasing OpenCV resources")
        previousGrayMat?.release()
    }
    
    companion object {
        private const val TAG = "OpenCVProcessor"
        private val FACE_RECT_COLOR = Scalar(0.0, 255.0, 0.0) // Green color in BGR for better visibility
    }
}