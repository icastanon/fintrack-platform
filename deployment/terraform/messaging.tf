resource "aws_sqs_queue" "transaction_processing_dlq" {
  name = "${local.resource_prefix}-transaction-processing-dlq"

  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true

  tags = {
    Name = "${local.resource_prefix}-transaction-processing-dlq"
  }
}

resource "aws_sqs_queue" "transaction_processing" {
  name = "${local.resource_prefix}-transaction-processing"

  visibility_timeout_seconds = 30
  receive_wait_time_seconds  = 20
  message_retention_seconds  = 345600
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transaction_processing_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name = "${local.resource_prefix}-transaction-processing"
  }
}

resource "aws_sqs_queue" "import_jobs_dlq" {
  name = "${local.resource_prefix}-import-jobs-dlq"

  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true

  tags = {
    Name = "${local.resource_prefix}-import-jobs-dlq"
  }
}

resource "aws_sqs_queue" "import_jobs" {
  name = "${local.resource_prefix}-import-jobs"

  visibility_timeout_seconds = 120
  receive_wait_time_seconds  = 20
  message_retention_seconds  = 345600
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.import_jobs_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name = "${local.resource_prefix}-import-jobs"
  }
}