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
    private val onFrameProcessed: (Bitmap) -> Unit,
    private val filterProvider: () -> MainActivity.FilterType,
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
            
            val filter = filterProvider()

            // If no processing, just close and let raw PreviewView display feed
            if (filter == MainActivity.FilterType.NONE) {
                lastProcessedTime = currentTime
                image.close()
                return
            }

            val nv21 = yuv420ToNV21(image)

            val width = image.width
            val height = image.height

            val processedBitmap: Bitmap? = when (filter) {
                MainActivity.FilterType.EDGE_DETECTION -> {
                    // Use native fast NV21 -> RGBA edge pipeline
                    val direct = ByteBuffer.allocateDirect(nv21.size).order(ByteOrder.nativeOrder())
                    direct.put(nv21)
                    direct.position(0)
                    val rgba = NativeOpenCVHelper.processPreview(direct, width, height)
                    if (rgba != null && rgba.size >= width * height * 4) {
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(rgba))
                        if (image.imageInfo.rotationDegrees != 0) {
                            val m = Matrix()
                            m.postRotate(image.imageInfo.rotationDegrees.toFloat())
                            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                            if (rotated != bmp) bmp.recycle()
                            rotated
                        } else bmp
                    } else null
                }
                MainActivity.FilterType.GRAYSCALE -> {
                    // Convert NV21 -> Bitmap once, then native grayscale
                    var baseBitmap = nv21ToBitmap(nv21, width, height, image.imageInfo.rotationDegrees)
                    if (baseBitmap == null || baseBitmap.isRecycled) null else {
                        if (baseBitmap.config != Bitmap.Config.ARGB_8888 || !baseBitmap.isMutable) {
                            val copy = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
                            if (copy != baseBitmap) baseBitmap.recycle()
                            baseBitmap = copy
                        }
                        val matAddr = NativeOpenCVHelper.convertBitmapToMat(baseBitmap)
                        if (matAddr == 0L) null else {
                            val grayAddr = NativeOpenCVHelper.convertToGrayscale(matAddr)
                            val out = Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
                            val ok = (grayAddr != 0L) && NativeOpenCVHelper.convertMatToBitmap(grayAddr, out)
                            NativeOpenCVHelper.releaseMat(grayAddr)
                            NativeOpenCVHelper.releaseMat(matAddr)
                            if (ok) out else null
                        }
                    }
                }
                else -> null
            }

            if (processedBitmap == null || processedBitmap.isRecycled) {
                Log.e(TAG, "Native processing failed")
                image.close()
                return
            }

            lastProcessedTime = currentTime
            onFrameProcessed(processedBitmap)
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

    private fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int, rotationDegrees: Int): Bitmap? {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, out)
        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        if (rotationDegrees != 0) {
            val m = Matrix()
            m.postRotate(rotationDegrees.toFloat())
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (rotated != bitmap) bitmap.recycle()
            bitmap = rotated
        }
        return bitmap
    }

    private fun yuv420ToNV21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer.duplicate()
        val uBuf = uPlane.buffer.duplicate()
        val vBuf = vPlane.buffer.duplicate()

        val out = ByteArray(width * height + (width * height / 2))
        var offset = 0

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            val rowStart = row * yRowStride
            if (yPixelStride == 1) {
                yBuf.position(rowStart)
                yBuf.limit(rowStart + width)
                yBuf.get(out, offset, width)
                offset += width
            } else {
                for (col in 0 until width) {
                    out[offset++] = yBuf.get(rowStart + col * yPixelStride)
                }
            }
        }

        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val uvHeight = height / 2
        val uvWidth = width / 2
        for (row in 0 until uvHeight) {
            val vRowStart = row * vRowStride
            val uRowStart = row * uRowStride
            for (col in 0 until uvWidth) {
                out[offset++] = vBuf.get(vRowStart + col * vPixelStride)
                out[offset++] = uBuf.get(uRowStart + col * uPixelStride)
            }
        }
        return out
    }

    companion object {
        private const val TAG = "OpenCVImageAnalyzer"
    }
}