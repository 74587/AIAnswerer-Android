package com.hwb.aianswerer

/**
 * 应用常量配置
 */
object Constants {
    // 通知渠道配置
    const val NOTIFICATION_CHANNEL_ID = "ai_answerer_service"
    const val NOTIFICATION_CHANNEL_NAME = "AI答题助手服务"
    const val NOTIFICATION_ID = 1001

    // Intent Actions
    const val ACTION_SHOW_ANSWER = "com.hwb.aianswerer.SHOW_ANSWER"
    const val ACTION_REQUEST_ANSWER = "com.hwb.aianswerer.REQUEST_ANSWER"
    const val EXTRA_ANSWER_TEXT = "answer_text"
    const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
    const val EXTRA_QUESTION_TEXT = "question_text"

    // 基础系统提示词
    private const val BASE_SYSTEM_PROMPT = """你是一个专业的答题助手。请分析用户提供的题目，并按照以下格式返回JSON：

{
  "question": "提取的完整题目",
  "questionType": "选择题/问答题/填空题",
  "answer": "答案内容",
  "options": ["选项A", "选项B", "选项C", "选项D"] // 仅选择题需要
}

要求：
1. 准确提取题目和选项
2. 正确判断题目类型（单选题、多选题、不定项选择题都归类为"选择题"）
3. 对于选择题：
   - 单选题：给出正确选项（如"A"或"A. xxx"）
   - 多选题/不定项：给出所有正确选项（如"ABC"或"A、B、C"）
4. 对于问答题和填空题，给出简洁准确的答案
5. 必须返回有效的JSON格式"""

    /**
     * 根据设置构建系统提示词
     *
     * @param questionTypes 题型集合（如：单选题、多选题等）
     * @param questionScope 题目内容范围
     * @return 优化后的系统提示词
     */
    fun buildSystemPrompt(questionTypes: Set<String>, questionScope: String): String {
        val promptBuilder = StringBuilder(BASE_SYSTEM_PROMPT)

        // 添加题型限制
        if (questionTypes.isNotEmpty()) {
            promptBuilder.append("\n\n【题型范围】")
            promptBuilder.append("\n重点关注以下题型：")
            promptBuilder.append(questionTypes.joinToString("、"))
        }

        // 添加题目内容范围限制
        if (questionScope.isNotBlank()) {
            promptBuilder.append("\n\n【题目范围】")
            promptBuilder.append("\n题目范围限定在：")
            promptBuilder.append(questionScope)
            promptBuilder.append("\n请优先考虑该范围内的知识点进行答题。")
        }

        return promptBuilder.toString()
    }
}

