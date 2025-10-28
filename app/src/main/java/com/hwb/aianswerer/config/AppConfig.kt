package com.hwb.aianswerer.config

import android.content.Context
import com.hwb.aianswerer.BuildConfig
import com.tencent.mmkv.MMKV

/**
 * 应用配置管理类
 * 负责保存和读取用户的API配置、语言设置等
 * 使用MMKV作为底层存储，提供高性能的key-value数据持久化
 */
object AppConfig {

    // MMKV存储键名
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL_NAME = "model_name"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_AUTO_SUBMIT = "auto_submit"
    private const val KEY_AUTO_COPY = "auto_copy"
    private const val KEY_QUESTION_TYPES = "question_types"
    private const val KEY_QUESTION_SCOPE = "question_scope"
    private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"

    // 语言代码常量
    const val LANGUAGE_ZH = "zh"
    const val LANGUAGE_EN = "en"

    private lateinit var mmkv: MMKV

    /**
     * 初始化MMKV
     * 应该在Application.onCreate()中调用
     */
    fun init(context: Context) {
        MMKV.initialize(context)
        mmkv = MMKV.defaultMMKV()
    }

    // ========== API配置相关 ==========

    /**
     * 保存API URL
     */
    fun saveApiUrl(url: String) {
        mmkv.encode(KEY_API_URL, url)
    }

    /**
     * 获取API URL
     * @return API URL，优先返回BuildConfig配置，其次返回用户设置值，最后返回默认值
     */
    fun getApiUrl(): String {
        return mmkv.decodeString(KEY_API_URL, BuildConfig.API_URL) ?: ""
    }

    /**
     * 保存API Key
     */
    fun saveApiKey(key: String) {
        mmkv.encode(KEY_API_KEY, key)
    }

    /**
     * 获取API Key
     * @return API Key，优先返回BuildConfig配置，其次返回用户设置值，最后返回空值
     */
    fun getApiKey(): String {
        return mmkv.decodeString(KEY_API_KEY, BuildConfig.API_KEY) ?: ""
    }

    /**
     * 保存模型名称
     */
    fun saveModelName(model: String) {
        mmkv.encode(KEY_MODEL_NAME, model)
    }

    /**
     * 获取模型名称
     * @return 模型名称，优先返回BuildConfig配置，其次返回用户设置值，最后返回默认值
     */
    fun getModelName(): String {
        return mmkv.decodeString(KEY_MODEL_NAME, BuildConfig.API_MODEL) ?: ""
    }

    /**
     * 验证API配置是否完整
     * @return true表示配置完整，false表示缺少必要配置
     */
    fun isApiConfigValid(): Boolean {
        val url = getApiUrl()
        val key = getApiKey()
        val model = getModelName()

        return url.isNotBlank() && key.isNotBlank() && model.isNotBlank() && url.startsWith("http")
    }

    // ========== 语言设置相关 ==========

    /**
     * 保存语言设置
     * @param languageCode 语言代码 (zh/en)
     */
    fun saveLanguage(languageCode: String) {
        mmkv.encode(KEY_LANGUAGE, languageCode)
    }

    /**
     * 获取当前设置的语言
     * @return 语言代码，默认为中文
     */
    fun getLanguage(): String {
        return mmkv.decodeString(KEY_LANGUAGE, LANGUAGE_ZH) ?: LANGUAGE_ZH
    }

    // ========== 应用设置相关 ==========

    /**
     * 保存自动提交设置
     * @param enabled 是否启用自动提交（识别后直接获取答案，不显示确认对话框）
     */
    fun saveAutoSubmit(enabled: Boolean) {
        mmkv.encode(KEY_AUTO_SUBMIT, enabled)
    }

    /**
     * 获取自动提交设置
     * @return 是否启用自动提交，默认为false
     */
    fun getAutoSubmit(): Boolean {
        return mmkv.decodeBool(KEY_AUTO_SUBMIT, false)
    }

    /**
     * 保存自动复制到剪贴板设置
     * @param enabled 是否启用自动复制（生成答案后自动复制到剪贴板）
     */
    fun saveAutoCopy(enabled: Boolean) {
        mmkv.encode(KEY_AUTO_COPY, enabled)
    }

    /**
     * 获取自动复制到剪贴板设置
     * @return 是否启用自动复制，默认为true（提升用户体验）
     */
    fun getAutoCopy(): Boolean {
        return mmkv.decodeBool(KEY_AUTO_COPY, true)
    }

    // ========== 答题设置相关 ==========

    /**
     * 保存题型设置
     * @param types 题型集合（如：单选题、多选题等）
     */
    fun saveQuestionTypes(types: Set<String>) {
        val typesString = types.joinToString(",")
        mmkv.encode(KEY_QUESTION_TYPES, typesString)
    }

    /**
     * 获取题型设置
     * @return 题型集合，默认为单选题
     */
    fun getQuestionTypes(): Set<String> {
        val typesString = mmkv.decodeString(KEY_QUESTION_TYPES, "单选题") ?: "单选题"
        return if (typesString.isBlank()) {
            setOf("单选题")
        } else {
            typesString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    /**
     * 保存题目内容范围
     * @param scope 题目内容范围描述
     */
    fun saveQuestionScope(scope: String) {
        mmkv.encode(KEY_QUESTION_SCOPE, scope)
    }

    /**
     * 获取题目内容范围
     * @return 题目内容范围，默认为空字符串（不限制）
     */
    fun getQuestionScope(): String {
        return mmkv.decodeString(KEY_QUESTION_SCOPE, "") ?: ""
    }

    // ========== 首次启动相关 ==========

    /**
     * 检查是否为首次启动
     * @return true表示首次启动，false表示已启动过
     */
    fun isFirstLaunch(): Boolean {
        return mmkv.decodeBool(KEY_IS_FIRST_LAUNCH, true)
    }

    /**
     * 标记首次启动完成
     */
    fun setFirstLaunchComplete() {
        mmkv.encode(KEY_IS_FIRST_LAUNCH, false)
    }
}

