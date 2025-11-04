package org.ta.ui;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent; // ✨ 引入 DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.util.function.Consumer; // ✨ 引入

public class CodeModificationDialog extends DialogWrapper {
    private final DiffRequestPanel diffPanel;
    // ✨ CHANGE: The callback now accepts the final text as a parameter.
    private final Consumer<String> applyCallback;
    // ✨ CHANGE: We now hold a reference to the editable content.
    private final DocumentContent modifiedContent;

    public CodeModificationDialog(@Nullable Project project, DiffContent originalContent, DocumentContent modifiedContent, Consumer<String> applyCallback) {
        super(project, false);
        this.applyCallback = applyCallback;
        this.modifiedContent = modifiedContent; // Store the editable content

        diffPanel = DiffManager.getInstance().createRequestPanel(project, this.getDisposable(), null);

        // The request now uses the editable DocumentContent for the right side
        SimpleDiffRequest request = new SimpleDiffRequest(
                "AI 代码修改建议 (右侧可编辑)", // Update title
                originalContent,
                this.modifiedContent, // Use the passed-in editable content
                "原始代码",
                "AI 生成的代码"
        );

        diffPanel.setRequest(request);

        setTitle("AI 代码修改审查");
        setOKButtonText("应用修改");
        setCancelButtonText("取消");

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return diffPanel.getComponent();
    }

    @Override
    protected void doOKAction() {
        // ✨ CHANGE: Get the LATEST text from the document and pass it to the callback.
        if (applyCallback != null) {
            // Get the text from the document, which may have been edited by the user.
            String finalCode = modifiedContent.getDocument().getText();
            applyCallback.accept(finalCode); // Use .accept() for a Consumer
        }
        super.doOKAction();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return diffPanel.getPreferredFocusedComponent();
    }

    /**
     * ✨ 最终修正：使用带有 ModalityState 的 invokeLater
     * @param newCode The code returned from the AI.
     */
    public void updateModifiedContent(String newCode) {
        // ✨ 2. 获取与我们对话框关联的当前模态状态
        // getWindow() 返回的是 DialogWrapper 的顶层窗口 (JFrame/JDialog)
        // stateForComponent() 能找到这个窗口当前所属的正确模态上下文
        ModalityState modalityState = ModalityState.stateForComponent(getWindow());

        // ✨ 3. 使用带有 ModalityState 的 invokeLater
        // 这会告诉 IntelliJ：“请把这个任务插入到与我的对话框最相关的那个事件队列中去”
        ApplicationManager.getApplication().invokeLater(() -> {
            // Document 的修改仍然需要在写操作中
            ApplicationManager.getApplication().runWriteAction(() -> {
                modifiedContent.getDocument().setText(newCode);
                System.out.println("UI 内容已更新。"); // 新增日志，确认UI更新代码被执行
            });
        }, modalityState); // ✨ 4. 将获取到的 modalityState 作为参数传入
    }

}