# Outputs del entorno staging (post-apply): URL del servicio, ECR y rol de despliegue.

output "app_service_url" {
  description = "URL pública del servicio App Runner (*.awsapprunner.com)."
  value       = module.runtime.service_url
}

output "ecr_repository_url" {
  description = "Repositorio ECR donde el pipeline replica la imagen (ADR-0010 D2)."
  value       = module.runtime.ecr_repository_url
}

output "deploy_role_arn" {
  description = "Rol que GitHub Actions asume vía OIDC para desplegar (configúralo como var del repo)."
  value       = module.cicd.deploy_role_arn
}

output "db_endpoint" {
  description = "Endpoint de RDS (host:puerto)."
  value       = module.database.db_endpoint
}
