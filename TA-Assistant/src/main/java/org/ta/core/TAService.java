// 这个文件现在可以改名为 TAService.java
package org.ta.core; // 建议把它移到 core 包，因为它现在是核心服务

import org.ta.api.*;
import org.ta.llm.SiliconLLMClient;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The main implementation of the TA service.
 * It's initialized once and holds the state, including the knowledge base and the current AI model.
 */
public class TAService implements ITAService {
    private static final Logger LOG = Logger.getLogger(TAService.class.getName());

    private final KnowledgeStore knowledgeStore;
    private final String apiKey;
    private AIModel currentModel; // 持有当前模型的状态

    /**
     * Constructor: Performs all expensive, one-time initializations.
     * @param apiKey The SiliconFlow API Key.
     */
    public TAService(String apiKey) {
        this.apiKey = apiKey;
        this.currentModel = AIModel.DEEPSEEK; // 设置一个默认模型

        LOG.info("Initializing TAService...");
        try {
            // 1. 初始化知识库 (只执行一次!)
            this.knowledgeStore = new HybridKnowledgeStore(apiKey);

            // 2. 加载并索引所有文档 (只执行一次!)
            Path docPath = Path.of("src/main/resources/docs/sample1.PDF");
            LOG.info("Loading documents into knowledge store...");
            this.knowledgeStore.addDocuments(List.of(docPath));
            LOG.info("Documents loaded successfully.");

        } catch (Exception e) {
            LOG.severe("Failed to initialize TAService: " + e.getMessage());
            e.printStackTrace();
            // 抛出运行时异常，因为服务无法在没有知识库的情况下工作
            throw new RuntimeException("Failed to initialize KnowledgeStore", e);
        }
    }

    @Override
    public void setModel(AIModel model) {
        LOG.info("Switching AI model to: " + model.name());
        this.currentModel = model;
    }

    @Override
    public Answer answer(String question, Optional<String> codeContext, int topK) {
        LOG.info("Answering question with model: " + currentModel.name());
        System.out.println("UserQuestion: " + question + " (using " + currentModel.name() + ")");

        // 1. 根据当前选择的模型创建 LLM 客户端
        // 这一步是轻量级的，可以在每次调用时执行
        SiliconLLMClient llmClient = new SiliconLLMClient(this.apiKey, this.currentModel.getModelName());

        // 2. 创建 RAG 服务
        // RagService 本身是无状态的，可以每次创建
        RagService ragService = new RagService(this.knowledgeStore, llmClient);

        // 3. 执行 RAG 流程并返回结果
        return ragService.answer(question, codeContext, topK);
    }

    //新增文档上传
    public void addDocument(Path docPath) throws IOException {
        LOG.info("Adding document: " + docPath.getFileName());
        this.knowledgeStore.addDocuments(List.of(docPath));
        LOG.info("Document added successfully: " + docPath.getFileName());
    }
}