package com.hwb.aianswerer

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 文本识别管理器
 * 使用ML Kit进行OCR文本识别
 */
class TextRecognitionManager {

    // 使用中文文本识别器（也支持英文）
    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 识别图片中的文本
     * @param bitmap 要识别的图片
     * @return 识别出的文本
     */
    suspend fun recognizeText(bitmap: Bitmap): Result<String> =
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val recognizedText = visionText.text
                        if (recognizedText.isNotBlank()) {
                            if (!continuation.isCompleted) {
                                continuation.resume(Result.success(recognizedText))
                            }
                        } else {
                            if (!continuation.isCompleted) {
                                continuation.resume(
                                    Result.failure(
                                        Exception(
                                            MyApplication.getString(R.string.error_no_text_recognized)
                                        )
                                    )
                                )
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        if (!continuation.isCompleted) {
                            continuation.resumeWithException(e)
                        }
                    }
                    .addOnCanceledListener {
                        if (!continuation.isCompleted) {
                            continuation.resume(
                                Result.failure(
                                    Exception(
                                        MyApplication.getString(R.string.error_recognition_cancelled)
                                    )
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 关闭识别器
     */
    fun close() {
        recognizer.close()
    }

    companion object {
        @Volatile
        private var instance: TextRecognitionManager? = null

        fun getInstance(): TextRecognitionManager {
            return instance ?: synchronized(this) {
                instance ?: TextRecognitionManager().also { instance = it }
            }
        }
    }
}

