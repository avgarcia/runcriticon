# Auditoría de arquitectura — Runcriticon

**Fecha**: 2026-09-19 · **Commit auditado**: `03cc3f7` (= `origin/main`) · **Alcance**: solo lectura, sin fixes aplicados.

**Volumen**: 746 `.kt` backend (270 de test), 432 `.ts` frontend, 22 JSON Schemas, 49 migraciones Flyway, 33 `.tf`, 17 ADRs.

---

## Resumen ejecutivo

**El código está sano; la documentación se ha desacoplado de él.** De 93 hallazgos, **1 es explotable** (`A-1`, IDOR) y **3 más son defectos de código con impacto en producción** (`R-1` dato de salud sin consentimiento, `E-1` emails duplicados ante reentrega del outbox, `E-2` evento marcado como procesado y descartado en silencio). El resto es deuda documental y huecos de enforcement.

De los 5 críticos y 18 altos, **9 pertenecen a dos cascadas documentales** — dos causas raíz, no una: la corrección de **ADR-0007 D13** (reintentos inexistentes, 13 hallazgos sobre 18 apariciones textuales: 3 críticos + 5 altos + 5 medios) y el residuo de **ADR-0006 D4** (Spring Session JDBC, 4 altos). Arreglar las dos sub-decisiones aguas abajo cierra 17 hallazgos con dos PRs. Descontando esas cascadas y los 32 de severidad baja, quedan **~44 hallazgos independientes**.

Tres conclusiones de fondo:

1. **Hay un IDOR real y explotable en producción** (`A-1`): `GetPlanQuery` es el único de 8 casos de uso de `planificacion` sin comprobación entrenador↔grupo. Su propio KDoc afirma que distingue *"no es tuyo"* — comprobación que el código nunca hace. Un entrenador lee planes ajenos con sus personalizaciones (`alumnoId` + `mensajeAlAlumno`). Es lo único que yo trataría como bloqueante inmediato.

2. **El enforcement mecánico da una falsa sensación de cobertura.** Los 25 tests de arquitectura pasan en verde, pero verifiqué que **`SchemaFronterasArchTest` es enteramente vacuo** (0 `nativeQuery` y 0 `@JoinColumn` en todo el backend; pasa solo por `allowEmptyShould(true)`, mientras los 37 ficheros con SQL real en `const val` quedan sin vigilar), que **el guard de typed IDs que ADR-0008 D11 exige no existe** (`UuidV7ArchTest` comprueba otra cosa, y la colisión de nombre lo disimula), y que **nada en CI ata un evento a su schema, un schema a su test, ni un listener a `markIfNew`**. La calidad observada es fruto de disciplina, no de guards.

3. **La mayor bolsa de deuda es una cascada documental de un mes.** La corrección de ADR-0007 D13 (2026-08-24, tras descompilar Modulith 2.1.0) estableció que **no existen reintentos con backoff**. Verifiqué **18 apariciones** de la política derogada repartidas en 6 ADRs, 2 guías, 1 skill y el glosario. La peor: `ADR-0011:271` define una **alarma de producción** (`outbox_dlq_events > 0`, *"reintentos agotados"*) sobre una condición que el framework no puede producir — una alarma que nunca disparará.

### Tabla resumen (severidad × área)

| # | Área | Crítico | Alto | Medio | Bajo | Total |
|---|---|---:|---:|---:|---:|---:|
| 1 | Hexagonal y fronteras (ADR-0007/0008) | 0 | 3 | 4 | 5 | **12** |
| 2 | Autorización en tres capas (ADR-0009) | **1** | 1 | 4 | 3 | **9** |
| 3 | RGPD + retención (ADR-0014/0017) | **1** | 1 | 3 | 6 | **11** |
| 4 | Integration events (ADR-0007) | 0 | 1 | 7 | 7 | **15** |
| 5 | Persistencia (ADR-0004) | 0 | 0 | 0 | 1 | **1** |
| 6 | Secretos y configuración (ADR-0013) | 0 | 0 | 0 | 0 | **0** |
| 7 | Lenguaje ubicuo (ADR-0008 D4) | 0 | 3 | 1 | 2 | **6** |
| 8 | Frontend (ADR-0001/0012) | 0 | 0 | 5 | 2 | **7** |
| 9 | Coherencia del corpus de ADRs | **3** | 9 | 13 | 6 | **31** |
| 10 | Seguridad general (OWASP) | 0 | 0 | 1 | 0 | **1** |
| 11 | IaC Terraform (ADR-0006/0013) | 0 | 0 | 0 | 0 | **0** |
| | **Total** | **5** | **18** | **38** | **32** | **93** |

> **Lectura honesta del total**: 93 suena a mucho y no lo es. **17 de esos hallazgos son dos frases mal copiadas** (cascada ADR-0007 D13: 13 hallazgos; cascada ADR-0006 D4: 4), y se cierran con dos PRs de revisión de ADR. De los 93, **1 es explotable** y 3 más son bugs con impacto en producción.

### Seguimiento en Linear

