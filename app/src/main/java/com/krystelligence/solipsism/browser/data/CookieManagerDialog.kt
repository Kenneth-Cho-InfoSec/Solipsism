package com.krystelligence.solipsism.browser.data

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.krystelligence.solipsism.R
import java.net.URI

object CookieManagerDialog {

    fun show(activity: Activity, url: String, repository: CookieManagerRepository) {
        if (!CookieManagerRepository.isManageableUrl(url)) return

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20))
        }
        val explanation = TextView(activity).apply {
            text = activity.getString(R.string.cookie_manager_explanation)
            setTextColor(activity.themeTextColor())
            setPadding(0, 0, 0, activity.dp(10))
        }
        val site = TextView(activity).apply {
            text = activity.getString(R.string.cookie_manager_site, siteLabel(url))
            setTextColor(activity.themeTextColor())
            setPadding(0, 0, 0, activity.dp(10))
        }
        val actions = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val cookieList = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val listScroll = ScrollView(activity).apply {
            addView(cookieList)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.dp(150)
            )
        }

        lateinit var dialog: androidx.appcompat.app.AlertDialog
        fun refresh() {
            cookieList.removeAllViews()
            val cookies = repository.listForUrl(url)
            if (cookies.isEmpty()) {
                cookieList.addView(TextView(activity).apply {
                    text = activity.getString(R.string.cookie_manager_empty)
                    setTextColor(activity.themeTextColor())
                    setPadding(0, activity.dp(18), 0, activity.dp(18))
                })
            } else {
                cookies.forEach { cookie ->
                    cookieList.addView(cookieRow(activity, url, cookie, repository) { refresh() })
                }
            }
        }

        val add = MaterialButton(activity).apply {
            icon = ContextCompat.getDrawable(activity, R.drawable.ic_action_plus)
            contentDescription = activity.getString(R.string.cookie_manager_add_short)
            compactActionStyle()
            setOnClickListener {
                showEditor(activity, url, repository, null) { refresh() }
            }
        }
        val refreshButton = MaterialButton(activity).apply {
            icon = ContextCompat.getDrawable(activity, R.drawable.ic_action_refresh)
            contentDescription = activity.getString(R.string.cookie_manager_refresh)
            compactActionStyle()
            setOnClickListener { refresh() }
        }
        val deleteAll = MaterialButton(activity).apply {
            icon = ContextCompat.getDrawable(activity, R.drawable.ic_action_delete)
            contentDescription = activity.getString(R.string.cookie_manager_delete_visible_short)
            compactActionStyle()
            setOnClickListener {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.cookie_manager_delete_visible)
                    .setMessage(R.string.cookie_manager_delete_visible_warning)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.action_yes) { _, _ ->
                        repository.deleteVisibleCookies(url) { result ->
                            activity.runOnUiThread {
                                if (result is CookieOperationResult.Failure) {
                                    showMessage(activity, result.reason)
                                }
                                refresh()
                            }
                        }
                    }
                    .show()
            }
        }
        actions.addView(add, buttonParams(activity))
        actions.addView(refreshButton, buttonParams(activity))
        actions.addView(deleteAll, buttonParams(activity))

        root.addView(explanation)
        root.addView(site)
        root.addView(actions)
        root.addView(listScroll)

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.cookie_manager)
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener { refresh() }
        dialog.show()
    }

    fun promptForUrl(context: Context, repository: CookieManagerRepository) {
        val input = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        val inputLayout = TextInputLayout(context).apply {
            hint = "https://example.com"
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cookie_manager)
            .setMessage(R.string.cookie_manager_url_prompt)
            .setView(inputLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                if (context is Activity && CookieManagerRepository.isManageableUrl(url)) {
                    show(context, url, repository)
                } else {
                    showMessage(context, context.getString(R.string.cookie_manager_invalid_url))
                }
            }
            .show()
    }

    private fun cookieRow(
        context: Context,
        url: String,
        cookie: BrowserCookie,
        repository: CookieManagerRepository,
        onChanged: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, context.dp(8), 0, context.dp(8))
        val details = TextView(context).apply {
            text = context.getString(
                R.string.cookie_manager_cookie_format,
                cookie.name,
                mask(cookie.value)
            )
            setTextColor(context.themeTextColor())
        }
        val buttons = LinearLayout(context).apply {
            addView(MaterialButton(context).apply {
                icon = ContextCompat.getDrawable(context, R.drawable.ic_action_edit)
                contentDescription = context.getString(R.string.cookie_manager_edit)
                compactActionStyle()
                setOnClickListener {
                    showEditor(context, url, repository, cookie, onChanged)
                }
            }, buttonParams(context))
            addView(MaterialButton(context).apply {
                icon = ContextCompat.getDrawable(context, R.drawable.ic_action_delete)
                contentDescription = context.getString(R.string.cookie_manager_delete)
                compactActionStyle()
                setOnClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.cookie_manager_delete)
                        .setMessage(R.string.cookie_manager_delete_warning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.action_yes) { _, _ ->
                            repository.delete(url, cookie.name) { result ->
                                (context as? Activity)?.runOnUiThread {
                                    if (result is CookieOperationResult.Failure) {
                                        showMessage(context, result.reason)
                                    }
                                    onChanged()
                                }
                            }
                        }
                        .show()
                }
            }, buttonParams(context))
        }
        addView(details)
        addView(buttons)
    }

    private fun showEditor(
        context: Context,
        url: String,
        repository: CookieManagerRepository,
        existing: BrowserCookie?,
        onChanged: () -> Unit
    ) {
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(20))
        }
        val name = editor(context, existing?.name.orEmpty(), "Name")
        val value = editor(context, existing?.value.orEmpty(), "Value", password = true)
        val domain = editor(context, "", "Domain (optional)")
        val path = editor(context, "/", "Path")
        val secure = MaterialCheckBox(context).apply { text = "Secure" }
        val httpOnly = MaterialCheckBox(context).apply { text = "HttpOnly" }
        val sameSite = editor(context, "", "SameSite (Strict, Lax, or None)")
        listOf(name.layout, value.layout, domain.layout, path.layout, secure, httpOnly, sameSite.layout)
            .forEach(form::addView)
        lateinit var editorDialog: androidx.appcompat.app.AlertDialog
        val submit: () -> Unit = {
            val draft = CookieDraft(
                name = name.input.text?.toString()?.trim().orEmpty(),
                value = value.input.text?.toString().orEmpty(),
                domain = domain.input.text?.toString()?.trim()?.ifEmpty { null },
                path = path.input.text?.toString()?.trim().orEmpty(),
                secure = secure.isChecked,
                httpOnly = httpOnly.isChecked,
                sameSite = sameSite.input.text?.toString()?.trim()?.ifEmpty { null }
            )
            repository.set(url, draft) { result ->
                (context as? Activity)?.runOnUiThread {
                    if (result is CookieOperationResult.Failure) {
                        showMessage(context, result.reason)
                    } else {
                        editorDialog.dismiss()
                        onChanged()
                    }
                }
            }
        }
        form.addView(MaterialButton(context).apply {
            text = context.getString(android.R.string.ok)
            isAllCaps = false
            setOnClickListener { submit() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        editorDialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.cookie_manager_add else R.string.cookie_manager_edit)
            .setMessage(R.string.cookie_manager_editor_warning)
            .setView(ScrollView(context).apply { addView(form) })
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        editorDialog.show()
    }

    private fun editor(
        context: Context,
        value: String,
        hint: String,
        password: Boolean = false
    ): EditorField {
        val input = TextInputEditText(context).apply {
            setText(value)
            setSingleLine(true)
            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT
            }
        }
        return EditorField(
            layout = TextInputLayout(context).apply {
                this.hint = hint
                addView(input)
            },
            input = input
        )
    }

    private data class EditorField(
        val layout: TextInputLayout,
        val input: TextInputEditText
    )

    private fun showMessage(context: Context, message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun mask(value: String): String =
        if (value.isEmpty()) "(empty)" else "•••• (${value.length} characters)"

    private fun siteLabel(url: String): String = runCatching {
        URI(url).host ?: url
    }.getOrDefault(url)

    private fun buttonParams(context: Context) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = context.dp(4)
        }

    private fun MaterialButton.compactActionStyle() {
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        setPadding(context.dp(4), 0, context.dp(4), 0)
        maxLines = 1
        textSize = 12f
        val containerColor = com.google.android.material.color.MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSecondaryContainer,
            com.google.android.material.color.MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSecondary,
                0
            )
        )
        val contentColor = com.google.android.material.color.MaterialColors.getColor(
            context,
            com.krystelligence.solipsism.R.attr.iconColor,
            com.google.android.material.color.MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSecondary,
                0
            )
        )
        backgroundTintList = ColorStateList.valueOf(containerColor)
        iconTint = ColorStateList.valueOf(contentColor)
        setTextColor(contentColor)
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun Context.themeTextColor(): Int =
        com.google.android.material.color.MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            android.graphics.Color.WHITE
        )
}
