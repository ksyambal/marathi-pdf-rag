package com.kapil.marathipdfrag.common.ocr;

import java.util.regex.Pattern;

/**
 * Textract does not OCR Marathi/Devanagari. Route pages that look Indic to a
 * dedicated OCR worker; use Textract for Latin-script English pages.
 */
public final class OcrRouter {

    private static final Pattern DEVANAGARI = Pattern.compile("\\p{IsDevanagari}");

    public enum Engine {
        TEXTRACT,
        INDIC
    }

    public Engine choose(String sampleText, String declaredLanguage) {
        if (declaredLanguage != null && declaredLanguage.toLowerCase().startsWith("mr")) {
            return Engine.INDIC;
        }
        if (sampleText != null && DEVANAGARI.matcher(sampleText).find()) {
            return Engine.INDIC;
        }
        return Engine.TEXTRACT;
    }
}
