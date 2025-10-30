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


    /**
     * 根据设置构建系统提示词
     *
     * @param questionTypes 题型集合（如：单选题、多选题等）
     * @param questionScope 题目内容范围
     * @return 优化后的系统提示词
     */
    fun buildSystemPrompt(questionTypes: Set<String>, questionScope: String): String {
        val basePrompt = getBaseSystemPrompt()
        if (questionTypes.isEmpty() && questionScope.isBlank()) {
            return basePrompt
        }

        val promptBuilder = StringBuilder(basePrompt)
        promptBuilder.append("\n\n")
        promptBuilder.append(MyApplication.getString(R.string.system_prompt_limit_header))
        promptBuilder.append('\n')

        val typeSeparator = MyApplication.getString(R.string.system_prompt_type_separator)
        val essayType = MyApplication.getString(R.string.ai_question_type_essay)
        var hasConstraint = false

        // 添加题型限制
        if (questionTypes.isNotEmpty()) {
            promptBuilder.append(
                MyApplication.getString(
                    R.string.system_prompt_type_template,
                    questionTypes.joinToString(typeSeparator),
                    essayType
                )
            )
            hasConstraint = true
        }

        // 添加题目内容范围限制
        if (questionScope.isNotBlank()) {
            if (hasConstraint) {
                promptBuilder.append('\n')
            }
            promptBuilder.append(
                MyApplication.getString(R.string.system_prompt_scope_template, questionScope)
            )
        }
        return promptBuilder.toString()
    }

    private fun getBaseSystemPrompt(): String {
        val choiceType = MyApplication.getString(R.string.ai_question_type_choice)
        val essayType = MyApplication.getString(R.string.ai_question_type_essay)
        val blankType = MyApplication.getString(R.string.ai_question_type_blank)
        return MyApplication.getString(
            R.string.system_prompt_base,
            choiceType,
            essayType,
            blankType
        )
    }
}

