package com.kapil.marathipdfrag.common.model;

import java.util.List;

public record ExtractedDocument(String docId, String s3Bucket, String s3Key, List<ExtractedPage> pages) {}
