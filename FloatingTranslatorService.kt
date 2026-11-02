package id.mangascreen.translator

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class FloatingTranslatorService : Service() {

    companion object {
        const val ACTION_START = "id.mangascreen.translator.ACTION_START"
        const val ACTION_STOP = "id.mangascreen.translator.ACTION_STOP"
        const val ACTION_UPDATE_LANG = "id.mangascreen.translator.ACTION_UPDATE_LANG"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_SOURCE_LANG = "extra_source_lang"
        private const val CHANNEL_ID = "manga_translator_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var floatingBubbleView: View? = null
    private var bubbleLabelView: TextView? = null
    private var overlayView: TranslationOverlayView? = null

    private var currentSourceLang: SourceLang = SourceLang.JAPANESE
    private var isProcessing = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine = OcrTranslatorEngine()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_LANG -> {
                val langCode = intent.getStringExtra(EXTRA_SOURCE_LANG) ?: SourceLang.JAPANESE.code
                currentSourceLang = SourceLang.fromCode(langCode)
                updateBubbleText()
                Toast.makeText(
                    this,
                    "Bahasa komik: ${currentSourceLang.displayName} -> Indonesia",
                    Toast.LENGTH_SHORT
                ).show()
                return START_STICKY
            }
            ACTION_START -> {
                val langCode = intent.getStringExtra(EXTRA_SOURCE_LANG) ?: SourceLang.JAPANESE.code
                currentSourceLang = SourceLang.fromCode(langCode)

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultData == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                setupMediaProjection(resultCode, resultData)
                showFloatingBubble()
            }
        }
        return START_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, resultData: Intent) {
        cleanupVirtualDisplay()
        mediaProjection?.stop()
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                cleanupVirtualDisplay()
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun showFloatingBubble() {
        if (floatingBubbleView != null) {
            updateBubbleText()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 60f
                setColor(Color.parseColor("#E61E293B"))
                setStroke(4, Color.parseColor("#38BDF8"))
            }
            background = bg
            setPadding(28, 20, 28, 20)
            elevation = 16f
        }

        val label = TextView(this).apply {
            text = "TERJEMAH\n[${currentSourceLang.code}->ID]"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        bubbleLabelView = label
        container.addView(label)

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 360
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(container, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            triggerFullScreenTranslation()
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingBubbleView = container
        windowManager.addView(container, params)
    }

    private fun updateBubbleText() {
        bubbleLabelView?.text = if (isProcessing) {
            "MEMBACA...\n[${currentSourceLang.code}]"
        } else {
            "TERJEMAH\n[${currentSourceLang.code}->ID]"
        }
    }

    private fun triggerFullScreenTranslation() {
        if (isProcessing) return
        val mp = mediaProjection
        if (mp == null) {
            Toast.makeText(this, "Izin tangkap layar belum aktif", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        removeTranslationOverlay()
        updateBubbleText()

        serviceScope.launch {
            try {
                floatingBubbleView?.visibility = View.INVISIBLE
                delay(150)

                val screenBitmap = captureScreenBitmap(mp)
                floatingBubbleView?.visibility = View.VISIBLE

                if (screenBitmap == null) {
                    Toast.makeText(
                        this@FloatingTranslatorService,
                        "Gagal membaca layar, coba ketuk lagi",
                        Toast.LENGTH_SHORT
                    ).show()
                    isProcessing = false
                    updateBubbleText()
                    return@launch
                }

                val translatedBlocks = withContext(Dispatchers.Default) {
                    engine.recognizeAndTranslateScreen(screenBitmap, currentSourceLang)
                }
                screenBitmap.recycle()

                showTranslationOverlay(translatedBlocks)
            } catch (e: Exception) {
                floatingBubbleView?.visibility = View.VISIBLE
                Toast.makeText(
                    this@FloatingTranslatorService,
                    "Error terjemahan: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isProcessing = false
                updateBubbleText()
            }
        }
    }

    private suspend fun captureScreenBitmap(mp: MediaProjection): Bitmap? =
        withContext(Dispatchers.Main) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            if (imageReader == null || virtualDisplay == null) {
                cleanupVirtualDisplay()
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                virtualDisplay = mp.createVirtualDisplay(
                    "MangaScreenCapture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface,
                    null,
                    null
                )
                delay(200)
            }

            val reader = imageReader ?: return@withContext null
            var image = reader.acquireLatestImage()
            var attempts = 0
            while (image == null && attempts < 4) {
                delay(100)
                image = reader.acquireLatestImage()
                attempts++
            }
            if (image == null) return@withContext null

            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val paddedBitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(buffer)

                if (rowPadding == 0) {
                    paddedBitmap
                } else {
                    val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                    paddedBitmap.recycle()
                    cropped
                }
            } finally {
                image.close()
            }
        }

    private fun showTranslationOverlay(blocks: List<TranslatedBlock>) {
        removeTranslationOverlay()

        val view = TranslationOverlayView(
            context = this,
            blocks = blocks,
            sourceLang = currentSourceLang,
            onDismiss = { removeTranslationOverlay() },
            onCycleLang = {
                currentSourceLang = when (currentSourceLang) {
                    SourceLang.JAPANESE -> SourceLang.KOREAN
                    SourceLang.KOREAN -> SourceLang.ENGLISH
                    SourceLang.ENGLISH -> SourceLang.JAPANESE
                }
                updateBubbleText()
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = view
        windowManager.addView(view, params)
    }

    private fun removeTranslationOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
    }

    private fun cleanupVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Penerjemah Layar Manga",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga tombol melayang penerjemah layar tetap aktif saat baca manga"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingTranslatorService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Penerjemah Manga Aktif (${currentSourceLang.code} -> ID)")
            .setContentText("Ketuk tombol melayang untuk menerjemahkan seluruh layar")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Matikan", pendingStop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        removeTranslationOverlay()
        floatingBubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        floatingBubbleView = null
        cleanupVirtualDisplay()
        mediaProjection?.stop()
        mediaProjection = null
        engine.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
