package com.krystelligence.solipsism.userscript

import android.text.Spannable
import android.text.Spanned
import android.text.Selection
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.krystelligence.solipsism.R
import java.util.regex.Pattern

/** Lightweight, local highlighting for JavaScript and userscript metadata. */
object UserScriptSyntaxHighlighter {

    private class SyntaxColorSpan(color: Int) : ForegroundColorSpan(color)

    private val comments = Pattern.compile("//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/", Pattern.MULTILINE)
    private val metadata = Pattern.compile("^\\s*//\\s*@[^\\r\\n]*$", Pattern.MULTILINE)
    private val strings = Pattern.compile("\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`", Pattern.MULTILINE)
    private val keywords = Pattern.compile(
        "\\b(?:as|async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|from|function|if|import|in|instanceof|let|new|of|return|static|super|switch|this|throw|try|typeof|var|void|while|with|yield)\\b"
    )

    fun apply(editor: TextView) {
        val text = editor.text as? Spannable ?: return
        val selectionStart = editor.selectionStart
        val selectionEnd = editor.selectionEnd
        text.getSpans(0, text.length, SyntaxColorSpan::class.java).forEach(text::removeSpan)

        val commentColor = MaterialColors.getColor(editor, R.attr.colorOnSurfaceVariant)
        val metadataColor = MaterialColors.getColor(editor, R.attr.colorSecondary)
        val stringColor = MaterialColors.getColor(editor, R.attr.colorPrimary)
        val keywordColor = MaterialColors.getColor(editor, R.attr.colorError)

        addSpans(text, comments, commentColor)
        addSpans(text, metadata, metadataColor)
        addSpans(text, strings, stringColor)
        addSpans(text, keywords, keywordColor)

        if (selectionStart >= 0 && selectionEnd >= 0) {
            Selection.setSelection(
                text,
                selectionStart.coerceAtMost(text.length),
                selectionEnd.coerceAtMost(text.length)
            )
        }
    }

    fun clear(editor: TextView) {
        val text = editor.text as? Spannable ?: return
        text.getSpans(0, text.length, SyntaxColorSpan::class.java).forEach(text::removeSpan)
    }

    private fun addSpans(text: Spannable, pattern: Pattern, color: Int) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            text.setSpan(
                SyntaxColorSpan(color),
                matcher.start(),
                matcher.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
