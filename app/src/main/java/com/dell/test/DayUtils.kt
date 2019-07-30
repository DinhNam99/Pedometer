package com.dell.test

import android.app.Dialog
import android.content.Context
import java.util.*

class DayUtils {
    constructor()

    fun getToday(): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = System.currentTimeMillis()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

}