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

Se aplican via `default_tags` en el provider (ver `_shared/providers.tf`).

## Estado de despliegue

| Bloque | Estado |
|---|---|
| Bootstrap (state backend manual) | ⏳ pendiente equipo |
| `_shared` (providers, variables) | ✅ Bloque 1 |
| `modules/network` | ✅ Bloque 2B (escrito + `validate` en CI; sin `apply`) |
| `modules/database` | ✅ Bloque 2B (escrito + `validate` en CI; sin `apply`) |
| `modules/secrets` | ✅ Bloque 2B (escrito + `validate` en CI; sin `apply`) |
| `modules/observability` | ✅ Bloque 2B (escrito + `validate` en CI; sin `apply`) |
| `modules/cicd` | ⏳ Bloque 4 |
| `modules/runtime` | ⏳ Bloque 4 |
| `environments/staging` | ⏳ Bloque 4 |

> **Nota**: los módulos del Bloque 2B están escritos y validados (`terraform validate` en CI, sin
> credenciales), pero **no se han aplicado**: el `apply` real requiere la cuenta AWS y el bootstrap
> del state backend, y se compone en `environments/staging` durante el Bloque 4.

## Referencias

- **ADR-0006** — Infraestructura: mono-tenant AWS, todas las sub-decisiones.
- **ADR-0010** — CI/CD con OIDC desde GitHub Actions.
- **ADR-0013** — Configuración y secretos en SSM Parameter Store.
- **ADR-0014** — RGPD: residencia UE, cifrado, subencargados.
- [`docs/arquitectura/persistencia.md`](../../docs/arquitectura/persistencia.md) — esquema por módulo, migraciones Flyway.
- [`docs/arquitectura/configuracion-y-secretos-en-modulos.md`](../../docs/arquitectura/configuracion-y-secretos-en-modulos.md) — convención de nombres SSM.
