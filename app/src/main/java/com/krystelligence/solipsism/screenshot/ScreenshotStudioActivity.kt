package com.krystelligence.solipsism.screenshot

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.ThemableBrowserActivity
import com.krystelligence.solipsism.databinding.ActivityScreenshotStudioBinding
import com.krystelligence.solipsism.accessibility.AccessibilityAnnouncer
import java.io.File
import java.io.FileOutputStream

class ScreenshotStudioActivity : ThemableBrowserActivity() {
    private lateinit var binding: ActivityScreenshotStudioBinding
    private var sourceFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenshotStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sourceFile = intent.getStringExtra(EXTRA_PATH)?.let(::File)
        val bitmap = sourceFile?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (bitmap == null) { finish(); return }
        binding.screenshotCanvas.setBitmap(bitmap)
        binding.closeButton.setOnClickListener { finish() }
        binding.clearSelectionButton.setOnClickListener { binding.screenshotCanvas.clearSelection() }
        binding.saveButton.setOnClickListener { saveImage(bitmap) }
        binding.searchButton.setOnClickListener { searchSelection() }
    }

    private fun saveImage(bitmap: android.graphics.Bitmap) {
        runCatching {
            val name = "Solipsism_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Solipsism")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create screenshot media entry")
                contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                } ?: error("Unable to open screenshot output")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                check(MediaStore.Images.Media.insertImage(contentResolver, bitmap, name, null) != null)
            }
            Toast.makeText(this, R.string.screenshot_saved, Toast.LENGTH_SHORT).show()
            AccessibilityAnnouncer.announce(binding.root, getString(R.string.screenshot_saved))
            finish()
        }.onFailure {
            Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            AccessibilityAnnouncer.announce(binding.root, getString(R.string.screenshot_failed))
        }
    }

    private fun searchSelection() {
        val selected = binding.screenshotCanvas.selectedBitmap()
        if (selected == null) {
            Toast.makeText(this, R.string.screenshot_studio_draw_first, Toast.LENGTH_SHORT).show()
            AccessibilityAnnouncer.announce(binding.root, getString(R.string.screenshot_studio_draw_first))
            return
        }
        runCatching {
            val file = File(cacheDir, "shared/screenshot-search-${System.currentTimeMillis()}.png")
                .also { it.parentFile?.mkdirs() }
            FileOutputStream(file).use { check(selected.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.screenshot_studio_search)))
        }.onFailure {
            Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            AccessibilityAnnouncer.announce(binding.root, getString(R.string.screenshot_failed))
        }
    }

    override fun onDestroy() {
        sourceFile?.delete()
        super.onDestroy()
    }

    companion object { const val EXTRA_PATH = "com.krystelligence.solipsism.extra.SCREENSHOT_PATH" }
}
