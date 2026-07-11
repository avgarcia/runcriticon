# Terraform de Runcriticon

Infraestructura como código de Runcriticon. Espejo aplicado de **ADR-0006** (mono-tenant en AWS `eu-west-1`).

## Estructura

```
infrastructure/terraform/
├── _shared/                    ← configuración base compartida (state backend, providers)
│   ├── state-backend.tf        ← S3 + DynamoDB para el state remoto (ADR-0006 D19)
│   ├── providers.tf            ← provider AWS con región y tags por defecto
│   └── variables.tf            ← variables comunes (env, account_id, region)
├── modules/                    ← módulos reusables (creados en bloques posteriores)
│   ├── network/                ← VPC + subnets privadas + NAT Gateway (ADR-0006 D11)
│   ├── database/               ← RDS PostgreSQL (ADR-0006 D7)
│   ├── secrets/                ← SSM Parameter Store (ADR-0013 D3)
│   ├── observability/          ← CloudWatch + AWS Budgets (ADR-0006 D24, D26)
│   ├── cicd/                   ← IAM OIDC para GitHub Actions (ADR-0010 D10)
│   └── runtime/                ← App Runner (ADR-0006 D3)
└── environments/               ← composición por entorno
    └── staging/
        └── main.tf
```

## Bootstrap (manual, primera vez)

Antes del primer `terraform apply`, el state backend debe existir. Como Terraform no puede gestionar su propio state (problema del huevo y la gallina), se crean a mano:

```bash
# 1. Cuenta AWS aprovisionada con permisos de admin
aws sts get-caller-identity                                     # debe responder con tu cuenta

# 2. Bucket S3 para el state remoto
aws s3api create-bucket \
  --bucket runcriticon-tfstate \
  --region eu-west-1 \
  --create-bucket-configuration LocationConstraint=eu-west-1

aws s3api put-bucket-versioning \
  --bucket runcriticon-tfstate \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket runcriticon-tfstate \
  --server-side-encryption-configuration '{
    "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "aws:kms"}}]
  }'

# 3. Tabla DynamoDB para el lock
aws dynamodb create-table \
  --table-name runcriticon-tfstate-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-1
```

## Aplicación (por entorno)

```bash
cd infrastructure/terraform/environments/staging
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

`terraform.tfvars` con valores reales **nunca** se commitea (está en `.gitignore`). Hay un `terraform.tfvars.example` para guiar.

## Convención de tagging (ADR-0006 D25)

Todos los recursos llevan los tags obligatorios:

| Tag | Valor |
|---|---|
| `Project` | `runcriticon` |
| `Environment` | `staging` \| `production` \| `shared` |
| `Module` | nombre del módulo Terraform (`network`, `database`, etc.) |
| `ManagedBy` | `terraform` |
| `CostCenter` | `mvp` (por ahora) |

Los 5 tags son **automáticos**, no hay que mezclarlos a mano en cada `resource`: cada `environments/*/main.tf` declara un `provider "aws"` con alias por módulo (`aws.network`, `aws.database`, …), cada uno con su propio `default_tags` (los 4 comunes + `Module`), y cada `module "..." { providers = { aws = aws.<módulo> } }` recibe el suyo. Ningún recurso nuevo puede olvidar el tag `Module` porque no lo declara — lo hereda del provider con el que se creó. (`_shared/providers.tf` es una referencia de la convención, no está enlazado por ningún `environments/*`; cada entorno declara sus providers explícitamente.)

## Estado de despliegue

| Bloque | Estado |
|---|---|
| Bootstrap (state backend manual) | ⏳ pendiente equipo |
| `_shared` (providers, variables) | ✅ Bloque 1 |
| `modules/network` | ✅ Bloque 2B (escrito + `validate` en CI; sin `apply`) |
| `modules/database` | ✅ Bloque 2B (escrito + `validate` en CI; sin `apply`) |
| `modules/secrets` | ✅ Bloque 2B (escrito + `validate` en CI; sin `apply`) |
| `modules/observability` | ✅ Bloque 2B (escrito + `validate` en CI; sin `apply`) |
| `modules/cicd` | ✅ Bloque 4 (escrito + `validate` en CI; sin `apply`) |
| `modules/runtime` | ✅ Bloque 4 (escrito + `validate` en CI; sin `apply`) |
| `environments/staging` | ✅ Bloque 4 (compone los 6 módulos; sin `apply`) |

> **Nota**: toda la IaC está escrita y validada (`terraform validate` en CI, sin credenciales),
> pero **no se ha aplicado**. El primer `terraform apply` requiere: (1) el bootstrap manual del
> state backend (S3 + DynamoDB, ver §Bootstrap), (2) credenciales de admin con SSO (ADR-0006
> D18/D27), y (3) `terraform.tfvars` con `alert_email`. El rol OIDC del módulo `cicd` está
> **acotado a despliegue** (push a ECR + redespliegue de App Runner + lectura de SSM); no aplica
> infraestructura. Tras el primer apply, GitHub Actions usa ese rol para el CD continuo (Bloque
> futuro de pipeline de despliegue).

## Referencias

- **ADR-0006** — Infraestructura: mono-tenant AWS, todas las sub-decisiones.
- **ADR-0010** — CI/CD con OIDC desde GitHub Actions.
- **ADR-0013** — Configuración y secretos en SSM Parameter Store.
- **ADR-0014** — RGPD: residencia UE, cifrado, subencargados.
- [`docs/arquitectura/persistencia.md`](../../docs/arquitectura/persistencia.md) — esquema por módulo, migraciones Flyway.
- [`docs/arquitectura/configuracion-y-secretos-en-modulos.md`](../../docs/arquitectura/configuracion-y-secretos-en-modulos.md) — convención de nombres SSM.
