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