package org.ta.api;

/**
 * Abstraction for LLM provider client. B will implement with real APIs later.
 */
public interface LLMClient {
    String complete(String prompt);
}