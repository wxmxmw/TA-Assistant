package org.ta.ui;
import org.ta.api.ITAService;
import org.ta.api.Answer;
import org.ta.api.Citation;
import org.ta.core.TAService;
import org.ta.mock.MockTAService;
import java. io. IOException;
import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ChatPanel: 完整聊天界面,带用户/AI气泡,支持上下文管理。
 */
public class ChatPanel extends JPanel {
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("发送");
    private final JButton clearButton = new JButton("清除");
    private final JPanel messageAreaPanel = new JPanel();
    private TAService service;
    private boolean serviceReady = false;
    private final JPanel contextsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private final List<ContextItem> contexts = new ArrayList<>();

    public ChatPanel() {
        super(new BorderLayout());

        // 创建底部状态栏面板
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // 左侧:状态标签
        JLabel statusLabel = new JLabel("正在初始化服务...");
        statusLabel.setForeground(new Color(100, 100, 100));
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        // 右侧:模型选择器面板
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        modelPanel.setOpaque(false);

        JLabel modelLabel = new JLabel("模型:");
        modelLabel.setForeground(new Color(100, 100, 100));
        modelPanel.add(modelLabel);

        JComboBox<ITAService.AIModel> modelSelector = new JComboBox<>(ITAService.AIModel.values());
        modelSelector.setPreferredSize(new Dimension(180, 25));
        modelSelector.addActionListener(e -> {
            ITAService.AIModel selectedModel = (ITAService.AIModel) modelSelector.getSelectedItem();
            if (serviceReady && selectedModel != null) {
                service.setModel(selectedModel);
                System.out.println("切换到模型: " + selectedModel.getModelName());
            }
        });
        modelPanel.add(modelSelector);

        bottomPanel.add(modelPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // 异步初始化 TAService
        SwingWorker<TAService, Void> initWorker = new SwingWorker<>() {
            @Override
            protected TAService doInBackground() throws Exception {
                String apiKey = "sk-vxlhkdlhcupthzbhbsgjodibgplhupnjsmlunudidlipbnut";
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException("请设置 SILICON_API_KEY 环境变量");
                }
                return new TAService(apiKey);
            }

            @Override
            protected void done() {
                try {
                    service = get();
                    serviceReady = true;
                    // 更新状态标签文本和颜色
                    statusLabel.setText("✓ 服务已就绪");
                    statusLabel.setForeground(new Color(0, 150, 0));
                } catch (Exception e) {
                    e.printStackTrace();
                    statusLabel.setText("✗ 服务初始化失败");
                    statusLabel.setForeground(Color.RED);
                    JOptionPane.showMessageDialog(
                            ChatPanel.this,
                            "服务初始化失败: " + e.getMessage(),
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        initWorker.execute();

        // 消息区
        messageAreaPanel.setLayout(new BoxLayout(messageAreaPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(messageAreaPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // 上下文区（显示在对话框下方）
        JPanel contextWrapper = new JPanel(new BorderLayout());
        contextWrapper.setOpaque(false);
        JLabel ctxHeader = new JLabel("上下文:");
        ctxHeader.setFont(ctxHeader.getFont().deriveFont(11f));
        ctxHeader.setForeground(new Color(100, 100, 100));
        JPanel ctxHeaderPanel = new JPanel(new BorderLayout());
        ctxHeaderPanel.setOpaque(false);
        ctxHeaderPanel.add(ctxHeader, BorderLayout.WEST);
        contextWrapper.add(ctxHeaderPanel, BorderLayout.NORTH);
        contextWrapper.add(contextsPanel, BorderLayout.CENTER);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(scrollPane, BorderLayout.CENTER);
        centerWrapper.add(contextWrapper, BorderLayout.SOUTH);

        // 输入区
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(inputField, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton uploadButton = new JButton("上传");
        uploadButton.addActionListener(e -> uploadDocument());
        buttonPanel.add(uploadButton);

        buttonPanel.add(clearButton);
        buttonPanel.add(sendButton);
        top.add(buttonPanel, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(centerWrapper, BorderLayout.CENTER);

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
        }

        appendMessage("You: " + question, ChatBubble.Type.USER, null);
        // 在“你”的消息气泡下附带每个上下文标题
        if (!contexts.isEmpty()) {
            renderUserContextTitles();
        }

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
                    appendMessage("TA: " + answer.getText(), ChatBubble.Type.ASSISTANT, answer);
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

    private void appendMessage(String message, ChatBubble.Type type, Answer answer) {
        ChatBubble bubble = new ChatBubble(message, type);
        messageAreaPanel.add(bubble);

        // 核心改进：只在非 fallback 时显示引用
        if (type == ChatBubble.Type.ASSISTANT && answer != null && !answer.isFallback()) {
            List<Citation> citations = answer.getCitations();
            if (citations != null && !citations.isEmpty()) {
                renderCitationCards(citations);
            }
        }

        messageAreaPanel.add(Box.createVerticalStrut(1));
        messageAreaPanel.revalidate();
        messageAreaPanel.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = ((JScrollPane) getComponent(1)).getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void renderCitationCards(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) return;

        // 去重逻辑
        Set<String> seen = new HashSet<>();
        List<Citation> uniqueCitations = new ArrayList<>();
        for (Citation citation : citations) {
            String key = citation.getSourceFile() + "|" + citation.getPageRange();
            if (seen.add(key)) {
                uniqueCitations.add(citation);
            }
        }
        if (uniqueCitations.isEmpty()) return;

        // 内容容器
        JPanel citationContainer = new JPanel();
        citationContainer.setLayout(new BoxLayout(citationContainer, BoxLayout.Y_AXIS));
        citationContainer.setOpaque(false);

        // headerLabel 单独包裹一个 panel
        JLabel headerLabel = new JLabel("参考来源:");
        headerLabel.setFont(headerLabel.getFont().deriveFont(11f));
        headerLabel.setForeground(new Color(100, 100, 100));

        // 新建单独 panel，只控制 header 左对齐，不影响卡片
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(headerLabel, BorderLayout.WEST);

        // 添加 headerPanel 到 citationContainer
        citationContainer.add(headerPanel);
        citationContainer.add(Box.createVerticalStrut(1));

        for (Citation citation : uniqueCitations) {
            JPanel card = createCitationCard(citation);
            citationContainer.add(card);
            citationContainer.add(Box.createVerticalStrut(1));
        }

        // wrapper 层
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.add(citationContainer);

        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setOpaque(false);
        outerPanel.setBorder(BorderFactory.createEmptyBorder(0, 50, 0, 100));
        outerPanel.add(wrapper, BorderLayout.WEST);

        messageAreaPanel.add(outerPanel);
        messageAreaPanel.revalidate();
        messageAreaPanel.repaint();
    }

    // 在用户消息气泡下方渲染当前上下文的标题列表
    private void renderUserContextTitles() {
        JPanel titlesContainer = new JPanel();
        titlesContainer.setLayout(new BoxLayout(titlesContainer, BoxLayout.Y_AXIS));
        titlesContainer.setOpaque(false);
        titlesContainer.setBorder(BorderFactory.createEmptyBorder(0, 50, 0, 100));

        JLabel header = new JLabel("附带上下文:");
        header.setFont(header.getFont().deriveFont(11f));
        header.setForeground(new Color(100, 100, 100));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(header, BorderLayout.WEST);

        titlesContainer.add(headerPanel);
        titlesContainer.add(Box.createVerticalStrut(1));

        for (ContextItem c : contexts) {
            JLabel titleLabel = new JLabel("• " + c.title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(10f));
            titleLabel.setForeground(new Color(120, 120, 120));
            titlesContainer.add(titleLabel);
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(titlesContainer, BorderLayout.WEST);

        messageAreaPanel.add(wrapper);
        messageAreaPanel.add(Box.createVerticalStrut(1));
        messageAreaPanel.revalidate();
        messageAreaPanel.repaint();
    }

    private JPanel createCitationCard(Citation citation) {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setBackground(new Color(245, 247, 250));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        card.setMaximumSize(new Dimension(350, 25));

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 1));
        infoPanel.setOpaque(false);

        JLabel fileLabel = new JLabel(citation.getSourceFile());
        fileLabel.setFont(fileLabel.getFont().deriveFont(10f));
        fileLabel.setForeground(Color.BLACK);

        JLabel pageLabel = new JLabel("页码: " + citation.getPageRange());
        pageLabel.setFont(pageLabel.getFont().deriveFont(9f));
        pageLabel.setForeground(new Color(120, 120, 120));

        infoPanel.add(fileLabel);
        infoPanel.add(pageLabel);
        card.add(infoPanel, BorderLayout.CENTER);

        JButton viewBtn = new JButton("查看");
        viewBtn.setFocusPainted(false);
        viewBtn.setPreferredSize(new Dimension(30, 10));
        viewBtn.setFont(viewBtn.getFont().deriveFont(8f));
        viewBtn.setMargin(new Insets(1, 4, 1, 4));
        viewBtn.addActionListener(e -> openPdfFromCitation(citation));
        card.add(viewBtn, BorderLayout.EAST);

        return card;
    }

    /**
     * 从引用信息中打开 PDF
     */
    private void openPdfFromCitation(Citation citation) {
        System.out.println("尝试打开 PDF: " + citation.getSourceFile());
        String fileNameOrPath = citation.getSourceFile();

        List<Path> candidates = new ArrayList<>();
        Path asPath = Paths.get(fileNameOrPath);
        if (asPath.isAbsolute()) {
            candidates.add(asPath);
        }
        // 开发环境资源目录
        candidates.add(Paths.get("src/main/resources/docs", fileNameOrPath));
        // 构建产物资源目录
        candidates.add(Paths.get("build/resources/main/docs", fileNameOrPath));
        // 项目根下的 docs 目录
        candidates.add(Paths.get("docs", fileNameOrPath));

        Path found = null;
        for (Path p : candidates) {
            if (Files.exists(p)) {
                found = p;
                break;
            }
        }

        if (found != null) {
            openPdfFile(found);
        } else {
            JOptionPane.showMessageDialog(
                    this,
                    "找不到文件: " + fileNameOrPath + "\n已尝试路径:\n" +
                            candidates.stream().map(Path::toString).collect(Collectors.joining("\n")),
                    "错误",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * 使用系统默认程序打开 PDF 文件
     */
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

    /**
     * 创建上下文标签(保留原有功能,用于显示选中的代码上下文)
     */
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

        ContextItem(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    // pdf上传逻辑
    private void uploadDocument() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PDF 文档", "pdf"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path selectedFile = fileChooser.getSelectedFile().toPath();

            try {
                // 使用项目资源目录，避免硬编码绝对路径
                Path docsDir = Paths.get("src/main/resources/docs");
                if (!Files.exists(docsDir)) {
                    Files.createDirectories(docsDir);
                }

                // 复制文件到 docs 目录
                Path targetPath = docsDir.resolve(selectedFile.getFileName());
                Files.copy(selectedFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

                // 显示上传中提示
                JLabel uploadingLabel = new JLabel("正在索引文档...");
                uploadingLabel.setForeground(new Color(100, 100, 100));
                messageAreaPanel.add(uploadingLabel);
                messageAreaPanel.revalidate();
                messageAreaPanel.repaint();

                // 然后在后台线程索引文件
                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        service.addDocument(targetPath); // 使用复制后的路径
                        return null;
                    }

                    @Override
                    protected void done() {
                        // 移除上传中提示
                        messageAreaPanel.remove(uploadingLabel);

                        try {
                            get();
                            JOptionPane.showMessageDialog(
                                    ChatPanel.this,
                                    "文档已保存到 docs 目录并索引成功",
                                    "成功",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                            JOptionPane.showMessageDialog(
                                    ChatPanel.this,
                                    "上传失败: " + e.getMessage(),
                                    "错误",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }

                        messageAreaPanel.revalidate();
                        messageAreaPanel.repaint();
                    }
                };
                worker.execute();

            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        this,
                        "文件复制失败: " + e.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
}