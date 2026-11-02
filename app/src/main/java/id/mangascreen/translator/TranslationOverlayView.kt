package id.mangascreen.translator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class TranslationOverlayView(
    context: Context,
    private val blocks: List<TranslatedBlock>,
    private val sourceLang: SourceLang,
    private val onDismiss: () -> Unit,
    private val onCycleLang: () -> Unit
) : View(context) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val bubbleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(242, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 41, 59)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(15, 23, 42)
        isFakeBoldText = true
    }

    private val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 15, 23, 42)
        style = Paint.Style.FILL
    }

    private val bannerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }

    private val closeBannerRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        for (item in blocks) {
            val box = item.boundingBox
            val pad = 8f
            val rectF = RectF(
                max(4f, box.left - pad),
                max(4f, box.top - pad),
                min(width - 4f, box.right + pad),
                min(height - 4f, box.bottom + pad)
            )

            val boxWidth = max(80, rectF.width().toInt() - 16)
            val boxHeight = max(40f, rectF.height())

            var candidateSize = 36f
            var staticLayout = buildLayout(item.translatedText, boxWidth, candidateSize)
            while (staticLayout.height > boxHeight && candidateSize > 18f) {
                candidateSize -= 2f
                staticLayout = buildLayout(item.translatedText, boxWidth, candidateSize)
            }

            val finalHeight = max(rectF.height(), staticLayout.height + 16f)
            rectF.bottom = min(height - 8f, rectF.top + finalHeight)

            canvas.drawRoundRect(rectF, 14f, 14f, bubbleFillPaint)
            canvas.drawRoundRect(rectF, 14f, 14f, bubbleStrokePaint)

            canvas.save()
            val textX = rectF.left + 8f
            val textY = rectF.top + max(6f, (rectF.height() - staticLayout.height) / 2f)
            canvas.translate(textX, textY)
            staticLayout.draw(canvas)
            canvas.restore()
        }

        val bannerTop = 72f
        val bannerHeight = 88f
        closeBannerRect.set(32f, bannerTop, width - 32f, bannerTop + bannerHeight)
        canvas.drawRoundRect(closeBannerRect, 44f, 44f, bannerPaint)

        val infoText = if (blocks.isEmpty()) {
            "Tidak ada teks (${sourceLang.code}) terdeteksi • Ketuk untuk tutup"
        } else {
            "${blocks.size} balon diterjemahkan (${sourceLang.code}->ID) • Ketuk layar utk tutup"
        }
        canvas.drawText(infoText, 64f, bannerTop + 55f, bannerTextPaint)
    }

    private fun buildLayout(text: String, widthPx: Int, textSizePx: Float): StaticLayout {
        textPaint.textSize = textSizePx
        return StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, max(60, widthPx))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.0f)
            .build()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            onDismiss()
            return true
        }
        return true
    }
}
