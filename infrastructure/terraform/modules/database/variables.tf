# Variables del módulo database (ADR-0006 D7, D8, D9).

variable "environment" {
  description = "Entorno: staging | production."
  type        = string
}

variable "private_subnet_ids" {
  description = "Subnets privadas donde vive RDS (del módulo network)."
  type        = list(string)
}

variable "database_security_group_id" {
  description = "SG de RDS que solo acepta del VPC connector (del módulo network)."
  type        = string
}

variable "ssm_kms_key_arn" {
  description = <<-EOT
    KMS con la que cifrar el parámetro SSM de la contraseña de BD (del módulo secrets). Si es null,
    se usa la clave gestionada por AWS para SSM (alias/aws/ssm).
  EOT
  type        = string
  default     = null
}

variable "instance_class" {
  description = "Clase de instancia RDS (ADR-0006 D7: db.t4g.small, ARM Graviton)."
  type        = string
  default     = "db.t4g.small"
}

variable "engine_version" {
  description = "Versión major de PostgreSQL (ADR-0004, ADR-0006 D7)."
  type        = string
  default     = "16"
}

variable "allocated_storage" {
  description = "Almacenamiento inicial en GB (ADR-0006 D7)."
  type        = number
  default     = 20
}

variable "max_allocated_storage" {
  description = "Tope de autogrow en GB (ADR-0006 D7)."
  type        = number
  default     = 100
}

variable "db_name" {
  description = "Nombre de la base de datos inicial."
  type        = string
  default     = "runcriticon"
}

variable "master_username" {
  description = "Usuario maestro de PostgreSQL."
  type        = string
  default     = "runcriticon"
}

variable "multi_az" {
  description = "Multi-AZ. False en MVP (ADR-0006 D7); disparador para true en D10."
  type        = bool
  default     = false
}

variable "backup_retention_period" {
  description = "Días de retención de backups automáticos (ADR-0006 D9, ADR-0014 D8)."
  type        = number
  default     = 30
}

variable "backup_window" {
  description = "Ventana de backups en UTC (antes de la de mantenimiento)."
  type        = string
  default     = "02:00-03:00"
}

variable "maintenance_window" {
  description = "Ventana de mantenimiento en UTC (~dom 04:00-05:00 CET, ADR-0006 D8)."
  type        = string
  default     = "sun:03:00-sun:04:00"
}

variable "deletion_protection" {
  description = "Protección contra borrado de la instancia. True por defecto."
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "Omitir snapshot final al destruir. False por defecto (se conserva el snapshot)."
  type        = bool
  default     = false
}
