# Auditoría de la estrategia de testing — septiembre 2026

> Foto de la cobertura de tests de `origin/main` (`8a4b6c0`, 2026-09-22) contrastada con [`testing-de-modulos.md`](testing-de-modulos.md), **ADR-0009 D14**, **ADR-0010 D7/D8/D9/D13/D21**, **ADR-0012** y **ADR-0014 D6**. Si hay conflicto, gana el ADR.
>
> **Método**: lectura estática del código de test y de producción, más la API de GitHub para la protección de rama. **No se han ejecutado los tests.** Todo hallazgo que ya estaba identificado en la auditoría de arquitectura de 2026-09 se marca como **pendiente** y no se vuelve a describir. **Este documento aporta los huecos marcados como «nuevo».**

## 1. Resumen

1. **Ningún quality gate bloquea un merge (incumple ADR-0010 D20).** `main` no tiene branch protection (la API devuelve 404) y el único ruleset (`main-branch`) está en `enforcement: disabled`. Toda la pirámide de ADR-0010 D7 es informativa. Es el hueco de mayor apalancamiento: los demás arreglos no sirven de nada si un PR en rojo entra igual.
2. **ADR-0009 D14 solo se cumple en `planificacion`.** En `clubtaxonomia` e `identidad`, los «tests de acceso cruzado» comprueban que se propaga el `clubId` del actor, o que un id **inexistente** da `NotFound`. Ninguno siembra un objeto real de otro club o de otro entrenador. Ninguno pasa por el aspecto `@AuthScope` real.
3. **Faltan los artefactos de soporte de la guía**: builders/Object Mother (§8), dataset de staging (§9), catálogo de tests críticos por módulo (§10) y helpers `TestPrincipals`/`TestClubs`. Sin ellos, el patrón canónico de §5 no se puede escribir barato, y esa es la causa raíz del punto 2.
4. **Listeners**: `CoachGroupProjectionListener` no tiene ningún test. Otros tres solo tienen tests unitarios con dobles, lo que explica que no se detectara que `PersonalizationProjectionListener` quema la clave de idempotencia antes de confirmar la escritura. Ocho no verifican el MDC.
5. **Flakiness estructural**: 15 esperas fijas (`SETTLE_MILLIS`) para afirmar que algo *no* ocurre, y un test que no puede fallar. Spring Modulith `Scenario` y Awaitility ya están en el classpath y no se usan.
6. **Frontend**: la cobertura unitaria es buena (57/89 artefactos, 56 de ellos de comportamiento). El bucle crítico entrenador → alumno (crear y publicar plan) no tiene e2e, y los e2e nunca tocan el backend.

## 2. Objetivos de cobertura

La guía §11 dice «sin objetivo numérico ciego», y ADR-0010 D13 aplaza los umbrales de Kover a ADR-0015. Por eso los objetivos son **de inventario, no de porcentaje**:

| Objetivo | Métrica verificable |
|---|---|
| Acceso cruzado | 100 % de los `@ApplicationService` en alcance de nivel de objeto con un test que **siembra el objeto ajeno real** y afirma `Forbidden` (o lista vacía en listados) |
| Listeners | 100 % de los `@ApplicationModuleListener` con test de integración + reentrega (mismo `event_id` → efecto único) + MDC restaurado y limpio |
| RGPD | Cada tabla que borra o anonimiza un `*DeletionListener` tiene una aserción, para cada evento de baja que consume (`Alumno`/`Entrenador`/`AdminEliminado`) |
| Contratos | 1 test por integration event (hoy 22/22 ✓) + payloads de versiones anteriores (ADR-0010 D8) |
| Catálogo crítico | 100 % de los casos del `README.md` de tests de cada módulo existen y están en verde |
| Frontend | Cada guard, interceptor y servicio con efecto (POST/PUT/DELETE) con spec de comportamiento; cada journey crítico de D6 con e2e + axe |

## 3. Huecos priorizados

Prioridad = riesgo en producción: **P0** gates y seguridad (IDOR) · **P1** RGPD e integridad de eventos · **P2** infraestructura de test y deuda que provoca flakiness · **P3** resto.

### P0 — Gates y autorización

