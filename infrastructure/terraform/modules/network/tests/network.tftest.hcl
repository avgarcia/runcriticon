# terraform test del módulo network con el provider AWS mockeado (mock_provider): valida la
# configuración vía `plan` sin AWS ni credenciales. No crea ni emula nada (ADR-0006 D25).

mock_provider "aws" {}

run "network_planifica" {
  command = plan

  variables {
    environment = "test"
  }

  assert {
    condition     = aws_vpc.this.cidr_block == "10.0.0.0/16"
    error_message = "La VPC debe usar el CIDR por defecto 10.0.0.0/16."
  }

  assert {
    condition     = length(aws_subnet.private) == 3
    error_message = "Deben crearse 3 subnets privadas (una por AZ)."
  }

  assert {
    condition     = length(aws_subnet.public) == 3
    error_message = "Deben crearse 3 subnets públicas (una por AZ)."
  }
}
