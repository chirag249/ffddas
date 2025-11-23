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

    init { initializeOpenCV(cascadeFile) }

    fun setFilter(filter: MainActivity.FilterType) { currentFilter = filter }

    private fun initializeOpenCV(cascadeFile: File) {
        try {
            if (!cascadeFile.exists() || cascadeFile.length() == 0L) return
            cascadeClassifier = CascadeClassifier(cascadeFile.absolutePath)
            if (cascadeClassifier?.empty() == true) cascadeClassifier = null
        } catch (_: UnsatisfiedLinkError) {
        } catch (_: Exception) {
        }
    }

    fun processImage(bitmap: Bitmap): Bitmap {
        return try {
            val rgbaMat = Mat(); Utils.bitmapToMat(bitmap, rgbaMat)
            val grayMat = Mat(); Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            val filteredMat = when (currentFilter) {
                MainActivity.FilterType.EDGE_DETECTION -> applyEdgeDetection(grayMat)
                MainActivity.FilterType.GRAYSCALE, MainActivity.FilterType.NONE -> grayMat
            }
            if (filteredMat.type() == grayMat.type()) Imgproc.cvtColor(filteredMat, rgbaMat, Imgproc.COLOR_GRAY2RGBA)
            val processedBitmap = Bitmap.createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgbaMat, processedBitmap)
            rgbaMat.release(); grayMat.release(); if (filteredMat != grayMat) filteredMat.release()
            processedBitmap
        } catch (_: Exception) { bitmap }
    }

    private fun applyEdgeDetection(grayMat: Mat): Mat {
        val edges = Mat(); Imgproc.GaussianBlur(grayMat, edges, Size(5.0, 5.0), 1.5); Imgproc.Canny(edges, edges, 50.0, 150.0); return edges
    }

    fun release() { }

    companion object { private const val TAG = "OpenCVProcessor" }
}