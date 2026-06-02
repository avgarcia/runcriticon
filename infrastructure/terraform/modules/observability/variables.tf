# Variables del módulo observability (ADR-0006 D24, D26).

variable "environment" {
  description = "Entorno: staging | production."
  type        = string
}

variable "alert_email" {
  description = "Email que recibe las alertas de facturación (ADR-0006 D26)."
  type        = string
}

variable "log_retention_days" {
  description = "Retención de logs en CloudWatch (ADR-0006 D24: 90 días, alineado con ADR-0014 D10)."
  type        = number
  default     = 90
}

variable "budget_currency" {
  description = "Moneda del presupuesto. Debe coincidir con la moneda de facturación de la cuenta."
  type        = string
  default     = "EUR"
}

variable "budget_warning_amount" {
  description = "Umbral de aviso del presupuesto mensual (ADR-0006 D26: 100)."
  type        = number
  default     = 100
}

variable "budget_critical_amount" {
  description = "Umbral crítico del presupuesto mensual (ADR-0006 D26: 200)."
  type        = number
  default     = 200
}
