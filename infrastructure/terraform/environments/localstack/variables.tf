# Variables del entorno de ensayo contra LocalStack.

variable "aws_region" {
  description = "Región. LocalStack acepta cualquiera; se mantiene eu-west-1 por consistencia con ADR-0006 D1."
  type        = string
  default     = "eu-west-1"
}

variable "cost_center" {
  description = "Tag de centro de coste (solo cosmético en LocalStack)."
  type        = string
  default     = "mvp-localstack"
}

variable "localstack_endpoint" {
  description = "Endpoint (edge) de LocalStack. Por defecto el del docker-compose de este directorio."
  type        = string
  default     = "http://localhost:4566"
}
