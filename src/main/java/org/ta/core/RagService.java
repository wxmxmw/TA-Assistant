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

        return "你是一个Java企业应用开发课程的助教。请严格根据下面提供的[背景知识]来回答[用户问题]。\n" +
                "规则：\n" +
                "1. 如果[背景知识]对回答问题有帮助，你的回答必须结合其中的信息。\n" +
                "2. 如果你使用了[背景知识]中的信息，必须在回答的结尾处，按照 \"[cite: 文件名, 页码范围]\" 的格式，清晰地列出所有引用来源。\n" +
                "3. 如果[背景知识]与问题完全无关，或内容为空，请使用你自己的通用知识来回答问题。但是，你的回答必须以特殊标记 \"[GENERAL_KNOWLEDGE]\" 开头，并且不能包含任何 cite 引用标记。\n" +
                "4. 如果用户提供了[代码上下文]，请结合代码进行分析和回答。\n" +
                "\n" +
                "[背景知识]\n" +
                "---\n" +
                formattedChunks + "\n" +
                "---\n" +
                "\n" +
                "[代码上下文]\n" +
                "---\n" +
                contextText + "\n" +
                "---\n" +
                "\n" +
                "[用户问题]\n" +
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