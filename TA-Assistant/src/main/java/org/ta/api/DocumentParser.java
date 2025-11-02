package org.ta.api;
import java.io.FileInputStream;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xslf.usermodel.*;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DocumentParser {

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

    public List<PageText> parse(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            return parsePdfByPage(filePath);
        } else if (fileName.endsWith(".docx")) {
            return parseWord(filePath);
        } else if (fileName.endsWith(".pptx")) {
            return parsePpt(filePath);
        } else {
            throw new IOException("Unsupported file format: " + fileName);
        }
    }

    // ✅ PDF
    public List<PageText> parsePdfByPage(Path pdfPath) throws IOException {
        List<PageText> result = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                result.add(new PageText(pdfPath.getFileName().toString(), i, text));
            }
        }
        return result;
    }

    // ✅ Word
    public List<PageText> parseWord(Path docxPath) throws IOException {
        List<PageText> result = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(docxPath.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            result.add(new PageText(docxPath.getFileName().toString(), 1, sb.toString()));
        }
        return result;
    }

    // ✅ PPT
    public List<PageText> parsePpt(Path pptxPath) throws IOException {
        List<PageText> result = new ArrayList<>();
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(pptxPath.toFile()))) {
            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                StringBuilder sb = new StringBuilder();
                for (XSLFShape shape : slides.get(i).getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        sb.append(textShape.getText()).append("\n");
                    }
                }
                result.add(new PageText(pptxPath.getFileName().toString(), i + 1, sb.toString()));
            }
        }
        return result;
    }
}
