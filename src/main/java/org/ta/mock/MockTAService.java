package org.ta.mock;

import java.util.logging.Logger;
import org.ta.api.Answer;
import org.ta.api.Citation;
import org.ta.api.RagService;

import java.util.Collections;
import java.util.Optional;

/**
 * Mock implementation for prototype: returns a fixed demo reply and logs the question.
 */
public class MockTAService implements RagService {
    private static final Logger LOG = Logger.getLogger(MockTAService.class.getName());

    @Override
    public Answer answer(String question, Optional<String> codeContext, int topK) {
        // Log to IntelliJ log and standard out for visibility
        LOG.info("UserQuestion: " + question);
        System.out.println("UserQuestion: " + question);

        String reply = "这是一个演示回复：我们已收到你的问题，稍后将接入课程材料。";

        return new Answer(
                reply,
                Collections.<Citation>emptyList(),
                "general_knowledge"
        );
    }
}