# terraform test del módulo runtime con el provider AWS mockeado. Valida la configuración de
# App Runner + ECR + IAM SIN AWS y SIN que LocalStack tenga que emular App Runner (no puede).
# mock_provider genera valores ficticios; las assertions comprueban la config, no el runtime real.

mock_provider "aws" {
  # Los aws_iam_policy_document mockeados deben devolver un JSON de política válido, o el recurso
  # que lo consume (aws_iam_role[_policy]) lo rechaza en plan ("not a JSON object").
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"*\",\"Resource\":\"*\"}]}"
    }
  }
}

variables {
  environment                 = "test"
  private_subnet_ids          = ["subnet-aaa", "subnet-bbb"]
  connector_security_group_id = "sg-connector"

  db_address                = "db.test.local"
  db_name                   = "runcriticon"
  db_username               = "runcriticon"
  db_password_parameter_arn = "arn:aws:ssm:eu-west-1:000000000000:parameter/runcriticon/test/db/password"

  ssm_parameter_path_arn = "arn:aws:ssm:eu-west-1:000000000000:parameter/runcriticon/test/*"
  ssm_kms_key_arns       = ["arn:aws:kms:eu-west-1:000000000000:key/test"]

  crypto_parameter_arns = {
    session_signing_key     = "arn:aws:ssm:eu-west-1:000000000000:parameter/runcriticon/test/crypto/session-signing-key"
    magic_link_signing_key  = "arn:aws:ssm:eu-west-1:000000000000:parameter/runcriticon/test/crypto/magic-link-signing-key"
    userid_hash_salt        = "arn:aws:ssm:eu-west-1:000000000000:parameter/runcriticon/test/crypto/userid-hash-salt"
    postmark_server_token   = "arn:aws:ssm:eu-west-1:000000000000:parameter/runcriticon/test/email/postmark-server-token"
    postmark_webhook_secret = "arn:aws:ssm:eu-west-1:000000000000:parameter/runcriticon/test/email/postmark-webhook-secret"
  }
}

run "runtime_planifica" {
  command = plan

  assert {
    condition     = aws_apprunner_service.this.service_name == "runcriticon-test"
    error_message = "El servicio App Runner debe nombrarse runcriticon-{env}."
  }

  assert {
    condition     = aws_ecr_repository.app.image_tag_mutability == "IMMUTABLE"
    error_message = "El repositorio ECR debe ser IMMUTABLE (ADR-0010 D18)."
  }

  assert {
    condition     = aws_apprunner_auto_scaling_configuration_version.this.max_concurrency == 100
    error_message = "El autoescalado debe usar 100 req/instancia por defecto (ADR-0006 D4)."
  }

  assert {
    condition     = length(aws_apprunner_custom_domain_association.this) == 0
    error_message = "Sin custom_domain no debe crearse la asociación de dominio."
  }
}

run "runtime_con_dominio_crea_asociacion" {
  command = plan

  variables {
    custom_domain = "runcriticon.example.com"
  }

  assert {
    condition     = length(aws_apprunner_custom_domain_association.this) == 1
    error_message = "Con custom_domain debe crearse exactamente una asociación de dominio."
  }
}
