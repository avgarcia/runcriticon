# Outputs del módulo database. runtime (Bloque 4) los inyecta como configuración de la app.
# La contraseña NO se expone como output: se lee de SSM en runtime (ADR-0013).

output "db_address" {
  description = "Host de RDS (sin puerto)."
  value       = aws_db_instance.this.address
}

output "db_port" {
  description = "Puerto de RDS."
  value       = aws_db_instance.this.port
}

output "db_endpoint" {
  description = "Endpoint de RDS (host:puerto)."
  value       = aws_db_instance.this.endpoint
}

output "db_name" {
  description = "Nombre de la base de datos."
  value       = aws_db_instance.this.db_name
}

output "master_username" {
  description = "Usuario maestro de PostgreSQL."
  value       = aws_db_instance.this.username
}

output "db_password_parameter_name" {
  description = "Nombre del parámetro SSM con la contraseña (ADR-0013 /runcriticon/{env}/db/password)."
  value       = aws_ssm_parameter.db_password.name
}

output "db_password_parameter_arn" {
  description = "ARN del parámetro SSM con la contraseña (lo consume runtime como runtime_environment_secret)."
  value       = aws_ssm_parameter.db_password.arn
}

output "kms_key_arn" {
  description = "ARN de la KMS de cifrado en reposo de RDS."
  value       = aws_kms_key.rds.arn
}
