# Runbook — recuperación ante desastre (RDS + App Runner)

> Invocado por ADR-0006 D29 (DR: **RTO < 4 h, RPO < 1 h**), con ADR-0006 D9 (backups automáticos 30
> días + PITR) y ADR-0014 D8 (tras restaurar, **reaplicar obligatoriamente** los borrados RGPD
> posteriores al punto de restauración). El paso de reaplicación de olvidos **no se puede saltar**.

> ⚠️ **Estado de la infraestructura a la fecha de este runbook**: la IaC existe
> (`infrastructure/terraform/`), pero **no se ha aplicado** en ningún entorno (ver
> `infrastructure/terraform/README.md` §Estado de despliegue) y solo está compuesto el entorno
> `staging` — **no hay `environments/production`**. Los comandos de abajo se derivan de la IaC, no de
> una restauración real. **Este runbook no está validado hasta que se ejecute un simulacro en staging**
> (ver "Simulacro").

## Cuándo se ejecuta

- **Pérdida o corrupción de datos** en RDS (borrado accidental, migración destructiva, corrupción):
  restauración a un instante anterior (PITR).
- **Pérdida de la instancia RDS** (fallo irrecuperable de la AZ — Single-AZ en MVP, ADR-0006 D7/D10).
- **Pérdida del servicio App Runner** o de su configuración: reaprovisionamiento con Terraform y
  redespliegue.
- **Pérdida de la región `eu-west-1`**: ver "Caída de región" — **no hay recuperación posible fuera de
  la región** en MVP; es una ventana de indisponibilidad aceptada por ADR-0006 D29.

Si el origen del desastre es un acceso malicioso, abrir en paralelo
[`respuesta-a-brecha.md`](respuesta-a-brecha.md): la pérdida de disponibilidad o integridad de datos
personales es una brecha (Art. 33).

## Quién puede ejecutarlo

- **Operador admin con SSO + MFA** (ADR-0006 D27). El rol OIDC de CI está acotado a despliegue y **no**
  puede reaprovisionar infraestructura (D27: `terraform apply` solo desde el terminal del operador).
- Decisión de restaurar producción: cualquier ingeniero puede iniciarla, notificando al admin
  (criterio de ADR-0010 D23).

## Objetivos

| Objetivo | Valor | Qué lo sostiene |
|---|---|---|
| RPO | < 1 h | Backups automáticos + PITR (D9). En la práctica PITR permite restaurar hasta ~5 min antes del fallo. |
| RTO | < 4 h | Restauración RDS (típicamente 30-90 min según tamaño — `<pendiente: medir en simulacro>`) + corte de tráfico + redeploy + reaplicación de olvidos + validación |

## Prerrequisitos

- [ ] AWS CLI con SSO + MFA sobre la cuenta del entorno, región `eu-west-1`.
- [ ] Terraform ≥ 1.7 y acceso al state remoto (`s3://runcriticon-tfstate`, lock en DynamoDB
      `runcriticon-tfstate-lock`, ADR-0006 D19).
- [ ] **Registro de supresiones RGPD** accesible (paso 5): `<pendiente: ubicación del registro de
      solicitudes RGPD — fuera del repo>`. Es la **única fuente** de la lista de olvidos a reaplicar.
- [ ] Acceso a la BD restaurada: `<pendiente: acceso-rds.md no existe todavía, ADR-0006 D13>`.
- [ ] Hora exacta (UTC) del incidente, para elegir el punto de restauración.

## Recursos implicados (de la IaC)

Nombres parametrizados por entorno (`<env>` = `staging` hoy; `production` cuando exista):

| Recurso | Nombre / valor | Módulo Terraform |
|---|---|---|
| Instancia RDS | `runcriticon-<env>` (PostgreSQL 16, `db.t4g.small`, Single-AZ, cifrada con KMS `alias/runcriticon-<env>-rds`) | `database` |
| Backups | `backup_retention_period = 30`, ventana `02:00-03:00` UTC | `database` |
| Protección | `deletion_protection = true`; snapshot final `runcriticon-<env>-final` al destruir | `database` |
| Subnet group | `runcriticon-<env>` (subnets privadas) | `database` |
| Security group de BD | output `database_security_group_id` del módulo `network` | `network` |
| Contraseña maestra | SSM `/runcriticon/<env>/db/password` | `database` |
| Servicio App Runner | `runcriticon-<env>`, `auto_deployments_enabled = false` | `runtime` |
| Imágenes | ECR `runcriticon-<env>` (IMMUTABLE, conserva las últimas 20) | `runtime` |
| Logs | CloudWatch `/runcriticon/<env>/application` | `observability` |

> **Deriva ADR ↔ IaC**: ADR-0006 D29 paso 4 dice "redespliegue desde la imagen vigente en **GHCR**".
> La IaC despliega desde **ECR** (réplica desde GHCR, ADR-0010 D2). Para el DR vale cualquiera de las
> dos mientras la imagen exista en ECR; si la ECR se perdió, hay que volver a replicar desde GHCR.

## Procedimiento

### 1. Abrir el incidente y fijar el punto de restauración

