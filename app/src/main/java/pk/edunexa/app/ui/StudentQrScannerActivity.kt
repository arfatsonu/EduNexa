package pk.edunexa.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/** Shared by attendance and fee lookup. Scanning itself never writes school data. */
@Suppress("DEPRECATION")
class StudentQrScannerActivity : Activity() {
    private lateinit var scanner: DecoratedBarcodeView
    private lateinit var switch: Button
    private var cameras = emptyList<Int>()
    private var cameraId = 0
    private var completed = false
    private var resumed = false
    private val prefs by lazy { getSharedPreferences("student-qr-camera", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 15, 24))
            // Keep camera controls outside system bars and display cutouts.
            fitsSystemWindows = true
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        val toolbar = LinearLayout(this)
        toolbar.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(0, -2, 1f))
        switch = Button(this).apply { setOnClickListener { switchCamera() } }
        toolbar.addView(switch, LinearLayout.LayoutParams(0, -2, 2f))
        layout.addView(toolbar)
        layout.addView(TextView(this).apply {
            text = "Scan student QR card"; setTextColor(Color.WHITE); textSize = 18f
            setPadding(20, 12, 20, 12)
        })
        scanner = DecoratedBarcodeView(this).apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            setStatusText("Keep the complete QR code inside the frame")
        }
        layout.addView(scanner, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        layout.requestApplyInsets()
        try {
            cameras = (0 until Camera.getNumberOfCameras()).toList()
            check(cameras.isNotEmpty()) { "No camera is available on this device." }
            val back = cameras.firstOrNull { facing(it) == Camera.CameraInfo.CAMERA_FACING_BACK } ?: cameras.first()
            cameraId = (savedInstanceState?.getInt("cameraId") ?: prefs.getInt("cameraId", back)).takeIf { it in cameras } ?: back
            updateCamera()
            scanner.barcodeView.addStateListener(object : com.journeyapps.barcodescanner.CameraPreview.StateListener {
                override fun previewSized() {}
                override fun previewStarted() {}
                override fun previewStopped() {}
                override fun cameraClosed() {}
                override fun cameraError(error: Exception) {
                    Toast.makeText(this@StudentQrScannerActivity, "Camera could not open. Try switching cameras or close and retry.", Toast.LENGTH_LONG).show()
                }
            })
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 40)
        } catch (error: Exception) {
            setResult(RESULT_CANCELED, Intent().putExtra("error", error.message ?: "Camera unavailable.")); finish()
        }
    }
    private fun facing(id: Int): Int = Camera.CameraInfo().also { Camera.getCameraInfo(id, it) }.facing
    private fun updateCamera() {
        scanner.cameraSettings = scanner.cameraSettings.apply { requestedCameraId = cameraId }
        switch.text = if (facing(cameraId) == Camera.CameraInfo.CAMERA_FACING_FRONT) "Front camera • Switch" else "Back camera • Switch"
        switch.isEnabled = cameras.size > 1
        prefs.edit().putInt("cameraId", cameraId).apply()
    }
    private fun switchCamera() {
        if (completed || cameras.size < 2) return
        scanner.pauseAndWait()
        cameraId = cameras[(cameras.indexOf(cameraId) + 1) % cameras.size]
        updateCamera()
        startCamera()
    }
    private fun startCamera() {
        if (!resumed || completed || isFinishing || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        scanner.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (completed || result.text.isNullOrBlank()) return
                completed = true
                scanner.pause()
                setResult(RESULT_OK, Intent().putExtra("qr", result.text))
                finish()
            }
        })
        scanner.resume()
    }
    override fun onResume() { super.onResume(); resumed = true; if (::scanner.isInitialized) startCamera() }
    override fun onPause() { resumed = false; if (::scanner.isInitialized) scanner.pauseAndWait(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("cameraId", cameraId); super.onSaveInstanceState(outState) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 40) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
            else { setResult(RESULT_CANCELED, Intent().putExtra("error", "Camera permission is required to scan a card. You can allow it in App Settings.")); finish() }
        }
    }
}
