package com.kapil.marathipdfrag.ingest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.NotificationChannel;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.StartDocumentTextDetectionRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentTextDetectionResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts async Textract and stores the Step Functions task token. Completion
 * arrives on SNS and {@link TextractCompletionHandler} calls SendTaskSuccess.
 */
public final class StartTextractHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final TextractClient textract = TextractClient.create();
    private final DynamoDbClient dynamo = DynamoDbClient.create();
    private final String snsTopicArn = required("TEXTRACT_SNS_TOPIC_ARN");
    private final String textractRoleArn = required("TEXTRACT_ROLE_ARN");
    private final String jobsTable = required("TEXTRACT_JOBS_TABLE");

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        String taskToken = input.get("taskToken");
        if (taskToken == null || taskToken.isBlank()) {
            throw new IllegalStateException("Missing taskToken. Start this handler with lambda:invoke.waitForTaskToken.");
        }
        StartDocumentTextDetectionResponse response = textract.startDocumentTextDetection(
                StartDocumentTextDetectionRequest.builder()
                        .documentLocation(DocumentLocation.builder()
                                .s3Object(S3Object.builder()
                                        .bucket(input.get("bucket"))
                                        .name(input.get("key"))
                                        .build())
                                .build())
                        .notificationChannel(NotificationChannel.builder()
                                .snsTopicArn(snsTopicArn)
                                .roleArn(textractRoleArn)
                                .build())
                        .jobTag(input.get("docId"))
                        .build()
        );
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("jobId", AttributeValue.fromS(response.jobId()));
        item.put("taskToken", AttributeValue.fromS(taskToken));
        item.put("docId", AttributeValue.fromS(input.get("docId")));
        item.put("bucket", AttributeValue.fromS(input.get("bucket")));
        item.put("s3Key", AttributeValue.fromS(input.get("key")));
        item.put("expiresAt", AttributeValue.fromN(Long.toString(Instant.now().getEpochSecond() + 86_400)));
        dynamo.putItem(PutItemRequest.builder().tableName(jobsTable).item(item).build());
        return Map.of(
                "docId", input.get("docId"),
                "bucket", input.get("bucket"),
                "key", input.get("key"),
                "textractJobId", response.jobId()
        );
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing env " + name);
        }
        return value;
    }
}
