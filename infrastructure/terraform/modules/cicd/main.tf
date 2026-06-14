# CI/CD federado por OIDC (ADR-0006 D27, ADR-0010 D10). GitHub Actions asume un rol con
# tokens temporales (sin claves de larga vida). El rol está ACOTADO a despliegue: empujar la
# imagen a ECR, redesplegar App Runner, leer los secretos SSM del entorno y pasar los roles de
# App Runner. El `terraform apply` de infraestructura lo ejecuta un operador admin con SSO en el
# bootstrap (ADR-0006 D13/D27), no este rol.

locals {
  module_tags  = { Module = "cicd" }
  subject      = var.github_subject_filter != null ? var.github_subject_filter : "repo:${var.github_org}/${var.github_repo}:*"
  provider_arn = var.create_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.existing_oidc_provider_arn
}

# OIDC provider de GitHub Actions. Recurso de CUENTA: solo lo crea el primer entorno.
# El thumbprint ya no lo valida AWS para este IdP, pero el campo sigue siendo obligatorio.
resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_oidc_provider ? 1 : 0

  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1", "1c58a3a8518e8759bf075b76b750d4f2df264fcd"]

  tags = merge(local.module_tags, { Name = "github-actions" })
}

# Confianza federada: solo tokens de este repo (y el filtro de sub) pueden asumir el rol.
data "aws_iam_policy_document" "assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.subject]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name               = "github-actions-runcriticon-${var.environment}"
  description        = "Rol de despliegue federado por OIDC para GitHub Actions (${var.environment})"
  assume_role_policy = data.aws_iam_policy_document.assume.json

  tags = merge(local.module_tags, { Name = "github-actions-runcriticon-${var.environment}" })
}

# Permisos mínimos de despliegue (ADR-0006 D27).
data "aws_iam_policy_document" "deploy" {
  # Token de registro de ECR (requiere recurso "*").
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # Push de la imagen replicada desde GHCR al ECR del proyecto.
  statement {
    sid    = "EcrPush"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [var.ecr_repository_arn]
  }

  # Redespliegue de App Runner (aws apprunner start-deployment / describe).
  statement {
    sid    = "AppRunnerDeploy"
    effect = "Allow"
    actions = [
      "apprunner:StartDeployment",
      "apprunner:UpdateService",
      "apprunner:DescribeService",
      "apprunner:ListOperations",
    ]
    resources = [var.apprunner_service_arn]
  }

  statement {
    sid       = "AppRunnerList"
    effect    = "Allow"
    actions   = ["apprunner:ListServices"]
    resources = ["*"]
  }

  # Lectura de los secretos del entorno (smoke tests, configuración del deploy).
  statement {
    sid    = "SsmRead"
    effect = "Allow"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = [var.ssm_parameter_path_arn]
  }

  # Descifrado de los SecureString leídos.
  dynamic "statement" {
    for_each = length(var.kms_key_arns) > 0 ? [1] : []
    content {
      sid       = "KmsDecrypt"
      effect    = "Allow"
      actions   = ["kms:Decrypt"]
      resources = var.kms_key_arns
    }
  }

  # Pasar los roles de App Runner durante el (re)despliegue.
  dynamic "statement" {
    for_each = length(var.passrole_arns) > 0 ? [1] : []
    content {
      sid       = "PassAppRunnerRoles"
      effect    = "Allow"
      actions   = ["iam:PassRole"]
      resources = var.passrole_arns
    }
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
