# Plan de implementación del MVP — Runcriticon

> Documento vivo. Recoge **cómo se construye** el MVP: el orden, las fases y los hitos. **No fija fechas** — no hay fecha objetivo; el plan avanza por hitos y el equipo estima la cadencia al ejecutarlo. Se apoya en las decisiones ya tomadas: los **16 ADR aceptados** (`docs/adr/`), el `backlog.md` y el discovery.

## Punto de partida

- Proyecto **greenfield**: el repositorio contiene toda la documentación (discovery, wireframes, 16 ADR, planes de formación, glosario, guía de estructura de módulo) y **ningún código de aplicación**.
- Las **decisiones de arquitectura están tomadas y aprobadas** — ADR-0001 a ADR-0016, todos en estado **Aceptado** tras la revisión Nivel 1 (mayo 2026).
- El **alcance del MVP** son las funcionalidades MUST del `backlog.md`, para un único club.
- **Equipo**: 4 personas, constituido y disponible. **Sin fecha objetivo** — el plan avanza por **hitos**.

## Principios de construcción

Decididos con negocio / jefe de equipo; gobiernan todo el plan:

1. **Esqueleto andante primero.** La primera meta no es una funcionalidad, sino una rebanada de extremo a extremo que se despliega sola. Desriesga la infraestructura y la integración desde el día 1.
2. **Camino crítico primero (*journey-first*).** Tras el esqueleto, se construye el *loop* entrenador↔alumno de punta a punta —lo mínimo de cada módulo para ese ciclo— antes de rellenar lo secundario.
3. **Kanban.** Flujo continuo con límites de WIP, sin *sprints*. **Demo quincenal con los usuarios finales** del club piloto, que dan feedback y validan si el flujo encaja en su día a día.
4. **El *loop* central es intocable.** Válvula de escape si hay que aligerar: lo secundario de cada módulo; lo primero aplazable es el panel de alertas y la vista de salud del club.
5. **La arquitectura no se renegocia sobre la marcha.** Los ADR son la referencia; la guía [`docs/arquitectura/estructura-de-un-modulo.md`](arquitectura/estructura-de-un-modulo.md) fija cómo se construye cada módulo.

## Prerrequisitos

Antes de la primera línea de código de funcionalidad:

- **Revisar el corpus de ADRs aceptados** (16): el equipo, ya constituido, lee los ADR y la guía [`docs/arquitectura/estructura-de-un-modulo.md`](arquitectura/estructura-de-un-modulo.md). Los ADR ya están en estado **Aceptado** tras la revisión Nivel 1 — esta revisión es de lectura y onboarding, no de aprobación.
- **Aprovisionar la infraestructura** con Terraform (ADR-0006): cuenta AWS, región `eu-west-1` (ADR-0006 D1, ADR-0014 D1), RDS PostgreSQL (ADR-0006 D7), App Runner (ADR-0006 D3), entornos `staging` y `producción` (ADR-0006 D20), backend de Terraform en S3 + DynamoDB (ADR-0006 D19), convención de tagging (ADR-0006 D25), alertas de facturación (ADR-0006 D26).
- **Formación en paralelo**: el equipo arranca los planes de `docs/formacion/` mientras construye — continuo, no bloqueante.
- Los **pendientes jurídicos de RGPD** (ADR-0014 — base legal, textos de consentimiento, firma de DPA con AWS/Postmark/GitHub, constitución de Runcriticon S.L., validación de DPIA y análisis sin DPO) no bloquean empezar a programar, pero **sí deben estar cerrados antes de la beta** con datos reales.

## Fases

### Fase 0 — Esqueleto andante

La rebanada de extremo a extremo, sin funcionalidades de negocio:

- Monorepo (ADR-0001); *build* de backend (Kotlin/Spring Boot) y frontend (Angular).
- *Pipeline* de CI/CD (ADR-0010): *quality gates*, imagen a GHCR, despliegue automático a `staging`.
- Infraestructura como código (Terraform) desplegada.
- Los **4 módulos vacíos** montados con Spring Modulith; estructura hexagonal según la guía de módulo.
- Primera migración Flyway; un esquema por módulo (ADR-0004).
- **Login mínimo** funcionando (sienta las bases de ADR-0003) y una pantalla trivial.

