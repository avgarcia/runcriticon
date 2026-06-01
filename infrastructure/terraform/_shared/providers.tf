# Provider AWS común a todos los entornos
# Cruce: ADR-0006 D1 (eu-west-1), ADR-0006 D25 (tagging obligatorio)

terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "runcriticon"
      Environment = var.environment
      ManagedBy   = "terraform"
      CostCenter  = var.cost_center
    }
  }
}
