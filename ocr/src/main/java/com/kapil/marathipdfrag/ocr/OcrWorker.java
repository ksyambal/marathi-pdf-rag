package com.kapil.marathipdfrag.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.marathipdfrag.common.model.ExtractedDocument;
import com.kapil.marathipdfrag.common.model.ExtractedPage;
import com.kapil.marathipdfrag.common.ocr.OcrRouter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.Document;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Page-level OCR. Devanagari pages use Tesseract mar+eng. Latin pages use the
 * PDF text layer, or Textract when the page is a scan. Tesseract text is kept
 * if Textract is unavailable.
 */
public final class OcrWorker {

    public static void main(String[] args) throws Exception {
        String bucket = required("BUCKET");
        String key = required("KEY");
        String docId = required("DOC_ID");
        String extractedKey = "extracted/" + docId + ".json";
        S3Client s3 = S3Client.create();
        TextractClient textract = TextractClient.create();
        OcrRouter router = new OcrRouter();
        ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        List<ExtractedPage> pages = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(object.asByteArray())) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(pdf);
            for (int index = 0; index < pdf.getNumberOfPages(); index++) {
                int pageNumber = index + 1;
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String textLayer = stripper.getText(pdf);
                pages.add(readPage(router, textract, renderer, index, pageNumber, textLayer));
            }
        }
        String json = new ObjectMapper().writeValueAsString(new ExtractedDocument(docId, bucket, key, pages));
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(extractedKey).contentType("application/json").build(),
                RequestBody.fromString(json));
        System.out.println("Wrote s3://" + bucket + "/" + extractedKey + " pages=" + pages.size());
    }

    private static ExtractedPage readPage(OcrRouter router, TextractClient textract, PDFRenderer renderer,
                                          int index, int pageNumber, String textLayer) throws Exception {
        String sample = textLayer == null ? "" : textLayer.strip();
        Path png = null;
        if (sample.isBlank()) {
            png = render(renderer, index);
            sample = tesseract(png);
        }
        boolean indic = router.choose(sample, null) == OcrRouter.Engine.INDIC;
        if (indic) {
            if (png == null) {
                png = render(renderer, index);
                sample = tesseract(png);
            }
            return new ExtractedPage(pageNumber, sample, "mr", 0.75f);
        }
        if (png != null) {
            try {
                String detected = textract(textract, png);
                if (!detected.isBlank()) {
                    sample = detected;
                }
            } catch (RuntimeException e) {
                System.err.println("Textract failed for page " + pageNumber + "; using Tesseract text. " + e.getMessage());
            }
        }
        return new ExtractedPage(pageNumber, sample, "en", 0.9f);
    }

    private static Path render(PDFRenderer renderer, int index) throws Exception {
        BufferedImage image = renderer.renderImageWithDPI(index, 200);
        Path png = Files.createTempFile("page-" + index + "-", ".png");
        ImageIO.write(image, "png", png.toFile());
        png.toFile().deleteOnExit();
        return png;
    }

    private static String tesseract(Path png) throws Exception {
        Process process = new ProcessBuilder("tesseract", png.toString(), "stdout", "-l", "mar+eng", "--psm", "6")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Tesseract timed out for " + png);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Tesseract failed: " + new String(output, StandardCharsets.UTF_8));
        }
        return new String(output, StandardCharsets.UTF_8).strip();
    }

    private static String textract(TextractClient textract, Path png) {
        StringBuilder text = new StringBuilder();
        for (Block block : textract.detectDocumentText(DetectDocumentTextRequest.builder()
                .document(Document.builder().bytes(software.amazon.awssdk.core.SdkBytes.fromByteArray(read(png))).build())
                .build()).blocks()) {
            if (block.blockType() == BlockType.LINE && block.text() != null) {
                text.append(block.text()).append('\n');
            }
        }
        return text.toString().strip();
    }

    private static byte[] read(Path png) {
        try {
            return Files.readAllBytes(png);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + png, e);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing env " + name);
        }
        return value;
    }
}
