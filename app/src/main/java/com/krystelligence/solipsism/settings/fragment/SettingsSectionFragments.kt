package com.krystelligence.solipsism.settings.fragment

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceGroup

private fun PreferenceGroup.retainSettings(keys: Set<String>) {
    val remove = mutableListOf<Preference>()
    for (index in 0 until preferenceCount) {
        val preference = getPreference(index)
        if (preference is PreferenceGroup) {
            preference.retainSettings(keys)
            if (preference.preferenceCount == 0) remove += preference
        } else if (preference.key !in keys) {
            remove += preference
        }
    }
    remove.forEach(::removePreference)
}

abstract class GeneralSectionFragment : GeneralSettingsFragment() {
    protected abstract val retainedKeys: Set<String>
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        preferenceScreen.retainSettings(retainedKeys)
    }
}

class BrowsingSettingsFragment : GeneralSectionFragment() {
    override val retainedKeys = setOf(
        "app_language", "custom_language_xml", "cb_images", "savedata", "cb_javascript",
        "cb_colormode", "proxy", "agent", "chrompatibility_mode", "home", "search", "suggestions_choice"
    )
}

class DownloadSettingsFragment : GeneralSectionFragment() {
    override val retainedKeys = setOf(
        "download", "custom_download_manager_enabled", "custom_download_manager", "save_images_as_jpeg"
    )
}

class NavigationSettingsFragment : DisplaySectionFragment() {
    override val retainedKeys = setOf(
        "rail_position", "rail_size", "rail_utility_action", "rail_menu_studio"
    )
}

abstract class DisplaySectionFragment : DisplaySettingsFragment() {
    protected abstract val retainedKeys: Set<String>
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        preferenceScreen.retainSettings(retainedKeys)
    }
}

class GraphicsSettingsFragment : DisplaySectionFragment() {
    override val retainedKeys = setOf(
        "app_theme", "text_size", "custom_font", "accent_palette", "match_system_accent",
        "wideViewPort", "overViewMode", "text_reflow"
    )
}

class HomepageSettingsFragment : DisplaySectionFragment() {
    override val retainedKeys = setOf(
        "homepage_wallpaper", "homepage_source", "homepage_layout", "homepage_editor",
        "homepage_datetime_enabled", "homepage_time_format", "homepage_date_format",
        "homepage_datetime_opacity"
    )
}

class FullscreenSettingsFragment : DisplaySectionFragment() {
    override val retainedKeys = setOf(
        "fullScreenOption", "fullscreen", "hide_rail_in_fullscreen", "black_status_bar"
    )
}
