package com.krystelligence.solipsism.download

data class ResolvedDownloadMetadata(
    val mimeType: String?,
    val contentDisposition: String?,
    val contentLength: Long
)
