resource "aws_s3_bucket" "imports" {
  bucket        = "${local.resource_prefix}-imports-${var.aws_account_id}-${var.aws_region}"
  force_destroy = true

  tags = {
    Name = "${local.resource_prefix}-imports"
  }
}

resource "aws_s3_bucket_public_access_block" "imports" {
  bucket = aws_s3_bucket.imports.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "imports" {
  bucket = aws_s3_bucket.imports.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "imports" {
  bucket = aws_s3_bucket.imports.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "imports" {
  bucket = aws_s3_bucket.imports.id

  rule {
    id     = "expire-import-artifacts"
    status = "Enabled"

    filter {
      prefix = "imports/"
    }

    expiration {
      days = 90
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

data "aws_iam_policy_document" "imports_require_tls" {
  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions = ["s3:*"]

    resources = [
      aws_s3_bucket.imports.arn,
      "${aws_s3_bucket.imports.arn}/*"
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "imports_require_tls" {
  bucket = aws_s3_bucket.imports.id
  policy = data.aws_iam_policy_document.imports_require_tls.json

  depends_on = [aws_s3_bucket_public_access_block.imports]
}