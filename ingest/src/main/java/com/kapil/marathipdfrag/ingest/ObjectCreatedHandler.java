package com.kapil.marathipdfrag.ingest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * EventBridge (S3 Object Created) -> SQS -> this Lambda -> Step Functions ingest workflow.
 */
public final class ObjectCreatedHandler implements RequestHandler<SQSEvent, String> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SfnClient sfn = SfnClient.create();
    private final String stateMachineArn = required("INGEST_STATE_MACHINE_ARN");

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                JsonNode root = mapper.readTree(message.getBody());
                JsonNode detail = root.has("detail") ? root.get("detail") : root;
                String bucket = text(detail, "bucket", "name");
                String key = decodeKey(text(detail, "object", "key"));
                if (bucket == null || key == null || !key.toLowerCase().endsWith(".pdf")) {
                    context.getLogger().log("Skipping non-pdf: " + key);
                    continue;
                }
                String docId = UUID.randomUUID().toString();
                Map<String, String> input = Map.of(
                        "docId", docId,
                        "bucket", bucket,
                        "key", key
                );
                sfn.startExecution(StartExecutionRequest.builder()
                        .stateMachineArn(stateMachineArn)
                        .name(docId)
                        .input(mapper.writeValueAsString(input))
                        .build());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start ingest for " + message.getMessageId(), e);
            }
        }
        return "ok";
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing env " + name);
        }
        return value;
    }

    private static String decodeKey(String key) {
        if (key == null) {
            return null;
        }
        return URLDecoder.decode(key.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String text(JsonNode detail, String parent, String child) {
        JsonNode node = detail.path(parent).path(child);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
