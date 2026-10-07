package com.kapil.marathipdfrag.ingest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskFailureRequest;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;
import software.amazon.awssdk.services.sfn.model.TaskDoesNotExistException;
import software.amazon.awssdk.services.sfn.model.TaskTimedOutException;

import java.util.Map;

/**
 * Textract publishes job completion to SNS. This resumes the Step Functions
 * task that is waiting on the task token stored for that job id.
 */
public final class TextractCompletionHandler implements RequestHandler<SNSEvent, String> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDbClient dynamo = DynamoDbClient.create();
    private final SfnClient sfn = SfnClient.create();
    private final String tableName = required("TEXTRACT_JOBS_TABLE");

    @Override
    public String handleRequest(SNSEvent event, Context context) {
        for (SNSEvent.SNSRecord record : event.getRecords()) {
            try {
                JsonNode message = mapper.readTree(record.getSNS().getMessage());
                String jobId = message.path("JobId").asText();
                String status = message.path("Status").asText();
                Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("jobId", AttributeValue.fromS(jobId)))
                        .build()).item();
                if (item == null || item.isEmpty()) {
                    throw new IllegalStateException("No task token stored for Textract job " + jobId);
                }
                String token = item.get("taskToken").s();
                if ("SUCCEEDED".equals(status)) {
                    String output = mapper.writeValueAsString(Map.of(
                            "docId", item.get("docId").s(),
                            "bucket", item.get("bucket").s(),
                            "key", item.get("s3Key").s(),
                            "textractJobId", jobId
                    ));
                    try {
                        sfn.sendTaskSuccess(SendTaskSuccessRequest.builder().taskToken(token).output(output).build());
                    } catch (TaskDoesNotExistException | TaskTimedOutException ignored) {
                        context.getLogger().log("Task already closed for job " + jobId);
                    }
                } else {
                    try {
                        sfn.sendTaskFailure(SendTaskFailureRequest.builder()
                                .taskToken(token)
                                .error("TextractFailed")
                                .cause(status)
                                .build());
                    } catch (TaskDoesNotExistException | TaskTimedOutException ignored) {
                        context.getLogger().log("Task already closed for job " + jobId);
                    }
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to resume Textract task", e);
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
}
