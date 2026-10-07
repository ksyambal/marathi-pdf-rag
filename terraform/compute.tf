resource "aws_ecr_repository" "query" {
  name                 = "${local.name}-query"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_bedrockagentcore_agent_runtime" "query" {
  agent_runtime_name = replace(local.name, "-", "_")
  description        = "Marathi PDF RAG query agent"
  role_arn           = aws_iam_role.agentcore.arn

  agent_runtime_artifact {
    container_configuration {
      container_uri = var.query_image
    }
  }

  environment_variables = {
    AWS_REGION             = var.aws_region
    OPENAI_API_KEY         = var.openai_api_key
    S3_VECTOR_BUCKET       = aws_s3vectors_vector_bucket.chunks.vector_bucket_name
    S3_VECTOR_INDEX        = aws_s3vectors_index.chunks.index_name
    S3_VECTOR_NAMESPACE    = var.vector_namespace
    OPENAI_EMBEDDING_MODEL = "text-embedding-3-small"
    OPENAI_CHAT_MODEL      = "gpt-4o-mini"
  }

  network_configuration {
    network_mode = "PUBLIC"
  }

  protocol_configuration {
    server_protocol = "HTTP"
  }
}
