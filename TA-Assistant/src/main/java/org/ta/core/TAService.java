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


    //F2功能实现处

    @Override
    public String modifyCode(String instruction, String codeToModify) {
        LOG.info("Performing code modification with GLM model.");

        // 1. 构造专门用于代码修改的 Prompt
        String prompt = buildCodeModificationPrompt(instruction, codeToModify);

        // 2. 创建一个使用 GLM 模型的 LLM 客户端
        SiliconLLMClient glmClient = new SiliconLLMClient(this.apiKey, AIModel.GLM.getModelName());

        // 3. 调用 LLM 并返回结果
        try {
            String modifiedCode = glmClient.generate(prompt);
            // AI有时会返回被 ```java ... ``` 包裹的代码块，我们需要清理它
            return cleanCodeBlock(modifiedCode);
        } catch (Exception e) {
            LOG.severe("Failed to modify code with AI: " + e.getMessage());
            // 返回原始代码或抛出异常，让上层处理
            return "// AI modification failed: " + e.getMessage() + "\n" + codeToModify;
        }
    }

    private String buildCodeModificationPrompt(String instruction, String code) {
        return "You are a senior Java architect and refactoring expert.\n" +
                "Your task is to modify the given Java code snippet according to the user's instructions.\n" +
                "Rules:\n" +
                "1. Strictly follow the user's instructions for modification.\n" +
                "2. Do not add any explanations, comments, or apologies unrelated to the code. Your response must be purely, directly compilable Java code.\n" +
                "3. Maintain the indentation and formatting style of the original code.\n" +
                "4. If the instruction cannot be completed, return the original code.\n\n" +
                "\"[User Instruction]\"\n" +
                instruction + "\n\n" +
                "\"[Original Code]\"\n" +
                "```java\n" + code + "\n" + "```\n\n" +
                "\"[Modified Code]\"\n";
    }

    private String cleanCodeBlock(String response) {
        // 移除 Markdown 代码块标记
        if (response.startsWith("```java\n")) {
            response = response.substring("```java\n".length());
        }
        if (response.endsWith("\n```")) {
            response = response.substring(0, response.length() - "\n```".length());
        }
        return response.trim();
    }

    //新增文档上传
    public void addDocument(Path docPath) throws IOException {
        LOG.info("Adding document: " + docPath.getFileName());
        this.knowledgeStore.addDocuments(List.of(docPath));
        LOG.info("Document added successfully: " + docPath.getFileName());

    }
}