resource "aws_cloudwatch_log_group" "start_ingest" {
  name              = "/aws/lambda/${local.name}-start-ingest"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "start_textract" {
  name              = "/aws/lambda/${local.name}-start-textract"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "index_document" {
  name              = "/aws/lambda/${local.name}-index-document"
  retention_in_days = 14
}

resource "aws_lambda_function" "start_ingest" {
  function_name    = "${local.name}-start-ingest"
  role             = aws_iam_role.lambda.arn
  handler          = "com.kapil.marathipdfrag.ingest.ObjectCreatedHandler::handleRequest"
  runtime          = "java21"
  filename         = local.ingest_jar
  source_code_hash = filebase64sha256(local.ingest_jar)
  memory_size      = 512
  timeout          = 30
  environment {
    variables = {
      INGEST_STATE_MACHINE_ARN = aws_sfn_state_machine.ingest.arn
    }
  }
  depends_on = [aws_cloudwatch_log_group.start_ingest]
}

resource "aws_lambda_event_source_mapping" "ingest_sqs" {
  event_source_arn = aws_sqs_queue.ingest.arn
  function_name    = aws_lambda_function.start_ingest.arn
  batch_size       = 1
}

resource "aws_cloudwatch_log_group" "classify_document" {
  name              = "/aws/lambda/${local.name}-classify-document"
  retention_in_days = 14
}

resource "aws_lambda_function" "classify_document" {
  function_name    = "${local.name}-classify-document"
  role             = aws_iam_role.lambda.arn
  handler          = "com.kapil.marathipdfrag.ingest.ClassifyDocumentHandler::handleRequest"
  runtime          = "java21"
  filename         = local.ingest_jar
  source_code_hash = filebase64sha256(local.ingest_jar)
  memory_size      = 2048
  timeout          = 120
  depends_on       = [aws_cloudwatch_log_group.classify_document]
}

resource "aws_cloudwatch_log_group" "textract_complete" {
  name              = "/aws/lambda/${local.name}-textract-complete"
  retention_in_days = 14
}

resource "aws_lambda_function" "textract_complete" {
  function_name    = "${local.name}-textract-complete"
  role             = aws_iam_role.lambda.arn
  handler          = "com.kapil.marathipdfrag.ingest.TextractCompletionHandler::handleRequest"
  runtime          = "java21"
  filename         = local.ingest_jar
  source_code_hash = filebase64sha256(local.ingest_jar)
  memory_size      = 512
  timeout          = 30
  environment {
    variables = {
      TEXTRACT_JOBS_TABLE = aws_dynamodb_table.textract_jobs.name
    }
  }
  depends_on = [aws_cloudwatch_log_group.textract_complete]
}

resource "aws_lambda_function" "start_textract" {
  function_name    = "${local.name}-start-textract"
  role             = aws_iam_role.lambda.arn
  handler          = "com.kapil.marathipdfrag.ingest.StartTextractHandler::handleRequest"
  runtime          = "java21"
  filename         = local.ingest_jar
  source_code_hash = filebase64sha256(local.ingest_jar)
  memory_size      = 512
  timeout          = 30
  environment {
    variables = {
      TEXTRACT_SNS_TOPIC_ARN = aws_sns_topic.textract.arn
      TEXTRACT_ROLE_ARN      = aws_iam_role.textract.arn
      TEXTRACT_JOBS_TABLE    = aws_dynamodb_table.textract_jobs.name
    }
  }
  depends_on = [aws_cloudwatch_log_group.start_textract]
}

resource "aws_lambda_function" "index_document" {
  function_name    = "${local.name}-index-document"
  role             = aws_iam_role.lambda.arn
  handler          = "com.kapil.marathipdfrag.ingest.IndexDocumentHandler::handleRequest"
  runtime          = "java21"
  filename         = local.ingest_jar
  source_code_hash = filebase64sha256(local.ingest_jar)
  memory_size      = 2048
  timeout          = 120
  environment {
    variables = {
      OPENAI_API_KEY         = var.openai_api_key
      S3_VECTOR_BUCKET       = aws_s3vectors_vector_bucket.chunks.vector_bucket_name
      S3_VECTOR_INDEX        = aws_s3vectors_index.chunks.index_name
      S3_VECTOR_NAMESPACE    = var.vector_namespace
      DOCUMENTS_TABLE        = aws_dynamodb_table.documents.name
      OPENAI_EMBEDDING_MODEL = "text-embedding-3-small"
    }
  }
  depends_on = [aws_cloudwatch_log_group.index_document]
}
