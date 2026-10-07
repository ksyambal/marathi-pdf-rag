package com.kapil.marathipdfrag.ingest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.marathipdfrag.common.chunk.TokenChunker;
import com.kapil.marathipdfrag.common.embed.OpenAiEmbeddingClient;
import com.kapil.marathipdfrag.common.model.Chunk;
import com.kapil.marathipdfrag.common.model.ExtractedDocument;
import com.kapil.marathipdfrag.common.model.ExtractedPage;
import com.kapil.marathipdfrag.common.vector.S3VectorsVectorStore;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads OCR output (Textract job or extracted JSON), chunks, embeds, and writes S3 Vectors.
 */
public final class IndexDocumentHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final S3Client s3 = S3Client.create();
    private final TextractClient textract = TextractClient.create();
    private final DynamoDbClient dynamo = DynamoDbClient.create();
    private final TokenChunker chunker = TokenChunker.defaults();
    private final OpenAiEmbeddingClient embeddings = new OpenAiEmbeddingClient(
            required("OPENAI_API_KEY"),
            envOr("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small")
    );
    private final S3VectorsVectorStore vectors = new S3VectorsVectorStore(
            required("S3_VECTOR_BUCKET"),
            required("S3_VECTOR_INDEX")
    );
    private final String namespace = envOr("S3_VECTOR_NAMESPACE", "default");
    private final String tableName = required("DOCUMENTS_TABLE");

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        ExtractedDocument document = load(input);
        List<Chunk> chunks = chunker.chunk(document);
        vectors.deleteByDocId(namespace, document.docId());
        if (!chunks.isEmpty()) {
            List<float[]> embedded = embeddings.embed(chunks.stream().map(Chunk::text).toList());
            vectors.upsert(namespace, chunks, embedded);
        }
        saveStatus(document, chunks.size());
        return Map.of(
                "docId", document.docId(),
                "chunks", String.valueOf(chunks.size())
        );
    }

    private ExtractedDocument load(Map<String, String> input) {
        String extractedKey = input.get("extractedKey");
        if (extractedKey != null && !extractedKey.isBlank()) {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(input.get("bucket"))
                    .key(extractedKey)
                    .build());
            try {
                return mapper.readValue(bytes.asString(StandardCharsets.UTF_8), ExtractedDocument.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse extracted JSON", e);
            }
        }
        String jobId = input.get("textractJobId");
        List<ExtractedPage> pages = new ArrayList<>();
        Map<Integer, StringBuilder> byPage = new LinkedHashMap<>();
        String next = null;
        do {
            GetDocumentTextDetectionResponse response = textract.getDocumentTextDetection(
                    GetDocumentTextDetectionRequest.builder()
                            .jobId(jobId)
                            .nextToken(next)
                            .build()
            );
            for (Block block : response.blocks()) {
                if (block.blockType() == BlockType.LINE && block.text() != null) {
                    int page = block.page() == null ? 1 : block.page();
                    byPage.computeIfAbsent(page, p -> new StringBuilder())
                            .append(block.text())
                            .append('\n');
                }
            }
            next = response.nextToken();
        } while (next != null);
        byPage.forEach((page, text) -> pages.add(new ExtractedPage(page, text.toString(), "en", 1.0f)));
        return new ExtractedDocument(input.get("docId"), input.get("bucket"), input.get("key"), pages);
    }

    private void saveStatus(ExtractedDocument document, int chunks) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("docId", AttributeValue.fromS(document.docId()));
        item.put("s3Key", AttributeValue.fromS(document.s3Key()));
        item.put("status", AttributeValue.fromS("INDEXED"));
        item.put("chunks", AttributeValue.fromN(Integer.toString(chunks)));
        item.put("updatedAt", AttributeValue.fromS(Instant.now().toString()));
        dynamo.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing env " + name);
        }
        return value;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
