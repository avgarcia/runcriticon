# RGPD — módulo `club_taxonomia`

Espejo aplicado de ADR-0014. Si hay conflicto, gana el ADR.

## Tablas con datos personales

| Tabla | Categoría | Retención | Borrado al olvido |
|---|---|---|---|
| `club_taxonomia.persona` | 1 — PII primaria | Proyección — vive hasta que llega `AlumnoEliminado`/`EntrenadorEliminado`/`AdminEliminado` | Físico (DELETE), en el mismo commit del listener |
| `club_taxonomia.alumno_tag` | 1 — PII primaria | Igual que `persona` | Físico (DELETE) |
| `club_taxonomia.grupo_alumno_override` | 1 — PII primaria | Igual que `persona` | Físico (DELETE) |
| `club_taxonomia.grupo_entrenador` | 1 — PII primaria | Igual que `persona` | Físico (DELETE) |
| `club_taxonomia.evento_auditoria` | 2 — Auditoría local | Sin purga programada (mismo pendiente que `identidad.evento_auditoria`) | Anonimización (`actor_id`/`sujeto_id` a `NULL`), no borrado |
| `club_taxonomia.persona_eliminada` | Lápida, SIN_PII | 30 días (`ClubTaxonomiaRetentionJob`, LAL-107) | N/A — no contiene datos de la persona, solo el `id` |
| `club_taxonomia.evento_procesado` | SIN_PII | 30 días (`ClubTaxonomiaRetentionJob`, LAL-107) | N/A |
| `club_taxonomia.tag_key`, `tag_value`, `grupo`, `grupo_tag_requerido`, `sugerencia_fusion_grupo` | SIN_PII | Indefinida | N/A |

`persona`, `alumno_tag`, `grupo_alumno_override` y `grupo_entrenador` son **proyecciones**, no la fuente de verdad — la fuente es `identidad.usuario`. Por eso su "retención" no es un plazo fijo como en `identidad`: la fila vive mientras la persona exista en `identidad`, y desaparece en cuanto se procesa el evento de baja.

## Borrado al ejercer el derecho de supresión

`StudentDeletionListener` (`application/listeners/`) escucha `AlumnoEliminado`, `EntrenadorEliminado` y `AdminEliminado` (de `identidad :: events`) y delega en `PersonErasureJdbc.erase()`:

1. Escribe la lápida en `persona_eliminada` (idempotente, `ON CONFLICT DO NOTHING`) — **antes** de borrar, para que una escritura concurrente que esperara el lock encuentre ya la marca.
2. Borra físicamente `persona`, `alumno_tag`, `grupo_alumno_override` y `grupo_entrenador` por `id`/`alumno_id`/`entrenador_id` — **sin filtrar por `club_id`**: la PK ya identifica unívocamente a la persona, y un `club_id` que no cuadrara convertiría el borrado en un no-borrado silencioso.
3. Anonimiza (no borra) `evento_auditoria` vía `ClubTaxonomiaAuditTrailImpl.anonymize()` — `AdminEliminado` solo dispara este paso (un admin nunca se proyecta en `persona`).

Todo en la misma transacción que abre `@ApplicationModuleListener` — la lápida, los cuatro borrados y la anonimización caen juntos o ninguno.

**Sin hueco conocido a día de hoy**: `grupo_alumno_override` y `grupo_entrenador` estaban documentadas como huecos pendientes en su migración de creación (`V202608050001`, `V202608120001`) porque los casos de uso que las escriben (`OverrideGroupMembershipCommand`, `AssignCoachToGroupCommand`) llegaron después — verificado en el código actual: `PersonErasureJdbc.erase()` ya las cubre.

## Eventos que disparan el borrado

| Evento | Origen | Efecto en este módulo |
|---|---|---|
| `AlumnoEliminado` | `identidad :: events` | Borrado físico completo (`persona` + `alumno_tag` + `grupo_alumno_override`) |
| `EntrenadorEliminado` | `identidad :: events` | Borrado físico completo (`persona` + `grupo_entrenador`) |
| `AdminEliminado` (LAL-126) | `identidad :: events` | Solo anonimización de `evento_auditoria` — un admin nunca se proyecta en `persona` |

## Pendientes jurídicos del módulo

Ninguno específico de este módulo. Los pendientes jurídicos de ADR-0014 (base legal, textos, DPIA) son transversales — ver `docs/adr/0014-proteccion-de-datos-rgpd.md`.
