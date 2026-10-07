package com.kapil.marathipdfrag.common.chunk;

import org.junit.jupiter.api.Test;

import com.kapil.marathipdfrag.common.model.ExtractedDocument;
import com.kapil.marathipdfrag.common.model.ExtractedPage;
import com.kapil.marathipdfrag.common.ocr.OcrRouter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenChunkerTest {

    @Test
    void splitsLongPageWithOverlap() {
        String text = "word ".repeat(500);
        var doc = new ExtractedDocument("doc-1", "bucket", "raw/a.pdf",
                List.of(new ExtractedPage(1, text, "en", 0.9f)));
        var chunks = TokenChunker.defaults().chunk(doc);
        assertFalse(chunks.isEmpty());
        assertEquals("doc-1", chunks.getFirst().docId());
    }

    @Test
    void routesMarathiToIndicOcr() {
        var router = new OcrRouter();
        assertEquals(OcrRouter.Engine.INDIC, router.choose("महाराष्ट्र शासन", null));
        assertEquals(OcrRouter.Engine.TEXTRACT, router.choose("Government of Maharashtra", "en"));
        assertTrue(TokenChunker.clean("  a \n\n\n b  ").contains("a"));
    }
}
