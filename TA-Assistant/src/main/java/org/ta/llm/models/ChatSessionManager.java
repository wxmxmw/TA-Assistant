package org.ta.llm.models;

import org.ta.llm.models.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮对话上下文管理器（复用现有 ChatMessage）
 */
public class ChatSessionManager {

    private final List<ChatMessage> history = new ArrayList<>();

    /** 添加用户消息 */
    public void addUserMessage(String text) {
        history.add(new ChatMessage("user", text));
    }
    public String getLastUserMessage() {
        if (history.isEmpty()) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            var msg = history.get(i);
            // 假设 Message 对象有个 getRole() 方法返回 "user" / "assistant"
            if ("user".equalsIgnoreCase(msg.getRole())) {
                return msg.getContent();
            }
        }
        return null;
    }


    /** 添加 AI 回复 */
    public void addAssistantMessage(String text) {
        history.add(new ChatMessage("assistant", text));
    }

    /** 清空对话记录 */
    public void clear() {
        history.clear();
    }

    /** 返回完整历史（供 LLM 使用） */
    public List<ChatMessage> getHistory() {
        return history;
    }

    /** 构造完整 Prompt */
    public String buildPrompt() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : history) {
            sb.append(m.getRole().equals("user") ? "User: " : "AI: ");
            sb.append(m.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
