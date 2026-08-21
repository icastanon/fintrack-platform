resource "aws_secretsmanager_secret" "jwt_signing_key" {
  name        = "${local.resource_prefix}/api/jwt-signing-key"
  description = "JWT signing key used by the FinTrack API service."

  recovery_window_in_days = 0

  tags = {
    Name = "${local.resource_prefix}-jwt-signing-key"
  }
}