- Anotar hora de inicio (arranca el RTO), operador, causa sospechada.
- Elegir el **instante de restauración** (UTC): justo antes del borrado/corrupción. Comprobar la
  ventana disponible:

```bash
aws rds describe-db-instances --db-instance-identifier runcriticon-<env> \
  --query 'DBInstances[0].[EarliestRestorableTime,LatestRestorableTime,DBInstanceStatus]'
```

### 2. Cortar la escritura sobre la BD dañada

Evita que la app siga escribiendo sobre datos corruptos mientras se restaura, y que usuarios operen
sobre un estado que se va a descartar.

```bash
aws apprunner pause-service --service-arn $APP_RUNNER_ARN_<ENTORNO>
```

`<pendiente: validar en simulacro que pausar el servicio es la forma adecuada de cortar tráfico, y
cómo se comunica la indisponibilidad a los usuarios — no hay página de mantenimiento>`.

### 3. Restaurar RDS a un instante (PITR) en una instancia nueva

PITR **siempre crea una instancia nueva**; no restaura sobre la existente. Hay que pasarle
explícitamente red y seguridad, o la crea en la VPC por defecto y accesible de forma distinta a la
original:

```bash
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier runcriticon-<env> \
  --target-db-instance-identifier runcriticon-<env>-restaurada \
  --restore-time <YYYY-MM-DDTHH:MM:SSZ> \
  --db-instance-class db.t4g.small \
  --db-subnet-group-name runcriticon-<env> \
  --vpc-security-group-ids <database_security_group_id> \
  --no-publicly-accessible \
  --no-multi-az \
  --deletion-protection \
  --copy-tags-to-snapshot

aws rds wait db-instance-available --db-instance-identifier runcriticon-<env>-restaurada
```

(Si la instancia origen ya no existe, restaurar desde el snapshot automático más reciente con
`aws rds restore-db-instance-from-db-snapshot` y los mismos parámetros de red; el RPO pasa a ser el de
ese snapshot, hasta 24 h — por encima del objetivo de D29.)

La instancia restaurada hereda el cifrado con la misma KMS y la contraseña maestra vigente en el
momento restaurado; `<pendiente: verificar en simulacro que coincide con /runcriticon/<env>/db/password
o si hay que resetearla con modify-db-instance --master-user-password>`.

### 4. Apuntar la aplicación a la instancia restaurada

Opciones, **ninguna validada todavía** (`<pendiente: elegir y validar en simulacro>`):

- **A — Intercambio de nombres** (mantiene el endpoint que ya conoce App Runner y el state de
  Terraform): renombrar la original a `runcriticon-<env>-danada` y la restaurada a `runcriticon-<env>`
  con `aws rds modify-db-instance --new-db-instance-identifier … --apply-immediately`. El endpoint DNS
  sigue el nombre. Queda por reconciliar el state de Terraform (el `resource_id` interno de la instancia
  cambia).
- **B — Nuevo endpoint** en la configuración de App Runner. Implica un `terraform apply` o un
  `update-service` manual, y deriva frente a la IaC.

La instancia original **no se borra**: queda con `deletion_protection` para análisis forense y como
rollback (ver "Rollback").

### 5. Reaplicar los olvidos pendientes (ADR-0014 D8) — obligatorio, antes de abrir el servicio

La BD restaurada es una foto del pasado: **toda persona suprimida después del instante de
restauración vuelve a existir**, con sus datos de salud, sus sesiones y sus proyecciones. Abrir el
servicio así reintroduce PII que el titular pidió borrar.

**La lista no puede salir de la propia BD restaurada**: no contiene los borrados posteriores, y el
asiento `CUENTA_ELIMINADA` de `identidad.evento_auditoria` se escribe sin sujeto (el runbook de
supresión lo explica). La única fuente es el **registro externo de solicitudes de supresión**
(prerrequisitos).

1. Del registro, extraer las supresiones **ejecutadas** con fecha **posterior** al instante de
   restauración: `usuarioId` y club de cada una.
2. Reanudar el servicio **sin anunciarlo** a los usuarios (hace falta la aplicación para el paso
   siguiente; `<pendiente: cómo reanudar sin exponer el servicio a usuarios — validar en simulacro>`):

   ```bash
   aws apprunner resume-service --service-arn $APP_RUNNER_ARN_<ENTORNO>
   ```

3. Para cada `usuarioId`, ejecutar la supresión con el **mismo endpoint** que el runbook de supresión
   (dispara borrado físico, anonimización de auditoría y propagación a los módulos):

   ```bash
   curl -X DELETE https://<host>/api/usuarios/<usuarioId> \
     -H "Cookie: <sesión del ADMIN>" -H "X-XSRF-TOKEN: <token CSRF>"
   ```

   - `204` — reaplicado.
   - `404` — la persona no existía en el instante restaurado (se dio de alta y de baja después): nada
     que reaplicar.
   - Verificar la propagación con las consultas del paso 3 de
     [`derechos-rgpd-supresion.md`](derechos-rgpd-supresion.md).

