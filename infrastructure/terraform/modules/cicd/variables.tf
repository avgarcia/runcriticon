# Variables del módulo cicd (ADR-0006 D27, ADR-0010 D10).

variable "environment" {
  description = "Entorno: staging | production."
  type        = string
}

variable "github_org" {
  description = "Organización/usuario de GitHub dueño del repositorio (ADR-0010 D10)."
  type        = string
  default     = "avgarcia"
}

variable "github_repo" {
  description = "Nombre del repositorio de GitHub."
  type        = string
  default     = "runcriticon"
}

variable "github_subject_filter" {
  description = <<-EOT
    Filtro del 'sub' del token OIDC que puede asumir el rol. Restringe qué refs/entornos de
    GitHub Actions tienen acceso (ADR-0010 D10). Por defecto, solo el entorno de GitHub homónimo.
  EOT
  type        = string
  default     = null
}

variable "create_oidc_provider" {
  description = <<-EOT
    El OIDC provider de GitHub es un recurso de CUENTA (uno solo, no por entorno). El primer
    entorno que se aprovisiona lo crea (true); los siguientes lo reutilizan pasando su ARN en
    existing_oidc_provider_arn y create_oidc_provider=false.
  EOT
  type        = bool
  default     = true
}

variable "existing_oidc_provider_arn" {
  description = "ARN del OIDC provider ya existente, si create_oidc_provider es false."
  type        = string
  default     = null
}

variable "ecr_repository_arn" {
  description = "ARN del repositorio ECR al que el pipeline empuja la imagen (del módulo runtime)."
  type        = string
}

variable "apprunner_service_arn" {
  description = "ARN del servicio App Runner que el pipeline redespliega (del módulo runtime)."
  type        = string
}

variable "ssm_parameter_path_arn" {
  description = "ARN con comodín de los parámetros SSM del entorno (arn:...:parameter/runcriticon/{env}/*)."
  type        = string
}

variable "kms_key_arns" {
  description = "ARNs de las KMS necesarias para descifrar los SecureString leídos por el pipeline."
  type        = list(string)
  default     = []
}

variable "passrole_arns" {
  description = "ARNs de los roles que el pipeline puede pasar a App Runner (instance + access role)."
  type        = list(string)
  default     = []
}
