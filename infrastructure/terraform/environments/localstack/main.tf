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

provider "aws" {
  region = var.aws_region

  # Credenciales falsas vía variables de entorno (NO en código, para no dejar secretos literales):
  #   export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
  # LocalStack no las valida (skip_credentials_validation). Ver README.md.

  # Evita llamadas a STS / metadata reales de AWS.
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  default_tags {
    tags = {
      Project     = "runcriticon"
      Environment = local.env
      ManagedBy   = "terraform"
      CostCenter  = var.cost_center
    }
  }

  # Redirige al endpoint de LocalStack los servicios que usan network (ec2) y secrets (kms, ssm).
  endpoints {
    ec2 = var.localstack_endpoint
    kms = var.localstack_endpoint
    ssm = var.localstack_endpoint
  }
}

locals {
  env = "localstack"
}

module "network" {
  source      = "../../modules/network"
  environment = local.env
}

module "secrets" {
  source      = "../../modules/secrets"
  environment = local.env
}
