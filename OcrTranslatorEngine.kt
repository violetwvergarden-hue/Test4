package id.mangascreen.translator

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

enum class SourceLang(val code: String, val displayName: String, val mlKitLang: String) {
    JAPANESE("JA", "Jepang (Manga)", TranslateLanguage.JAPANESE),
    KOREAN("KO", "Korea (Manhwa)", TranslateLanguage.KOREAN),
    ENGLISH("EN", "Inggris (Comic)", TranslateLanguage.ENGLISH);

    companion object {
        fun fromCode(code: String): SourceLang {
            return entries.find { it.code == code } ?: JAPANESE
        }
    }
}

data class TranslatedBlock(
    val boundingBox: Rect,
    val originalText: String,
    val translatedText: String
)

class OcrTranslatorEngine {

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val japaneseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private val koreanRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    private val translators = mutableMapOf<SourceLang, Translator>()

    private fun getTranslator(sourceLang: SourceLang): Translator {
        return translators.getOrPut(sourceLang) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang.mlKitLang)
                .setTargetLanguage(TranslateLanguage.INDONESIAN)
                .build()
            Translation.getClient(options)
        }
    }

    suspend fun ensureModelsDownloaded(
        sourceLang: SourceLang,
        onStatus: (String) -> Unit
    ): Boolean {
        return try {
            onStatus("Mengunduh/memeriksa kamus ${sourceLang.displayName} -> Indonesia...")
            val conditions = DownloadConditions.Builder().build()
            val translator = getTranslator(sourceLang)
            translator.downloadModelIfNeeded(conditions).await()
            onStatus("Model ${sourceLang.displayName} -> Indonesia siap digunakan (bisa offline)!")
            true
        } catch (e: Exception) {
            onStatus("Gagal mengunduh model: ${e.localizedMessage ?: "Pastikan ada koneksi internet"}")
            false
        }
    }

    suspend fun recognizeAndTranslateScreen(
        bitmap: Bitmap,
        sourceLang: SourceLang
    ): List<TranslatedBlock> {
        val recognizer = when (sourceLang) {
            SourceLang.JAPANESE -> japaneseRecognizer
            SourceLang.KOREAN -> koreanRecognizer
            SourceLang.ENGLISH -> latinRecognizer
        }

        val translator = getTranslator(sourceLang)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(inputImage).await()

        val results = mutableListOf<TranslatedBlock>()

        for (block in visionText.textBlocks) {
            val rect = block.boundingBox ?: continue
            val rawText = block.text
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (rawText.isEmpty()) continue
            if (sourceLang == SourceLang.ENGLISH && rawText.length < 2) continue

            val translated = try {
                translator.translate(rawText).await()
            } catch (e: Exception) {
                rawText
            }

            if (translated.isNotBlank()) {
                results.add(
                    TranslatedBlock(
                        boundingBox = rect,
                        originalText = rawText,
                        translatedText = translated
                    )
                )
            }
        }

        return results
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
