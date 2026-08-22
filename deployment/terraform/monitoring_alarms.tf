locals {
  monitored_ecs_services = {
    api    = aws_ecs_service.api.name
    worker = aws_ecs_service.worker.name
  }

  monitored_dlq_queues = {
    transaction = aws_sqs_queue.transaction_processing_dlq.name
    import      = aws_sqs_queue.import_jobs_dlq.name
  }

  monitored_queue_ages = {
    transaction = {
      queue_name        = aws_sqs_queue.transaction_processing.name
      threshold_seconds = 300
    }
    import = {
      queue_name        = aws_sqs_queue.import_jobs.name
      threshold_seconds = 600
    }
  }
}

resource "aws_sns_topic" "operations_alerts" {
  name = "${local.resource_prefix}-operations-alerts"

  tags = {
    Name = "${local.resource_prefix}-operations-alerts"
  }
}

resource "aws_sns_topic_subscription" "operations_email" {
  topic_arn = aws_sns_topic.operations_alerts.arn
  protocol  = "email"
  endpoint  = var.operations_notification_email
}

resource "aws_cloudwatch_metric_alarm" "ecs_service_not_running" {
  for_each = local.monitored_ecs_services

  alarm_name          = "${local.resource_prefix}-${each.key}-not-running"
  alarm_description   = "The FinTrack ${each.key} ECS service has no running task."
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  metric_name         = "RunningTaskCount"
  namespace           = "ECS/ContainerInsights"
  period              = 60
  statistic           = "Minimum"
  threshold           = 1
  treat_missing_data  = "breaching"

  dimensions = {
    ClusterName = aws_ecs_cluster.fintrack.name
    ServiceName = each.value
  }

  alarm_actions = [aws_sns_topic.operations_alerts.arn]
  ok_actions    = [aws_sns_topic.operations_alerts.arn]

  tags = {
    Name = "${local.resource_prefix}-${each.key}-not-running"
  }
}

resource "aws_cloudwatch_metric_alarm" "api_no_healthy_targets" {
  alarm_name          = "${local.resource_prefix}-api-no-healthy-targets"
  alarm_description   = "The API target group has no healthy ECS tasks."
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  metric_name         = "HealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Minimum"
  threshold           = 1
  treat_missing_data  = "breaching"

  dimensions = {
    LoadBalancer = aws_lb.api.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }

  alarm_actions = [aws_sns_topic.operations_alerts.arn]
  ok_actions    = [aws_sns_topic.operations_alerts.arn]

  tags = {
    Name = "${local.resource_prefix}-api-no-healthy-targets"
  }
}

resource "aws_cloudwatch_metric_alarm" "api_target_5xx" {
  alarm_name          = "${local.resource_prefix}-api-target-5xx"
  alarm_description   = "The API produced at least five server errors within five minutes."
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  statistic           = "Sum"
  threshold           = 5
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.api.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }

  alarm_actions = [aws_sns_topic.operations_alerts.arn]
  ok_actions    = [aws_sns_topic.operations_alerts.arn]

  tags = {
    Name = "${local.resource_prefix}-api-target-5xx"
  }
}

resource "aws_cloudwatch_metric_alarm" "dlq_message_present" {
  for_each = local.monitored_dlq_queues

  alarm_name          = "${local.resource_prefix}-${each.key}-dlq-message-present"
  alarm_description   = "The FinTrack ${each.key} DLQ contains at least one failed message."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = each.value
  }

  alarm_actions = [aws_sns_topic.operations_alerts.arn]
  ok_actions    = [aws_sns_topic.operations_alerts.arn]

  tags = {
    Name = "${local.resource_prefix}-${each.key}-dlq-message-present"
  }
}

resource "aws_cloudwatch_metric_alarm" "queue_oldest_message" {
  for_each = local.monitored_queue_ages

  alarm_name          = "${local.resource_prefix}-${each.key}-queue-backlog"
  alarm_description   = "The oldest ${each.key} message exceeded the expected processing time."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  metric_name         = "ApproximateAgeOfOldestMessage"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  threshold           = each.value.threshold_seconds
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = each.value.queue_name
  }

  alarm_actions = [aws_sns_topic.operations_alerts.arn]
  ok_actions    = [aws_sns_topic.operations_alerts.arn]

  tags = {
    Name = "${local.resource_prefix}-${each.key}-queue-backlog"
  }
}