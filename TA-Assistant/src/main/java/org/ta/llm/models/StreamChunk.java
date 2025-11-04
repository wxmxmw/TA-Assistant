// 可以在 org.ta.llm.models 包下创建
package org.ta.llm.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamChunk {
    public List<Choice> choices;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        public Delta delta;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        public String content;
    }

    // 辅助方法，用于方便地获取内容
    public String getContent() {
        if (choices != null && !choices.isEmpty() && choices.get(0).delta != null) {
            return choices.get(0).delta.content;
        }
        return null;
    }
}