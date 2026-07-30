package com.krystelligence.solipsism.browser.tab

import android.webkit.WebView
import com.krystelligence.solipsism.preference.SitePermissionDecision
import com.krystelligence.solipsism.preference.SitePermissionKey
import com.krystelligence.solipsism.preference.SitePermissionStore
import javax.inject.Inject
import javax.inject.Singleton

/** Applies best-effort script-level restrictions for APIs without WebView permission callbacks. */
@Singleton
class SitePermissionRuntime @Inject constructor(
    private val sitePermissionStore: SitePermissionStore
) {

    fun injectAfterPageFinished(view: WebView, url: String) {
        val denied = SitePermissionKey.entries.filter {
            sitePermissionStore.decision(url, it) == SitePermissionDecision.DENY
        }
        if (denied.isEmpty()) return

        val blockedNames = buildList {
            if (SitePermissionKey.NOTIFICATIONS in denied) add("Notification")
            if (SitePermissionKey.CLIPBOARD in denied) add("navigator.clipboard")
            if (SitePermissionKey.MOTION_SENSORS in denied) {
                add("DeviceMotionEvent")
                add("DeviceOrientationEvent")
            }
            if (SitePermissionKey.NFC in denied) add("navigator.nfc")
            if (SitePermissionKey.USB in denied) add("navigator.usb")
            if (SitePermissionKey.SERIAL in denied) add("navigator.serial")
            if (SitePermissionKey.VIRTUAL_REALITY in denied ||
                SitePermissionKey.AUGMENTED_REALITY in denied) add("navigator.xr")
            if (SitePermissionKey.APPS_ON_DEVICE in denied) add("navigator.getInstalledRelatedApps")
            if (SitePermissionKey.FILE_EDITING in denied) {
                add("showOpenFilePicker")
                add("showSaveFilePicker")
                add("showDirectoryPicker")
            }
        }
        if (blockedNames.isEmpty()) return

        val script = buildString {
            append("(function(){const names=")
            append(blockedNames.joinToString(prefix = "[", postfix = "]") { "'$it'" })
            append(";for(const name of names){try{let target=window, key=name;")
            append("if(name.startsWith('navigator.')){target=navigator;key=name.substring(10);}")
            append("Object.defineProperty(target,key,{configurable:true,get:()=>undefined});}catch(e){}}})();")
        }
        view.evaluateJavascript(script, null)
    }
}
