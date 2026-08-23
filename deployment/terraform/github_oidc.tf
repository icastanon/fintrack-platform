resource "aws_iam_openid_connect_provider" "github_actions" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  tags = {
    Name = "${local.resource_prefix}-github-actions-oidc"
  }
}

data "aws_iam_policy_document" "github_actions_deploy_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_repository_owner}@${var.github_repository_owner_id}/${var.github_repository_name}@${var.github_repository_id}:ref:refs/heads/${var.github_deployment_branch}"
      ]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name                 = "${local.resource_prefix}-github-actions-deploy"
  description          = "Assumed by the FinTrack GitHub Actions deployment workflow."
  assume_role_policy   = data.aws_iam_policy_document.github_actions_deploy_assume_role.json
  max_session_duration = 3600

  tags = {
    Name = "${local.resource_prefix}-github-actions-deploy"
  }
}