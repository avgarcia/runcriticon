# Runbook — atención de una solicitud de acceso y portabilidad (Arts. 15 y 20 RGPD)

> Invocado por ADR-0014 D12 (acceso y portabilidad: runbook manual + export JSON). No hay export
> self-service: este procedimiento es el único camino. El export contiene **datos de salud** (Art. 9),
> así que generarlo, custodiarlo y entregarlo son pasos explícitos, no algo que el operador improvisa.

## Cuándo se ejecuta

- El titular pide una copia de sus datos (acceso, Art. 15) o recibirlos en formato estructurado para
  llevárselos (portabilidad, Art. 20), por correo a `privacidad@runcriticon.com` (D11) o a través del
  admin de su club.
- El mismo export sirve para los dos derechos: D12 fija un único JSON estructurado.

**Disparadores que retiran este runbook** (ADR-0014 D12, tabla maestra de ADR-0015 "Self-service de
export RGPD"): entra el **segundo club**, o se atienden **más de 5 solicitudes al mes durante dos meses
consecutivos**. Al cumplirse cualquiera, se construye el export self-service y este runbook pasa a
ser el respaldo.

## Quién puede ejecutarlo

- El **responsable del tratamiento** (ADR-0014 D23) o un miembro del equipo con acceso administrativo
  a RDS delegado por él. El admin del club **no** puede ejecutarlo por sí solo: la aplicación no expone
  ningún endpoint de export y la consulta es directa contra la base de datos.
- En producción: acceso con MFA (ADR-0006 D27) y sesión SSM auditada por CloudTrail (ADR-0006 D13).

## Plazo

- **1 mes** desde la solicitud (Art. 12.3, D11), prorrogable a **3 meses** si es compleja o
  numerosa, comunicando la prórroga y su motivo al interesado dentro del primer mes.

## Prerrequisitos

- [ ] La solicitud está registrada (email del titular, fecha, club, derecho ejercido) en
      `<pendiente: ubicación del registro de solicitudes RGPD — fuera del repo, nunca en git porque
      contiene PII>`.
- [ ] Verificación de identidad del solicitante completada (siguiente sección).
- [ ] Se conoce el `usuarioId` (UUID), el rol (`ALUMNO`/`ENTRENADOR`/`ADMIN`) y el `club_id` de la
      persona.
- [ ] Acceso de solo lectura a la base de datos del entorno. **`<pendiente: acceso-rds.md no existe
      todavía>`** — ADR-0006 D13 fija SSM Session Manager + port forwarding desde una EC2 efímera, pero
      ni el runbook ni ese target efímero están en `infrastructure/terraform/`. Hasta que exista, el
      acceso a la BD de un entorno desplegado no tiene procedimiento documentado.
- [ ] `psql` (cliente PostgreSQL 16) en la máquina del operador.

## Verificación de identidad del solicitante

Criterio fijado por ADR-0014 D12, el mismo que usa el runbook de supresión:

1. Confirmación por el **email vigente** de la cuenta (`identidad.usuario.email`).
2. Confirmación **fuera de banda** con el admin del club (llamada, mensaje directo — un canal distinto
   del email de la solicitud).

Sin las dos, no se genera el export. Entregar los datos de salud de una persona a un suplantador es
en sí una brecha (ver [`respuesta-a-brecha.md`](respuesta-a-brecha.md)).

## Procedimiento

### 1. Localizar a la persona y confirmar su rol

```sql
SELECT id, club_id, rol, estado, creado_en
FROM identidad.usuario
WHERE email_normalizado = lower(trim('<email del titular>'));
```

- 0 filas: la cuenta no existe (o ya se suprimió). Se responde al titular que no hay datos asociados a
  ese email — no hay nada que exportar salvo lo que caduca pasivamente (outbox, backups; ver "Qué no
  cubre el export").
- 1 fila: anotar `id` (= `usuarioId`), `club_id` y `rol`.

### 2. Generar el export (transacción de solo lectura)

Guardar el script siguiente como `export-acceso.sql` en un directorio local cifrado del operador (no
en el repo) y ejecutarlo:

```bash
psql "host=<host> port=<puerto> dbname=runcriticon user=<usuario>" \
  -X -q -A -t -v uid='<usuarioId>' \
  -f export-acceso.sql -o export-<usuarioId>.json
```

`-A -t -q` dejan en el fichero solo el documento JSON, sin cabeceras ni etiquetas de comando.

```sql
BEGIN READ ONLY;

SELECT jsonb_pretty(jsonb_build_object(
  'formato', 'runcriticon-export-rgpd-v1',
  'generado_en', now(),
  'usuario_id', :'uid'::uuid,

  -- identidad: cuenta y credenciales (sin hashes: son secretos del sistema, no datos del titular)
  'identidad', jsonb_build_object(
    'usuario', (SELECT to_jsonb(u) - 'password_hash'
                FROM identidad.usuario u WHERE u.id = :'uid'::uuid),
    'invitaciones', (SELECT coalesce(jsonb_agg(to_jsonb(i) - 'token_hash' ORDER BY i.emitida_en), '[]')
                     FROM identidad.invitacion i WHERE i.usuario_id = :'uid'::uuid),
    'magic_links', (SELECT coalesce(jsonb_agg(to_jsonb(m) - 'token_hash' ORDER BY m.emitido_en), '[]')
                    FROM identidad.magic_link m WHERE m.usuario_id = :'uid'::uuid),
    'cambios_de_contrasena', (SELECT coalesce(jsonb_agg(p.creado_en ORDER BY p.creado_en), '[]')
                              FROM identidad.password_historico p WHERE p.usuario_id = :'uid'::uuid),
    'consentimientos', (SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY c.concedido_en), '[]')
                        FROM identidad.consentimiento c WHERE c.usuario_id = :'uid'::uuid),
    -- auditoría local: se omite la identidad del tercero (actor ajeno, o sujeto ajeno si el titular
    -- actuó como admin), y la IP y la metadata cuando no son del titular
    'auditoria_de_cuenta', (SELECT coalesce(jsonb_agg(jsonb_build_object(
        'tipo', e.tipo,
        'ts', e.ts,
        'actor_es_titular', e.actor_id = :'uid'::uuid,
        'sujeto_es_titular', e.sujeto_id = :'uid'::uuid,
        'ip', CASE WHEN e.actor_id = :'uid'::uuid THEN host(e.ip) END,
        'metadata', CASE WHEN e.sujeto_id = :'uid'::uuid OR e.actor_id = :'uid'::uuid AND e.sujeto_id IS NULL
                         THEN e.metadata END
      ) ORDER BY e.ts), '[]')
      FROM identidad.evento_auditoria e
      WHERE e.actor_id = :'uid'::uuid OR e.sujeto_id = :'uid'::uuid)
  ),

  -- club_taxonomia: ficha proyectada, tags, grupos
  'club_taxonomia', jsonb_build_object(
    'persona', (SELECT to_jsonb(p) - 'last_processed_event_id' - 'last_processed_event_ts'
                FROM club_taxonomia.persona p WHERE p.id = :'uid'::uuid),
    'tags', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                 'etiqueta', tk.nombre, 'valor', tv.nombre, 'asignado_en', at.creado_en)), '[]')
             FROM club_taxonomia.alumno_tag at
             JOIN club_taxonomia.tag_value tv ON tv.id = at.tag_value_id
             JOIN club_taxonomia.tag_key tk ON tk.id = tv.tag_key_id
             WHERE at.alumno_id = :'uid'::uuid),
    'grupos_ajuste_manual', (SELECT coalesce(jsonb_agg(jsonb_build_object(
                 'grupo', g.nombre, 'incluido', o.incluido)), '[]')
             FROM club_taxonomia.grupo_alumno_override o
             JOIN club_taxonomia.grupo g ON g.id = o.grupo_id
             WHERE o.alumno_id = :'uid'::uuid),
    'grupos_como_entrenador', (SELECT coalesce(jsonb_agg(g.nombre), '[]')
             FROM club_taxonomia.grupo_entrenador ge
             JOIN club_taxonomia.grupo g ON g.id = ge.grupo_id
             WHERE ge.entrenador_id = :'uid'::uuid),
    'auditoria_de_clasificacion', (SELECT coalesce(jsonb_agg(jsonb_build_object(
        'tipo', e.tipo, 'ts', e.ts,
        'sujeto_es_titular', e.sujeto_id = :'uid'::uuid,
        'metadata', CASE WHEN e.sujeto_id = :'uid'::uuid THEN e.metadata END
      ) ORDER BY e.ts), '[]')
      FROM club_taxonomia.evento_auditoria e
      WHERE e.sujeto_id = :'uid'::uuid OR e.actor_id = :'uid'::uuid)
  ),

  -- planificacion: personalizaciones del alumno y planes del entrenador
  'planificacion', jsonb_build_object(
    'personalizaciones', (SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY p.creado_en), '[]')
                          FROM planificacion.personalizacion p WHERE p.alumno_id = :'uid'::uuid),
    'planes_congelados', (SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY s.congelado_en), '[]')
                          FROM planificacion.plan_snapshot_alumno s WHERE s.alumno_id = :'uid'::uuid),
    'pertenencia_a_grupos', (SELECT coalesce(jsonb_agg(to_jsonb(m) - 'last_processed_event_id'
                                                                - 'last_processed_event_ts'), '[]')
                             FROM planificacion.miembro_grupo m WHERE m.persona_id = :'uid'::uuid),
    'planes_como_entrenador', (SELECT coalesce(jsonb_agg(to_jsonb(ps) ORDER BY ps.semana), '[]')
                               FROM planificacion.plan_semanal ps WHERE ps.entrenador_id = :'uid'::uuid)
  ),

  -- seguimiento: datos de salud (Art. 9)
  'seguimiento', jsonb_build_object(
    'plan_recibido', (SELECT coalesce(jsonb_agg(to_jsonb(r) - 'last_processed_event_id'
                                                     - 'last_processed_event_ts' ORDER BY r.dia), '[]')
                      FROM seguimiento.plan_resuelto_por_alumno r WHERE r.alumno_id = :'uid'::uuid),
    'reportes_de_sesion', (SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY r.dia), '[]')
                           FROM seguimiento.reporte_sesion r WHERE r.alumno_id = :'uid'::uuid),
    'reajustes', (SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY r.dia), '[]')
                  FROM seguimiento.reajuste_dia r WHERE r.alumno_id = :'uid'::uuid),
    'marcas', (SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.distancia), '[]')
               FROM seguimiento.marca_alumno m WHERE m.alumno_id = :'uid'::uuid),
    'consentimiento_proyectado', (SELECT to_jsonb(c) - 'last_processed_event_id' - 'last_processed_event_ts'
                                  FROM seguimiento.consentimiento_alumno c WHERE c.alumno_id = :'uid'::uuid),
    'grupos_como_entrenador', (SELECT coalesce(jsonb_agg(g.grupo_id), '[]')
                               FROM seguimiento.grupo_entrenador g WHERE g.entrenador_id = :'uid'::uuid)
  ),

  -- auditoria (ADR-0009 D17): quién accedió a datos sensibles del titular, sin identificar al actor
  'accesos_registrados', (SELECT coalesce(jsonb_agg(jsonb_build_object(
      'tipo', a.tipo, 'recurso', a.recurso, 'ts', a.ts,
      'actor_es_titular', a.actor_id = :'uid'::uuid,
      'sujeto_es_titular', a.sujeto_id = :'uid'::uuid
    ) ORDER BY a.ts), '[]')
    FROM auditoria.evento a
    WHERE a.sujeto_id = :'uid'::uuid OR a.actor_id = :'uid'::uuid)
));

ROLLBACK;
```

Notas sobre el script:

- **Solo lectura por construcción**: `BEGIN READ ONLY` hace que cualquier escritura falle; el
  `ROLLBACK` final no deshace nada, solo cierra la transacción.
- **Cobertura verificada contra las migraciones Flyway** (`backend/src/main/resources/db/migration/`)
  y los `RGPD.md` de cada módulo. Cubre los tres roles: las secciones que no aplican a un rol salen
  como `[]` o `null`, no hay que editar el script por rol.
- **Qué se omite a propósito**: `password_hash`, `token_hash` y los hashes del histórico de contraseñas
  (son secretos del sistema, no datos del titular — de `password_historico` solo se exporta la fecha
  de cada cambio); columnas técnicas de proyección (`last_processed_event_*`); y la **identidad de
  terceros** en toda fila de auditoría (D12: "auditoría asociada anonimizada de terceros").
- **Tabla nueva = script desactualizado**: si una PR añade una tabla con `@RgpdCategory(PII_PRIMARIA)`,
  este script debe ampliarse en la misma PR. No hay test que lo vigile — es convención de revisión.

### 3. Revisar el export antes de entregarlo

- [ ] El fichero es JSON válido y el `usuario_id` coincide con el de la solicitud.
- [ ] Revisar a mano los campos `metadata` de `auditoria_de_cuenta` y `auditoria_de_clasificacion`:
      su contenido varía por `tipo` y el script no puede garantizar que ninguna clave identifique a un
      tercero. Si alguna lo hace, eliminarla del fichero antes de entregar.
- [ ] Los campos `notas`, `mensaje`, `mensaje_al_alumno` y `descripcion_dolor` son texto libre: pueden
      mencionar a terceros por nombre. Se entregan (son datos del titular), pero si mencionan a otra
      persona de forma que revele datos de salud de ella, consultar con el responsable antes de entregar.

### 4. Entregar al titular

- **No por email**: el export contiene datos de salud y la premisa de ADR-0005 D12 prohíbe enviar datos
  de salud por Postmark. Canal de entrega: **`<pendiente: canal seguro de entrega del export (p. ej.
  enlace cifrado con caducidad o entrega en mano) — decisión del responsable del tratamiento>`**.
- Acompañar el JSON de la descripción de su estructura (sección "Estructura del export" de abajo),
  que es lo que D12 llama "esquema documentado".

### 5. Destruir la copia local y cerrar

- Borrar `export-<usuarioId>.json` y `export-acceso.sql` de la máquina del operador tras la entrega.
- Responder al titular confirmando la entrega dentro del plazo de D11.
- Registrar fecha de cierre, operador y canal de entrega junto a la solicitud (mismo registro del
  prerrequisito).

## Estructura del export (`runcriticon-export-rgpd-v1`)

| Clave | Contenido |
|---|---|
| `identidad.usuario` | Cuenta: nombre, email, rol, estado, fechas de alta y modificación |
| `identidad.invitaciones`, `identidad.magic_links` | Fechas de emisión, caducidad y uso de invitaciones y enlaces de acceso |
| `identidad.cambios_de_contrasena` | Fechas de cada cambio de contraseña |
| `identidad.consentimientos` | Cada concesión/revocación del consentimiento de datos de salud (versión del texto, IP, navegador) |
| `identidad.auditoria_de_cuenta` | Eventos de seguridad de la cuenta (accesos, cambios, bloqueos) |
| `club_taxonomia.*` | Ficha en el club, etiquetas asignadas, grupos (ajustes manuales y como entrenador), historial de cambios de etiquetas |
| `planificacion.*` | Personalizaciones del plan, planes congelados, pertenencia a grupos; si es entrenador, sus planes semanales |
| `seguimiento.*` | Plan recibido día a día, reportes de sesión, reajustes, marcas personales, estado del consentimiento |
| `accesos_registrados` | Accesos a datos sensibles del titular y denegaciones de acceso, sin identificar a quién accedió |

## Qué no cubre el export (léase al titular si pregunta)

- **Outbox (`event_publication`)** — payloads técnicos de eventos que caducan a los 30 días (ADR-0007
  D15, categoría RGPD 4). Replican datos ya incluidos en el export.
- **Backups de RDS** — copia de la misma base de datos, caducan a los 30 días (categoría 5).
- **Logs operativos** — el `userId` va hasheado (ADR-0011 D9) e IP truncada: no son atribuibles al
  titular por consulta directa.
- **Sesiones activas** (`spring_session`) — datos técnicos de la sesión en curso, no exportables de
  forma útil.

## Limitaciones conocidas

- **Sin acceso documentado a la BD de un entorno desplegado** hasta que exista `acceso-rds.md`
  (ADR-0006 D13).
- **Las lecturas del operador no dejan asiento de auditoría**: el aspecto `@AuditAccess` solo actúa en
  los casos de uso de la aplicación, no en una consulta directa. CloudTrail registra la sesión SSM
  (quién y cuándo), no qué filas se leyeron — el registro de la solicitud es la única trazabilidad del
  contenido.
- **Sin export self-service** — aplazado conscientemente con disparador (ADR-0014 D12, ADR-0015).

## Rollback

**No aplica**: el procedimiento es de solo lectura (`BEGIN READ ONLY`). Si el export se entrega a quien
no debía, no es un rollback sino una brecha: [`respuesta-a-brecha.md`](respuesta-a-brecha.md).

## Referencias

- ADR-0014 D5 (categorías), D11 (plazo), D12 (acceso y portabilidad), D23 (responsable).
- ADR-0006 D13 (acceso administrativo a RDS), ADR-0009 D17 (auditoría de autorización).
- `RGPD.md` de cada módulo (`backend/src/main/kotlin/com/runcriticon/{modulo}/RGPD.md`) — inventario de
  tablas con datos personales.
- [`derechos-rgpd-supresion.md`](derechos-rgpd-supresion.md) — mismo criterio de verificación de
  identidad.
