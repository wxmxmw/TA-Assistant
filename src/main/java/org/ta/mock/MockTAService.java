package org.ta.mock;

import java.util.logging.Logger;
import org.ta.api.Answer;
import org.ta.api.Citation;
import org.ta.api.LLMClient;
import org.ta.api.RagService;
import org.ta.llm.SiliconLLMClient;

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
        SiliconLLMClient llmClient=new SiliconLLMClient("sk-vxlhkdlhcupthzbhbsgjodibgplhupnjsmlunudidlipbnut","deepseek-ai/DeepSeek-V3.2-Exp");

        String reply = llmClient.generate(question);


        return new Answer(
                reply,
                Collections.<Citation>emptyList(),
                true
        );
    }
}