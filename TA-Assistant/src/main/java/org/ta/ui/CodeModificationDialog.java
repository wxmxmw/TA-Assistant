package org.ta.ui;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CodeModificationDialog extends DialogWrapper {
    private final DiffRequestPanel diffPanel;
    private final Consumer<String> applyCallback;
    private final DocumentContent modifiedContent;

    // 🆕 多轮对话相关
    private final JBTextField chatInput;
    private final JButton chatSendButton;
    private final JBLabel statusLabel;
    private BiConsumer<String, String> onChatSubmit; // (instruction, currentCode) -> void

    public CodeModificationDialog(@Nullable Project project,
                                  DiffContent originalContent,
                                  DocumentContent modifiedContent,
                                  Consumer<String> applyCallback) {
        super(project, false);
        this.applyCallback = applyCallback;
        this.modifiedContent = modifiedContent;

        diffPanel = DiffManager.getInstance().createRequestPanel(project, this.getDisposable(), null);

        SimpleDiffRequest request = new SimpleDiffRequest(
                "AI 代码修改建议 (右侧可编辑)",
                originalContent,
                this.modifiedContent,
                "原始代码",
                "AI 生成的代码"
        );

        diffPanel.setRequest(request);

        // 初始化多轮对话组件
        chatInput = new JBTextField();
        chatInput.getEmptyText().setText("继续输入指令，AI 将基于右侧代码进行修改...");

        chatSendButton = new JButton("发送");
        chatSendButton.setPreferredSize(new Dimension(80, 28));

        statusLabel = new JBLabel("");
        statusLabel.setForeground(new Color(128, 128, 128));

        setTitle("AI 代码修改审查");
        setOKButtonText("应用修改");
        setCancelButtonText("取消");

        // 绑定发送事件
        setupChatListeners();

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 上部：Diff 面板
        mainPanel.add(diffPanel.getComponent(), BorderLayout.CENTER);

        // 下部：多轮对话区域
        JPanel chatPanel = createChatPanel();
        mainPanel.add(chatPanel, BorderLayout.SOUTH);

        return mainPanel;
    }

    /**
     * 🆕 创建多轮对话面板
     */
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(JBUI.Borders.empty(10, 12));
        panel.setOpaque(false); // 允许透明
        panel.setBackground(new Color(173, 216, 230, 128)); // 半透明浅蓝色

        // 左侧：输入框
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setOpaque(false);

        JBLabel promptLabel = new JBLabel("💬");
        promptLabel.setFont(promptLabel.getFont().deriveFont(16f));
        inputPanel.add(promptLabel, BorderLayout.WEST);
        inputPanel.add(chatInput, BorderLayout.CENTER);

        // 右侧：发送按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(chatSendButton);

        // 顶部：状态提示
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(statusLabel, BorderLayout.WEST);

        // 组装
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(inputPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * 🆕 设置聊天监听器
     */
    private void setupChatListeners() {
        Runnable submitAction = () -> {
            String instruction = chatInput.getText().trim();
            if (instruction.isEmpty()) {
                return;
            }

            if (onChatSubmit != null) {
                String currentCode = modifiedContent.getDocument().getText();

                // 禁用输入框和按钮
                setChatEnabled(false);
                setStatus("正在处理...");

                // 调用回调
                onChatSubmit.accept(instruction, currentCode);

                // 清空输入框
                chatInput.setText("");
            }
        };

        chatSendButton.addActionListener(e -> submitAction.run());
        chatInput.addActionListener(e -> submitAction.run());
    }

    /**
     * 🆕 设置多轮对话回调
     * @param callback (instruction, currentCode) -> void
     */
    public void setOnChatSubmit(BiConsumer<String, String> callback) {
        this.onChatSubmit = callback;
    }

    /**
     * 🆕 设置聊天区域状态
     */
    public void setChatEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            chatInput.setEnabled(enabled);
            chatSendButton.setEnabled(enabled);
        });
    }

    /**
     * 🆕 设置状态文本
     */
    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
        });
    }

    @Override
    protected void doOKAction() {
        if (applyCallback != null) {
            String finalCode = modifiedContent.getDocument().getText();
            applyCallback.accept(finalCode);
        }
        super.doOKAction();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return diffPanel.getPreferredFocusedComponent();
    }

    // ========== 原有方法保持不变 ==========

    public void updateModifiedContent(String newCode) {
        ModalityState modalityState = ModalityState.stateForComponent(getWindow());
        ApplicationManager.getApplication().invokeLater(() -> {
            ApplicationManager.getApplication().runWriteAction(() -> {
                modifiedContent.getDocument().setText(newCode);
                System.out.println("UI 内容已更新。");
            });
        }, modalityState);
    }

    public void clearModifiedContent() {
        updateModifiedContent("");
    }

    public void appendModifiedContent(String textChunk) {
        ModalityState modalityState = ModalityState.stateForComponent(getWindow());
        ApplicationManager.getApplication().invokeLater(() -> {
            ApplicationManager.getApplication().runWriteAction(() -> {
                Document doc = modifiedContent.getDocument();
                doc.insertString(doc.getTextLength(), textChunk);
            });
        }, modalityState);
    }

    public void appendTextChunk(String textChunk) {
        Document doc = modifiedContent.getDocument();
        doc.insertString(doc.getTextLength(), textChunk);
    }

    public void clearText() {
        modifiedContent.getDocument().setText("");
    }
}