package com.satellite.wallpaper

import android.app.ActivityManager
import android.content.Context
import kotlin.math.sqrt

object ImageBudget {
    fun size(context: Context): Pair<Int, Int> {
        val (w, h) = ScreenSize.real(context)
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val cap = if (am.isLowRamDevice || info.lowMemory || am.memoryClass <= 128) 720 else 1080
        // Reserve space for WebView, GPU buffers, the existing wallpaper and decoding.
        val heap = Runtime.getRuntime()
        val free = (heap.maxMemory() - heap.totalMemory() + heap.freeMemory()).coerceAtLeast(1L)
        val pixels = (free / 48L).coerceIn(240_000L, 2_500_000L)
        val scale = minOf(1.0, cap.toDouble() / w, sqrt(pixels.toDouble() / (w.toLong() * h)))
        return Pair((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
    }
}
