package com.psike.pantrypilot

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** The starter app had no database. Version 1 stores weekly defaults independently. */
class PantryDatabase(context: Context, name: String = "pantry-pilot.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE checklist (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                amount TEXT NOT NULL,
                unit TEXT NOT NULL,
                repeats_weekly INTEGER NOT NULL,
                weekly_amount TEXT NOT NULL,
                weekly_unit TEXT NOT NULL,
                answer TEXT NOT NULL
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE shopping (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                amount TEXT NOT NULL,
                unit TEXT NOT NULL,
                purchased INTEGER NOT NULL
            )""".trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes need a migration; never silently discard saved lists.
        error("A database migration from $oldVersion to $newVersion is required.")
    }

    fun load(): PantrySnapshot {
        val db = readableDatabase
        val checklist = db.rawQuery("SELECT * FROM checklist ORDER BY rowid", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    ChecklistItem(
                        id = cursor.text("id"),
                        name = cursor.text("name"),
                        quantity = cursor.quantity("amount", "unit"),
                        repeatsWeekly = cursor.number("repeats_weekly") != 0,
                        weeklyQuantity = cursor.quantity("weekly_amount", "weekly_unit"),
                        answer = Answer.valueOf(cursor.text("answer")),
                    ),
                )
            }
        }
        val shopping = db.rawQuery("SELECT * FROM shopping ORDER BY rowid", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    ShoppingItem(
                        id = cursor.text("id"),
                        name = cursor.text("name"),
                        quantity = cursor.quantity("amount", "unit"),
                        purchased = cursor.number("purchased") != 0,
                    ),
                )
            }
        }
        return PantrySnapshot(checklist, shopping)
    }

    fun save(state: PantrySnapshot) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("checklist", null, null)
            state.checklist.forEach { item ->
                db.insertOrThrow("checklist", null, ContentValues().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("amount", item.quantity.amount)
                    put("unit", item.quantity.unit)
                    put("repeats_weekly", if (item.repeatsWeekly) 1 else 0)
                    put("weekly_amount", item.weeklyQuantity.amount)
                    put("weekly_unit", item.weeklyQuantity.unit)
                    put("answer", item.answer.name)
                })
            }
            db.delete("shopping", null, null)
            state.shopping.forEach { item ->
                db.insertOrThrow("shopping", null, ContentValues().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("amount", item.quantity.amount)
                    put("unit", item.quantity.unit)
                    put("purchased", if (item.purchased) 1 else 0)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.text(column: String) = getString(getColumnIndexOrThrow(column))
    private fun Cursor.number(column: String) = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.quantity(amount: String, unit: String) =
        checkNotNull(Quantity.parse(text(amount), text(unit))) { "Invalid saved quantity." }
}
