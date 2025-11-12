package org.ta.core;

import org.ta.api.ITAService.AIModel;
import org.ta.llm.SiliconLLMClient;

import java.util.logging.Logger;
import java.util.function.Consumer;

/**
 * 轻量级的 AI 代码修改工具类
 * 🆕 现在支持传入模型参数
 */
public final class AIModificationUtil {

    private static final Logger LOG = Logger.getLogger(AIModificationUtil.class.getName());

    private AIModificationUtil() {}

    /**
     * 阻塞式调用 AI 修改代码
     * @param instruction 用户指令
     * @param codeToModify 待修改的代码
     * @param apiKey API 密钥
     * @param model 🆕 使用的模型
     * @return AI 生成的新代码
     */
    public static String modifyCode(String instruction, String codeToModify, String apiKey, AIModel model) {
        LOG.info("Performing code modification with model: " + model.name());

        String prompt = buildCodeModificationPrompt(instruction, codeToModify);
        SiliconLLMClient client = new SiliconLLMClient(apiKey, model.getModelName());

        try {
            String modifiedCode = client.generate(prompt);
            return cleanCodeBlock(modifiedCode);
        } catch (Exception e) {
            LOG.severe("Failed to modify code with AI: " + e.getMessage());
            return "// AI modification failed: " + e.getMessage() + "\n" + codeToModify;
        }
    }

    /**
     * 🔥 流式调用 AI 修改代码
     * @param instruction 用户指令
     * @param codeToModify 待修改的代码
     * @param apiKey API 密钥
     * @param model 🆕 使用的模型
     * @param onChunk 每收到一个文本块时的回调
     * @param onComplete 完成时的回调
     * @param onError 错误时的回调
     */
    public static void modifyCodeStream(String instruction, String codeToModify, String apiKey, AIModel model,
                                        Consumer<String> onChunk, Runnable onComplete, Consumer<Exception> onError) {
        LOG.info("Performing streaming code modification with model: " + model.name());

        String prompt = buildCodeModificationPrompt(instruction, codeToModify);
        SiliconLLMClient client = new SiliconLLMClient(apiKey, model.getModelName());

        // 包装 onChunk，自动清理 Markdown 代码块标记
        Consumer<String> cleanedOnChunk = (chunk) -> {
            // 清理第一个块中的 ```java\n
            String cleaned = chunk.replace("```java\n", "").replace("```java", "");
            if (!cleaned.isEmpty()) {
                onChunk.accept(cleaned);
            }
        };

        // 包装 onComplete，清理最后的 ```
        Runnable cleanedOnComplete = () -> {
            // 如果需要在完成时做额外清理，可以在这里处理
            onComplete.run();
        };

        client.generateStream(prompt, cleanedOnChunk, cleanedOnComplete, onError);
    }

    /**
     * 🆕 兼容旧代码：默认使用 GLM 模型
     */
    @Deprecated
    public static String modifyCode(String instruction, String codeToModify, String apiKey) {
        return modifyCode(instruction, codeToModify, apiKey, AIModel.GLM);
    }

    /**
     * 🆕 兼容旧代码：默认使用 GLM 模型
     */
    @Deprecated
    public static void modifyCodeStream(String instruction, String codeToModify, String apiKey,
                                        Consumer<String> onChunk, Runnable onComplete, Consumer<Exception> onError) {
        modifyCodeStream(instruction, codeToModify, apiKey, AIModel.GLM, onChunk, onComplete, onError);
    }

    // ========== 私有辅助方法 ==========

    private static String buildCodeModificationPrompt(String instruction, String code) {
        return "You are a senior Java architect and refactoring expert.\n" +
                "Your task is to modify the given Java code " +
                " according to the user's instructions.\n" +
                "Rules:\n" +
                "1. Strictly follow the user's instructions for modification.\n" +
                "2. Do not add any explanations, comments, or apologies unrelated to the code. " +
                "Your response must be purely, directly compilable Java code.\n" +
                "3. Maintain the indentation and formatting style of the original code.\n" +
                "4. If the instruction cannot be completed, return the original code.\n\n" +
                "\"[User Instruction]\"\n" +
                instruction + "\n\n" +
                "\"[Original Code]\"\n" +
                "```java\n" + code + "\n" + "```\n\n" +
                "\"[Modified Code]\"\n";
    }

    public static String cleanCodeBlock(String response) {
        // 移除 Markdown 代码块标记
        response = response.trim();

        if (response.startsWith("```java")) {
            int firstNewline = response.indexOf('\n');
            if (firstNewline != -1) {
                response = response.substring(firstNewline + 1);
            }
        }

        if (response.endsWith("```")) {
            response = response.substring(0, response.lastIndexOf("```"));
        }

        return response.trim();
    }
}