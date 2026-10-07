package com.kapil.marathipdfrag.common.ocr;

import java.util.List;

/**
 * Picks a document path from the text already embedded in each PDF page.
 * Scanned Marathi files have no text layer, so they must be uploaded under raw/mr/.
 */
public final class PageRouteDecider {

    public enum Route {
        TEXTRACT,
        PAGE_OCR
    }

    private final OcrRouter router = new OcrRouter();

    public Route decide(String s3Key, List<String> pageTextSamples) {
        if (s3Key != null && s3Key.startsWith("raw/mr/")) {
            return Route.PAGE_OCR;
        }
        if (pageTextSamples != null) {
            for (String sample : pageTextSamples) {
                if (router.choose(sample, null) == OcrRouter.Engine.INDIC) {
                    return Route.PAGE_OCR;
                }
            }
        }
        return Route.TEXTRACT;
    }
}
