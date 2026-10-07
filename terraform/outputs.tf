output "pdf_bucket" {
  value = aws_s3_bucket.docs.id
}

output "upload_prefix" {
  value = "s3://${aws_s3_bucket.docs.id}/${var.raw_prefix}"
}

output "query_repository" {
  value = aws_ecr_repository.query.repository_url
}

output "agent_runtime_arn" {
  value = aws_bedrockagentcore_agent_runtime.query.agent_runtime_arn
}

output "state_machine" {
  value = aws_sfn_state_machine.ingest.arn
}

output "vector_bucket" {
  value = aws_s3vectors_vector_bucket.chunks.vector_bucket_name
}

output "ocr_repository" {
  value = aws_ecr_repository.ocr.repository_url
}

output "vector_index" {
  value = aws_s3vectors_index.chunks.index_name
}
