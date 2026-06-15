package com.honglu.typing.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Time utility functions.
 */
object TimeUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatTimestamp(epochMillis: Long): String {
        return dateFormat.format(epochMillis)
    }

    fun millisToSeconds(ms: Long): Long {
        return ms / 1000
    }

    fun millisToMinutes(ms: Long): Float {
        return (ms / 1000f) / 60f
    }
}
