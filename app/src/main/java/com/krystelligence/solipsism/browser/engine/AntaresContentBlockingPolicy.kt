package com.krystelligence.solipsism.browser.engine

import android.app.Application
import android.os.ParcelFileDescriptor
import com.krystelligence.solipsism.adblock.custom.CustomFilterRepository
import com.krystelligence.solipsism.database.adblock.HostsRepository
import com.krystelligence.solipsism.preference.UserPreferences
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the compact network policy transferred to the isolated Antares process. */
@Singleton
class AntaresContentBlockingPolicy @Inject constructor(
    private val application: Application,
    private val hostsRepository: HostsRepository,
    private val customFilterRepository: CustomFilterRepository,
    private val userPreferences: UserPreferences,
) {
    private val policyDirectory by lazy {
        File(application.cacheDir, "antares-content-blocking")
    }
    private var cachedSignature: Int? = null

    /** The returned descriptor owns a read-only snapshot and must be closed by the caller. */
    @Synchronized
    fun openFileDescriptor(): ParcelFileDescriptor {
        val hosts = runBlocking { hostsRepository.allHosts() }
        val customRules = customFilterRepository.all()
            .filter { it.enabled }
            .map { it.line }
        val signature = 31 * hosts.hashCode() +
            17 * customRules.hashCode() +
            userPreferences.uBlockOriginEnabled.hashCode()
        val policyFile = File(policyDirectory, POLICY_FILE_NAME)

        if (cachedSignature != signature || !policyFile.isFile) {
            policyDirectory.mkdirs()
            val temporary = File(policyDirectory, "$POLICY_FILE_NAME.tmp")
            temporary.bufferedWriter().use { writer ->
                if (hosts.isEmpty()) {
                    application.assets.open(HOSTS_ASSET).bufferedReader().use { reader ->
                        reader.copyTo(writer)
                    }
                } else {
                    hosts.forEach { host ->
                        writer.append("||").append(host.name).appendLine("^")
                    }
                }
                if (userPreferences.uBlockOriginEnabled) {
                    writer.appendLine()
                    application.assets.open(UBLOCK_ASSET).bufferedReader().use { reader ->
                        reader.copyTo(writer)
                    }
                }
                customRules.forEach { rule -> writer.appendLine().append(rule) }
            }
            check(temporary.renameTo(policyFile)) { "Unable to update Antares content policy" }
            cachedSignature = signature
        }

        return ParcelFileDescriptor.open(policyFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private companion object {
        const val POLICY_FILE_NAME = "network-policy.txt"
        const val HOSTS_ASSET = "hosts.txt"
        const val UBLOCK_ASSET = "ublock_origin_filters.txt"
    }
}
