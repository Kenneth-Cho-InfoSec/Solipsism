package com.krystelligence.solipsism.database.vault

data class VaultEntry(
    val id: Long,
    val url: String,
    val title: String,
    val savedAt: Long
)
