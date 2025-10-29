package org.ta.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class HybridKnowledgeStore implements KnowledgeStore {
    private final LuceneKnowledgeStore luceneStore;
    private final EmbeddingIndex embeddingIndex = new EmbeddingIndex();
    private final double alpha = 0.6;

    public HybridKnowledgeStore() throws IOException {
        Path indexPath = Path.of("C:/Users/LENOVO/IdeaProjects/TA-Assistant/index");
        this.luceneStore = new LuceneKnowledgeStore(indexPath);
    }

    @Override
    public void addDocuments(List<Path> docs) {
        luceneStore.addDocuments(docs);
        embeddingIndex.addDocuments(docs);
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        List<RetrievedChunk> luceneResults = luceneStore.search(query, topK);
        List<RetrievedChunk> vectorResults = embeddingIndex.search(query, topK);

        // 按 sourceFile + pageRange 进行融合（简单平均或加权）
        Map<String, RetrievedChunk> merged = new HashMap<>();

        for (RetrievedChunk r : luceneResults) {
            merged.put(key(r), r);
        }

        for (RetrievedChunk r : vectorResults) {
            merged.merge(key(r), r, (a, b) ->
                    new RetrievedChunk(
                            a.getSourceFile(),
                            a.getPageRange(),
                            a.getContent(),
                            alpha * a.getScore() + (1 - alpha) * b.getScore()
                    )
            );
        }

        // 按融合后的得分排序
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private String key(RetrievedChunk r) {
        return r.getSourceFile() + ":" + r.getPageRange();
    }
}
