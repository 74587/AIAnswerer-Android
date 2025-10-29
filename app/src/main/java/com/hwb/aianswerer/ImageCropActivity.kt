package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import com.hwb.aianswerer.utils.ImageCropUtil
import com.hwb.aianswerer.utils.LanguageUtil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 图片裁剪Activity
 * 简化版：只支持拖动左上角和右下角调整矩形区域
 */
class ImageCropActivity : ComponentActivity() {

    private var imagePath: String? = null
    private var bitmap: Bitmap? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取图片路径
        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imagePath == null) {
            finish()
            return
        }

        // 加载图片
        try {
            bitmap = ImageCropUtil.loadBitmapFromFile(imagePath!!)
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
            return
        }

        setContent {
            AIAnswererTheme {
                ImageCropScreen(
                    bitmap = bitmap!!,
                    onConfirm = { cropRect ->
                        // 通过广播返回裁剪坐标
                        val broadcastIntent =
                            Intent(FloatingWindowService.ACTION_CROP_RESULT).apply {
                                setPackage(packageName)
                                putExtra(FloatingWindowService.EXTRA_IMAGE_PATH, imagePath)
                                putExtra(EXTRA_TOP_LEFT_X, cropRect.topLeft.x)
                                putExtra(EXTRA_TOP_LEFT_Y, cropRect.topLeft.y)
                                putExtra(EXTRA_BOTTOM_RIGHT_X, cropRect.bottomRight.x)
                                putExtra(EXTRA_BOTTOM_RIGHT_Y, cropRect.bottomRight.y)
                            }
                        sendBroadcast(broadcastIntent)
                        finish()
                    },
                    onCancel = {
                        // 取消时删除临时文件
                        imagePath?.let { ImageCropUtil.deleteTempFile(it) }
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 回收bitmap
        bitmap?.recycle()
        bitmap = null
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_TOP_LEFT_X = "top_left_x"
        const val EXTRA_TOP_LEFT_Y = "top_left_y"
        const val EXTRA_BOTTOM_RIGHT_X = "bottom_right_x"
        const val EXTRA_BOTTOM_RIGHT_Y = "bottom_right_y"
    }
}

@Composable
fun ImageCropScreen(
    bitmap: Bitmap,
    onConfirm: (CropRect) -> Unit,
    onCancel: () -> Unit
) {
    // 画布尺寸
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // 原始图片尺寸
    val imageWidth = bitmap.width
    val imageHeight = bitmap.height

    // 计算图片适配屏幕的缩放比例和位置（留出底部按钮空间）
    val bottomButtonHeight = 100f // 底部按钮区域高度

    // 使用 derivedStateOf 确保这些值随 canvasSize 变化而更新
    val imageScale = remember(canvasSize.width, canvasSize.height) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val availableHeight = canvasSize.height - bottomButtonHeight - 40 // 留出更多空间
            val scaleX = canvasSize.width.toFloat() / imageWidth
            val scaleY = availableHeight / imageHeight
            min(scaleX, scaleY) * 0.9f // 留出边距
        } else {
            1f
        }
    }

    val displayWidth = remember(imageScale) { imageWidth * imageScale }
    val displayHeight = remember(imageScale) { imageHeight * imageScale }
    val displayLeft = remember(canvasSize.width, displayWidth) {
        (canvasSize.width - displayWidth) / 2
    }
    val displayTop = 80f // 顶部留出空间给提示

    // 裁剪矩形的两个角（相对于画布坐标）
    var topLeft by remember { mutableStateOf(PointF(0f, 0f)) }
    var bottomRight by remember { mutableStateOf(PointF(0f, 0f)) }

    // 当前拖动的角（-1: 无, 0: 左上, 1: 右下）
    var draggingCorner by remember { mutableStateOf(-1) }

    // 初始化裁剪矩形（在画布尺寸确定后）
    val isInitialized = remember { mutableStateOf(false) }
    if (canvasSize.width > 0 && displayWidth > 0 && !isInitialized.value) {
        val margin = min(displayWidth, displayHeight) * 0.1f
        topLeft = PointF(displayLeft + margin, displayTop + margin)
        bottomRight = PointF(
            displayLeft + displayWidth - margin,
            displayTop + displayHeight - margin
        )
        isInitialized.value = true
    }

    // 转换为原始图片坐标
    fun screenToImageCoords(point: PointF): PointF {
        val imageX = ((point.x - displayLeft) / imageScale).coerceIn(0f, imageWidth.toFloat())
        val imageY = ((point.y - displayTop) / imageScale).coerceIn(0f, imageHeight.toFloat())
        return PointF(imageX, imageY)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 图片和裁剪框层（只在图片区域响应拖动）
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(displayLeft, displayWidth, displayTop, displayHeight) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val touchX = offset.x
                                val touchY = offset.y

                                // 检测是否在左上角附近
                                val topLeftDist = sqrt(
                                    (touchX - topLeft.x) * (touchX - topLeft.x) +
                                            (touchY - topLeft.y) * (touchY - topLeft.y)
                                )

                                // 检测是否在右下角附近
                                val bottomRightDist = sqrt(
                                    (touchX - bottomRight.x) * (touchX - bottomRight.x) +
                                            (touchY - bottomRight.y) * (touchY - bottomRight.y)
                                )

                                val touchRadius = 60f

                                draggingCorner = when {
                                    topLeftDist < touchRadius -> 0
                                    bottomRightDist < touchRadius -> 1
                                    else -> -1
                                }
                            },
                            onDrag = { change, dragAmount ->
                                when (draggingCorner) {
                                    0 -> {
                                        // 拖动左上角
                                        topLeft = PointF(
                                            (topLeft.x + dragAmount.x).coerceIn(
                                                displayLeft,
                                                bottomRight.x - 50f
                                            ),
                                            (topLeft.y + dragAmount.y).coerceIn(
                                                displayTop,
                                                bottomRight.y - 50f
                                            )
                                        )
                                        change.consume()
                                    }

                                    1 -> {
                                        // 拖动右下角
                                        bottomRight = PointF(
                                            (bottomRight.x + dragAmount.x).coerceIn(
                                                topLeft.x + 50f,
                                                displayLeft + displayWidth
                                            ),
                                            (bottomRight.y + dragAmount.y).coerceIn(
                                                topLeft.y + 50f,
                                                displayTop + displayHeight
                                            )
                                        )
                                        change.consume()
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingCorner = -1
                            }
                        )
                    }
            ) {
                // 绘制图片
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(displayLeft.toInt(), displayTop.toInt()),
                    dstSize = IntSize(displayWidth.toInt(), displayHeight.toInt())
                )

                val cropLeft = topLeft.x.coerceIn(displayLeft, displayLeft + displayWidth)
                val cropTop = topLeft.y.coerceIn(displayTop, displayTop + displayHeight)
                val cropRight = bottomRight.x.coerceIn(displayLeft, displayLeft + displayWidth)
                val cropBottom = bottomRight.y.coerceIn(displayTop, displayTop + displayHeight)

                // 绘制四个半透明遮罩区域（选择框外的部分）
                val maskColor = Color.Black.copy(alpha = 0.6f)

                // 上方遮罩
                if (cropTop > displayTop) {
                    drawRect(
                        color = maskColor,
                        topLeft = Offset(displayLeft, displayTop),
                        size = Size(displayWidth, cropTop - displayTop)
                    )
                }

                // 下方遮罩
                if (cropBottom < displayTop + displayHeight) {
                    drawRect(
                        color = maskColor,
                        topLeft = Offset(displayLeft, cropBottom),
                        size = Size(displayWidth, displayTop + displayHeight - cropBottom)
                    )
                }

                // 左侧遮罩
                if (cropLeft > displayLeft) {
                    drawRect(
                        color = maskColor,
                        topLeft = Offset(displayLeft, cropTop),
                        size = Size(cropLeft - displayLeft, cropBottom - cropTop)
                    )
                }

                // 右侧遮罩
                if (cropRight < displayLeft + displayWidth) {
                    drawRect(
                        color = maskColor,
                        topLeft = Offset(cropRight, cropTop),
                        size = Size(displayLeft + displayWidth - cropRight, cropBottom - cropTop)
                    )
                }

                // 绘制选择框边框（虚线）
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = Offset(cropLeft, cropTop),
                    size = Size(cropRight - cropLeft, cropBottom - cropTop),
                    style = Stroke(
                        width = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )

                // 绘制四个角的拖动点
                val cornerRadius = 12f
                val cornerColor = Color(0xFF2196F3)
                val cornerStrokeColor = Color.White

                // 左上角
                drawCircle(
                    color = cornerStrokeColor,
                    radius = cornerRadius + 2f,
                    center = Offset(cropLeft, cropTop)
                )
                drawCircle(
                    color = cornerColor,
                    radius = cornerRadius,
                    center = Offset(cropLeft, cropTop)
                )

                // 右下角
                drawCircle(
                    color = cornerStrokeColor,
                    radius = cornerRadius + 2f,
                    center = Offset(cropRight, cropBottom)
                )
                drawCircle(
                    color = cornerColor,
                    radius = cornerRadius,
                    center = Offset(cropRight, cropBottom)
                )
            }

            // 顶部提示卡片
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.crop_instruction),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 底部按钮栏
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 取消按钮
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.crop_cancel))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 重置按钮
                    OutlinedButton(
                        onClick = {
                            // 重置裁剪框
                            val margin = min(displayWidth, displayHeight) * 0.1f
                            topLeft = PointF(displayLeft + margin, displayTop + margin)
                            bottomRight = PointF(
                                displayLeft + displayWidth - margin,
                                displayTop + displayHeight - margin
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.crop_reset))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 确认按钮
                    Button(
                        onClick = {
                            // 转换坐标并返回
                            val cropRect = CropRect(
                                topLeft = screenToImageCoords(topLeft),
                                bottomRight = screenToImageCoords(bottomRight)
                            )
                            onConfirm(cropRect)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.crop_confirm),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
