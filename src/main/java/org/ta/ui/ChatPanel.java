package org.ta.ui;

import org.ta.api.Answer;
import org.ta.mock.MockTAService;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

/**
 * Simple chat panel for prototype: input, send button, and message display.
 */
public class ChatPanel extends JPanel {
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("发送");
    private final JTextArea messageArea = new JTextArea();
    private final MockTAService service = new MockTAService();
    // Context accumulation UI and storage
    private final JPanel contextsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private final java.util.List<ContextItem> contexts = new java.util.ArrayList<>();

    public ChatPanel() {
        super(new BorderLayout());
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(inputField, BorderLayout.CENTER);
        top.add(sendButton, BorderLayout.EAST);
        // contexts chips row sits below the input row
        contextsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(contextsPanel, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(messageArea), BorderLayout.CENTER);

        sendButton.addActionListener(e -> {
            String question = inputField.getText().trim();
            if (question.isEmpty()) {
                return;
            }
            // Aggregate contexts into one string and send
            Optional<String> aggregated = buildAggregatedContextOptional();
            ask(question, aggregated);
            // Clear contexts after sending
            clearContexts();
            inputField.setText("");
        });
    }

    public void ask(String question, Optional<String> codeContext) {
        // Print full aggregated context to console if present
        codeContext.ifPresent(ctx -> {
            System.out.println("上下文全文:\n" + ctx);
        });
        if (!contexts.isEmpty()) {
            StringBuilder titles = new StringBuilder();
            for (int i = 0; i < contexts.size(); i++) {
                if (i > 0) titles.append(", ");
                titles.append(contexts.get(i).title);
            }
            appendLine("上下文: " + titles);
        }
        appendLine("你: " + question);
        Answer answer = service.answer(question, codeContext, 3);
        appendLine("TA: " + answer.getText());
    }

    // Add a selected code context (title shown as chip, text stored for prompt)
    public void addContext(String title, String text) {
        if (title == null || title.isBlank() || text == null || text.isBlank()) {
            return;
        }
        contexts.add(new ContextItem(title, text));
        JLabel chip = createChipLabel(title);
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
            if (i < contexts.size() - 1) {
                sb.append("\n---\n\n");
            }
        }
        return Optional.of(sb.toString());
    }

    private void clearContexts() {
        contexts.clear();
        contextsPanel.removeAll();
        contextsPanel.revalidate();
        contextsPanel.repaint();
    }

    private void appendLine(String text) {
        if (messageArea.getText().isEmpty()) {
            messageArea.setText(text);
        } else {
            messageArea.append("\n" + text);
        }
    }

    private JLabel createChipLabel(String title) {
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