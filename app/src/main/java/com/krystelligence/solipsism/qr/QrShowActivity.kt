package com.krystelligence.solipsism.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.krystelligence.solipsism.ThemeApplication
import com.krystelligence.solipsism.databinding.ActivityQrShowBinding

class QrShowActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrShowBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeApplication.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityQrShowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        binding.urlText.text = url

        if (url.isNotEmpty()) {
            generateQrCode(url)?.let {
                binding.qrCodeImage.setImageBitmap(it)
            }
        }

        // Set brightness to maximum
        val layoutParams = window.attributes
        layoutParams.screenBrightness = 1.0f
        window.attributes = layoutParams
    }

    private fun generateQrCode(text: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_URL = "com.krystelligence.solipsism.extra.QR_URL"
    }
}
