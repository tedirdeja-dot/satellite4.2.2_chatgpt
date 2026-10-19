package com.satellite.wallpaper

/** Pure interval decision, also exercised by JVM tests. Strictly greater than the interval. */
object UpdatePolicy {
    val minutes = listOf(0, 15, 30, 60)
    fun due(now: Long, lastSuccess: Long, intervalMinutes: Int): Boolean {
        require(intervalMinutes in minutes)
        return lastSuccess <= 0L || now < lastSuccess ||
            now - lastSuccess > intervalMinutes * 60_000L
    }
}
