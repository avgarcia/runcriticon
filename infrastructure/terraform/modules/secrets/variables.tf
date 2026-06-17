# Variables del módulo secrets (ADR-0006 D28, ADR-0013).

variable "environment" {
  description = "Entorno: staging | production. Forma el prefijo /runcriticon/{env}/ de SSM."
  type        = string
}

variable "postmark_placeholder" {
  description = <<-EOT
    Valor inicial de los secretos EXTERNOS de Postmark (server token y webhook secret). Nunca el
    valor real: este se inyecta fuera de banda y Terraform lo ignora (lifecycle ignore_changes).
  EOT
  type        = string
  default     = "REEMPLAZAR_FUERA_DE_BANDA"
}

variable "bootstrap_placeholder" {
  description = "Valor placeholder del secreto bootstrap-admin-password. El real se inyecta fuera de banda."
  type        = string
  default     = "PLACEHOLDER_CAMBIAR_ANTES_DEL_PRIMER_DEPLOY"
  sensitive   = true
}
