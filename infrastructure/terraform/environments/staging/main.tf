# Composición del entorno staging (ADR-0006 D20): aprovisiona los seis módulos con el mismo
# código que producción, parametrizado por variables. El state vive en S3 + DynamoDB (D19).
#
# NOTA: no se ha aplicado. El primer `terraform apply` requiere el bootstrap del state backend
# (ver infrastructure/terraform/README.md §Bootstrap) y credenciales de admin con SSO (ADR-0006
# D13/D27). El rol OIDC (módulo cicd) está acotado a despliegue, no aplica esta infraestructura.

terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "s3" {
    bucket         = "runcriticon-tfstate"
    key            = "environments/staging/terraform.tfstate"
    region         = "eu-west-1"
    dynamodb_table = "runcriticon-tfstate-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "runcriticon"
      Environment = local.env
      ManagedBy   = "terraform"
      CostCenter  = var.cost_center
    }
  }
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  env          = "staging"
  ssm_path_arn = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/runcriticon/${local.env}/*"
}

module "network" {
  source      = "../../modules/network"
  environment = local.env
}

module "secrets" {
  source      = "../../modules/secrets"
  environment = local.env
}

module "database" {
  source                     = "../../modules/database"
  environment                = local.env
  private_subnet_ids         = module.network.private_subnet_ids
  database_security_group_id = module.network.database_security_group_id
  ssm_kms_key_arn            = module.secrets.kms_key_arn
}

module "observability" {
  source      = "../../modules/observability"
  environment = local.env
  alert_email = var.alert_email
}

module "runtime" {
  source                      = "../../modules/runtime"
  environment                 = local.env
  private_subnet_ids          = module.network.private_subnet_ids
  connector_security_group_id = module.network.connector_security_group_id
  image_tag                   = var.image_tag
  custom_domain               = var.custom_domain

  db_address                = module.database.db_address
  db_port                   = module.database.db_port
  db_name                   = module.database.db_name
  db_username               = module.database.master_username
  db_password_parameter_arn = module.database.db_password_parameter_arn

  crypto_parameter_arns  = module.secrets.parameter_arns
  ssm_parameter_path_arn = local.ssm_path_arn
  ssm_kms_key_arns       = [module.secrets.kms_key_arn, module.database.kms_key_arn]
}

module "cicd" {
  source      = "../../modules/cicd"
  environment = local.env
  github_org  = var.github_org
  github_repo = var.github_repo

  ecr_repository_arn     = module.runtime.ecr_repository_arn
  apprunner_service_arn  = module.runtime.service_arn
  ssm_parameter_path_arn = local.ssm_path_arn
  kms_key_arns           = [module.secrets.kms_key_arn, module.database.kms_key_arn]
  passrole_arns          = [module.runtime.instance_role_arn, module.runtime.access_role_arn]
}
