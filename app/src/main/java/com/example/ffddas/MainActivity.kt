package com.example.ffddas

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.ffddas.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var opencvProcessor: OpenCVProcessor? = null
    private lateinit var cameraExecutor: ExecutorService

    // Filter state
    private var currentFilter = FilterType.NONE
    private var isProcessing = false
    private var lastProcessedBitmap: Bitmap? = null // Store the last processed frame

    enum class FilterType {
        NONE, EDGE_DETECTION, GRAYSCALE
    }

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result received")
        permissions.forEach { (permission, granted) ->
            Log.d(TAG, "Permission $permission granted: $granted")
        }

        val cameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false

        when {
            cameraPermissionGranted -> {
                Log.d(TAG, "Camera permission granted, starting camera")
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                startCamera()
            }
            else -> {
                Log.d(TAG, "Camera permission denied")
                Toast.makeText(
                    this,
                    "Camera permission is required for camera functionality",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize background executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        Log.d(TAG, "Background executor initialized")

        // Setup UI event listeners
        setupUIListeners()

        // Check and request permissions
        Log.d(TAG, "Checking permissions")
        if (allPermissionsGranted()) {
            Log.d(TAG, "All permissions granted, starting camera")
            startCamera()
        } else {
            Log.d(TAG, "Requesting permissions")
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun setupUIListeners() {
        // Capture button click listener
        binding.captureButton.setOnClickListener {
            takePhoto()
        }
        Log.d(TAG, "Capture button listener set")

        // Filter selection listener
        binding.filterGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.edgeDetectionRadio -> FilterType.EDGE_DETECTION
                R.id.grayscaleRadio -> FilterType.GRAYSCALE
                R.id.faceDetectionRadio -> FilterType.NONE
                else -> FilterType.NONE
            }
            onFilterChanged()
        }
        Log.d(TAG, "Filter group listener set")
    }

    private fun onFilterChanged() {
        Log.d(TAG, "=== Filter changed to: $currentFilter ===")
        updateStatusText()

        // Apply filter to OpenCV processor
        opencvProcessor?.setFilter(currentFilter)
        Log.d(TAG, "Filter applied to OpenCV processor")
    }

    private fun updateStatusText() {
        val filterText = when (currentFilter) {
            FilterType.EDGE_DETECTION -> "Edge Detection"
            FilterType.GRAYSCALE -> "Grayscale"
            FilterType.NONE -> "Face Detection"
        }

        runOnUiThread {
            binding.statusText.text = "Filter: $filterText"
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        val granted = ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Permission $it granted: $granted")
        granted
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera initialization")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        Log.d(TAG, "Got camera provider future")

        cameraProviderFuture.addListener({
            try {
                Log.d(TAG, "Camera provider future listener triggered")
                val provider = cameraProviderFuture.get()
                Log.d(TAG, "Got camera provider from future")
                cameraProvider = provider

                // Initialize OpenCV processor
                Log.d(TAG, "Initializing OpenCV processor")
                initializeOpenCVProcessor()

                // Build and bind the camera use cases
                Log.d(TAG, "Binding camera use cases")
                bindCameraUseCases()

                updateStatusText()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera provider", e)
                Toast.makeText(
                    this,
                    "Failed to initialize camera: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun initializeOpenCVProcessor() {
        try {
            Log.d(TAG, "Initializing OpenCV processor")
            val faceCascadeFile = OpenCVHelper.getFaceCascadeFile(this)
            Log.d(TAG, "Cascade file path: ${faceCascadeFile.absolutePath}")

            opencvProcessor = OpenCVProcessor(faceCascadeFile)
            opencvProcessor?.setFilter(currentFilter)
            Log.d(TAG, "OpenCV processor initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OpenCV", e)
            Toast.makeText(
                this,
                "Failed to initialize face detection: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun bindCameraUseCases() {
        Log.d(TAG, "Binding camera use cases for Photo mode")

        val provider = cameraProvider ?: run {
            Log.e(TAG, "Camera provider not initialized")
            Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Unbind all use cases before rebinding
            provider.unbindAll()
            Log.d(TAG, "Unbound all previous use cases")

            // Get screen rotation
            val rotation = binding.previewView.display.rotation

            // Camera selector - prefer back camera
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // Preview use case
            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build()

            // Image capture use case
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Image analysis for preview processing
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(1280, 720))
                .setTargetRotation(rotation)
                .build()

            // Set up analyzer
            opencvProcessor?.let { processor ->
                imageAnalyzer?.setAnalyzer(cameraExecutor, OpenCVImageAnalyzer(processor, { processedBitmap ->
                    runOnUiThread {
                        // Store a copy of the processed bitmap for capture
                        lastProcessedBitmap = processedBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        binding.previewView.alpha = 0f
                        binding.processedImageView.visibility = View.VISIBLE
                        binding.processedImageView.setImageBitmap(processedBitmap)
                    }
                }, 100))
            }

            // Bind use cases to lifecycle
            provider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )

            // Attach preview to surface provider
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            Log.d(TAG, "Photo mode setup complete")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Toast.makeText(
                this,
                "Camera binding failed: ${exc.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun takePhoto() {
        if (isProcessing) {
            Toast.makeText(this, "Processing previous capture...", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Taking photo with filter: $currentFilter")
        
        // Check if we have a processed bitmap
        val bitmapToSave = lastProcessedBitmap ?: run {
            Log.e(TAG, "No processed image available")
            Toast.makeText(this, "No preview available", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress indicator
        isProcessing = true
        runOnUiThread {
            binding.processingProgress.visibility = View.VISIBLE
            binding.processingProgress.isIndeterminate = true
        }

        // Save the processed bitmap
        saveBitmapToGallery(bitmapToSave)
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        try {
            Log.d(TAG, "Saving bitmap to gallery, size: ${bitmap.width}x${bitmap.height}, filter: $currentFilter")
            
            val filterName = when (currentFilter) {
                FilterType.EDGE_DETECTION -> "EdgeDetection"
                FilterType.GRAYSCALE -> "Grayscale"
                FilterType.NONE -> "FaceDetection"
            }
            
            val timestamp = System.currentTimeMillis()
            val name = "IMG_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(timestamp)}_${filterName}"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                Log.d(TAG, "Using MediaStore API for Android ${Build.VERSION.SDK_INT}")
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${getString(R.string.app_name)}")
                    put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                    put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                if (uri != null) {
                    Log.d(TAG, "Created MediaStore entry: $uri")
                    
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                        outputStream.flush()
                        Log.d(TAG, "Bitmap compress success: $success")
                    }
                    
                    // Mark as not pending anymore
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                    
                    Log.d(TAG, "Image saved successfully to MediaStore: $uri")
                    handleCaptureSuccess("Photo saved to gallery")
                } else {
                    val error = "Failed to create MediaStore entry"
                    Log.e(TAG, error)
                    handleCaptureError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, error, null))
                }
            } else {
                // For older Android versions, save to file
                Log.d(TAG, "Using file storage for Android ${Build.VERSION.SDK_INT}")
                
                val photoFile = File(getOutputDirectory(), "$name.jpg")
                Log.d(TAG, "Saving to file: ${photoFile.absolutePath}")
                
                photoFile.outputStream().use { outputStream ->
                    val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    outputStream.flush()
                    Log.d(TAG, "Bitmap compress success: $success")
                }
                
                // Notify media scanner
                val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = android.net.Uri.fromFile(photoFile)
                sendBroadcast(intent)
                
                Log.d(TAG, "Image saved to file and media scanner notified")
                handleCaptureSuccess("Photo saved")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to gallery: ${e.message}", e)
            e.printStackTrace()
            handleCaptureError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Failed to save image: ${e.message}", e))
        }
    }

    private fun captureToMediaStore(imageCapture: ImageCapture) {
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${getString(R.string.app_name)}")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    handleCaptureError(exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    handleCaptureSuccess("Photo saved to gallery")
                }
            }
        )
    }

    private fun captureToFile(imageCapture: ImageCapture) {
        val photoFile = File(
            getOutputDirectory(),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    handleCaptureError(exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    handleCaptureSuccess("Photo saved: ${photoFile.absolutePath}")
                }
            }
        )
    }

    private fun handleCaptureSuccess(message: String) {
        Log.d(TAG, message)
        runOnUiThread {
            binding.processingProgress.visibility = View.GONE
            Toast.makeText(this, "Photo captured successfully!", Toast.LENGTH_SHORT).show()
        }
        isProcessing = false
    }

    private fun handleCaptureError(exc: ImageCaptureException) {
        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
        runOnUiThread {
            binding.processingProgress.visibility = View.GONE
            Toast.makeText(
                this,
                "Failed to capture photo: ${exc.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        isProcessing = false
    }

    private fun getOutputDirectory(): File {
        val mediaDir = getExternalMediaDirs().firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        // Shutdown executor
        cameraExecutor.shutdown()

        // Unbind camera
        cameraProvider?.unbindAll()

        // Release OpenCV resources
        opencvProcessor?.release()

        Log.d(TAG, "Resources cleaned up")
    }

    external fun stringFromJNI(): String

    companion object {
        private const val TAG = "MainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )

        // Load native libraries
        init {
            try {
                System.loadLibrary("ffddas")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}", e)
            }

            try {
                System.loadLibrary("opencv_java4")
                Log.d(TAG, "OpenCV library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load OpenCV library: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading OpenCV library: ${e.message}", e)
            }
        }
    }
}
