package org.ta.api;

import java.util.Optional;

/**
 * Orchestrates retrieval, prompt construction, LLM answer, and post-processing.
 * For prototype, we provide a mock implementation.
 */
public interface RagService {
    Answer answer(String question, Optional<String> codeContext, int topK);
}