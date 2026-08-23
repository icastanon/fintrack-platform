output "terraform_context" {
  description = "Account, region, and naming context targeted by this configuration."

  value = {
    account_id      = var.aws_account_id
    region          = var.aws_region
    resource_prefix = local.resource_prefix
  }
}

output "vpc_id" {
  description = "ID of the FinTrack VPC."
  value       = aws_vpc.fintrack.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs keyed by Availability Zone."
  value = {
    for availability_zone, subnet in aws_subnet.public :
    availability_zone => subnet.id
  }
}

output "private_application_subnet_ids" {
  description = "Private application subnet IDs keyed by Availability Zone."
  value = {
    for availability_zone, subnet in aws_subnet.private_application :
    availability_zone => subnet.id
  }
}

output "private_data_subnet_ids" {
  description = "Private data subnet IDs keyed by Availability Zone."
  value = {
    for availability_zone, subnet in aws_subnet.private_data :
    availability_zone => subnet.id
  }
}

output "database_connection" {
  description = "Private RDS connection details and managed credential secret."

  value = {
    host                   = aws_db_instance.postgresql.address
    port                   = aws_db_instance.postgresql.port
    database_name          = aws_db_instance.postgresql.db_name
    master_user_secret_arn = aws_db_instance.postgresql.master_user_secret[0].secret_arn
  }
}

output "redis_connection" {
  description = "Private TLS-enabled Redis connection details."

  value = {
    host = aws_elasticache_replication_group.redis.primary_endpoint_address
    port = aws_elasticache_replication_group.redis.port
  }
}

output "application_queue_urls" {
  description = "URLs of the FinTrack application queues and their DLQs."

  value = {
    transaction_processing     = aws_sqs_queue.transaction_processing.url
    transaction_processing_dlq = aws_sqs_queue.transaction_processing_dlq.url
    import_jobs                = aws_sqs_queue.import_jobs.url
    import_jobs_dlq            = aws_sqs_queue.import_jobs_dlq.url
  }
}

output "imports_bucket_name" {
  description = "Name of the private transaction-import S3 bucket."
  value       = aws_s3_bucket.imports.id
}

output "runtime_secret_arns" {
  description = "Secrets injected into FinTrack ECS tasks."

  value = {
    database_credentials = aws_db_instance.postgresql.master_user_secret[0].secret_arn
    jwt_signing_key      = aws_secretsmanager_secret.jwt_signing_key.arn
  }
}

output "ecs_role_arns" {
  description = "IAM roles used by the FinTrack ECS tasks."

  value = {
    task_execution = aws_iam_role.ecs_task_execution.arn
    api_task       = aws_iam_role.api_task.arn
    worker_task    = aws_iam_role.worker_task.arn
  }
}

output "security_group_ids" {
  description = "Security groups used by FinTrack infrastructure."

  value = {
    alb        = aws_security_group.alb.id
    api        = aws_security_group.api.id
    worker     = aws_security_group.worker.id
    postgresql = aws_security_group.postgresql.id
    redis      = aws_security_group.redis.id
  }
}

output "ecr_repository_urls" {
  description = "Private ECR repositories used by the FinTrack deployment pipeline."

  value = {
    for service_name, repository in aws_ecr_repository.service :
    service_name => repository.repository_url
  }
}

output "ecs_cluster" {
  description = "ECS cluster that runs the FinTrack API and worker services."

  value = {
    name = aws_ecs_cluster.fintrack.name
    arn  = aws_ecs_cluster.fintrack.arn
  }
}

output "ecs_log_group_names" {
  description = "CloudWatch log groups used by the FinTrack ECS services."

  value = {
    for service_name, log_group in aws_cloudwatch_log_group.ecs_service :
    service_name => log_group.name
  }
}

output "api_task_definition" {
  description = "API ECS task definition registered for Fargate."

  value = {
    family   = aws_ecs_task_definition.api.family
    arn      = aws_ecs_task_definition.api.arn
    revision = aws_ecs_task_definition.api.revision
  }
}

output "worker_task_definition" {
  description = "Worker ECS task definition registered for Fargate."

  value = {
    family   = aws_ecs_task_definition.worker.family
    arn      = aws_ecs_task_definition.worker.arn
    revision = aws_ecs_task_definition.worker.revision
  }
}

output "api_load_balancer" {
  description = "Public API load balancer and target-group details."

  value = {
    dns_name         = aws_lb.api.dns_name
    arn              = aws_lb.api.arn
    target_group_arn = aws_lb_target_group.api.arn
  }
}

output "api_ecs_service" {
  description = "API ECS service running behind the public load balancer."

  value = {
    name = aws_ecs_service.api.name
    arn  = aws_ecs_service.api.id
  }
}

output "worker_ecs_service" {
  description = "Private worker ECS service that processes SQS messages."

  value = {
    name = aws_ecs_service.worker.name
    arn  = aws_ecs_service.worker.id
  }
}

output "cloudwatch_operations_dashboard" {
  description = "CloudWatch operations dashboard name."
  value       = aws_cloudwatch_dashboard.operations.dashboard_name
}

output "operations_alert_topic_arn" {
  description = "SNS topic used by FinTrack operational CloudWatch alarms."
  value       = aws_sns_topic.operations_alerts.arn
}

output "github_actions_oidc_provider_arn" {
  description = "GitHub Actions OIDC identity-provider ARN."
  value       = aws_iam_openid_connect_provider.github_actions.arn
}

output "github_actions_deploy_role_arn" {
  description = "IAM role assumed by the FinTrack GitHub Actions deployment workflow."
  value       = aws_iam_role.github_actions_deploy.arn
}

output "cloudfront_distribution" {
  description = "CloudFront distribution serving the frontend and proxying backend routes."

  value = {
    id          = aws_cloudfront_distribution.frontend.id
    domain_name = aws_cloudfront_distribution.frontend.domain_name
  }
}