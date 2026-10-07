package com.kapil.marathipdfrag.common.embed;

import java.util.List;

public interface EmbeddingClient {
    List<float[]> embed(List<String> texts);
}
