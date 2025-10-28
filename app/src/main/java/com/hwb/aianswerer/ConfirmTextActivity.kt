package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.utils.LanguageUtil


/**
 * 透明确认Activity
 * 显示OCR识别的文本，让用户确认后调用AI获取答案
 */
class ConfirmTextActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ConfirmTextActivity"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val recognizedText = intent.getStringExtra(Constants.EXTRA_RECOGNIZED_TEXT) ?: ""

        setContent {
            MaterialTheme {
                ConfirmTextScreen(
                    recognizedText = recognizedText,
                    onConfirm = { editedText ->
                        handleConfirm(editedText)
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun handleConfirm(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_text_empty), Toast.LENGTH_SHORT).show()
            return
        }

        // 显示提示
        Toast.makeText(this, getString(R.string.toast_getting_answer), Toast.LENGTH_SHORT).show()

        // 发送请求答案的广播到FloatingWindowService
        val intent = Intent(Constants.ACTION_REQUEST_ANSWER).apply {
            setPackage(packageName)  // 指定应用包名，确保广播能被接收（Android 8.0+要求）
            putExtra(Constants.EXTRA_QUESTION_TEXT, text)
        }
        Log.d(
            TAG,
            "发送请求答案广播: Action=${intent.action}, Package=${intent.`package`}, 文本长度=${text.length}"
        )
        sendBroadcast(intent)
        Log.d(TAG, "广播已发送，即将关闭Activity")

        // 立即关闭Activity
        finish()
    }
}

@Composable
fun ConfirmTextScreen(
    recognizedText: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(recognizedText) }

    // 获取当前设置信息
    val questionTypes = AppConfig.getQuestionTypes()
    val questionScope = AppConfig.getQuestionScope()
    val settingsText = buildString {
        append(questionTypes.joinToString("、"))
        if (questionScope.isNotBlank()) {
            append(" | ")
            append(questionScope)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题
                Text(
                    text = MyApplication.getString(R.string.confirm_text_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 显示当前设置
                Text(
                    text = MyApplication.getString(R.string.current_settings, settingsText),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 文本输入框
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = { Text(MyApplication.getString(R.string.confirm_text_label)) },
                    placeholder = { Text(MyApplication.getString(R.string.confirm_text_placeholder)) },
                    maxLines = Int.MAX_VALUE,
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 取消按钮
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(MyApplication.getString(R.string.button_cancel))
                    }

                    // 确认按钮
                    Button(
                        onClick = { onConfirm(text) },
                        modifier = Modifier.weight(1f),
                        enabled = text.isNotBlank()
                    ) {
                        Text(MyApplication.getString(R.string.button_confirm_and_answer))
                    }
                }
            }
        }
    }
}

