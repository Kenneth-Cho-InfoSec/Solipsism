package com.krystelligence.solipsism.qr

import com.krystelligence.solipsism.AppTheme
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.databinding.ActivityQrScannerBinding
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService

    private val barcodeScanner: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    @Volatile
    private var resultReturned = false

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.qr_scanner_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.closeButton.setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = binding.cameraPreview.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, ::analyzeImage) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (resultReturned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val scannedValue = barcodes
                    .firstNotNullOfOrNull { barcode -> barcode.url?.url ?: barcode.rawValue }
                    ?.trim()

                if (!scannedValue.isNullOrEmpty() && !resultReturned) {
                    resultReturned = true
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_SCAN_RESULT, scannedValue)
                    )
                    finish()
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeScanner.close()
        cameraExecutor.shutdown()
    }

    private fun applySavedTheme() {
        val themeValue = getSharedPreferences(USER_PREFERENCES, MODE_PRIVATE)
            .getInt(THEME_PREFERENCE, AppTheme.LIGHT.value)
        setTheme(
            when (AppTheme.entries.firstOrNull { it.value == themeValue } ?: AppTheme.LIGHT) {
                AppTheme.LIGHT -> R.style.Theme_LightTheme
                AppTheme.DARK -> R.style.Theme_DarkTheme
                AppTheme.BLACK -> R.style.Theme_BlackTheme
            }
        )
    }

    private fun applySystemBarInsets() {
        val topBaseMargin = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
        val bottomBaseMargin = (48 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.scannerTopPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top + topBaseMargin
            }
            binding.scannerHint.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + bottomBaseMargin
            }
            binding.scannerTopPanel.updatePadding(
                left = binding.scannerTopPanel.paddingLeft,
                right = binding.scannerTopPanel.paddingRight
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    companion object {
        const val EXTRA_SCAN_RESULT = "com.krystelligence.solipsism.extra.QR_SCAN_RESULT"
        private const val USER_PREFERENCES = "settings"
        private const val THEME_PREFERENCE = "Theme"
    }
}
