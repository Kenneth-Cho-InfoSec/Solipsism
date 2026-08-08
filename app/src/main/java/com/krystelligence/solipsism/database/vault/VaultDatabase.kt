package com.krystelligence.solipsism.database.vault

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.krystelligence.solipsism.database.databaseDelegate
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultDatabase @Inject constructor(
    application: Application
) : SQLiteOpenHelper(application, DATABASE_NAME, null, DATABASE_VERSION), VaultRepository {

    private val database: SQLiteDatabase by databaseDelegate()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_ENTRIES (" +
                "$KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$KEY_URL TEXT NOT NULL UNIQUE, " +
                "$KEY_TITLE TEXT NOT NULL, " +
                "$KEY_SAVED_AT INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ENTRIES")
        onCreate(db)
    }

    override fun savePage(url: String, title: String): Completable = Completable.fromAction {
        val values = ContentValues().apply {
            put(KEY_URL, url)
            put(KEY_TITLE, title.ifBlank { url })
            put(KEY_SAVED_AT, System.currentTimeMillis())
        }
        database.insertWithOnConflict(
            TABLE_ENTRIES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    override fun getAll(): Single<List<VaultEntry>> = Single.fromCallable {
        database.query(
            TABLE_ENTRIES,
            arrayOf(KEY_ID, KEY_URL, KEY_TITLE, KEY_SAVED_AT),
            null,
            null,
            null,
            null,
            "$KEY_SAVED_AT DESC"
        ).use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(KEY_ID)
                val urlIndex = cursor.getColumnIndexOrThrow(KEY_URL)
                val titleIndex = cursor.getColumnIndexOrThrow(KEY_TITLE)
                val savedAtIndex = cursor.getColumnIndexOrThrow(KEY_SAVED_AT)
                while (cursor.moveToNext()) {
                    add(
                        VaultEntry(
                            id = cursor.getLong(idIndex),
                            url = cursor.getString(urlIndex),
                            title = cursor.getString(titleIndex),
                            savedAt = cursor.getLong(savedAtIndex)
                        )
                    )
                }
            }
        }
    }

    override fun delete(id: Long): Completable = Completable.fromAction {
        database.delete(TABLE_ENTRIES, "$KEY_ID = ?", arrayOf(id.toString()))
    }

    override fun clear(): Completable = Completable.fromAction {
        database.delete(TABLE_ENTRIES, null, null)
    }

    private companion object {
        const val DATABASE_NAME = "vault.db"
        const val DATABASE_VERSION = 1
        const val TABLE_ENTRIES = "vault_entries"
        const val KEY_ID = "id"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_SAVED_AT = "saved_at"
    }
}
