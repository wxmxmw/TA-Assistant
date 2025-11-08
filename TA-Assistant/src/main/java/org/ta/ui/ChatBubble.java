package org.ta.ui;

import javax.swing.*;
import java.awt.*;

public class ChatBubble extends JPanel {
    public enum Type { USER, ASSISTANT }

    private static final int MAX_WIDTH = 360;

    public ChatBubble(String message, Type type) {
        setLayout(new BorderLayout());
        setOpaque(false);

        // ====== 文本内容 ======
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFont(new Font("SansSerif", Font.PLAIN, 14));
        text.setForeground(Color.BLACK);

        // 估算文本宽度
        int width = estimateTextWidth(text);
        int bubbleWidth = Math.min(width + 5, MAX_WIDTH);
        text.setSize(new Dimension(bubbleWidth, Short.MAX_VALUE));

        //颜色与圆角
        Color bg = (type == Type.USER) ? new Color(144, 238, 144) : Color.WHITE;
        Color border = (type == Type.USER) ? new Color(120, 200, 120) : new Color(220, 223, 230);

        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.add(text, BorderLayout.CENTER);
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        //包裹层控制左右贴边
        JPanel wrapper = new JPanel(new FlowLayout(
                type == Type.USER ? FlowLayout.RIGHT : FlowLayout.LEFT,
                0, 0
        ));
        wrapper.setOpaque(false);
        wrapper.add(bubble);

        //ChatBubble 自身边距控制
        if (type == Type.USER) {
            setBorder(BorderFactory.createEmptyBorder(2, 100, 2, 8)); // 左宽右窄
            add(wrapper, BorderLayout.EAST);
        } else {
            setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 100)); // 右宽左窄
            add(wrapper, BorderLayout.WEST);
        }
    }

    //根据文字长度估算宽度
    private int estimateTextWidth(JTextArea text) {
        FontMetrics fm = text.getFontMetrics(text.getFont());
        int max = 0;
        for (String line : text.getText().split("\n")) {
            max = Math.max(max, fm.stringWidth(line));
        }
        return max;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = getPreferredSize();
        d.width = MAX_WIDTH + 20;
        return d;
    }
}