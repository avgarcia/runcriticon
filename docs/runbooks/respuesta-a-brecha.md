# Runbook — respuesta a una brecha de datos personales (Arts. 33 y 34 RGPD)

> Invocado por ADR-0014 D26 (runbook de respuesta a brechas), con D24 (notificación a la AEPD
> **≤ 72 h**) y D25 (comunicación a interesados si hay alto riesgo). El producto trata **datos de
> salud** (Art. 9): casi cualquier brecha que los toque es de riesgo para los interesados. Este
> runbook existe para que el reloj de 72 h no se consuma decidiendo qué hacer.

## Cuándo se ejecuta

Ante cualquier sospecha fundada de **destrucción, pérdida, alteración, comunicación o acceso no
autorizado** a datos personales, incluidos:

- Acceso indebido a la BD, a SSM o a la cuenta AWS; credencial o secreto filtrado (repo, log, chat).
- Un usuario que ve datos de otro club u otra persona (fallo de autorización / IDOR).
- Un export RGPD o un email entregado a quien no debía.
- Pérdida de datos sin backup restaurable (ver también [`disaster-recovery.md`](disaster-recovery.md)).
- Aviso externo (usuario, investigador de seguridad, subencargado: AWS, Postmark, GitHub — ADR-0014 D22).

**Ante la duda, se abre**. Abrir y cerrar como "no brecha" cuesta una entrada en el registro; no
abrir una brecha real cuesta el plazo de 72 h.

## Quién puede ejecutarlo

- **Cualquier miembro del equipo** abre la brecha y avisa al responsable del tratamiento en cuanto
  la detecta.
- La **decisión de notificar** (pasos 4-6) es del **responsable del tratamiento** (ADR-0014 D23:
  `<pendiente: persona concreta que actúa como responsable y su suplente — la entidad jurídica está
  pendiente de constitución>`).
- Acciones de contención sobre producción: acceso con MFA (ADR-0006 D27).

## Plazos

| Hito | Plazo | Origen |
|---|---|---|
| Notificación a la AEPD | **≤ 72 h desde que el responsable es consciente** de la brecha (no desde el ataque) | Art. 33, ADR-0014 D24 |
| Notificación tardía | Permitida, pero **explicando el motivo de la dilación** | Art. 33.1, D24 |
| Comunicación a interesados | **Sin dilación indebida** si hay alto riesgo | Art. 34, D25 |
| Postmortem | **≤ 1 semana** tras el cierre | ADR-0014 D26 paso 7, ADR-0010 D23 |

**Anotar la hora de "consciencia" (T0) en el primer minuto**: es la que cuenta la AEPD.

## Prerrequisitos

- [ ] Registro de brechas donde abrir la entrada: `<pendiente: ubicación del registro de brechas —
      fuera del repo, nunca en git porque contiene PII de afectados>`. Toda brecha se registra,
      **se notifique o no** (Art. 33.5).
- [ ] Contacto del responsable y suplente (ver arriba).
- [ ] Acceso AWS CLI con MFA al entorno afectado.

## Procedimiento

### 1. Detección y apertura

1. Abrir la entrada en el registro con: **T0**, quién detecta, cómo, descripción inicial.
2. Avisar al responsable del tratamiento.
3. **No borrar evidencias** (logs, filas de auditoría, sesiones) aunque parezca que "limpia" el
   problema: el análisis del paso 3 las necesita.

Fuentes de detección que existen hoy:

- `auditoria.evento` — denegaciones de autorización (`ACCESO_DENEGADO`) y accesos a datos sensibles
  (`ACCESO_DATOS_SENSIBLES`), ADR-0009 D17. Un pico de denegaciones de un mismo actor es la señal más
  directa de un intento de IDOR.
- `identidad.evento_auditoria` — logins fallidos, rate limits (`MAGIC_LINK_RATE_LIMITED`,
  `RESETEO_RATE_LIMITED`, `INVITACION_RATE_LIMITED`), cambios de email y contraseña.
- CloudWatch Logs de la aplicación — log group `/runcriticon/<ENTORNO>/application`.
- CloudTrail — accesos a SSM (`GetParameter` con descifrado, ADR-0013 D14) y sesiones SSM a RDS
  (ADR-0006 D13).

