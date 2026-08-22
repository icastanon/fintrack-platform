resource "aws_lb" "api" {
  name               = "${local.resource_prefix}-api-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [for subnet in aws_subnet.public : subnet.id]

  enable_deletion_protection = false
  drop_invalid_header_fields = true
  idle_timeout               = 60

  tags = {
    Name = "${local.resource_prefix}-api-alb"
  }
}

resource "aws_lb_target_group" "api" {
  name        = "${local.resource_prefix}-api-tg"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.fintrack.id

  deregistration_delay = 30
  protocol_version     = "HTTP1"

  health_check {
    enabled             = true
    path                = "/actuator/health/readiness"
    port                = "traffic-port"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${local.resource_prefix}-api-tg"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }

  tags = {
    Name = "${local.resource_prefix}-api-http-listener"
  }
}