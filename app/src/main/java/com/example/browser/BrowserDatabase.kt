package com.example.browser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BrowserDatabase(context: Context) :
    SQLiteOpenHelper(context, "browser.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                visited_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_history_visited_at ON history(visited_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS bookmarks")
        db.execSQL("DROP TABLE IF EXISTS history")
        onCreate(db)
    }
}

data class UrlEntry(val id: Long, val url: String, val title: String, val timestamp: Long)

class BrowserRepository(context: Context) {
    private val dbHelper = BrowserDatabase(context.applicationContext)

    fun isBookmarked(url: String): Boolean {
        dbHelper.readableDatabase.query(
            "bookmarks", arrayOf("id"), "url = ?", arrayOf(url), null, null, null
        ).use { return it.moveToFirst() }
    }

    fun addBookmark(url: String, title: String) {
        val values = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("created_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            "bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun removeBookmark(url: String) {
        dbHelper.writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
    }

    fun listBookmarks(): List<UrlEntry> = query(
        "SELECT id, url, title, created_at FROM bookmarks ORDER BY created_at DESC"
    )

    fun addHistory(url: String, title: String) {
        val values = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("visited_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.insert("history", null, values)
    }

    fun listHistory(limit: Int = 200): List<UrlEntry> = query(
        "SELECT id, url, title, visited_at FROM history ORDER BY visited_at DESC LIMIT $limit"
    )

    fun clearHistory() {
        dbHelper.writableDatabase.delete("history", null, null)
    }

    fun deleteHistoryEntry(id: Long) {
        dbHelper.writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
    }

    private fun query(sql: String): List<UrlEntry> {
        val result = mutableListOf<UrlEntry>()
        dbHelper.readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    UrlEntry(
                        id = cursor.getLong(0),
                        url = cursor.getString(1),
                        title = cursor.getString(2),
                        timestamp = cursor.getLong(3)
                    )
                )
            }
        }
        return result
    }
}
