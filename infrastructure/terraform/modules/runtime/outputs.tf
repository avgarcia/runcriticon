# Outputs del módulo runtime. cicd consume los ARN para acotar su policy de despliegue;
# el equipo usa la service_url para los smoke tests post-despliegue (ADR-0010).

output "service_arn" {
  description = "ARN del servicio App Runner."
  value       = aws_apprunner_service.this.arn
}

output "service_url" {
  description = "URL pública por defecto del servicio (*.awsapprunner.com)."
  value       = aws_apprunner_service.this.service_url
}

output "ecr_repository_url" {
  description = "URL del repositorio ECR (destino de la réplica desde GHCR)."
  value       = aws_ecr_repository.app.repository_url
}

output "ecr_repository_arn" {
  description = "ARN del repositorio ECR (para la policy de push de cicd)."
  value       = aws_ecr_repository.app.arn
}

output "instance_role_arn" {
  description = "ARN del instance role de App Runner (para iam:PassRole de cicd)."
  value       = aws_iam_role.instance.arn
}

output "access_role_arn" {
  description = "ARN del access role de App Runner (para iam:PassRole de cicd)."
  value       = aws_iam_role.access.arn
}
