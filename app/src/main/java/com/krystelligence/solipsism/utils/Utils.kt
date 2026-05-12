package com.krystelligence.solipsism.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.URLUtil
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.DefaultBrowserActivity
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.constant.HTTPS
import com.krystelligence.solipsism.database.HistoryEntry
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.extensions.snackbar
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.Date

object Utils {

    private const val TAG = "Utils"

    /**
     * Creates a new intent that can launch the email app with a subject, address, body, and cc.
     */
    @JvmStatic
    fun newEmailIntent(address: String?, subject: String?, body: String?, cc: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_CC, cc)
            type = "message/rfc822"
        }

    /**
     * Creates a dialog with only a title, message, and okay button.
     */
    @JvmStatic
    fun createInformativeDialog(activity: Activity, @StringRes title: Int, @StringRes message: Int) {
        val builder = MaterialAlertDialogBuilder(activity)
        builder.setTitle(title)
        builder.setMessage(message)
            .setCancelable(true)
            .setPositiveButton(activity.resources.getString(R.string.action_ok)) { _, _ -> }
        val alert = builder.create()
        alert.show()
        BrowserDialog.setDialogSize(activity, alert)
    }

    /**
     * Converts Density Pixels (DP) to Pixels (PX).
     */
    @JvmStatic
    fun dpToPx(dp: Float): Int {
        val metrics = Resources.getSystem().displayMetrics
        return (dp * metrics.density + 0.5f).toInt()
    }

    /**
     * Extracts the domain name from a URL. NOTE: Should be used for display only.
     */
    @JvmStatic
    fun getDisplayDomainName(url: String?): String {
        if (url.isNullOrEmpty()) return ""

        var adjustedUrl = url
        val ssl = URLUtil.isHttpsUrl(adjustedUrl)
        val index = adjustedUrl.indexOf('/', 8)
        if (index != -1) {
            adjustedUrl = adjustedUrl.substring(0, index)
        }

        val domain = try {
            URI(adjustedUrl).host
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Unable to parse URI", e)
            null
        }

        if (domain.isNullOrEmpty()) {
            return adjustedUrl
        }

        return if (ssl) {
            HTTPS + domain
        } else {
            domain.removePrefix("www.")
        }
    }

    @JvmStatic
    fun trimCache(context: Context) {
        try {
            val dir = context.cacheDir
            if (dir != null && dir.isDirectory) {
                deleteDir(dir)
            }
        } catch (_: Exception) {
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (child in children) {
                    val success = deleteDir(File(dir, child))
                    if (!success) {
                        return false
                    }
                }
            }
        }
        return dir != null && dir.delete()
    }

    @JvmStatic
    fun isColorGrayscale(pixel: Int): Boolean {
        val red = pixel and 0x00FF0000 shr 16
        val green = pixel and 0x0000FF00 shr 8
        val blue = pixel and 0x000000FF
        return red == green && green == blue
    }

    @JvmStatic
    fun isColorTooDark(color: Int): Boolean {
        val redChannel = 16
        val greenChannel = 8

        val r = ((color shr redChannel and 0xff) * 0.3f).toInt() and 0xff
        val g = ((color shr greenChannel and 0xff) * 0.59f).toInt() and 0xff
        val b = ((color and 0xff) * 0.11f).toInt() and 0xff
        val gr = (r + g + b) and 0xff
        val gray = gr + (gr shl greenChannel) + (gr shl redChannel)

        return gray < 0x727272
    }

    @JvmStatic
    fun mixTwoColors(color1: Int, color2: Int, amount: Float): Int {
        val alphaChannel = 24
        val redChannel = 16
        val greenChannel = 8

        val inverseAmount = 1.0f - amount

        val r = (((color1 shr redChannel and 0xff) * amount) +
            ((color2 shr redChannel and 0xff) * inverseAmount)).toInt() and 0xff
        val g = (((color1 shr greenChannel and 0xff) * amount) +
            ((color2 shr greenChannel and 0xff) * inverseAmount)).toInt() and 0xff
        val b = (((color1 and 0xff) * amount) +
            ((color2 and 0xff) * inverseAmount)).toInt() and 0xff

        return (0xff shl alphaChannel) or (r shl redChannel) or (g shl greenChannel) or b
    }

    @SuppressLint("SimpleDateFormat")
    @JvmStatic
    @Throws(IOException::class)
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    /**
     * Quietly closes a closeable object like an InputStream or OutputStream.
     */
    @JvmStatic
    fun close(closeable: Closeable?) {
        if (closeable == null) {
            return
        }
        try {
            closeable.close()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to close closeable", e)
        }
    }

    @JvmStatic
    fun createShortcut(activity: Activity, historyEntry: HistoryEntry, favicon: Bitmap) {
        createShortcut(activity, historyEntry.url, historyEntry.title, favicon)
    }

    @JvmStatic
    fun createShortcut(
        activity: Activity,
        url: String,
        unsafeTitle: String,
        unsafeFavicon: Bitmap?
    ) {
        val shortcutIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            setClass(activity, DefaultBrowserActivity::class.java)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val title = if (unsafeTitle.isEmpty()) activity.getString(R.string.untitled) else unsafeTitle
        val webPageDrawable = checkNotNull(ContextCompat.getDrawable(activity, R.drawable.ic_webpage))
        val webPageBitmap = webPageDrawable.toBitmap(
            webPageDrawable.intrinsicWidth,
            webPageDrawable.intrinsicHeight,
            null
        )

        val favicon = (unsafeFavicon ?: webPageBitmap).let { bitmap ->
            val iconSize = activity.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            if (bitmap.width > iconSize || bitmap.height > iconSize) {
                Bitmap.createScaledBitmap(bitmap, iconSize, iconSize, true)
            } else {
                bitmap
            }
        }

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(activity)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(
                activity,
                "browser-shortcut-${url.hashCode()}"
            )
                .setActivity(ComponentName(activity, DefaultBrowserActivity::class.java))
                .setIntent(shortcutIntent)
                .setIcon(IconCompat.createWithBitmap(favicon))
                .setShortLabel(title.take(MAX_SHORTCUT_LABEL_LENGTH).ifBlank {
                    activity.getString(R.string.untitled)
                })
                .setLongLabel(title)
                .build()

            if (ShortcutManagerCompat.requestPinShortcut(activity, pinShortcutInfo, null)) {
                activity.snackbar(R.string.message_added_to_homescreen)
            } else {
                activity.snackbar(R.string.shortcut_message_failed_to_add)
            }
        } else {
            activity.snackbar(R.string.shortcut_message_failed_to_add)
        }
    }

    @JvmStatic
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    @JvmStatic
    fun guessFileExtension(filename: String): String? {
        val lastIndex = filename.lastIndexOf('.') + 1
        return if (lastIndex > 0 && filename.length > lastIndex) {
            filename.substring(lastIndex)
        } else {
            null
        }
    }
}

private const val MAX_SHORTCUT_LABEL_LENGTH = 12
