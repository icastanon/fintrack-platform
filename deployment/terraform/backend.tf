terraform {
  backend "s3" {
    bucket              = "fintrack-terraform-state-533951097801-us-east-1"
    key                 = "fintrack/dev/terraform.tfstate"
    region              = "us-east-1"
    encrypt             = true
    use_lockfile        = true
    allowed_account_ids = ["533951097801"]
  }
}