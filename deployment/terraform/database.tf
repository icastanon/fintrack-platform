resource "aws_db_instance" "postgresql" {
  identifier = "${local.resource_prefix}-postgresql"

  engine         = "postgres"
  engine_version = "17.10"
  instance_class = "db.t4g.micro"

  db_name  = "fintrack"
  username = "fintrack"
  port     = 5432

  manage_master_user_password = true

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.postgresql.name
  vpc_security_group_ids = [aws_security_group.postgresql.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 1
  copy_tags_to_snapshot   = true

  auto_minor_version_upgrade = true
  apply_immediately          = true

  deletion_protection      = false
  skip_final_snapshot      = true
  delete_automated_backups = true

  tags = {
    Name = "${local.resource_prefix}-postgresql"
  }
}