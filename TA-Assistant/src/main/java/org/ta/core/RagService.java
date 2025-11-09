package org.ta.core;

import org.ta.api.*;
import org.ta.llm.SiliconLLMClient; // Your LLM client implementation

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RagService {

    // 正则表达式，用于匹配 [cite: 文件名, 页码] 格式的引用
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[cite:\\s*(.*?),\s*(.*?)\\]");

    // LLM 回复的特殊标记，表示它使用了通用知识
    private static final String GENERAL_KNOWLEDGE_MARKER = "[GENERAL_KNOWLEDGE]";

    // 当回答基于通用知识时，附加的免责声明
    private static final String GENERAL_KNOWLEDGE_DISCLAIMER = "（回答基于通用知识；未引用特定课程材料）";

    private final KnowledgeStore knowledgeStore;
    private final LLMClient llmClient;

    public RagService(KnowledgeStore knowledgeStore, LLMClient llmClient) {
        this.knowledgeStore = knowledgeStore;
        this.llmClient = llmClient;
    }

    /**
     * RAG 管线的核心方法，现在支持三种情况的处理。
     */
    public Answer answer(String question, Optional<String> codeContext, int topK) {
        // 步骤 1: 检索。无论是否找到，流程都将继续。
        List<RetrievedChunk> chunks = knowledgeStore.search(question, topK);

        // 步骤 2: 构造 Prompt。现在这个方法会处理 chunks 为空的情况。
        String prompt = buildPrompt(question, codeContext, chunks);

        // 步骤 3: 调用 LLM 生成回复。
        String rawResponse;
        try {
            rawResponse = llmClient.generate(prompt);
        } catch (RuntimeException e) {
            System.err.println("API 调用失败: " + e.getMessage());
            e.printStackTrace();
            return new Answer("抱歉，调用AI服务时发生错误。", Collections.emptyList(), true);
        }

        // 步骤 4: 解析 LLM 的回复。这是逻辑的核心。
        return parseLLMResponse(rawResponse);
    }

    /**
     * 构造发送给 LLM 的最终 Prompt。
     * 新增了规则，要求 LLM 在未使用背景知识时，添加特殊标记。
     */
    private String buildPrompt(String question, Optional<String> codeContext, List<RetrievedChunk> chunks) {
        String formattedChunks = chunks.isEmpty() ? "无" : formatChunks(chunks);
        String contextText = codeContext.orElse("无");

        return "You are a teaching assistant for the Java Enterprise Application Development course. Please strictly follow the \"[Background Knowledge]\" provided below to answer the \"[User Question]\".\n" +
                "Rules:\n" +
                "1. If the \"[Background Knowledge]\" is helpful for answering the question, your answer must incorporate information from it.\n" +
                "2. If you use information from the \"[Background Knowledge]\" you must clearly list all cited sources at the end of your answer in the format \"[cite: file_name, page_range]\".\n" +
                "3. If the \"[Background Knowledge]\" is completely irrelevant to the question or is empty, please use your own general knowledge to answer. However, your answer must start with the special marker \"[GENERAL_KNOWLEDGE]\" and must not contain any cite reference markers.\n" +
                "4. If the user provides \"[Code Context]\", please analyze and answer in conjunction with the code.\n" +
                "\n" +
                "\"[Background Knowledge]\"\n" +
                "---\n" +
                formattedChunks + "\n" +
                "---\n" +
                "\n" +
                "\"[Code Context]\"\n" +
                "---\n" +
                contextText + "\n" +
                "---\n" +
                "\n" +
                "\"[User Question]\"\n" +
                question;
    }

    private String formatChunks(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> String.format(
                        "引用来源: %s, 页码: %s\n内容:\n%s",
                        chunk.getSourceFile(),
                        chunk.getPageRange(),
                        chunk.getContent()
                ))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 解析 LLM 的原始回复，根据规则生成结构化的 Answer 对象。
     */
    private Answer parseLLMResponse(String rawResponse) {
        String cleanText = rawResponse.trim();
        List<Citation> citations = new ArrayList<>();

        // 情况 1 & 3: LLM 明确告知使用了通用知识
        if (cleanText.startsWith(GENERAL_KNOWLEDGE_MARKER)) {
            // 移除标记，并附加免责声明
            cleanText = cleanText.substring(GENERAL_KNOWLEDGE_MARKER.length()).trim();
            cleanText += "\n\n" + GENERAL_KNOWLEDGE_DISCLAIMER;
            return new Answer(cleanText, Collections.emptyList(), true);
        }

        // 情况 2: LLM 回复中包含引用标记
        Matcher matcher = CITATION_PATTERN.matcher(cleanText);
        boolean foundCitations = false;
        while (matcher.find()) {
            foundCitations = true;
            String sourceFile = matcher.group(1).trim();
            String pageRange = matcher.group(2).trim();
            citations.add(new Citation(sourceFile, pageRange));
        }

        if (foundCitations) {
            // 移除所有引用标记，得到纯净的回答文本
            cleanText = matcher.replaceAll("").trim();
            return new Answer(cleanText, citations, false);
        }

        // 容错情况：LLM 可能忘记添加 [GENERAL_KNOWLEDGE] 标记，但也没有提供引用。
        // 我们默认这种情况也属于通用知识回答。
        cleanText += "\n\n" + GENERAL_KNOWLEDGE_DISCLAIMER;
        return new Answer(cleanText, Collections.emptyList(), true);
    }
}