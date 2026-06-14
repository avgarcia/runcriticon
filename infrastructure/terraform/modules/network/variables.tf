# Variables del módulo network (ADR-0006 D11, D12).

variable "environment" {
  description = "Entorno al que pertenece la red: staging | production."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR de la VPC. /16 deja espacio de sobra para subnets por AZ."
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "Zonas de disponibilidad. Tres AZ para preparar Multi-AZ futuro (ADR-0006 D11)."
  type        = list(string)
  default     = ["eu-west-1a", "eu-west-1b", "eu-west-1c"]
}

variable "app_port" {
  description = "Puerto en el que escucha la aplicación tras el VPC connector (informativo)."
  type        = number
  default     = 8080
}

variable "db_port" {
  description = "Puerto de PostgreSQL que el SG de RDS abre al SG del VPC connector."
  type        = number
  default     = 5432
}
