/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.download;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.krystelligence.solipsism.R;
import com.krystelligence.solipsism.browser.di.MainScheduler;
import com.krystelligence.solipsism.browser.di.NetworkScheduler;
import com.krystelligence.solipsism.constant.Constants;
import com.krystelligence.solipsism.dialog.BrowserDialog;
import com.krystelligence.solipsism.extensions.ActivityExtensions;
import com.krystelligence.solipsism.log.Logger;
import com.krystelligence.solipsism.preference.UserPreferences;
import com.krystelligence.solipsism.utils.FileUtils;
import com.krystelligence.solipsism.utils.Utils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Handle download requests
 */
@Singleton
public class DownloadHandler {

    private static final String TAG = "DownloadHandler";
    private static final int MAX_BLOB_BYTES = 16 * 1024 * 1024;
    private static final int MAX_BLOB_BASE64_CHARS = ((MAX_BLOB_BYTES + 2) / 3) * 4;

    private static final String COOKIE_REQUEST_HEADER = "Cookie";
    private static final String USER_AGENT_REQUEST_HEADER = "User-Agent";

    private final DownloadManager downloadManager;
    private final Scheduler networkScheduler;
    private final Scheduler mainScheduler;
    private final Logger logger;

    @Inject
    public DownloadHandler(DownloadManager downloadManager,
                           @NetworkScheduler Scheduler networkScheduler,
                           @MainScheduler Scheduler mainScheduler,
                           Logger logger) {
        this.downloadManager = downloadManager;
        this.networkScheduler = networkScheduler;
        this.mainScheduler = mainScheduler;
        this.logger = logger;
    }

    public void onDownloadStart(@NonNull Activity context,
                                @NonNull UserPreferences manager,
                                @NonNull String url, String userAgent,
                                @Nullable String contentDisposition,
                                @Nullable String mimeType,
                                @NonNull String contentSize) {

        logger.log(TAG, "DOWNLOAD: Trying to download from URL: " + url);

        // A download request must always be sent to DownloadManager. Opening
        // inline responses with ACTION_VIEW makes images/videos appear to
        // work, but sends other MIME types back to a viewer or to this browser
        // instead of saving them.
        onDownloadStartNoStream(context, manager, url, userAgent, contentDisposition, mimeType, contentSize);
    }

