package org.ta.api;

import java.util.Objects;

/**
 * Represents a single citation, linking back to a source document.
 */
public class Citation {
    private final String sourceFile; // 来源文件名, e.g., "Week 3 - Spring Boot.pdf"
    private final String pageRange;  // 页码范围, e.g., "12-13"

    public Citation(String sourceFile, String pageRange) {
        this.sourceFile = sourceFile;
        this.pageRange = pageRange;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getPageRange() {
        return pageRange;
    }

    @Override
    public String toString() {
        return "Citation{" +
                "sourceFile='" + sourceFile + '\'' +
                ", pageRange='" + pageRange + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Citation citation = (Citation) o;
        return Objects.equals(sourceFile, citation.sourceFile) && Objects.equals(pageRange, citation.pageRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile, pageRange);
    }
}