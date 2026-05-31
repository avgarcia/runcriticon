# Configuración del state backend de Terraform en S3 + DynamoDB lock
# Cruce: ADR-0006 D19
#
# Importante: estos recursos NO se gestionan por Terraform (problema del huevo y la gallina).
# Se crean a mano en el bootstrap inicial (ver infrastructure/terraform/README.md §Bootstrap).
# Este archivo solo declara la CONFIGURACIÓN del backend que cada entorno hereda.

# Bloque vacío de ejemplo — cada environments/{env}/main.tf lo configura con su key específica:
#
# terraform {
#   backend "s3" {
#     bucket         = "runcriticon-tfstate"
#     key            = "environments/staging/terraform.tfstate"
#     region         = "eu-west-1"
#     dynamodb_table = "runcriticon-tfstate-lock"
#     encrypt        = true
#   }
# }
