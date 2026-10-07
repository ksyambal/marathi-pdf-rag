package com.kapil.marathipdfrag.common.vector;

import com.kapil.marathipdfrag.common.model.Chunk;
import com.kapil.marathipdfrag.common.model.ScoredChunk;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.ListOutputVector;
import software.amazon.awssdk.services.s3vectors.model.ListVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.ListVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Amazon S3 Vectors has no Pinecone-style namespace. The namespace argument is
 * stored as filterable metadata and applied on every query and delete.
 * QueryVectors returns cosine distance (lower is closer). Scores are converted
 * to similarity {@code 1 - distance} so the orchestrator can keep a higher-is-better threshold.
 */
public final class S3VectorsVectorStore implements VectorStore {

    private static final int PUT_BATCH = 100;
    private static final int DELETE_BATCH = 500;

    private final S3VectorsClient client;
    private final String bucket;
    private final String index;

    public S3VectorsVectorStore(String bucket, String index) {
        this(S3VectorsClient.create(), bucket, index);
    }

    S3VectorsVectorStore(S3VectorsClient client, String bucket, String index) {
        this.client = client;
        this.bucket = bucket;
        this.index = index;
    }

    @Override
    public void upsert(String namespace, List<Chunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks and embeddings size mismatch");
        }
        for (int start = 0; start < chunks.size(); start += PUT_BATCH) {
            int end = Math.min(chunks.size(), start + PUT_BATCH);
            List<PutInputVector> vectors = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) {
                Chunk chunk = chunks.get(i);
                vectors.add(PutInputVector.builder()
                        .key(chunk.id())
                        .data(VectorData.builder().float32(box(embeddings.get(i))).build())
                        .metadata(metadata(namespace, chunk))
                        .build());
            }
            client.putVectors(PutVectorsRequest.builder()
                    .vectorBucketName(bucket)
                    .indexName(index)
                    .vectors(vectors)
                    .build());
        }
    }

    @Override
    public void deleteByDocId(String namespace, String docId) {
        List<String> keys = new ArrayList<>();
        String next = null;
        do {
            ListVectorsRequest.Builder request = ListVectorsRequest.builder()
                    .vectorBucketName(bucket)
                    .indexName(index)
                    .returnMetadata(true)
                    .maxResults(DELETE_BATCH);
            if (next != null) {
                request.nextToken(next);
            }
            ListVectorsResponse page = client.listVectors(request.build());
            for (ListOutputVector vector : page.vectors()) {
                if (docId.equals(text(vector.metadata(), "doc_id"))
                        && namespace.equals(text(vector.metadata(), "namespace"))) {
                    keys.add(vector.key());
                }
            }
            next = page.nextToken();
        } while (next != null && !next.isBlank());

        for (int start = 0; start < keys.size(); start += DELETE_BATCH) {
            List<String> batch = keys.subList(start, Math.min(keys.size(), start + DELETE_BATCH));
            client.deleteVectors(DeleteVectorsRequest.builder()
                    .vectorBucketName(bucket)
                    .indexName(index)
                    .keys(batch)
                    .build());
        }
    }

    @Override
    public List<ScoredChunk> query(String namespace, float[] vector, int topK, Map<String, String> filter) {
        Map<String, Document> filterFields = new LinkedHashMap<>();
        if (namespace != null && !namespace.isBlank()) {
            filterFields.put("namespace", Document.fromString(namespace));
        }
        if (filter != null) {
            filter.forEach((key, value) -> filterFields.put(key, Document.fromString(value)));
        }
        QueryVectorsRequest.Builder request = QueryVectorsRequest.builder()
                .vectorBucketName(bucket)
                .indexName(index)
                .topK(topK)
                .returnDistance(true)
                .returnMetadata(true)
                .queryVector(VectorData.builder().float32(box(vector)).build());
        if (!filterFields.isEmpty()) {
            request.filter(Document.fromMap(filterFields));
        }
        QueryVectorsResponse response = client.queryVectors(request.build());
        List<ScoredChunk> hits = new ArrayList<>();
        for (QueryOutputVector match : response.vectors()) {
            Document meta = match.metadata();
            Map<String, String> metadata = new LinkedHashMap<>();
            if (meta != null && !meta.isNull()) {
                meta.asMap().forEach((key, value) -> metadata.put(key, text(meta, key)));
            }
            Chunk chunk = new Chunk(
                    match.key(),
                    text(meta, "doc_id"),
                    number(meta, "page"),
                    text(meta, "language").isBlank() ? "unknown" : text(meta, "language"),
                    text(meta, "text"),
                    metadata
            );
            float distance = match.distance() == null ? 1f : match.distance().floatValue();
            hits.add(new ScoredChunk(chunk, similarity(distance)));
        }
        hits.sort(Comparator.comparing(ScoredChunk::score).reversed());
        return hits;
    }

    static float similarity(float cosineDistance) {
        return Math.max(0f, Math.min(1f, 1f - cosineDistance));
    }

    private static Document metadata(String namespace, Chunk chunk) {
        Map<String, Document> fields = new LinkedHashMap<>();
        fields.put("namespace", Document.fromString(namespace));
        fields.put("doc_id", Document.fromString(chunk.docId()));
        fields.put("page", Document.fromNumber(chunk.page()));
        fields.put("language", Document.fromString(chunk.language() == null ? "unknown" : chunk.language()));
        fields.put("text", Document.fromString(chunk.text()));
        chunk.metadata().forEach((key, value) -> {
            if (!fields.containsKey(key) && value != null) {
                fields.put(key, Document.fromString(value));
            }
        });
        return Document.fromMap(fields);
    }

    private static List<Float> box(float[] values) {
        List<Float> boxed = new ArrayList<>(values.length);
        for (float value : values) {
            boxed.add(value);
        }
        return boxed;
    }

    private static String text(Document metadata, String field) {
        if (metadata == null || metadata.isNull()) {
            return "";
        }
        Document value = metadata.asMap().get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            return value.asNumber().toString();
        }
        return "";
    }

    private static int number(Document metadata, String field) {
        if (metadata == null || metadata.isNull()) {
            return 0;
        }
        Document value = metadata.asMap().get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return 0;
        }
        return value.asNumber().intValue();
    }
}
