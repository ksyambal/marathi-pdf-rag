package com.kapil.marathipdfrag.common.vector;

import com.kapil.marathipdfrag.common.model.Chunk;
import com.kapil.marathipdfrag.common.model.ScoredChunk;

import java.util.List;
import java.util.Map;

public interface VectorStore {
    void upsert(String namespace, List<Chunk> chunks, List<float[]> embeddings);

    void deleteByDocId(String namespace, String docId);

    List<ScoredChunk> query(String namespace, float[] vector, int topK, Map<String, String> filter);
}
