package com.agent.gateway.runtime.chat;

import java.util.regex.Pattern;

/**
 * 清洗模型误把工具调用写成文本的情况（如 {@code <tool_call>...</tool_call>}）。
 * <p>所属模块：runtime。SSE answer 不应泄漏原始工具标签。
 */
final class AnswerMarkupSanitizer {

    private static final Pattern TOOL_CALL_BLOCK = Pattern.compile(
            "<tool_call>\\s*[\\s\\S]*?\\s*</tool_call>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCOMPLETE_TOOL_CALL = Pattern.compile(
            "<tool_call>[\\s\\S]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTIAL_OPEN = Pattern.compile(
            "<(?:t(?:o(?:o(?:l(?:_(?:c(?:a(?:l(?:l(?:\\s*)?)?)?)?)?)?)?)?)?)?$",
            Pattern.CASE_INSENSITIVE);

    private AnswerMarkupSanitizer() {
    }

    /**
     * 去掉完整 / 未闭合的 tool_call 标签，避免前端气泡出现原始 XML。
     *
     * @param text 原始 answer / thinking 文本
     * @return 清洗后文本；全是标签时可能为空串
     */
    static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String out = TOOL_CALL_BLOCK.matcher(text).replaceAll("");
        out = INCOMPLETE_TOOL_CALL.matcher(out).replaceAll("");
        out = PARTIAL_OPEN.matcher(out).replaceAll("");
        return out;
    }
}
