# terraform test del módulo cicd con el provider AWS mockeado. Valida el rol de despliegue OIDC y
# la creación del provider OIDC, sin AWS (ADR-0006 D27, ADR-0010 D10).

mock_provider "aws" {}

run "cicd_planifica" {
  command = plan

  variables {
    environment = "test"

    ecr_repository_arn     = "arn:aws:ecr:eu-west-1:000000000000:repository/runcriticon-test"
    apprunner_service_arn  = "arn:aws:apprunner:eu-west-1:000000000000:service/runcriticon-test"
    ssm_parameter_path_arn = "arn:aws:ssm:eu-west-1:000000000000:parameter/runcriticon/test/*"
  }

  assert {
    condition     = aws_iam_role.deploy.name == "github-actions-runcriticon-test"
    error_message = "El rol de despliegue debe nombrarse github-actions-runcriticon-{env}."
  }

  assert {
    condition     = length(aws_iam_openid_connect_provider.github) == 1
    error_message = "Con create_oidc_provider=true debe crearse el OIDC provider de GitHub."
  }
}
