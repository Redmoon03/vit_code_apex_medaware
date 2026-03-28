package deo.raghav.medaware.screens

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import deo.raghav.medaware.R
import deo.raghav.medaware.networking.SocketManager
import deo.raghav.medaware.screens.AddReminder
import deo.raghav.medaware.utility.Constants.VERIFIED_TIMEOUT
import deo.raghav.medaware.utility.Utilities.checkOverlayPermission
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // Initialize your manager
    private val socketManager = SocketManager()
    private lateinit var resultView: ImageView
    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }
    private var isVerified: Boolean = false
    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // The task that runs if 60 seconds pass without verification
    private val timeoutTask = Runnable {
        handleTimeout()
    }
    private val rid by lazy {
        intent.getIntExtra("REMINDER_ID", -1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show()
            checkOverlayPermission(this)
            // We return here because we can't do anything until they come back with permission
            return
        }

        // START THE 60 SECOND COUNTDOWN
        timeoutHandler.postDelayed(timeoutTask, VERIFIED_TIMEOUT)

        resultView = findViewById<ImageView>(R.id.result_view)
        socketManager.initialize()
        socketManager.setupListeners("annotated_frame", ::handleAnnotatedFrame)
        socketManager.setupListeners("verified", ::handleVerified)
        socketManager.connect()

        // In onCreate, replace startCamera() with this:
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socketManager.disconnect() // Prevent memory leaks and background data usage
        cameraExecutor.shutdownNow()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.Companion.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Setup the Front Camera Selector
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                // Keep the latest frame to prevent lag if the network slows down
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(640, 480)) // Lower res = Faster socket transfer
                .build()

            // Set the analyzer using your lazy-loaded executor
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (exc: Exception) {
                println("Camera binding failed: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun handleAnnotatedFrame(data: Any?) {

        println("DEBUG: handleAnnotatedFrame triggered with: ${data?.javaClass?.simpleName}")
        if (data == null) {
            println("Data is empty for annotated_frame")
            return
        }

        println("DEBUG: Received data type: ${data.javaClass.simpleName}")

        val bytes = data as? ByteArray
        if (bytes == null) {
            println("Casting to byte array failed for annotated_frame")
            return
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            println("Error: Could not decode bytes into a Bitmap")
            return
        }
        runOnUiThread {
            // 1. Clear placeholder padding on the first successful frame
            if (resultView.paddingTop != 0) {
                resultView.setPadding(0, 0, 0, 0)
            }
            resultView.setImageBitmap(bitmap)
            println("Annotated image set successfully")
        }
    }

    fun handleVerified(data: Any?) {
        isVerified = true
        timeoutHandler.removeCallbacks(timeoutTask)
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Reminder verified")
            builder.setMessage("This reminder was verified")
            builder.setPositiveButton("OK") { dialog, _ ->
                finish() // Close the alarm screen
            }
            builder.create().show()
        }
    }

    fun handleTimeout() {
        if (isVerified) return // Safety check
        val sharedPreferences: SharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val uid = sharedPreferences.getInt("uid", 1)
        val data = JSONObject()
        data.put("uid", uid)
        data.put("rid", rid)
        socketManager.sendEvent("not verified", data)
        socketManager.disconnect()
        cameraExecutor.shutdownNow()
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Reminder not verified")
            builder.setMessage("This reminder was not verified")
            builder.setPositiveButton("OK", null)
            builder.setPositiveButton("OK") { dialog, _ ->
                finish() // Close the alarm screen
            }
            builder.create().show()
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val rotation = imageProxy.imageInfo.rotationDegrees
        // Convert ImageProxy (YUV) to JPEG ByteArray
        var bitmap = imageProxy.toBitmap() // Converts internal camera format to usable Bitmap

        // Rotate the bitmap to match the device orientation
        if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val outputStream = ByteArrayOutputStream()

        // Compress to JPEG (70% quality is a good balance for AI)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()

        // Send via your SocketManager
        socketManager.send(bytes)

        // CRITICAL: Release the frame so CameraX can send the next one
        imageProxy.close()
    }

}


