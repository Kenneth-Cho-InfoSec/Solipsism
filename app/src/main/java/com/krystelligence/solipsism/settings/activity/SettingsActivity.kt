/*
 * Copyright 2014 A.C.R. Development
 */
package com.krystelligence.solipsism.settings.activity

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.settings.fragment.AbstractSettingsFragment
import com.krystelligence.solipsism.settings.fragment.RootSettingsFragment
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : ThemableSettingsActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private var settingsSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_root)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<EditText>(R.id.settings_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                settingsSearchQuery = s?.toString().orEmpty()
                applySettingsSearch()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        supportFragmentManager.addOnBackStackChangedListener(::applySettingsSearch)

        // Let FragmentManager restore the current nested settings page after a configuration
        // or theme recreation. Replacing it unconditionally would always send the user back to
        // the root settings screen.
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.root, RootSettingsFragment())
                .commit()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (supportFragmentManager.popBackStackImmediate()) return
                    returnToBrowser()
                }
            }
        )
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_from_right,
                R.anim.fade_out_scale,
                R.anim.fade_in_scale,
                R.anim.slide_out_to_right
            )
            .replace(R.id.root, fragment)
            .addToBackStack(null)
            .commit()
        supportFragmentManager.executePendingTransactions()
        applySettingsSearch()
        return true
    }

    private fun applySettingsSearch() {
        (supportFragmentManager.findFragmentById(R.id.root) as? AbstractSettingsFragment)
            ?.applySettingsSearch(settingsSearchQuery)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun returnToBrowser() {
        val incognito = intent.getBooleanExtra(
            SettingsNavigation.EXTRA_INCOGNITO,
            false
        )
        startActivity(SettingsNavigation.createBrowserIntent(this, incognito))
        finish()
    }
}
