package com.hwb.aianswerer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.R
import com.hwb.aianswerer.config.AppConfig

/**
 * 使用方法：在Compose中调用 LanguageSelectionDialog()
 * 功能：首次启动时提供语言选择功能，选择后重启Activity生效
 */
@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit,
    onLanguageConfirmed: () -> Unit
) {
    LocalContext.current
    val currentLanguage = AppConfig.getLanguage()
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_language_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_language_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 中文选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLanguage = AppConfig.LANGUAGE_ZH }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedLanguage == AppConfig.LANGUAGE_ZH,
                        onClick = { selectedLanguage = AppConfig.LANGUAGE_ZH }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "中文",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 英文选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLanguage = AppConfig.LANGUAGE_EN }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedLanguage == AppConfig.LANGUAGE_EN,
                        onClick = { selectedLanguage = AppConfig.LANGUAGE_EN }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "English",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 保存语言设置
                    AppConfig.saveLanguage(selectedLanguage)
                    AppConfig.setFirstLaunchComplete()

                    // 触发确认回调
                    onLanguageConfirmed()
                }
            ) {
                Text(stringResource(R.string.dialog_language_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    // 设置默认语言并标记首次启动完成
                    AppConfig.saveLanguage(AppConfig.LANGUAGE_ZH)
                    AppConfig.setFirstLaunchComplete()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.dialog_language_cancel))
            }
        }
    )
}