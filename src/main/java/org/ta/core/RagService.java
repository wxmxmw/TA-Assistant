package org.ta.core; // Assuming RagService is in this package

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

    // Regex to find citation markers like [cite: filename.pdf, 10-12]
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[cite:\\s*(.*?),\s*(.*?)\\]");
    private static final String FALLBACK_MESSAGE = "根据现有的课程材料，我无法回答这个问题。";

    private final KnowledgeStore knowledgeStore;
    private final LLMClient llmClient;

    /**
     * Constructs the RagService with its dependencies.
     * @param knowledgeStore The store for retrieving course materials.
     * @param llmClient The client for interacting with the Large Language Model.
     */
    public RagService(KnowledgeStore knowledgeStore, LLMClient llmClient) {
        this.knowledgeStore = knowledgeStore;
        this.llmClient = llmClient;
    }

    /**
     * The main entry point for the RAG pipeline.
     * @param question The user's question.
     * @param codeContext Optional code snippet selected by the user.
     * @param topK The number of relevant chunks to retrieve.
     * @return A structured Answer object.
     */
    public Answer answer(String question, Optional<String> codeContext, int topK) {
        // 1. RETRIEVE: Get relevant chunks from the knowledge store.
        List<RetrievedChunk> chunks = knowledgeStore.search(question, topK);

        // Optimization: If no relevant documents are found, return fallback immediately.
        if (chunks.isEmpty()) {
            return new Answer(FALLBACK_MESSAGE, Collections.emptyList(), true);
        }

        // 2. CONSTRUCT PROMPT: Build the prompt using the retrieved chunks.
        String prompt = buildPrompt(question, codeContext, chunks);

        // 3. GENERATE: Call the LLM to get a raw text response.
        String rawResponse;
        try {
            rawResponse = llmClient.generate(prompt);
        } catch (RuntimeException e) {
            // Handle API errors gracefully.
            System.err.println("API call failed: " + e.getMessage());
            e.printStackTrace();
            return new Answer("抱歉，调用AI服务时发生错误。", Collections.emptyList(), true);
        }

        // 4. POST-PROCESS: Parse the raw response into a structured Answer object.
        return parseLLMResponse(rawResponse);
    }

    /**
     * Builds the final prompt string to be sent to the LLM.
     */
    private String buildPrompt(String question, Optional<String> codeContext, List<RetrievedChunk> chunks) {
        String formattedChunks = formatChunks(chunks);
        String contextText = codeContext.orElse("无"); // Provide a default if code context is empty.

        return "你是一个Java企业应用开发课程的助教。请严格根据下面提供的[背景知识]来回答[用户问题]。\n" +
                "规则：\n" +
                "1. 你的回答必须结合[背景知识]中的信息。\n" +
                "2. 回答的结尾处，必须按照 \"[cite: 文件名, 页码范围]\" 的格式，清晰地列出所有你参考过的背景知识来源。\n" +
                "3. 如果[背景知识]与问题完全无关，或不足以回答，你必须回复：\"" + FALLBACK_MESSAGE + "\" 并且不要添加任何引用。\n" +
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

    /**
     * Formats a list of RetrievedChunk objects into a single string for the prompt.
     */
    private String formatChunks(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> String.format(
                        "引用来源: %s, 页码: %s\n内容:\n%s",
                        chunk.getSourceFile(), // Assuming RetrievedChunk has these methods
                        chunk.getPageRange(),
                        chunk.getContent()
                ))
                .collect(Collectors.joining("\n\n---\n\n")); // Separate chunks clearly
    }

    /**
     * Parses the LLM's raw string response to extract the clean text and citations.
     */
    private Answer parseLLMResponse(String rawResponse) {
        // First, check if the response is the exact fallback message.
        if (rawResponse.trim().equals(FALLBACK_MESSAGE)) {
            return new Answer(FALLBACK_MESSAGE, Collections.emptyList(), true);
        }

        List<Citation> citations = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(rawResponse);

        // Find all citation markers in the text.
        while (matcher.find()) {
            String sourceFile = matcher.group(1).trim();
            String pageRange = matcher.group(2).trim();
            citations.add(new Citation(sourceFile, pageRange));
        }

        // Remove the citation markers from the response to get the clean answer text.
        String cleanText = matcher.replaceAll("").trim();

        return new Answer(cleanText, citations, false);
    }
}