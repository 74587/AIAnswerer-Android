package com.hwb.aianswerer.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 剪贴板工具类
 */
object ClipboardUtil {

    /**
     * 复制文本到剪贴板
     */
    fun copyToClipboard(context: Context, text: String, label: String = "AI答案"): Boolean {
        return try {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboardManager?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从剪贴板获取文本
     */
    fun getFromClipboard(context: Context): String? {
        return try {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

