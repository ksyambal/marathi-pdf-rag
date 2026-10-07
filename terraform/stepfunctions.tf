resource "aws_sfn_state_machine" "ingest" {
  name     = "${local.name}-ingest"
  role_arn = aws_iam_role.sfn.arn
  definition = templatefile("${path.module}/ingest.asl.json", {
    classify_document_arn    = aws_lambda_function.classify_document.arn
    start_textract_arn       = aws_lambda_function.start_textract.arn
    index_document_arn       = aws_lambda_function.index_document.arn
    ocr_cluster_arn          = aws_ecs_cluster.ocr.arn
    ocr_task_definition_arn  = aws_ecs_task_definition.ocr.arn
    ocr_subnet_a             = aws_subnet.ocr[0].id
    ocr_subnet_b             = aws_subnet.ocr[1].id
    ocr_security_group       = aws_security_group.ocr.id
  })
  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.sfn.arn}:*"
    include_execution_data = true
    level                  = "ERROR"
  }
}

resource "aws_cloudwatch_log_group" "sfn" {
  name              = "/aws/vendedlogs/states/${local.name}-ingest"
  retention_in_days = 14
}

resource "aws_cloudwatch_metric_alarm" "dlq" {
  alarm_name          = "${local.name}-ingest-dlq"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  alarm_description   = "Ingest DLQ has messages"
  dimensions = {
    QueueName = aws_sqs_queue.ingest_dlq.name
  }
}

resource "aws_cloudwatch_metric_alarm" "sfn_failed" {
  alarm_name          = "${local.name}-ingest-failed"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsFailed"
  namespace           = "AWS/States"
  period              = 60
  statistic           = "Sum"
  threshold           = 0
  dimensions = {
    StateMachineArn = aws_sfn_state_machine.ingest.arn
  }
}
