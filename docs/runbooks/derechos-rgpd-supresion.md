# Runbook — atención de una solicitud de supresión (Art. 17 RGPD)

> Invocado por ADR-0014 D14 (supresión, cruce a D6 borrado mixto + D7 propagación). El borrado es
> **irreversible** — este procedimiento existe para que verificar, ejecutar y confirmar sean pasos
> explícitos, no algo que el operador improvisa en el momento.

## Cuándo se ejecuta

- El titular solicita la supresión de su cuenta por correo a `privacidad@runcriticon.com` (D11), o
  se lo pide directamente al admin de su club.
- **No existe self-service todavía** — D14 describe un botón "Eliminar mi cuenta" en el perfil que no
  está construido. Hoy el único camino es este runbook, ejecutado por un humano.

## Quién puede ejecutarlo

- El **ADMIN** del club de la persona (`Role.ADMIN` + `Resource.USER`/`Action.DELETE` en
  `AuthorizationMatrix`) o el responsable del tratamiento operando con esas credenciales.
- En producción: acceso con MFA (ADR-0006 D27).

## Plazo

- **1 mes** desde la solicitud (Art. 12.3, D11), prorrogable a **3 meses** si es compleja o numerosa
  — con comunicación al interesado dentro del primer mes explicando la prórroga y el motivo.
- Propagación interna a los módulos consumidores: **< 24 h p95** (NFR de ADR-0014). En la práctica es
  casi instantánea (el listener se dispara en el mismo `commit`); las 24 h son el margen para el caso
  en que la propagación falle y necesite reintento (ver más abajo).

## Prerrequisitos

- [ ] La solicitud está registrada (email del titular, fecha, club) — es lo que demuestra que se
      atendió dentro de plazo si alguien lo audita después.
- [ ] Verificación de identidad del solicitante completada (siguiente sección).
- [ ] Se conoce el `usuarioId` (UUID), el rol (`ALUMNO`/`ENTRENADOR`/`ADMIN`) y el `club_id` de la
      persona a suprimir — vía la pantalla de gestión de usuarios del club o consulta directa.

## Verificación de identidad del solicitante

Mismo criterio que D12 fija para las solicitudes de acceso — **no es self-service, así que hay que
confirmar que quien pide el borrado es de verdad el titular**, no un suplantador pidiendo borrar la
cuenta de otra persona:

1. Confirmación por el **email vigente** de la cuenta (el que aparece en `identidad.usuario`).
2. Confirmación **fuera de banda** con el admin del club (llamada, mensaje directo — un canal
   distinto del email de la solicitud).

Sin los dos, no se ejecuta. Es el paso más barato de saltarse y el más caro de haberse saltado: el
borrado que sigue no tiene deshacer.

## Procedimiento

### 1. Confirmar los datos antes de ejecutar

Anotar: `usuarioId`, rol, `club_id`, y si el solicitante es el propio titular o el admin en su
nombre. Dos guardas del caso de uso devuelven `409 Conflict` y hay que saber leerlas, no son un bug:

- **No puedes suprimir tu propia cuenta** vía este endpoint (`actor.userId == targetUserId` → 409).
  Si el admin quiere autosuprimirse, lo hace otro admin del club.
- **No puedes suprimir al último ADMIN activo del club** (409, `"no puedes eliminar el último
  administrador del club"`) — dejaría el club sin nadie que pueda gestionar nada. Si es ese caso,
  primero hay que dar de alta o activar a otro admin.

### 2. Ejecutar el borrado

```bash
curl -X DELETE https://<host>/api/usuarios/<usuarioId> \
  -H "Cookie: <sesión del ADMIN autenticado>"
```

- **Éxito**: `204 No Content`.
- **Repetir la llamada da `404 Not Found`**, no `204` — el endpoint no es idempotente a propósito: el
  usuario ya no existe, así que no hay "no-op silencioso" que fingir. Un `404` en el segundo intento
  es la confirmación de que el primero funcionó, no un fallo.

Dentro de la transacción (`DeleteUserCommand`, todo o nada — si algo falla, no queda rastro parcial):

1. Se revocan todas las sesiones activas del usuario.
2. Se borran físicamente sus datos en `identidad`: la fila de usuario, invitaciones, magic links,
   histórico de contraseñas.
3. Se anonimiza `identidad.evento_auditoria`: `actor_id`/`sujeto_id` → `NULL` donde mencionan a esta
   persona, IP truncada, `email_hash` purgado de `metadata`. **Las filas no se borran** — es
   responsabilidad proactiva, el rastro de auditoría sobrevive a la persona que menciona.
4. Se escribe un asiento `CUENTA_ELIMINADA` (sin `subjectId`: el sujeto ya está anonimizado en el
   mismo barrido del paso 3).
5. Se publica `AlumnoEliminado` o `EntrenadorEliminado` al outbox — **excepto si el suprimido es
   ADMIN**, que no publica evento (no existe como persona proyectada en otros módulos). Si el
   solicitante era un ADMIN, los pasos de propagación de más abajo no aplican — su borrado termina
   aquí.

