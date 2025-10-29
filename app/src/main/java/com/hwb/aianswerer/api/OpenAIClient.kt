package com.hwb.aianswerer.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.hwb.aianswerer.Constants
import com.hwb.aianswerer.MyApplication
import com.hwb.aianswerer.R
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.models.ChatMessage
import com.hwb.aianswerer.models.ChatRequest
import com.hwb.aianswerer.models.ChatResponse
import com.hwb.aianswerer.models.ResponseFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * OpenAI API客户端
 *
 * 负责与OpenAI格式的API进行通信
 * 支持动态配置，从AppConfig读取最新的API设置
 */
class OpenAIClient {

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 分析题目并获取答案
     *
     * 动态从AppConfig读取最新的API配置
     *
     * @param recognizedText OCR识别的文本
     * @param questionTypes 题型集合（如：单选题、多选题等）
     * @param questionScope 题目内容范围
     * @return AI解析的答案，包装在Result中
     */
    suspend fun analyzeQuestion(
        recognizedText: String,
        questionTypes: Set<String> = emptySet(),
        questionScope: String = ""
    ): Result<AIAnswer> = withContext(Dispatchers.IO) {
        try {
            // 从配置中读取最新的API设置
            val apiUrl = AppConfig.getApiUrl()
            val apiKey = AppConfig.getApiKey()
            val modelName = AppConfig.getModelName()

            // 验证配置有效性
            if (!AppConfig.isApiConfigValid()) {
                return@withContext Result.failure(
                    Exception("API配置无效，请在设置中配置API信息")
                )
            }

            // 构建请求，使用动态系统提示词
            val systemPrompt = Constants.buildSystemPrompt(questionTypes, questionScope)
            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = "请分析以下题目：\n\n$recognizedText")
            )

            val chatRequest = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = 0.3,  // 较低的温度以获得更确定的答案
                responseFormat = ResponseFormat(type = "json_object")
            )

            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // 发送请求
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("API请求失败: ${response.code} ${response.message}")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(
                    Exception(
                        MyApplication.getString(R.string.error_empty_response)
                    )
                )

            // 解析响应
            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
            val answerContent = chatResponse.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(
                    Exception(
                        MyApplication.getString(R.string.error_no_answer_content)
                    )
                )

            // 解析AI返回的JSON答案
            val aiAnswer = try {
                gson.fromJson(answerContent, AIAnswer::class.java)
            } catch (e: JsonSyntaxException) {
                // 如果解析失败，尝试提取文本作为答案
                AIAnswer(
                    question = MyApplication.getString(R.string.error_parse_question_failed),
                    questionType = MyApplication.getString(R.string.question_type_essay),
                    answer = answerContent
                )
            }

            Result.success(aiAnswer)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 测试API连接
     *
     * 发送简单的"hello"消息到配置的API端点，验证：
     * - API地址是否可达
     * - API密钥是否有效
     * - 模型名称是否正确
     *
     * @return Result<String> 成功返回"连接成功"，失败返回错误信息
     */
    suspend fun testConnection(): Result<String> {
        return testConnection(
            AppConfig.getApiUrl(),
            AppConfig.getApiKey(),
            AppConfig.getModelName()
        )
    }

    /**
     * 测试API连接，支持传入未保存的配置参数
     */
    suspend fun testConnection(
        apiUrl: String,
        apiKey: String,
        modelName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 验证配置有效性
            if (!AppConfig.isApiConfigValid(apiUrl, apiKey, modelName)) {
                return@withContext Result.failure(
                    Exception("API配置无效，请先完整配置API信息")
                )
            }

            // 构建最简单的测试请求
            val messages = listOf(
                ChatMessage(role = "user", content = "hello")
            )

            val chatRequest = ChatRequest(
                model = modelName,
                messages = messages,
            )

            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // 发送请求
            val response = client.newCall(request).execute()

            // 检查响应状态
            if (!response.isSuccessful) {
                val errorMessage = when (response.code) {
                    401 -> "API密钥无效或已过期"
                    403 -> "无权访问该API"
                    404 -> "API地址错误"
                    429 -> "请求过于频繁，请稍后再试"
                    500, 502, 503 -> "API服务器错误"
                    else -> "HTTP ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(Exception(errorMessage))
            }

            // 验证响应体存在
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                return@withContext Result.failure(Exception("API返回空响应"))
            }

            // 尝试解析响应以验证格式正确
            try {
                val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                if (chatResponse.choices.isEmpty()) {
                    return@withContext Result.failure(Exception("API响应格式异常"))
                }
            } catch (e: JsonSyntaxException) {
                return@withContext Result.failure(Exception("API响应格式错误"))
            }

            // 测试成功
            Result.success(MyApplication.getString(R.string.toast_connection_success))

        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Unable to connect to server, please check network and API URL"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Connection timeout, please check network"))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception("SSL connection error, please check API URL"))
        } catch (e: Exception) {
            val unknownError = MyApplication.getString(R.string.error_unknown)
            Result.failure(
                Exception(
                    MyApplication.getString(
                        R.string.error_connection_test_failed,
                        e.message ?: unknownError
                    )
                )
            )
        }
    }

    companion object {
        @Volatile
        private var instance: OpenAIClient? = null

        fun getInstance(): OpenAIClient {
            return instance ?: synchronized(this) {
                instance ?: OpenAIClient().also { instance = it }
            }
        }
    }
}

