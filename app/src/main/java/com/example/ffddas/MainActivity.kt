package com.example.ffddas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result received")
        permissions.forEach { (permission, granted) ->
            Log.d(TAG, "Permission $permission granted: $granted")
        }
        
        val cameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false
        // Removed storage permission checks since we're not requiring them anymore

        when {
            cameraPermissionGranted -> {
                // Camera permission granted, start camera
                Log.d(TAG, "Camera permission granted, starting camera")
                startCamera()
            }
            else -> {
                // Camera permission denied
                Log.d(TAG, "Camera permission denied")
                Toast.makeText(this, "Camera permission is required for camera functionality", Toast.LENGTH_SHORT).show()
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

        // Set up capture button click listener
        binding.captureButton.setOnClickListener {
            takePhoto()
        }
        Log.d(TAG, "Capture button listener set")
        
        // Set up gallery button click listener
        binding.galleryButton.setOnClickListener {
            openGallery()
        }
        Log.d(TAG, "Gallery button listener set")

        // Check and request permissions
        Log.d(TAG, "Checking permissions")
        if (allPermissionsGranted()) {
            Log.d(TAG, "All permissions granted, starting camera")
            startCamera()
        } else {
            Log.d(TAG, "Requesting permissions")
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA
                    // Removed storage permissions
                )
            )
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

        // Add a timeout to prevent indefinite waiting
        cameraProviderFuture.addListener({
            try {
                Log.d(TAG, "Camera provider future listener triggered")
                // Camera provider is now guaranteed to be available
                val provider = cameraProviderFuture.get()
                Log.d(TAG, "Got camera provider from future")
                cameraProvider = provider

                // Initialize OpenCV processor
                Log.d(TAG, "Initializing OpenCV processor")
                initializeOpenCVProcessor()

                // Build and bind the camera use cases
                Log.d(TAG, "Binding camera use cases")
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera provider", e)
                Toast.makeText(this, "Failed to initialize camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun initializeOpenCVProcessor() {
        try {
            Log.d(TAG, "Initializing OpenCV processor")
            // Get the face cascade file
            val faceCascadeFile = OpenCVHelper.getFaceCascadeFile(this)
            Log.d(TAG, "Cascade file path: ${faceCascadeFile.absolutePath}")
            
            // Initialize the OpenCV processor
            opencvProcessor = OpenCVProcessor(faceCascadeFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OpenCV", e)
            Toast.makeText(this, "Failed to initialize OpenCV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun bindCameraUseCases() {
        Log.d(TAG, "Binding camera use cases")
        // Get screen metrics used to setup camera for full screen resolution
        val rotation = binding.previewView.display.rotation

        // Create configuration object for the viewfinder use case
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()

        // Create a capture use case
        imageCapture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .build()

        // Create an image analysis use case
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(1280, 720)) // Higher resolution for better quality
            .build()

        // Set up the image analyzer with OpenCV processing
        opencvProcessor?.let { processor ->
            imageAnalyzer?.setAnalyzer(cameraExecutor, OpenCVImageAnalyzer(processor, { processedBitmap ->
                // Display the processed frame with face detection
                runOnUiThread {
                    // Hide the raw preview and show only the processed image with face detection
                    binding.previewView.alpha = 0f
                    binding.processedImageView.visibility = android.view.View.VISIBLE
                    binding.processedImageView.setImageBitmap(processedBitmap)
                    Log.d(TAG, "Processed frame with face detection displayed")
                }
            }, 100)) // Process frames more frequently (10 FPS) for smoother output
        }

        // Choose the camera by requiring a lens facing
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        try {
            // Unbind use cases before rebinding
            cameraProvider?.unbindAll()

            // Bind use cases to camera
            cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer, imageCapture
            )

            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            Log.d(TAG, "Camera use cases bound successfully")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Toast.makeText(this, "Use case binding failed: ${exc.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        Log.d(TAG, "Opening gallery")
        // Start the GalleryActivity
        val intent = android.content.Intent(this, GalleryActivity::class.java)
        startActivity(intent)
    }
    
    // Method to capture photo
    private fun takePhoto() {
        Log.d(TAG, "Taking photo")
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            getOutputDirectory(),
            SimpleDateFormat(
                "yyyy-MM-dd-HH-mm-ss-SSS",
                Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Setup image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    // Handle error
                    Log.e(TAG, "Photo capture failed", exc)
                    Toast.makeText(baseContext, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Photo saved successfully
                    val msg = "Photo capture succeeded: ${photoFile.absolutePath}"
                    Log.d(TAG, msg)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    
                    // Request storage permission after successful capture if needed
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        requestStoragePermission()
                    }
                }
            }
        )
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above, we don't need WRITE_EXTERNAL_STORAGE
            // MediaStore API is used instead
            return
        }
        
        // For older versions, request storage permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            val storagePermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    Log.d(TAG, "Storage permission granted")
                } else {
                    Log.d(TAG, "Storage permission denied")
                    Toast.makeText(this, "Storage permission is needed to save photos", Toast.LENGTH_SHORT).show()
                }
            }
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = getExternalMediaDirs().firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        opencvProcessor?.release()
    }

    /**
     * A native method that is implemented by the 'ffddas' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
            // Removed storage permissions as they're not always needed for CameraX
            // and can cause issues on newer Android versions
        )

        // Used to load the 'ffddas' library on application startup.
        init {
            System.loadLibrary("ffddas")
            // Load OpenCV library with error handling
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