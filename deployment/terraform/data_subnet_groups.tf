resource "aws_db_subnet_group" "postgresql" {
  name        = "${local.resource_prefix}-postgresql"
  description = "Private data subnets for FinTrack PostgreSQL."
  subnet_ids  = [for subnet in aws_subnet.private_data : subnet.id]

  tags = {
    Name = "${local.resource_prefix}-postgresql-subnet-group"
  }
}

resource "aws_elasticache_subnet_group" "redis" {
  name        = "${local.resource_prefix}-redis"
  description = "Private data subnets for FinTrack Redis."
  subnet_ids  = [for subnet in aws_subnet.private_data : subnet.id]

  tags = {
    Name = "${local.resource_prefix}-redis-subnet-group"
  }
}