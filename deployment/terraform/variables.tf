variable "aws_account_id" {
  description = "AWS account where FinTrack infrastructure is deployed."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must contain exactly 12 digits."
  }
}

variable "aws_region" {
  description = "AWS region where FinTrack infrastructure is deployed."
  type        = string
  default     = "us-east-1"

  validation {
    condition     = var.aws_region == "us-east-1"
    error_message = "FinTrack currently deploys only to us-east-1."
  }
}

variable "project_name" {
  description = "Project name used in resource names and tags."
  type        = string
  default     = "fintrack"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "dev"
}

variable "owner" {
  description = "Owner tag applied to FinTrack resources."
  type        = string
  default     = "icastanon"
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block assigned to the FinTrack VPC."
  type        = string
  default     = "10.20.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "vpc_cidr must be a valid IPv4 CIDR block."
  }
}

variable "api_image_tag" {
  description = "ECR image tag used by the API ECS task definition."
  type        = string
  default     = "bootstrap"

  validation {
    condition     = length(trimspace(var.api_image_tag)) > 0
    error_message = "api_image_tag must not be blank."
  }
}

variable "worker_image_tag" {
  description = "ECR image tag used by the worker ECS task definition."
  type        = string
  default     = "bootstrap"

  validation {
    condition     = length(trimspace(var.worker_image_tag)) > 0
    error_message = "worker_image_tag must not be blank."
  }
}

variable "operations_notification_email" {
  description = "Email address that receives FinTrack operational alarm notifications."
  type        = string

  validation {
    condition     = can(regex("^[^@]+@[^@]+\\.[^@]+$", var.operations_notification_email))
    error_message = "operations_notification_email must be a valid email address."
  }
}

variable "github_repository_owner" {
  description = "GitHub owner of the FinTrack repository."
  type        = string
  default     = "icastanon"
}

variable "github_repository_name" {
  description = "GitHub repository containing the FinTrack monorepo."
  type        = string
  default     = "fintrack-platform"
}

variable "github_repository_owner_id" {
  description = "Immutable numeric GitHub repository-owner ID."
  type        = string
}

variable "github_repository_id" {
  description = "Immutable numeric GitHub repository ID."
  type        = string
}

variable "github_deployment_branch" {
  description = "Git branch authorized to deploy FinTrack."
  type        = string
  default     = "main"
}