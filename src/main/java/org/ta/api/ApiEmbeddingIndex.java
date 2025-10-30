package org.ta.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class ApiEmbeddingIndex {
    private final Map<String, float[]> index = new HashMap<>();
    private final Map<String, String> contentCache = new HashMap<>(); // 缓存实际内容
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private boolean isEnabled = true;

    public ApiEmbeddingIndex(String apiKey) {
        this(apiKey, "BAAI/bge-large-zh-v1.5"); // 默认使用中文模型
    }

    public ApiEmbeddingIndex(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = "https://api.siliconflow.cn/v1/embeddings";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();

        System.out.println("✅ API 嵌入索引初始化: 模型 = " + model);
    }

    public void addDocuments(List<Path> docs) {
        if (!isEnabled) {
            System.out.println("⚠️  API 嵌入未启用");
            return;
        }

        DocumentParser parser = new DocumentParser();
        int totalPages = 0;

        for (Path path : docs) {
            try {
                System.out.println("📄 处理文档: " + path.getFileName());
                List<DocumentParser.PageText> pages = parser.parse(path);

                for (DocumentParser.PageText p : pages) {
                    String docId = path.getFileName() + ":" + p.page;

                    // 获取嵌入向量
                    float[] vec = getEmbedding(p.text);
                    index.put(docId, vec);
                    contentCache.put(docId, p.text); // 缓存内容

                    totalPages++;

                    // 避免 API 限流，稍微延迟
                    Thread.sleep(100);
                }

                System.out.println("✅ 已向量化: " + path.getFileName() + " (" + pages.size() + " 页)");
            } catch (Exception e) {
                System.err.println("⚠️  向量化失败: " + path.getFileName());
                e.printStackTrace();
            }
        }

        System.out.println("📊 向量索引统计: 共 " + totalPages + " 个文档块");
    }

    private float[] getEmbedding(String text) throws Exception {
        // 截断过长的文本（API 通常有长度限制）
        if (text.length() > 2000) {
            text = text.substring(0, 2000);
        }

        // 构建请求体
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", model);
        requestMap.put("input", text);
        requestMap.put("encoding_format", "float");

        String requestBody = mapper.writeValueAsString(requestMap);

        // 发送请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        // 检查响应状态
        if (response.statusCode() != 200) {
            throw new RuntimeException("API 请求失败: " + response.statusCode() + " - " + response.body());
        }

        // 解析响应
        JsonNode root = mapper.readTree(response.body());

        // 检查是否有错误
        if (root.has("error")) {
            throw new RuntimeException("API 返回错误: " + root.get("error").get("message").asText());
        }

        JsonNode embedding = root.path("data").get(0).path("embedding");

        float[] vec = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vec[i] = (float) embedding.get(i).asDouble();
        }

        return vec;
    }

    public List<RetrievedChunk> search(String query, int topK) {
        List<RetrievedChunk> results = new ArrayList<>();

        if (!isEnabled) {
            System.out.println("⚠️  API 向量搜索未启用");
            return results;
        }

        if (index.isEmpty()) {
            System.out.println("⚠️  向量索引为空");
            return results;
        }

        try {
            System.out.println("🔍 向量搜索: " + query);
            float[] qVec = getEmbedding(query);

            for (Map.Entry<String, float[]> entry : index.entrySet()) {
                double sim = cosine(qVec, entry.getValue());
                String[] parts = entry.getKey().split(":");

                // 获取实际内容
                String content = contentCache.getOrDefault(entry.getKey(), "（内容略）");
                // 截取摘要
                String snippet = content.length() > 200 ? content.substring(0, 200) + "..." : content;

                results.add(new RetrievedChunk(parts[0], "p" + parts[1], snippet, sim));
            }

            results.sort(Comparator.comparingDouble(RetrievedChunk::getScore).reversed());
            List<RetrievedChunk> topResults = results.subList(0, Math.min(topK, results.size()));

            System.out.println("✅ 找到 " + topResults.size() + " 个匹配结果");
            for (RetrievedChunk r : topResults) {
                System.out.printf("   📄 %.3f - %s@%s%n", r.getScore(), r.getSourceFile(), r.getPageRange());
            }

            return topResults;

        } catch (Exception e) {
            System.err.println("⚠️  向量搜索失败: " + e.getMessage());
            e.printStackTrace();
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