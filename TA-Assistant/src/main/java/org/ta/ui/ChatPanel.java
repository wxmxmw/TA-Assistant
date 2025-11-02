package org.ta.ui;

import org.ta.api.Answer;
import org.ta.api.Citation;
import org.ta.mock.MockTAService;

import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * ChatPanel: 完整聊天界面,带用户/AI气泡,支持上下文管理。
 */
public class ChatPanel extends JPanel {
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("发送");
    private final JButton clearButton = new JButton("清除");
    private final JPanel messageAreaPanel = new JPanel();
    private final MockTAService service = new MockTAService();
    private final JPanel contextsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private final java.util.List<ContextItem> contexts = new java.util.ArrayList<>();

    public ChatPanel() {
        super(new BorderLayout());

        // 消息区
        messageAreaPanel.setLayout(new BoxLayout(messageAreaPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(messageAreaPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // 输入区
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(inputField, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonPanel.add(clearButton);
        buttonPanel.add(sendButton);
        top.add(buttonPanel, BorderLayout.EAST);

        contextsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(contextsPanel, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // 发送按钮事件
        sendButton.addActionListener(e -> {
            String question = inputField.getText().trim();
            if (question.isEmpty()) return;

            Optional<String> aggregated = buildAggregatedContextOptional();
            ask(question, aggregated);
            clearContexts();
            inputField.setText("");
        });

        // 清除按钮事件
        clearButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "确定要清除所有聊天记录吗?",
                    "确认清除",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                clearMessages();
                clearContexts();
            }
        });
    }

    public void ask(String question, Optional<String> codeContext) {
        // 打印上下文信息
        codeContext.ifPresent(ctx -> {
            System.out.println("上下文全文:\n" + ctx);
        });
        if (!contexts.isEmpty()) {
            StringBuilder titles = new StringBuilder();
            for (int i = 0; i < contexts.size(); i++) {
                if (i > 0) titles.append(", ");
                titles.append(contexts.get(i).title);
            }
            appendMessage("上下文: " + titles.toString(), ChatBubble.Type.USER, null);
        }
        appendMessage("You: " + question, ChatBubble.Type.USER, null);

        // 显示加载状态
        JLabel loadingLabel = new JLabel("正在思考...");
        loadingLabel.setForeground(new Color(100, 100, 100));
        messageAreaPanel.add(loadingLabel);
        messageAreaPanel.revalidate();
        messageAreaPanel.repaint();

        // 在后台线程执行 RAG 调用
        SwingWorker<Answer, Void> worker = new SwingWorker<Answer, Void>() {
            @Override
            protected Answer doInBackground() throws Exception {
                return service.answer(question, codeContext, 3);
            }

            @Override
            protected void done() {
                // 移除加载指示器
                messageAreaPanel.remove(loadingLabel);

                try {
                    Answer answer = get();
                    appendMessage("TA: " + answer.toString(), ChatBubble.Type.ASSISTANT, answer);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(
                            ChatPanel.this,
                            "获取回答失败: " + e.getMessage(),
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        worker.execute();
    }

    public void addContext(String title, String text) {
        if (title == null || title.isBlank() || text == null || text.isBlank()) return;

        ContextItem item = new ContextItem(title, text);
        contexts.add(item);
        JLabel chip = createChipLabel(title, item);
        contextsPanel.add(chip);
        contextsPanel.revalidate();
        contextsPanel.repaint();
    }

    private Optional<String> buildAggregatedContextOptional() {
        if (contexts.isEmpty()) return Optional.empty();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            ContextItem c = contexts.get(i);
            sb.append("[Context ").append(i + 1).append("] ").append(c.title).append("\n");
            sb.append(c.text).append("\n");
            if (i < contexts.size() - 1) sb.append("\n---\n\n");
        }
        return Optional.of(sb.toString());
    }

    private void clearContexts() {
        contexts.clear();
        contextsPanel.removeAll();
        contextsPanel.revalidate();
        contextsPanel.repaint();
    }

    private void clearMessages() {
        messageAreaPanel.removeAll();
        messageAreaPanel.revalidate();
        messageAreaPanel.repaint();
    }

    /** 添加气泡消息,支持显示引用按钮 */
    private void appendMessage(String message, ChatBubble.Type type, Answer answer) {
        ChatBubble bubble = new ChatBubble(message, type);
        messageAreaPanel.add(bubble);

        // 如果是 AI 回答且有引用信息,添加引用按钮
        if (type == ChatBubble.Type.ASSISTANT && answer != null && answer.getCitations() != null && !answer.getCitations().isEmpty()) {
            // 创建引用按钮面板
            JPanel citationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            citationPanel.setOpaque(false);

            for (Citation citation : answer.getCitations()) {
                JButton viewSourceBtn = new JButton("📄 " + citation.getSourceFile() + "@p" + citation.getPageRange());
                viewSourceBtn.addActionListener(e -> {
                    openPdfFromCitation(citation);
                });
                citationPanel.add(viewSourceBtn);
            }

            messageAreaPanel.add(citationPanel);
        }

        messageAreaPanel.add(Box.createVerticalStrut(6));
        messageAreaPanel.revalidate();
        messageAreaPanel.repaint();

        // 自动滚动到底部
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = ((JScrollPane) getComponent(1)).getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    /** 从引用信息中打开 PDF */
    private void openPdfFromCitation(Citation citation) {
        System.out.println("尝试打开 PDF: " + citation.getSourceFile());

        // 从 citation 中提取 PDF 文件名
        String fileName = citation.getSourceFile();
        Path pdfPath = Paths.get("D:/0-xiangmu/TA-Assistant/build/resources/main/docs", fileName);

        if (Files.exists(pdfPath)) {
            openPdfFile(pdfPath);
        } else {
            JOptionPane.showMessageDialog(this, "找不到文件: " + fileName);
        }
    }

    /** 使用系统默认程序打开 PDF 文件 */
    private void openPdfFile(Path pdfPath) {
        System.out.println("打开 PDF: " + pdfPath);

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdfPath.toFile());
                System.out.println("PDF 已打开");
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "系统不支持打开 PDF 文件",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "打开 PDF 失败: " + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /** 创建上下文标签(保留原有功能,用于显示选中的代码上下文) */
    private JLabel createChipLabel(String title, ContextItem item) {
        JLabel label = new JLabel(title);
        label.setOpaque(false);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        return label;
    }

    private static class ContextItem {
        final String title;
        final String text;
        ContextItem(String title, String text) { this.title = title; this.text = text; }
    }
}