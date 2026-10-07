package com.kapil.marathipdfrag.query.config;

import com.kapil.marathipdfrag.common.embed.EmbeddingClient;
import com.kapil.marathipdfrag.common.embed.OpenAiEmbeddingClient;
import com.kapil.marathipdfrag.common.llm.LlmClient;
import com.kapil.marathipdfrag.common.llm.OpenAiChatClient;
import com.kapil.marathipdfrag.common.orchestrate.QueryOrchestrator;
import com.kapil.marathipdfrag.common.orchestrate.QueryRouter;
import com.kapil.marathipdfrag.common.vector.S3VectorsVectorStore;
import com.kapil.marathipdfrag.common.vector.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    EmbeddingClient embeddingClient(@Value("${openai.api-key}") String apiKey,
                                    @Value("${openai.embedding-model}") String model) {
        requireApiKey(apiKey);
        return new OpenAiEmbeddingClient(apiKey, model);
    }

    @Bean
    LlmClient llmClient(@Value("${openai.api-key}") String apiKey,
                        @Value("${openai.chat-model}") String model) {
        return new OpenAiChatClient(apiKey, model);
    }

    @Bean
    VectorStore vectorStore(@Value("${s3vectors.bucket}") String bucket,
                            @Value("${s3vectors.index}") String index) {
        return new S3VectorsVectorStore(bucket, index);
    }

    @Bean
    QueryOrchestrator queryOrchestrator(
            EmbeddingClient embeddings,
            VectorStore vectors,
            LlmClient llm,
            @Value("${s3vectors.namespace}") String namespace,
            @Value("${rag.top-k}") int topK,
            @Value("${rag.min-score}") float minScore
    ) {
        return new QueryOrchestrator(new QueryRouter(minScore), embeddings, vectors, llm, namespace, topK);
    }

    private static void requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is empty. Set it in this terminal before starting the query service.");
        }
    }
}
