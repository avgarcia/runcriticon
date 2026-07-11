# Cómputo del esqueleto (ADR-0006 D3, D4, D6, D12, D14, D16): App Runner sirve la imagen
# replicada en ECR (origen GHCR, ADR-0010 D2/D3), alcanza RDS por el VPC connector y lee los
# secretos desde SSM en runtime. HTTPS y autoescalado los gestiona App Runner.

locals {
  module_tags = { Module = "computo" }

  # Variables de entorno no secretas (la conexión a BD; la contraseña va por secrets).
  base_env = {
    SPRING_PROFILES_ACTIVE = var.spring_profile
    SPRING_DATASOURCE_URL  = "jdbc:postgresql://${var.db_address}:${var.db_port}/${var.db_name}"
    DB_USERNAME            = var.db_username
  }

  # Secretos inyectados como referencias a SSM (App Runner los resuelve en runtime).
  runtime_secrets = {
    DB_PASSWORD             = var.db_password_parameter_arn
    TOKEN_HMAC_SECRET       = var.crypto_parameter_arns["token_hmac_secret"]
    USERID_HASH_SALT        = var.crypto_parameter_arns["userid_hash_salt"]
    POSTMARK_SERVER_TOKEN   = var.crypto_parameter_arns["postmark_server_token"]
    POSTMARK_WEBHOOK_SECRET = var.crypto_parameter_arns["postmark_webhook_secret"]
  }
}

# Repositorio ECR por entorno (destino de la réplica desde GHCR, ADR-0010 D2). IMMUTABLE: cada
# imagen se etiqueta por commit y no se sobrescribe (ADR-0010 D18); el rollback es redesplegar un
# tag anterior (D12). El (re)despliegue lo dispara el pipeline con UpdateService al nuevo tag, no
# la sobrescritura de un tag mutable.
resource "aws_ecr_repository" "app" {
  name                 = "runcriticon-${var.environment}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}" })
}

# Conserva las últimas 20 imágenes; purga las antiguas para no acumular coste.
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Conservar solo las 20 imágenes más recientes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

# --- Roles IAM de App Runner ---
# Access role: permite a App Runner tirar la imagen de ECR (ADR-0010 D2).
data "aws_iam_policy_document" "access_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["build.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "access" {
  name               = "runcriticon-${var.environment}-apprunner-access"
  assume_role_policy = data.aws_iam_policy_document.access_assume.json

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-apprunner-access" })
}

resource "aws_iam_role_policy_attachment" "access_ecr" {
  role       = aws_iam_role.access.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess"
}

# Instance role: lo asume el contenedor para leer los secretos de SSM (ADR-0013).
data "aws_iam_policy_document" "instance_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["tasks.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "runcriticon-${var.environment}-apprunner-instance"
  assume_role_policy = data.aws_iam_policy_document.instance_assume.json

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-apprunner-instance" })
}

data "aws_iam_policy_document" "instance" {
  statement {
    sid       = "SsmRead"
    effect    = "Allow"
    actions   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [var.ssm_parameter_path_arn]
  }

  statement {
    sid       = "KmsDecrypt"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = var.ssm_kms_key_arns
  }
}

resource "aws_iam_role_policy" "instance" {
  name   = "ssm-read"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.instance.json
}

# --- App Runner ---
# Conector a la VPC privada para alcanzar RDS sin exponerla (ADR-0006 D12).
resource "aws_apprunner_vpc_connector" "this" {
  vpc_connector_name = "runcriticon-${var.environment}"
  subnets            = var.private_subnet_ids
  security_groups    = [var.connector_security_group_id]

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}" })
}

# Autoescalado por concurrencia (ADR-0006 D4: min 1, max 3, 100 req/instancia).
resource "aws_apprunner_auto_scaling_configuration_version" "this" {
  auto_scaling_configuration_name = "runcriticon-${var.environment}"
  max_concurrency                 = var.max_concurrency
  min_size                        = var.min_size
  max_size                        = var.max_size

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}" })
}

resource "aws_apprunner_service" "this" {
  service_name = "runcriticon-${var.environment}"

  source_configuration {
    # El pipeline controla el despliegue (UpdateService al nuevo tag de commit, ADR-0010 D18);
    # no hay auto-deploy por sobrescritura de tag porque el repo es IMMUTABLE.
    auto_deployments_enabled = false

    authentication_configuration {
      access_role_arn = aws_iam_role.access.arn
    }

    image_repository {
      image_identifier      = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
      image_repository_type = "ECR"

      image_configuration {
        port                          = tostring(var.app_port)
        runtime_environment_variables = merge(local.base_env, var.extra_environment_variables)
        runtime_environment_secrets   = merge(local.runtime_secrets, var.extra_secrets)
      }
    }
  }

  instance_configuration {
    cpu               = var.cpu
    memory            = var.memory
    instance_role_arn = aws_iam_role.instance.arn
  }

  auto_scaling_configuration_arn = aws_apprunner_auto_scaling_configuration_version.this.arn

  network_configuration {
    egress_configuration {
      egress_type       = "VPC"
      vpc_connector_arn = aws_apprunner_vpc_connector.this.arn
    }
  }

  health_check_configuration {
    protocol = "HTTP"
    path     = var.health_check_path
  }

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}" })

  # El pipeline de CD actualiza la imagen al nuevo tag de commit (ADR-0010 D18); Terraform no debe
  # revertirla al valor inicial en el siguiente apply.
  lifecycle {
    ignore_changes = [source_configuration[0].image_repository[0].image_identifier]
  }
}

# Dominio propio opcional (ADR-0006 D14/D16). App Runner emite y renueva el certificado; requiere
# crear los registros DNS de validación que expone esta asociación.
resource "aws_apprunner_custom_domain_association" "this" {
  count = var.custom_domain != "" ? 1 : 0

  domain_name = var.custom_domain
  service_arn = aws_apprunner_service.this.arn
}
