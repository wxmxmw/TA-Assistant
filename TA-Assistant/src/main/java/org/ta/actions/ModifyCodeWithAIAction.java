package org.ta.actions;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.ta.core.AIModificationUtil;
import org.ta.ui.CodeModificationDialog;
import org.ta.ui.UIAIModel;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModifyCodeWithAIAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabled(editor != null && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) return;

        SelectionModel selectionModel = editor.getSelectionModel();
        String originalCode = selectionModel.getSelectedText();
        if (originalCode == null || originalCode.isBlank()) return;

        showInlineInputPanel(project, editor, originalCode);
    }

    /**
     * 显示输入框（带模型选择）
     */
    private void showInlineInputPanel(Project project, Editor editor, String originalCode) {
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setPreferredSize(new Dimension(600, 150));

        // ========== 顶部：模型选择 ==========
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JBLabel modelLabel = new JBLabel("模型:");

        JComboBox<UIAIModel> modelComboBox = new JComboBox<>(UIAIModel.values());
        modelComboBox.setPreferredSize(new Dimension(200, 28));
        modelComboBox.setSelectedItem(UIAIModel.DEEPSEEK); // 默认模型

        // 自定义渲染器
        modelComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof UIAIModel) {
                    UIAIModel model = (UIAIModel) value;
                    setText(model.getDisplayName());
                    if (index >= 0) {
                        setToolTipText(model.getDescription());
                    }
                }
                return this;
            }
        });

        topPanel.add(modelLabel);
        topPanel.add(modelComboBox);

        // ========== 中间：输入框 ==========
        JBTextField inputField = new JBTextField();
        inputField.getEmptyText().setText("描述你想要的修改或提出问题...");

        // ========== 底部：按钮 ==========
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton sendButton = new JButton("发送");
        JButton cancelButton = new JButton("取消");

        buttonPanel.add(cancelButton);
        buttonPanel.add(sendButton);

        // ========== 组装 ==========
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(inputField, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // ========== 创建弹窗 ==========
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(mainPanel, inputField)
                .setRequestFocus(true)
                .setTitle("AI 代码助手")
                .setResizable(true)
                .setMovable(true)
                .createPopup();

        // ========== 按钮事件 ==========
        Runnable submitAction = () -> {
            String instruction = inputField.getText().trim();
            UIAIModel selectedModel = (UIAIModel) modelComboBox.getSelectedItem();

            if (!instruction.isEmpty() && selectedModel != null) {
                popup.cancel();
                showAsynchronousDiff(project, editor, originalCode, instruction, selectedModel);
            }
        };

        sendButton.addActionListener(evt -> submitAction.run());
        cancelButton.addActionListener(evt -> popup.cancel());
        inputField.addActionListener(evt -> submitAction.run());

        popup.showInBestPositionFor(editor);
    }

    /**
     * 显示 Diff 对话框并流式生成代码
     */
    private void showAsynchronousDiff(Project project, Editor editor, String originalCode,
                                      String userInstruction, UIAIModel selectedModel) {
        // 1. 准备 Diff 窗口的初始内容
        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        DiffContent originalContent = contentFactory.create(project, originalCode);

        // 2. 右侧面板的初始内容是"正在思考..."
        String loadingText = "正在思考，请稍候...";
        Document editableDocument = EditorFactory.getInstance().createDocument(loadingText);
        DocumentContent modifiedContent = contentFactory.create(project, editableDocument);

        // 3. 定义回调
        Consumer<String> applyChangesCallback = (finalCode) -> applyChanges(project, editor, finalCode);

        CodeModificationDialog dialog = new CodeModificationDialog(
                project,
                originalContent,
                modifiedContent,
                applyChangesCallback
        );

        // 🆕 设置多轮对话回调
        dialog.setOnChatSubmit((nextInstruction, currentCode) -> {
            // 用户输入新指令，基于当前右侧代码继续修改
            continueModification(project, nextInstruction, currentCode, selectedModel, dialog,editableDocument);
        });

        final AtomicBoolean isFirstChunk = new AtomicBoolean(true);

        ProgressManager.getInstance().run(new Task.Backgroundable(
                project,
                "正在使用 " + selectedModel.getDisplayName() + " 生成代码...") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // API Key（实际应用中应该从配置中读取）
                String apiKey = "sk-vxlhkdlhcupthzbhbsgjodibgplhupnjsmlunudidlipbnut";

                // 定义流式回调
                Consumer<String> onChunk = (chunk) -> {
                    SwingUtilities.invokeLater(() -> {
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            if (!chunk.isEmpty() && isFirstChunk.getAndSet(false)) {
                                dialog.clearText();
                            }
                            dialog.appendTextChunk(chunk);
                        });
                    });
                };

                Runnable onComplete = () -> {
                    System.out.println("✅ AI 流式输出完成。");
                    SwingUtilities.invokeLater(() -> {
                        // 🔥 核心修复：在完成时对整个文档进行一次最终清洗
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            // 1. 获取当前生成的所有文本
                            Document doc = modifiedContent.getDocument();
                            String rawText = doc.getText();

                            // 2. 调用工具类清洗（去除首尾的 ```java 和 ```）
                            String cleanedText = AIModificationUtil.cleanCodeBlock(rawText);

                            // 3. 如果清洗后内容有变化，更新文档
                            if (!rawText.equals(cleanedText)) {
                                doc.setText(cleanedText);
                            }
                        });

                        dialog.setChatEnabled(true);
                        dialog.setStatus(isFirstChunk.get() ? "⚠ 无内容生成" : "");
                    });

                    if (isFirstChunk.get()) {
                        SwingUtilities.invokeLater(() -> {
                            WriteCommandAction.runWriteCommandAction(project, () -> {
                                dialog.updateModifiedContent("// AI 返回了空内容");
                            });
                        });
                    }
                };

                Consumer<Exception> onError = (e) -> {
                    System.err.println("❌ AI 流式输出错误: " + e.getMessage());
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        dialog.setChatEnabled(true);
                        dialog.setStatus("✗ 生成失败");
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            dialog.updateModifiedContent("// AI 修改失败: " + e.getMessage());
                        });
                    });
                };

                // 调用 AIModificationUtil 的流式方法
                indicator.setText("正在使用 " + selectedModel.getDisplayName() + " 处理...");
                AIModificationUtil.modifyCodeStream(
                        userInstruction,
                        originalCode,
                        apiKey,
                        selectedModel.getBackendModel(),
                        onChunk,
                        onComplete,
                        onError
                );
            }
        });

        dialog.show();
    }

    /**
     * 🆕 多轮对话：继续修改代码
     */
    /**
     * 🆕 多轮对话：继续修改代码
     * 增加了一个参数 editableDocument，用于在最后进行全文清洗
     */
    private void continueModification(Project project, String nextInstruction, String currentCode,
                                      UIAIModel selectedModel, CodeModificationDialog dialog,
                                      Document editableDocument) { // 👈 新增这个参数

        final AtomicBoolean isFirstChunk = new AtomicBoolean(true);

        ProgressManager.getInstance().run(new Task.Backgroundable(
                project,
                "正在使用 " + selectedModel.getDisplayName() + " 继续修改...") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // 你的 API Key
                String apiKey = "sk-vxlhkdlhcupthzbhbsgjodibgplhupnjsmlunudidlipbnut";

                Consumer<String> onChunk = (chunk) -> {
                    SwingUtilities.invokeLater(() -> {
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            if (!chunk.isEmpty() && isFirstChunk.getAndSet(false)) {
                                dialog.clearText();
                            }
                            dialog.appendTextChunk(chunk);
                        });
                    });
                };

                // 🔥 核心修改在这里
                Runnable onComplete = () -> {
                    System.out.println("✅ 多轮对话完成。");

                    SwingUtilities.invokeLater(() -> {
                        // 1. 在 WriteCommandAction 中进行最终清洗
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            String rawText = editableDocument.getText();
                            // 调用工具类清洗
                            String cleanedText = AIModificationUtil.cleanCodeBlock(rawText);

                            // 如果有变化（比如去掉了 ```），则更新文档
                            if (!rawText.equals(cleanedText)) {
                                editableDocument.setText(cleanedText);
                            }
                        });

                        // 2. 恢复 UI 状态
                        dialog.setChatEnabled(true);
                        dialog.setStatus("✓ 生成完成");
                    });

                    // 处理空内容的边缘情况
                    if (isFirstChunk.get()) {
                        SwingUtilities.invokeLater(() -> {
                            WriteCommandAction.runWriteCommandAction(project, () -> {
                                dialog.updateModifiedContent("// AI 返回了空内容");
                            });
                        });
                    }
                };

                Consumer<Exception> onError = (e) -> {
                    System.err.println("❌ 多轮对话错误: " + e.getMessage());
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        dialog.setChatEnabled(true);
                        dialog.setStatus("✗ 生成失败");
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            dialog.updateModifiedContent("// AI 修改失败: " + e.getMessage());
                        });
                    });
                };

                // 调用 AIModificationUtil，使用当前代码作为基础
                indicator.setText("正在使用 " + selectedModel.getDisplayName() + " 处理...");
                AIModificationUtil.modifyCodeStream(
                        nextInstruction,
                        currentCode,
                        apiKey,
                        selectedModel.getBackendModel(),
                        onChunk,
                        onComplete,
                        onError
                );
            }
        });
    }

    /**
     * 应用修改到编辑器
     */
    private void applyChanges(Project project, Editor editor, String newText) {
        SelectionModel selectionModel = editor.getSelectionModel();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            editor.getDocument().replaceString(
                    selectionModel.getSelectionStart(),
                    selectionModel.getSelectionEnd(),
                    newText
            );
        });
    }
}