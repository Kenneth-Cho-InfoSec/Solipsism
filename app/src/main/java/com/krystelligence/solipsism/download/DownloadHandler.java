/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.download;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.File;
import java.io.IOException;

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
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Handle download requests
 */
@Singleton
public class DownloadHandler {

    private static final String TAG = "DownloadHandler";

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
        final String filename = FileUtils.sanitizeFileName(
            URLUtil.guessFileName(url, contentDisposition, mimetype)
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
