package com.krystelligence.solipsism.utils

import android.app.Application
import android.os.Bundle
import android.os.Environment
import android.os.Parcel
import android.util.Log
import io.reactivex.rxjava3.core.Completable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream

object FileUtils {

    private const val TAG = "FileUtils"

    @JvmField
    val DEFAULT_DOWNLOAD_PATH: String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path

    /**
     * Writes a bundle to persistent storage in the files directory using the specified file name.
     */
    @JvmStatic
    fun writeBundleToStorage(app: Application, bundle: Bundle, name: String): Completable =
        Completable.fromAction {
            val outputFile = File(app.filesDir, name)
            val temporaryFile = File(app.filesDir, "$name.tmp")
            val parcel = Parcel.obtain()
            val serialized = try {
                parcel.writeBundle(bundle)
                if (parcel.hasFileDescriptors()) {
                    Log.w(TAG, "Skipping bundle snapshot containing file descriptors")
                    null
                } else {
                    parcel.marshall()
                }
            } catch (error: RuntimeException) {
                // WebView state may temporarily contain Binder objects or file descriptors, for
                // example while a system file chooser is open. A failed snapshot must never take
                // down the browser; the previous valid snapshot remains available instead.
                Log.w(TAG, "Skipping bundle snapshot that cannot be serialized", error)
                null
            } finally {
                parcel.recycle()
            } ?: return@fromAction

            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(temporaryFile)
                outputStream.write(serialized)
                outputStream.flush()
                outputStream.close()
                outputStream = null
                if (!temporaryFile.renameTo(outputFile)) {
                    throw IOException("Unable to replace bundle snapshot")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Unable to write bundle to storage", e)
            } finally {
                Utils.close(outputStream)
                if (temporaryFile.exists()) temporaryFile.delete()
            }
        }

    /**
     * Use this method to delete the bundle with the specified name.
     */
    @JvmStatic
    fun deleteBundleInStorage(app: Application, name: String) {
        val outputFile = File(app.filesDir, name)
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }

    /**
     * Reads a bundle from the file with the specified name in the persistent storage files
     * directory.
     */
    @JvmStatic
    fun readBundleFromStorage(app: Application, name: String): Bundle? {
        val inputFile = File(app.filesDir, name)
        var inputStream: FileInputStream? = null
        var parcel: Parcel? = null
        try {
            inputStream = FileInputStream(inputFile)
            parcel = Parcel.obtain()
            val data = ByteArray(inputStream.channel.size().toInt())

            inputStream.read(data, 0, data.size)
            parcel.unmarshall(data, 0, data.size)
            parcel.setDataPosition(0)
            val out = parcel.readBundle(ClassLoader.getSystemClassLoader())
            out?.putAll(out)
            return out
        } catch (_: FileNotFoundException) {
            Log.e(TAG, "Unable to read bundle from storage")
        } catch (e: IOException) {
            Log.e(TAG, "Unable to read bundle from storage", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to deserialize bundle from storage", e)
        } finally {
            parcel?.recycle()
            Utils.close(inputStream)
        }
        return null
    }

    /**
     * Writes a stacktrace to app storage.
     */
    @JvmStatic
    fun writeCrashToStorage(app: Application, throwable: Throwable) {
        val fileName = throwable::class.java.simpleName + '_' + System.currentTimeMillis() + ".txt"
        val crashDir = File(app.filesDir, "crashes")
        crashDir.mkdirs()
        val outputFile = File(crashDir, sanitizeFileName(fileName))

        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(outputFile)
            throwable.printStackTrace(PrintStream(outputStream))
            outputStream.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to write bundle to storage")
        } finally {
            Utils.close(outputStream)
        }
    }

    @JvmStatic
    fun sanitizeFileName(fileName: String?): String {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "download"
        }
        var sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim()
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1)
        }
        if (sanitized.isEmpty()) {
            return "download"
        }
        if (sanitized.length > 120) {
            sanitized = sanitized.substring(0, 120)
        }
        return sanitized
    }

    /**
     * Converts megabytes to bytes.
     */
    @JvmStatic
    fun megabytesToBytes(megaBytes: Long): Long = megaBytes * 1024 * 1024

    /**
     * Determine whether there is write access in the given directory.
     */
    @JvmStatic
    fun isWriteAccessAvailable(directory: String?): Boolean {
        if (directory.isNullOrEmpty()) {
            return false
        }

        val fileName = "test"
        val fileExtension = ".txt"
        var dir = addNecessarySlashes(directory)
        dir = getFirstRealParentDirectory(dir)
        var file = File(dir + fileName + fileExtension)
        for (n in 0 until 100) {
            if (!file.exists()) {
                try {
                    if (file.createNewFile()) {
                        file.delete()
                    }
                    return true
                } catch (_: IOException) {
                    return false
                }
            } else {
                file = File(dir + fileName + '-' + n + fileExtension)
            }
        }
        return file.canWrite()
    }

    /**
     * Returns the first parent directory of a directory that exists.
     */
    private fun getFirstRealParentDirectory(directory: String?): String {
        var currentDirectory = directory
        while (true) {
            if (currentDirectory.isNullOrEmpty()) {
                return "/"
            }
            currentDirectory = addNecessarySlashes(currentDirectory)
            val file = File(currentDirectory)
            if (!file.isDirectory) {
                val indexSlash = currentDirectory.lastIndexOf('/')
                if (indexSlash > 0) {
                    val parent = currentDirectory.substring(0, indexSlash)
                    val previousIndex = parent.lastIndexOf('/')
                    currentDirectory = if (previousIndex > 0) {
                        parent.substring(0, previousIndex)
                    } else {
                        return "/"
                    }
                } else {
                    return "/"
                }
            } else {
                return currentDirectory
            }
        }
    }

    @JvmStatic
    fun addNecessarySlashes(originalPath: String?): String {
        if (originalPath.isNullOrEmpty()) {
            return "/"
        }
        var path = originalPath
        if (path.last() != '/') {
            path += '/'
        }
        if (path.first() != '/') {
            path = "/$path"
        }
        return path
    }
}
