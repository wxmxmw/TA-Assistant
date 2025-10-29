package org.ta.mock;

import org.ta.api.HybridKnowledgeStore;
import org.ta.api.KnowledgeStore;
import org.ta.api.LuceneKnowledgeStore;
import org.ta.api.RetrievedChunk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class TestIndexer {
    public static void main(String[] args) throws IOException {
        KnowledgeStore store = new HybridKnowledgeStore();

        // 1. 添加 PDF 文档（这里改成你自己的路径）
        store.addDocuments(List.of(
                Path.of("docs/sample1.pdf")

        ));

        // 2. 检索
        List<RetrievedChunk> results = store.search("What is inheritance", 5);

        // 3. 输出
        for (RetrievedChunk r : results) {
            System.out.printf("score=%.2f | file=%s | page=%s%n | content=%s",
                    r.getScore(), r.getSourceFile(), r.getPageRange(), r.getContent());
        }
    }
}
