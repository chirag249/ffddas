package com.example.ffddas

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File

class OpenCVProcessor(cascadeFile: File) {
    
    private var cascadeClassifier: CascadeClassifier? = null
    private var currentFilter: MainActivity.FilterType = MainActivity.FilterType.NONE
    
    init {
        Log.d(TAG, "Initializing OpenCV processor with cascade file: ${cascadeFile.absolutePath}")
        Log.d(TAG, "Cascade file exists: ${cascadeFile.exists()}")
        Log.d(TAG, "Cascade file size: ${cascadeFile.length()} bytes")
        initializeOpenCV(cascadeFile)
        Log.d(TAG, "OpenCV processor initialization completed")
    }
    
    /**
     * Set current filter type
     */
    fun setFilter(filter: MainActivity.FilterType) {
        currentFilter = filter
        Log.d(TAG, "Filter set to: $currentFilter")
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
     * Process a bitmap image with OpenCV for face detection and filters
     * @param bitmap The input bitmap to process
     * @return The processed bitmap with filters and/or face detection overlays
     */
    fun processImage(bitmap: Bitmap): Bitmap {
        try {
            Log.d(TAG, "Processing image with OpenCV, filter: $currentFilter")
            Log.d(TAG, "Input bitmap size: ${bitmap.width}x${bitmap.height}")
            
            // Convert bitmap to Mat
            val rgbaMat = Mat()
            Utils.bitmapToMat(bitmap, rgbaMat)
            Log.d(TAG, "Converted to Mat: ${rgbaMat.cols()}x${rgbaMat.rows()}")
            
            // Convert to grayscale
            val grayMat = Mat()
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Log.d(TAG, "Converted to grayscale")
            
            // Apply filter based on current selection
            val filteredMat = when (currentFilter) {
                MainActivity.FilterType.EDGE_DETECTION -> {
                    Log.d(TAG, "Applying EDGE_DETECTION filter")
                    applyEdgeDetection(grayMat)
                }
                MainActivity.FilterType.GRAYSCALE -> {
                    Log.d(TAG, "Applying GRAYSCALE filter")
                    grayMat
                }
                MainActivity.FilterType.NONE -> {
                    Log.d(TAG, "Applying face detection (NONE filter)")
                    // Apply motion blur reduction for face detection
                    reduceMotionBlur(grayMat)
                }
            }
            
            // For face detection, detect faces and draw rectangles
            if (currentFilter == MainActivity.FilterType.NONE && cascadeClassifier != null) {
                // Detect faces with optimized parameters for performance
                val faceDetections = MatOfRect()
                cascadeClassifier?.detectMultiScale(
                    filteredMat,
                    faceDetections,
                    1.05,  // scaleFactor - smaller for better accuracy
                    3,     // minNeighbors - balanced for accuracy
                    0,     // flags
                    Size(60.0, 60.0),   // minSize - larger minimum to reduce false positives
                    Size()              // maxSize - empty means no maximum size limit
                )
                
                // Draw rectangles around detected faces on color image
                val faces = faceDetections.toArray()
                Log.d(TAG, "Detected ${faces.size} faces")
                for (face in faces) {
                    Imgproc.rectangle(
                        rgbaMat,
                        face.tl(),
                        face.br(),
                        FACE_RECT_COLOR,
                        3
                    )
                }
                
                faceDetections.release()
            } else {
                // Convert filtered grayscale back to RGBA for display
                Imgproc.cvtColor(filteredMat, rgbaMat, Imgproc.COLOR_GRAY2RGBA)
            }
            
            // Convert result back to bitmap
            val processedBitmap = Bitmap.createBitmap(
                rgbaMat.cols(), 
                rgbaMat.rows(), 
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(rgbaMat, processedBitmap)
            
            // Release Mats to free memory
            rgbaMat.release()
            grayMat.release()
            if (filteredMat != grayMat) {
                filteredMat.release()
            }
            
            Log.d(TAG, "Image processing completed, output bitmap size: ${processedBitmap.width}x${processedBitmap.height}")
            return processedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image with OpenCV: ${e.message}", e)
            return bitmap
        }
    }
    
    /**
     * Apply edge detection filter (Canny)
     */
    private fun applyEdgeDetection(grayMat: Mat): Mat {
        val edges = Mat()
        // Apply Gaussian blur to reduce noise
        Imgproc.GaussianBlur(grayMat, edges, Size(5.0, 5.0), 1.5)
        // Apply Canny edge detection
        Imgproc.Canny(edges, edges, 50.0, 150.0)
        Log.d(TAG, "Applied edge detection filter")
        return edges
    }
    
    /**
     * Apply motion blur reduction techniques to the grayscale image
     * @param grayMat The grayscale image Mat
     * @return Processed Mat with motion blur reduced
     */
    private fun reduceMotionBlur(grayMat: Mat): Mat {
        val processedMat = Mat()
        
        // Apply histogram equalization to improve contrast and reduce blur effects
        Imgproc.equalizeHist(grayMat, processedMat)
        
        // Apply sharpening to counteract any blur
        val sharpened = Mat()
        val kernel = Mat(3, 3, CvType.CV_32F)
        kernel.put(0, 0, 
            0.0, -1.0, 0.0,
            -1.0, 5.0, -1.0,
            0.0, -1.0, 0.0
        )
        Imgproc.filter2D(processedMat, sharpened, -1, kernel)
        kernel.release()
        
        processedMat.release()
        return sharpened
    }
    
    fun release() {
        Log.d(TAG, "Releasing OpenCV resources")
    }
    
    companion object {
        private const val TAG = "OpenCVProcessor"
        private val FACE_RECT_COLOR = Scalar(0.0, 255.0, 0.0) // Green color in BGR
    }
}