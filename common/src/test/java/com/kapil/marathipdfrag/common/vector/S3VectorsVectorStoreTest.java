package com.kapil.marathipdfrag.common.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3VectorsVectorStoreTest {

    @Test
    void convertsCosineDistanceToSimilarity() {
        assertEquals(1f, S3VectorsVectorStore.similarity(0f));
        assertEquals(0.25f, S3VectorsVectorStore.similarity(0.75f));
        assertEquals(0f, S3VectorsVectorStore.similarity(2f));
    }
}
