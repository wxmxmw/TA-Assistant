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
import java.time.Duration;
import java.util.Collections;
import java.util.List;

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
}