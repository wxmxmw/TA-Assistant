package org.ta.api;

import java.util.Collections;
import java.util.List;

/**
 * Represents the structured final answer from the RAG service.
 */
public class Answer {
    private final String text;                  // The clean answer text, without citation markers.
    private final List<Citation> citations;     // A list of citations parsed from the LLM response.
    private final boolean isFallback;           // True if the answer is a fallback message due to lack of materials.

    public Answer(String text, List<Citation> citations, boolean isFallback) {
        this.text = text;
        this.citations = Collections.unmodifiableList(citations); // Make the list immutable
        this.isFallback = isFallback;
    }

    public String getText() {
        return text;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public boolean isFallback() {
        return isFallback;
    }

    @Override
    public String toString() {
        return "Answer{" +
                "text='" + text + '\'' +
                ", citations=" + citations +
                ", isFallback=" + isFallback +
                '}';
    }
}