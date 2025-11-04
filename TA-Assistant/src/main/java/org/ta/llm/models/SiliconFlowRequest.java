package org.ta.llm.models;
import java.util.List;
// API请求类
public class SiliconFlowRequest {
    private String model;
    private List<ChatMessage> messages;
    private Double temperature;
    private Integer maxTokens;
    private boolean stream = false; // 默认不开启流式


    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }


    public SiliconFlowRequest() {}

    public SiliconFlowRequest(String model, List<ChatMessage> messages) {
        this.model = model;
        this.messages = messages;
        this.temperature = 0.7;
        this.maxTokens = 10000;
    }

    // getter和setter
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
}
