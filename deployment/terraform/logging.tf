locals {
  ecs_services = toset([
    "api-service",
    "worker-service"
  ])
}

resource "aws_cloudwatch_log_group" "ecs_service" {
  for_each = local.ecs_services

  name              = "/ecs/${local.resource_prefix}/${each.value}"
  retention_in_days = 14

  tags = {
    Name = "${local.resource_prefix}-${each.value}-logs"
  }
}