package com.kapil.marathipdfrag.common.orchestrate;

import com.kapil.marathipdfrag.common.embed.EmbeddingClient;
import com.kapil.marathipdfrag.common.llm.LlmClient;
import com.kapil.marathipdfrag.common.model.ScoredChunk;
import com.kapil.marathipdfrag.common.vector.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class QueryOrchestrator {

    private final QueryRouter router;
    private final EmbeddingClient embeddings;
    private final VectorStore vectors;
    private final LlmClient llm;
    private final String namespace;
    private final int topK;

    public QueryOrchestrator(
            QueryRouter router,
            EmbeddingClient embeddings,
            VectorStore vectors,
            LlmClient llm,
            String namespace,
            int topK
    ) {
        this.router = router;
        this.embeddings = embeddings;
        this.vectors = vectors;
        this.llm = llm;
        this.namespace = namespace;
        this.topK = topK;
    }

    public QueryAnswer ask(String question, Map<String, String> filter) {
        if (router.route(question) == Route.NO_LLM) {
            return QueryAnswer.canned(router.canned(question));
        }
        float[] queryVector = embeddings.embed(List.of(question)).getFirst();
        List<ScoredChunk> hits = vectors.query(namespace, queryVector, topK, filter);
        if (hits.isEmpty() || hits.getFirst().score() < router.minScore()) {
            String answer = llm.complete(generalSystem(), "Question:\n" + question);
            return QueryAnswer.llm(answer);
        }
        String context = hits.stream()
                .map(h -> "[doc=%s page=%d score=%.3f]%n%s".formatted(
                        h.chunk().docId(), h.chunk().page(), h.score(), h.chunk().text()))
                .collect(Collectors.joining("\n\n"));
        String system = """
                Prefer the PDF excerpts when they answer the question, and cite doc id and page.
                If the question needs information that is not in the excerpts, answer that part from general knowledge and say it is not from the uploaded PDFs.
                You may answer in Marathi or English to match the question.
                """;
        String user = "Question:\n" + question + "\n\nExcerpts:\n" + context;
        String answer = llm.complete(system, user);
        return QueryAnswer.rag(answer, hits);
    }

    private static String generalSystem() {
        return """
                No relevant PDF excerpt was found.
                Answer the question from general knowledge.
                Say that the answer is not from the uploaded PDFs.
                You may answer in Marathi or English to match the question.
                """;
    }

    public record QueryAnswer(String kind, String text, List<ScoredChunk> citations) {
        static QueryAnswer canned(String text) {
            return new QueryAnswer("canned", text, List.of());
        }

        static QueryAnswer llm(String text) {
            return new QueryAnswer("llm", text, List.of());
        }

        static QueryAnswer rag(String text, List<ScoredChunk> citations) {
            return new QueryAnswer("rag", text, citations);
        }
    }
}
