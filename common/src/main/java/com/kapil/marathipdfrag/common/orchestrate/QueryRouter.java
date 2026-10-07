package com.kapil.marathipdfrag.common.orchestrate;

import java.util.Locale;
import java.util.Set;

public final class QueryRouter {

    private static final Set<String> SMALL_TALK = Set.of(
            "hi", "hello", "hey", "thanks", "thank you", "namaste", "नमस्ते"
    );

    private final float minScore;

    public QueryRouter(float minScore) {
        this.minScore = minScore;
    }

    public Route route(String question) {
        String q = question == null ? "" : question.strip().toLowerCase(Locale.ROOT);
        if (q.isBlank() || SMALL_TALK.contains(q)) {
            return Route.NO_LLM;
        }
        return Route.RAG;
    }

    public float minScore() {
        return minScore;
    }

    public String canned(String question) {
        return "Ask a question about the uploaded PDFs and I will answer from that corpus.";
    }
}
