package org.ta.api;

import ai.djl.Application;
import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

import java.nio.file.Path;
import java.util.*;

public class EmbeddingIndex {
    private final Map<String, float[]> index = new HashMap<>();
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private boolean isEnabled = false;

    public EmbeddingIndex() {
        try {
            System.out.println("正在加载向量嵌入模型...");
            Criteria<String, float[]> criteria = Criteria.builder()
                    .optApplication(Application.NLP.TEXT_EMBEDDING)
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2")
                    .build();
            model = criteria.loadModel();
            predictor = model.newPredictor();
            isEnabled = true;
            System.out.println("✅ 向量嵌入模型加载成功");
        } catch (Exception e) {
            System.err.println("⚠️  向量嵌入模型加载失败，将禁用向量搜索功能");
            System.err.println("   原因: " + e.getMessage());
            System.err.println("   提示: 这不影响关键词搜索功能");
            isEnabled = false;
            predictor = null;
            model = null;
        }
    }

    public void addDocuments(List<Path> docs) {
        if (!isEnabled) {
            System.out.println("⚠️  向量嵌入未启用，跳过文档向量化");
            return;
        }

        // 直接解析文档内容
        DocumentParser parser = new DocumentParser();
        for (Path path : docs) {
            try {
                List<DocumentParser.PageText> pages = parser.parse(path);
                for (DocumentParser.PageText p : pages) {
                    float[] vec = predictor.predict(p.text);
                    index.put(path.getFileName() + ":" + p.page, vec);
                }
                System.out.println("✅ 已向量化: " + path.getFileName());
            } catch (Exception e) {
                System.err.println("⚠️  向量化失败: " + path.getFileName() + " - " + e.getMessage());
            }
        }
    }

    public List<RetrievedChunk> search(String query, int topK) {
        List<RetrievedChunk> results = new ArrayList<>();

        if (!isEnabled) {
            System.out.println("⚠️  向量搜索未启用，返回空结果");
            return results;
        }

        if (index.isEmpty()) {
            System.out.println("⚠️  向量索引为空，没有可搜索的文档");
            return results;
        }

        try {
            float[] qVec = predictor.predict(query);
            for (Map.Entry<String, float[]> entry : index.entrySet()) {
                double sim = cosine(qVec, entry.getValue());
                String[] parts = entry.getKey().split(":");
                results.add(new RetrievedChunk(parts[0], "p" + parts[1], "（内容略）", sim));
            }
            results.sort(Comparator.comparingDouble(RetrievedChunk::getScore).reversed());
            return results.subList(0, Math.min(topK, results.size()));
        } catch (TranslateException e) {
            System.err.println("⚠️  向量搜索失败: " + e.getMessage());
            return results;
        }
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public boolean isEnabled() {
        return isEnabled;
    }
}