### 3. Verificar la propagación

Los módulos consumidores procesan el evento de forma asíncrona (outbox de Spring Modulith). Confirmar
que cada uno de ellos aplicó su parte, contra el `usuarioId` suprimido:

```sql
-- club_taxonomia: la proyección y todo lo que colgaba de la persona debe estar a 0 filas
SELECT count(*) FROM club_taxonomia.persona            WHERE id = '<usuarioId>';
SELECT count(*) FROM club_taxonomia.alumno_tag          WHERE alumno_id = '<usuarioId>';
SELECT count(*) FROM club_taxonomia.grupo_alumno_override WHERE alumno_id = '<usuarioId>';
SELECT count(*) FROM club_taxonomia.grupo_entrenador     WHERE entrenador_id = '<usuarioId>';
-- todas deben dar 0

-- la lápida SÍ debe existir (impide que un alta rezagada resucite a la persona)
SELECT * FROM club_taxonomia.persona_eliminada WHERE id = '<usuarioId>';

-- club_taxonomia.evento_auditoria: NO se borra, se anonimiza — comprobar que ya no aparece el id
SELECT count(*) FROM club_taxonomia.evento_auditoria
WHERE actor_id = '<usuarioId>' OR sujeto_id = '<usuarioId>';
-- debe dar 0 (si daba > 0 antes del borrado, tras la anonimización actor_id/sujeto_id ya no son el id buscado).
-- Excepción: si el suprimido era ADMIN, sus asientos como actor_id NO se anonimizan por esta vía — ver
-- "Qué NO se borra" más abajo.

-- planificacion: personalizaciones, snapshots y pertenencias a grupo a 0; si era entrenador, también
-- sus planes semanales
SELECT count(*) FROM planificacion.personalizacion      WHERE alumno_id = '<usuarioId>';
SELECT count(*) FROM planificacion.plan_snapshot_alumno  WHERE alumno_id = '<usuarioId>';
SELECT count(*) FROM planificacion.miembro_grupo         WHERE persona_id = '<usuarioId>';
SELECT count(*) FROM planificacion.plan_semanal          WHERE entrenador_id = '<usuarioId>';

-- auditoria: NO se borra, se anonimiza — comprobar que ya no aparece el id, no que la fila desaparezca
SELECT count(*) FROM auditoria.evento
WHERE actor_id = '<usuarioId>' OR sujeto_id = '<usuarioId>';
-- debe dar 0 (si daba > 0 antes del borrado, tras la anonimización actor_id/sujeto_id ya no son el id buscado)

-- idempotencia de cada listener (confirma que procesó el evento de baja, no que reintentará)
SELECT * FROM club_taxonomia.evento_procesado ORDER BY processed_at DESC LIMIT 5;
SELECT * FROM planificacion.evento_procesado  ORDER BY processed_at DESC LIMIT 5;
SELECT * FROM auditoria.evento_procesado      ORDER BY processed_at DESC LIMIT 5;
```

En condiciones normales todo esto ocurre en el mismo `commit` que el `DELETE`, así que las
comprobaciones de arriba pasan casi al instante. Si no pasan, ver la siguiente sección.

### 4. Si la propagación falla

Hoy **no hay alarma ni notificación** de que un evento de baja se quedó sin procesar — el operador
tiene que comprobarlo a mano. Localizar eventos atascados en el outbox compartido:

```sql
SELECT id, listener_id, event_type, publication_date, status, completion_attempts
FROM event_publication
WHERE completion_date IS NULL
  AND publication_date < NOW() - INTERVAL '5 minutes'
ORDER BY publication_date;
```

Filtrar por `event_type IN ('AlumnoEliminado', 'EntrenadorEliminado')` si se busca específicamente la
baja de esta persona (el `serialized_event` contiene el `usuarioId` como `aggregateId`, consultable
con `serialized_event LIKE '%<usuarioId>%'` si hace falta identificar la fila exacta).

**Recuperación real, la única que existe hoy**: redeploy de la aplicación. `application.yml` tiene
`spring.modulith.events.republish-outstanding-events-on-restart: true`, así que al arrancar, Spring
Modulith reentrega todo evento con `completion_date IS NULL`. Los listeners son idempotentes (tabla
`evento_procesado`), así que reentregar es seguro incluso si algún consumidor sí llegó a procesarlo.

```bash
aws apprunner start-deployment --service-arn $APP_RUNNER_ARN_<ENTORNO>
```

Tras el redeploy (~5-10 min), repetir las comprobaciones del paso 3.

> ⚠️ **ADR-0007 D13 describe un endpoint `POST /admin/events/republish` y una política de 5
> reintentos con backoff que NO están implementados.** El esquema real de `event_publication` no
> tiene la columna `last_error` que el ADR menciona — tiene `status`, `completion_attempts` y
> `last_resubmission_date`. Mientras eso no se construya, un redeploy es la única palanca de
> recuperación. Ticket abierto para cerrar esta brecha (ver *Limitaciones conocidas*).

