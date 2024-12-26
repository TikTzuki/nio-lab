package com.tik;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.Serializable;
import java.util.List;

@Slf4j
@Builder
@Data
public class CompletionBuilder implements Serializable {
    public static final String URL = "https://api.openai.com";
    public static final ObjectMapper objectMapper = new ObjectMapper();
    String apiKey;
    Body body;

    public String execute() throws JsonProcessingException {
        OkHttpClient client = new OkHttpClient()
                .newBuilder()
                .build();
        String body = objectMapper.writeValueAsString(getBody());
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.getBytes()))
                .build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("Failed to connect to OpenAI API" + resp.body().string());
            }
            StringBuilder sb = new StringBuilder();
            try (Reader input = resp.body().charStream(); BufferedReader reader = new BufferedReader(input)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("content")) {
                        String word = objectMapper.readTree(line.substring(5))
                                .get("choices")
                                .get(0)
                                .get("delta")
                                .get("content")
                                .asText();
                        sb.append(word);
                        System.out.print(word);
                    }
                }
                System.out.println();
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to OpenAI API", e);
        }

    }

    @Data
    @Builder
    public static class Body {
        String model;
        boolean stream;
        List<Message> messages;
    }

    @Data
    @Builder
    public static class Message implements Serializable {
        String role;
        Object content;
    }

    static class ContentBuilder {
        public static JsonNode build(Type type, Serializable content) {
            switch (type) {
                case TEXT:
                    return objectMapper.createObjectNode().put("type", type.getType()).put("text", (String) content);
                case IMAGE:
                    return objectMapper.createObjectNode().put("type", type.getType()).put("image_url", (String) content);
                case IMAGE_URL:
                    return objectMapper.createObjectNode()
                            .put("type", type.getType())
                            .put("image_url", objectMapper.createObjectNode()
                                    .put("url", (String) content));
                default:
                    throw new RuntimeException("Invalid type");
            }
        }
    }
}

