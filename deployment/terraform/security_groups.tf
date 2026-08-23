data "aws_ec2_managed_prefix_list" "cloudfront_origin_facing" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${local.resource_prefix}-alb-sg"
  description = "Controls traffic for the public FinTrack ALB."
  vpc_id      = aws_vpc.fintrack.id

  tags = {
    Name = "${local.resource_prefix}-alb-sg"
  }
}

resource "aws_security_group" "api" {
  name        = "${local.resource_prefix}-api-sg"
  description = "Controls traffic for FinTrack API tasks."
  vpc_id      = aws_vpc.fintrack.id

  tags = {
    Name = "${local.resource_prefix}-api-sg"
  }
}

resource "aws_security_group" "worker" {
  name        = "${local.resource_prefix}-worker-sg"
  description = "Controls traffic for FinTrack worker tasks."
  vpc_id      = aws_vpc.fintrack.id

  tags = {
    Name = "${local.resource_prefix}-worker-sg"
  }
}

resource "aws_security_group" "postgresql" {
  name        = "${local.resource_prefix}-postgresql-sg"
  description = "Controls traffic for FinTrack PostgreSQL."
  vpc_id      = aws_vpc.fintrack.id

  tags = {
    Name = "${local.resource_prefix}-postgresql-sg"
  }
}

resource "aws_security_group" "redis" {
  name        = "${local.resource_prefix}-redis-sg"
  description = "Controls traffic for FinTrack Redis."
  vpc_id      = aws_vpc.fintrack.id

  tags = {
    Name = "${local.resource_prefix}-redis-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id

  description    = "Allow HTTP traffic from CloudFront origin-facing servers."
  prefix_list_id = data.aws_ec2_managed_prefix_list.cloudfront_origin_facing.id
  from_port      = 80
  ip_protocol    = "tcp"
  to_port        = 80
}

resource "aws_vpc_security_group_egress_rule" "alb_to_api" {
  security_group_id = aws_security_group.alb.id

  description                  = "Allow ALB traffic to API tasks."
  referenced_security_group_id = aws_security_group.api.id
  from_port                    = 8080
  ip_protocol                  = "tcp"
  to_port                      = 8080
}

resource "aws_vpc_security_group_ingress_rule" "api_from_alb" {
  security_group_id = aws_security_group.api.id

  description                  = "Allow traffic from the public ALB."
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8080
  ip_protocol                  = "tcp"
  to_port                      = 8080
}

resource "aws_vpc_security_group_egress_rule" "api_all" {
  security_group_id = aws_security_group.api.id

  description = "Allow API outbound traffic."
  cidr_ipv4   = "0.0.0.0/0"
  ip_protocol = "-1"
}

resource "aws_vpc_security_group_egress_rule" "worker_all" {
  security_group_id = aws_security_group.worker.id

  description = "Allow worker outbound traffic."
  cidr_ipv4   = "0.0.0.0/0"
  ip_protocol = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "postgresql_from_api" {
  security_group_id = aws_security_group.postgresql.id

  description                  = "Allow PostgreSQL from API tasks."
  referenced_security_group_id = aws_security_group.api.id
  from_port                    = 5432
  ip_protocol                  = "tcp"
  to_port                      = 5432
}

resource "aws_vpc_security_group_ingress_rule" "postgresql_from_worker" {
  security_group_id = aws_security_group.postgresql.id

  description                  = "Allow PostgreSQL from worker tasks."
  referenced_security_group_id = aws_security_group.worker.id
  from_port                    = 5432
  ip_protocol                  = "tcp"
  to_port                      = 5432
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_api" {
  security_group_id = aws_security_group.redis.id

  description                  = "Allow Redis from API tasks."
  referenced_security_group_id = aws_security_group.api.id
  from_port                    = 6379
  ip_protocol                  = "tcp"
  to_port                      = 6379
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_worker" {
  security_group_id = aws_security_group.redis.id

  description                  = "Allow Redis from worker tasks."
  referenced_security_group_id = aws_security_group.worker.id
  from_port                    = 6379
  ip_protocol                  = "tcp"
  to_port                      = 6379
}