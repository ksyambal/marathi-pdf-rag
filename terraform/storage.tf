resource "aws_s3_bucket" "docs" {
  bucket = "${local.name}-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_versioning" "docs" {
  bucket = aws_s3_bucket.docs.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "docs" {
  bucket = aws_s3_bucket.docs.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "docs" {
  bucket                  = aws_s3_bucket.docs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_notification" "eventbridge" {
  bucket      = aws_s3_bucket.docs.id
  eventbridge = true
}

resource "aws_dynamodb_table" "documents" {
  name         = "${local.name}-documents"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "docId"

  attribute {
    name = "docId"
    type = "S"
  }
}

resource "aws_dynamodb_table" "textract_jobs" {
  name         = "${local.name}-textract-jobs"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "jobId"

  attribute {
    name = "jobId"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }
}

resource "aws_secretsmanager_secret" "openai" {
  name = "${local.name}/openai"
}

resource "aws_secretsmanager_secret_version" "openai" {
  secret_id     = aws_secretsmanager_secret.openai.id
  secret_string = var.openai_api_key
}

resource "aws_s3vectors_vector_bucket" "chunks" {
  vector_bucket_name = "${local.name}-vec-${data.aws_caller_identity.current.account_id}"
  force_destroy      = true
}

resource "aws_s3vectors_index" "chunks" {
  index_name         = "chunks"
  vector_bucket_name = aws_s3vectors_vector_bucket.chunks.vector_bucket_name
  data_type          = "float32"
  dimension          = var.vector_dimension
  distance_metric    = "cosine"

  metadata_configuration {
    non_filterable_metadata_keys = ["text"]
  }
}
