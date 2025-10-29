package org.ta.api;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DocumentParser {

    /**
     * 按页解析 PDF 文件
     */
    public List<PageText> parsePdfByPage(Path pdfPath) throws IOException {
        List<PageText> pages = new ArrayList<>();
        File file = pdfPath.toFile();

        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document).trim();

                if (!text.isEmpty()) {
                    pages.add(new PageText(file.getName(), i, text));
                }
            }
        }
        return pages;
    }

    /** 每一页的结果结构体 */
    public static class PageText {
        public final String fileName;
        public final int page;
        public final String text;

        public PageText(String fileName, int page, String text) {
            this.fileName = fileName;
            this.page = page;
            this.text = text;
        }
    }
}