**En paralelo** — Claude produce el **diseño visual**: *mockups* hi-fi en HTML/CSS con estilo Material (ADR-0012) de las pantallas del camino crítico, que alimentan el frontend.

**Hito H0:** un *commit* llega solo a `staging`, se puede iniciar sesión y se ve una pantalla. El camino del *commit* al despliegue queda probado.

### Fase 1 — Camino crítico (el *loop* entrenador↔alumno)

Lo **mínimo** de cada módulo para que funcione el ciclo completo:

- **Identidad y acceso**: invitaciones, activación, magic link, roles; el primer admin por semilla (ADR-0003).
- **Club y taxonomía**: el club, el editor de taxonomía (TagKey/TagValue), alta de alumnos y entrenadores, grupos como consulta sobre tags (ADR-0002); alta de alumnos por delegación a entrenadores.
- **Planificación**: editor de plan semanal, sesiones, **personalización por alumno** (M12 — ver nota más abajo), publicación a un grupo con *snapshot* de membresía.
- **Seguimiento**: el alumno ve su plan resuelto (incluida su personalización, si la tiene) y **reporta una sesión**.

Cada funcionalidad pasa los *quality gates* de ADR-0010. Comunicación entre módulos *events-first* desde el primer evento.

#### Nota técnica — la personalización (M12) es ciudadano de primera

La M12 no es un "*nice to have*" que se anexa al final: define el modelo del módulo Planificación desde el día 1. Cuando se programe, los siguientes elementos van **a la par** del agregado `PlanSemanal`, no en una fase posterior.

**Modelo de dominio (módulo Planificación)** — `Personalizacion` es entidad **hija** del agregado `PlanSemanal`, no una tabla suelta:

```
PlanSemanal (raíz del agregado)
  ├─ Sesion (entidad)            ← sesión base que ve todo el grupo
  └─ Personalizacion (entidad)
       ├─ alumnoId
       ├─ sesionId               ← apunta a la sesión sobrescrita
       ├─ override: Sesion       ← misma forma que Sesion, pero sólo para ese alumno
       └─ mensajeAlAlumno: String?
```

Invariantes que protege la raíz: una personalización única por `(plan, sesion, alumno)`; el alumno debe estar en el grupo (o, tras publicar, en el snapshot); resolver la sesión que ve un alumno es una **función pura** del agregado: `resolverSesionParaAlumno(plan, dia, alumno)` devuelve el override si existe, la sesión base si no.

**Persistencia (schema `planificacion`)** — tabla aparte para soportar consultas tipo *"personalizaciones de este alumno"*, *"sesiones personalizadas de este plan"* y, más adelante, métricas en la salud del club:

```
planificacion.personalizacion (
  id,
  plan_id, sesion_id, alumno_id,
  override JSONB,            -- mismo shape que Sesion
  mensaje_al_alumno TEXT,    -- opcional, visible al alumno
  creado_en, modificado_en,
  UNIQUE (plan_id, sesion_id, alumno_id)
)
```

**Eventos de dominio** — Planificación emite (vía outbox de Spring Modulith, ADR-0007):

- `PlanPublicado(planId, grupoId, snapshotAlumnos[], sesiones[])`
- `SesionPersonalizada(planId, sesionId, alumnoId, override, mensajeAlAlumno?)`
- `PersonalizacionRetirada(planId, sesionId, alumnoId)`

**Read model en Seguimiento** — la vista "hoy" del alumno (spec 06) **no resuelve nada en tiempo de petición**. Lee de una proyección local en el módulo Seguimiento, alimentada por los tres eventos anteriores:

```
seguimiento.plan_resuelto_por_alumno (
  alumno_id, plan_id, dia,
  sesion_resuelta JSONB,     -- override si lo hay, base si no
  es_personalizada BOOL,     -- uso interno (alertas, métricas), NO se muestra al alumno
  mensaje_al_alumno TEXT     -- el único elemento visible que delata la personalización
)
```

**Consistencia eventual**: cuando el entrenador añade una personalización a un plan ya publicado, hay un *lag* (segundos) hasta que el alumno la ve refrescando. Aceptable; el toast del entrenador lo refleja (*"Personalización guardada. Marta la verá al refrescar."*).

**Casos borde a soportar desde el primer corte**:

