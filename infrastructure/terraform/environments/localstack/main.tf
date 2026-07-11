# Entorno de ENSAYO LOCAL: lanza Terraform contra LocalStack (AWS emulado en Docker),
# sin cuenta AWS ni coste. NO es infraestructura real ni comparte state con staging/production.
#
# Cubre solo los módulos emulables por LocalStack Community y sin dependencias de servicios
# no soportados:
#   - network  (VPC, subnets, SG, NAT, route tables  -> EC2)
#   - secrets  (KMS + SSM Parameter Store + random_*)
#
# Deliberadamente FUERA (ver README.md):
#   - database      -> RDS solo en LocalStack Pro
#   - runtime       -> App Runner NO existe en LocalStack
#   - observability -> aws_budgets_budget no emulable de forma fiable
#   - cicd          -> depende de outputs de App Runner (runtime)

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

  # State LOCAL y efímero: este entorno no usa el backend S3 de los entornos reales.
  backend "local" {}
}

locals {
  env = "localstack"

  # Los 4 tags comunes (ADR-0006 D25). El 5º, Module, varía por módulo — mismo patrón de
  # provider alias por módulo que environments/staging/main.tf.
  base_tags = {
    Project     = "runcriticon"
    Environment = local.env
    ManagedBy   = "terraform"
    CostCenter  = var.cost_center
  }

  # Config común a todos los providers "aws.*" de este entorno: credenciales falsas vía
  # variables de entorno (NO en código):
  #   export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
  # LocalStack no las valida (skip_credentials_validation). Ver README.md.
}

provider "aws" {
  alias  = "network"
  region = var.aws_region

  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  default_tags {
    tags = merge(local.base_tags, { Module = "red" })
  }

  endpoints {
    ec2 = var.localstack_endpoint
  }
}

provider "aws" {
  alias  = "secrets"
  region = var.aws_region

  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  default_tags {
    tags = merge(local.base_tags, { Module = "seguridad" })
  }

  endpoints {
    kms = var.localstack_endpoint
    ssm = var.localstack_endpoint
  }
}

module "network" {
  source      = "../../modules/network"
  environment = local.env

  providers = { aws = aws.network }
}

module "secrets" {
  source      = "../../modules/secrets"
  environment = local.env

  providers = { aws = aws.secrets }
}
