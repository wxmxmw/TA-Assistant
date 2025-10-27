package org.ta.api;

import java.util.List;

/**
 * LLM/RAG answer payload.
 */
public class Answer {
    private final String text;
    private final List<Citation> citations;
    // "course_material" | "general_knowledge"
    private final String sourceType;

    public Answer(String text, List<Citation> citations, String sourceType) {
        this.text = text;
        this.citations = citations;
        this.sourceType = sourceType;
    }

    public String getText() { return text; }
    public List<Citation> getCitations() { return citations; }
    public String getSourceType() { return sourceType; }
}