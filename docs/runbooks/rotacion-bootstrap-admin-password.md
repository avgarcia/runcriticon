# Runbook — rotación del secreto `identidad/bootstrap-admin-password`

> Secreto de **semilla, solo `staging`** (ADR-0003 D3). Producción nunca siembra credenciales, así que este runbook **no aplica a producción**. Catálogo: [`configuracion-y-secretos-en-modulos.md` §3](../arquitectura/configuracion-y-secretos-en-modulos.md) (subsección "Secretos de semilla").

## Frecuencia

Ante **sospecha de compromiso** o cuando se quiera invalidar el acceso del admin sembrado en staging. **Sin cadencia fija**: es una credencial de conveniencia de staging, no de producción.

## Pre-requisitos

- Acceso AWS CLI con MFA al entorno `staging` (`eu-west-1`).
- ARN del servicio App Runner de staging (`$APP_RUNNER_ARN_STAGING`) y acceso a la base de datos RDS de staging (vía runbook [`acceso-rds.md`](acceso-rds.md) cuando exista).
- Valores actuales de `bootstrap_admin_email` y `bootstrap_club_id` del entorno (por defecto `admin@runcriticon.staging` y `00000000-0000-0000-0000-000000000001`; ver `infrastructure/terraform/environments/staging/`).

## Procedimiento

> ⚠️ El orden importa. `IdentidadSeeder` es idempotente y **no re-hashea** un admin que ya existe (retorna si encuentra la fila por `club_id` + `normalized_email`). Por eso, además de actualizar SSM, hay que **borrar la fila** para que el seeder la recree con el hash nuevo.

1. **Generar nuevo valor** (en el portapapeles del operador, no en consola compartida):

   ```bash
   openssl rand -base64 24
   ```

2. **Persistir en SSM** (ADR-0013 D5/D10):

   ```bash
   aws ssm put-parameter \
     --name /runcriticon/staging/identidad/bootstrap-admin-password \
     --value "$NUEVO_VALOR" \
     --type SecureString \
     --overwrite
   ```

   El `lifecycle.ignore_changes` de Terraform (ADR-0003 D3) garantiza que un `terraform apply` posterior no sobrescriba este valor.

3. **Borrar la fila del admin sembrado** en la BD de staging para que el seeder la recree (el `normalized_email` es el email en minúsculas y sin espacios):

   ```sql
   DELETE FROM identidad.usuario
   WHERE club_id = '00000000-0000-0000-0000-000000000001'
     AND normalized_email = 'admin@runcriticon.staging';
   ```

4. **Redeploy de App Runner (staging)** — al arrancar, `IdentidadSeeder` recrea el admin con el hash Argon2id del nuevo valor:

   ```bash
   aws apprunner start-deployment --service-arn $APP_RUNNER_ARN_STAGING
   ```

   App Runner tarda ~5-10 min.

5. **Verificación**:
   - `/actuator/health` reporta `UP`.
   - Login en staging con `admin@runcriticon.staging` + la contraseña nueva funciona.

6. **Revocar el valor antiguo**: no aplica (el `put-parameter --overwrite` lo reemplaza; el hash antiguo desaparece al borrar la fila en el paso 3).

7. **Registrar** en [`log-rotaciones.md`](log-rotaciones.md) (se crea en la primera rotación real) con fecha, secreto y operador.

## Rollback

Si el redeploy falla o el login nuevo no funciona:

- Re-inyectar en SSM el valor anterior (si se conservó) con `aws ssm put-parameter --overwrite` y repetir los pasos 3-4, **o** sembrar de nuevo con una contraseña conocida.
- **No afecta a producción** (no siembra). El único impacto es la pérdida de las sesiones del admin sembrado (debe volver a entrar), aceptable en staging.

## Referencias

- ADR-0003 D3 — semilla del primer admin del club.
- ADR-0013 D5/D10/D11 — convención SSM, política de rotación, runbooks.
- Plantilla base: [`configuracion-y-secretos-en-modulos.md` §9](../arquitectura/configuracion-y-secretos-en-modulos.md).
