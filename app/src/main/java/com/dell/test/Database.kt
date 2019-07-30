package com.dell.test

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Pair
import java.util.concurrent.atomic.AtomicInteger


private val DB_NAME = "steps"
private val DB_VERSION = 2

private val openCounter = AtomicInteger()
@Suppress("UNREACHABLE_CODE")
class Database(context: Context?) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private var instance : Database? = null


    @Synchronized
    fun getInstance(c: Context): Database {
        if (instance == null)
            instance = Database(c.applicationContext)
        openCounter.incrementAndGet()
        return instance as Database
    }

    override fun close() {
        if (openCounter.decrementAndGet() == 0)
            super.close()
    }

    override fun onCreate(db: SQLiteDatabase?) {

        db!!.execSQL("CREATE TABLE $DB_NAME (date INTEGER, steps INTEGER)")

    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if(oldVersion == 1){
            db!!.execSQL("CREATE TABLE " + DB_NAME + "2 (date INTEGER, steps INTEGER)")
            db.execSQL("INSERT INTO " + DB_NAME + "2 (date, steps) SELECT date, steps FROM " + DB_NAME)
            db.execSQL("DROP TABLE $DB_NAME")
            db.execSQL("ALTER TABLE " + DB_NAME + "2 RENAME TO " + DB_NAME )
        }
    }

    fun insertNewDay(date:Long, steps:Int){
        writableDatabase.beginTransaction()
        try {
            val c = readableDatabase.query(
                DB_NAME, arrayOf("date"), "date = ?",
                arrayOf(date.toString()), null, null, null
            )
            if (c.count == 0 && steps >= 0) {

                // add 'steps' to yesterdays count
                addToLastEntry(steps)

                // add today
                val values = ContentValues()
                values.put("date", date)
                // use the negative steps as offset
                values.put("steps", steps)
                writableDatabase.insert(DB_NAME, null, values)
            }
            c.close()
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }
    fun addToLastEntry(steps:Int) {
        writableDatabase.execSQL("UPDATE " + DB_NAME + " SET steps = steps + " + steps + " WHERE date = (SELECT MAX(date) FROM " + DB_NAME + ")")
    }

    fun getTotalWithoutToday(): Int {
        val c = readableDatabase
            .query(
                DB_NAME, arrayOf("SUM(steps)"), "steps > 0 AND date > 0 AND date < ?",
                arrayOf(DayUtils().getToday().toString()), null, null, null
            )
        c.moveToFirst()
        val re = c.getInt(0)
        c.close()
        return re
    }

    fun getSteps(date: Long): Int {
        val c = readableDatabase.query(
            DB_NAME, arrayOf("steps"), "date = ?",
            arrayOf(date.toString()), null, null, null
        )
        c.moveToFirst()
        val re: Int
        if (c.count == 0)
            re = Integer.MIN_VALUE
        else
            re = c.getInt(0)
        c.close()
        return re
    }
    fun getLastEntries(num: Int): List<Pair<Long, Int>> {
        val c = readableDatabase
            .query(
                DB_NAME, arrayOf("date", "steps"), "date > 0", null, null, null,
                "date DESC", num.toString()
            )
        val max = c.count
        val result = ArrayList<Pair<Long, Int>>(max)
        if (c.moveToFirst()) {
            do {
                result.add(Pair(c.getLong(0), c.getInt(1)))
            } while (c.moveToNext())
        }
        return result
    }
    fun saveCurrentSteps(steps: Int) {
        val values = ContentValues()
        values.put("steps", steps)
        if (writableDatabase.update(DB_NAME, values, "date = -1", null) == 0) {
            values.put("date", -1)
            writableDatabase.insert(DB_NAME, null, values)
        }

    }
    fun getDaysWithoutToday(): Int {

        val c = readableDatabase
            .query(
                DB_NAME, arrayOf("COUNT(*)"), "steps > ? AND date < ? AND date > 0",
                arrayOf(0.toString(), DayUtils().getToday().toString()), null, null, null
            )
        c.moveToFirst()
        val re = c.getInt(0)
        c.close()
        return if (re < 0) 0 else re
    }
    fun getDays(): Int {
        // todays is not counted yet
        return getDaysWithoutToday() + 1
    }
    fun getCurrentSteps(): Int {
        val re = getSteps(-1)
        return if (re == Integer.MIN_VALUE) 0 else re
    }
}