4. **Registrar** qué olvidos se reaplicaron (D8: "registro auditado de qué olvidos se reaplican"):
   instante de restauración, lista de `usuarioId`, resultado de cada uno, operador.

### 6. Revisar el outbox de la BD restaurada

`event_publication` vuelve al estado del instante restaurado: eventos que ya se habían procesado
después de ese instante no existen, y los que estaban pendientes en ese instante se reentregarán al
arrancar (`republish-outstanding-events-on-restart: true`). Los listeners son idempotentes
(`evento_procesado`), así que la reentrega es segura.

```sql
SELECT event_type, count(*) FROM event_publication
WHERE completion_date IS NULL GROUP BY event_type;
```

Tras unos minutos, debe tender a 0. Si no, ver el paso 4 de
[`derechos-rgpd-supresion.md`](derechos-rgpd-supresion.md).

### 7. Validación y apertura

No hay pipeline de CD ni smoke tests automatizados todavía (ADR-0010; el workflow `ci.yml` no
despliega), así que la validación es manual:

- [ ] `GET https://<host>/actuator/health` → `UP` (es el health check de App Runner).
- [ ] Login con una cuenta de prueba del club y navegación básica (plan, marcas).
- [ ] Los olvidos del paso 5 están aplicados (paso 3 de supresión da 0 filas para cada uno).
- [ ] Anotar hora de fin → RTO real.

Comunicar la reapertura a los usuarios. Si hubo pérdida de datos entre el instante restaurado y el
incidente, comunicarlo también (y valorarlo como brecha de disponibilidad en
[`respuesta-a-brecha.md`](respuesta-a-brecha.md)).

### 8. Reaprovisionamiento completo (pérdida de App Runner, VPC u otros recursos)

Si lo perdido es infraestructura y no datos, la IaC permite levantarla de cero (ADR-0006 D18/D29
paso 1):

```bash
cd infrastructure/terraform/environments/<env>
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

- `terraform.tfvars` no está en el repo (solo `terraform.tfvars.example`); el operador lo tiene en
  local.
- Parámetros SSM: los externos (tokens de Postmark, contraseña bootstrap de staging) llevan
  `lifecycle.ignore_changes = [value]` y se crean con placeholder — si se perdieron, Terraform los
  recrea **con el placeholder** y hay que volver a poner cada valor real a mano (ver
  [`rotacion-secretos.md`](rotacion-secretos.md)). `<pendiente: verificar en simulacro qué valor
  toman los secretos generados por Terraform (HMAC, salt, db/password) si se recrean desde el state>`.
- App Runner arranca con `var.image_tag`; después, desplegar la imagen vigente de ECR (el tag del
  último commit desplegado).

## Caída de región

State de Terraform (S3/DynamoDB), ECR, backups y snapshots de RDS viven **todos en `eu-west-1`**. Si
la región cae, no hay nada desde lo que reaprovisionar en otra: se espera a que AWS la recupere. Es
la ventana de indisponibilidad que acepta ADR-0006 D29. **Disparador para cambiarlo** (ADR-0015, tabla
maestra "Backups cross-region"; ADR-0006 D9/D29): cliente con SLA contractual > 99,5 %.

## Rollback

- Mientras la instancia original siga existiendo (con `deletion_protection`), volver atrás es
  deshacer el paso 4 (volver a apuntar a ella) — **perdiendo** lo escrito sobre la restaurada.
- La instancia original se elimina solo tras confirmar el servicio restaurado y cerrar el análisis
  forense, quitando antes `deletion_protection` a propósito.

## Qué NO se hace

- **No se restauran backups selectivamente** para recuperar datos de una persona suprimida (ADR-0014
  D8, ADR-0006 D9): la restauración es siempre completa y va seguida del paso 5.
- **No se abre el servicio sin el paso 5**, aunque presione el RTO.

## Simulacro

`<pendiente: primer simulacro de DR en staging tras el primer terraform apply>` — objetivo: medir el
RTO real, validar los pasos marcados como pendientes (2, 3, 4, 5.2) y actualizar este runbook con lo
aprendido. Hasta entonces, los tiempos del RTO son estimaciones.

## Registro

- Incidente: hora de inicio y fin, instante de restauración, RTO y RPO reales, operador, causa.
- Olvidos reaplicados (paso 5) — registro exigido por ADR-0014 D8. Contiene `usuarioId`: guardarlo
  junto al registro de solicitudes RGPD, **no en el repo**.
- Postmortem en ≤ 1 semana (ADR-0010 D23).

## Referencias

- ADR-0006 D7 (RDS), D9 (backups + PITR), D10 (Multi-AZ), D13 (acceso a RDS), D18-D19 (Terraform y
  state), D27 (IAM), D29 (DR).
- ADR-0014 D8 (backups y reaplicación de olvidos), ADR-0007 (outbox), ADR-0010 D2/D23 (imágenes y
  rollback).
- `infrastructure/terraform/modules/database/`, `modules/runtime/`, `environments/staging/`.
- [`derechos-rgpd-supresion.md`](derechos-rgpd-supresion.md), [`respuesta-a-brecha.md`](respuesta-a-brecha.md).
