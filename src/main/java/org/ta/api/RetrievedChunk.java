package org.ta.api;

/**
 * Represents a retrieved piece of course material.
 */
public class RetrievedChunk {
    private final String text;
    private final String sourcePath;
    private final String pageRange;
    private final double score;

    public RetrievedChunk(String text, String sourcePath, String pageRange, double score) {
        this.text = text;
        this.sourcePath = sourcePath;
        this.pageRange = pageRange;
        this.score = score;
    }

    public String getText() { return text; }
    public String getSourcePath() { return sourcePath; }
    public String getPageRange() { return pageRange; }
    public double getScore() { return score; }
}