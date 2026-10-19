package com.satellite.wallpaper

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var preview: CropPreviewView
    private lateinit var button: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var changeView: Button
    private lateinit var grantOverlay: Button
    private lateinit var lockStatic: CheckBox
    private var leavingActivity = false
    private val updates = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (!isActivityUsable()) return
            if (intent.action == SatelliteWallpaperService.ACTION_CACHE_UPDATED) loadCachedPreview()
            updateStatus()
        }
    }
    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter(SatelliteWallpaperService.ACTION_CACHE_UPDATED).apply { addAction(WallpaperRepository.ACTION_STATUS) }
        androidx.core.content.ContextCompat.registerReceiver(
            this, updates, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    override fun onStop() {
        unregisterReceiver(updates)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySafeBottomInset()

        preview = findViewById(R.id.crop_preview)
        button = findViewById(R.id.set_wallpaper)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)


        preview.onStateChanged = { WallpaperRepository.saveState(this, it) }
        button.setOnClickListener { openConfirm() }

        changeView = findViewById(R.id.change_view)
        grantOverlay = findViewById(R.id.grant_overlay)
        lockStatic = findViewById(R.id.lock_static)

        changeView.setOnClickListener { openPicker() }
        grantOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.grant_battery).setOnClickListener {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
        findViewById<Button>(R.id.test_bg).apply {
            text = "Intervalo de actualización"
            setOnClickListener { chooseInterval() }
        }
        UnlockService.start(this) // mantiene la app viva y escucha el desbloqueo aunque esté cerrada
        lockStatic.isChecked = WallpaperRepository.lockEnabled(this)
        lockStatic.setOnCheckedChangeListener { _, checked ->
            WallpaperRepository.setLockEnabled(this, checked)

        }

        loadCachedPreview()
        if (savedInstanceState == null) chooseInterval()
    }

    private fun chooseInterval() {
        val values = UpdatePolicy.minutes
        android.app.AlertDialog.Builder(this)
            .setTitle("Tiempo mínimo entre actualizaciones")
            .setSingleChoiceItems(arrayOf("0 minutos (cada desbloqueo)", "15 minutos", "30 minutos", "60 minutos"),
                values.indexOf(WallpaperRepository.interval(this))) { dialog, which ->
                WallpaperRepository.setInterval(this, values[which])
                dialog.dismiss()
                loadCachedPreview()
            }
            .setCancelable(false)
            .show()
    }

    private fun versionLabel(): String = runCatching {
        val pi = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        "v${pi.versionName} (build $code)"
    }.getOrDefault("v?")

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.version).text = "Versión ${versionLabel()}"
        grantOverlay.visibility =
            if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
        val ignoring = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        findViewById<View>(R.id.grant_battery).visibility = if (ignoring) View.GONE else View.VISIBLE
        loadCachedPreview()
        updateStatus()
    }

    private fun updateStatus() {
        val log = WallpaperRepository.attemptLog(this)
        findViewById<TextView>(R.id.attempt_log).text = "${versionLabel()} · últimos intentos:\n$log"
        val busy = WallpaperRepository.isRunning()
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) {
            status.visibility = View.VISIBLE
            status.text = "Descargando la vista de Zoom Earth… Mantén el teléfono desbloqueado."
        } else if (!WallpaperRepository.cacheFile(this).exists()) {
            status.visibility = View.VISIBLE
            status.text = WallpaperRepository.lastError?.let { "No se pudo descargar: $it\nConsulta el registro inferior." }
                ?: "Sin imagen todavía. Elige el intervalo, bloquea y desbloquea el teléfono. Consulta abajo si se recibe el aviso."
        } else status.visibility = View.GONE
    }

    private fun openPicker() {
        startActivityForResult(Intent(this, PickViewActivity::class.java), REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!isActivityUsable()) return
        if (requestCode == REQ_CONFIRM) {
            if (resultCode == RESULT_OK) saveAndOpenWallpaperPicker()
            return
        }
        if (requestCode != REQ_PICK) return
        loadCachedPreview()
    }

    private fun loadCachedPreview() {
        val cached = WallpaperRepository.loadCached(this)
        if (cached != null) showImageSafely(cached)
        else {
            progress.visibility = View.GONE
            status.visibility = View.VISIBLE
            status.text = "Elige una vista si lo deseas. Bloquea y desbloquea el teléfono para obtener la primera imagen."
            button.isEnabled = false
        }
    }

    override fun onDestroy() {
        leavingActivity = true
        super.onDestroy()
    }

    private fun applySafeBottomInset() {
        val root = findViewById<View>(R.id.root_container)
        val basePadding = root.paddingBottom
        val extra = (24 * resources.displayMetrics.density).toInt()
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, basePadding + bottom + extra)
            insets
        }
        root.requestApplyInsets()
    }

    /** Muestra la imagen a pantalla completa con Aceptar / Cancelar antes de aplicar el fondo. */
    private fun openConfirm() {
        WallpaperRepository.saveState(this, preview.state)
        startActivityForResult(Intent(this, ConfirmWallpaperActivity::class.java), REQ_CONFIRM)
    }

    private fun showImageSafely(bitmap: Bitmap) {
        try {
            showImage(bitmap)
        } catch (t: Throwable) {
            showError()
        }
    }

    private fun showImage(bitmap: Bitmap) {
        progress.visibility = View.GONE
        status.visibility = View.GONE
        preview.setBitmap(
            bitmap,
            WallpaperRepository.loadState(this),
            WallpaperRepository.updatedAt(this)
        )
        button.isEnabled = true
    }

    private fun showError() {
        if (!isActivityUsable()) return
        progress.visibility = View.GONE
        status.visibility = View.VISIBLE
        val reason = WallpaperRepository.lastError
        status.text = getString(R.string.download_error) +
            (if (reason != null) "\n\nMotivo: $reason" else "") +
            "\n\n" + "Bloquea y desbloquea para volver a intentarlo."
        button.isEnabled = false
    }

    private fun isActivityUsable(): Boolean = !(leavingActivity || isFinishing || isDestroyed)

    private fun saveAndOpenWallpaperPicker() {
        WallpaperRepository.saveState(this, preview.state)
        val component = ComponentName(this, SatelliteWallpaperService::class.java)
        val intent = Intent("android.service.wallpaper.CHANGE_LIVE_WALLPAPER").apply {
            putExtra("android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT", component)
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            startActivity(Intent("android.service.wallpaper.LIVE_WALLPAPER_CHOOSER"))
        }
    }

    companion object {
        private const val REQ_PICK = 1001
        private const val REQ_CONFIRM = 1002
    }
}
