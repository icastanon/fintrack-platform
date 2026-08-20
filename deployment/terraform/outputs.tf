output "terraform_context" {
  description = "Account, region, and naming context targeted by this configuration."

  value = {
    account_id      = var.aws_account_id
    region          = var.aws_region
    resource_prefix = local.resource_prefix
  }
}