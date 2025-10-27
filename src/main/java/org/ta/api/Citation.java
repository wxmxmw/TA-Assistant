package org.ta.api;

/**
 * Citation metadata for an answer.
 */
public class Citation {
    private final String titleOrFileName;
    private final String pageRange;
    private final String snippetPreview;
    private final double score;

    public Citation(String titleOrFileName, String pageRange, String snippetPreview, double score) {
        this.titleOrFileName = titleOrFileName;
        this.pageRange = pageRange;
        this.snippetPreview = snippetPreview;
        this.score = score;
    }

    public String getTitleOrFileName() { return titleOrFileName; }
    public String getPageRange() { return pageRange; }
    public String getSnippetPreview() { return snippetPreview; }
    public double getScore() { return score; }
}