    /**
     * Save bytes extracted from a WebView-owned blob URL. DownloadManager only
     * accepts network URLs, so blob data must be persisted by the app itself.
     */
    public Single<String> downloadBlob(@NonNull Activity context,
                                       @NonNull UserPreferences preferences,
                                       @NonNull String sourceUrl,
                                       @Nullable String contentDisposition,
                                       @Nullable String mimeType,
                                       @NonNull String base64Data) {
        return Single.fromCallable(() -> {
            if (base64Data.length() > MAX_BLOB_BASE64_CHARS) {
                throw new IOException("Blob download exceeds the safety limit");
            }
            String filename = FileUtils.sanitizeFileName(
                URLUtil.guessFileName(sourceUrl, contentDisposition, mimeType)
            );
            String contentType = TextUtils.isEmpty(mimeType)
                ? "application/octet-stream"
                : mimeType;
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            if (bytes.length > MAX_BLOB_BYTES) {
                throw new IOException("Blob download exceeds the safety limit");
            }
            if (preferences.getSaveImagesAsJpeg() && DownloadFilenameResolver.isRasterImage(contentType)) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    filename = filename.replaceFirst("(?i)\\.[^.]+$", "") + ".jpg";
                    java.io.ByteArrayOutputStream converted = new java.io.ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, converted);
                    bitmap.recycle();
                    bytes = converted.toByteArray();
                    contentType = "image/jpeg";
                }
            }
            String location = FileUtils.addNecessarySlashes(preferences.getDownloadDirectory());
            String defaultPath = FileUtils.addNecessarySlashes(FileUtils.DEFAULT_DOWNLOAD_PATH);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && location.equalsIgnoreCase(defaultPath)) {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, contentType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Unable to create download entry");
                try {
                    OutputStream output = resolver.openOutputStream(uri);
                    if (output == null) throw new IOException("Unable to open download entry");
                    try (OutputStream stream = output) {
                        stream.write(bytes);
                    }
                    ContentValues completed = new ContentValues();
                    completed.put(MediaStore.Downloads.IS_PENDING, 0);
                    resolver.update(uri, completed, null, null);
                    return uri.toString();
                } catch (Exception exception) {
                    resolver.delete(uri, null, null);
                    throw exception;
                }
            }

            File directory = new File(location);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Unable to create download directory");
            }
            File outputFile = new File(directory, filename);
            try (FileOutputStream output = new FileOutputStream(outputFile)) {
                output.write(bytes);
            }
            return outputFile.toURI().toString();
        });
    }

    /** Downloads and converts a raster image without routing it through DownloadManager. */
    public Single<String> downloadImageAsJpeg(@NonNull Activity context,
                                               @NonNull UserPreferences preferences,
                                               @NonNull String url,
                                               @Nullable String userAgent,
                                               @NonNull String filename) {
        return Single.fromCallable(() -> {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(120000);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (!TextUtils.isEmpty(cookie)) connection.setRequestProperty(COOKIE_REQUEST_HEADER, cookie);
            if (!TextUtils.isEmpty(userAgent)) connection.setRequestProperty(USER_AGENT_REQUEST_HEADER, userAgent);
            try (InputStream input = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) throw new IOException("Unable to decode image");
                File temp = File.createTempFile("jpeg_", ".jpg", context.getCacheDir());
                try (FileOutputStream output = new FileOutputStream(temp)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw new IOException("Unable to encode JPEG");
                } finally { bitmap.recycle(); }
                String jpgName = filename.replaceFirst("(?i)\\.[^.]+$", "") + ".jpg";
                try { return publishScannedFile(context, preferences, temp, jpgName, "image/jpeg"); }
                finally { temp.delete(); }
            } finally { connection.disconnect(); }
        });
    }

    /**
     * Publishes bytes that have already passed a security scan. The source is app-private and the
     * MediaStore entry remains pending until the copy has completed.
     */
    public String publishScannedFile(@NonNull Activity context,
                                     @NonNull UserPreferences preferences,
                                     @NonNull File source,
                                     @NonNull String filename,
                                     @Nullable String mimeType) throws IOException {
        String contentType = TextUtils.isEmpty(mimeType)
            ? "application/octet-stream"
            : mimeType;
        String location = FileUtils.addNecessarySlashes(preferences.getDownloadDirectory());
        String defaultPath = FileUtils.addNecessarySlashes(FileUtils.DEFAULT_DOWNLOAD_PATH);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            && location.equalsIgnoreCase(defaultPath)) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, contentType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Unable to create download entry");
            try {
                OutputStream output = resolver.openOutputStream(uri);
                if (output == null) throw new IOException("Unable to open download entry");
                try (FileInputStream input = new FileInputStream(source);
                     OutputStream stream = output) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        stream.write(buffer, 0, count);
                    }
                }
                ContentValues completed = new ContentValues();
                completed.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, completed, null, null);
                return uri.toString();
            } catch (Exception exception) {
                resolver.delete(uri, null, null);
                if (exception instanceof IOException) throw (IOException) exception;
                throw new IOException(exception);
            }
        }

        File directory = new File(location);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create download directory");
        }
        File outputFile = availableFile(directory, filename);
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return outputFile.toURI().toString();
    }

    @NonNull
    private static File availableFile(@NonNull File directory, @NonNull String filename) {
        File requested = new File(directory, filename);
        if (!requested.exists()) return requested;
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        int suffix = 1;
        File candidate;
        do {
            candidate = new File(directory, stem + " (" + suffix++ + ")" + extension);
        } while (candidate.exists());
        return candidate;
    }

    @NonNull
    private static String encodePath(@NonNull String path) {
        char[] chars = path.toCharArray();
        boolean needed = false;
        for (char c : chars) {
            if (c == '[' || c == ']' || c == '|') {
                needed = true;
                break;
            }
        }
        if (!needed) return path;

        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            if (c == '[' || c == ']' || c == '|') {
                sb.append('%');
                sb.append(Integer.toHexString(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void onDownloadStartNoStream(@NonNull final Activity context,
                                         @NonNull UserPreferences preferences,
                                         @NonNull String url, String userAgent,
                                         @Nullable String contentDisposition,
                                         @Nullable String mimetype,
                                         @NonNull String contentSize) {
        final String filename = DownloadFilenameResolver.resolve(
            url, contentDisposition, mimetype, false
        );

        String status = Environment.getExternalStorageState();
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            int title = status.equals(Environment.MEDIA_SHARED) ? R.string.download_sdcard_busy_dlg_title : R.string.download_no_sdcard_dlg_title;
            String msg = status.equals(Environment.MEDIA_SHARED) ? context.getString(R.string.download_sdcard_busy_dlg_msg) : context.getString(R.string.download_no_sdcard_dlg_msg);

            Dialog dialog = new AlertDialog.Builder(context).setTitle(title)
                .setIcon(android.R.drawable.ic_dialog_alert).setMessage(msg)
                .setPositiveButton(R.string.action_ok, null).show();
            BrowserDialog.setDialogSize(context, dialog);
            return;
        }

        WebAddress webAddress;
        try {
            webAddress = new WebAddress(url);
            webAddress.setPath(encodePath(webAddress.getPath()));
        } catch (Exception e) {
            ActivityExtensions.snackbar(context, R.string.problem_download);
            return;
        }

        String addressString = webAddress.toString();
        final DownloadManager.Request request;
        try {
            request = new DownloadManager.Request(Uri.parse(addressString));
        } catch (IllegalArgumentException e) {
            ActivityExtensions.snackbar(context, R.string.cannot_download);
            return;
        }

        String location = preferences.getDownloadDirectory();
        String slashedDefaultPath = FileUtils.addNecessarySlashes(FileUtils.DEFAULT_DOWNLOAD_PATH);
        String slashedLocation = FileUtils.addNecessarySlashes(location);
        boolean isDefaultPath = slashedLocation.equalsIgnoreCase(slashedDefaultPath);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !isDefaultPath) {
            if (!isWriteAccessAvailable(Uri.parse(slashedLocation))) {
                ActivityExtensions.snackbar(context, R.string.problem_location_download);
                return;
            }
        }

        String newMimeType = mimetype;
        if (!TextUtils.isEmpty(newMimeType)) {
            int semicolonIndex = newMimeType.indexOf(';');
            if (semicolonIndex != -1) {
                newMimeType = newMimeType.substring(0, semicolonIndex).trim();
            }
        }
        if (TextUtils.isEmpty(newMimeType)) {
            newMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Utils.guessFileExtension(filename));
        }
        if (!TextUtils.isEmpty(newMimeType)) {
            request.setMimeType(newMimeType);
        }
        request.setTitle(filename);
        
        if (isDefaultPath) {
             request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
        } else {
             request.setDestinationUri(Uri.parse(Constants.FILE + slashedLocation + filename));
        }

        request.setVisibleInDownloadsUi(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            request.allowScanningByMediaScanner();
        }
        request.setDescription(webAddress.getHost());
        String cookies = CookieManager.getInstance().getCookie(url);
        if (!TextUtils.isEmpty(cookies)) {
            request.addRequestHeader(COOKIE_REQUEST_HEADER, cookies);
        }
        if (!TextUtils.isEmpty(userAgent)) {
            request.addRequestHeader(USER_AGENT_REQUEST_HEADER, userAgent);
        }
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        if (mimetype == null) {
            if (TextUtils.isEmpty(addressString)) return;
            final Disposable disposable = new FetchUrlMimeType(downloadManager, request, addressString, cookies, userAgent)
                .create()
                .subscribeOn(networkScheduler)
                .observeOn(mainScheduler)
                .subscribe(result -> {
                    switch (result) {
                        case FAILURE_ENQUEUE: ActivityExtensions.snackbar(context, R.string.cannot_download); break;
                        case FAILURE_LOCATION: ActivityExtensions.snackbar(context, R.string.problem_location_download); break;
                        case SUCCESS: ActivityExtensions.snackbar(context, R.string.download_pending); break;
                    }
                });
        } else {
            try {
                downloadManager.enqueue(request);
                ActivityExtensions.snackbar(context, context.getString(R.string.download_pending) + ' ' + filename);
            } catch (Exception e) {
                logger.log(TAG, "Unable to enqueue request", e);
                ActivityExtensions.snackbar(context, R.string.cannot_download);
            }
        }
    }

    private static boolean isWriteAccessAvailable(@NonNull Uri fileUri) {
        if (fileUri.getPath() == null) return false;
        File file = new File(fileUri.getPath());
        if (!file.isDirectory() && !file.mkdirs()) return false;
        try {
            File testFile = new File(file, "test_write_access_" + System.currentTimeMillis());
            if (testFile.createNewFile()) {
                testFile.delete();
                return true;
            }
        } catch (IOException ignored) {}
        return false;
    }
}
