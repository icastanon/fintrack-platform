resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${local.resource_prefix}-redis"
  description          = "FinTrack development cache and distributed rate limiter."

  engine               = "redis"
  engine_version       = "7.1"
  node_type            = "cache.t4g.micro"
  parameter_group_name = "default.redis7"
  port                 = 6379

  cluster_mode       = "disabled"
  num_cache_clusters = 1

  automatic_failover_enabled = false
  multi_az_enabled           = false

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  transit_encryption_mode    = "required"

  snapshot_retention_limit   = 0
  auto_minor_version_upgrade = true
  apply_immediately          = true

  tags = {
    Name = "${local.resource_prefix}-redis"
  }
}