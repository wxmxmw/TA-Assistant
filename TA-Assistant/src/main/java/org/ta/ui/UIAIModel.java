package org.ta.ui;

import org.ta.api.ITAService;

/**
 * UI 层使用的模型配置
 */
public enum UIAIModel {
    DEEPSEEK("DeepSeek V3.2", "推理能力强，适合复杂代码重构", ITAService.AIModel.DEEPSEEK),
    QWEN("通义千问 3-VL", "多模态支持，视觉理解能力强", ITAService.AIModel.QWEN),
    GLM("智谱 GLM-4.5", "中文理解优秀，适合本地化场景", ITAService.AIModel.GLM);

    private final String displayName;
    private final String description;
    private final ITAService.AIModel backendModel;

    UIAIModel(String displayName, String description, ITAService.AIModel backendModel) {
        this.displayName = displayName;
        this.description = description;
        this.backendModel = backendModel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public ITAService.AIModel getBackendModel() {
        return backendModel;
    }

    @Override
    public String toString() {
        return displayName;
    }
}