> ⚠️ **No hay alarmas de seguridad configuradas.** ADR-0014 D26 cita "alarmas de ADR-0010 D22", pero
> esas son alarmas del **pipeline de CI/CD** (main rojo, deploy fallido, minutos de Actions), no de
> seguridad. El módulo Terraform `observability` solo crea el log group y los budgets: no hay alarmas
> de CloudWatch/AMG, ni CloudTrail gestionado en IaC, ni GuardDuty. La detección hoy depende de
> revisión manual o aviso externo. `<pendiente: alarmas de seguridad mínimas (pico de
> ACCESO_DENEGADO, GetParameter sobre /runcriticon/production/*) — ADR-0011 D16 prevé
> alarmas/{alarma}.md al configurarlas>`.

### 2. Contención

Cortar el vector **antes** de analizarlo a fondo. Palancas disponibles, de más acotada a más amplia:

**Una cuenta comprometida** (el ADMIN del club, con su sesión):

```bash
# cerrar todas sus sesiones
curl -X POST https://<host>/api/usuarios/<usuarioId>/revocacion-sesiones \
  -H "Cookie: SESSION=<sesión del ADMIN>; XSRF-TOKEN=<token CSRF>" -H "X-XSRF-TOKEN: <token CSRF>"

# si además hay que impedir que vuelva a entrar
curl -X POST https://<host>/api/usuarios/<usuarioId>/desactivacion \
  -H "Cookie: SESSION=<sesión del ADMIN>; XSRF-TOKEN=<token CSRF>" -H "X-XSRF-TOKEN: <token CSRF>"
```

La desactivación **no tiene endpoint de reactivación**: deshacerla exige un `UPDATE` manual en
`identidad.usuario`. Usarla solo si revocar sesiones no basta.

**Todas las sesiones de todos los usuarios** (sospecha de robo masivo de cookies o de la tabla de
sesiones). Spring Session JDBC vive en el esquema `public` (`V202606030001__crea_spring_session.sql`);
borrar las sesiones fuerza a todo el mundo a volver a autenticarse:

```sql
DELETE FROM spring_session;  -- spring_session_attributes cae por ON DELETE CASCADE
```

(Requiere acceso de escritura a la BD: `<pendiente: acceso-rds.md no existe todavía, ADR-0006 D13>`.)

**Un secreto filtrado** — rotarlo. Secretos del entorno, todos en SSM bajo
`/runcriticon/<ENTORNO>/…` (módulos Terraform `secrets` y `database`):

| Parámetro SSM | Efecto de rotarlo | Runbook |
|---|---|---|
| `security/token-hmac-secret` | Invalida magic links e invitaciones en vuelo; cambia también el hash de email (`EmailHasherImpl`) usado en rate limiting y en `email_hash` de la auditoría | `<pendiente: rotacion-token-hmac-secret.md>` |
| `crypto/userid-hash-salt` | Cambia el hash de `userId` en logs (se pierde la continuidad de agrupación) | `<pendiente: sin runbook previsto en el catálogo>` |
| `email/postmark-server-token` | Corta el envío de email hasta actualizarlo | `<pendiente: rotacion-postmark-token.md>` |
| `email/postmark-webhook-secret` | Rechaza webhooks de Postmark firmados con el anterior | `<pendiente: sin runbook previsto en el catálogo>` |
| `db/password` | Requiere cambiar también la contraseña del usuario maestro en RDS | `<pendiente: rotacion-db-password.md>` |
| `identidad/bootstrap-admin-password` (solo staging) | — | [`rotacion-bootstrap-admin-password.md`](rotacion-bootstrap-admin-password.md) |

Mientras no existan esos runbooks, aplicar las reglas comunes de
[`rotacion-secretos.md`](rotacion-secretos.md): `aws ssm put-parameter --overwrite` con el nuevo valor
+ `aws apprunner start-deployment --service-arn $APP_RUNNER_ARN_<ENTORNO>`.

