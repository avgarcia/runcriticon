# Staging local (H0a)

La imagen de producción desplegada como en App Runner, pero en Docker y sin cuenta AWS. Es la mitad de H0 que se
puede cumplir hoy (ver [`docs/plan-implementacion-mvp.md`](../../docs/plan-implementacion-mvp.md)): cada merge a
`main` construye la imagen, la levanta aquí, pasa el smoke y solo entonces se publica en GHCR como
`runcriticon-backend:main-<sha7>` (ADR-0010 D3/D18). El despliegue real a `staging` en AWS es H0b.

| Pieza | Qué imita de AWS |
|---|---|
| `app` | La imagen de `backend/Dockerfile` con perfil `staging` y **solo** las variables de entorno que App Runner inyecta (mismos nombres; `check_env_contract.py` lo comprueba contra Terraform) |
| `postgres` | RDS: Postgres 16 vacío en cada arranque, así que Flyway aplica todas las migraciones como el primer día |
| `proxy` | El proxy de App Runner: termina TLS y reenvía a la app por HTTP con `X-Forwarded-Proto` |

## Qué valida y qué no

| Valida | No valida (queda para H0b) |
|---|---|
| Arranque en GraalVM CE 25 con la configuración solo por entorno | Que App Runner resuelva los secretos desde SSM (rol de instancia, KMS) |
| Flyway completo sobre una base vacía | RDS a través del VPC connector y la red privada |
| `XSRF-TOKEN` con `Secure` detrás del proxy (`forward-headers-strategy`) | El rol OIDC: push a ECR y redespliegue de App Runner |
| Cookie de sesión `Secure`/`HttpOnly`/`SameSite=Lax`, login del admin de arranque, la SPA en `/` | El `terraform apply` (LocalStack solo emula `network` y `secrets`) |

## Uso en local

Desde la raíz del repo (necesita Docker y curl; el smoke es bash):

```bash
docker compose -f infrastructure/staging-local/docker-compose.yml up -d --build
```

```bash
bash infrastructure/staging-local/smoke.sh
```

```bash
docker compose -f infrastructure/staging-local/docker-compose.yml down -v
```

La app queda en <https://localhost:8443> con certificado de la CA interna de Caddy (el navegador avisará), y el admin
de arranque es `admin@runcriticon.staging` / `staging-local`. Los valores del compose son ficticios: no hay nada real
que proteger aquí.

## En CI

El job `docker-image` de `.github/workflows/ci.yml` hace lo mismo en cada PR y en cada push a `main`: construye la
imagen, comprueba el contrato de variables, levanta el entorno con `RUNCRITICON_IMAGE` apuntando a esa imagen y pasa
el smoke. En `main`, además, publica la imagen en GHCR. Una imagen que no pasa el smoke no se publica.
