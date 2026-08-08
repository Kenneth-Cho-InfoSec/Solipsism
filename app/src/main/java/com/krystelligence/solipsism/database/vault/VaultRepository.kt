package com.krystelligence.solipsism.database.vault

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

interface VaultRepository {
    fun savePage(url: String, title: String): Completable
    fun getAll(): Single<List<VaultEntry>>
    fun delete(id: Long): Completable
    fun clear(): Completable
}
