data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid       = "AuthenticateToEcr"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PushFinTrackImages"

    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
      "ecr:DescribeImages"
    ]

    resources = [
      aws_ecr_repository.service["api-service"].arn,
      aws_ecr_repository.service["worker-service"].arn
    ]
  }

  statement {
    sid = "ManageTaskDefinitionRevisions"

    actions = [
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition"
    ]

    resources = ["*"]
  }

  statement {
    sid = "DeployFinTrackServices"

    actions = [
      "ecs:DescribeServices",
      "ecs:UpdateService"
    ]

    resources = [
      "arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:service/${aws_ecs_cluster.fintrack.name}/${aws_ecs_service.api.name}",
      "arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:service/${aws_ecs_cluster.fintrack.name}/${aws_ecs_service.worker.name}"
    ]
  }

  statement {
    sid     = "PassFinTrackTaskRoles"
    actions = ["iam:PassRole"]

    resources = [
      aws_iam_role.ecs_task_execution.arn,
      aws_iam_role.api_task.arn,
      aws_iam_role.worker_task.arn
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  statement {
    sid = "ListFrontendBucket"

    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket"
    ]

    resources = [aws_s3_bucket.frontend.arn]
  }

  statement {
    sid = "DeployFrontendObjects"

    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject"
    ]

    resources = ["${aws_s3_bucket.frontend.arn}/*"]
  }

  statement {
    sid = "RefreshFrontendDistribution"

    actions = [
      "cloudfront:CreateInvalidation",
      "cloudfront:GetInvalidation"
    ]

    resources = [aws_cloudfront_distribution.frontend.arn]
  }
}

resource "aws_iam_policy" "github_actions_deploy" {
  name        = "${local.resource_prefix}-github-actions-deploy"
  description = "Least-privilege permissions for the FinTrack GitHub Actions deployment workflow."
  policy      = data.aws_iam_policy_document.github_actions_deploy.json

  tags = {
    Name = "${local.resource_prefix}-github-actions-deploy"
  }
}

resource "aws_iam_role_policy_attachment" "github_actions_deploy" {
  role       = aws_iam_role.github_actions_deploy.name
  policy_arn = aws_iam_policy.github_actions_deploy.arn
}