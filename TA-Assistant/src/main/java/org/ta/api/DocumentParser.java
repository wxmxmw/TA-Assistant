package org.ta.api;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;

import java.io.FileInputStream;
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

    // ========== 配置参数 ==========
    private static final int MAX_CHUNK_SIZE = 800;      // 每个块最大字符数
    private static final int OVERLAP_SIZE = 100;        // 重叠字符数（保持上下文连贯）
    private static final int MIN_CHUNK_SIZE = 200;      // 最小块大小（避免过小的碎片）

    // ========== 解析入口 ==========
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

    // ========== PDF 解析（保持不变）==========
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

    // ========== Word 解析（方案二：滑动窗口分块）==========
    /**
     * Word 文档解析 - 采用滑动窗口策略
     *
     * 策略说明：
     * 1. 提取所有段落文本并合并为完整文档
     * 2. 使用滑动窗口切分固定大小的文本块
     * 3. 在句子边界处切分，保持语义完整
     * 4. 相邻块之间有重叠区域（overlap），确保上下文连续性
     *
     * 适用场景：长篇连续文本（论文、书籍、技术文档）
     */
    public List<PageText> parseWord(Path docxPath) throws IOException {
        List<PageText> result = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(docxPath.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {

            // ========== 步骤 1: 提取所有段落文本 ==========
            StringBuilder fullText = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText().trim();
                if (!text.isEmpty()) {
                    fullText.append(text).append("\n\n");  // 段落间用双换行分隔
                }
            }

            // ========== 步骤 2: 滑动窗口切分 ==========
            String content = fullText.toString();
            String fileName = docxPath.getFileName().toString();

            // 如果文档太短，直接返回整篇
            if (content.length() <= MAX_CHUNK_SIZE) {
                result.add(new PageText(fileName, 1, content));
                return result;
            }

            int chunkIndex = 1;
            int start = 0;

            while (start < content.length()) {
                // 计算当前块的结束位置
                int end = Math.min(start + MAX_CHUNK_SIZE, content.length());

                // ========== 步骤 3: 在句子边界处切分 ==========
                if (end < content.length()) {
                    // 查找最近的句子终止符（中英文标点）
                    int boundary = findBestBoundary(content, start, end);

                    // 只有在找到合理的边界时才调整（避免切分位置太靠前）
                    if (boundary > start + MIN_CHUNK_SIZE) {
                        end = boundary;
                    }
                }

                // 提取当前块的文本
                String chunk = content.substring(start, end).trim();

                // 只保存非空块
                if (!chunk.isEmpty() && chunk.length() >= 50) {  // 过滤掉过短的碎片
                    result.add(new PageText(fileName, chunkIndex++, chunk));
                }

                // ========== 步骤 4: 滑动窗口移动 ==========
                // 向前移动 (MAX_CHUNK_SIZE - OVERLAP_SIZE)，保留 overlap 区域
                start += (MAX_CHUNK_SIZE - OVERLAP_SIZE);

                // 如果剩余内容太少，直接跳到末尾（避免最后一个块太小）
                if (content.length() - start < MIN_CHUNK_SIZE) {
                    break;
                }
            }
        }

        return result;
    }

    /**
     * 查找最佳切分边界（优先在句子终止符处切分）
     *
     * @param text 完整文本
     * @param start 当前块起始位置
     * @param end 当前块建议结束位置
     * @return 最佳切分位置（字符索引）
     */
    private int findBestBoundary(String text, int start, int end) {
        // 优先级：句号 > 问号/感叹号 > 分号 > 换行符
        String[] boundaries = {"。", "！", "？", ".", "!", "?", "；", ";", "\n\n"};

        // 从 end 位置向前搜索，找到最近的句子边界
        for (String boundary : boundaries) {
            int lastIndex = text.lastIndexOf(boundary, end);

            // 确保找到的边界不会太靠前（至少保留 MIN_CHUNK_SIZE）
            if (lastIndex > start + MIN_CHUNK_SIZE) {
                // 返回边界符后一个字符的位置
                return lastIndex + boundary.length();
            }
        }

        // 如果没找到合适的边界，尝试在空格处切分
        for (int i = end - 1; i > start + MIN_CHUNK_SIZE; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }

        // 实在找不到，就在原位置切分
        return end;
    }

    // ========== PPT 解析（保持不变）==========
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