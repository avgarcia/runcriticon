# terraform test del módulo secrets con el provider AWS mockeado. Valida la convención de nombres
# SSM y que los secretos sean SecureString, sin AWS (ADR-0013).

mock_provider "aws" {}

run "secrets_planifica" {
  command = plan

  variables {
    environment = "test"
  }

  assert {
    condition     = aws_ssm_parameter.token_hmac_secret.name == "/runcriticon/test/security/token-hmac-secret"
    error_message = "El parámetro SSM debe seguir la convención /runcriticon/{env}/{component}/..."
  }

  assert {
    condition     = aws_ssm_parameter.token_hmac_secret.type == "SecureString"
    error_message = "Los secretos deben almacenarse como SecureString."
  }

  assert {
    condition     = aws_kms_key.ssm.enable_key_rotation == true
    error_message = "La KMS de SSM debe tener la rotación de clave activada."
  }
}