| # | Hueco | Tipo de test / acción | Caso de ejemplo | Estado |
|---|---|---|---|---|
| P0-1 | `main` sin branch protection; ruleset `main-branch` desactivado. Ningún job de CI es *required*. Incumple ADR-0010 D20. El repo es público, así que GitHub aplica la protección sin plan de pago | Configuración del repo: activar el ruleset con los checks `backend`, `frontend`, `secret-scan`, `sast` y `terraform` como required | — | **nuevo** |
| P0-2 | `GetPlanQuery` solo prueba otro club, no otro grupo del mismo club | Acceso cruzado | «entrenador A no lee el plan del grupo de B, mismo club» → `Forbidden` | pendiente |
| P0-3 | Acceso cruzado de `clubtaxonomia` (grupos, tags, sugerencias de fusión) e `identidad` (desactivar, borrar, revocar sesiones, reenviar invitación) con ids inexistentes en lugar de objetos ajenos reales. `ArchiveTagKey`, `ArchiveTagValue`, `ChangeTagValueMetadata` y `DismissMergeSuggestion` no tienen ni eso | Acceso cruzado de integración (`IntegrationTestBase`) | Sembrar un grupo en el club Y; el admin de X hace `AssignCoachToGroup(grupoY)` → denegado y sin fila en `grupo_entrenador` | pendiente (y **nuevo** para los 4 sin test) |
| P0-4 | Violación de ADR-0009 D12/D14: el ADR exige `Either.Left(XxxError.Forbidden)` o lista vacía. `GetGroupDetailQuery.kt:24` y los tests de clubtaxonomia/identidad devuelven `NotFound` a propósito, para no filtrar existencia | Cambiar los servicios a `Forbidden`. Si el equipo prefiere anti-enumeración con `NotFound`, hace falta una PR de revisión de ADR-0009 encadenada; mientras no exista, gana el ADR | — | **nuevo** |
| P0-5 | Ningún test de acceso cruzado de caso de uso pasa por el aspecto `@AuthScope` real: todos son unitarios con dobles. `AuthScopeAspectIntegrationTest` solo ejercita `UserRepository.findById` | Integración: un repositorio `@AuthScope(CLUB)` por módulo, llamado con el `clubId` de otro club | «`GroupRepository.find(clubY, id)` con un principal de X → rechazo fail-closed» | **nuevo** |
| P0-6 | `AuthorizationMatrix` sin tests por rol para `PLAN`, `AUDIT_EVENT`, `GROUP_MERGE_SUGGESTION` y `GROUP:ASSIGN_COACH`; `grantedTo` solo probado para ADMIN | Unitario parametrizado rol × recurso × acción | Tabla completa esperada vs. matriz | **nuevo** |
| P0-7 | Límite de staleness: no hay caso `lag=59s` → permite; no hay test del mapeo `ProjectionStale` → 503 (`PlanErrorMapper.kt:22`) | Unitario + test de controlador | `lag=59` publica; `lag=60` → 503 `PROJECTION_STALE` | **nuevo** |
| P0-8 | Casos de uso de alumno en seguimiento (`RecordMark`, `WithdrawMark`, `RescheduleDay`, `WithdrawDayAdjustment`, `SubmitSessionReport`, `GetMyMarks`) sin test de aislamiento entre alumnos del mismo club | Acceso cruzado | «alumno A no retira la marca de B» | pendiente (aislamiento e integridad del gate de consentimiento); aislamiento: **nuevo** |
| P0-9 | `/me/permissions`: falta el caso ENTRENADOR; ningún test HTTP con sesión real | Unitario + integración HTTP | — | **nuevo** |

### P1 — RGPD e integridad de eventos

