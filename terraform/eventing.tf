resource "aws_sqs_queue" "ingest_dlq" {
  name = "${local.name}-ingest-dlq"
}

resource "aws_sqs_queue" "ingest" {
  name                       = "${local.name}-ingest"
  visibility_timeout_seconds = 60
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ingest_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_cloudwatch_event_rule" "s3_pdf" {
  name = "${local.name}-s3-pdf-created"
  event_pattern = jsonencode({
    source        = ["aws.s3"]
    "detail-type" = ["Object Created"]
    detail = {
      bucket = {
        name = [aws_s3_bucket.docs.id]
      }
      object = {
        key = [{ prefix = var.raw_prefix }]
      }
    }
  })
}

resource "aws_cloudwatch_event_target" "s3_pdf_sqs" {
  rule = aws_cloudwatch_event_rule.s3_pdf.name
  arn  = aws_sqs_queue.ingest.arn
}

resource "aws_sqs_queue_policy" "ingest" {
  queue_url = aws_sqs_queue.ingest.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.ingest.arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.s3_pdf.arn }
      }
    }]
  })
}

resource "aws_sns_topic" "textract" {
  name = "${local.name}-textract"
}

resource "aws_sns_topic_subscription" "textract_complete" {
  topic_arn = aws_sns_topic.textract.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.textract_complete.arn
}

resource "aws_lambda_permission" "textract_sns" {
  statement_id  = "AllowTextractSns"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.textract_complete.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.textract.arn
}