**Una credencial humana de AWS comprometida** — revocar sus sesiones en IAM Identity Center y revisar
CloudTrail de esa identidad. `<pendiente: procedimiento de revocación de acceso SSO — depende de
cómo se configure IAM Identity Center, ADR-0006 D27>`.

**Un fallo de autorización en la aplicación** — hotfix + deploy, o rollback a la imagen anterior
(ADR-0010 D23). Si no hay fix inmediato: `<pendiente: no existe interruptor para desactivar un
endpoint concreto sin redeploy>`.

Anotar en el registro cada acción de contención con su hora.

### 3. Análisis del alcance

Responder, con evidencias, a lo que pide la notificación (paso 5):

- **Qué datos**: categorías (identificativos, credenciales, **datos de salud**: reportes de sesión,
  reajustes con motivo `MOLESTIAS`/`LESION`, marcas). Mapear tablas → categoría con los `RGPD.md` de
  cada módulo.
- **Cuántas personas** y de qué roles (alumnos, entrenadores, admin).
- **Ventana temporal**: primer y último acceso indebido.
- **Qué riesgo** para los derechos de las personas (reidentificación, exposición de salud, suplantación).

Consultas útiles (solo lectura):

```sql
-- denegaciones y accesos a datos sensibles de un actor sospechoso
SELECT tipo, sujeto_id, recurso, motivo, ts
FROM auditoria.evento
WHERE actor_id = '<actorId>' AND ts BETWEEN '<desde>' AND '<hasta>'
ORDER BY ts;

-- actividad de cuenta del sospechoso (logins, cambios)
SELECT tipo, sujeto_id, ts, ip
FROM identidad.evento_auditoria
WHERE actor_id = '<actorId>' AND ts BETWEEN '<desde>' AND '<hasta>'
ORDER BY ts;
```

La consulta sobre `auditoria.evento` también está en la aplicación para el ADMIN:
`GET /api/auditoria/eventos?actorId=&sujetoId=&tipo=&desde=&hasta=`.

**Limitación del análisis**: `auditoria.evento` solo registra accesos de un tercero a datos de salud
en los casos de uso anotados con `@AuditAccess` (hoy `ListCoachAlertsQuery`; ver
`seguimiento/RGPD.md`). Una lectura por otro camino — consulta directa a BD, un endpoint sin anotar —
no deja rastro ahí; el alcance se reconstruye entonces con CloudTrail y CloudWatch Logs.

### 4. Decisión de notificación

El responsable decide y **deja la decisión motivada en el registro**, notifique o no:

| Pregunta | Sí → | No → |
|---|---|---|
| ¿Es improbable que la brecha suponga un riesgo para los derechos y libertades? | Registrar sin notificar (motivar por qué) | Notificar a la AEPD (paso 5) |
| ¿Supone un **alto** riesgo? | Comunicar además a los afectados (paso 6) | Solo AEPD |
| ¿Se aplica alguna excepción del Art. 34.3 (datos cifrados de forma que la brecha no compromete su confidencialidad, o medidas posteriores que eliminan el alto riesgo)? | No comunicar a afectados (motivar) | Comunicar |

Con datos de salud expuestos a terceros, la respuesta por defecto es **notificar**. El cifrado en
reposo de RDS (ADR-0014 D3) **no** vale como excepción del Art. 34.3 si el acceso se hizo a través de
la aplicación o de credenciales válidas: los datos se leyeron ya descifrados.

### 5. Notificación a la AEPD (≤ 72 h desde T0)

- **Canal**: `<pendiente: canal de notificación de brechas de la AEPD a usar (sede electrónica) y
  credencial/certificado con que firma el responsable — verificar antes de la beta>`.
- **Notificación por fases**: si a las 72 h no se conoce todo el alcance, se notifica lo que se sabe
  y se completa después (Art. 33.4). No esperar a tener el análisis cerrado.

Plantilla (campos de ADR-0014 D26 paso 5 / Art. 33.3):

