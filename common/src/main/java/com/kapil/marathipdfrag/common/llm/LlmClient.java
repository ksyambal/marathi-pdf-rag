package com.kapil.marathipdfrag.common.llm;

public interface LlmClient {
    String complete(String systemPrompt, String userPrompt);
}
