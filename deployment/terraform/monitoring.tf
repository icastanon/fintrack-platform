resource "aws_cloudwatch_dashboard" "operations" {
  dashboard_name = "${local.resource_prefix}-operations"

  dashboard_body = jsonencode({
    start          = "-PT6H"
    periodOverride = "inherit"

    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6

        properties = {
          title  = "ECS service utilization"
          region = var.aws_region
          period = 60
          stat   = "Average"
          view   = "timeSeries"

          yAxis = {
            left = {
              min = 0
              max = 100
            }
          }

          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.fintrack.name, "ServiceName", aws_ecs_service.api.name, {
              label = "API CPU"
            }],
            ["AWS/ECS", "MemoryUtilization", "ClusterName", aws_ecs_cluster.fintrack.name, "ServiceName", aws_ecs_service.api.name, {
              label = "API memory"
            }],
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.fintrack.name, "ServiceName", aws_ecs_service.worker.name, {
              label = "Worker CPU"
            }],
            ["AWS/ECS", "MemoryUtilization", "ClusterName", aws_ecs_cluster.fintrack.name, "ServiceName", aws_ecs_service.worker.name, {
              label = "Worker memory"
            }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6

        properties = {
          title  = "Running ECS tasks"
          region = var.aws_region
          period = 60
          stat   = "Minimum"
          view   = "timeSeries"

          yAxis = {
            left = {
              min = 0
            }
          }

          metrics = [
            ["ECS/ContainerInsights", "RunningTaskCount", "ClusterName", aws_ecs_cluster.fintrack.name, "ServiceName", aws_ecs_service.api.name, {
              label = "API running tasks"
            }],
            ["ECS/ContainerInsights", "RunningTaskCount", "ClusterName", aws_ecs_cluster.fintrack.name, "ServiceName", aws_ecs_service.worker.name, {
              label = "Worker running tasks"
            }],
            ["AWS/ApplicationELB", "HealthyHostCount", "LoadBalancer", aws_lb.api.arn_suffix, "TargetGroup", aws_lb_target_group.api.arn_suffix, {
              label = "Healthy API targets"
            }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6

        properties = {
          title  = "API traffic and latency"
          region = var.aws_region
          period = 60
          view   = "timeSeries"

          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", aws_lb.api.arn_suffix, {
              label = "Requests"
              stat  = "Sum"
            }],
            ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", aws_lb.api.arn_suffix, {
              label = "API 5xx responses"
              stat  = "Sum"
            }],
            ["AWS/ApplicationELB", "HTTPCode_ELB_5XX_Count", "LoadBalancer", aws_lb.api.arn_suffix, {
              label = "ALB 5xx responses"
              stat  = "Sum"
            }],
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", aws_lb.api.arn_suffix, "TargetGroup", aws_lb_target_group.api.arn_suffix, {
              label = "Response time p95"
              stat  = "p95"
              yAxis = "right"
            }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6

        properties = {
          title  = "SQS available-message backlog"
          region = var.aws_region
          period = 60
          stat   = "Maximum"
          view   = "timeSeries"

          yAxis = {
            left = {
              min = 0
            }
          }

          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.transaction_processing.name, {
              label = "Transaction queue"
            }],
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.import_jobs.name, {
              label = "Import queue"
            }],
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.transaction_processing_dlq.name, {
              label = "Transaction DLQ"
            }],
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.import_jobs_dlq.name, {
              label = "Import DLQ"
            }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6

        properties = {
          title  = "SQS age of oldest message"
          region = var.aws_region
          period = 60
          stat   = "Maximum"
          view   = "timeSeries"

          yAxis = {
            left = {
              min   = 0
              label = "Seconds"
            }
          }

          metrics = [
            ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", aws_sqs_queue.transaction_processing.name, {
              label = "Transaction queue"
            }],
            ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", aws_sqs_queue.import_jobs.name, {
              label = "Import queue"
            }],
            ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", aws_sqs_queue.transaction_processing_dlq.name, {
              label = "Transaction DLQ"
            }],
            ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", aws_sqs_queue.import_jobs_dlq.name, {
              label = "Import DLQ"
            }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 12
        width  = 12
        height = 6

        properties = {
          title  = "RDS and Redis utilization"
          region = var.aws_region
          period = 60
          stat   = "Average"
          view   = "timeSeries"

          yAxis = {
            left = {
              min = 0
              max = 100
            }
          }

          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.postgresql.identifier, {
              label = "PostgreSQL CPU"
            }],
            ["AWS/ElastiCache", "EngineCPUUtilization", "CacheClusterId", tolist(aws_elasticache_replication_group.redis.member_clusters)[0], "CacheNodeId", "0001", {
              label = "Redis engine CPU"
            }],
            ["AWS/ElastiCache", "DatabaseMemoryUsagePercentage", "CacheClusterId", tolist(aws_elasticache_replication_group.redis.member_clusters)[0], "CacheNodeId", "0001", {
              label = "Redis memory"
            }],
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", aws_db_instance.postgresql.identifier, {
              label = "PostgreSQL connections"
              yAxis = "right"
            }]
          ]
        }
      }
    ]
  })
}