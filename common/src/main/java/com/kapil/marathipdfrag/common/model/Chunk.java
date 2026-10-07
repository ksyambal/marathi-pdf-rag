package com.kapil.marathipdfrag.common.model;

import java.util.Map;

public record Chunk(
        String id,
        String docId,
        int page,
        String language,
        String text,
        Map<String, String> metadata
) {}
