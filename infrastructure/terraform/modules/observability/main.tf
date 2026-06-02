# Observabilidad de plataforma en MVP (ADR-0006 D24, D26): logs en CloudWatch con retención
# acotada y alertas de facturación con dos umbrales. La observabilidad runtime más rica
# (métricas/trazas de la app) la decide ADR-0011; aquí solo el mínimo de plataforma.

locals {
  module_tags = { Module = "obs" }
}

# Log group de la aplicación (ADR-0006 D24). App Runner y RDS crean además los suyos propios.
resource "aws_cloudwatch_log_group" "application" {
  name              = "/runcriticon/${var.environment}/application"
  retention_in_days = var.log_retention_days

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-application" })
}

# Presupuesto mensual con aviso (100) y crítico (200) por email (ADR-0006 D26).
resource "aws_budgets_budget" "monthly" {
  name         = "runcriticon-${var.environment}-mensual"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_critical_amount)
  limit_unit   = var.budget_currency
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = var.budget_warning_amount
    threshold_type             = "ABSOLUTE_VALUE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = var.budget_critical_amount
    threshold_type             = "ABSOLUTE_VALUE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }
}
