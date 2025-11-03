package org.ta.api;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * The main interface for the Teaching Assistant service, defining its capabilities.
 */
public interface ITAService {

    /**
     * Answers a user's question using the RAG pipeline.
     */
    Answer answer(String question, Optional<String> codeContext, int topK);

    /**
     * Sets the active AI model to be used for generating answers.
     * @param model The model to switch to.
     */
    void setModel(AIModel model);

    /**
     * Enum representing the available AI models.
     * This provides a type-safe way to select models.
     */
    enum AIModel {
        DEEPSEEK("deepseek-ai/DeepSeek-V3.2-Exp"),
        QWEN("Qwen/Qwen3-VL-32B-Thinking"),
        GLM("zai-org/GLM-4.5");

        private final String modelName;

        AIModel(String modelName) {
            this.modelName = modelName;
        }

        public String getModelName() {
            return modelName;
        }
    }
}