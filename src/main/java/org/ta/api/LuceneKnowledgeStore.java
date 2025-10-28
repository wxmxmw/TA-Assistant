package org.ta.api;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.RAMDirectory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of KnowledgeStore using Lucene + PDFBox
 */
public class LuceneKnowledgeStore implements KnowledgeStore {
    private final RAMDirectory directory = new RAMDirectory();
    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final DocumentParser parser = new DocumentParser();

    @Override
    public void addDocuments(List<Path> docs) {
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            for (Path path : docs) {
                List<DocumentParser.PageText> pages = parser.parsePdfByPage(path);

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
            Query query = new QueryParser("text", analyzer).parse(queryStr);
            TopDocs topDocs = searcher.search(query, topK);

            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document d = searcher.doc(sd.doc);
                String text = d.get("text");
                String file = d.get("sourcePath");
                String page = d.get("pageRange");
                double score = sd.score;

                results.add(new RetrievedChunk(text, file, page, score));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
}