Si el redeploy no resuelve la propagación (el evento sigue con `completion_date IS NULL` tras
reiniciar), es señal de un consumidor genuinamente roto, no un problema transitorio — escalar al
equipo de desarrollo antes de intentar nada manual sobre la tabla `event_publication`.

### 5. Confirmar el cierre al titular

Responder al solicitante confirmando que su cuenta y datos personales han sido suprimidos, dentro del
plazo de D11. Registrar la fecha de cierre junto a la solicitud inicial (mismo registro del
prerrequisito).

## Qué NO se borra (léase al titular si pregunta)

La supresión es de **borrado mixto** (ADR-0014 D6): físico para PII primaria, anonimización para lo
que debe sobrevivir por responsabilidad proactiva. Lo que queda tras un borrado, hoy:

- **`identidad.evento_auditoria`** — anonimizado (paso 3 de arriba): sin `actor_id`/`sujeto_id`, IP
  truncada. Las filas siguen ahí, pero ya no identifican a la persona.
- **`auditoria.evento`** — igual, anonimizado, no borrado (categoría RGPD 2, ADR-0014 D5/D6).
- **`club_taxonomia.evento_auditoria`** — anonimizado igual (`StudentDeletionListener`), con una
  excepción: si el suprimido era **ADMIN**, `DeleteUserCommand` no publica evento de baja para ese
  rol, así que el listener nunca se dispara y su `actor_id` en asientos de clasificación que hizo
  como admin **no** se anonimiza por esta vía. `sujeto_id` (siempre un alumno) y el `actor_id` de un
  entrenador suprimido sí quedan cubiertos. Cerrarlo del todo requiere un evento de baja de ADMIN —
  ticket aparte (ver *Limitaciones conocidas*).
- **Outbox (`event_publication`)** — caduca de forma pasiva: los eventos procesados se compactan a
  los 30 días (ADR-0007 D15, categoría RGPD 4). Si el evento de baja aún no ha caducado, puede
  contener el `usuarioId` en su payload serializado hasta entonces.
- **Backups de RDS** — caducan de forma pasiva (≤ 30 días, categoría RGPD 5). **No se restauran
  selectivamente** para resucitar datos borrados (ADR-0014 D8).
- **Logs operativos** — IPs ya truncadas y `userId` sustituido por un hash determinístico opaco
  (ADR-0011 D9); el hash sigue siendo opaco tras el borrado, no hay nada que anonimizar de nuevo.

## Limitaciones conocidas (con ticket)

- **El `actor_id` de un ADMIN suprimido no se anonimiza** en `club_taxonomia.evento_auditoria` ni en
  `auditoria.evento` — borrar un ADMIN no publica evento de baja, así que los listeners event-driven
  de esos módulos nunca se disparan para él. `sujeto_id` (siempre un alumno) y el `actor_id` de un
  entrenador suprimido sí quedan cubiertos. Ticket:
  [LAL-126](https://linear.app/lalin1982/issue/LAL-126).
- **ADR-0007 D13 no está implementado** — sin endpoint de reproceso manual, sin política de
  reintentos configurada, sin columna `last_error`. Ticket:
  [LAL-125](https://linear.app/lalin1982/issue/LAL-125).

## Rollback

**No existe.** El borrado físico no se deshace, y los backups no se restauran selectivamente para
recuperar una persona suprimida (ADR-0014 D8) — restaurar un snapshot completo para rescatar una fila
reintroduciría datos de todas las demás personas borradas o modificadas desde ese snapshot, que es
peor que el problema que se intenta resolver. Por eso los pasos 1 y 2 (confirmar identidad y datos
antes de ejecutar) son las únicas guardas reales: se aplican *antes*, porque después no hay vuelta
atrás.

## Referencias

- ADR-0014 D6 (borrado mixto), D7 (propagación vía evento), D8 (backups, sin restauración selectiva),
  D11 (plazo), D14 (supresión).
- ADR-0007 D13 (política de fallos del outbox — parcialmente implementada, ver arriba).
- ADR-0009 D17 (anonimización de `auditoria.evento`).
- [`DeleteUserCommand`](../../backend/src/main/kotlin/com/runcriticon/identidad/application/usecases/account/DeleteUserCommand.kt) — caso de uso del paso 2.
- [`UserAdminController`](../../backend/src/main/kotlin/com/runcriticon/identidad/infrastructure/rest/UserAdminController.kt) — endpoint del paso 2.
- [`PersonErasureJdbc`](../../backend/src/main/kotlin/com/runcriticon/clubtaxonomia/infrastructure/persistence/projections/PersonErasureJdbc.kt) — borrado en `club_taxonomia`.
- [`docs/arquitectura/rgpd-en-modulos.md`](../arquitectura/rgpd-en-modulos.md) — patrón de borrado mixto en detalle.
