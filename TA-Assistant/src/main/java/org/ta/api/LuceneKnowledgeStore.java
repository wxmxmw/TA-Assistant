package org.ta.api;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory;


import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of KnowledgeStore using Lucene 9.x + PDFBox
 */
public class LuceneKnowledgeStore implements KnowledgeStore {
    // ✅ Lucene 9.x 支持 RAMDirectory 或 FSDirectory，两者皆可
    private final Directory directory;

    public LuceneKnowledgeStore(Path indexPath) throws IOException {
        this.directory = new ByteBuffersDirectory();
    }

    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final DocumentParser parser = new DocumentParser();

    @Override
    public void addDocuments(List<Path> docs) {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        // ✅ Lucene 9.x 默认使用新的索引格式，可以显式设置 open mode
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (Path path : docs) {
                List<DocumentParser.PageText> pages = parser.parse(path);

                for (DocumentParser.PageText page : pages) {
                    Document doc = new Document();
                    doc.add(new TextField("text", page.text, Field.Store.YES));
                    doc.add(new StringField("sourcePath", page.fileName, Field.Store.YES));
                    doc.add(new StringField("pageRange", "p" + page.page, Field.Store.YES));
                    writer.addDocument(doc);
                }

                System.out.println("✅ Indexed: " + path.getFileName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<RetrievedChunk> search(String queryStr, int topK) {
        List<RetrievedChunk> results = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // ✅ Lucene 9.x QueryParser 依然可用（classic 包未移除）
            QueryParser parser = new QueryParser("text", analyzer);
            Query query = parser.parse(queryStr);

            TopDocs topDocs = searcher.search(query, topK);

            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document d = searcher.doc(sd.doc);
                String text = d.get("text");
                String file = d.get("sourcePath");
                String page = d.get("pageRange");
                double score = sd.score;

                results.add(new RetrievedChunk(file, page, text, score));
                System.out.printf("🔍 Match: [%s@%s]  score=%.3f%n", file, page, score);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
}
