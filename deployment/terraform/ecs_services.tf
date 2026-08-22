resource "aws_ecs_service" "api" {
  name            = "${local.resource_prefix}-api-service"
  cluster         = aws_ecs_cluster.fintrack.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = 1

  platform_version = "LATEST"

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 120

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = 1
    weight            = 1
  }

  network_configuration {
    subnets          = [for subnet in aws_subnet.private_application : subnet.id]
    security_groups  = [aws_security_group.api.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api-service"
    container_port   = 8080
  }

  enable_ecs_managed_tags = true
  propagate_tags          = "SERVICE"

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [
    aws_ecs_cluster_capacity_providers.fintrack,
    aws_lb_listener.http
  ]

  tags = {
    Name = "${local.resource_prefix}-api-service"
  }
}