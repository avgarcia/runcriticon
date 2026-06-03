# Entorno `localstack` — ensayo del Terraform sin AWS

Permite ejecutar `terraform apply` contra **[LocalStack](https://localstack.cloud)** (AWS emulado en Docker) para **validar y ensayar el código Terraform sin una cuenta AWS y sin coste**. No crea infraestructura real.

> No sustituye a `staging` / `production`: el state es **local y efímero**, y solo se aplican los módulos que LocalStack Community sabe emular.

## Qué se aplica aquí

| Módulo | Recursos | Emulable |
|---|---|---|
| `network` | VPC, subnets, IGW, NAT, route tables, security groups (EC2) | ✅ |
| `secrets` | KMS, SSM Parameter Store, `random_*` | ✅ |

## Qué queda FUERA (y por qué)

| Módulo | Motivo |
|---|---|
| `database` | `aws_db_instance` (RDS) solo está en **LocalStack Pro** (de pago). En local se usa el Postgres del `docker-compose.yml` raíz. |
| `runtime` | **App Runner no existe en LocalStack** (ni Community ni Pro). En local la app corre con `./gradlew bootRun`. |
| `observability` | `aws_budgets_budget` no es emulable de forma fiable. |
| `cicd` | Depende de outputs de App Runner (`runtime`), que no se crea aquí. |

## Requisitos

- **Docker** (para LocalStack).
- **Terraform** >= 1.7.
- Opcional: `awslocal` / `aws --endpoint-url` para inspeccionar.

## Uso

```bash
# 1. Levanta LocalStack (desde este directorio)
docker compose up -d
curl -s http://localhost:4566/_localstack/health   # debe responder

# 2. Credenciales falsas (cualquier valor sirve; LocalStack no las valida)
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# 3. Aplica el Terraform contra LocalStack
terraform init
terraform plan
terraform apply

# 4. Inspecciona lo creado
aws --endpoint-url=http://localhost:4566 ec2 describe-vpcs
aws --endpoint-url=http://localhost:4566 ssm get-parameters-by-path --path /runcriticon/localstack --recursive

# 5. Limpia
terraform destroy
docker compose down -v
```

## Cómo apunta a LocalStack

`main.tf` configura el provider `aws` con credenciales falsas, los `skip_*` flags y `endpoints {}`
redirigidos a `var.localstack_endpoint` (`http://localhost:4566`). El `backend` es **`local`**, no el
S3 de los entornos reales — por eso este entorno nunca toca el state compartido.

## Ampliar el alcance

Para ensayar también el **ECR** (sí emulable) habría que extraer `aws_ecr_repository` del módulo
`runtime` a su propio submódulo, de modo que se pueda componer aquí sin arrastrar App Runner.
Pendiente. **App Runner y RDS (Community) no se podrán ensayar nunca offline** — son límite de LocalStack.
