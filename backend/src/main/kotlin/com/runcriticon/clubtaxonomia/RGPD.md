# RGPD — módulo `club_taxonomia`

Espejo aplicado de ADR-0014. Si hay conflicto, gana el ADR.

## Tablas con datos personales

| Tabla | Categoría | Retención | Borrado al olvido |
|---|---|---|---|
| `club_taxonomia.persona` | 1 — PII primaria | Proyección — vive hasta que llega `AlumnoEliminado`/`EntrenadorEliminado`/`AdminEliminado` | Físico (DELETE), en el mismo commit del listener |
| `club_taxonomia.alumno_tag` | 1 — PII primaria | Igual que `persona` | Físico (DELETE) |
| `club_taxonomia.grupo_alumno_override` | 1 — PII primaria | Igual que `persona` | Físico (DELETE) |
| `club_taxonomia.grupo_entrenador` | 1 — PII primaria | Igual que `persona` | Físico (DELETE) |
| `club_taxonomia.evento_auditoria` | 2 — Auditoría local | 12 meses (`ClubTaxonomiaRetentionJob`) | Anonimización (`actor_id`/`sujeto_id` a `NULL`), no borrado |
| `club_taxonomia.persona_eliminada` | Lápida, SIN_PII | 30 días (`ClubTaxonomiaRetentionJob`) | N/A — no contiene datos de la persona, solo el `id` |
| `club_taxonomia.evento_procesado` | SIN_PII | 30 días (`ClubTaxonomiaRetentionJob`) | N/A |
| `club_taxonomia.consentimiento_alumno` | SIN_PII (solo el booleano `vigente`) | Igual que `persona` | Físico (DELETE), con el resto de la proyección |
| `club_taxonomia.tag_key`, `tag_value`, `grupo`, `grupo_tag_requerido`, `sugerencia_fusion_grupo` | SIN_PII | Indefinida | N/A |

`persona`, `alumno_tag`, `grupo_alumno_override` y `grupo_entrenador` son **proyecciones**, no la fuente de verdad — la fuente es `identidad.usuario`. Por eso su "retención" no es un plazo fijo como en `identidad`: la fila vive mientras la persona exista en `identidad`, y desaparece en cuanto se procesa el evento de baja.

## Dato de salud: el tag `estado = lesión`

Un valor de `alumno_tag` es dato de salud (art. 9): `lesión` del eje `estado` (`InjuryAxis`), que asigna `LesionDeclaradaListener` cuando el alumno avisa de lesión, o un entrenador a mano. Toda lectura que lo expone lleva `@AuditAccess(SALUD)` (ADR-0009 D15) y registra solo a los alumnos que quedan expuestos como lesionados:

| Lectura | Sujetos auditados |
|---|---|
| `ListStudentsQuery` (`GET /api/alumnos`) | Los alumnos devueltos que llevan el tag |
| `ListStudentTagsQuery` (`GET /api/alumnos/{id}/tags`) | El alumno, si lo lleva |
| `GetGroupDetailQuery` (`GET /api/grupos/{id}`) | Si el filtro del grupo lo exige: miembros por filtro y excluidos a mano que lo cumplen |
| `PreviewGroupMembersQuery` (`GET /api/grupos/miembros`) | Si el filtro lo exige: todos los devueltos |

Las lecturas que solo devuelven recuentos (`ListGroupsQuery`, `ListCoachWorkloadQuery`, `GetTagValueArchiveImpactQuery`) no identifican a nadie y no se auditan. El entrenador sigue viendo el tag en todo el club, no solo en sus grupos: es una decisión aceptada que no cambia con la auditoría.

**Consentimiento** (ADR-0014 D16/D18): la única base legal de este dato es el consentimiento explícito del alumno, que el módulo proyecta en `consentimiento_alumno` (`HealthConsentListener`, desde `ConsentimientoConcedido`/`ConsentimientoRevocado`).

- **Asignar** el tag a un alumno sin consentimiento vigente se rechaza (409 `ALUMNO_SIN_CONSENTIMIENTO_SALUD`) por las tres vías del entrenador —asignar, reemplazar, en masa— y también desde `LesionDeclaradaListener`. Sin fila en la proyección cuenta como no consentido (fail-closed).
- **Revocar** retira el tag: el tratamiento dependiente cesa, y la pertenencia a un grupo filtrado por lesión también lo delataría, así que se quita en vez de ocultarse en lectura. Pasa por `classify`, que recalcula los grupos y deja el asiento `before`/`after`. Al volver a conceder, el tag **no** reaparece: hay que reasignarlo.
- Los planes ya publicados conservan su snapshot de alumnos (ADR-0002 D5): retirar el tag no saca al alumno de un plan publicado a un grupo filtrado por lesión.

**Límite conocido**: el tag se reconoce por su nombre. Si un admin renombra el eje o el valor, estas lecturas dejan de auditarse, la guarda deja de exigir consentimiento y la revocación deja de retirarlo, todo sin avisar.

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
| `AdminEliminado` | `identidad :: events` | Solo anonimización de `evento_auditoria` — un admin nunca se proyecta en `persona` |

## Pendientes jurídicos del módulo

Ninguno específico de este módulo. Los pendientes jurídicos de ADR-0014 (base legal, textos, DPIA) son transversales — ver `docs/adr/0014-proteccion-de-datos-rgpd.md`.
