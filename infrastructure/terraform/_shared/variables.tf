# Variables comunes a todos los entornos
# Las variables específicas de cada entorno viven en environments/{env}/variables.tf

variable "aws_region" {
  description = "Región AWS donde vive el proyecto (ADR-0006 D1 — eu-west-1 fija)."
  type        = string
  default     = "eu-west-1"
  validation {
    condition     = var.aws_region == "eu-west-1"
    error_message = "ADR-0006 D1 fija eu-west-1 (Irlanda) como única región. Si necesitas otra región, reabre el ADR."
  }
}

variable "environment" {
  description = "Entorno: staging, production o shared (recursos no por entorno)."
  type        = string
  validation {
    condition     = contains(["staging", "production", "shared"], var.environment)
    error_message = "environment debe ser 'staging', 'production' o 'shared'."
  }
}

variable "cost_center" {
  description = "Centro de coste para atribución de facturación (ADR-0006 D25)."
  type        = string
  default     = "mvp"
}

variable "aws_account_id" {
  description = "ID de la cuenta AWS (12 dígitos). Se usa para construir ARNs explícitos."
  type        = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id debe ser un número de 12 dígitos."
  }
}