| # | Hueco | Tipo | Caso de ejemplo | Estado |
|---|---|---|---|---|
| P1-1 | `CoachGroupProjectionListener` sin ningún test | Integración EventFlow + reentrega | `EntrenadorAsignadoAGrupo` ×2 → una sola fila en `seguimiento.grupo_entrenador` | **nuevo** |
| P1-2 | `PlanificacionDeletionListener` sin test (solo el adaptador) | Integración EventFlow | — | pendiente |
| P1-3 | `PersonalizationProjectionListener` y `LesionDeclaradaListener` solo con unitarios; el doble del tracker oculta que la clave se quema antes de escribir | Integración con BD real y fallo inyectado | Forzar un fallo tras el `markProcessed` → la reentrega debe aplicar el cambio | pendiente |
| P1-4 | `AuditTrailAnonymizationListener`: sin caso `EntrenadorEliminado` ni reentrega | Integración | Baja de entrenador → `auditoria.evento` anonimizado; segunda entrega sin efecto | **nuevo** |
| P1-5 | `SeguimientoDeletionListener`: `reajuste_dia` se borra pero no se comprueba; `grupo_entrenador` y `EntrenadorEliminado` solo en unitario | Integración | — | **nuevo** (complementa la tarea pendiente de deuda RGPD restante) |
| P1-6 | `SeguimientoDeletionEventFlowIntegrationTest.kt:81` duerme y no afirma nada: no puede fallar | Reescribir: comprobar que el evento queda completado en `event_publication` | — | **nuevo** |
| P1-7 | `AuditEventListener`: reentrega probada solo para `AccesoDenegado`, no para `AccesoADatosSensibles` | Integración | — | **nuevo** |
| P1-8 | MDC sin verificar en 8 listeners (MergeSuggestion, LesionDeclarada, GroupMembersProjection, PersonalizationProjection, ConsentProjection, CoachGroupProjection, AuditTrailAnonymization, AuditEvent) | Unitario por listener o regla ArchUnit (P2-3) | — | **nuevo** (los 3 de email: pendiente) |
| P1-9 | `last_processed_event_id/ts` solo se afirma en `club_taxonomia.persona`; `seguimiento.projection_lag_seconds` sin test | Integración por proyección | — | **nuevo** / pendiente |
| P1-10 | Retrocompatibilidad JSON Schema: no hay payloads de versiones anteriores en `src/test/resources/events/` (ADR-0010 D8) | Contrato | Deserializar `plan-publicado-v1` fijado frente a la clase actual | **nuevo** (endurecimiento: pendiente) |

### P2 — Infraestructura de test

| # | Hueco | Acción | Estado |
|---|---|---|---|
| P2-1 | No existen builders/Object Mother (§8) ni `TestPrincipals`/`TestClubs`/`TestPrincipalContext` (§5). Hay 14 `*AuthorizationTest` con un `fun principal(role)` local duplicado, siempre del mismo club | Crear el kit en `com.runcriticon.testing` antes de atacar P0-3/P0-8 | **nuevo** |
| P2-2 | No hay catálogo `README.md` de tests críticos en ningún módulo (§10) | Uno por módulo, alimentado por este documento | **nuevo** |
| P2-3 | Reglas ArchUnit exigidas por CLAUDE.md que no existen, algunas ya con violaciones: listeners fuera de `application/listeners` (los 3 de email en `identidad/infrastructure/events`), `UUID` crudo en `domain` (auditoría, `AuditEntry`, `UserInvited`, `DayAdjustment`), `planificacion` sin bean de métricas, `@ApplicationService` fuera de `application`, SDK AWS general | Reglas nuevas; las violaciones, a su ticket | Reglas: **nuevo**; typed IDs: pendiente |
| P2-4 | Reglas que pasan en vacío: `SchemaFronterasArchTest:186/194` (0 `@JoinColumn`/`nativeQuery`; el SQL vive en ~29 repos JDBC; además `clubtaxonomia` ≠ `club_taxonomia` en `moduleSchemaOrNull`); la regla de `@Repository` excluye las 11 interfaces Spring Data; `allowEmptyShould(true)` generalizado | — | pendiente |
| P2-5 | Esperas fijas: 15 `SETTLE_MILLIS` y ~25 bucles de sondeo hechos a mano | Migrar a `Scenario` de Spring Modulith o a Awaitility (ambos ya en el classpath) | pendiente |
| P2-6 | 36 clases siguen con `@Container` propio en lugar de `IntegrationTestBase` singleton, y los contratos corren dos veces | Migrar; excluir `*.contracts.*` de `test` | pendiente; migración: **nuevo** |
| P2-7 | Flyway: `ContextoArrancaTest` valida solo las 11 `@Entity` con `ddl-auto: validate`; los repos JDBC no se validan contra el esquema; no hay test de compatibilidad hacia atrás (ADR-0010 D11) | Test que ejecute cada query JDBC contra el esquema migrado (smoke por repo) | **nuevo** |
| P2-8 | ADR-0010 promete y no existe: PITest nocturno (D9, el ADR ya lo reconoce), retry de Gradle y `retries: 1` de Playwright (hoy 2, D21), `dependency-review` (D14), `format:check` de Prettier (D7), cobertura publicada (D7), dataset de staging (§9, ADR-0006 D21), `loadTest` en ningún workflow | Decidir por punto: implementar o enmendar el ADR | PITest: pendiente; resto: **nuevo** |
| P2-9 | Comentarios de `ci.yml:47-48` y `:54-55` falsos («no necesita base de datos», «sin contexto Spring») | Corregir | **nuevo** (menor) |

