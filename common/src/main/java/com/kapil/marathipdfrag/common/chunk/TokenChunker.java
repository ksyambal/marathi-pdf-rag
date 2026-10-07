package com.kapil.marathipdfrag.common.chunk;

import com.kapil.marathipdfrag.common.model.Chunk;
import com.kapil.marathipdfrag.common.model.ExtractedDocument;
import com.kapil.marathipdfrag.common.model.ExtractedPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Approximate token chunks using whitespace. Swap for a tokenizer later without
 * changing ingest or query.
 */
public final class TokenChunker {

    private final int maxChars;
    private final int overlapChars;

    public TokenChunker(int maxChars, int overlapChars) {
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public static TokenChunker defaults() {
        return new TokenChunker(1800, 200);
    }

    public List<Chunk> chunk(ExtractedDocument document) {
        List<Chunk> chunks = new ArrayList<>();
        for (ExtractedPage page : document.pages()) {
            String cleaned = clean(page.text());
            if (cleaned.isBlank()) {
                continue;
            }
            int start = 0;
            int part = 0;
            while (start < cleaned.length()) {
                int end = Math.min(cleaned.length(), start + maxChars);
                if (end < cleaned.length()) {
                    int lastSpace = cleaned.lastIndexOf(' ', end);
                    if (lastSpace > start + maxChars / 2) {
                        end = lastSpace;
                    }
                }
                String text = cleaned.substring(start, end).trim();
                if (!text.isEmpty()) {
                    Map<String, String> meta = new LinkedHashMap<>();
                    meta.put("s3_key", document.s3Key());
                    meta.put("page", String.valueOf(page.pageNumber()));
                    meta.put("language", page.language());
                    chunks.add(new Chunk(
                            document.docId() + "-" + page.pageNumber() + "-" + part,
                            document.docId(),
                            page.pageNumber(),
                            page.language(),
                            text,
                            meta
                    ));
                    part++;
                }
                if (end >= cleaned.length()) {
                    break;
                }
                start = Math.max(end - overlapChars, start + 1);
            }
        }
        return chunks;
    }

    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u00a0', ' ')
                .replaceAll("[\\t\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
