package org.ta.mock;

import org.ta.api.LuceneKnowledgeStore;

import java.nio.file.Path;
import java.util.List;

public class LuceneTest {
    public static void main(String[] args) throws Exception {
        Path indexPath = Path.of("C:/Users/LENOVO/IdeaProjects/TA-Assistant/index");
        LuceneKnowledgeStore store = new LuceneKnowledgeStore(indexPath);
        store.addDocuments(List.of(
                Path.of("C:/Users/LENOVO/IdeaProjects/TA-Assistant/src/main/resources/docs/sample1.pdf")
        ));
        store.search("inheritance", 5);
    }
}
