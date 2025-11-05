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
        return "你是一名资深的Java架构师和重构专家。\n" +
                "你的任务是根据用户的指令，修改给定的Java代码片段。\n" +
                "规则：\n" +
                "1. 严格遵循用户的指令进行修改。\n" +
                "2. 不要添加任何与代码无关的解释、注释或道歉。你的回答必须是纯粹的、可直接编译的Java代码。\n" +
                "3. 保持原始代码的缩进和格式风格。\n" +
                "4. 如果无法完成指令，请返回原始代码。\n" +
                "5. 不要用Markdown代码块包裹代码，直接输出代码即可。\n\n" +
                "[用户指令]\n" +
                instruction + "\n\n" +
                "[原始代码]\n" +
                code + "\n\n" +
                "[修改后的代码]\n";
    }

    private static String cleanCodeBlock(String response) {
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