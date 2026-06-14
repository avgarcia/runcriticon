# Secretos en SSM Parameter Store (ADR-0006 D28, ADR-0013). Convención de nombres
# /runcriticon/{env}/{component}/{name} (configuracion-y-secretos-en-modulos.md §2).
#
# Dos clases de secreto:
#  - GENERADOS por Terraform (claves cripto): valor aleatorio, vive en el state cifrado.
#  - EXTERNOS (tokens de Postmark): se crean con placeholder y lifecycle ignore_changes; el
#    valor real se inyecta fuera de banda. Ningún valor real se commitea (ADR-0013 D12).

locals {
  module_tags = { Module = "seguridad" }
  prefix      = "/runcriticon/${var.environment}"
}

# KMS dedicada para cifrar los SecureString del proyecto (ADR-0006 D28).
resource "aws_kms_key" "ssm" {
  description         = "Cifra los SecureString de Runcriticon en SSM (${var.environment})"
  enable_key_rotation = true

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-ssm" })
}

resource "aws_kms_alias" "ssm" {
  name          = "alias/runcriticon-${var.environment}-ssm"
  target_key_id = aws_kms_key.ssm.id
}

# --- Secretos generados por Terraform (claves cripto de 256 bits en hex) ---
resource "random_bytes" "session_signing_key" {
  length = 32
}

resource "random_bytes" "magic_link_signing_key" {
  length = 32
}

resource "random_bytes" "userid_hash_salt" {
  length = 32
}

resource "aws_ssm_parameter" "session_signing_key" {
  name        = "${local.prefix}/crypto/session-signing-key"
  description = "Firma de Spring Session (ADR-0003 D10)"
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = random_bytes.session_signing_key.hex

  tags = local.module_tags
}

resource "aws_ssm_parameter" "magic_link_signing_key" {
  name        = "${local.prefix}/crypto/magic-link-signing-key"
  description = "Firma de tokens magic link (ADR-0003 D5/D8)"
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = random_bytes.magic_link_signing_key.hex

  tags = local.module_tags
}

resource "aws_ssm_parameter" "userid_hash_salt" {
  name        = "${local.prefix}/crypto/userid-hash-salt"
  description = "Salt del hash determinístico de user_id en logs (ADR-0011 D5, ADR-0014 D9)"
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = random_bytes.userid_hash_salt.hex

  tags = local.module_tags
}

# --- Secretos externos (Postmark): placeholder + ignore_changes (ADR-0005 D1, D9) ---
resource "aws_ssm_parameter" "postmark_server_token" {
  name        = "${local.prefix}/email/postmark-server-token"
  description = "Token de servidor de Postmark (ADR-0005 D1). Valor real fuera de banda."
  type        = "SecureString"
  key_id      = aws_kms_key.ssm.id
  value       = var.postmark_placeholder

  tags = local.module_tags

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

  tags = local.module_tags

  lifecycle {
    ignore_changes = [value]
  }
}
