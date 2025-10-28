package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 截图管理器
 * 使用MediaProjection API进行屏幕截图
 */
class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val projectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    /**
     * 创建截图Intent，用于请求权限
     */
    fun createScreenCaptureIntent(): Intent {
        return projectionManager.createScreenCaptureIntent()
    }

    // 保存权限数据，用于重新创建MediaProjection
    private var savedResultCode: Int? = null
    private var savedData: Intent? = null

    /**
     * 初始化MediaProjection（保存权限数据）
     */
    fun initMediaProjection(resultCode: Int, data: Intent?) {
        // 保存权限数据
        savedResultCode = resultCode
        savedData = data

        // 清理旧的MediaProjection
        release()

        // 创建新的MediaProjection
        createMediaProjection()
    }

    /**
     * 创建MediaProjection实例（只创建一次）
     */
    private fun createMediaProjection() {
        if (savedResultCode == null || savedData == null) {
            return
        }

        try {
            mediaProjection =
                projectionManager.getMediaProjection(savedResultCode!!, savedData!!).also {
                    // 注册回调以管理资源（Android 14+强制要求，但所有版本都支持）
                    it!!.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            super.onStop()
                            // MediaProjection停止时清理所有资源
                            cleanUpVirtualDisplay()
                        }
                    }, Handler(Looper.getMainLooper()))
                }
        } catch (e: Exception) {
            e.printStackTrace()
            mediaProjection = null
        }
    }

    /**
     * 执行截图
     */
    suspend fun captureScreen(): Bitmap = suspendCancellableCoroutine { continuation ->
        try {
            // 检查MediaProjection是否已初始化
            if (mediaProjection == null) {
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(Exception("MediaProjection未初始化，请先授权截图权限"))
                }
                return@suspendCancellableCoroutine
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            // 如果VirtualDisplay和ImageReader不存在，创建它们（只创建一次）
            if (virtualDisplay == null || imageReader == null) {
                // 创建ImageReader
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

                // 创建虚拟显示（保持一直存在）
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenCapture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface,
                    null,
                    null
                )
            }

            // 设置图像可用监听器（每次截图都设置，确保回调正确）
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image: Image? = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image, width, height)
                        image.close()

                        // 不清理VirtualDisplay，保留它供下次使用

                        if (!continuation.isCompleted) {
                            continuation.resume(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(e)
                    }
                }
            }, null)

            // 设置取消回调
            continuation.invokeOnCancellation {
                // 取消时也不清理，保留VirtualDisplay
            }
        } catch (e: Exception) {
            if (!continuation.isCompleted) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * 将Image转换为Bitmap
     */
    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // 裁剪多余的部分
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        }
    }

    /**
     * 清理VirtualDisplay和ImageReader（保留MediaProjection）
     */
    private fun cleanUpVirtualDisplay() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
    }

    /**
     * 释放所有资源（包括MediaProjection和保存的权限数据）
     */
    fun release() {
        cleanUpVirtualDisplay()
        mediaProjection?.stop()
        mediaProjection = null
        // 不清理savedResultCode和savedData，以便重新创建
    }

    /**
     * 完全清理（包括权限数据）
     */
    fun releaseAll() {
        release()
        savedResultCode = null
        savedData = null
    }
}