Los 93 hallazgos están repartidos en el proyecto **[Runcriticon — Auditoría de arquitectura 2026-09](https://linear.app/lalin1982/project/runcriticon-auditoria-de-arquitectura-2026-09-af05d7621262)** (`P-LAL-8`): 11 tareas de primer nivel que siguen el plan de corrección de abajo, más 9 sub-tareas colgando de la nº 11.

| Tarea | Punto del plan | Hallazgos |
|---|---|---|
| `LAL-139` | 1 · IDOR en `GetPlanQuery` | `A-1` `A-2` |
| `LAL-140` | 2 · Consentimiento art. 9 en marcas | `R-1` |
| `LAL-141` | 3 · Cascada ADR-0007 D13 | `C9-1`…`C9-12` `G-2` |
| `LAL-142` | 4 · Purga de auditoría a 12 meses | `R-2` |
| `LAL-143` | 5 · Cascada ADR-0006 D4 | `C9-13`…`C9-16` |
| `LAL-144` | 6 · Email listeners sin idempotencia | `E-1` |
| `LAL-145` | 7 · Guards ArchUnit agujereados | `H-1` `H-2` `H-3` `H-4` `H-6` `C9-23` `C9-24` `C9-25` |
| `LAL-146` | 8 · Glosario desactualizado | `G-1` `G-3` `G-5` `G-6` |
| `LAL-147` | 9 · Lista de PII de ADR-0014 D5 | `G-4` `R-6` `R-7` |
| `LAL-148` | 10 · Accesibilidad frontend | `F-1`…`F-5` |
| `LAL-138` | 11 · Resto documental y cosmético *(padre)* | — |
| ↳ `LAL-149` | 11a · Idempotencia y proyecciones de eventos | `E-2` `E-3` `E-8` |
| ↳ `LAL-150` | 11b · Drift en READMEs y docs de eventos | `E-4` `E-5` `E-6` `E-7` `E-9` `E-10` `E-11` `E-12` `E-15` |
| ↳ `LAL-151` | 11c · Endurecer contratos de eventos | `E-13` `E-14` |
| ↳ `LAL-152` | 11d · Defensa en profundidad de autorización | `A-3`…`A-9` |
| ↳ `LAL-153` | 11e · Deuda RGPD restante | `R-3` `R-4` `R-5` `R-8` `R-9` `R-10` `R-11` |
| ↳ `LAL-154` | 11f · Estructura hexagonal y typed IDs | `H-5` `H-7` `H-8` `H-9` `H-10` `H-11` `H-12` |
| ↳ `LAL-155` | 11g · Coherencia restante del corpus ADR | `C9-17`…`C9-22` `C9-26`…`C9-31` |
| ↳ `LAL-156` | 11h · Cobertura e2e/axe pendiente | `F-6` `F-7` |
| ↳ `LAL-157` | 11i · Persistencia y seguridad sueltos | `P-1` `S-1` |

Cobertura verificada: los 93 identificadores aparecen una sola vez entre las 20 tareas, sin duplicados ni huecos.

### Los 5 críticos

| # | Área | Qué es | Por qué es crítico |
|---|---|---|---|
| **A-1** | 2 | IDOR en `GetPlanQuery` | **Único defecto explotable hoy.** Fuga de datos entre entrenadores del mismo club. |
| **R-1** | 3 | `RecordMarkCommand` escribe dato de salud art. 9 sin gate de consentimiento | Justificado con una cita falsa a ADR-0014 D18. Riesgo RGPD; la resolución es decisión jurídica. |
| **C-1** | 9 | ADR-0005:51,67,206 hereda la política de reintentos derogada, incluido en su **tabla de NFR** | Un NFR es un compromiso verificable; este es inverificable. |
| **C-2** | 9 | ADR-0005:286 mitiga un riesgo con un endpoint que D13 declaró **diferido y no existe** | Riesgo documentado como cubierto cuando no lo está. |
| **C-3** | 9 | ADR-0011:271 define alarma `outbox_dlq_events > 0` sobre condición imposible | **Alarma de producción que nunca disparará.** Falsa red de seguridad operativa. |

### Suposiciones y niveles de confianza

| Suposición | Confianza |
|---|---|
| `origin/main` = `03cc3f7` es el estado vigente; los 3 ficheros sin commitear (puerto local) no son estado del repo | **alta** — verificado con `git status`/`git log` |
| Los 25 tests de arquitectura en verde son el baseline real de enforcement | **alta** — ejecutados, no inferidos: `BUILD SUCCESSFUL`, 0 failures |
| `A-1` es un olvido y no una decisión deliberada | **alta** — 7 de 8 casos de uso hermanos sí comprueban; ni siquiera inyecta `CoachGroupLookup`; su KDoc describe la comprobación ausente |
| `R-1`: la marca **es** dato art. 9 | **media** — lo afirman la migración y ADR-0014 D5; el KDoc afirma lo contrario. **Hay contradicción con certeza alta; cuál lado corregir es decisión jurídica, no técnica** |
| `H-10` (UUID crudo en firmas de `execute()`) es incumplimiento y no interpretación | **media** — D11 ata `domain` y sitúa la conversión "en los bordes"; `CLAUDE.md` dice "nunca UUID sueltos" sin matiz. Lo objetivo es que **el corpus es incoherente consigo mismo**: `identidad` usa typed IDs y los otros 4 módulos no |
| `A-3` (huecos de `@AuthScope`) no es explotable hoy | **media** — barridos los 17 call sites; **un call site nuevo lo reabre sin que CI avise** |
| Los guards ArchUnit afirmados en ADR-0002/0003/0008 no existen bajo otro nombre | **media** — barridas las 23 reglas `@ArchTest` del proyecto |
| Los tests de contrato **pasan** | **alta** — ejecutado `gradlew contractTest`: 144 tests, 0 failures, 36 clases |

### Qué NO se verificó

- **No ejecuté la aplicación** ni pruebas manuales: `A-1` se dictamina explotable por lectura de código y firma del endpoint, no por explotación real.
- **`/security-review` no se ejecutó tal cual**: la skill revisa *"the pending changes on the current branch"* y el árbol solo tiene 3 ficheros de config sin commitear, así que habría devuelto "sin hallazgos" sobre un diff vacío — una falsa señal de limpieza. Barrí el área 10 manualmente sobre el árbol completo.
- **No corrí la suite completa** (`gradlew build`): ejecuté el subconjunto de arquitectura, `detekt`, `ktlintCheck` y `contractTest`, todos en verde. Los tests de integración con Testcontainers **no** se ejecutaron.

---

## 0. Verificación ejecutada (no solo lectura de código)

| Comando | Resultado |
|---|---|
| `gradlew test --tests "com.runcriticon.architecture.*"` | **BUILD SUCCESSFUL** — 25/25 tests, 0 failures, 0 errors |
| `gradlew detekt ktlintCheck` | **BUILD SUCCESSFUL** |
| `gradlew contractTest` | (pendiente / ver nota) |

Desglose de los 25 tests de arquitectura en verde:

| Clase | Tests | Cubre |
|---|---|---|
| `AuthorizationArchTest` | 6 | `@Repository`→`@AuthScope`, `@ApplicationService`→matriz, handlers REST, param `clubId`, `SecurityContextHolder`, `HttpSession` |
| `CapasArchTest` | 4 | dominio puro, dependencias entre capas |
| `ModulithFronterasTest` | 2 | `ApplicationModules.verify()` + 5 bounded contexts |
| `RgpdArchTest` | 3 | `@Entity`→`@RgpdCategory`, `@AuditAccess` |
| `SchemaFronterasArchTest` | 2 | fronteras de esquema SQL |
| `NamingConventionArchTest` | 2 | regla de idioma (ADR-0008 D4) |
| `IntegrationEventArchTest` / `DomainEventArchTest` | 2+2 | contrato de eventos |
| `ConfiguracionArchTest` / `UuidV7ArchTest` | 1+1 | config, UUID v7 |

**Conclusión del baseline**: el enforcement mecánico de este repo es real y está verde. El valor de esta auditoría está en lo que las reglas **no** cubren (huecos de regla) y en el *drift* documental.

---

## 5. Persistencia (ADR-0004) — LIMPIO salvo un detalle

Comprobado, sin hallazgos:
- **Un esquema por módulo**: `identidad`, `club_taxonomia`, `planificacion`, `seguimiento`, `auditoria`, `_shared`. Correcto.
- **Sin FK cruzando esquema**: las 15 `REFERENCES` cualificadas apuntan todas al esquema de su propia carpeta (8 club_taxonomia, 4 identidad, 3 planificacion). Verificado por grep + `SchemaFronterasArchTest` en verde.
- **Convención de nombres `V{YYYYMMDD}{NNNN}__`**: las 49 migraciones casan. Cero excepciones.
- **Sin versiones Flyway duplicadas** entre carpetas de módulo (la trampa de la versión global): cero colisiones.

### Hallazgos

| # | fichero:línea | sev. | ADR | hallazgo |
|---|---|---|---|---|
| P-1 | `backend/src/main/resources/db/migration/_shared/V202606010001__crea_event_publication.sql` | bajo | ADR-0014 D2 / guía `persistencia.md` | La migración que crea el outbox no lleva comentario de categoría RGPD, pese a que `event_publication` es categoría **OUTBOX** en ADR-0014. Las otras dos migraciones sin comentario (`V202608140001` índice, `V202607260001` extensión `unaccent`) no crean tabla y no aplican. |

---

## 6. Secretos y configuración (ADR-0013) — LIMPIO

Comprobado, sin hallazgos:
- **Cero imports del SDK de AWS** en código de módulo (`software.amazon.awssdk` / `com.amazonaws`): 0 coincidencias en los 746 `.kt`. Además, la dependencia **ni siquiera está en `build.gradle.kts` ni en el version catalog** — la prohibición de ADR-0013 es estructuralmente imposible de violar, no solo convencional.
- **Convención SSM `/runcriticon/{env}/{component}/{name}`** respetada en los 5 puntos de uso reales (`security/token-hmac-secret`, `crypto/userid-hash-salt`, `email/postmark-server-token`, `db/password`, `identidad/bootstrap-admin-password`) y coherente entre ADR-0013, `application.yml`, `CONFIG.md` y los KDoc de `TokenHasherImpl` / `EmailHasherImpl` / `HmacUserIdHasher`.
- **Sin ficheros sensibles**: no existe ningún `.env`, `*.tfvars` real ni `secrets*.yml` en el árbol, y `git ls-files` confirma que **ninguno está trackeado**. `.gitignore` los cubre (`*.tfvars` con excepción a `example.tfvars`, `.env*`).
- Los únicos valores que parecen secretos en config trackeada son **fakes etiquetados** de local/test (`local-dev-not-prod`, `test-only-not-for-prod-hmac-secret-…`, `cambia-esta-password-local`), con el prefijo visible que el propio fichero documenta como convención anti-confusión. No es hallazgo.

> Nota: `application-local.yml` y `frontend/proxy.conf.json` tienen cambios locales sin commitear (cambio de puerto a 8081). No son estado del repo y no se reportan como hallazgo.

---

## 7. Lenguaje ubicuo (ADR-0008 D4) — DRIFT REAL EN EL GLOSARIO

El vocabulario **visible** está limpio: los textos de UI de los mockups (`docs/diseno/*.html`) y la prosa de los 10 wireframes están en castellano canónico ("Añadir entrenador", "Nuevo alumno", "Marcar como hecho", "Reajustar día"). Las apariciones de `coach`/`student` son **nombres de clase CSS, identificadores de región/CTA y nombres de fichero** — correctos en inglés por ADR-0008 D4 y explícitamente fuera del alcance de la regla.

El problema está en `docs/glosario.md`, que `CLAUDE.md` declara **autoritativo** y que lleva sin tocarse desde el 2026-09-02 (`29ed87b`), mientras los ADRs que describe se han revisado después.

| # | fichero:línea | sev. | ADR incumplida | hallazgo |
|---|---|---|---|---|
| G-1 | `docs/glosario.md:81` | **alto** | ADR-0009 **D11** | El glosario define `@AuthScope` como *"aspecto que **inyecta filtros** (`club_id`, relación del principal) en las queries de los repositorios"*. ADR-0009 D11:212 dice literalmente lo contrario: *"El filtrado **no** lo inyecta un aspecto mágico en la query — lo aplica el propio método del repositorio […] Un aspecto ligero **verifica** esa firma en runtime, no la construye"*. Es exactamente el *drift* doc↔código que la revisión del 2026-07-05 cerró en el ADR y que nunca se propagó al glosario. Peligroso: induce a creer que el filtro es automático y que basta con anotar. |
| G-2 | `docs/glosario.md:116-117` | **alto** | ADR-0007 **D13** | El glosario afirma *"DLQ implícita — eventos […] que han agotado los **5 reintentos**"* y *"Política de fallos del outbox — **5 reintentos con backoff exponencial**"*. ADR-0007 D13:385 dice: *"Spring Modulith **no reintenta automáticamente con backoff**"*, y la línea 381 documenta la corrección expresa (2026-08-24) de que esa afirmación concreta era **falsa**, verificada descompilando `spring-modulith-events-core` 2.1.0. El glosario conserva la afirmación ya desmentida. |
| G-3 | `docs/glosario.md:3` | **alto** | ADR-0008 **D4** | El párrafo de apertura dice: *"El vocabulario está en castellano y **así se escribe también en el código**"*. ADR-0008 D4:180 dice: *"El glosario es la lengua ubicua del negocio, en castellano; **no impone castellano a los identificadores de código**"*, y la nota de revisión del 2026-06-19 señala que la premisa antigua *"contradecía el código real"* y generaba *"retrabajo recurrente de nomenclatura"*. La premisa superada sigue viva, literalmente en la primera frase del documento declarado autoritativo. |
| G-4 | `docs/glosario.md:92` | medio | ADR-0014 D5/D6 | La definición de **PII primaria** enumera `seguimiento.alumno_perfil` y `seguimiento.marca`. Ninguna de las dos existe: las tablas reales del esquema son `plan_resuelto_por_alumno`, `evento_procesado`, `reporte_sesion`, `consentimiento_alumno`, **`marca_alumno`**, `reajuste_dia`, `grupo_entrenador`. Una lista de PII primaria que nombra tablas inexistentes es un riesgo directo para el flujo de derecho al olvido. |
| G-5 | `docs/glosario.md:97` | bajo | ADR-0014 D19 | Afirma *"**Pendiente de redactar** — el directorio `docs/legal/` no existe todavía en el repo"*. Sí existe: `docs/legal/rat.md` (4.141 bytes, 2026-09-04) y `docs/legal/consentimiento/`. Cita obsoleta. |
| G-6 | `docs/glosario.md:126,133` | bajo (informativo) | ADR-0008 D4 | Ejemplos en castellano (`Ritmo`, `Distancia`, `AlumnoId`) frente al código real (`Pace`, `Distance`, typed IDs en inglés). **No es incumplimiento**: ADR-0008 D4:184 se adelanta a esto (*"Los ejemplos […] que aún muestran nombres en castellano son previos a esta regla […] Se migran de forma oportunista"*). Se anota solo como inventario de LAL-52. |

**Nota de verificación**: comprobé si la cita `ADR-0008 D4` que usan `CLAUDE.md`, `backend/CLAUDE.md` y la skill `glosario-guardian` está rota, porque el título de D4 es *"Catálogo del DDD táctico que se aplica"*. **No lo está**: la regla de idioma es una viñeta dentro de la sección D4 (líneas 180-184). La cita es correcta.

---

## 10. Seguridad general (OWASP) — LIMPIO salvo un endpoint

> **Sobre `/security-review`**: la skill revisa *"the pending changes on the current branch"*. El árbol de trabajo solo tiene 3 ficheros de config sin commitear (cambio de puerto local), así que ejecutarla tal cual habría devuelto "sin hallazgos" sobre un diff vacío — una falsa señal de limpieza. Barrí el área manualmente sobre el árbol completo.

Comprobado, sin hallazgos:
- **Inyección SQL**: cero concatenación o interpolación Kotlin dentro de strings SQL en los 746 `.kt` (`@Query`, `createNativeQuery`, `jdbcTemplate`, raw strings `"""`). Todo parametrizado.
- **Fijación de sesión**: `SecuritySessionManager.startSession()` invoca `ChangeSessionIdAuthenticationStrategy.onAuthentication(...)` **antes** de guardar el contexto. El id de sesión rota al autenticar. Cubre login, cambio de contraseña caducada y magic link (todos pasan por `startSession`).
- **Cookie de sesión**: `Secure` + `HttpOnly` + `SameSite=Lax` fijados **en código** (`SessionConfig.cookieSerializer`), no por propiedad — con el motivo documentado (en Boot 4 `server.servlet.session.cookie.*` no llega al `CookieSerializer` de Spring Session). Correcto y deliberado.
- **CSRF**: activado con `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfCookieFilter` (patrón oficial SPA). El flag `Secure` de la cookie `XSRF-TOKEN` deriva de `request.isSecure()`, que **sí** es true en producción gracias a `server.forward-headers-strategy: framework` (`application.yml:83`) tras el proxy TLS de App Runner — punto explícitamente resuelto y comentado. No es hallazgo.
- **XSS**: CSP restrictiva (`default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`, `base-uri 'self'`, `form-action 'self'`; `style-src 'unsafe-inline'` justificado por los estilos de componente de Angular). En el frontend: **cero** `bypassSecurityTrust*`, `innerHTML`, `outerHTML`, `eval(`, `document.write`.
- **Fuga de información**: `server.error.include-stacktrace: never` + `GlobalRestExceptionHandler` con mensajes neutros; `management.endpoint.health.show-details: never`.
- **Fuerza bruta / enumeración**: throttling progresivo por IP **y** por cuenta con `Retry-After` creciente, 401 neutro que no distingue email inexistente de contraseña incorrecta, y reseteo con 202 neutro. Argon2id parametrizable. Tokens de un solo uso con `consumedAt` + `expiresAt` verificados en el agregado de dominio (`Invitation.kt:61-62`).
- **Cabeceras**: HSTS 1 año + `includeSubDomains`, `Referrer-Policy: strict-origin-when-cross-origin`.

### Hallazgos

| # | fichero:línea | sev. | ADR | hallazgo |
|---|---|---|---|---|
| S-1 | `backend/.../identidad/infrastructure/security/SecurityConfig.kt:62-71` | **medio** | ADR-0009 D1/D2 · ADR-0011 | `/actuator/prometheus` está expuesto (`application.yml:55`) pero **no tiene matcher propio**, así que cae en `anyRequest().authenticated()`: **cualquier usuario autenticado, incluido un `alumno`, puede leer todas las métricas operativas del sistema**. Contrasta con `/actuator/loggers`, que sí está correctamente restringido a `hasRole("ADMIN")` en la línea 70 — la asimetría sugiere descuido, no decisión. Doble filo: además, si AMP scrapea sin sesión, el scrape está roto hoy. Conviene decidir explícitamente (rol ADMIN, red interna, o token de scrape) en vez de dejarlo al default. |

---

## 3. RGPD (ADR-0014) + jobs de retención (ADR-0017) — 2 HALLAZGOS SERIOS

Comprobado, sin hallazgos (verificado columna a columna):
- **Las 11 `@Entity` están bien clasificadas**, no solo anotadas: PII primaria en `usuario`/`invitacion`/`magic_link`/`password_historico`/`consentimiento`, cat. 2 en los dos `evento_auditoria`, cat. 3 en `auditoria.evento`, `SIN_PII` en `club`/`tag_key`/`tag_value`. Ninguna PII disfrazada de `SIN_PII`, ninguna auditoría marcada como cat. 1. Las 26 tablas sin entidad JPA llevan comentario de categoría correcto en su migración.
- **`StudentDeletionListener`: cobertura completa en los 4 módulos con PII.** No hay ni una tabla con PII que su listener no toque (salvo R-3 abajo). Los cuatro son idempotentes vía `ProcessedEventTracker` y restauran el MDC con `finally { clear() }`. Los tres roles disparan borrado.
- **El bug de `set_masklen` NO está presente**: `identidad.trunca_ip` castea a `cidr` (no a `inet`) y la migración documenta por qué. Único uso en el repo. Además **ningún log operativo emite una IP** (sin accesslog de Tomcat, sin `ip` en el MDC, sin logs en el path de rate-limiting) — D9 se cumple, aunque de forma vacía. La IP completa en `evento_auditoria.ip` y `consentimiento.ip` es **lo correcto** (D9 auditoría, D18 forense), y la anonimización sí la trunca al ejercer el olvido.
- **Ausencia de lock distribuido en los jobs: correcta** — ADR-0017 D3 la autoriza para `DELETE` por fecha sin efectos colaterales, y los tres cumplen esa condición. Los tres tienen test de integración con datos sembrados (D7) y cron externalizado a `application.yml` (D8).
- **Modelo de consentimiento art. 9.2.a completo** (tabla con `version_texto`/`concedido_en`/`revocado_en`/`ip`/`user_agent`, proyección local en `seguimiento` por eventos) y **gate aplicado** con `ensure(consentReader.isGranted(...))` justo tras el RBAC en `SubmitSessionReportCommand` y `RescheduleDayCommand` — en todo salvo R-1.

### Hallazgos

| # | fichero:línea | sev. | ADR | hallazgo |
|---|---|---|---|---|
| R-1 | `backend/.../seguimiento/application/usecases/marks/RecordMarkCommand.kt:33` | **crítico** | ADR-0014 **D16/D18** | `RecordMarkCommand` escribe un dato de salud del art. 9 **sin comprobar el consentimiento**, y lo justifica en su KDoc con una restricción que el ADR no contiene: *"la marca no es un dato […] cubierto por el consentimiento (ADR-0014 **D18 lo ata a `reporte_sesion`**)"*. **Verifiqué D18 literalmente: no menciona `reporte_sesion` en ningún punto**; dice *"el módulo Seguimiento rechaza nuevas operaciones de tratamiento"*, sin acotar a tabla. Y en sentido contrario, ADR-0014 **D5:163** enumera `seguimiento.marca` entre los datos de salud de categoría 1, y la propia migración `V202608280001__crea_marca_alumno.sql:4` dice *"dato de salud sensible (art. 9 RGPD), citado por ADR-0014 D5"*. La contradicción es cierta; **cuál de los dos lados corregir es decisión jurídica, no técnica**: o se añade el gate de consentimiento, o se reclasifica la marca en D5 + migración. Mientras no se decida, hay una escritura de dato art. 9 sin base legal verificada en runtime. |
| R-2 | `identidad/V202606210001__crea_evento_auditoria.sql:2`<br>`club_taxonomia/V202608230001__crea_evento_auditoria.sql:5` | **alto** | ADR-0014 **D10** · ADR-0017 | **No existe ningún job de retención de categoría 2 (12 meses).** Verificado: el repo tiene exactamente **3** `@Scheduled` — `AuditoriaRetentionJob` (cat. 3, 24 m), `ClubTaxonomiaRetentionJob` (`evento_procesado`, 30 d) y `EventPublicationRetentionJob` (outbox, 30 d). Ninguno toca `evento_auditoria`. Las dos tablas crecen indefinidamente pese a que su propia migración dice *"Retención 12 meses (purga posterior)"* y D10 exige *"Cron mensual purga filas con `ts < now() - 12 months`"*. **No está diferido**: la lista *"Lo que este ADR no decide"* de ADR-0017 nombra las categorías 1, 5 y 6 — no la 2. |
| R-3 | `identidad/V202609180003__amplia_invitacion_invitado_por.sql:10` + `InvitationEntityRepository.kt:25` | medio | ADR-0014 D6 (cat. 1) | Al suprimir a quien invitó, su UUID **sobrevive indefinidamente** en `invitado_por` de todas las invitaciones que emitió: el borrado solo filtra por `usuario_id` (`delete … where i.clubId = :clubId and i.userId = :userId`). La columna está clasificada `PII_PRIMARIA` (*"identifica a un usuario del club, igual que usuario_id"*), así que o el borrado la anula o la clasificación sobra. |
| R-4 | `backend/src/test/kotlin/com/runcriticon/architecture/RgpdArchTest.kt:31` | medio | ADR-0014 D5 | **HUECO DE REGLA.** El guard `toda @Entity declara su @RgpdCategory` cubre **11 de 37 tablas**. `seguimiento` y `planificacion` no tienen **ni una** `@Entity` (persistencia JDBC pura), así que las tres tablas de salud art. 9 (`reporte_sesion`, `marca_alumno`, `reajuste_dia`) y `club_taxonomia.persona` (nombre + email en claro) quedan fuera del enforcement: solo las protege un comentario SQL que nada verifica en CI. El guard da una falsa sensación de cobertura total. |
| R-5 | `identidad/V202606030002__crea_usuario.sql:5-18` | medio | ADR-0014 D10 (cat. 1) | La purga de cuentas en gracia (30 d) no solo falta: **no hay columna de fecha de baja** sobre la que construirla (`estado` DESACTIVADO + `modificado_en`, que se mueve con cualquier edición). Requiere migración, no solo un job. Atenuante: ADR-0017 sí difiere explícitamente la categoría 1. |
| R-6 | `_shared/V202606030001__crea_spring_session.sql:4` | bajo | ADR-0014 D5 | `SPRING_SESSION(_ATTRIBUTES)` no declara **ninguna de las seis categorías**; inventa la etiqueta *"datos operativos de sesión"*. `PRINCIPAL_NAME` se puebla. |
| R-7 | `seguimiento/V202608250003__crea_consentimiento_alumno.sql:2` | bajo | ADR-0014 D5 | `SIN_PII` contradice el criterio que el repo aplica en todas las demás tablas con solo un id de persona (cf. `V202609040002:12`, que marca `PII_PRIMARIA` por llevar `entrenador_id`). Sin impacto real: el borrado la limpia igualmente. |
| R-8 | `auditoria/V202608190001__crea_evento_y_evento_procesado.sql` | bajo | ADR-0017 D4 | Los `evento_procesado` de `planificacion`, `seguimiento` y `auditoria` crecen sin límite: la purga a 30 días solo se implementó en `club_taxonomia`. `auditoria` tiene incluso el índice `evento_procesado_processed_at_idx` sembrado para un job que nunca llegó. |
| R-9 | `shared/events/infrastructure/scheduling/EventPublicationRetentionJob.kt:31` | bajo | ADR-0011 / ADR-0017 D2 | `Counter.builder("shared.events.retention_purge.rows_deleted")` sin tag `module`, a diferencia de los otros dos jobs que van por `{Modulo}Metrics`. Atenuante: la tabla no pertenece a ningún módulo. |
| R-10 | `auditoria/infrastructure/scheduling/AuditoriaRetentionJob.kt:25` | bajo | ADR-0014 D10 | Cron **diario** donde D10 dice *"Cron mensual"*. Más estricto, no divergente — el plazo de 24 meses se respeta. Anotado solo por trazabilidad. |
| R-11 | los 3 jobs `*RetentionJob.kt` | bajo | convención del repo | Incumplen *"sin referencias a ADR ni LAL en el código"*: p. ej. `"Purga de retención de auditoria.evento (ADR-0017 D5, cierra LAL-133)"`. La trazabilidad va en la PR y en `docs/`. |

> **Nota cruzada con G-4**: ADR-0014 **D5:163** enumera como PII primaria `seguimiento.alumno_perfil` y `seguimiento.marca`. **Ninguna de las dos existe** (las reales son `reporte_sesion`, `marca_alumno`, `reajuste_dia`, …). El error no está solo en el glosario: está en el **ADR autoritativo**. Eleva G-4 de "glosario desactualizado" a "la lista de PII primaria es incorrecta en la fuente de verdad" — con impacto directo en el flujo de derecho al olvido.

---

## 11. IaC Terraform (ADR-0006, ADR-0013) — LIMPIO

Área no cubierta por el reparto de subagentes; auditada en sesión sobre los 33 `.tf`.

Comprobado, sin hallazgos:
- **Región `eu-west-1`** en `staging` y también en `localstack` (por consistencia declarada con ADR-0006 D1).
- **`default_tags`** aplicado por provider con alias por módulo, de modo que cubre **todos** los recursos sin repetir `tags` recurso a recurso (ADR-0006 tagging).
- **Los 6 secretos son `SecureString`** con CMK propia (`alias/runcriticon-{env}-ssm`), y sus nombres respetan la convención `/runcriticon/{env}/{component}/{name}` sin excepción: `security/token-hmac-secret`, `crypto/userid-hash-salt`, `email/postmark-server-token`, `email/postmark-webhook-secret`, `identidad/bootstrap-admin-password`, `db/password`.
- **RDS**: `storage_encrypted = true` con KMS key dedicada, `publicly_accessible = false`, `deletion_protection` parametrizado y `final_snapshot_identifier` condicionado a `skip_final_snapshot`.
- **IAM**: los dos únicos `resources = ["*"]` son `ecr:GetAuthorizationToken` y `apprunner:ListServices`, acciones que **no admiten ARN de recurso** en la API de AWS. Correcto, no es sobre-permisividad.
- Estructura por módulos (`network`, `database`, `runtime`, `secrets`, `observability`, `cicd`) con `versions.tf` por módulo y backend de state remoto en `_shared/state-backend.tf`.

> Observación (no hallazgo): solo existen los entornos `staging` y `localstack`; no hay `production`. Coherente con el estado H0.

---

## 2. Autorización en tres capas (ADR-0009) — 1 IDOR REAL

Comprobado, sin hallazgos:
- **89 `@ApplicationService` inventariados.** Las 12 exenciones son legítimas y verificadas una a una: 8 `@NoAuthRequired` son flujos anónimos por token/credencial (login, activación, magic link, reseteo, `ResolveInvitationQuery`); 4 `@AuthenticatedOnly` operan solo sobre el propio principal (`QueryClubQuery` lee `ClubId.of(actor.clubId)`, etc.). Ninguna exención encubre acceso a recursos de terceros.
- **Filtro `club_id` realmente en la query**, no en Kotlin: leído el SQL de los 40 `@Repository`. Todos los `@AuthScope(Scope.CLUB)` lo llevan en el `WHERE`/`JOIN`. **Cero filtrado en memoria tras un `findAll`** (D10 limpio).
- **`AuthScopeEnforcementAspect`**: pointcut `@annotation(authScope)`, no restringido a `@Repository`, así que cubre todo método anotado. Wiring verificado (`build.gradle.kts:118`). Sin auto-invocación que lo esquive.
- **Capa 2 correcta en `seguimiento`**: todos los `/me/*` derivan la identidad de `StudentId.of(actor.userId)`, **jamás de un parámetro**. El único ID de entrada del módulo (`groupId?` de `ListCoachAlertsQuery`) va sobre un CTE `coach_groups` ANDeado incondicionalmente: el filtro opcional solo **estrecha**, nunca ensancha.
- **`/me/permissions` conforme a D18**: única referencia en `MeController.kt:42-46`; **cero** usos de `grantedTo`/`permissions` como guarda en ningún controller o filtro.

### Hallazgos

| # | fichero:línea | sev. | ADR-0009 | hallazgo | ¿explotable? |
|---|---|---|---|---|---|
| A-1 | `backend/.../planificacion/application/usecases/plans/GetPlanQuery.kt:29-50` | **crítico** | **D3** (+D1, D14) | **IDOR real.** Único caso de uso de `planificacion` que **no** comprueba la relación entrenador↔grupo: solo RBAC + `club_id`. Verificado por comparación directa: de los 8 casos de uso del módulo, 7 llaman `ensure(coachGroupLookup.isCoachOfGroup(...))` y **solo este no** — ni siquiera inyecta `CoachGroupLookup` en el constructor. Lo delata su propio KDoc, que afirma *"colapsa «no existe» y «no es tuyo» en `Forbidden`"*: **el código nunca comprueba «no es tuyo»**. La matriz lo prescribe explícitamente (`AuthorizationMatrix.kt:65-66`: *"La comprobación de que el entrenador tiene relación con el grupo del plan va en el caso de uso (CoachGroupLookup)"*). No es decisión documentada — es un olvido. | **Sí.** `GET /api/planes/{planId}` (`PlanController.kt:77-85`). Cualquier `ENTRENADOR` del club lee el detalle completo de un plan de un grupo que no lleva, **incluidas las `personalizaciones` con `alumnoId` + `mensajeAlAlumno`**. Requiere un `planId` conocido: UUIDv7 no enumerable, pero circula por `GET /api/planes?grupoId=` y por la UI. |
| A-2 | `backend/src/test/.../GetPlanQueryTest.kt:27-57` | **alto** | **D14** | Falta el test de acceso cruzado del **mismo rol**, que es justamente el que D14 exige. Los 4 casos cubren plan existente, inexistente, **otro club** y rol `ALUMNO` — ninguno prueba entrenador A vs. plan de entrenador B **en el mismo club**. `PlanAuthorizationTest.kt` tampoco. Es la razón de que A-1 pasara revisión y CI en verde. | N/A |
| A-3 | `backend/src/test/.../AuthorizationArchTest.kt:38-48` | medio (regla) / bajo (hoy) | D11, D13 | **HUECO DE REGLA confirmado y mayor de lo que sospechaba.** El predicado `areDeclaredInClassesThat().areAnnotatedWith(Repository::class)` deja fuera: (a) **9** interfaces Spring Data sin `@Repository` (no 2 — las 7 de `identidad`, 3 de `clubtaxonomia`, 1 de `auditoria`); (b) **todos** los métodos heredados de `JpaRepository` (`findById`, `findAll`, `deleteAll`, `save`), que nunca están "declarados en" la clase anotada. | **No hoy.** Barridos los 17 call sites: los adaptadores solo usan `save` o derived queries con `club_id`. El único uso vivo de método heredado (`TaxonomyRepositoryImpl.kt:97,99`, `deleteAll`) recibe colecciones ya acotadas por `findAllByClubId`. **Un call site nuevo lo reabre sin que CI avise.** |
| A-4 | `shared/autorizacion/annotations/Authorize.kt` + `SecurityConfig.kt:63-71` | medio (sistémico) | D2 | `@Authorize("PLAN:LIST")` es **puramente declarativo**: cero referencias a `Authorize::class` fuera de los controllers, ningún aspecto ni interceptor lo lee, y la `SecurityFilterChain` solo hace `anyRequest().authenticated()`. **La capa 1 no existe en runtime.** Aplazamiento **documentado** (D2: *"el motor llega en Fase 1"*), así que no es incumplimiento — pero explica por qué A-1 tiene una sola barrera efectiva. |
| A-5 | `shared/autorizacion/spring/AuthScopeEnforcementAspect.kt:43-46` | medio (proceso) | D11, D3 | La capa 2 es **inexpresable** en la malla: el aspecto falla cerrado ante cualquier scope ≠ `CLUB`, así que declarar `Scope.OWNED`/`GRUPOS_DEL_ENTRENADOR` convierte toda llamada real en 403. Resultado: nada mecánico puede detectar un `isCoachOfGroup` ausente. **Causa raíz sistémica de A-1.** |
| A-6 | `clubtaxonomia/.../GetGroupDetailQuery.kt:44`, `ListGroupCoachesQuery.kt:47`, `ListStudentsQuery.kt`, `studenttags/*` | medio | D3, D8 | `clubtaxonomia` no tiene capa 2: un entrenador ve cualquier grupo/alumno/tag del club. **Decisión documentada y aceptada**, no descuido (`AuthorizationMatrix.kt:31-34`: *"la relación entrenador↔alumno todavía no existe"*). Se distingue de A-1, donde la matriz **sí** promete la comprobación. |
| A-7 | `identidad/.../InvitationRepositoryImpl.kt:42` → `InvitationEntityRepository.kt:18` | bajo | D4 | `findTopByUserIdOrderByIssuedAtDesc(userId)` sin `club_id`, con `@NoAuthScope`. **No explotable**: `InvitationIssuer.reissueFor` hace antes `userRepository.findById(ClubId.of(actor.clubId), userId)` (`@AuthScope(CLUB)`) y corta con `NotFound`. Falta la defensa en profundidad de D4. |
| A-8 | `planificacion/.../WeeklyPlanRepositoryJdbc.kt` (`FIND_SESSIONS_SQL`, `FIND_PERSONALIZATIONS_SQL`, …) | bajo | D4 | Queries hijas filtran solo por `plan_id`, sin `club_id`, contra la letra de D4 (*"Toda query del repositorio incluye el `club_id`"*). **No explotable**: el padre se carga con `FIND_PLAN_SQL` (`id = ? AND club_id = ?`) y devuelve `null` antes de tocar las hijas. |
| A-9 | `identidad/.../AuditTrailImpl.kt` | bajo | D4 | Anonimización RGPD por `metadata ->> 'email_hash'` sin `club_id`: con el mismo hash de email en dos clubes anonimizaría filas ajenas. Inocuo en mono-club (ADR-0006 D1); **anotar para el salto a multi-club**. |

---

## 4. Integration events (ADR-0007) — CONTRATOS SANOS, CI NO LOS PROTEGE

**`gradlew contractTest`: BUILD SUCCESSFUL — 144 tests, 0 failures, 36 clases.** Ejecutado, no inferido.

Comprobado, sin hallazgos (22 eventos: 9 `identidad`, 3 `clubtaxonomia`, 3 `planificacion`, 4 `seguimiento`, 3 `shared`):
- **6 campos obligatorios + `traceparent` en los 22**, en el mismo orden, todos `data class` con `@NamedInterface("events")`. Cero ausencias, cero nulabilidades invertidas.
- **Sincronización clase↔schema: 22/22**, campo a campo y tipo a tipo, **en ambos sentidos**, incluidos los payloads anidados. Los 22 con `$schema` 2020-12, `additionalProperties: false`, `$id` absoluto, `actorId`/`traceparent` como `["X","null"]` y fuera de `required`. Verificados además los **dominios de enum** (`AdjustmentAction`, `AdjustmentReason`, `ReportStatus`, `NotDoneReason`, `RaceDistance`): el `.name` emitido coincide exactamente con el conjunto del schema.
- **22 tests `@Tag("contract")`**, uno por schema, sin huérfanos en ninguna dirección.
- **Propagación de `traceparent`**: los 17 listeners invocan `MdcRestorerForEvents.restore(...)` con el del evento. Los 20 eventos con consumidor, cubiertos.
- **Los 14 listeners de `application/listeners/` cumplen el patrón completo**: `markIfNew(LISTENER, event.eventId)` como primera sentencia del `try` y **`finally { clear() }` verificado literalmente** en los 14 (no solo la llamada a `restore`). Trackers en `{esquema}.evento_procesado(listener, event_id)` con `UNIQUE`, en la misma transacción, sin `REQUIRES_NEW`.
- **Las 7 proyecciones cross-módulo declaran ambas columnas** `last_processed_event_id` y `last_processed_event_ts`, contrastado contra las migraciones Flyway.
- `LesionDeclarada` en `shared.api.events` con un productor y un consumidor: correcto igualmente por dirección de dependencia (ponerlo en `seguimiento.api.events` cerraría un ciclo de Modulith).

### Hallazgos

| # | fichero:línea | sev. | ADR | hallazgo |
|---|---|---|---|---|
| E-1 | `identidad/infrastructure/events/InvitationEmailListener.kt:21`<br>(+ `MagicLinkEmailListener.kt:21`, `PasswordResetEmailListener.kt:21`) | **alto** | ADR-0007 D9 | **3 `@ApplicationModuleListener` sin guarda de idempotencia.** Verificado leyendo el fichero: tienen `restore` + `finally { clear() }` pero **ningún `markIfNew`**. Son eventos internos de puerto, no `IntegrationEvent`, pero viajan igual por `event_publication`, cuya entrega es *at-least-once* por diseño → **una reentrega reenvía el email**: invitación, magic link o reseteo de contraseña duplicados. |
| E-2 | `seguimiento/application/listeners/MarkPaceRecalculationListener.kt:67` | medio | ADR-0007 D9 | `markIfNew` marca el evento como procesado **antes** de parsear la distancia; acto seguido `val distance = distanciaLiteral.toRaceDistance() ?: return`. Un literal desconocido **se descarta para siempre**, sin reproceso posible ni log de error — el evento queda marcado como procesado sin haberlo sido. |
| E-3 | `seguimiento/` (9 copias) | medio | ADR-0007 D11 | El mapeo `RaceDistance`↔`"5K"/"10K"/"21K"/"42K"` está duplicado **9 veces** (3 `toLiteral`, 6 parsers) con **3 modos de fallo distintos**: `null`, `error(...)` y `?: return`. Hoy coinciden; nada lo garantiza. Es la causa directa de E-2. |
| E-4 | `auditoria/README.md:40` | medio | ADR-0009 D15 | La fila de `AccesoADatosSensibles` dice *"Nadie todavía"* como productor; `AuditAccessAspect.kt:58` lo publica desde hace tiempo. |
| E-5 | `auditoria/README.md:39,17` | medio | ADR-0009 D15/D16 | La fila de `AccesoDenegado` lista **un** productor; hay **5** (`IdentidadAccessAuditor:44`, `ClubTaxonomiaAccessAuditor:36`, `PlanificacionAccessAuditor:36`, `SetPersonalizationCommand:181`, `RemovePersonalizationCommand:98`). |
| E-6 | `auditoria/README.md:20` | medio | ADR-0009 D15 | Afirma que *"el módulo seguimiento no [existe]"*. Existe, y es el dueño de los datos de salud. **E-4/E-5/E-6 juntos** describen el mecanismo de auditoría como muerto cuando está vivo — el tipo de drift que lleva a alguien a no instrumentar un caso de uso. |
| E-7 | `shared/` (ausente) | medio | ADR-0007 D12 | No existe `shared/README.md`: los 3 eventos de `shared.api.events` no tienen tabla canónica "Eventos publicados" en su propio módulo. |
| E-8 | `planificacion/infrastructure/` (ausente) | medio | ADR-0009 D9 | `planificacion` tiene 2 proyecciones con `last_processed_event_*` y `ProjectionFreshnessJdbc`, pero **no existe** `infrastructure/observability/` ni el gauge `planificacion.projection_lag_seconds`. `clubtaxonomia` y `seguimiento` sí lo tienen. Sin gauge no hay alarma de staleness para ese módulo. |
| E-9 | `schemas/README.md:57` | bajo | ADR-0007 D11 | El doc canónico de la convención dice que el directorio está *"Vacío"* — con 22 schemas dentro. El árbol de ejemplo cita `sesion-personalizada-v1.json` y `salud/`, inexistentes. |
| E-10 | `seguimiento/domain/AdjustmentReason.kt:18`, `CoachAlert.kt:48` | bajo | ADR-0007 D12 | Enlace KDoc a FQN inexistente `[com.runcriticon.seguimiento.api.events.LesionDeclarada]`: el evento vive en `shared.api.events`. |
| E-11 | `identidad/README.md:10-13` | bajo | ADR-0007 D11 | Columna "Consumido por" obsoleta: dice *"Seguimiento (pendientes de construir)"*; `seguimiento` existe y no consume esos eventos. |
| E-12 | `planificacion/README.md:71`, `seguimiento/README.md:142` | bajo | ADR-0007 D11 | Sus tablas "Eventos publicados" no llevan columna de ruta del schema; `identidad` y `clubtaxonomia` sí. |
| E-13 | `schemas/*/*.json` (los 22) | bajo | ADR-0007 D11 | `version` es `type: integer` **sin `const`/`enum`**: un payload v2 valida contra el schema v1, lo que debilita la detección durante el dual-publishing de 4 semanas. |
| E-14 | `seguimiento/contracts/ReporteRegistradoContractTest.kt:22` (patrón en los 22) | bajo | ADR-0007 D11 | Cada test construye su propio `JsonMapper` sin el módulo Kotlin, así que no valida el `ObjectMapper` real del outbox. Riesgo hoy bajo: verificado que no hay bean `ObjectMapper` ni `spring.jackson` global. |
| E-15 | `schemas/club_taxonomia/` | bajo | ADR-0007 D11 | Directorio `club_taxonomia` frente al paquete `clubtaxonomia`; la convención documentada es `schemas/{modulo}/`. Coincide con el esquema SQL, no con el módulo. |

### Huecos de regla (latentes, sin violación hoy)

Los 4 guards (`IntegrationEventArchTest`, `DomainEventArchTest`) cubren **solo** ubicación de paquete y `@NamedInterface`. **Nada en CI ata**:

| hueco | regla que se queda corta |
|---|---|
| Una clase `IntegrationEvent` a la existencia de su schema | `IntegrationEventArchTest.kt:25` (solo `resideInAPackage`) |
| Un schema a la existencia de un test `@Tag("contract")` | `IntegrationEventArchTest.kt:33` (solo `beAnnotatedWith`) |
| Un `@ApplicationModuleListener` a `restore` + `finally { clear() }` | no existe regla |
| Un `@ApplicationModuleListener` a `ProcessedEventTracker.markIfNew` | **no existe regla — y por eso CI no caza E-1** |

Consecuencia: **un evento nuevo sin schema y sin test de contrato no rompe nada en CI.** La coherencia 22/22 verificada arriba es fruto de disciplina, no de enforcement.

---

## 1. Hexagonal y fronteras de módulo (ADR-0007, ADR-0008) — CÓDIGO LIMPIO, GUARDS AGUJEREADOS

El código está **sustancialmente limpio**. El valor de esta área está en los **4 huecos de regla**, tres de ellos verificados por mí directamente.

Comprobado, sin hallazgos:
- **Pureza de `domain/`: LIMPIO.** Barrido de todos los `^import` bajo cualquier `*/domain/*` de los 5 módulos. El conjunto completo de imports no-`com.runcriticon` es: `kotlin*`, `arrow*`, `java.util.{UUID,Locale}`, `java.time.{Instant,Duration,LocalDate,DayOfWeek}`, `java.text.Normalizer`, `java.security.MessageDigest`, `java.nio.charset.StandardCharsets`, `com.github.f4b6a3.uuid.UuidCreator`. **Cero** coincidencias de `jakarta.`, `org.slf4j`, `io.micrometer`, `org.springframework`, `com.fasterxml`, `tools.jackson`, `software.amazon` ni `java.time.Clock` (los casos de uso reciben `now: Instant` como parámetro, no inyectan `Clock` en dominio).
- **Sin llamadas síncronas cruzadas: LIMPIO.** Matriz completa 5×5: **27 imports cruzados, todos a `.api.`** (25 a `api.events`, 2 a los DTOs `planificacion.api.*`). Comprobada además la vía indirecta (los puertos de `shared.autorizacion` no hacen de puente síncrono) y la vía SQL (ningún `.kt` referencia el esquema de otro módulo).
- **Errores como `Either`: LIMPIO.** Cero `throw`, cero `: Exception`, cero `class …Exception` en todo `domain/` y `application/`. Las dos únicas clases de excepción del backend están donde deben. **Los 10 `require` de `domain/` se verificaron uno a uno contra sus llamadores**: todos son precondiciones inalcanzables con la regla de negocio expresada aguas arriba como `ensure(...) { XxxError… }` (p. ej. `User.activate` ← `ActivateAccountCommand.kt:86`). Uso correcto según ADR-0008.
- **`api → domain`: LIMPIO** (cero imports). **`domain → application/infrastructure`: LIMPIO** (cero imports).
- **Estructura de sub-paquetes**: los 5 módulos con `domain/`/`application/`/`infrastructure/`; 4 con `api/`. **`auditoria` sin `api/` no es hallazgo** — ADR-0007 D3 la define como *"sumidero puro, no publica eventos que otros consuman"*.
- Descartado explícitamente tras evaluarlo: el patrón `..domain..` **sí** casa `com.runcriticon.auditoria.domain` (el `..` final admite cero paquetes), no hay fuga por segmento terminal; y `allowEmptyShould(true)` **no** enmascara nada en `CapasArchTest` (sus 4 reglas seleccionan clases reales).

### Huecos de regla (lo importante de esta área)

| # | fichero:línea | sev. | ADR | hallazgo |
|---|---|---|---|---|
| H-1 | `backend/src/test/.../UuidV7ArchTest.kt:19-24` | **alto** | ADR-0008 **D11** | **El guard que el ADR exige literalmente no existe.** ADR-0008 D11 (`0008-…md:317`) pide *"Test ArchUnit que detecta `UUID` y `String` como parámetros de métodos en `…domain.*`"*. **Verificado leyendo el fichero**: el único test con "Uuid" en el nombre comprueba algo **distinto** — que nadie llame a `UUID.randomUUID()` (ADR-0004 D8, regla legítima pero otra). La colisión de nombre crea una falsa sensación de cobertura: nada en CI verifica los typed IDs, que es justo lo que permite que existan H-4 y H-5. |
| H-2 | `backend/src/test/.../SchemaFronterasArchTest.kt:37-43` | **alto** | ADR-0007 D3 / ADR-0004 D4 | **La regla es enteramente vacua y pasa en verde.** Solo inspecciona `@Query(nativeQuery = true)` en interfaces. **Verificado por mí**: el backend tiene **0 ficheros con `nativeQuery`** y **0 `@JoinColumn`** — las dos reglas de esta clase seleccionan **cero** clases y pasan solo gracias a `allowEmptyShould(true)`. Mientras tanto, la superficie SQL real son **37 ficheros con `JdbcTemplate` y SQL en `const val`**, completamente sin vigilar. (Comprobado aparte que hoy **no hay violación real**: ninguna referencia a un esquema ajeno fuera de un KDoc.) |
| H-3 | `backend/src/test/.../SchemaFronterasArchTest.kt:112-118` | medio | ADR-0004 D4 | **Segundo defecto, independiente del anterior.** `moduleSchemaOrNull()` casa el nombre de **paquete** contra el de **esquema**: `MODULE_SCHEMAS` contiene `club_taxonomia` (con guion bajo) pero el paquete real es `com.runcriticon.clubtaxonomia`. **Verificado leyendo el matcher** (`this == "com.runcriticon.$schema" \|\| startsWith("com.runcriticon.$schema.")`): nunca casa, devuelve `null` y la condición hace `return` sin evaluar. **El módulo con más SQL del backend nunca se comprueba**, ni siquiera si algún día usara `nativeQuery`. |
| H-4 | `backend/src/test/.../CapasArchTest.kt:47-55` | **alto** | ADR-0008 D3 | **HUECO DE REGLA con violación real asociada (H-5).** La regla `application no depende de infrastructure` analiza bytecode; el import de H-5 solo sirve para leer un `const val`, que **Kotlin inlinea en el sitio de uso**. El agente lo verificó con `javap -v` sobre `AuditEventListener.class`: el constant pool **no contiene ninguna referencia a `infrastructure`**, solo la cadena literal. Consecuencia general: **cualquier violación de capa vehiculada por `const val` es invisible a este guard.** |
| H-5 | `auditoria/application/listeners/AuditEventListener.kt:8`<br>`auditoria/application/listeners/AuditTrailAnonymizationListener.kt:4` | medio | ADR-0008 D3 | `application` importa una clase de `infrastructure` de su propio módulo (`import …auditoria.infrastructure.persistence.events.AuditoriaProcessedEventTracker` para leer `QUALIFIER`). **Es el único módulo que lo hace**: `clubtaxonomia`/`planificacion`/`seguimiento` resuelven el mismo `@Qualifier` con literal de cadena y no importan nada de infra. Invisible a CI por H-4. |
| H-6 | `backend/src/test/.../CapasArchTest.kt:32-45` | bajo | ADR-0008 D3 | La denylist de frameworks en `domain` **omite `org.hibernate..`, que el ADR exige explícitamente**, y tampoco cubre `jakarta.validation..`, `org.slf4j..` ni `io.micrometer..`. Sin violación actual. |
| H-7 | `identidad/IdentidadModule.kt`, `auditoria/AuditoriaModule.kt` | bajo | ADR-0007 D3, D8 | Los dos únicos módulos **sin `allowedDependencies`**. Sin allowlist, Modulith solo frena ciclos y accesos a internas: la dirección del DAG que fija D3 (identidad como raíz que no consume de nadie) no está declarada ni verificada para ellos. |
| H-8 | `backend/src/test/.../CapasArchTest.kt:11-13` | bajo | — | KDoc obsoleto: *"En H0 Bloque 2A todavía no hay módulos […] estas reglas pasan de forma vacía"*. Hoy las 4 sí seleccionan clases. Mantener esa frase invita a leer un fallo futuro como "regla vacía". |

### Hallazgos de código

| # | fichero:línea | sev. | ADR | hallazgo |
|---|---|---|---|---|
| H-9 | `auditoria/domain/AuditEvent.kt:20-21`; `clubtaxonomia/domain/audit/AuditEntry.kt:19-20`; `identidad/domain/audit/AuditEntry.kt:19-20`; `identidad/domain/events/UserInvited.kt:15`; `seguimiento/domain/DayAdjustment.kt:26,42` | medio | ADR-0008 D11 | `UUID` crudo como identificador en firmas y propiedades **de dominio**, donde debería ir un typed ID (`val actorId: UUID?`, `val sujetoId: UUID?`, `val operationId: UUID`). Excluidos del recuento el `val value: UUID` de los propios `value class` y los `eventId: UUID` (contrato de 6 campos, ADR-0007 D10). Nadie lo caza: ver H-1. |
| H-10 | `clubtaxonomia/.../AssignCoachToGroupCommand.kt:52-53` **y 32 ficheros más** (clubtaxonomia 25, planificacion 5, seguimiento 2, identidad 1, sobre 84 casos de uso) | medio | ADR-0008 D11 | Las firmas de `execute()` reciben `UUID` crudo y envuelven dentro (`fun execute(actor, groupId: UUID, coachId: UUID)` → `GroupId.of(groupId)`). **Severidad con confianza media**: el texto literal de D11 ata `domain` y sitúa la conversión *"en los bordes (controlador, mapeador)"* — un caso de uso no es un borde; `CLAUDE.md` dice *"nunca `UUID` sueltos"* sin matiz. Lo decisivo es que **el corpus es incoherente consigo mismo**: `identidad` sí usa typed IDs en `execute()` (`DeactivateUserCommand.kt:48 targetUserId: UserId`) y los otros cuatro módulos no. Conviene fijar el criterio. |
| H-11 | `shared/api/rest/config/GlobalRestExceptionHandler.kt` | bajo | ADR-0008 D2 | El paquete `api/` está reservado por el diagrama del ADR a contratos públicos entre módulos (`api/events/`); aquí aloja un `@RestControllerAdvice` de infraestructura web. Efecto colateral: la regla `api no depende de domain` empieza a aplicarse a código REST. |
| H-12 | `planificacion/api/{PersonalizedSession,PublishedSession,PublishedPersonalization}.kt` | bajo | ADR-0008 D2 | DTOs públicos colgando directamente de `api/` en vez de un sub-paquete; el diagrama del ADR solo contempla `api/events/`. Son los únicos ficheros de `api/` que no son eventos en los 4 módulos que lo tienen. |

---

## 8. Frontend (ADR-0001, ADR-0012) — ESTRUCTURALMENTE IMPECABLE, DEUDA EN ACCESIBILIDAD

**Todo lo estructural está limpio.** Los 7 hallazgos se concentran en accesibilidad (D6-D8) y cobertura de verificación (D7).

Comprobado, sin hallazgos:
- **Standalone + OnPush: 61/61** ficheros con `@Component` declaran `ChangeDetectionStrategy.OnPush` (incluidos los 70 helm de `src/app/ui/`). **0 `@NgModule`**, 0 `standalone: false`.
- **Signals sin NgRx**: `@ngrx/`, `createReducer`, `createEffect` no aparecen ni en `package.json` ni en el código. **Cero `BehaviorSubject`**; los 2 únicos `new Subject` son *plumbing* de `debounceTime`/`distinctUntilChanged`, no estado.
- **spartan.ng + Tailwind v4, sin Material**: `@angular/material` no existe. **`@spartan-ng/helm` no es dependencia** — los 15 alias de `tsconfig.json` resuelven a `./src/app/ui/*`, es decir, los helm **están copiados** como manda D1. `@angular/cdk` fuera de `ui/` solo como `DIALOG_DATA` en specs (autorizado por D2).
- **Cliente OpenAPI**: **cero `fetch(`**, cero `HttpClient` inyectado en features o servicios de dominio, cero URL `/api/...` construida a mano. Los interceptores usan `SesionService.XxxPath` del cliente generado, no strings.
- **Sin tokens en storage**: `localStorage`, `sessionStorage` y `document.cookie` **no aparecen en ningún sitio** salvo un comentario que documenta la prohibición. El CSRF va por `withXsrfConfiguration` de Angular (D13).
- **`/me/permissions` como UX, no barrera**: los 21 `can()` son `@if` de plantilla salvo uno que alimenta un `computed` de visibilidad. **Ningún `can()` gatea una petición, una navegación ni una rama de negocio.** Las rutas van protegidas aparte por guards.
- **i18n**: 225 `$localize` + 76 `i18n`/`i18n-*`. Barrido de nodos de texto de las 61 plantillas: 16 candidatos, **todos falsos positivos**. Cero literales de UI sin `$localize` en `.ts`. Identificadores en inglés, textos en castellano — ADR-0008 D4 cumplido.
- **Errores 4xx (D19)**: `error-codes.ts` traduce `code` → catálogo con cascada `code:field:message` → `code:message` → `code` → fallback. **El `message` del backend se usa solo como discriminante de clave, nunca se interpola en la UI.** Cero `error.message` en código de feature.
- **Patrones a11y correctos**: 0 `(click)` sobre `<div>/<span>/<li>/<td>/<tr>` (verificado con regex multilínea), las 3 `<img alt="">` son logos decorativos con texto adyacente, landmarks en ambos shells, un solo `<h1>` por página, y axe corre con `withTags(['wcag2a','wcag2aa','wcag21a','wcag21aa'])` **sin `disableRules` ni `.exclude()`**, escaneando también los diálogos abiertos.

### Hallazgos

| # | fichero:línea | sev. | ADR-0012 | hallazgo |
|---|---|---|---|---|
| F-1 | `frontend/e2e/` (ausencia) · `features/planificacion/pages/plan-detail.component.ts` | medio | **D7** | El **editor de plan semanal** — pantalla crítica **nombrada literalmente en D6** — no tiene test e2e ni escaneo axe. **Verificado**: los 11 specs de `e2e/` son activacion, club-ajustes, club-alumnos, club-entrenadores, club-grupos, club-salud, club-taxonomia, home, login, mi-cuenta, mi-plan; **ninguna ruta `/planificacion/**`**. Sí tiene 17 `it` de Jest, pero D7 exige axe en las pantallas de D6. |
| F-2 | `shared/layout/app-shell.component.ts:36`<br>`shared/layout/student-shell.component.ts:23` | medio | **D8** | **Ningún shell tiene skip link** al contenido principal, que D8 exige explícitamente. **Verificado**: cero coincidencias de `skip`/`saltar al contenido`/`sr-only` en `shared/layout/`. Ambos shells tienen `<header>` + `<nav>` antes del `<main>`, así que un usuario de teclado recorre toda la navegación en cada página. |
| F-3 | `report-dialog.component.ts:76-98` · `reschedule-dialog.component.ts:71-82` · `session-editor-dialog.component.ts:89` (+9 grupos más, 5 ficheros) | medio | D6 | 12 grupos de selección exclusiva exponen el estado «seleccionado» **solo con clases CSS**. **Verificado: `aria-pressed` no aparece ni una vez en todo el frontend.** Un lector de pantalla no puede saber qué opción está activa. Lo delata la inconsistencia dentro del mismo fichero: `report-dialog` hace lo correcto en la escala de valoración (`role="radiogroup"` + `aria-checked`, línea 112) y no en el grupo de estado. **axe no lo detecta** — por eso pasa la suite en verde. |
| F-4 | `e2e/club-ajustes.spec.ts:97` · `e2e/club-taxonomia.spec.ts:218` (únicos) | medio | **D8** | D8 pide tests e2e de teclado en las pantallas críticas. Los únicos 4 `page.keyboard` del repo están en dos pantallas que **no** son críticas de D6; **ninguna de las 6 críticas tiene recorrido de teclado**. |
| F-5 | `group-coaches-dialog.component.ts:77` · `group-membership-dialog.component.ts:124` · `personalizations-dialog.component.ts:132,214` | medio | D6 | 4 inputs cuyo **único nombre accesible es el `placeholder`**, que desaparece al escribir: sin `<label for>`, sin `aria-label`, sin `aria-labelledby` (WCAG 1.3.1 / 3.3.2). El `<h3>` adyacente no está asociado programáticamente. |
| F-6 | `e2e/` (ausencia) — 9 rutas | bajo | D7 | Sin e2e ni axe: `/coaches`, `/alertas`, `/mis-marcas`, `/planificacion/grupos/:id/planes`, `/entrar`, `/entrar-con-enlace`, `/restablecer`, `/restablecer/nueva`, `/cambiar-contrasena`. No son críticas de D6, de ahí la severidad baja. |
| F-7 | `src/**/*.spec.ts` (ausencia) | bajo | D7 | **Ningún test unitario con axe-core** para componentes reutilizables, que D7 pide (*"Para componentes reutilizables, test unitario con `axe-core` también"*). Los 4 `.spec.ts` que casan con «axe» son la variable `axes` (ejes de taxonomía): falso positivo. |

---

## 9. Coherencia del corpus de ADRs — DOS CASCADAS ABIERTAS Y UNA RECIÉN CREADA

**Método**: mapa de sub-decisiones por parsing de los 17 ADRs → 283 entradas; scanner de las ~340 referencias `ADR-0NNN Dn` contra ese mapa sobre corpus ADR + 3 `CLAUDE.md` + `docs/arquitectura/*` + `.claude/agents/*` + `.claude/skills/**`; cotejo de disparadores ADR-0015 ↔ ADR origen uno a uno; y contraste de cada afirmación *"lo verifica ArchUnit"* contra las 23 reglas `@ArchTest` reales.

Comprobado, sin hallazgos:
- **0 cruces `ADR-NNNN Dn` colgantes** sobre ~340 referencias en los 5 conjuntos. Scanner validado con fixture. Todos los rangos citados (`ADR-0011 D1-D24`, `ADR-0014 D24-D26`, …) verificados dentro de límites. *(Caveat: la forma `ADR-0015 A<n>` queda fuera de ese barrido por construcción — y ahí sí hay 2 errores, C9-18 y C9-19.)*
- **Contradicciones cruzadas candidatas verificadas y limpias**: ADR-0009 D17 ↔ ADR-0014 D6 (anonimización — resuelto con corrección expresa); ADR-0006 D24 ↔ ADR-0011 D7 (CloudWatch vs AMP/AMG — complementariedad con tabla de reparto explícita); ADR-0011 D13 ↔ ADR-0013 D9 (log levels); ADR-0003 D15 ↔ ADR-0009 D15-D17 (auditoría identidad vs autorización — con párrafo de deslinde).
- **Disparadores vs ADR-0015: cotejo numérico limpio.** 14 aplazamientos contrastados uno a uno contra su ADR origen (Multi-AZ, ECS Fargate, CloudFront, SES, Loki/Tempo, Sentry, Datadog, Slack/PagerDuty, Secrets Manager, CMK, Vault, DPO, export self-service, menores) — **sin divergencia de cifra en ninguno**. Las 3 entradas retiradas (i18n, WCAG, Tailwind) están correctamente en *Aplazamientos retirados* y no reaparecen.
- **Formato Nivel 1 y estados**: los 17 declaran `Aceptado`, coincidente con el README. **Correspondencia anchor ↔ heading perfecta en los 17** (12/12, 10/10, 16/16, 30/30, …). Todos con *Índice de sub-decisiones* y *Premisas heredadas*.
- **Premisas heredadas corregidas por `797d9b4` verificadas**: UUID v7 y Spring Boot 4.x, sin reaparición de las versiones viejas. La cadena de auto-corrección anidada de ADR-0016 D3 (target Java 21) está completa y coherente.
- **10 familias de guards ArchUnit afirmados y verificados como existentes** (ADR-0003 D10, ADR-0004 D4/D8, ADR-0007 D12, ADR-0008 D3/D4/D14, ADR-0009 D13, ADR-0013, ADR-0014). Y `ADR-0006:516` (`publicly_accessible`) está cubierto por la rama "política IaC" de la propia frase: `infrastructure/terraform/modules/database/tests/database.tftest.hcl:28`.

### Cascada 1 — ADR-0007 D13 (reintentos inexistentes): 13 hallazgos, 18 apariciones textuales

Origen verificado: `ADR-0007:385` *"Spring Modulith **no reintenta automáticamente con backoff**"*, y `:381` documenta la corrección expresa (2026-08-24) de que la afirmación contraria era **falsa**, comprobada descompilando `spring-modulith-events-core` 2.1.0. **Verifiqué 18 apariciones de la política derogada en el repo.** Ninguna de las dos PRs recientes tocó esta cascada.

| # | fichero:línea | sev. | hallazgo |
|---|---|---|---|
| C9-1 | `docs/adr/0005-email-transaccional.md:51,67,206` | **crítico** | Hereda el mecanismo derogado, **incluido en su tabla de NFR**: *"Reintentos por evento \| 5 con backoff exponencial (heredado de ADR-0007 D13)"*. Un NFR es un compromiso verificable; este es inverificable. |
| C9-2 | `docs/adr/0005-email-transaccional.md:286` | **crítico** | Mitiga un riesgo apoyándose en *"el endpoint admin de republicación (ADR-0007 D13)"*, que D13 declara **diferido a ADR-0015** y que no existe. Riesgo documentado como cubierto sin estarlo. |
| C9-3 | `docs/adr/0011-observabilidad.md:271` | **crítico** | **Alarma de producción sobre condición imposible**: `\| Eventos \| outbox_dlq_events (reintentos agotados, ADR-0007 D13) \| > 0 (cualquiera) \|`. Verificado literalmente. La señal real es `status='FAILED'` vía `spring.modulith.events.staleness`. Falsa red de seguridad operativa. |
| C9-4 | `docs/adr/0011-observabilidad.md:64` | alto | Misma premisa rota en el bloque de premisas heredadas. |
| C9-5 | `docs/adr/0009-modelo-de-autorizacion.md:59` | alto | Ídem, premisa heredada. |
| C9-6 | `docs/adr/0014-proteccion-de-datos-rgpd.md:201` | alto | Ídem, **en el mecanismo de propagación del borrado RGPD**. |
| C9-7 | `docs/arquitectura/estructura-de-un-modulo.md:398,417,583,589,769` | alto | La guía operativa principal documenta *"5 reintentos con backoff exponencial 1/2/4/8/16 s"* — una cadencia que el framework **nunca tuvo** — y la mete en el **checklist de creación de módulo**. 5 ubicaciones. |
| C9-8 | `docs/adr/0004-base-de-datos-postgresql.md:451` | medio | Cifra inventada: *"la política de reintento/DLQ de ADR-0007 D13 (5 intentos)"*. |
| C9-9 | `docs/adr/0010-pipeline-ci-cd.md:349` | medio | El catálogo de tests críticos **exige probar** el endpoint diferido. |
| C9-10 | `docs/arquitectura/persistencia.md:196-197` | medio | Ídem cadencia 1/2/4/8/16 s. |
| C9-11 | `.claude/skills/spring-modulith-debug/SKILL.md:59` | medio | La skill de diagnóstico **enseña a buscar un síntoma que no puede ocurrir**. |
| C9-12 | `docs/adr/0015-…md` (fila ausente) | medio | Aplazamiento sin entrada en el índice maestro: D13 dice *"diferido a ADR-0015"*, pero ADR-0015 **no tiene fila** para `POST /admin/events/republish`. |
| **G-2** | `docs/glosario.md:116-117` | **alto** | *(ya detallado en área 7)* — **la 14.ª ubicación de esta misma cascada**, en el documento declarado autoritativo. |

### Cascada 2 — residuo de `d26834c` (ADR-0006 D4, Spring Session JDBC)

La corrección más reciente dejó **tres contradicciones aguas abajo, una de ellas en el propio ADR-0006 y otra interna a ADR-0015**. Es exactamente el fallo que el commit pretendía cerrar.

| # | fichero:línea | sev. | hallazgo |
|---|---|---|---|
| C9-13 | `docs/adr/0006-…md:528` | **alto** | Las **Notas del propio ADR-0006** contradicen a su D4 corregido 350 líneas más arriba: *"ElastiCache (Redis) para sesión compartida al activar min ≥ 2 (D4)"* vs `D4:177` *"Subir min a 2 o más **no exige ningún cambio de sesión**"*. |
| C9-14 | `docs/adr/0015-…md:61` vs `:115` | **alto** | **Contradicción interna de ADR-0015**: A2 dice *"implica activar Redis ya para sesión"*; su propia tabla maestra dice *"JDBC … válido con min ≥ 2 tal cual … no un paso obligado del escalado"*. |
| C9-15 | `docs/adr/0017-…md:40` | alto | Premisa heredada rota: *"Con min ≥ 2 la sesión migraría a Redis"*. |
| C9-16 | `.claude/skills/disparador-checker/SKILL.md:48` | alto | **El checker dispararía en falso** al llegar a `min ≥ 2`: conserva el disparador derogado. |

### Cascada 3 — residuo de `797d9b4` y otros

| # | fichero:línea | sev. | ADR | hallazgo |
|---|---|---|---|---|
| C9-17 | `docs/adr/0014-…md:184` | **alto** | ADR-0014 D6 ↔ ADR-0004 D11/D16, ADR-0007 D13 | D6 cat. 4 mantiene la **reescritura activa de payload** que ADR-0004 y ADR-0007 retiraron por no existir. D10 sí se corrigió el 2026-09-19; **D6 se quedó fuera**. Describe una anonimización que nadie implementa, en el mecanismo DSAR. |
| C9-18 | `docs/adr/0006-…md:481` | medio | ADR-0015 A2/A3 | Cruce a sub-decisión equivocada: zonas horarias es **A3**, A2 es caché de aplicación. |
| C9-19 | `docs/adr/0006-…md:177` | medio | ADR-0015 A2 | Cruce impreciso **introducido por `d26834c`**: A2 es caché; la entrada de sesión vive solo en la tabla maestra. |
| C9-20 | `0011:6` · `0012:6,299` · `0014:6` · `0010:350` | medio | ADR-0008 D12 | Terminología derogada `Result<T, DomainError>` superviviente a `797d9b4`: el commit la corrigió en el cuerpo pero **no en las cabeceras "Relacionado con" ni en D14 del frontend**. |
| C9-21 | `docs/arquitectura/estructura-de-un-modulo.md:141` | medio | ADR-0008 D11 (es **D12**) | Cruce mal atribuido en la guía principal: D11 es *Typed IDs*; el manejo de errores es D12. |
| C9-22 | `docs/adr/0008-…md:316,642` | medio | ADR-0008 D11 | *"Lo verifica ArchUnit"* **sin guard** — es el mismo hueco que H-1, visto desde el corpus. |
| C9-23 | `docs/adr/0008-…md:646` | medio | ADR-0008 D15 | *"Lo verifica ArchUnit"* sin guard: la regla real verifica lo **contrario** (que el anotado autorice), no que toda clase pública lleve `@ApplicationService`. El cuerpo (`:443`) sí matiza *"Test ArchUnit posible"*; la tabla de tests críticos lo afirma como hecho. |
| C9-24 | `0002:389,403,406` · `0003:383` | medio | ADR-0002 D6/D7 · ADR-0003 D13 | **4 guards ArchUnit afirmados que no existen** entre las 23 reglas reales (enum `Distancia` único, privacidad de `marca_alumno`, `metadata` JSONB, *"no `equals` con tokens"*). ADR-0004 D8 usa el patrón honesto (*"no existe todavía — pendiente"*); estos no. |
| C9-25 | `0015:154` + `0012` D10 | medio | ADR-0012 D10 | **Aplazamiento ya disparado** que sigue en la tabla maestra: `features/club/` y `features/planificacion/` existen con sus `*.routes.ts`; solo falta `layouts/`. Además el nombre `salud/` quedó obsoleto (esquema canónico `seguimiento`). |
| C9-26 | `docs/adr/0006-…md:388` | bajo | ADR-0006 D24 | Estado obsoleto: *"ADR-0011 (pendiente)"* — Aceptado desde 2026-05-22. ADR-0009 corrigió esta misma cita el 2026-06-12; ADR-0006 no. |
| C9-27 | `0015:117` vs `0006:225` | bajo | ADR-0006 D9 | Disparador con dos ramas en el origen (*"> 99,5 % **o el segundo club**"*), una sola recogida en ADR-0015. |
| C9-28 | `.claude/agents/idor-hunter.md:3,152` · `docs/arquitectura/testing-de-modulos.md:380` | bajo | ADR-0008 D12 / ADR-0009 D12 | Tooling y guía con la API derogada (`Result.Forbidden`; lo vigente es `Either.Left(XxxError.Forbidden)`). |
| C9-29 | `docs/adr/0005-…md:118-260` | bajo | — | Desviación de formato Nivel 1 que `797d9b4` decía haber alineado: único ADR con sub-decisiones a nivel `####` bajo agrupadores `###` y ancla **inline**. |
| C9-30 | `docs/adr/README.md` (fila 0004) | bajo | — | Título del índice ≠ título del fichero (*esquema* vs *schema*). |
| C9-31 | `docs/adr/index.md:9` | bajo | — | Landing de log4brains congelada: *"Los 16 ADR del corpus inicial"*, nunca menciona ADR-0017. |

---

## Plan de corrección sugerido (por orden de valor)

Nada de esto se ha aplicado — auditoría de solo lectura. Si se aborda, cada fix va en su propia rama `feature/{tipo}-{slug}` y PR.

| Orden | Qué | Tarea | Cierra | Coste |
|---|---|---|---|---|
| **1** | Inyectar `CoachGroupLookup` en `GetPlanQuery` + `ensure(isCoachOfGroup(...))` tras el `ensureNotNull`, **y el test D14 de dos entrenadores del mismo club** | `LAL-139` | `A-1`, `A-2` | bajo — **es el único bloqueante** |
| **2** | Decidir (jurídico) si la marca es dato art. 9; según la respuesta, añadir el gate de consentimiento **o** reclasificar en ADR-0014 D5 + migración | `LAL-140` | `R-1` | bajo técnico, requiere decisión |
| **3** | Una PR de revisión de ADR que barra la cascada D13 (13 hallazgos sobre 18 apariciones textuales), empezando por la alarma de `0011:271` | `LAL-141` | `C9-1`…`C9-12`, `G-2` | medio — 9 ficheros, un solo criterio |
| **4** | Job `@Scheduled` de purga a 12 meses para los dos `evento_auditoria` | `LAL-142` | `R-2` | bajo — el patrón ya existe 3 veces |
| **5** | Cerrar el residuo de `d26834c` (4 sitios, uno interno a ADR-0015, uno en el checker de disparadores) | `LAL-143` | `C9-13`…`C9-16` | bajo |
| **6** | `markIfNew` en los 3 email listeners de `identidad` | `LAL-144` | `E-1` | bajo — evita emails duplicados |
| **7** | Arreglar los guards agujereados: `MODULE_SCHEMAS` (`club_taxonomia`→`clubtaxonomia`), extender `SchemaFronterasArchTest` al SQL en `const val`, crear el guard de typed IDs de D11, y añadir las 4 reglas de eventos (schema por evento, test por schema, `markIfNew`, `restore`+`finally`) | `LAL-145` | `H-1`,`H-2`,`H-3`,`H-4`, huecos de área 4 | medio — **el de mayor valor a largo plazo** |
| **8** | Actualizar `docs/glosario.md` (`@AuthScope` verifica ≠ inyecta, premisa de idioma, tablas de PII inexistentes) | `LAL-146` | `G-1`,`G-3`,`G-5`,`G-6` | bajo |
| **9** | Corregir la lista de PII primaria en **ADR-0014 D5** (`seguimiento.alumno_perfil` y `seguimiento.marca` no existen) | `LAL-147` | `G-4`,`R-6`,`R-7` | bajo — impacta al derecho al olvido |
| **10** | Accesibilidad: skip links, `aria-pressed` en los 12 grupos, labels en los 4 inputs, e2e+axe del editor de plan | `LAL-148` | `F-1`…`F-5` | medio |
| **11** | Restante documental y cosmético | `LAL-138` | resto | bajo |
