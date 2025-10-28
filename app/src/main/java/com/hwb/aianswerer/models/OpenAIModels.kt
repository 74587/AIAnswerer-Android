package com.hwb.aianswerer.models

import com.google.gson.annotations.SerializedName

/**
 * OpenAI API请求模型
 */
data class ChatRequest(
    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<ChatMessage>,

    @SerializedName("temperature")
    val temperature: Double = 0.7,

    @SerializedName("max_tokens")
    val maxTokens: Int? = null,

    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = null
)

/**
 * 聊天消息
 */
data class ChatMessage(
    @SerializedName("role")
    val role: String,  // "system", "user", "assistant"

    @SerializedName("content")
    val content: String
)

/**
 * 响应格式配置
 */
data class ResponseFormat(
    @SerializedName("type")
    val type: String = "json_object"
)

/**
 * OpenAI API响应模型
 */
data class ChatResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val objectType: String,

    @SerializedName("created")
    val created: Long,

    @SerializedName("model")
    val model: String,

    @SerializedName("choices")
    val choices: List<Choice>,

    @SerializedName("usage")
    val usage: Usage? = null
)

/**
 * 选择项
 */
data class Choice(
    @SerializedName("index")
    val index: Int,

    @SerializedName("message")
    val message: ChatMessage,

    @SerializedName("finish_reason")
    val finishReason: String
)

/**
 * Token使用情况
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,

    @SerializedName("completion_tokens")
    val completionTokens: Int,

    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * AI答案解析结果
 */
data class AIAnswer(
    val question: String,
    val questionType: String,  // 选择题/问答题/填空题
    val answer: String,
    val options: List<String>? = null
) {
    /**
     * 格式化显示答案
     */
    fun formatAnswer(): String {
        return buildString {
            appendLine("【题目】")
            appendLine(question)
            appendLine()

            if (!options.isNullOrEmpty()) {
                appendLine("【选项】")
                options.forEach { appendLine(it) }
                appendLine()
            }

            appendLine("【答案】")
            append(answer)
        }
    }
}

