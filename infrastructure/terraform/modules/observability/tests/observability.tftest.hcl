# terraform test del módulo observability con el provider AWS mockeado. Valida la convención del
# log group, su retención y el presupuesto, sin AWS (ADR-0006 D24, D26).

mock_provider "aws" {}

run "observability_planifica" {
  command = plan

  variables {
    environment = "test"
    alert_email = "alertas@example.com"
  }

  assert {
    condition     = aws_cloudwatch_log_group.application.name == "/runcriticon/test/application"
    error_message = "El log group debe seguir la convención /runcriticon/{env}/application."
  }

  assert {
    condition     = aws_cloudwatch_log_group.application.retention_in_days == 90
    error_message = "La retención de logs debe ser 90 días por defecto (ADR-0006 D24)."
  }

  assert {
    condition     = aws_budgets_budget.monthly.budget_type == "COST"
    error_message = "El presupuesto mensual debe ser de tipo COST."
  }
}
