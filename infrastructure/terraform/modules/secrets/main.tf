# Secretos en SSM Parameter Store (ADR-0006 D28, ADR-0013). Convención de nombres
# /runcriticon/{env}/{component}/{name} (configuracion-y-secretos-en-modulos.md §2).
#
# Dos clases de secreto:
#  - GENERADOS por Terraform (claves cripto): valor aleatorio, vive en el state cifrado.
#  - EXTERNOS (tokens de Postmark): se crean con placeholder y lifecycle ignore_changes; el
#    valor real se inyecta fuera de banda. Ningún valor real se commitea (ADR-0013 D12).

locals {
  prefix = "/runcriticon/${var.environment}"
}

# Los 5 tags obligatorios (Project/Environment/ManagedBy/CostCenter/Module) llegan vía
# default_tags del provider "aws.secrets" pasado a este módulo (ADR-0006 D25).

# KMS dedicada para cifrar los SecureString del proyecto (ADR-0006 D28).
resource "aws_kms_key" "ssm" {
  description         = "Cifra los SecureString de Runcriticon en SSM (${var.environment})"
  enable_key_rotation = true

  tags = { Name = "runcriticon-${var.environment}-ssm" }
}

resource "aws_kms_alias" "ssm" {
  name          = "alias/runcriticon-${var.environment}-ssm"
  target_key_id = aws_kms_key.ssm.id
}

# --- Secretos generados por Terraform (claves cripto de 256 bits en hex) ---
resource "random_bytes" "token_hmac_secret" {
  length = 32
}

resource "random_bytes" "userid_hash_salt" {
  length = 32
}

resource "aws_ssm_parameter" "token_hmac_secret" {
  name        = "${local.prefix}/security/token-hmac-secret"
  description = "HMAC de tokens de un solo uso (invitación, magic link, reseteo) y de email para rate-limiting (ADR-0003 D13)"
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = random_bytes.token_hmac_secret.hex
}

resource "aws_ssm_parameter" "userid_hash_salt" {
  name        = "${local.prefix}/crypto/userid-hash-salt"
  description = "Salt del hash determinístico de user_id en logs (ADR-0011 D5, ADR-0014 D9)"
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = random_bytes.userid_hash_salt.hex
}

# --- Secretos externos (Postmark): placeholder + ignore_changes (ADR-0005 D1, D9) ---
resource "aws_ssm_parameter" "postmark_server_token" {
  name        = "${local.prefix}/email/postmark-server-token"
  description = "Token de servidor de Postmark (ADR-0005 D1). Valor real fuera de banda."
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = var.postmark_placeholder

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "postmark_webhook_secret" {
  name        = "${local.prefix}/email/postmark-webhook-secret"
  description = "Secreto del webhook de Postmark (ADR-0005 D9). Valor real fuera de banda."
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = var.postmark_placeholder

  lifecycle {
    ignore_changes = [value]
  }
}

# Contraseña del admin bootstrap (ADR-0003 D3). Externo: el valor real se inyecta fuera
# de banda; Terraform crea el placeholder y no lo sobreescribe tras el primer apply.
resource "aws_ssm_parameter" "bootstrap_admin_password" {
  name        = "${local.prefix}/identidad/bootstrap-admin-password"
  description = "Contraseña del primer admin del club (ADR-0003 D3). Valor real fuera de banda."
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = var.bootstrap_placeholder

  lifecycle {
    ignore_changes = [value]
  }
}
