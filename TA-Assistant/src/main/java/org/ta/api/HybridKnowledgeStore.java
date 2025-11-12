package org.ta.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class HybridKnowledgeStore implements KnowledgeStore {
    private final LuceneKnowledgeStore luceneStore;
    private final ApiEmbeddingIndex embeddingIndex;
    private final double alpha = 0.6; // Lucene 权重 60%, 向量权重 40%

    /**
     * 构造函数 - 初始化混合检索存储
     * @param apiKey 硅基流动 API Key
     */
    public HybridKnowledgeStore(String apiKey) throws IOException {
        // ✅ 初始化 Lucene 存储
        Path indexPath = Path.of("D:\\Grade Three\\Java\\docs\\Lec01.PDF");
        this.luceneStore = new LuceneKnowledgeStore(indexPath);

        // ✅ 初始化 API 嵌入索引，使用 bge-m3 模型
        this.embeddingIndex = new ApiEmbeddingIndex(apiKey, "BAAI/bge-m3");

        System.out.println("✅ 混合知识库初始化完成 (Lucene + API向量)");
    }

    @Override
    public void addDocuments(List<Path> docs) {
        System.out.println("\n========== 开始索引文档 ==========");

        // 1. Lucene 关键词索引（快速）
        System.out.println("📝 [1/2] Lucene 关键词索引中...");
        try {
            luceneStore.addDocuments(docs);
            System.out.println("✅ Lucene 索引完成");
        } catch (Exception e) {
            System.err.println("❌ Lucene 索引失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 2. API 向量索引（较慢，有网络请求）
        System.out.println("\n🌐 [2/2] API 向量索引中（需要几秒钟）...");
        try {
            embeddingIndex.addDocuments(docs);
            System.out.println("✅ API 向量索引完成");
        } catch (Exception e) {
            System.err.println("❌ API 向量索引失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("========== ✅ 文档索引完成 ==========\n");
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        System.out.println("\n========== 开始混合检索 ==========");
        System.out.println("🔍 查询: " + query);
        System.out.println("📊 检索策略: Lucene权重=" + (alpha * 100) + "%, 向量权重=" + ((1-alpha) * 100) + "%");

        // 1. Lucene 关键词检索
        System.out.println("\n[1/2] Lucene 关键词检索...");
        List<RetrievedChunk> luceneResults = new ArrayList<>();
        try {
            luceneResults = luceneStore.search(query, topK);
            System.out.println("   ✅ Lucene 找到 " + luceneResults.size() + " 个结果");
        } catch (Exception e) {
            System.err.println("   ⚠️  Lucene 检索失败: " + e.getMessage());
        }

        // 2. API 向量检索
        System.out.println("\n[2/2] API 向量检索...");
        List<RetrievedChunk> vectorResults = new ArrayList<>();
        try {
            vectorResults = embeddingIndex.search(query, topK);
            System.out.println("   ✅ 向量检索找到 " + vectorResults.size() + " 个结果");
        } catch (Exception e) {
            System.err.println("   ⚠️  向量检索失败: " + e.getMessage());
        }

        // 3. 融合两种检索结果
        System.out.println("\n🔀 融合检索结果...");
        Map<String, RetrievedChunk> merged = new HashMap<>();

        // 添加 Lucene 结果
        for (RetrievedChunk r : luceneResults) {
            String key = key(r);
            merged.put(key, r);
        }

        // 合并向量检索结果（如果已存在则加权平均分数）
        for (RetrievedChunk r : vectorResults) {
            String key = key(r);
            merged.merge(key, r, (luceneChunk, vectorChunk) -> {
                // 加权融合分数: alpha * Lucene分数 + (1-alpha) * 向量分数
                double fusedScore = alpha * luceneChunk.getScore() + (1 - alpha) * vectorChunk.getScore();

                // 使用向量检索的内容（通常更完整）
                return new RetrievedChunk(
                        luceneChunk.getSourceFile(),
                        luceneChunk.getPageRange(),
                        vectorChunk.getContent(),
                        fusedScore
                );
            });
        }

        // 4. 按融合后的分数排序并返回 top-K
        List<RetrievedChunk> finalResults = merged.values().stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        System.out.println("\n========== ✅ 混合检索完成 ==========");
        System.out.println("📊 最终返回 " + finalResults.size() + " 个结果:\n");

        for (int i = 0; i < finalResults.size(); i++) {
            RetrievedChunk r = finalResults.get(i);
            System.out.printf("   [%d] 分数: %.3f | %s@%s%n",
                    i + 1, r.getScore(), r.getSourceFile(), r.getPageRange());
        }
        System.out.println();

        return finalResults;
    }

    /**
     * 生成文档块的唯一键（用于结果融合）
     */
    private String key(RetrievedChunk r) {
        return r.getSourceFile() + ":" + r.getPageRange();
    }
}