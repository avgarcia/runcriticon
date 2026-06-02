# RDS PostgreSQL del esqueleto (ADR-0006 D7, D8, D9). Single-AZ en MVP, cifrada en reposo,
# privada (solo accesible desde el VPC connector via SG dedicado). La contraseña maestra se
# genera aquí y se publica en SSM con la convención /runcriticon/{env}/db/password (ADR-0013).

locals {
  module_tags = { Module = "bd" }
}

# KMS para el cifrado en reposo de RDS, snapshots y backups (ADR-0006 D7, ADR-0014 D3).
resource "aws_kms_key" "rds" {
  description         = "Cifrado en reposo de RDS Runcriticon (${var.environment})"
  enable_key_rotation = true

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-rds" })
}

resource "aws_kms_alias" "rds" {
  name          = "alias/runcriticon-${var.environment}-rds"
  target_key_id = aws_kms_key.rds.id
}

resource "aws_db_subnet_group" "this" {
  name       = "runcriticon-${var.environment}"
  subnet_ids = var.private_subnet_ids

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}" })
}

# Contraseña maestra generada (sin caracteres que RDS prohíbe: / @ " y espacio).
resource "random_password" "master" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# La contraseña vive en SSM SecureString (nunca en el repo). db/password (ADR-0013).
resource "aws_ssm_parameter" "db_password" {
  name        = "/runcriticon/${var.environment}/db/password"
  description = "Contraseña del usuario maestro de RDS (ADR-0013 D6)"
  type        = "SecureString"
  key_id      = coalesce(var.ssm_kms_key_arn, "alias/aws/ssm")
  value       = random_password.master.result

  tags = local.module_tags
}

resource "aws_db_instance" "this" {
  identifier     = "runcriticon-${var.environment}"
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.rds.arn

  db_name  = var.db_name
  username = var.master_username
  password = random_password.master.result

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.database_security_group_id]
  multi_az               = var.multi_az
  publicly_accessible    = false

  backup_retention_period = var.backup_retention_period
  backup_window           = var.backup_window
  maintenance_window      = var.maintenance_window

  auto_minor_version_upgrade  = true  # parches minor en la ventana (ADR-0006 D8)
  allow_major_version_upgrade = false # los major se planean (ADR-0006 D8)

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"] # logs a CloudWatch (ADR-0006 D24)

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "runcriticon-${var.environment}-final"

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}" })
}
