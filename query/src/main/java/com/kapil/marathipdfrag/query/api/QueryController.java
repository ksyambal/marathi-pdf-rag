package com.kapil.marathipdfrag.query.api;

import com.kapil.marathipdfrag.common.orchestrate.QueryOrchestrator;
import com.kapil.marathipdfrag.common.orchestrate.QueryOrchestrator.QueryAnswer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class QueryController {

    private final QueryOrchestrator orchestrator;

    public QueryController(QueryOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "Healthy");
    }

    @PostMapping("/invocations")
    public QueryResponse invocations(@RequestBody InvocationRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt must be a non-empty string");
        }
        return answer(request.prompt(), request.filter());
    }

    @PostMapping("/query")
    public QueryResponse query(@RequestBody QueryRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question must be a non-empty string");
        }
        return answer(request.question(), request.filter());
    }

    private QueryResponse answer(String question, Map<String, String> filter) {
        QueryAnswer result = orchestrator.ask(question, filter == null ? Map.of() : filter);
        List<Citation> citations = result.citations().stream()
                .map(h -> new Citation(h.chunk().docId(), h.chunk().page(), h.score(), h.chunk().language()))
                .toList();
        return new QueryResponse(result.kind(), result.text(), citations);
    }

    public record InvocationRequest(String prompt, Map<String, String> filter) {}

    public record QueryRequest(String question, Map<String, String> filter) {}

    public record Citation(String docId, int page, float score, String language) {}

    public record QueryResponse(String kind, String answer, List<Citation> citations) {}
}
