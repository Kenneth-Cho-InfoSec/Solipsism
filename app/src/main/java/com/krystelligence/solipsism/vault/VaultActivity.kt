package com.krystelligence.solipsism.vault

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.settings.activity.ThemableSettingsActivity
import com.krystelligence.solipsism.browser.BrowserActivity
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.database.vault.VaultEntry
import com.krystelligence.solipsism.database.vault.VaultRepository
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

class VaultActivity : ThemableSettingsActivity() {

    @Inject lateinit var vaultRepository: VaultRepository

    private val disposables = CompositeDisposable()
    private lateinit var entriesContainer: LinearLayout
    private lateinit var emptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(this)
        title = getString(R.string.action_vault)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(resources.getDimensionPixelSize(R.dimen.chrome_outer_margin))
        }
        root.addView(MaterialButton(this).apply {
            setText(R.string.vault_clear)
            setOnClickListener { confirmClear() }
        }, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this)
        entriesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emptyState = TextView(this).apply {
            setText(R.string.vault_empty_summary)
            gravity = Gravity.CENTER
            setPadding(resources.getDimensionPixelSize(R.dimen.chrome_outer_margin))
        }
        scroll.addView(entriesContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadEntries()
    }

    private fun loadEntries() {
        disposables.add(
            vaultRepository.getAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::renderEntries) { renderEntries(emptyList()) }
        )
    }

    private fun renderEntries(entries: List<VaultEntry>) {
        entriesContainer.removeAllViews()
        if (entries.isEmpty()) {
            entriesContainer.addView(emptyState)
            return
        }
        entries.forEach { entry ->
            val card = MaterialCardView(this).apply {
                setContentPadding(16, 12, 16, 12)
                setOnClickListener { openEntry(entry) }
                setOnLongClickListener { confirmDelete(entry); true }
            }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(TextView(this).apply { text = entry.title; textSize = 17f })
            content.addView(TextView(this).apply {
                text = entry.url
                textSize = 13f
                alpha = 0.75f
            })
            content.addView(TextView(this).apply {
                text = DateFormat.getDateTimeInstance().format(Date(entry.savedAt))
                textSize = 12f
                alpha = 0.65f
            })
            card.addView(content)
            entriesContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.chrome_outer_margin)
            })
        }
    }

    private fun openEntry(entry: VaultEntry) {
        startActivity(Intent(this, BrowserActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = entry.url.toUri()
        })
    }

    private fun confirmDelete(entry: VaultEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_delete)
            .setMessage(entry.title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vault_delete) { _, _ ->
                disposables.add(
                    vaultRepository.delete(entry.id)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(::loadEntries)
                )
            }
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_clear)
            .setMessage(R.string.vault_empty_summary)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vault_clear) { _, _ ->
                disposables.add(
                    vaultRepository.clear()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(::loadEntries)
                )
            }
            .show()
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }
}
