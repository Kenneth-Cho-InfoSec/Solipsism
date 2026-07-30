package com.krystelligence.solipsism.settings.fragment

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.DatabaseScheduler
import com.krystelligence.solipsism.browser.di.MainScheduler
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.browser.data.CookieManagerDialog
import com.krystelligence.solipsism.browser.data.CookieManagerRepository
import com.krystelligence.solipsism.browser.tab.WebViewFactory
import com.krystelligence.solipsism.database.history.HistoryRepository
import com.krystelligence.solipsism.dialog.BrowserDialog
import com.krystelligence.solipsism.dialog.DialogItem
import com.krystelligence.solipsism.extensions.snackbar
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.utils.WebUtils
import android.os.Bundle
import android.webkit.WebView
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import javax.inject.Inject

class PrivacySettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var historyRepository: HistoryRepository
    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject @DatabaseScheduler internal lateinit var databaseScheduler: Scheduler
    @Inject @MainScheduler internal lateinit var mainScheduler: Scheduler
    @Inject internal lateinit var cookieManagerRepository: CookieManagerRepository

    override fun providePreferencesXmlResource() = R.xml.preference_privacy

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        clickablePreference("cookie_manager") {
            CookieManagerDialog.promptForUrl(requireContext(), cookieManagerRepository)
        }

        clickablePreference(preference = SETTINGS_CLEARCACHE, onClick = this::clearCache)
        clickablePreference(preference = SETTINGS_CLEARHISTORY, onClick = this::clearHistoryDialog)
        clickablePreference(preference = SETTINGS_CLEARCOOKIES, onClick = this::clearCookiesDialog)
        clickablePreference(preference = SETTINGS_CLEARWEBSTORAGE, onClick = this::clearWebStorage)

        togglePreference(
            preference = SETTINGS_THIRDPCOOKIES,
            isChecked = userPreferences.blockThirdPartyCookiesEnabled,
            onCheckChange = { userPreferences.blockThirdPartyCookiesEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_CACHEEXIT,
            isChecked = userPreferences.clearCacheExit,
            onCheckChange = { userPreferences.clearCacheExit = it }
        )

        togglePreference(
            preference = SETTINGS_HISTORYEXIT,
            isChecked = userPreferences.clearHistoryExitEnabled,
            onCheckChange = { userPreferences.clearHistoryExitEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_COOKIEEXIT,
            isChecked = userPreferences.clearCookiesExitEnabled,
            onCheckChange = { userPreferences.clearCookiesExitEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_WEBSTORAGEEXIT,
            isChecked = userPreferences.clearWebStorageExitEnabled,
            onCheckChange = { userPreferences.clearWebStorageExitEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_DONOTTRACK,
            isChecked = userPreferences.doNotTrackEnabled,
            onCheckChange = { userPreferences.doNotTrackEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_WEBRTC,
            isChecked = userPreferences.webRtcEnabled,
            onCheckChange = { userPreferences.webRtcEnabled = it }
        )

        togglePreference(
            preference = SETTINGS_IDENTIFYINGHEADERS,
            isChecked = userPreferences.removeIdentifyingHeadersEnabled,
            summary = "${WebViewFactory.HEADER_REQUESTED_WITH}, ${WebViewFactory.HEADER_WAP_PROFILE}",
            onCheckChange = { userPreferences.removeIdentifyingHeadersEnabled = it }
        )

    }

    private fun clearHistoryDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            activity = requireActivity(),
            title = R.string.title_clear_history,
            message = R.string.dialog_history,
            positiveButton = DialogItem(title = R.string.action_yes) {
                clearHistory()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe {
                        requireActivity().snackbar(R.string.message_clear_history)
                    }
            },
            negativeButton = DialogItem(title = R.string.action_no) {},
            onCancel = {}
        )
    }

    private fun clearCookiesDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            activity = requireActivity(),
            title = R.string.title_clear_cookies,
            message = R.string.dialog_cookies,
            positiveButton = DialogItem(title = R.string.action_yes) {
                clearCookies()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe {
                        requireActivity().snackbar(R.string.message_cookies_cleared)
                    }
            },
            negativeButton = DialogItem(title = R.string.action_no) {},
            onCancel = {}
        )
    }

    private fun clearCache() {
        WebView(requireNotNull(activity)).apply {
            clearCache(true)
            destroy()
        }
        requireActivity().snackbar(R.string.message_cache_cleared)
    }

    private fun clearHistory(): Completable = Completable.fromAction {
        val activity = activity
        if (activity != null) {
            // TODO: 6/9/17 clearHistory is not synchronous
            WebUtils.clearHistory(activity, historyRepository, databaseScheduler)
        } else {
            throw RuntimeException("Activity was null in clearHistory")
        }
    }

    private fun clearCookies(): Completable = Completable.fromAction {
        WebUtils.clearCookies()
    }

    private fun clearWebStorage() {
        WebUtils.clearWebStorage()
        requireActivity().snackbar(R.string.message_web_storage_cleared)
    }

    companion object {
        private const val SETTINGS_THIRDPCOOKIES = "third_party"
        private const val SETTINGS_CACHEEXIT = "clear_cache_exit"
        private const val SETTINGS_HISTORYEXIT = "clear_history_exit"
        private const val SETTINGS_COOKIEEXIT = "clear_cookies_exit"
        private const val SETTINGS_CLEARCACHE = "clear_cache"
        private const val SETTINGS_CLEARHISTORY = "clear_history"
        private const val SETTINGS_CLEARCOOKIES = "clear_cookies"
        private const val SETTINGS_CLEARWEBSTORAGE = "clear_webstorage"
        private const val SETTINGS_WEBSTORAGEEXIT = "clear_webstorage_exit"
        private const val SETTINGS_DONOTTRACK = "do_not_track"
        private const val SETTINGS_WEBRTC = "webrtc_support"
        private const val SETTINGS_IDENTIFYINGHEADERS = "remove_identifying_headers"
    }

}
