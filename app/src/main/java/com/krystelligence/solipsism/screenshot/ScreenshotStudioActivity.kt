package com.krystelligence.solipsism.screenshot

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.ThemableBrowserActivity
import com.krystelligence.solipsism.DefaultBrowserActivity
import com.krystelligence.solipsism.databinding.ActivityScreenshotStudioBinding
import com.krystelligence.solipsism.accessibility.AccessibilityAnnouncer
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class ScreenshotStudioActivity : ThemableBrowserActivity() {
    private lateinit var binding: ActivityScreenshotStudioBinding
    private var sourceFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenshotStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val baseTopPadding = binding.root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = baseTopPadding + statusBarTop)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
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
        lifecycleScope.launch {
            val result = runCatching { uploadToYandexImages(selected) }
            result.onSuccess { location ->
                // Yandex is used only as a short-lived background visual classifier. Never
                // expose its page to the user; send the first recognised label to Google Images.
                val titleResult = runCatching { resolveYandexTitle(location) }.getOrNull()
                if (titleResult.isNullOrBlank()) {
                    Toast.makeText(this@ScreenshotStudioActivity, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val googleImagesUrl = Uri.parse("https://www.google.com/search").buildUpon()
                    .appendQueryParameter("q", titleResult)
                    .appendQueryParameter("tbm", "isch")
                    .build()
                startActivity(Intent(this@ScreenshotStudioActivity, DefaultBrowserActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = googleImagesUrl
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                finish()
            }.onFailure {
                Toast.makeText(this@ScreenshotStudioActivity, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                AccessibilityAnnouncer.announce(binding.root, getString(R.string.screenshot_failed))
            }
        }
    }

    private suspend fun uploadToYandexImages(bitmap: android.graphics.Bitmap): String = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "shared/screenshot-search-${System.currentTimeMillis()}.jpg")
            .also { it.parentFile?.mkdirs() }
        FileOutputStream(file).use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, output))
        }
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upfile", "blob", file.asRequestBody("image/jpeg".toMediaType()))
                .build()
            val requestUrl = "https://yandex.com/images/search".toHttpUrl().newBuilder()
                .addQueryParameter("rpt", "imageview")
                .addQueryParameter("format", "json")
                .addQueryParameter(
                    "request",
                    "{\"blocks\":[{\"block\":\"b-page_type_search-by-image__link\"}]}"
                )
                .build()
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36")
                .post(requestBody)
                .build()
            OkHttpClient().newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Yandex image upload failed: HTTP ${response.code}" }
                val json = org.json.JSONObject(response.body?.string().orEmpty())
                val params = json.getJSONArray("blocks")
                    .getJSONObject(0)
                    .getJSONObject("params")
                val originalImageUrl = params.getString("originalImageUrl")
                val cbirId = params.getString("cbirId")
                "https://yandex.com/images/search".toHttpUrl().newBuilder()
                    .addQueryParameter("rpt", "imageview")
                    .addQueryParameter("url", originalImageUrl)
                    .addQueryParameter("cbir_id", cbirId)
                    .build()
                    .toString()
            }
        } finally {
            file.delete()
        }
    }

    private suspend fun resolveYandexTitle(resultUrl: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resultUrl)
            .header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36")
            .get()
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Yandex result fetch failed: HTTP ${response.code}" }
            val html = response.body?.string().orEmpty()
            val tagBlock = Regex("&quot;cbirTags&quot;:\\{&quot;tags&quot;:\\[(.*?)\\]")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
            val encodedTitle = Regex("&quot;text&quot;:&quot;([^&]+)")
                .find(tagBlock)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
            org.jsoup.nodes.Entities.unescape(encodedTitle)
                .trim()
                .takeIf { it.length >= 2 }
                ?: error("Yandex returned no image label")
        }
    }

    override fun onDestroy() {
        sourceFile?.delete()
        super.onDestroy()
    }

    companion object { const val EXTRA_PATH = "com.krystelligence.solipsism.extra.SCREENSHOT_PATH" }
}
