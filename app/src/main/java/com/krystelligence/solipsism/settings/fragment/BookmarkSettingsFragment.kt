/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.bookmark.LegacyBookmarkImporter
import com.krystelligence.solipsism.bookmark.NetscapeBookmarkFormatImporter
import com.krystelligence.solipsism.bookmark.DecoyBookmarkFactory
import com.krystelligence.solipsism.browser.di.DatabaseScheduler
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.database.bookmark.BookmarkExporter
import com.krystelligence.solipsism.database.bookmark.BookmarkRepository
import com.krystelligence.solipsism.database.Bookmark
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.dialog.DialogItem
import com.krystelligence.solipsism.extensions.fileInputStream
import com.krystelligence.solipsism.extensions.fileName
import com.krystelligence.solipsism.extensions.fileOutputStream
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.extensions.toast
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.utils.Utils
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.settings.preference.LongPressPreference
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject

class BookmarkSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var bookmarkRepository: BookmarkRepository
    @Inject internal lateinit var application: Application
    @Inject internal lateinit var netscapeBookmarkFormatImporter: NetscapeBookmarkFormatImporter
    @Inject internal lateinit var legacyBookmarkImporter: LegacyBookmarkImporter
    @Inject @DatabaseScheduler internal lateinit var databaseScheduler: Scheduler
    @Inject @MainScheduler internal lateinit var mainScheduler: Scheduler
    @Inject internal lateinit var logger: Logger
    @Inject internal lateinit var userPreferences: UserPreferences

    private var importSubscription: Disposable? = null
    private var exportDisposable: Disposable? = null

    override fun providePreferencesXmlResource() = R.xml.preference_bookmarks

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        clickablePreference(preference = SETTINGS_EXPORT, onClick = this::showBookmarkExportChooser)
        val importPreference = findPreference<LongPressPreference>(SETTINGS_IMPORT)!!
        importPreference.onPreferenceClickListener = androidx.preference.Preference.OnPreferenceClickListener {
            showFileChooser()
            true
        }
        importPreference.onLongPress = {
            showBookmarkDecoyModePrompt()
            true
        }
        clickablePreference(
            preference = SETTINGS_DELETE_BOOKMARKS,
            onClick = this::deleteAllBookmarks
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroyView() {
        super.onDestroyView()

        exportDisposable?.dispose()
        importSubscription?.dispose()
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        super.onDestroy()

        exportDisposable?.dispose()
        importSubscription?.dispose()
    }

    private fun exportBookmarksToUri(uri: Uri) {
        bookmarkRepository.getAllBookmarksSorted()
            .subscribeOn(databaseScheduler)
            .subscribe { list ->
                if (!isAdded) {
                    return@subscribe
                }

                val fileName = activity?.fileName(uri).orEmpty()
                val outputStream =
                    activity?.fileOutputStream(uri) ?: return@subscribe showExportError()
                exportDisposable?.dispose()
                exportDisposable =
                    outputStream
                        .flatMapCompletable {
                            BookmarkExporter.exportBookmarksToOutputStream(list, it)
                        }
                        .subscribeOn(databaseScheduler)
                        .observeOn(mainScheduler)
                        .subscribeBy(
                            onComplete = {
                                activity?.apply {
                                    snackbar("${getString(R.string.bookmark_export_path)} $fileName")
                                }
                            },
                            onError = { throwable ->
                                logger.log(TAG, "onError: exporting bookmarks", throwable)
                                showExportError()
                            }
                        )
            }
    }

    private fun showExportError() {
        val activity = activity
        if (activity != null && !activity.isFinishing && isAdded) {
            Utils.createInformativeDialog(
                activity,
                R.string.title_error,
                R.string.bookmark_export_failure
            )
        } else {
            application.toast(R.string.bookmark_export_failure)
        }
    }

    private fun deleteAllBookmarks() {
        showDeleteBookmarksDialog()
    }

    private fun showDeleteBookmarksDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            activity = requireActivity(),
            title = R.string.action_delete,
            message = R.string.action_delete_all_bookmarks,
            positiveButton = DialogItem(title = R.string.yes) {
                bookmarkRepository
                    .deleteAllBookmarks()
                    .subscribeOn(databaseScheduler)
                    .subscribe()
            },
            negativeButton = DialogItem(title = R.string.no) {},
            onCancel = {}
        )
    }

    private fun showBookmarkExportChooser() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, BOOKMARK_EXPORT_FILE)
        }

        startActivityForResult(intent, EXPORT_FILE_REQUEST_CODE)
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = TEXT_MIME_TYPE
        }

        startActivityForResult(intent, IMPORT_FILE_REQUEST_CODE)
    }

    private fun showBookmarkDecoyModePrompt() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bookmark_decoy_mode_title)
            .setMessage(R.string.bookmark_decoy_mode_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.bookmark_decoy_mode_start) { _, _ ->
                enableBookmarkDecoyMode()
            }
            .show()
    }

    private fun enableBookmarkDecoyMode() {
        importSubscription?.dispose()
        importSubscription = bookmarkRepository
            .replaceAllBookmarks(DecoyBookmarkFactory.create())
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(
                onComplete = {
                    userPreferences.bookmarkDecoyModeEnabled = true
                    activity?.snackbar(R.string.bookmark_decoy_mode_enabled)
                },
                onError = {
                    logger.log(TAG, "Unable to enable bookmark decoy mode", it)
                    activity?.toast(R.string.bookmark_decoy_mode_failed)
                }
            )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            IMPORT_FILE_REQUEST_CODE,
            EXPORT_FILE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK && requestCode == IMPORT_FILE_REQUEST_CODE) {
                    data?.data?.also(::importBookmarksFromUri)
                } else if (resultCode == Activity.RESULT_OK && requestCode == EXPORT_FILE_REQUEST_CODE) {
                    data?.data?.also(::exportBookmarksToUri)
                } else {
                    activity?.toast(R.string.action_message_canceled)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun importBookmarksFromUri(uri: Uri) {
        val fileName = activity?.fileName(uri)

        val inputStream = activity?.fileInputStream(uri) ?: return

        importSubscription?.dispose()
        importSubscription = inputStream
            .map {
                if (fileName?.endsWith(EXTENSION_HTML) == true) {
                    netscapeBookmarkFormatImporter.importBookmarks(it)
                } else {
                    legacyBookmarkImporter.importBookmarks(it)
                }
            }
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(
                onSuccess = { imported -> handleImportedBookmarks(imported) },
                onError = {
                    logger.log(TAG, "onError: importing bookmarks", it)
                    showImportError()
                }
            )
    }

    private fun handleImportedBookmarks(imported: List<Bookmark.Entry>) {
        if (imported.isEmpty()) {
            showImportError()
            return
        }
        when (userPreferences.bookmarkImportMode) {
            IMPORT_MODE_REPLACE -> confirmBookmarkReplacement(imported)
            IMPORT_MODE_ASK -> showImportChoice(imported)
            else -> applyImportedBookmarks(imported, replace = false)
        }
    }

    private fun showImportChoice(imported: List<Bookmark.Entry>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bookmark_import_behaviour)
            .setMessage(getString(R.string.bookmark_import_choice_message, imported.size))
            .setNegativeButton(R.string.bookmark_import_merge) { _, _ ->
                applyImportedBookmarks(imported, replace = false)
            }
            .setPositiveButton(R.string.bookmark_import_replace) { _, _ ->
                confirmBookmarkReplacement(imported)
            }
            .show()
    }

    private fun confirmBookmarkReplacement(imported: List<Bookmark.Entry>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bookmark_import_replace)
            .setMessage(R.string.bookmark_import_replace_warning)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.yes) { _, _ ->
                applyImportedBookmarks(imported, replace = true)
            }
            .show()
    }

    private fun applyImportedBookmarks(imported: List<Bookmark.Entry>, replace: Boolean) {
        importSubscription?.dispose()
        val operation = if (replace) {
            bookmarkRepository.replaceAllBookmarks(imported)
        } else {
            bookmarkRepository.addBookmarkList(imported)
        }
        importSubscription = operation
            .andThen(Single.just(imported.size))
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(
                onSuccess = { count ->
                    userPreferences.bookmarkDecoyModeEnabled = false
                    activity?.snackbar("$count ${getString(R.string.message_import)}")
                },
                onError = {
                    logger.log(TAG, "onError: saving imported bookmarks", it)
                    showImportError()
                }
            )
    }

    private fun showImportError() {
        val activity = activity
        if (activity != null && !activity.isFinishing && isAdded) {
            Utils.createInformativeDialog(
                activity,
                R.string.title_error,
                R.string.import_bookmark_error
            )
        } else {
            application.toast(R.string.import_bookmark_error)
        }
    }

    companion object {

        private const val IMPORT_FILE_REQUEST_CODE = 100
        private const val EXPORT_FILE_REQUEST_CODE = 101
        private const val TEXT_MIME_TYPE = "text/*"

        private const val TAG = "BookmarkSettingsFrag"

        private const val EXTENSION_HTML = "html"

        private const val BOOKMARK_EXPORT_FILE = "ExportedBookmarks.txt"
        private const val IMPORT_MODE_REPLACE = "replace"
        private const val IMPORT_MODE_ASK = "ask"

        private const val SETTINGS_EXPORT = "export_bookmark"
        private const val SETTINGS_IMPORT = "import_bookmark"
        private const val SETTINGS_DELETE_BOOKMARKS = "delete_bookmarks"

    }
}