```text
1. Responsable del tratamiento
   - Denominación: <pendiente: denominación legal — "Runcriticon S.L." a confirmar, ADR-0014 D23>
   - Contacto: privacidad@runcriticon.com
   - Persona de contacto para esta brecha: <nombre, cargo, teléfono>

2. Naturaleza de la brecha
   - Tipo: confidencialidad / integridad / disponibilidad
   - Fecha y hora de la brecha (o ventana estimada): <…>
   - Fecha y hora en que el responsable tuvo conocimiento (T0): <…>
   - Descripción de lo ocurrido: <…>
   - Si se notifica pasadas 72 h, motivo de la dilación: <…>

3. Categorías y número aproximado
   - Interesados afectados: <n> (alumnos / entrenadores / administradores del club)
   - Registros afectados: <n>
   - Categorías de datos: identificativos / credenciales / datos de salud (Art. 9) / otros

4. Posibles consecuencias para los interesados
   - <exposición de datos de salud, suplantación, pérdida de acceso, …>

5. Medidas adoptadas o propuestas
   - Contención: <paso 2 del runbook, con horas>
   - Mitigación de efectos para los afectados: <…>
   - Medidas para evitar que se repita: <resultado previsto del postmortem>

6. Comunicación a los interesados
   - Sí (fecha y canal) / No (motivo, Art. 34.3)
```

### 6. Comunicación a los afectados (si alto riesgo, D25)

- **Contenido** (Art. 34.2): en lenguaje claro — qué ha pasado, qué datos suyos, posibles
  consecuencias, qué se ha hecho, qué pueden hacer ellos (p. ej. cambiar contraseña), contacto
  `privacidad@runcriticon.com`.
- **Canales fijados por D25**:
  - Email transaccional (ADR-0005) al email vigente del afectado —
    `<pendiente: no existe plantilla de email de aviso de brecha en el backend>`. El texto **no** puede
    incluir datos de salud (premisa de ADR-0005 D12).
  - Página `/avisos-de-seguridad` del producto — `<pendiente: la ruta no existe en el frontend>`.
- Hasta que existan, el responsable decide el canal provisional y lo anota en el registro.

### 7. Cierre y postmortem

- Cerrar la entrada del registro: hechos, efectos, medidas adoptadas (Art. 33.5 — es lo que la AEPD
  puede pedir en una inspección).
- **Postmortem sin culpables en ≤ 1 semana** (ADR-0010 D23): causa raíz, cronología, qué detectó la
  brecha y qué debió detectarla antes, acciones con responsable y ticket.
- Si la brecha revela un hueco que un ADR da por cubierto, abrir la revisión del ADR correspondiente.

## Verificación

- [ ] T0 anotado y notificación a la AEPD enviada dentro de las 72 h (o dilación motivada).
- [ ] El vector está cerrado: la acción que causó la brecha ya no es reproducible.
- [ ] Decisión de notificar / no notificar motivada en el registro.
- [ ] Postmortem publicado con acciones y tickets.

## Rollback

- **No aplica a la notificación**: una notificación enviada no se retira; si resulta que no era
  brecha, se envía una comunicación complementaria a la AEPD.
- **Contención**: revocar sesiones o borrar `spring_session` no tiene rollback (los usuarios vuelven a
  iniciar sesión). Desactivar una cuenta se deshace con un `UPDATE` manual de `identidad.usuario.estado`
  a `ACTIVO` (no hay endpoint). Rotar un secreto se deshace volviendo a poner el valor anterior en SSM
  — **nunca** si ese valor es el comprometido.

## Registro

Registro de brechas del responsable (ver prerrequisitos): T0, cronología, acciones con hora,
decisión motivada, notificaciones con fecha y justificante, postmortem. Toda brecha, notificada o no.

## Referencias

- ADR-0014 D3 (cifrado en reposo), D22 (subencargados), D23 (responsable), D24 (AEPD ≤ 72 h), D25
  (interesados), D26 (este runbook).
- ADR-0009 D17 (`auditoria.evento`), ADR-0003 D15 (`identidad.evento_auditoria`).
- ADR-0010 D23 (rollback y postmortem), ADR-0013 D14 (acceso humano a secretos + CloudTrail).
- ADR-0006 D13 (acceso a RDS), D27 (IAM + MFA).
- [`rotacion-secretos.md`](rotacion-secretos.md), [`disaster-recovery.md`](disaster-recovery.md).
