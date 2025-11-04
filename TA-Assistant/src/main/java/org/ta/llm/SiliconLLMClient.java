package org.ta.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ta.api.LLMClient;
import org.ta.llm.models.ChatMessage;
import org.ta.llm.models.SiliconFlowRequest;
import org.ta.llm.models.SiliconFlowResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.ta.llm.models.StreamChunk; // 引入新模型
import java.util.function.Consumer;
import java.io.BufferedReader; // ✨ 1. 添加这一行
import java.io.InputStreamReader; // ✨ 2. 添加这一行
/**
 * An LLMClient implementation for the SiliconFlow API.
 */
public class SiliconLLMClient implements LLMClient {

    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a client to interact with the SiliconFlow API.
     *
     * @param apiKey Your SiliconFlow API key.
     * @param model  The specific model you want to use (e.g., "deepseek-v2-chat").
     */
    public SiliconLLMClient(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be null or empty.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name cannot be null or empty.");
        }

        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String generate(String prompt) {
        try {
            // 1. Create the request payload
            List<ChatMessage> messages = Collections.singletonList(new ChatMessage("user", prompt));
            SiliconFlowRequest apiRequest = new SiliconFlowRequest(this.model, messages);

            // Set other parameters if needed, e.g., temperature
            // apiRequest.setTemperature(0.8);

            String requestBody = objectMapper.writeValueAsString(apiRequest);

            // 2. Build the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + this.apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // 3. Send the request and get the response
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 4. Check for errors
            if (response.statusCode() != 200) {
                throw new RuntimeException("API call failed with status code " + response.statusCode() + ": " + response.body());
            }

            // 5. Deserialize the response and extract the content
            SiliconFlowResponse apiResponse = objectMapper.readValue(response.body(), SiliconFlowResponse.class);
            String reply = apiResponse.getReplyContent();

            if (reply == null) {
                throw new RuntimeException("Received an empty reply from the API.");
            }

            return reply;

        } catch (Exception e) {
            // Wrap exceptions in a single runtime exception for easier handling by the caller
            throw new RuntimeException("Failed to call SiliconFlow API", e);
        }
    }
    /**
     * 新增：以流式方式调用 API 并处理响应
     * @param prompt 用户的提示
     * @param onChunkReceived 一个回调函数，每当收到一个新的文本块时就会被调用
     * @param onComplete 当流结束时调用的回调
     * @param onError 当发生错误时调用的回调
     */
// 在 org.ta.llm.SiliconLLMClient.java 中

    public void generateStream(String prompt, Consumer<String> onChunkReceived, Runnable onComplete, Consumer<Exception> onError) {
        try {
            SiliconFlowRequest apiRequest = new SiliconFlowRequest(
                    this.model,
                    Collections.singletonList(new ChatMessage("user", prompt))
            );
            apiRequest.setStream(true);

            String requestBody = objectMapper.writeValueAsString(apiRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + this.apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new RuntimeException("API call failed with status code " + response.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // ✨ DEBUGGING LOG 1: 打印从服务器收到的每一行原始数据
                    System.out.println("RAW <-- " + line);

                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.equals("[DONE]")) {
                            break;
                        }

                        try {
                            StreamChunk chunk = objectMapper.readValue(data, StreamChunk.class);
                            String content = chunk.getContent();

                            // ✨ DEBUGGING LOG 2: 打印解析后的内容
                            System.out.println("PARSED DATA: " + data + " -> CONTENT: " + content);

                            if (content != null) { // 注意：这里我们不过滤空字符串""
                                onChunkReceived.accept(content);
                            } else {
                                // ✨ DEBUGGING LOG 3: 记录收到但内容为空的块
                                System.out.println("Parsed chunk, but content is null.");
                            }
                        } catch (Exception parseException) {
                            // ✨ DEBUGGING LOG 4: 捕获并打印JSON解析错误
                            System.err.println("Failed to parse JSON chunk: " + data);
                            parseException.printStackTrace();
                        }
                    }
                }
            }
            onComplete.run();
        } catch (Exception e) {
            onError.accept(e);
        }
    }
}