package org.ta.api;

import java.util.Objects;

/**
 * Represents a chunk of text retrieved from the knowledge store.
 * This is the data contract between the KnowledgeStore (Group A) and the RagService (Group B).
 */
public class RetrievedChunk {

    private final String sourceFile; // The name of the document the chunk came from (e.g., "Week_4_Slides.pdf")
    private final String pageRange;  // The page or pages the chunk is from (e.g., "15" or "15-16")
    private final String content;    // The actual text content of the chunk

    /**
     * Constructs a new RetrievedChunk.
     * @param sourceFile The source document name.
     * @param pageRange The page range.
     * @param content The text content.
     */
    public RetrievedChunk(String sourceFile, String pageRange, String content) {
        this.sourceFile = sourceFile;
        this.pageRange = pageRange;
        this.content = content;
    }

    // --- GETTER METHODS ---
    // These are the methods that RagService needs to call, which will resolve the red highlights.

    public String getSourceFile() {
        return sourceFile;
    }

    public String getPageRange() {
        return pageRange;
    }

    public String getContent() {
        return content;
    }

    // --- Optional: hashCode, equals, and toString for better debugging ---

    @Override
    public String toString() {
        return "RetrievedChunk{" +
                "sourceFile='" + sourceFile + '\'' +
                ", pageRange='" + pageRange + '\'' +
                ", content='" + (content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetrievedChunk that = (RetrievedChunk) o;
        return Objects.equals(sourceFile, that.sourceFile) &&
                Objects.equals(pageRange, that.pageRange) &&
                Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile, pageRange, content);
    }
}