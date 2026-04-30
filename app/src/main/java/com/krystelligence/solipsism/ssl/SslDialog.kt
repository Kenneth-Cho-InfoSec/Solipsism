package com.krystelligence.solipsism.ssl

import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.extensions.inflater
import com.krystelligence.solipsism.extensions.resizeAndShow
import android.content.Context
import android.net.http.SslCertificate
import android.text.format.DateFormat
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shows an informative dialog with the provided [SslCertificate] information.
 */
fun Context.showSslDialog(sslCertificateInfo: SslCertificateInfo) {
    val dateFormat = DateFormat.getDateFormat(applicationContext)

    val contentView = inflater.inflate(R.layout.dialog_ssl_info, null, false).apply {
        findViewById<TextView>(R.id.ssl_layout_issue_by).text =
            sslCertificateInfo.issuedByCommonName
        findViewById<TextView>(R.id.ssl_layout_issue_to).text =
            sslCertificateInfo.issuedToOrganizationName?.takeIf(String::isNotBlank)
                ?: sslCertificateInfo.issuedToCommonName
        findViewById<TextView>(R.id.ssl_layout_issue_date).text =
            dateFormat.format(sslCertificateInfo.issueDate)
        findViewById<TextView>(R.id.ssl_layout_expire_date).text =
            dateFormat.format(sslCertificateInfo.expireDate)
    }

    val icon = createSslDrawableForState(sslCertificateInfo.sslState)

    MaterialAlertDialogBuilder(this)
        .setIcon(icon)
        .setTitle(sslCertificateInfo.issuedToCommonName)
        .setView(contentView)
        .setPositiveButton(R.string.action_ok, null)
        .resizeAndShow()
}
