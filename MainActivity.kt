package id.mangascreen.translator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var radioGroupLang: RadioGroup
    private lateinit var radioJapanese: RadioButton
    private lateinit var radioKorean: RadioButton
    private lateinit var radioEnglish: RadioButton
    private lateinit var tvStatusModel: TextView
    private lateinit var btnDownloadModels: Button
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnStartFloating: Button
    private lateinit var btnStopFloating: Button

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine = OcrTranslatorEngine()

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startFloatingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(
                this,
                "Izin tangkapan layar diperlukan untuk membaca halaman manga",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        requestScreenCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        radioGroupLang = findViewById(R.id.radioGroupLang)
        radioJapanese = findViewById(R.id.radioJapanese)
        radioKorean = findViewById(R.id.radioKorean)
        radioEnglish = findViewById(R.id.radioEnglish)
        tvStatusModel = findViewById(R.id.tvStatusModel)
        btnDownloadModels = findViewById(R.id.btnDownloadModels)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnStartFloating = findViewById(R.id.btnStartFloating)
        btnStopFloating = findViewById(R.id.btnStopFloating)

        radioGroupLang.setOnCheckedChangeListener { _, _ ->
            val selected = getSelectedSourceLang()
            val updateIntent = Intent(this, FloatingTranslatorService::class.java).apply {
                action = FloatingTranslatorService.ACTION_UPDATE_LANG
                putExtra(FloatingTranslatorService.EXTRA_SOURCE_LANG, selected.code)
            }
            startService(updateIntent)
        }

        btnDownloadModels.setOnClickListener {
            downloadAllModels()
        }

        btnOverlayPermission.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Izin tombol melayang sudah aktif!", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartFloating.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Aktifkan dulu izin tampil di atas aplikasi lain (Langkah 1)",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestScreenCapture()
            }
        }

        btnStopFloating.setOnClickListener {
            val stopIntent = Intent(this, FloatingTranslatorService::class.java).apply {
                action = FloatingTranslatorService.ACTION_STOP
            }
            startService(stopIntent)
            Toast.makeText(this, "Tombol melayang dimatikan", Toast.LENGTH_SHORT).show()
        }

        downloadAllModels()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtonState()
    }

    private fun updatePermissionButtonState() {
        if (Settings.canDrawOverlays(this)) {
            btnOverlayPermission.text = "1. Izin Overlay Sudah Aktif (Siap)"
        } else {
            btnOverlayPermission.text = "1. Berikan Izin Tombol Melayang (Overlay)"
        }
    }

    private fun getSelectedSourceLang(): SourceLang {
        return when (radioGroupLang.checkedRadioButtonId) {
            R.id.radioKorean -> SourceLang.KOREAN
            R.id.radioEnglish -> SourceLang.ENGLISH
            else -> SourceLang.JAPANESE
        }
    }

    private fun downloadAllModels() {
        btnDownloadModels.isEnabled = false
        uiScope.launch {
            var allSuccess = true
            for (lang in listOf(SourceLang.JAPANESE, SourceLang.KOREAN, SourceLang.ENGLISH)) {
                val ok = engine.ensureModelsDownloaded(lang) { msg ->
                    tvStatusModel.text = msg
                }
                if (!ok) allSuccess = false
            }
            if (allSuccess) {
                tvStatusModel.text =
                    "Status Kamus: Jepang, Korea & Inggris -> Indonesia SIAP (Bisa Offline!)"
            }
            btnDownloadModels.isEnabled = true
        }
    }

    private fun requestScreenCapture() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val selectedLang = getSelectedSourceLang()
        val serviceIntent = Intent(this, FloatingTranslatorService::class.java).apply {
            action = FloatingTranslatorService.ACTION_START
            putExtra(FloatingTranslatorService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingTranslatorService.EXTRA_RESULT_DATA, data)
            putExtra(FloatingTranslatorService.EXTRA_SOURCE_LANG, selectedLang.code)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(
            this,
            "Tombol melayang aktif! Buka aplikasi manga lalu ketuk tombol TERJEMAH.",
            Toast.LENGTH_LONG
        ).show()
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        engine.close()
        uiScope.cancel()
        super.onDestroy()
    }
}
