package com.krystelligence.solipsism.qr

import android.content.Context
import com.krystelligence.solipsism.i18n.TranslationOverrides
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.ThemeApplication
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(TranslationOverrides.wrap(newBase))
    }

    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService

    private val qrReader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
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
        ThemeApplication.applySavedTheme(this)
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

    private fun analyzeImage(imageProxy: ImageProxy) {
        if (resultReturned) {
            imageProxy.close()
            return
        }

        try {
            decodeQrValue(imageProxy)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { scannedValue ->
                    resultReturned = true
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_SCAN_RESULT, scannedValue)
                    )
                    finish()
                }
        } finally {
            qrReader.reset()
            imageProxy.close()
        }
    }

    private fun decodeQrValue(imageProxy: ImageProxy): String? {
        val yPlane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = yPlane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val source = PlanarYUVLuminanceSource(
            data,
            imageProxy.width,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false
        )
        return try {
            qrReader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
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
    }
}
