package com.hwb.aianswerer

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hwb.aianswerer.api.OpenAIClient
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.models.formatAnswerWithConfig
import com.hwb.aianswerer.ui.icons.LocalIcons
import com.hwb.aianswerer.utils.ClipboardUtil
import com.hwb.aianswerer.utils.ImageCropUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * 悬浮窗服务
 * 显示悬浮按钮用于截图，并显示AI答案
 */
class FloatingWindowService : Service(), LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val TAG = "FloatingWindowService"

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var screenCaptureManager: ScreenCaptureManager? = null
    private val textRecognitionManager = TextRecognitionManager.getInstance()

    private var answerText = mutableStateOf<String?>(null)
    private var showAnswer = mutableStateOf(false)
    private var statusMessage = mutableStateOf<String?>(null)
    private var questionTypes = mutableSetOf<String>()  // 题型集合
    private var questionScope = ""  // 题目范围
    private var cropMode = AppConfig.CROP_MODE_FULL  // 截图识别模式
    private var savedCropRect: CropRect? = null  // 保存的裁剪坐标（单次模式）
    private var savedCropRectEach: CropRect? = null  // 保存的裁剪坐标（每次模式）

    // Lifecycle
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    // ViewModelStore
    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    // SavedStateRegistry
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val answerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_SHOW_ANSWER -> {
                    // 直接显示已获取的答案（向后兼容）
                    val answer = intent.getStringExtra(Constants.EXTRA_ANSWER_TEXT)
                    if (!answer.isNullOrBlank()) {
                        answerText.value = answer
                        showAnswer.value = true
                    }
                }

                Constants.ACTION_REQUEST_ANSWER -> {
                    // 接收问题文本，调用API获取答案
                    val questionText = intent.getStringExtra(Constants.EXTRA_QUESTION_TEXT)
                    if (!questionText.isNullOrBlank()) {
                        fetchAnswer(questionText)
                    }
                }

                ACTION_CROP_RESULT -> {
                    // 接收裁剪结果
                    val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
                    val topLeftX = intent.getFloatExtra(ImageCropActivity.EXTRA_TOP_LEFT_X, 0f)
                    val topLeftY = intent.getFloatExtra(ImageCropActivity.EXTRA_TOP_LEFT_Y, 0f)
                    val bottomRightX =
                        intent.getFloatExtra(ImageCropActivity.EXTRA_BOTTOM_RIGHT_X, 0f)
                    val bottomRightY =
                        intent.getFloatExtra(ImageCropActivity.EXTRA_BOTTOM_RIGHT_Y, 0f)

                    if (imagePath != null) {
                        val cropRect = CropRect(
                            topLeft = android.graphics.PointF(topLeftX, topLeftY),
                            bottomRight = android.graphics.PointF(bottomRightX, bottomRightY)
                        )

                        // 根据模式保存裁剪坐标
                        when (cropMode) {
                            AppConfig.CROP_MODE_ONCE -> {
                                savedCropRect = cropRect
                            }

                            AppConfig.CROP_MODE_EACH -> {
                                savedCropRectEach = cropRect
                            }
                        }

                        // 处理裁剪后的图片
                        handleCroppedImage(imagePath, cropRect)
                    }
                }

                else -> {
                    // 忽略未知广播
                }
            }
        }
    }

    companion object {
        const val ACTION_CROP_RESULT = "com.hwb.aianswerer.ACTION_CROP_RESULT"
        const val EXTRA_IMAGE_PATH = "image_path"
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(this)

        // 注册广播接收器
        val filter = IntentFilter(Constants.ACTION_SHOW_ANSWER)
        filter.addAction(Constants.ACTION_REQUEST_ANSWER)
        filter.addAction(ACTION_CROP_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(answerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(answerReceiver, filter)
        }

        createNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID, createNotification())

        showFloatingWindow()

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 初始化MediaProjection和设置
        intent?.let {
            if (it.hasExtra("resultCode") && it.hasExtra("data")) {
                val resultCode = it.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                val data = it.getParcelableExtra<Intent>("data")
                if (resultCode == Activity.RESULT_OK && data != null) {
                    screenCaptureManager?.initMediaProjection(resultCode, data)
                }
            }

            // 读取答题设置
            if (it.hasExtra("questionTypes")) {
                val typesList = it.getStringArrayListExtra("questionTypes")
                if (typesList != null) {
                    questionTypes = typesList.toMutableSet()
                }
            }

            if (it.hasExtra("questionScope")) {
                questionScope = it.getStringExtra("questionScope") ?: ""
            }

            if (it.hasExtra("cropMode")) {
                cropMode = it.getStringExtra("cropMode")
                    ?: AppConfig.CROP_MODE_FULL
            }

            // 清除保存的裁剪坐标（新答题会话）
            savedCropRect = null
            savedCropRectEach = null
        }
        return START_STICKY
    }

    private fun showFloatingWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)

            setContent {
                MaterialTheme {
                    FloatingWindowContent(
                        answerText = answerText.value,
                        showAnswer = showAnswer.value,
                        statusMessage = statusMessage.value,
                        onCaptureClick = { handleCapture() },
                        onCloseAnswer = {
                            showAnswer.value = false
                            answerText.value = null
                        },
                        onCloseStatus = {
                            statusMessage.value = null
                        },
                        onMove = { deltaX, deltaY ->
                            params.x += deltaX.toInt()
                            params.y += deltaY.toInt()
                            windowManager.updateViewLayout(this, params)
                        }
                    )
                }
            }
        }

        windowManager.addView(floatingView, params)
    }

    private fun handleCapture() {
        serviceScope.launch {
            try {
                // 隐藏上一道题的结果
                showAnswer.value = false
                answerText.value = null

                statusMessage.value = "📸 正在截图..."

                // 执行截图
                val bitmap = screenCaptureManager?.captureScreen()
                if (bitmap == null) {
                    statusMessage.value = "❌ 截图失败，请确保已授予截图权限"
                    return@launch
                }

                // 根据截图识别模式处理
                when (cropMode) {
                    AppConfig.CROP_MODE_FULL -> {
                        // 全屏模式：直接识别
                        processBitmap(bitmap)
                    }

                    AppConfig.CROP_MODE_EACH -> {
                        // 部分识别（每次）：启动裁剪Activity（传递上次的坐标）
                        launchCropActivity(bitmap, savedCropRectEach)
                    }

                    AppConfig.CROP_MODE_ONCE -> {
                        if (savedCropRect != null) {
                            // 已有保存的坐标：直接裁剪
                            val croppedBitmap = ImageCropUtil.cropBitmap(
                                bitmap,
                                savedCropRect!!
                            )
                            bitmap.recycle()
                            processBitmap(croppedBitmap)
                        } else {
                            // 没有坐标：启动裁剪Activity
                            launchCropActivity(bitmap, null)
                        }
                    }
                }

            } catch (e: Exception) {
                statusMessage.value = "❌ 操作失败: ${e.message}"
                Log.e(TAG, "❌ 操作失败: ${e.message}")
                // 5秒后自动关闭错误消息
                delay(5000)
                if (statusMessage.value?.startsWith("❌") == true) {
                    statusMessage.value = null
                }
            }
        }
    }

    /**
     * 启动裁剪Activity
     * @param bitmap 待裁剪的图片
     * @param previousCropRect 上一次的裁剪坐标（如果有的话）
     */
    private suspend fun launchCropActivity(
        bitmap: android.graphics.Bitmap,
        previousCropRect: CropRect?
    ) {
        try {
            // 保存bitmap到临时文件
            val imagePath =
                ImageCropUtil.saveBitmapToTempFile(bitmap, cacheDir)
            bitmap.recycle()

            // 启动裁剪Activity
            val intent = Intent(this, ImageCropActivity::class.java).apply {
                putExtra(ImageCropActivity.EXTRA_IMAGE_PATH, imagePath)
                // 如果有上次的裁剪坐标，则传递过去
                previousCropRect?.let {
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_TOP_LEFT_X, it.topLeft.x)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_TOP_LEFT_Y, it.topLeft.y)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_BOTTOM_RIGHT_X, it.bottomRight.x)
                    putExtra(ImageCropActivity.EXTRA_PREVIOUS_BOTTOM_RIGHT_Y, it.bottomRight.y)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)

            statusMessage.value = "✂️ 请选择识别区域..."
            delay(2000)
            statusMessage.value = null
        } catch (e: Exception) {
            statusMessage.value = "❌ 启动裁剪失败: ${e.message}"
            Log.e(TAG, "启动裁剪失败", e)
            delay(5000)
            statusMessage.value = null
        }
    }

    /**
     * 处理裁剪后的图片
     */
    private fun handleCroppedImage(
        imagePath: String,
        cropRect: CropRect
    ) {
        serviceScope.launch {
            try {
                // 加载图片
                val bitmap = ImageCropUtil.loadBitmapFromFile(imagePath)

                // 裁剪图片
                val croppedBitmap =
                    ImageCropUtil.cropBitmap(bitmap, cropRect)
                bitmap.recycle()

                // 处理裁剪后的图片（OCR）
                processBitmap(croppedBitmap)

                // 清理临时文件
                ImageCropUtil.deleteTempFile(imagePath)
            } catch (e: Exception) {
                statusMessage.value = "❌ 裁剪失败: ${e.message}"
                Log.e(TAG, "裁剪失败", e)
                delay(5000)
                statusMessage.value = null
            }
        }
    }

    /**
     * 处理bitmap（OCR识别）
     */
    private suspend fun processBitmap(bitmap: android.graphics.Bitmap) {
        try {
            statusMessage.value = "🔍 正在识别文字..."

            // 识别文本
            val result = textRecognitionManager.recognizeText(bitmap)
            bitmap.recycle()

            result.onSuccess { recognizedText ->
                statusMessage.value = "✅ 识别完成"

                // 从配置读取自动提交设置
                val autoSubmit = AppConfig.getAutoSubmit()

                if (autoSubmit) {
                    // 自动提交：直接调用fetchAnswer获取答案
                    fetchAnswer(recognizedText)
                } else {
                    // 显示确认对话框
                    val intent = Intent(
                        this@FloatingWindowService,
                        ConfirmTextActivity::class.java
                    ).apply {
                        putExtra(Constants.EXTRA_RECOGNIZED_TEXT, recognizedText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    // 2秒后自动关闭状态消息
                    delay(2000)
                    statusMessage.value = null
                }
            }.onFailure { error ->
                statusMessage.value = "❌ 文字识别失败: ${error.message}"
                // 5秒后自动关闭错误消息
                delay(5000)
                if (statusMessage.value?.startsWith("❌") == true) {
                    statusMessage.value = null
                }
            }
        } catch (e: Exception) {
            statusMessage.value = "❌ 识别失败: ${e.message}"
            Log.e(TAG, "识别失败", e)
            delay(5000)
            if (statusMessage.value?.startsWith("❌") == true) {
                statusMessage.value = null
            }
        }
    }

    /**
     * 获取问题答案
     * @param text 问题文本
     */
    private fun fetchAnswer(text: String) {
        lifecycleScope.launch {
            try {
                statusMessage.value = "🤖 正在获取答案..."

                // 从配置读取答题设置
                val questionTypes = AppConfig.getQuestionTypes()
                val questionScope = AppConfig.getQuestionScope()
                val autoCopy = AppConfig.getAutoCopy()

                val apiClient = OpenAIClient.getInstance()
                val result = apiClient.analyzeQuestion(text, questionTypes, questionScope)

                result.onSuccess { aiAnswer ->
                    // 读取答题卡片显示配置
                    val showQuestion =
                        AppConfig.getShowAnswerCardQuestion()
                    val showOptions = AppConfig.getShowAnswerCardOptions()

                    // 根据配置格式化答案
                    val formattedAnswer = aiAnswer.formatAnswerWithConfig(showQuestion, showOptions)

                    // 根据设置决定是否复制到剪贴板
                    if (autoCopy) {
                        ClipboardUtil.copyToClipboard(this@FloatingWindowService, formattedAnswer)
                    }

                    // 显示在悬浮窗
                    answerText.value = formattedAnswer
                    showAnswer.value = true

                    statusMessage.value = if (autoCopy) "✅ 答案已复制" else "✅ 答案已生成"
                    delay(2000)
                    statusMessage.value = null
                }.onFailure { error ->
                    statusMessage.value = "❌ AI分析失败: ${error.message}"
                    Log.e(TAG, "❌ AI分析失败: ${error.message}")
                    delay(5000)
                    if (statusMessage.value?.startsWith("❌") == true) {
                        statusMessage.value = null
                    }
                }
            } catch (e: Exception) {
                statusMessage.value = "❌ 获取答案失败: ${e.message}"
                Log.e(TAG, "❌ 获取答案失败: ${e.message}")
                delay(5000)
                if (statusMessage.value?.startsWith("❌") == true) {
                    statusMessage.value = null
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI答题助手后台服务"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        try {
            unregisterReceiver(answerReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        floatingView?.let {
            windowManager.removeView(it)
        }

        screenCaptureManager?.releaseAll()
        serviceScope.cancel()
        _viewModelStore.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingWindowContent(
    answerText: String?,
    showAnswer: Boolean,
    statusMessage: String?,
    onCaptureClick: () -> Unit,
    onCloseAnswer: () -> Unit,
    onCloseStatus: () -> Unit,
    onMove: (Float, Float) -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.End
    ) {
        // 悬浮按钮
        FloatingActionButton(
            onClick = onCaptureClick,
            modifier = Modifier
                .size(37.dp)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        onMove(dragAmount.x, dragAmount.y)
                    }
                },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = LocalIcons.Search,
                contentDescription = MyApplication.getString(R.string.cd_capture_button),
                modifier = Modifier.size(21.dp)
            )
        }

        // 状态消息卡片
        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.width(200.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCloseStatus,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = LocalIcons.Close,
                            contentDescription = MyApplication.getString(R.string.cd_close_button),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 答案显示卡片
        if (showAnswer && answerText != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .width(300.dp)
                    .heightIn(max = 400.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = MyApplication.getString(R.string.floating_answer_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = onCloseAnswer,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = LocalIcons.Close,
                                contentDescription = MyApplication.getString(R.string.cd_close_button),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = answerText,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .weight(1f, fill = false)
                    )
                }
            }
        }
    }
}


