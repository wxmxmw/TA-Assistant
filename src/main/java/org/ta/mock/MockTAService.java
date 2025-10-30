package org.ta.mock;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.ta.api.Answer;
import org.ta.api.Citation;
import org.ta.api.HybridKnowledgeStore;
import org.ta.api.KnowledgeStore;
import org.ta.core.RagService;
import org.ta.llm.SiliconLLMClient;

import java.util.Collections;
import java.util.Optional;

/**
 * Mock implementation for prototype: returns a fixed demo reply and logs the question.
 */
public class MockTAService  {
    private static final Logger LOG = Logger.getLogger(MockTAService.class.getName());


    public Answer answer(String question, Optional<String> codeContext, int topK) throws IOException, URISyntaxException {
        // Log to IntelliJ log and standard out for visibility
        LOG.info("UserQuestion: " + question);
        System.out.println("UserQuestion: " + question);
        SiliconLLMClient llmClient=new SiliconLLMClient("sk-vxlhkdlhcupthzbhbsgjodibgplhupnjsmlunudidlipbnut","deepseek-ai/DeepSeek-V3.2-Exp");
        KnowledgeStore store = new HybridKnowledgeStore("sk-vxlhkdlhcupthzbhbsgjodibgplhupnjsmlunudidlipbnut");

        // 1. 添加 PDF 文档（这里改成你自己的路径）

        Path docPath = Path.of("D:/Grade Three/Java/TA-Assistant/src/main/resources/docs/sample1.PDF");
        store.addDocuments(List.of(docPath));




        RagService ragService=new RagService(store,llmClient);
        Answer reply=ragService.answer(question, codeContext, topK);


        return reply;
    }
}