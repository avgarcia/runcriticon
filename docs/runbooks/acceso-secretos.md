# Runbook — acceso humano a un secreto en SSM

> Invocado por [ADR-0013 D14](../adr/0013-configuracion-y-secretos.md#d14). Política operativa para que una persona del equipo lea (o, con rol admin, escriba) un secreto de runtime en SSM Parameter Store de forma auditada. Forma parte del [onboarding](../onboarding.md).

## Frecuencia

**Bajo demanda**, sin cadencia fija. Solo cuando hay una necesidad concreta:

- Incidente o *debugging* en un entorno remoto que exige conocer el valor.
- Rotación de un secreto (en ese caso manda el runbook de rotación, ver [`rotacion-secretos.md`](rotacion-secretos.md)).

En **desarrollo local nunca** se accede a SSM: el perfil `local` usa valores fake y `LocalProfileGuard` impide arrancar con credenciales AWS reales (ADR-0013 D13). Si lo que necesitas es un dato para depurar en local, ajusta los valores fake de `application-local.yml`; no abras acceso.

## Quién puede ejecutarlo

Según los roles IAM mínimos por path de [ADR-0013 D15](../adr/0013-configuracion-y-secretos.md#d15):

| Rol (SSO) | Puede |
|---|---|
| **Developer read-only** (`developer-readonly`, ADR-0006 D27) | `ssm:GetParameter` sobre `/runcriticon/staging/*` (sin producción) |
| **Developer admin** (`developer-admin-tfa`, con MFA) | `ssm:GetParameter` + `ssm:PutParameter` sobre `/runcriticon/staging/*` y `/runcriticon/production/*` |

CI (GitHub Actions con OIDC) **no tiene acceso** a secretos de runtime (ADR-0013 D15).

> **Pendiente**: los roles de desarrollador por SSO (IAM Identity Center, ADR-0006 D27) **no están aprovisionados todavía** en `infrastructure/terraform/`, que hoy solo define el entorno `staging` (más `localstack`). Hasta que existan, el nombre del perfil SSO y del rol de este runbook son marcadores.

## Prerrequisitos

- [ ] AWS CLI v2 instalado.
- [ ] Perfil SSO configurado contra IAM Identity Center (`aws configure sso`) con el rol que corresponda a la tabla anterior. En los comandos: `<perfil-sso>`.
- [ ] MFA activo si el rol es *Developer admin*.
- [ ] Motivo concreto del acceso (ticket de incidente o de rotación) — se anota en el registro.
- [ ] Terminal **no compartida** (sin sesión de pantalla compartida ni grabación).

## Procedimiento

1. **Iniciar sesión SSO** (credenciales temporales, ADR-0013 D14):

   ```bash
   aws sso login --profile <perfil-sso>
   aws sts get-caller-identity --profile <perfil-sso>   # confirmar cuenta y rol antes de seguir
   ```

2. **Localizar el nombre exacto** del secreto. Todo secreto sigue `/runcriticon/{env}/{component}/{name}` ([ADR-0013 D5](../adr/0013-configuracion-y-secretos.md#d5)); el catálogo está en [ADR-0013 D6](../adr/0013-configuracion-y-secretos.md#d6) y en [`configuracion-y-secretos-en-modulos.md` §2-§3](../arquitectura/configuracion-y-secretos-en-modulos.md). Listar solo nombres, **sin descifrar**:

   ```bash
   aws ssm get-parameters-by-path --path /runcriticon/{env}/ --recursive \
     --query "Parameters[].Name" --profile <perfil-sso>
   ```

   Secretos definidos hoy en Terraform (`infrastructure/terraform/modules/database` y `modules/secrets`):

   | Path | Uso |
   |---|---|
   | `/runcriticon/{env}/db/password` | Contraseña de RDS |
   | `/runcriticon/{env}/security/token-hmac-secret` | HMAC de tokens de un solo uso (ADR-0003 D13) |
   | `/runcriticon/{env}/crypto/userid-hash-salt` | Salt del hash de `userId` en logs (ADR-0011 D5) |
   | `/runcriticon/{env}/email/postmark-server-token` | Token de servidor de Postmark (ADR-0005 D1) |
   | `/runcriticon/{env}/email/postmark-webhook-secret` | Secreto del webhook de Postmark (ADR-0005 D9) |
   | `/runcriticon/staging/identidad/bootstrap-admin-password` | Semilla del admin, **solo staging** (ADR-0003 D3) |

3. **Leer el valor descifrado** — un único parámetro, el mínimo imprescindible:

   ```bash
   aws ssm get-parameter --name /runcriticon/{env}/{component}/{name} \
     --with-decryption --query "Parameter.Value" --output text --profile <perfil-sso>
   ```

   Requiere además `kms:Decrypt` sobre la clave KMS con la que el entorno cifra sus `SecureString`. CloudTrail registra cada `GetParameter` con `withDecryption=true` (ADR-0013 D14).

4. **Usar el valor sin propagarlo**. **Nunca** (ADR-0013 D14):
   - copiarlo al portapapeles del sistema sin necesidad;
   - pegarlo en un chat, ticket, PR o email;
   - escribirlo en un log, en un fichero del repo o en el historial de la shell de una máquina compartida.

5. **Escritura** (solo *Developer admin*, y solo dentro de un procedimiento de rotación): sigue el runbook de rotación del secreto concreto. Nunca un `put-parameter` suelto fuera de ese procedimiento.

6. **Cerrar la sesión** al terminar:

   ```bash
   aws sso logout --profile <perfil-sso>
   ```

## Verificación

- [ ] `aws sts get-caller-identity` mostró el rol esperado (no uno más privilegiado del necesario).
- [ ] El valor no ha quedado en ningún canal de la lista del paso 4.
- [ ] Sesión SSO cerrada.

## Rollback

Una lectura no cambia estado, así que no hay nada que deshacer **salvo que el valor se haya expuesto** (pegado en un chat, un log, un ticket, una pantalla compartida…). En ese caso se trata como **compromiso**:

- Rotación **inmediata** del secreto, invariante no negociable (ADR-0013 D10), siguiendo su runbook de [`rotacion-secretos.md`](rotacion-secretos.md). Si el secreto aún no tiene runbook propio, se aplica el procedimiento genérico de ADR-0013 D11.
- Si la exposición fue en el repositorio: además, lo que marca ADR-0013 D12 (revisión y, si aplica, reescritura de historia y postmortem).

## Registro

- **Automático**: CloudTrail registra la llamada, la identidad SSO y la hora (ADR-0013 D14).
- **Manual**: anota en el ticket del incidente o de la rotación qué secreto se consultó, quién y por qué. CloudTrail dice *quién* y *cuándo*; el ticket dice *por qué*.

## Cruces

- [ADR-0013](../adr/0013-configuracion-y-secretos.md) D5 (convención de nombres), D6 (catálogo), D10 (rotación inmediata ante sospecha), D11 (procedimiento de rotación), D12 (secretos filtrados), D13 (perfil local sin SSM), D14 (acceso humano + CloudTrail), D15 (roles IAM mínimos).
- ADR-0006 D27 (roles de operador humano `developer-readonly` / `developer-admin-tfa` con SSO).
- [`configuracion-y-secretos-en-modulos.md`](../arquitectura/configuracion-y-secretos-en-modulos.md) §2, §3 y §11.
- [`rotacion-secretos.md`](rotacion-secretos.md) — índice de runbooks de rotación.
