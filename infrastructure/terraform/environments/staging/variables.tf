# Variables del entorno staging. Los valores reales van en terraform.tfvars (NO se commitea).

variable "aws_region" {
  description = "Región AWS (ADR-0006 D1: eu-west-1)."
  type        = string
  default     = "eu-west-1"
}

variable "cost_center" {
  description = "Centro de coste para atribución de facturación (ADR-0006 D25)."
  type        = string
  default     = "mvp"
}

variable "alert_email" {
  description = "Email que recibe las alertas de facturación (ADR-0006 D26)."
  type        = string
}

variable "image_tag" {
  description = "Tag de la imagen en ECR que sirve App Runner (ADR-0010 D3)."
  type        = string
  default     = "staging"
}

variable "github_org" {
  description = "Organización/usuario de GitHub (ADR-0010 D10)."
  type        = string
  default     = "avgarcia"
}

variable "github_repo" {
  description = "Repositorio de GitHub."
  type        = string
  default     = "runcriticon"
}

variable "custom_domain" {
  description = "Dominio propio de App Runner (ADR-0006 D14/D16). Vacío = solo *.awsapprunner.com."
  type        = string
  default     = ""
}
