# Outputs del módulo secrets. database (Bloque 2B) consume la KMS; runtime (Bloque 4)
# consume los ARN de los parámetros para referenciarlos como env vars de App Runner.

output "kms_key_arn" {
  description = "ARN de la KMS que cifra los SecureString del proyecto."
  value       = aws_kms_key.ssm.arn
}

output "kms_key_id" {
  description = "ID de la KMS que cifra los SecureString del proyecto."
  value       = aws_kms_key.ssm.id
}

output "parameter_arns" {
  description = "ARN de cada parámetro SSM, por nombre lógico (para policies IAM de App Runner)."
  value = {
    session_signing_key      = aws_ssm_parameter.session_signing_key.arn
    magic_link_signing_key   = aws_ssm_parameter.magic_link_signing_key.arn
    userid_hash_salt         = aws_ssm_parameter.userid_hash_salt.arn
    postmark_server_token    = aws_ssm_parameter.postmark_server_token.arn
    postmark_webhook_secret  = aws_ssm_parameter.postmark_webhook_secret.arn
    bootstrap_admin_password = aws_ssm_parameter.bootstrap_admin_password.arn
  }
}

output "parameter_prefix" {
  description = "Prefijo común de los parámetros del entorno (/runcriticon/{env})."
  value       = local.prefix
}
