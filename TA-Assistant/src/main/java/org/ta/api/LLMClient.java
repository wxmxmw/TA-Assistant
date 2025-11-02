package org.ta.api;

/**
 * Abstraction for LLM provider client. B will implement with real APIs later.
 */

public interface LLMClient {
    /**
     * 向大语言模型发送一个提示（prompt），并获取生成的文本回复。
     * @param prompt 构造好的完整提示词
     * @return LLM 生成的回复字符串
     */
    String generate(String prompt);
}
