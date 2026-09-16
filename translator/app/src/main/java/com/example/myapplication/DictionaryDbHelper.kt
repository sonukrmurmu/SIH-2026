package com.example.myapplication // Change this if your package name is different

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

class DictionaryDbHelper(private val context: Context) {
    private val dbName = "dictionary.db"
    private var db: SQLiteDatabase? = null

    init {
        copyDatabaseIfNeeded()
        openDatabase()
    }

    private fun copyDatabaseIfNeeded() {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(dbName).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun openDatabase() {
        val dbPath = context.getDatabasePath(dbName).path
        db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun lookup(word: String): String? {
        // Clean the input to match the database (lowercase, remove punctuation)
        val cleanWord = word.trim().lowercase().removeSuffix(".").removeSuffix("?").removeSuffix("!")

        val cursor = db?.rawQuery(
            "SELECT santali FROM lexicon WHERE english = ? LIMIT 1",
            arrayOf(cleanWord)
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0) // Return the Santali word
            }
        }
        return null // Word not found
    }
}