package org.ta.mock;

import org.ta.api.KnowledgeStore;
import org.ta.api.LuceneKnowledgeStore;
import org.ta.api.RetrievedChunk;

import java.nio.file.Path;
import java.util.List;

public class TestIndexer {
    public static void main(String[] args) {
        KnowledgeStore store = new LuceneKnowledgeStore();

        // 1. 添加 PDF 文档（这里改成你自己的路径）
        store.addDocuments(List.of(
                Path.of("docs/sample1.pdf")
        ));

        // 2. 检索
        List<RetrievedChunk> results = store.search("inheritance", 5);

        // 3. 输出
        for (RetrievedChunk r : results) {
            System.out.printf("score=%.2f | file=%s | page=%s%n",
                    r.getScore(), r.getSourcePath(), r.getPageRange());
        }
    }
}
