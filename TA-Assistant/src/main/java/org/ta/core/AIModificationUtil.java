package org.ta.core; // 或者 org.ta.core

import org.ta.api.ITAService.AIModel; // 确保可以访问到这个枚举
import org.ta.llm.SiliconLLMClient;

import java.util.logging.Logger;
import java.util.function.Consumer;
/**
 * 一个轻量级的工具类，专门用于执行AI代码修改。
 * 它不包含任何与知识库(KnowledgeStore)相关的重量级初始化。
 */
public final class AIModificationUtil {

    private static final Logger LOG = Logger.getLogger(AIModificationUtil.class.getName());

    // 私有构造函数，防止被实例化
    private AIModificationUtil() {}

    /**
     * 调用AI修改代码的静态方法。
     * @param instruction 用户的指令
     * @param codeToModify 待修改的代码
     * @param apiKey 你的API密钥
     * @return AI生成的新代码
     */
    public static String modifyCode(String instruction, String codeToModify, String apiKey) {
        LOG.info("Performing code modification with GLM model via lightweight utility.");

        // 1. 构造专门用于代码修改的 Prompt
        String prompt = buildCodeModificationPrompt(instruction, codeToModify);

        // 2. 创建一个使用 GLM 模型的 LLM 客户端
        SiliconLLMClient glmClient = new SiliconLLMClient(apiKey, AIModel.GLM.getModelName());

        // 3. 调用 LLM 并返回结果
        try {
            String modifiedCode = glmClient.generate(prompt);
            // 清理AI可能返回的Markdown代码块标记
            return cleanCodeBlock(modifiedCode);
        } catch (Exception e) {
            LOG.severe("Failed to modify code with AI: " + e.getMessage());
            // 返回包含错误信息的代码，以便在Diff视图中显示
            return "// AI modification failed: " + e.getMessage() + "\n" + codeToModify;
        }
    }

    private static String buildCodeModificationPrompt(String instruction, String code) {
        return "你是一名资深的Java架构师和重构专家。\n" +
                "你的任务是根据用户的指令，修改给定的Java代码片段。\n" +
                "规则：\n" +
                "1. 严格遵循用户的指令进行修改。\n" +
                "2. 不要添加任何与代码无关的解释、注释或道歉。你的回答必须是纯粹的、可直接编译的Java代码。\n" +
                "3. 保持原始代码的缩进和格式风格。\n" +
                "4. 如果无法完成指令，请返回原始代码。\n\n" +
                "[用户指令]\n" +
                instruction + "\n\n" +
                "[原始代码]\n" +
                "```java\n" +
                code + "\n" +
                "```\n\n" +
                "[修改后的代码]\n";
    }

    private static String cleanCodeBlock(String response) {
        if (response.startsWith("```java\n")) {
            response = response.substring("```java\n".length());
        }
        if (response.endsWith("\n```")) {
            response = response.substring(0, response.length() - "\n```".length());
        }
        return response.trim();
    }
    /**
     * 新增：以流式方式调用AI修改代码
     */
    public static void modifyCodeStream(String instruction, String codeToModify, String apiKey,
                                        Consumer<String> onChunk, Runnable onComplete, Consumer<Exception> onError) {
        LOG.info("Performing code modification with GLM model via streaming.");
        String prompt = buildCodeModificationPrompt(instruction, codeToModify);

        // 固定使用 GLM 模型
        SiliconLLMClient glmClient = new SiliconLLMClient(apiKey, AIModel.GLM.getModelName());

        // 直接透传回调函数
        glmClient.generateStream(prompt, onChunk, onComplete, onError);
    }
}