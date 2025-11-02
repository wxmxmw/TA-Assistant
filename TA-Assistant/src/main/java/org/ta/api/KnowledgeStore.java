package org.ta.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for course material storage and retrieval.
 * A implements this with real parsing and indexing later.
 */
public interface KnowledgeStore {
    void addDocuments(List<Path> docs);

    List<RetrievedChunk> search(String query, int topK);
}