- Personalizar antes de publicar — no emite eventos hacia Seguimiento (no hay snapshot).
- Personalizar después de publicar — sólo si el alumno está en el snapshot.
- Editar la sesión base de un plan publicado que tenía personalizaciones — las personalizaciones se mantienen tal cual; aviso al entrenador.
- Sacar al alumno del grupo después de publicar — el snapshot lo mantiene; sus personalizaciones siguen vigentes hasta el final de la semana.

Referencias: glosario (`docs/glosario.md`), specs 05 y 06, mockups `docs/diseno/editor-sesion.html` y `docs/diseno/modal-personalizaciones.html`.

#### Nota técnica — ritmos relativos a marcas (M19 + M20) son MUST

Decidido el 2026-05-27: la H5 entra al MVP. El modelo de `Ritmo` y la entidad `MarcaAlumno` están definidos en **ADR-0002**; aquí se anota qué implica programarlo en Fase 1, en línea con la del bloque anterior.

**Privacidad fuerte como invariante de diseño**: las marcas del alumno **solo las ve y edita el alumno**. Ni el entrenador ni el admin tienen acceso — ni a valores, ni a contadores agregados ("cuántos alumnos no tienen marca de 10K"). Esto se traduce en barreras de módulo, autorización en los casos de uso y ausencia de cualquier listado lateral en las pantallas de entrenador/admin.

**Modelo de dominio** — `MarcaAlumno` es agregado pequeño en el módulo **Seguimiento**:

```
MarcaAlumno
  ├─ alumnoId
  ├─ distancia     ∈ {5K, 10K, 21K, 42K}
  └─ tiempoSegundos
PK (alumnoId, distancia)  -- una marca por distancia, sin histórico en MVP
```

Y el `Ritmo` del módulo **Planificación** pasa de `pct_*` a:

```
sealed Ritmo:
  Absoluto(segPorKm: Int)
  Relativo(referencia: Distancia, deltaSegPorKm: Int)   -- delta firmado
```

Las constraints en `planificacion.sesion` están en ADR-0002.

**Eventos** que añade Seguimiento:

- `MarcaActualizada(alumnoId, distancia, tiempoSegundos)` — emitido por el agregado al crear/modificar. Lo consume **el propio módulo Seguimiento** en otro listener para recalcular las filas del read model donde `ritmo_referencia_distancia = distancia` y `alumno_id = ese alumno`.
- `MarcaRetirada(alumnoId, distancia)` — el alumno borra una marca; el read model vuelve a estado "no resuelto" para esas filas.

**Read model enriquecido** — `seguimiento.plan_resuelto_por_alumno` añade columnas:

```
  ritmo_tipo_origen           ABSOLUTO | RELATIVO    -- copiado de la sesión resuelta
  ritmo_calculado_seg_por_km  INT NULL               -- el valor que verá el alumno
  ritmo_referencia_distancia  TEXT NULL              -- contexto sutil para el alumno
  ritmo_falta_marca           TEXT NULL              -- '10K' si el alumno la necesita
```

**Resolución**:

- Si `Ritmo` de la sesión resuelta es `Absoluto` → `ritmo_calculado_seg_por_km = segPorKm`; las columnas de referencia quedan nulas.
- Si es `Relativo` y el alumno tiene la marca → `ritmo_calculado_seg_por_km = marca.tiempoEnSegPorKm + deltaSegPorKm`. Se copia `ritmo_referencia_distancia` para mostrar el contexto al alumno.
- Si es `Relativo` y **no** tiene la marca → `ritmo_calculado_seg_por_km = NULL`, `ritmo_falta_marca = referencia`. El frontend del alumno muestra empty state con CTA *"Añade tu marca de [distancia]"*. El frontend del entrenador no muestra nada distinto: él solo conoce *qué* pidió.

**Casos borde a soportar desde el primer corte**:

- Alumno cambia su marca tras publicarse el plan — recálculo automático vía `MarcaActualizada`; lag de segundos hasta que lo ve.
- Alumno no tiene marca de la referencia — sesión visible al alumno, sin ritmo concreto, con CTA para rellenar.
- Entrenador edita la sesión cambiando de absoluto a relativo (o viceversa) en un plan publicado — se vuelve a procesar como `PlanPublicado` para recalcular el read model.
- Personalización con ritmo relativo distinto (ej. la sesión base usa 10K + 10s/km y para Marta personaliza con 10K + 20s/km) — se almacena en el override de la personalización con el mismo modelo.

