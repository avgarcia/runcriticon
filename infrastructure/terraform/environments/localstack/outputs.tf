# Outputs del entorno de ensayo (útiles para inspeccionar lo creado en LocalStack).

output "vpc_id" {
  description = "ID de la VPC creada en LocalStack."
  value       = module.network.vpc_id
}

output "private_subnet_ids" {
  description = "Subnets privadas creadas en LocalStack."
  value       = module.network.private_subnet_ids
}

output "kms_key_arn" {
  description = "ARN de la KMS (emulada) que cifra los SecureString."
  value       = module.secrets.kms_key_arn
}

output "ssm_parameter_prefix" {
  description = "Prefijo de los parámetros SSM creados (/runcriticon/localstack)."
  value       = module.secrets.parameter_prefix
}
