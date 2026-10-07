package com.kapil.marathipdfrag.common.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class OpenAiEmbeddingClient implements EmbeddingClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final URI endpoint;

    public OpenAiEmbeddingClient(String apiKey, String model) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = URI.create("https://api.openai.com/v1/embeddings");
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.putPOJO("input", texts);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI embeddings failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode data = mapper.readTree(response.body()).path("data");
            float[][] ordered = new float[texts.size()][];
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                ordered[item.path("index").asInt()] = vector;
            }
            List<float[]> vectors = new ArrayList<>(ordered.length);
            for (float[] vector : ordered) {
                if (vector == null) {
                    throw new IllegalStateException("OpenAI embeddings response missing a vector");
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Embedding request failed", e);
        }
    }
}
