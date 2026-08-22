resource "aws_ecs_cluster" "fintrack" {
  name = "${local.resource_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name = "${local.resource_prefix}-cluster"
  }
}

resource "aws_ecs_cluster_capacity_providers" "fintrack" {
  cluster_name = aws_ecs_cluster.fintrack.name

  capacity_providers = [
    "FARGATE",
    "FARGATE_SPOT"
  ]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = 1
    weight            = 1
  }
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.resource_prefix}-api-service"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.api_task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "api-service"
      image     = "${aws_ecr_repository.service["api-service"].repository_url}:${var.api_image_tag}"
      essential = true

      portMappings = [
        {
          name          = "http"
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
          appProtocol   = "http"
        }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "deployment" },
        { name = "FINTRACK_ENVIRONMENT", value = var.environment },
        { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.postgresql.address}:${aws_db_instance.postgresql.port}/${aws_db_instance.postgresql.db_name}" },
        { name = "REDIS_HOST", value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_PORT", value = tostring(aws_elasticache_replication_group.redis.port) },
        { name = "SPRING_DATA_REDIS_SSL_ENABLED", value = "true" },
        { name = "FINTRACK_OUTBOX_RELAY_ENABLED", value = "true" },
        { name = "FINTRACK_S3_IMPORT_BUCKET", value = aws_s3_bucket.imports.id },
        { name = "FINTRACK_SQS_TRANSACTION_PROCESSING_QUEUE", value = aws_sqs_queue.transaction_processing.name },
        { name = "FINTRACK_SQS_IMPORT_JOBS_QUEUE", value = aws_sqs_queue.import_jobs.name },
        { name = "SPRING_CLOUD_AWS_REGION_STATIC", value = var.aws_region },
        { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError" }
      ]

      secrets = [
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "${aws_db_instance.postgresql.master_user_secret[0].secret_arn}:username::"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${aws_db_instance.postgresql.master_user_secret[0].secret_arn}:password::"
        },
        {
          name      = "JWT_SECRET"
          valueFrom = aws_secretsmanager_secret.jwt_signing_key.arn
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs_service["api-service"].name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "api"
        }
      }

      stopTimeout = 60
    }
  ])

  tags = {
    Name = "${local.resource_prefix}-api-service"
  }
}