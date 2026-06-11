package com.accord.pebluelinkcompanion

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $message"
        synchronized(logs) {
            logs.add(0, entry) // Newest first
            if (logs.size > 500) logs.removeAt(logs.size - 1)
        }
    }

    fun getLogs(): String = synchronized(logs) {
        logs.joinToString("\n")
    }

    fun clear() = synchronized(logs) {
        logs.clear()
    }
}
