data "aws_iam_policy_document" "ecs_tasks_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [var.aws_account_id]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:*"]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name               = "${local.resource_prefix}-ecs-task-execution"
  description        = "Allows ECS to start FinTrack tasks."
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json

  tags = {
    Name = "${local.resource_prefix}-ecs-task-execution"
  }
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "api_task" {
  name               = "${local.resource_prefix}-api-task"
  description        = "Permissions used by the FinTrack API application."
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json

  tags = {
    Name = "${local.resource_prefix}-api-task"
  }
}

resource "aws_iam_role" "worker_task" {
  name               = "${local.resource_prefix}-worker-task"
  description        = "Permissions used by the FinTrack worker application."
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json

  tags = {
    Name = "${local.resource_prefix}-worker-task"
  }
}

data "aws_iam_policy_document" "api_task" {
  statement {
    sid = "PublishApplicationEvents"

    actions = [
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
      "sqs:SendMessage"
    ]

    resources = [
      aws_sqs_queue.transaction_processing.arn,
      aws_sqs_queue.import_jobs.arn
    ]
  }

  statement {
    sid = "UploadAndCleanUpImportSources"

    actions = [
      "s3:DeleteObject",
      "s3:PutObject"
    ]

    resources = ["${aws_s3_bucket.imports.arn}/imports/*/*/source.csv"]
  }

  statement {
    sid       = "DownloadRejectedImportOutput"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.imports.arn}/imports/*/*/rejected.csv"]
  }
}

resource "aws_iam_policy" "api_task" {
  name        = "${local.resource_prefix}-api-task"
  description = "Least-privilege AWS access for the FinTrack API application."
  policy      = data.aws_iam_policy_document.api_task.json

  tags = {
    Name = "${local.resource_prefix}-api-task"
  }
}

resource "aws_iam_role_policy_attachment" "api_task" {
  role       = aws_iam_role.api_task.name
  policy_arn = aws_iam_policy.api_task.arn
}

data "aws_iam_policy_document" "worker_task" {
  statement {
    sid = "ConsumeApplicationEvents"

    actions = [
      "sqs:ChangeMessageVisibility",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
      "sqs:ReceiveMessage"
    ]

    resources = [
      aws_sqs_queue.transaction_processing.arn,
      aws_sqs_queue.import_jobs.arn
    ]
  }

  statement {
    sid       = "ReadImportSources"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.imports.arn}/imports/*/*/source.csv"]
  }

  statement {
    sid       = "WriteRejectedImportOutput"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.imports.arn}/imports/*/*/rejected.csv"]
  }
}

resource "aws_iam_policy" "worker_task" {
  name        = "${local.resource_prefix}-worker-task"
  description = "Least-privilege AWS access for the FinTrack worker application."
  policy      = data.aws_iam_policy_document.worker_task.json

  tags = {
    Name = "${local.resource_prefix}-worker-task"
  }
}

resource "aws_iam_role_policy_attachment" "worker_task" {
  role       = aws_iam_role.worker_task.name
  policy_arn = aws_iam_policy.worker_task.arn
}