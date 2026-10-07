package com.kapil.marathipdfrag.common.ocr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageRouteDeciderTest {

    private final PageRouteDecider decider = new PageRouteDecider();

    @Test
    void sendsMarathiPrefixToPageOcr() {
        assertEquals(PageRouteDecider.Route.PAGE_OCR, decider.decide("raw/mr/notice.pdf", List.of("English only")));
    }

    @Test
    void sendsMixedDevanagariToPageOcr() {
        assertEquals(PageRouteDecider.Route.PAGE_OCR,
                decider.decide("raw/notice.pdf", List.of("Government of Maharashtra", "महाराष्ट्र शासन")));
    }

    @Test
    void sendsEnglishTextToTextract() {
        assertEquals(PageRouteDecider.Route.TEXTRACT,
                decider.decide("raw/notice.pdf", List.of("Government of Maharashtra", "")));
    }
}
