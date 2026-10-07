package com.kapil.marathipdfrag.ingest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.kapil.marathipdfrag.common.ocr.PageRouteDecider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the PDF text layer and chooses Textract for English documents or the
 * page-level OCR worker when any page is Marathi or the key is under raw/mr/.
 */
public final class ClassifyDocumentHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final S3Client s3 = S3Client.create();
    private final PageRouteDecider decider = new PageRouteDecider();

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        String bucket = input.get("bucket");
        String key = input.get("key");
        ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        List<String> samples = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(bytes.asByteArray())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                samples.add(stripper.getText(pdf));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to classify " + key, e);
        }
        return Map.of(
                "docId", input.get("docId"),
                "bucket", bucket,
                "key", key,
                "route", decider.decide(key, samples).name()
        );
    }
}
