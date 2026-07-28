package com.krystelligence.solipsism.userscript

enum class UserScriptRunAt {
    DOCUMENT_START,
    DOCUMENT_END,
    DOCUMENT_IDLE
}

data class UserScriptMetadata(
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val matches: List<String>,
    val includes: List<String>,
    val excludes: List<String>,
    val grants: List<String>,
    val requires: List<String>,
    val runAt: UserScriptRunAt,
    val noFrames: Boolean,
    val updateUrl: String?,
    val downloadUrl: String?
) {
    val isUnprivileged: Boolean
        get() = grants.size == 1 && grants.single() == "none"
}

data class UserScript(
    val id: String,
    val metadata: UserScriptMetadata,
    val source: String,
    val dependencies: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    val executableSource: String
        get() = (dependencies + source).joinToString("\n")
}
