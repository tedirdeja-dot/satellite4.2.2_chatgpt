package com.satellite.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object WallpaperRepository {
    const val ACTION_STATUS = "com.satellite.wallpaper.STATUS"
    fun isRunning() = running
    private val io = Executors.newSingleThreadExecutor()
    private var running = false // main thread only
    private var armed = true
    @Volatile private var generation = 0L
    @Volatile var lastError: String? = null
        private set
    private fun prefs(c: Context) = c.getSharedPreferences("satellite_wallpaper", 0)
    fun cacheFile(c: Context) = File(c.cacheDir, "satellite_cache.jpg")
    fun updatedAt(c: Context) = prefs(c).getLong("updated_at", 0L)
    fun hasSelection(c: Context) = prefs(c).contains("view_url")
    fun viewUrl(c: Context) = prefs(c).getString("view_url", null) ?: ZoomEarthCapture.PAGE_URL
    fun interval(c: Context) = prefs(c).getInt("interval_minutes", 15).let { if (it in UpdatePolicy.minutes) it else 15 }
    fun setInterval(c: Context, value: Int) {
        require(value in UpdatePolicy.minutes)
        prefs(c).edit().putInt("interval_minutes", value).apply()
    }
    fun saveViewUrl(c: Context, url: String) {
        if (url == viewUrl(c) && hasSelection(c)) return
        cancelForLock()
        prefs(c).edit().putString("view_url", url).remove("updated_at")
            .remove("success_elapsed").remove("zoom").remove("offset_x").remove("offset_y").apply()
        // Keep the last good image until the next successful unlock capture.
    }
    fun lockEnabled(c: Context) = prefs(c).getBoolean("lock_static", true)
    fun setLockEnabled(c: Context, value: Boolean) { prefs(c).edit().putBoolean("lock_static", value).apply() }
    fun loadState(c: Context) = prefs(c).let { CropState(it.getFloat("zoom", 1f), it.getFloat("offset_x", 0f), it.getFloat("offset_y", 0f)).clamped() }
    fun saveState(c: Context, state: CropState) {
        val s = state.clamped()
        prefs(c).edit().putFloat("zoom", s.zoom).putFloat("offset_x", s.offsetX).putFloat("offset_y", s.offsetY).apply()
    }
    fun loadCached(c: Context): Bitmap? {
        val f = cacheFile(c)
        if (!f.exists()) {
            val legacy = File(c.filesDir, "satellite_cache.jpg")
            if (legacy.exists()) runCatching { legacy.renameTo(f) }
        }
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        val (w, h) = ImageBudget.size(c)
        var sample = 1
        while (o.outWidth / sample > w * 2 || o.outHeight / sample > h * 2) sample *= 2
        repeat(3) {
            try {
                return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            } catch (_: OutOfMemoryError) { sample *= 2 }
        }
        return null
    }
    fun unlocked(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isInteractive &&
        !c.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun boot(c: Context): Int = if (Build.VERSION.SDK_INT >= 24)
        Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1) else -1
    private fun due(c: Context): Boolean {
        val p = prefs(c)
        val b = boot(c)
        return if (b >= 0 && p.getInt("success_boot", -2) == b)
            UpdatePolicy.due(SystemClock.elapsedRealtime(), p.getLong("success_elapsed", 0L), interval(c))
        else UpdatePolicy.due(System.currentTimeMillis(), updatedAt(c), interval(c))
    }
    fun isOnline(c: Context): Boolean = runCatching {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
    @Synchronized fun note(c: Context, source: String, text: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val lines = (attemptLog(c).lines().filter { it.isNotBlank() } + "$stamp · $source · $text").takeLast(10)
        prefs(c).edit().putString("attempt_log", lines.joinToString("\n")).apply()
        Log.i("SatelliteTiming", "$source: $text")
        c.sendBroadcast(Intent(ACTION_STATUS).setPackage(c.packageName))
    }
    fun attemptLog(c: Context) = prefs(c).getString("attempt_log", "") ?: ""

    /** SCREEN_OFF cancels the current transaction; it never starts a download. */
    fun cancelForLock() {
        generation++
        armed = true
        ZoomEarthCapture.cancel()
    }

    /** Only system USER_PRESENT receivers call this entry point. No timers or retries later. */
    fun onUnlock(context: Context) {
        val app = context.applicationContext
        if (!unlocked(app)) {
            note(app, "desbloqueo", "aviso recibido, pero Android aún indica pantalla bloqueada o apagada")
            return
        }
        if (!armed) return // duplicate notification within the same unlock session
        armed = false
        note(app, "desbloqueo", "aviso recibido")
        if (!prefs(app).contains("interval_minutes")) {
            note(app, "desbloqueo", "elige el intervalo en la app antes de descargar"); return
        }
        if (running) { note(app, "desbloqueo", "ya hay una captura en curso"); return }
        if (!due(app)) {
            note(app, "desbloqueo", "esperando intervalo de ${interval(app)} min desde la última actualización"); return
        }
        if (!isOnline(app)) { note(app, "desbloqueo", "sin conexión; se conserva la imagen"); return }
        running = true
        val token = ++generation
        val started = SystemClock.elapsedRealtime()
        val url = viewUrl(app)
        app.cacheDir.listFiles()?.filter { it.name.startsWith("capture-") && it.extension == "jpg" }?.forEach { it.delete() }
        val tmp = File(app.cacheDir, "capture-$token.jpg")
        val valid = { token == generation && unlocked(app) && url == viewUrl(app) }
        note(app, "desbloqueo", "iniciando captura; intervalo ${interval(app)} min")
        val done: (String?) -> Unit = { error ->
            // Callback always on main. Commit and notify together, only for the live unlock session.
            var ok = false
            if (error == null && valid()) {
                ok = runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(tmp.path, bounds)
                    check(bounds.outWidth > 0 && bounds.outHeight > 0)
                    // Same directory/filesystem: rename replaces atomically on Android.
                    check(tmp.renameTo(cacheFile(app)))
                    File(app.filesDir, "satellite_cache.jpg").delete() // remove the pre-4.2 persistent copy
                    prefs(app).edit().putLong("updated_at", System.currentTimeMillis())
                        .putLong("success_elapsed", SystemClock.elapsedRealtime())
                        .putInt("success_boot", boot(app)).apply()
                    true
                }.getOrDefault(false)
            }
            tmp.delete()
            running = false
            lastError = if (ok) null else error ?: "captura cancelada o archivo no válido"
            note(app, "desbloqueo", if (ok) "OK ${SystemClock.elapsedRealtime() - started} ms [${ZoomEarthCapture.lastMode}]" else "sin actualizar: $lastError")
            if (ok) {
                app.sendBroadcast(Intent(SatelliteWallpaperService.ACTION_CACHE_UPDATED).setPackage(app.packageName))
                if (lockEnabled(app)) io.execute { if (valid()) applyLockScreen(app, valid) }
            }
        }
        try { ZoomEarthCapture.captureBackground(app, tmp, url, done) }
        catch (t: Throwable) { done("${t.javaClass.simpleName}: ${t.message}") }
    }
    private fun applyLockScreen(c: Context, valid: () -> Boolean) {
        if (Build.VERSION.SDK_INT < 24 || !valid()) return
        var src: Bitmap? = null
        var out: Bitmap? = null
        try {
            src = loadCached(c) ?: return
            val (w, h) = ImageBudget.size(c)
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            CropMath.draw(canvas, src, w, h, loadState(c))
            CropMath.drawUpdatedAt(canvas, updatedAt(c), w, h)
            if (valid()) WallpaperManager.getInstance(c).setBitmap(out, null, true, WallpaperManager.FLAG_LOCK)
        } catch (t: Throwable) { note(c, "bloqueo", "no se pudo aplicar: ${t.javaClass.simpleName}") }
        finally { src?.recycle(); out?.recycle() }
    }
}