### P3 — Frontend

| # | Hueco | Tipo | Estado |
|---|---|---|---|
| P3-1 | Crear y publicar plan (editor semanal, crítico según ADR-0012 D6) sin e2e; `PlanService` y `coachGuard` sin spec | e2e + axe; unit | axe: pendiente; funcional: **nuevo** |
| P3-2 | Ningún e2e llega al backend real (todo `page.route()`); `home.spec`/`login.spec` sin mock, con comportamiento distinto en CI y en local | Un smoke e2e contra `bootRun` + Postgres en CI (login → publicar plan → alumno lo ve) | **nuevo** |
| P3-3 | CSRF (`X-XSRF-TOKEN`) sin test en ninguna capa | Unit con `HttpTestingController` o e2e real (P3-2) | **nuevo** |
| P3-4 | `studentGuard` redirige a `/` y pierde el `returnUrl` ante un 401 (contradice D15); `landingGuard` sin `catchError`; `returnUrl` solo probado en el caso feliz (usa `navigateByUrl`, que no sale del origen, así que no es un *open redirect*) | Unit de guards + fix | **nuevo** |
| P3-5 | Catálogo D19: falta `MERGE_SUGGESTION_NOT_FOUND`; nada sincroniza `error-codes.ts` con los `ErrorMapper` del backend | Test que compare el catálogo con los códigos emitidos (fichero generado en el build del backend) | **nuevo** |
| P3-6 | Servicios con efecto sin spec: `consent.service` (envía la versión del texto RGPD), `my-plan.service`, `plan.service`, logout de `student-shell` (no limpia cachés) | Unit | **nuevo** |
| P3-7 | Dependencia de la hora real (`club-salud.spec`, `my-week`, `reschedule-dialog`, `coach-alerts`); `mark-freshness.ts` sin test; selectores `.first()`/`.last()` en `club-grupos.spec` | `page.clock` / reloj inyectable; selectores por rol | **nuevo** |
| P3-8 | Rutas no críticas sin e2e/axe (magic link, reseteo, invitar entrenador, alertas, marcas) | e2e + axe | pendiente |
| P3-9 | Sin `coverageThreshold` en Jest (D21 pide >70 %) | Configuración | **nuevo** |

## 4. Lo que está bien

Para no repetirlo en futuras auditorías:

- **Contratos**: 22/22 integration events con schema v1 y test `@Tag("contract")`.
- **Planificación**: cumple D14 al pie de la letra: `Forbidden` en comandos, lista vacía en listados, `AccesoDenegado` emitido.
- **Aspecto `@AuthScope`**: fail-closed probado para club distinto, principal ausente, parámetro ausente y scope `OWNED`.
- **Purgas `@Scheduled`** (ADR-0017): las tres existentes cubiertas, incluido el borde de la ventana.
- **Borrado en identidad y clubtaxonomia**: cobertura sólida (cinco tablas, terceros intactos, idempotencia).
- **Frontend**: interceptores 401/4xx/5xx, `authGuard`/`staffGuard`, `SessionService` y `PermissionsService` (fail-closed, solo UX).

## 5. Secuencia propuesta

1. **P0-1** (branch protection). Es una tarea de configuración, sin código.
2. **P2-1** (kit de test: builders + `TestPrincipals`), porque desbloquea todo lo demás.
3. **P0-2…P0-9**, por módulo: una PR por módulo con sus tests de acceso cruzado y su `README.md` de catálogo (**P2-2**).
4. **P1** en bloque de listeners, junto con **P2-5**: cada listener que se toque migra a `Scenario`.
5. **P2-3/P2-4** (ArchUnit), después de arreglar las violaciones o con ellas congeladas (`FreezingArchRule`).
6. **P3-1/P3-2** (e2e real del bucle entrenador → alumno); el resto de P3 como mantenimiento.

## 6. Supuestos e incertidumbres

| Supuesto | Confianza |
|---|---|
| La protección de rama se leyó con `gh api` el 2026-09-22; puede cambiar sin commit | Alta |
| «Pasa en vacío» se deduce por grep del código de producción, sin ejecutar ArchUnit | Media-alta |
| Un test se da por inexistente si no aparece por nombre de clase ni por patrón. Un test con un nombre inesperado podría haberse escapado | Media |
| Los recuentos del frontend excluyen `src/app/ui/` (helm) y el cliente generado | Alta |
| No se ejecutó ningún test: «existe» no implica «pasa» | — |
