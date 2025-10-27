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

    public ChatPanel() {
        super(new BorderLayout());
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(inputField, BorderLayout.CENTER);
        top.add(sendButton, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(messageArea), BorderLayout.CENTER);

        sendButton.addActionListener(e -> {
            String question = inputField.getText().trim();
            if (question.isEmpty()) {
                return;
            }
            ask(question, Optional.empty());
            inputField.setText("");
        });
    }

    public void ask(String question, Optional<String> codeContext) {
        appendLine("你: " + question);
        Answer answer = service.answer(question, codeContext, 3);
        appendLine("TA: " + answer.getText());
    }

    private void appendLine(String text) {
        if (messageArea.getText().isEmpty()) {
            messageArea.setText(text);
        } else {
            messageArea.append("\n" + text);
        }
    }
}