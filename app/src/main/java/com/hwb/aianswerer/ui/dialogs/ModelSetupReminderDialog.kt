package com.hwb.aianswerer.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hwb.aianswerer.R

/**
 * 使用方法：在Compose中调用 ModelSetupReminderDialog()
 * 功能：提醒用户设置AI模型配置，引导到设置页面
 */
@Composable
fun ModelSetupReminderDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_model_setup_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_model_setup_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onGoToSettings()
                }
            ) {
                Text(stringResource(R.string.dialog_model_setup_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.dialog_model_setup_cancel))
            }
        }
    )
}