package com.krystelligence.solipsism.settings.activity

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.browser.di.injector
import com.krystelligence.solipsism.databinding.ActivityUserScriptEditorBinding
import com.krystelligence.solipsism.userscript.UserScriptManager
import com.krystelligence.solipsism.userscript.UserScriptMetadataParser
import com.krystelligence.solipsism.userscript.UserScriptTemplate
import com.krystelligence.solipsism.userscript.UserScriptSyntaxHighlighter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

class UserScriptEditorActivity : ThemableSettingsActivity() {

    @Inject lateinit var userScriptManager: UserScriptManager

    private lateinit var binding: ActivityUserScriptEditorBinding
    private var scriptId: String? = null
    private var isSaving = false
    private var syntaxHighlightingEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(this)
        binding = ActivityUserScriptEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID)
        val existing = scriptId?.let(userScriptManager::find)
        if (scriptId != null && existing == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(
            if (existing == null) R.string.userscripts_editor_new_title
            else R.string.userscripts_editor_title
        )

        binding.sourceEditor.setText(existing?.source ?: UserScriptTemplate.SOURCE)
        binding.sourceEditor.setSelection(binding.sourceEditor.length())
        syntaxHighlightingEnabled = getPreferences(MODE_PRIVATE)
            .getBoolean(SYNTAX_HIGHLIGHTING_KEY, true)
        binding.syntaxHighlightingSwitch.isChecked = syntaxHighlightingEnabled
        if (syntaxHighlightingEnabled) {
            UserScriptSyntaxHighlighter.apply(binding.sourceEditor)
        }
        updateSourceStatus()
        binding.sourceEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.sourceLayout.error = null
                updateSourceStatus()
                if (syntaxHighlightingEnabled) {
                    UserScriptSyntaxHighlighter.apply(binding.sourceEditor)
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.pasteButton.setOnClickListener { pasteFromClipboard() }
        binding.syntaxHighlightingSwitch.setOnCheckedChangeListener { _, enabled ->
            syntaxHighlightingEnabled = enabled
            getPreferences(MODE_PRIVATE).edit { putBoolean(SYNTAX_HIGHLIGHTING_KEY, enabled) }
            if (enabled) UserScriptSyntaxHighlighter.apply(binding.sourceEditor)
            else UserScriptSyntaxHighlighter.clear(binding.sourceEditor)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_user_script_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        if (item.itemId == R.id.action_save_userscript) {
            saveSource()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0 ||
            (!clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                !clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML))
        ) {
            Toast.makeText(this, R.string.userscripts_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).coerceToText(this).toString()
        binding.sourceEditor.setText(text)
        binding.sourceEditor.setSelection(binding.sourceEditor.length())
    }

    private fun saveSource() {
        if (isSaving) return
        val source = binding.sourceEditor.text?.toString().orEmpty()
        val sourceBytes = source.toByteArray(Charsets.UTF_8).size
        if (sourceBytes > MAX_SOURCE_BYTES) {
            showEditorError(getString(R.string.userscripts_source_too_large))
            return
        }
        val metadata = UserScriptMetadataParser.parse(source)
        if (metadata == null) {
            showEditorError(getString(R.string.userscripts_source_invalid))
            return
        }
        if (!metadata.isUnprivileged) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.userscripts_privileged_title)
                .setMessage(R.string.userscripts_privileged_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save) { _, _ -> persistSource(source) }
                .show()
            return
        }
        persistSource(source)
    }

    private fun persistSource(source: String) {
        isSaving = true
        binding.toolbar.menu.findItem(R.id.action_save_userscript)?.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    scriptId?.let { userScriptManager.updateSource(it, source) }
                        ?: userScriptManager.install(source)
                }
            }.onSuccess {
                setResult(RESULT_OK)
                finish()
            }.onFailure { error ->
                isSaving = false
                binding.toolbar.menu.findItem(R.id.action_save_userscript)?.isEnabled = true
                showEditorError(error.message ?: getString(R.string.userscripts_source_invalid))
            }
        }
    }

    private fun showEditorError(message: String) {
        binding.sourceLayout.error = message
        binding.sourceStatus.text = message
    }

    private fun updateSourceStatus() {
        val bytes = binding.sourceEditor.text?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0
        val kib = bytes / 1024.0
        binding.sourceStatus.text = getString(
            R.string.userscripts_source_size,
            String.format(Locale.ROOT, "%.1f", kib),
            MAX_SOURCE_BYTES / 1024
        )
    }

    companion object {
        private const val EXTRA_SCRIPT_ID = "script_id"
        private const val SYNTAX_HIGHLIGHTING_KEY = "userscripts_syntax_highlighting"
        private const val MAX_SOURCE_BYTES = 1024 * 1024

        fun newIntent(context: Context) = Intent(context, UserScriptEditorActivity::class.java)

        fun editIntent(context: Context, scriptId: String) =
            newIntent(context).putExtra(EXTRA_SCRIPT_ID, scriptId)
    }
}
