//package org.ta.ui;
//
//import com.intellij.diff.DiffContentFactory;
//import com.intellij.diff.DiffManager;
//import com.intellij.diff.DiffRequestPanel;
//import com.intellij.diff.contents.DiffContent;
//import com.intellij.diff.contents.DocumentContent;
//import com.intellij.diff.requests.SimpleDiffRequest;
//import com.intellij.icons.AllIcons;
//import com.intellij.openapi.Disposable;
//import com.intellij.openapi.application.ApplicationManager;
//import com.intellij.openapi.editor.Document;
//import com.intellij.openapi.editor.EditorFactory;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.util.Disposer;
//import com.intellij.ui.JBColor;
//import com.intellij.ui.components.JBLabel;
//import org.jetbrains.annotations.NotNull;
//import org.ta.api.ITAService.AIModel;
//import org.ta.core.AIModificationUtil;
//import org.ta.llm.models.ChatSessionManager;
//import org.ta.actions.ModifyCodeWithAIAction;
//
//import javax.swing.*;
//import java.awt.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//public class DiffPreviewPanel extends JPanel implements Disposable {
//    private final Project project;
//    private final DiffRequestPanel diffPanel;
//    private final DocumentContent modifiedContent;
//    private final Document modifiedDocument;
//    private final String originalCode;
//
//    private JButton acceptButton;
//    private JButton rejectButton;
//    private JButton regenerateButton;
//    private JBLabel statusLabel;
//    private JTextField chatInput;
//    private JButton sendButton;
//
//    private Runnable onAccept;
//    private Runnable onReject;
//    private Runnable onRegenerate;
//
//    private final ChatSessionManager sessionManager = new ChatSessionManager();
//    private final AIModel currentModel = AIModel.DEEPSEEK;
//
//    public DiffPreviewPanel(@NotNull Project project, @NotNull String originalCode) {
//        this.project = project;
//        this.originalCode = originalCode;
//
//        setLayout(new BorderLayout(0, 10));
//        setPreferredSize(new Dimension(900, 650));
//        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//
//        // ✅ 正确创建可编辑的 DocumentContent（完全沿用 CodeModificationDialog 思路）
//        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
//        DiffContent originalContent = contentFactory.create(project, originalCode);
//
//        modifiedDocument = EditorFactory.getInstance().createDocument("正在生成代码...\n");
//        modifiedContent = contentFactory.create(project, modifiedDocument); // ✅ 用 Document，不用 createEditable
//
//        diffPanel = DiffManager.getInstance().createRequestPanel(project, this, null);
//
//        SimpleDiffRequest request = new SimpleDiffRequest(
//                "AI 代码修改建议",
//                originalContent,
//                modifiedContent,
//                "原始代码",
//                "AI 生成的代码（可编辑）"
//        );
//        diffPanel.setRequest(request);
//
//        // 顶部
//        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
//        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
//
//        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
//        JBLabel titleLabel = new JBLabel("AI 代码修改预览");
//        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
//        titleLabel.setIcon(AllIcons.Vcs.Merge);
//
//        statusLabel = new JBLabel("等待输入...");
//        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
//        statusLabel.setForeground(JBColor.GRAY);
//
//        titlePanel.add(titleLabel);
//        titlePanel.add(Box.createHorizontalStrut(10));
//        titlePanel.add(statusLabel);
//
//        JBLabel hintLabel = new JBLabel("💡 右侧代码可编辑，底部可继续对话");
//        hintLabel.setFont(hintLabel.getFont().deriveFont(11f));
//        hintLabel.setForeground(new JBColor(new Color(100, 150, 200), new Color(100, 150, 200)));
//
//        headerPanel.add(titlePanel, BorderLayout.WEST);
//        headerPanel.add(hintLabel, BorderLayout.EAST);
//
//        // 中间 Diff
//        JPanel diffContainer = new JPanel(new BorderLayout());
//        diffContainer.setBorder(BorderFactory.createLineBorder(JBColor.border(), 1));
//        diffContainer.add(diffPanel.getComponent(), BorderLayout.CENTER);
//
//        // ✅ 底部输入框：防止焦点冲突
//        JPanel chatPanel = new JPanel(new BorderLayout(8, 0));
//        chatInput = new JTextField();
//        chatInput.setToolTipText("请输入AI指令，例如：优化命名 / 添加注释 / 修复Bug...");
//        chatInput.setFocusable(true);
//        chatInput.setEnabled(true);
//        chatInput.setRequestFocusEnabled(true);
//
//        sendButton = new JButton("发送");
//        sendButton.setPreferredSize(new Dimension(90, 32));
//        sendButton.setBackground(new Color(33, 150, 243));
//        sendButton.setForeground(Color.WHITE);
//        sendButton.setBorderPainted(false);
//        sendButton.setOpaque(true);
//        chatPanel.add(chatInput, BorderLayout.CENTER);
//        chatPanel.add(sendButton, BorderLayout.EAST);
//
//        // 底部按钮
//        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
//        rejectButton = new JButton("✗ 拒绝修改");
//        regenerateButton = new JButton("🔄 重新生成");
//        acceptButton = new JButton("✓ 应用修改");
//
//        rejectButton.setPreferredSize(new Dimension(110, 34));
//        regenerateButton.setPreferredSize(new Dimension(120, 34));
//        acceptButton.setPreferredSize(new Dimension(110, 34));
//
//        acceptButton.setBackground(new Color(76, 175, 80));
//        acceptButton.setForeground(Color.WHITE);
//        acceptButton.setOpaque(true);
//        acceptButton.setBorderPainted(false);
//        acceptButton.setFont(acceptButton.getFont().deriveFont(Font.BOLD));
//
//        rejectButton.setBackground(new Color(244, 67, 54));
//        rejectButton.setForeground(Color.WHITE);
//        rejectButton.setOpaque(true);
//        rejectButton.setBorderPainted(false);
//
//        regenerateButton.setBackground(new Color(33, 150, 243));
//        regenerateButton.setForeground(Color.WHITE);
//        regenerateButton.setOpaque(true);
//        regenerateButton.setBorderPainted(false);
//
//        buttonPanel.add(rejectButton);
//        buttonPanel.add(regenerateButton);
//        buttonPanel.add(acceptButton);
//
//        JPanel footerPanel = new JPanel(new BorderLayout(0, 5));
//        footerPanel.add(chatPanel, BorderLayout.NORTH);
//        footerPanel.add(buttonPanel, BorderLayout.SOUTH);
//
//        // 组装
//        add(headerPanel, BorderLayout.NORTH);
//        add(diffContainer, BorderLayout.CENTER);
//        add(footerPanel, BorderLayout.SOUTH);
//
//        // 事件绑定
//        acceptButton.addActionListener(e -> { if (onAccept != null) onAccept.run(); });
//        rejectButton.addActionListener(e -> { if (onReject != null) onReject.run(); });
//        regenerateButton.addActionListener(e -> {
//            if (onRegenerate != null) onRegenerate.run();
//            if (!sessionManager.getHistory().isEmpty()) {
//                String last = sessionManager.getHistory()
//                        .get(sessionManager.getHistory().size() - 1)
//                        .getContent();
//                runAI(last);
//            }
//        });
//
//        sendButton.addActionListener(e -> sendPrompt());
//        chatInput.addActionListener(e -> sendPrompt());
//    }
//
//    /** 发送输入到 AI */
//    private void sendPrompt() {
//        String prompt = chatInput.getText().trim();
//        if (prompt.isEmpty()) return;
//        chatInput.setText("");
//        runAI(prompt);
//    }
//
//    /** 核心：流式生成逻辑 */
//    private void runAI(String userPrompt) {
//        sessionManager.addUserMessage(userPrompt);
//        clearCode();
//        setStatus("AI 正在生成中...");
//
//        AtomicBoolean isFirstChunk = new AtomicBoolean(true);
//
//        AIModificationUtil.modifyCodeStream(
//                userPrompt,
//                originalCode,
//                ModifyCodeWithAIAction.API_KEY,
//                currentModel,
//                chunk -> ApplicationManager.getApplication().invokeLater(() -> {
//                    if (isFirstChunk.getAndSet(false)) setStatus("AI 正在生成...");
//                    appendCode(chunk);
//                }),
//                () -> ApplicationManager.getApplication().invokeLater(() -> {
//                    setGenerationComplete();
//                    sessionManager.addAssistantMessage(getGeneratedCode());
//                }),
//                e -> ApplicationManager.getApplication().invokeLater(() ->
//                        setGenerationFailed(e.getMessage()))
//        );
//    }
//
//    // ========= 基础工具 =========
//    public void setCode(String code) { modifiedDocument.setText(code); }
//    public void appendCode(String chunk) { modifiedDocument.insertString(modifiedDocument.getTextLength(), chunk); }
//    public void clearCode() { modifiedDocument.setText(""); }
//    public String getGeneratedCode() { return modifiedDocument.getText(); }
//
//    public void setStatus(String status) { statusLabel.setText(status); }
//    public void setGenerationComplete() {
//        statusLabel.setText("✓ 生成完成");
//        statusLabel.setForeground(new JBColor(new Color(76, 175, 80), new Color(76, 175, 80)));
//    }
//    public void setGenerationFailed(String error) {
//        statusLabel.setText("✗ 生成失败: " + error);
//        statusLabel.setForeground(new JBColor(Color.RED, Color.RED));
//    }
//
//    public JComponent getPreferredFocusedComponent() { return diffPanel.getPreferredFocusedComponent(); }
//    public void setOnAccept(Runnable callback) { this.onAccept = callback; }
//    public void setOnReject(Runnable callback) { this.onReject = callback; }
//    public void setOnRegenerate(Runnable callback) { this.onRegenerate = callback; }
//
//    @Override public void dispose() { Disposer.dispose(diffPanel); }
//}
