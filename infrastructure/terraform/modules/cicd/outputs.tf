# Outputs del módulo cicd.

output "deploy_role_arn" {
  description = "ARN del rol que GitHub Actions asume vía OIDC para desplegar. Se configura como secret/var del repo."
  value       = aws_iam_role.deploy.arn
}

output "oidc_provider_arn" {
  description = "ARN del OIDC provider de GitHub (creado o reutilizado)."
  value       = local.provider_arn
}
