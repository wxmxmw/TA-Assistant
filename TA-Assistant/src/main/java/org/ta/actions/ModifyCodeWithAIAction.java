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
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.ta.api.ITAService;
import org.ta.core.AIModificationUtil;
import org.ta.core.TAService;
import org.ta.ui.CodeModificationDialog;

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

        // 1. 创建一个自定义的 Panel 作为 Popup 的内容
        JPanel panel = new JPanel(new BorderLayout());
        JBTextField instructionField = new JBTextField("请输入你的代码修改指令...");
        panel.add(instructionField, BorderLayout.CENTER);

        // 2. 使用 JBPopupFactory 创建 Popup
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, instructionField)
                .setTitle("AI 代码修改")
                .setMovable(true)
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .setAdText("按 Enter 键提交")
                .createPopup();

        // 3. 为输入框添加一个 Action Listener，当用户按 Enter 时触发
        instructionField.addActionListener(event -> {
            popup.closeOk(null); // 关闭 popup
            String instruction = instructionField.getText();
            if (instruction != null && !instruction.isBlank()) {
                // 4. 用户提交后，在这里启动异步的 Diff 流程
                showAsynchronousDiff(project, editor, originalCode, instruction);
            }
        });

        // 5. 显示 Popup，actionPerformed 方法的职责到此结束
        popup.showInBestPositionFor(editor);
    }

    private void showAsynchronousDiff(Project project, Editor editor, String originalCode, String instruction) {
        // 1. 准备 Diff 窗口的初始内容
        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        DiffContent originalContent = contentFactory.create(project, originalCode);

        // 2. 右侧面板的初始内容是“正在思考...”
        String loadingText = "正在思考，请稍候...";
        Document editableDocument = EditorFactory.getInstance().createDocument(loadingText);
        DocumentContent modifiedContent = contentFactory.create(project, editableDocument);

        // 3. 定义回调
        Consumer<String> applyChangesCallback = (finalCode) -> applyChanges(project, editor, finalCode);

        CodeModificationDialog dialog = new CodeModificationDialog(project, originalContent, modifiedContent, applyChangesCallback);

        final AtomicBoolean isFirstChunk = new AtomicBoolean(true);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在与 AI 建立连接...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                String apiKey = "sk-vxlhkdlhcupthzbhbsgjodibgplhupnjsmlunudidlipbnut";

                Consumer<String> onChunk = (chunk) -> {
                    // ✨ 核心修正：使用 SwingUtilities.invokeLater 和 runWriteAction 统一处理UI更新
                    SwingUtilities.invokeLater(() -> {
                        // 在写操作中修改文档
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            if (!chunk.isEmpty() && isFirstChunk.getAndSet(false)) {
                                dialog.clearText(); // 调用简化的清空方法
                            }
                            dialog.appendTextChunk(chunk); // 调用简化的追加方法
                        });
                    });
                };

                Runnable onComplete = () -> {
                    System.out.println("AI 流式输出完成。");
                    if (isFirstChunk.get()) {
                        SwingUtilities.invokeLater(() -> {
                            WriteCommandAction.runWriteCommandAction(project, () -> {
                                dialog.updateModifiedContent("// AI 返回了空内容");
                            });
                        });
                    }
                };

                Consumer<Exception> onError = (e) -> {
                    System.err.println("AI 流式输出错误: " + e.getMessage());
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            dialog.updateModifiedContent("// AI 修改失败: " + e.getMessage());
                        });
                    });
                };

                AIModificationUtil.modifyCodeStream(instruction, originalCode, apiKey, onChunk, onComplete, onError);
            }
        });

        dialog.show();


    }
    private void applyChanges(Project project, Editor editor, String newText) {
        Document document = editor.getDocument();
        SelectionModel selectionModel = editor.getSelectionModel();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.replaceString(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), newText);
        });
    }
}