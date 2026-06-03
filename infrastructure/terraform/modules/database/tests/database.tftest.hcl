# terraform test del módulo database con el provider AWS mockeado. Valida la config de RDS
# (motor, cifrado, no público) sin AWS ni RDS real (ADR-0006 D7, ADR-0004).

mock_provider "aws" {}

run "database_planifica" {
  command = plan

  variables {
    environment = "test"

    private_subnet_ids         = ["subnet-aaa", "subnet-bbb", "subnet-ccc"]
    database_security_group_id = "sg-database"
    ssm_kms_key_arn            = "arn:aws:kms:eu-west-1:000000000000:key/test"
  }

  assert {
    condition     = aws_db_instance.this.engine == "postgres"
    error_message = "El motor de base de datos debe ser PostgreSQL."
  }

  assert {
    condition     = aws_db_instance.this.storage_encrypted == true
    error_message = "RDS debe estar cifrada en reposo."
  }

  assert {
    condition     = aws_db_instance.this.publicly_accessible == false
    error_message = "RDS nunca debe ser accesible públicamente."
  }
}
