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