Referencias: ADR-0002 (modelo de datos), spec 10 (marcas del alumno), specs 05 y 06 actualizadas, mockups `docs/diseno/mis-marcas.html`, `docs/diseno/editor-sesion.html` y `docs/diseno/vista-hoy-alumno.html`.

**Hito H1 — arranque de la BETA:** el *loop* crear plan → publicar → ejecutar → reportar funciona de extremo a extremo en `producción` y pasa un *smoke test*. **El club piloto empieza a usar Runcriticon de verdad**; las funcionalidades siguientes llegan con el club ya dentro.

### Fase 2 — Rellenar (funcionalidades secundarias)

Con el club piloto ya usando la app, se completan las funcionalidades secundarias de cada módulo en flujo continuo (Kanban), priorizadas con el feedback de las demos quincenales. Aquí entran, entre otras, el panel de alertas y la vista de salud del club — que son también la **válvula de escape** si hay que aligerar. Claude produce por delante el diseño de las pantallas nuevas.

#### Tareas técnicas pendientes de análisis (entran como tickets en Fase 2)

- **Logout de todos los dispositivos por el propio usuario** (sin pasar por admin). Aplazado en ADR-0003 D11. Hay que analizar alternativas: (a) pantalla en el perfil con botón explícito "Cerrar sesión en todos los dispositivos"; (b) acción automática tras detectar comportamiento sospechoso; (c) integrar con un futuro panel de "sesiones activas" que liste los dispositivos. El workaround actual (cambiar contraseña → invalida todas las sesiones) cubre temporalmente el caso "sospecho que me han robado la contraseña".

### Fase 3 — Endurecimiento y consolidación

Transversal, se intensifica hacia el final:

- Tests de **carga/rendimiento** para validar los NFR de ADR-0001 (p95 de latencia).
- **DAST** de seguridad y revisión de accesibilidad.
- Observabilidad afinada con datos reales (ADR-0011).
- Cierre confirmado de los pendientes jurídicos de RGPD.

## Hitos

| Hito | Significado |
|------|-------------|
| **H0 — Esqueleto andante** | Un *commit* se despliega solo a `staging`; login y una pantalla. |
| **H1 — Beta** | El *loop* entrenador↔alumno funciona en `producción`; el club piloto empieza a usarlo. |
| **H2 — MVP completo** | Todas las funcionalidades MUST del `backlog.md` entregadas. |
| **H3 — Consolidación** | NFR validados, seguridad y accesibilidad revisadas, RGPD cerrado. |

> El plan fija el **orden y los hitos**, no fechas. La estimación en calendario, si se necesita, la hace el equipo al ejecutar.

## Seguimiento y feedback

- **Kanban** con límites de WIP; tablero visible.
- **Demo quincenal con los usuarios finales** del club piloto: dan feedback y, según se añaden funcionalidades, trabajan con la app para comprobar si el flujo encaja en su día a día.
- El feedback de las demos prioriza la cola de la Fase 2.

## Criterio de éxito de la beta

El éxito de la beta se mide por el **grado de adopción real**: que entrenadores y alumnos usen Runcriticon **en su día a día**, en lugar de las herramientas que usaban antes (Excel, WhatsApp…). Una adopción real significa que la aplicación está bien construida y que los usuarios la encuentran útil — valida la hipótesis central del discovery.

## Lo que NO entra en el MVP

Recordatorio (ver `backlog.md` y ADR-0015): app móvil nativa, login con Google, importación masiva de alumnos, ritmos relativos en la UI, versionado de API, caché, i18n. Son evoluciones posteriores, no olvidos.

## Riesgos de la implementación

- **Equipo nuevo en un stack con curva** (hexagonal, DDD, events-first) → planes de formación en paralelo, la guía de estructura de módulo como referencia, y empezar por el esqueleto antes que por funcionalidades.
- **Consistencia eventual mal gestionada** (events-first) → un módulo de ejemplo bien hecho como patrón, tests de las proyecciones, consumidores idempotentes.
- **Alcance que se infla** → la válvula de escape del principio 4; el *journey-first* permite recortar lo secundario sin tocar el *loop*.
- **La beta depende del email** (R10) y de los pendientes de RGPD → cerrar ambos antes del Hito H1.
