# Outputs del módulo observability.

output "application_log_group_name" {
  description = "Nombre del log group de la aplicación en CloudWatch."
  value       = aws_cloudwatch_log_group.application.name
}

output "budget_name" {
  description = "Nombre del presupuesto mensual de AWS Budgets."
  value       = aws_budgets_budget.monthly.name
}
