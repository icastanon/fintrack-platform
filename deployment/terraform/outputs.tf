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
