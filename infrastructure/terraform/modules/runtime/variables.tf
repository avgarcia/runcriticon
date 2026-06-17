# Variables del módulo runtime (ADR-0006 D3, D4, D6, D12, D14, D16).

variable "environment" {
  description = "Entorno: staging | production."
  type        = string
}

variable "private_subnet_ids" {
  description = "Subnets privadas para el VPC connector (del módulo network)."
  type        = list(string)
}

variable "connector_security_group_id" {
  description = "SG del VPC connector (del módulo network)."
  type        = string
}

variable "image_tag" {
  description = <<-EOT
    Tag inicial de la imagen en ECR que sirve App Runner. El pipeline lo actualiza al tag de commit
    (SHA) en cada despliegue (ADR-0010 D18); por eso el servicio ignora cambios en image_identifier.
  EOT
  type        = string
  default     = "bootstrap"
}

variable "app_port" {
  description = "Puerto en el que escucha Spring Boot dentro del contenedor."
  type        = number
  default     = 8080
}

variable "cpu" {
  description = "vCPU de la instancia App Runner en unidades (1024 = 1 vCPU, ADR-0006 D4)."
  type        = string
  default     = "1024"
}

variable "memory" {
  description = "Memoria de la instancia App Runner en MB (2048 = 2 GB, ADR-0006 D4)."
  type        = string
  default     = "2048"
}

variable "min_size" {
  description = "Mínimo de instancias (ADR-0006 D4: 1; con >=2 hay que introducir Redis)."
  type        = number
  default     = 1
}

variable "max_size" {
  description = "Máximo de instancias (ADR-0006 D4: 3)."
  type        = number
  default     = 3
}

variable "max_concurrency" {
  description = "Concurrencia por instancia que dispara el escalado (ADR-0006 D4: 100)."
  type        = number
  default     = 100
}

variable "health_check_path" {
  description = "Ruta del health check de App Runner (ADR-0006 D3, Actuator)."
  type        = string
  default     = "/actuator/health"
}

variable "spring_profile" {
  description = "Perfil de Spring activo en el contenedor."
  type        = string
  default     = "staging"
}

variable "db_address" {
  description = "Host de RDS (del módulo database)."
  type        = string
}

variable "db_port" {
  description = "Puerto de RDS (del módulo database)."
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "Nombre de la base de datos (del módulo database)."
  type        = string
}

variable "db_username" {
  description = "Usuario maestro de PostgreSQL (del módulo database)."
  type        = string
}

variable "db_password_parameter_arn" {
  description = "ARN del parámetro SSM con la contraseña de BD (del módulo database)."
  type        = string
}

variable "crypto_parameter_arns" {
  description = <<-EOT
    Mapa de ARNs de los secretos en SSM (del módulo secrets), por nombre lógico:
    session_signing_key, magic_link_signing_key, userid_hash_salt, postmark_server_token,
    postmark_webhook_secret.
  EOT
  type        = map(string)
}

variable "ssm_parameter_path_arn" {
  description = "ARN con comodín de los parámetros SSM del entorno, para acotar el instance role."
  type        = string
}

variable "ssm_kms_key_arns" {
  description = "ARNs de las KMS que cifran los SecureString, para el kms:Decrypt del instance role."
  type        = list(string)
}

variable "custom_domain" {
  description = <<-EOT
    Dominio propio asociado a App Runner (ADR-0006 D14/D16). Vacío = solo la URL *.awsapprunner.com.
    Requiere Route53/DNS para validar el certificado, así que en staging va desactivado por defecto.
  EOT
  type        = string
  default     = ""
}

variable "extra_environment_variables" {
  description = "Variables de entorno no secretas adicionales para el contenedor."
  type        = map(string)
  default     = {}
}

variable "extra_secrets" {
  description = "ARNs de parámetros SSM adicionales para inyectar como secretos en App Runner."
  type        = map(string)
  default     = {}
}
