package org.ta.actions;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.ta.ui.ChatPanel;

import java.util.Optional;

public class AskSelectedCodeAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean hasSelection = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabled(hasSelection);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null || !editor.getSelectionModel().hasSelection()) return;

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) return;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("TA Chat");
        if (toolWindow != null) {
            toolWindow.activate(() -> {
                Content[] contents = toolWindow.getContentManager().getContents();
                for (Content content : contents) {
                    if (content.getComponent() instanceof ChatPanel panel) {
                        // 以 文件名:起止行 的形式作为上下文标题
                        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
                        String fileName = (vf != null) ? vf.getName() : "当前文件";
                        int startOffset = editor.getSelectionModel().getSelectionStart();
                        int endOffset = editor.getSelectionModel().getSelectionEnd();
                        int startLine = editor.getDocument().getLineNumber(startOffset) + 1;
                        int endLine = editor.getDocument().getLineNumber(endOffset) + 1;
                        String title = fileName + ":" + startLine + "-" + endLine;
                        // Stash context instead of sending immediately
                        panel.addContext(title, selectedText);
                        break;
                    }
                }
            });
        }

        NotificationGroupManager.getInstance()
                .getNotificationGroup("TA Notifications")
                .createNotification("已暂存选中代码为上下文", NotificationType.INFORMATION)
                .notify(project);
    }
}