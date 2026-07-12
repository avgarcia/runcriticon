# ADR-0009 — Modelo de autorización: RBAC + autorización a nivel de objeto

- **Estado**: Aceptado
- **Fecha**: 2026-05-22 · revisado 2026-05-29 (reorganización Nivel 1: premisas heredadas, NFRs propios, sub-decisiones numeradas D1-D19 con anchors; incorporación de: política frente a proyección stale, patrón de listados con aspecto, errores como `Result.Forbidden`, garantía arquitectónica con ArchUnit, alcance concreto de auditoría, módulo `auditoria` dedicado, endpoint `/me/permissions` como ayuda de UX, decisión consciente de aplazar el rol de soporte interno) · **aceptado 2026-05-29** · revisado 2026-06-12 (**D2**: anotaciones propias `@Authorize`/`@NoAuthRequired` evaluadas contra la `AuthorizationMatrix` en lugar de `@PreAuthorize` de Spring Security — alinea el ADR con la implementación H0 y elimina el SpEL; nota de SpEL en Notas sustituida; citas corregidas: ADR-0004 D7→D4 (esquema por módulo), ADR-0011 ya Aceptado; sin cambio en el modelo de tres capas) · revisado 2026-07-05 (**D11**: de "aspecto que inyecta predicados en la query" a **filtro por firma + aspecto verificador fail-closed** — el método `@AuthScope(CLUB)` recibe `clubId` y filtra en su propia query; el aspecto `AuthScopeEnforcementAspect` verifica en runtime que ese `clubId` coincide con `principal.clubId` y falla cerrado si no hay principal, falta el parámetro o no coincide; scopes sin verificación implementada (`OWNED`, `GRUPOS_DEL_ENTRENADOR`, `MIS_GRUPOS`) fallan cerrado hasta implementarse; ArchUnit exige el parámetro `clubId: UUID` en todo `@AuthScope(CLUB)` — corrige divergencia doc↔código detectada en revisión 2026-07-03, LAL-59; **D13**: añadida opción (d) `@AuthenticatedOnly(justificacion)` para casos de uso/handlers que requieren sesión activa sin regla de matriz aplicable (`QueryCurrentSession`/`SessionController.current()`, LAL-37); sin cambio en el modelo de tres capas) · **revisado 2026-07-11** (**D12**: la premisa heredada de ADR-0008 D11/D12 cambió de `Result<T, DomainError>` a `Either<XxxError, T>` — D12 de este ADR se reescribe (`Either.Left(XxxError.Forbidden)`, `data object` sin parámetro de razón, verificado contra `IdentidadError.kt`); D9 y D14/D15 actualizan sus citas en cascada. `ProjectionStale` queda como variante convenida para módulos consumidores de proyecciones (`identidad` no la necesita — solo publica eventos). Sin cambio de código)
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0003 (autenticación, principal, auditoría de identidad), ADR-0004 (base de datos, esquema por módulo), ADR-0006 (`club_id`), ADR-0007 (monolito modular, events-first, política de fallos), ADR-0008 (hexagonal y DDD, `Either<XxxError, T>`), ADR-0010 (observabilidad mínima), ADR-0014 (RGPD)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre autorización. Las diecinueve sub-decisiones se agrupan en seis áreas:

- **Modelo y capas (D1-D4)** — las tres capas (RBAC + nivel de objeto + `club_id`) y dónde vive cada una.
- **Topología y reglas (D5-D9)** — autorización por módulo (sin módulo central), núcleo compartido, servicio por módulo, proyecciones locales y política frente a proyección stale.
- **Patrón de listados y errores (D10-D12)** — listados filtrados en query, filtro por firma verificado por aspecto fail-closed, errores como `Either.Left(XxxError.Forbidden)`.
- **Garantías arquitectónicas (D13-D14)** — ArchUnit obligatorio + tests de acceso cruzado por caso de uso.
- **Auditoría (D15-D17)** — alcance, emisión asíncrona y módulo `auditoria` dedicado.
- **UX y operación (D18-D19)** — endpoint `/me/permissions` y aplazamiento consciente del rol de soporte interno.

| #   | Sub-decisión                                                                       | Capa         |
|-----|------------------------------------------------------------------------------------|--------------|
| D1  | [Tres capas: RBAC + nivel de objeto + `club_id`](#d1)                              | Estratégica  |
| D2  | [RBAC con anotación propia `@Authorize` en el adaptador de entrada](#d2)           | Operativa    |
| D3  | [Nivel de objeto en la capa de aplicación](#d3)                                    | Estratégica  |
| D4  | [Filtrado por `club_id` sistemático en los repositorios](#d4)                      | Operativa    |
| D5  | [Autorización por módulo; sin módulo central de autorización](#d5)                 | Estratégica  |
| D6  | [Núcleo compartido: principal + primitivas + matriz fija](#d6)                     | Estratégica  |
| D7  | [Servicio de autorización por módulo](#d7)                                         | Operativa    |
| D8  | [Proyecciones locales de relaciones alimentadas por eventos](#d8)                  | Estratégica  |
| D9  | [Política frente a proyección stale: fail-closed con timeout](#d9)                 | Operativa    |
| D10 | [Listados filtrados en query, nunca en memoria](#d10)                              | Operativa    |
| D11 | [Filtro `@AuthScope` por firma, verificado fail-closed por aspecto](#d11)          | Operativa    |
| D12 | [Errores de autorización como `Either.Left(XxxError.Forbidden)`](#d12)             | Operativa    |
| D13 | [ArchUnit obligatorio: todo `@ApplicationService` autoriza](#d13)                  | Operativa    |
| D14 | [Tests de acceso cruzado obligatorios por caso de uso](#d14)                       | Operativa    |
| D15 | [Alcance: denegaciones siempre, accesos a salud y perfil personal](#d15)           | Estratégica  |
| D16 | [Emisión asíncrona vía evento + outbox](#d16)                                      | Operativa    |
| D17 | [Módulo `auditoria` dedicado, esquema propio](#d17)                                | Operativa    |
| D18 | [Endpoint `GET /me/permissions` como ayuda de UX, no barrera](#d18)                | Operativa    |
| D19 | [Sin rol de soporte interno en MVP (aplazamiento consciente)](#d19)                | Estratégica  |

## Contexto y problema

El ADR-0003 resuelve la **autenticación** — probar quién es el usuario. Falta decidir la **autorización**: una vez dentro, qué operaciones puede ejecutar cada usuario y **a qué datos concretos** puede acceder.

Sin un modelo explícito el riesgo es doble:

- Operaciones ejecutadas por quien no debe (un alumno publicando un plan).
- Más sutil y más grave: un usuario accediendo a **datos de otro** — un alumno viendo el perfil de otro alumno. Es la vulnerabilidad **nº 1 del OWASP API Security Top 10**: *Broken Object-Level Authorization* (IDOR). Con datos de salud sensibles (RGPD), un fallo aquí es serio.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Tres roles fijos y conocidos** `ADMIN` / `ENTRENADOR` / `ALUMNO`, **un único rol por usuario en MVP** (ADR-0003 D2). El multi-rol queda fuera de este ADR.
- **Principal viene de Spring Security + Spring Session** (ADR-0003 D10). No hay JWT ni cabeceras propias. El principal es `(userId, clubId, rol)`.
- **Revocación inmediata de sesión** (ADR-0003 D11): si una cuenta queda comprometida o desactivada, el siguiente *gate check* la corta. La autorización vuelve a partir del nuevo principal.
- **Auditoría de eventos de identidad ya existe** (ADR-0003 D15): tabla `identidad.evento_auditoria` con login, magic link, cambio de contraseña, etc. **Este ADR audita cosa distinta**: accesos a datos y denegaciones. Las dos auditorías viven en sitios distintos y se consultan por separado.
- **Spring Modulith + events-first** (ADR-0007 D6, D8). Habilita proyecciones locales sin acoplamiento síncrono entre módulos.
- **Política de fallos sobre outbox** (ADR-0007 D13): 5 reintentos; tras agotarse, DLQ implícita en `event_publication` + alarma + republicación admin. Aplica a los eventos de relación que alimentan las proyecciones de autorización (D8).
- **Hexagonal + DDD con `Either<XxxError, T>`** (ADR-0008 D11/D12): los fallos cruzan capas como `Either` (Raise DSL de Arrow-kt), no como excepciones. `XxxError` es un sealed class propio de cada módulo — sin tipo de error compartido entre módulos.
- **PostgreSQL un esquema por módulo** (ADR-0004 D4): el filtro por `club_id` se aplica esquema por esquema; no hay JOINs entre módulos.
- **Datos de salud sujetos a RGPD** (ADR-0014). Justifica la auditoría y el borrado mixto al ejercer el derecho al olvido.
- **Dashboard mínimo + alarmas en GitHub Actions / observabilidad** (ADR-0010 D22, ADR-0011): latencia y tasa de denegaciones de autorización son métricas a vigilar.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| **Latencia añadida por la capa de autorización (p95)** | < **5 ms** por petición (decisión resuelta contra proyecciones locales) |
| **Falsos positivos de autorización** | **0 %** — un usuario nunca ve datos de otro, ni por race condition |
| **Ventana de inconsistencia eventual aceptable** (cambio de relación → autorización actualizada) | < **30 s p95** en estado estable |
| **Umbral de fail-closed por proyección atrasada** | **60 s** sin convergencia → se deniega + alarma (D9) |
| **Tasa de denegaciones en producción** | < **1 %** de las peticiones autenticadas en régimen estable |
| **Umbral de alarma por denegaciones** | pico sostenido **> 5 %** durante > 5 min → alarma (señal de escaneo o bug introducido) |
| **Tamaño del log de auditoría de autorización** | retención fijada por ADR-0014; orden de magnitud esperado: < 5 GB/año al volumen de la beta |

## Drivers de la decisión

- Datos de salud sensibles → **minimizar quién ve cada ficha**; cumplir RGPD, incluida la *responsabilidad proactiva* (poder demostrar quién accedió).
- Hay que impedir el **acceso transversal a objetos de otros usuarios**, no solo restringir operaciones por rol.
- Coherencia con la arquitectura hexagonal y el monolito modular *events-first* (ADR-0007/0008).
- Equipo pequeño → modelo **simple y sistemático**, sin un motor de políticas pesado.
- Preparación multi-club: aislamiento por `club_id` desde el día 1 (ADR-0006).
- **Imposibilidad de olvidar autorizar** debe ser una propiedad del código, no del proceso humano.

## Opciones consideradas

- **Opción A** — RBAC + autorización a nivel de objeto, en capas.
- **Opción B** — Solo RBAC (control por rol).
- **Opción C** — ABAC / motor de políticas configurable (p. ej. OPA, Cerbos).

### Opción A — RBAC + autorización a nivel de objeto

Control "grueso" por rol **más** comprobación, para cada objeto concreto, de la relación entre quien pide y el objeto.

- 👍 Cubre las dos preguntas: qué operaciones (rol) y a qué datos (relación).
- 👍 Cierra la vulnerabilidad IDOR.
- 👍 Simple: con 3 roles fijos el RBAC es trivial; las reglas de relación son pocas.
- 👎 Exige disciplina: la comprobación a nivel de objeto hay que aplicarla **sistemáticamente** en cada caso de uso. Se mitiga con ArchUnit (D13).

### Opción B — Solo RBAC

- 👍 Lo más simple.
- 👎 No distingue entre dos usuarios del mismo rol → **no impide que un alumno vea el perfil de otro**. Deja abierta la vulnerabilidad IDOR. Insuficiente.

### Opción C — ABAC / motor de políticas configurable

- 👍 Muy flexible: reglas dinámicas por atributos, externalizadas.
- 👎 Sobredimensionado para 3 roles fijos; un servicio o librería más que aprender, desplegar y operar; complejidad que un MVP con equipo pequeño no justifica.

## Decisión

**Opción A: RBAC + autorización a nivel de objeto, aplicadas en tres capas.** Las diecinueve sub-decisiones desarrolladas a continuación. Siete son **estratégicas** (D1, D3, D5, D6, D8, D15, D19 — modelo, topología, fuente de relaciones, alcance de auditoría y posición sobre soporte interno); el resto son **operativas** y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — Tres capas: RBAC + nivel de objeto + `club_id`

La autorización se enforce en tres capas concéntricas, cada una más cercana al dato:

- **Capa 1 — RBAC (por rol)**: responde a *"¿este rol puede ejecutar esta operación?"*. Es el control grueso, la primera reja.
- **Capa 2 — Nivel de objeto**: responde a *"¿puede este usuario concreto tocar este objeto concreto?"*. Es la capa que cierra IDOR. Incluye dos formas: comprobación al cargar un objeto suelto por su `id`, y filtrado al construir listados (ningún `findAll` puro — ver D10).
- **Capa 3 — Aislamiento por `club_id`**: toda consulta a datos se filtra por el `club_id` del principal. En MVP hay un solo club, pero la disciplina se aplica desde el día 1 (ADR-0006): un fallo puntual nunca podría cruzar datos entre clubes.

Las tres capas son **defensa en profundidad**: un fallo en una no implica fuga si las otras dos siguen activas.

<a id="d2"></a>
### D2 — RBAC con anotación propia `@Authorize` en el adaptador de entrada

Cada handler público de un controlador declara su regla RBAC con la anotación propia `@Authorize("RECURSO:ACCION")` del núcleo compartido (`com.runcriticon.shared.autorizacion`, D6) o, si el endpoint es deliberadamente público (health check, login), con `@NoAuthRequired(justificacion = "...")`. La expresión referencia una entrada de la `AuthorizationMatrix` (D6), que resuelve la decisión antes de tocar el caso de uso. Es declarativo, barato y vive en el lugar correcto (primera reja). ArchUnit exige que todo handler lleve una de las dos anotaciones (D13): ningún endpoint queda sin decisión explícita.

**No se usa `@PreAuthorize` de Spring Security** (revisión 2026-06-12; la redacción original de este D2 lo prescribía). Razones del cambio:

- **La matriz queda en un único sitio**: con `@PreAuthorize` las reglas RBAC se dispersan en SpEL por los controladores; con `@Authorize` la anotación solo *referencia* la `AuthorizationMatrix` (D6), que sigue siendo la única fuente de la política.
- **Elimina de raíz el riesgo de SpEL** que la redacción original ya señalaba como su propia debilidad: no hay lógica embebida en strings que un rename rompa en silencio. La expresión `"RECURSO:ACCION"` es un identificador de entrada en la matriz, no código.
- **Verificable estructuralmente**: la presencia de `@Authorize`/`@NoAuthRequired` es una regla ArchUnit trivial (D13); la disciplina de SpEL "solo métodos tipados" dependía de revisión humana.

Riesgo residual: la expresión sigue siendo un string que el compilador no verifica. Mitigación: el motor de evaluación (en H0 existe el contrato y la verificación estructural; el motor llega en Fase 1) trata cualquier expresión que no exista en la matriz como denegación — fail-closed, coherente con la postura de D9 — y los tests de acceso cruzado (D14) cubren los endpoints.

<a id="d3"></a>
### D3 — Nivel de objeto en la capa de aplicación

El nivel de objeto vive en los **casos de uso** (`@ApplicationService`, ADR-0008 D7), no en el controlador: el caso de uso tiene el contexto de dominio para decidir si quien pide puede tocar el objeto, y es donde naturalmente se cargan los agregados.

Cada caso de uso que carga, modifica o devuelve un objeto invoca al **servicio de autorización del módulo** (D7) antes de la operación. La invocación es explícita y verificable arquitectónicamente (D13).

<a id="d4"></a>
### D4 — Filtrado por `club_id` sistemático en los repositorios

Toda query del repositorio incluye el `club_id` del principal en su `WHERE`. Ningún método de repositorio acepta una consulta sin `club_id`. Es defensa en profundidad: aunque las capas 1 y 2 fallen, los datos no salen del club.

Implementación: el propio método del repositorio recibe `clubId` como parámetro y lo aplica en su `WHERE` (filtro por firma). El aspecto del D11 no inyecta el predicado — **verifica en runtime** que ese `clubId` coincide con el del principal, y falla cerrado si no. Las consultas que necesitan saltarlo (raras y siempre administrativas) declaran `@NoAuthScope` explícitamente y se cubren con tests específicos.

<a id="d5"></a>
### D5 — Autorización por módulo; sin módulo central de autorización

La autorización se enforce en **cada módulo, sobre sus propios recursos**, usando sus **proyecciones locales** de los datos de relación. **No hay un módulo central de autorización** al que llamar — sería el acoplamiento síncrono que events-first (ADR-0007) descarta.

Lo común vive en un **núcleo compartido** ligero (D6); las reglas concretas de relación viven en cada módulo (D7).

<a id="d6"></a>
### D6 — Núcleo compartido: principal + primitivas + matriz fija

Un *shared kernel* pequeño contiene:

- El **principal** (`userId`, `clubId`, `rol`) y la forma de obtenerlo de la sesión actual.
- Las **primitivas de decisión** (`puedeRol(rol, recurso, accion)`, `esDuenoDe(userId, objeto)`, etc.).
- La **matriz fija** rol × recurso × acción ([matriz de visibilidad](#matriz-de-visibilidad)) codificada como datos en el núcleo compartido.

La matriz vive aquí, no en un módulo en runtime, porque **no cambia** durante la vida del MVP. Si pasa a ser configurable, se reabre con un módulo central (ver D19 y Notas).

<a id="d7"></a>
### D7 — Servicio de autorización por módulo

Cada módulo tiene un `AutorizacionService` (vive en `domain` como puerto, implementación en `application` o `domain` según naturaleza — ADR-0008) que centraliza las reglas de relación específicas del módulo:

- *"¿este entrenador es responsable de este alumno?"*
- *"¿este plan pertenece a un grupo del entrenador?"*
- *"¿este reporte fue creado por este alumno?"*

El caso de uso invoca al `AutorizacionService` del módulo, no a las primitivas del núcleo compartido directamente. Esto evita duplicar reglas dentro del módulo y facilita testearlas en un solo sitio.

<a id="d8"></a>
### D8 — Proyecciones locales de relaciones alimentadas por eventos

Los datos de relación necesarios para autorizar (qué alumno está en qué grupo, qué grupo es de qué entrenador, etc.) son **propiedad del módulo Club y taxonomía**. Cualquier otro módulo que autorice contra esas relaciones mantiene una **proyección local**, alimentada por eventos de dominio (`AlumnoAsignadoAGrupo`, `EntrenadorAsignadoAGrupo`, `UsuarioCambioDeRol`, etc.).

**No se consulta de forma síncrona** al módulo dueño — sería el acoplamiento que events-first descarta (ADR-0007).

La política de fallos del outbox (ADR-0007 D13) aplica: si un listener falla 5 veces, el evento queda en DLQ y dispara alarma. Mientras tanto, la proyección queda atrasada — escenario cubierto por D9.

<a id="d9"></a>
### D9 — Política frente a proyección stale: fail-closed con timeout

La proyección local de relaciones es **eventualmente consistente**. Cada proyección expone un *lag* (segundos desde el último evento procesado vs el último evento publicado por el módulo origen).

- **Lag < 30 s p95**: se autoriza con la proyección tal cual. Es la ventana normal.
- **Lag ≥ 60 s sin convergencia**: la comprobación a nivel de objeto del módulo deniega cualquier decisión que dependa de esa relación, devolviendo `Either.Left(XxxError.ProjectionStale)`, y dispara alarma operativa.
- Las consultas que **no dependen** de la proyección stale (otras relaciones, otros recursos) siguen funcionando con normalidad.

Razón: con datos de salud, autorizar contra una proyección atrasada >60 s es preferible interrumpirlo a falsos positivos. La alarma fuerza la atención humana; la mayoría de retrasos legítimos (despliegues, reinicios) se resuelven en mucho menos.

<a id="d10"></a>
### D10 — Listados filtrados en query, nunca en memoria

Los endpoints que devuelven listas (*"mis alumnos"*, *"planes del club"*) **no traen todo y filtran en la UI o en memoria** — eso es una fuga esperando a ocurrir. La consulta se construye **ya acotada al alcance del principal**: solo se materializan las filas que el principal puede ver.

Aplica a todos los listados sin excepción. El día que aparezca un caso aparentemente válido para traer-y-filtrar (paginación complicada, vista admin), se trata como excepción explícita marcada y revisada — nunca como atajo.

<a id="d11"></a>
### D11 — Filtro `@AuthScope` por firma, verificado fail-closed por aspecto

El filtrado **no** lo inyecta un aspecto mágico en la query — lo aplica el propio método del repositorio, que recibe `clubId` (u otro predicado de relación) como **parámetro explícito de su firma** y lo usa en su `WHERE`. Un aspecto ligero **verifica** esa firma en runtime, no la construye:

- Todo `@Repository` está bajo el aspecto (`AuthScopeEnforcementAspect`, `shared.autorizacion.spring`) por defecto.
- Para métodos anotados `@AuthScope(Scope.CLUB)`: el aspecto resuelve el principal de la sesión actual (vía `PrincipalProvider`) y compara el argumento `clubId` recibido con `principal.clubId`. Si no hay principal, el método no declara un parámetro `clubId: UUID`, o el valor no coincide, el aspecto **falla cerrado** lanzando `AuthScopeViolationException` (403) — nunca deja pasar la llamada.
- Para el resto de scopes (`OWNED`, `GRUPOS_DEL_ENTRENADOR`, `MIS_GRUPOS`): mientras no exista una verificación equivalente implementada, el aspecto también **falla cerrado** en cuanto detecta el scope — un método no puede declarar un `@AuthScope` que nadie hace cumplir.
- Métodos del repositorio **sin** `@AuthScope` quedan rechazados por ArchUnit (D13) salvo que estén explícitamente marcados `@NoAuthScope` (raros, siempre administrativos, justificados).

La responsabilidad del filtrado real recae en quien escribe la query (revisión de PR + tests de acceso cruzado, D14); el aspecto es una **red de seguridad en runtime**, no el mecanismo de filtrado. Se compensa con tres garantías:

- ArchUnit detecta métodos de repositorio sin anotación de scope, y exige que todo `@AuthScope(Scope.CLUB)` declare un parámetro `clubId: UUID` (si no, el aspecto no tendría qué verificar).
- Tests de integración con Testcontainers que verifican que el aspecto corta la llamada cuando el `clubId` del argumento no coincide con el del principal (y que una llamada legítima con el `clubId` del propio principal pasa).
- Log de auditoría registra los accesos con `@NoAuthScope` siempre (señal de revisión).

<a id="d12"></a>
### D12 — Errores de autorización como `Either.Left(XxxError.Forbidden)`

Coherente con ADR-0008 D11/D12. La comprobación de la matriz vive **inline en el caso de uso**, dentro del bloque `either { }` (Raise DSL de Arrow-kt) — no hay un `AutorizacionService` separado que envuelva `AuthorizationMatrix`:

```kotlin
either {
    ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.INVITE)) { IdentidadError.Forbidden }
    // ...
}
```

`IdentidadError.Forbidden` es un `data object` **sin parámetro de razón** — verificado contra el módulo `identidad`. La razón de la denegación (qué regla de la matriz faltó, qué comprobación a nivel de objeto falló) se deja en el log de auditoría (`AccesoDenegado`, D9), no en el propio error de dominio: el error solo necesita distinguir el caso para el `when` exhaustivo del adaptador REST.

`ProjectionStale(modulo, lag)` (D9, `fail-closed` a 60 s) es una variante **convenida para módulos consumidores de proyecciones** (`CLAUDE.md` raíz) — `identidad` no la tiene porque solo publica eventos, no consume proyecciones de otros módulos; el primer módulo que sí consuma (`club_taxonomia`, `planificacion`, …) la añadirá a su propio `XxxError`.

El caso de uso propaga el `Either.Left(XxxError.Forbidden)` hacia arriba; el adaptador REST lo traduce a **HTTP 403 con cuerpo neutro** (sin revelar la razón al cliente: solo al log de auditoría). La razón concreta se persiste en el log para investigación posterior.

Las excepciones (`RuntimeException`, etc.) quedan reservadas para errores del framework, no para flujo de autorización.

<a id="d13"></a>
### D13 — ArchUnit obligatorio: todo `@ApplicationService` autoriza

Un test ArchUnit obligatorio en CI verifica:

- **Cada método público de una clase `@ApplicationService`** tiene **al menos uno** de: (a) anotación `@Authorize(...)`, (b) llamada explícita al `AutorizacionService` del módulo (verificada a nivel de clase: la clase, o alguna de sus clases anidadas, accede a la `AuthorizationMatrix`), (c) anotación `@NoAuthRequired` con justificación en comentario, (d) anotación `@NoAuthRequired`/`@AuthenticatedOnly(justificacion)` a nivel de clase.
- **`@AuthenticatedOnly(justificacion)`** cubre el caso que no encaja en (a)-(c): el caso de uso o handler **requiere sesión activa** (la garantiza la `SecurityFilterChain`) pero **no hay ninguna regla de la matriz aplicable** — solo devuelve o gestiona la propia sesión del llamador, sin tocar ningún `Recurso`/`Accion` de terceros (ej. `QueryCurrentSession`, logout de la propia sesión). Distinta de `@NoAuthRequired`: aquí sí hace falta estar autenticado, solo que la matriz no tiene nada que decir. Requiere justificación igual que las otras exenciones.
- **Cada método de un `@Repository`** tiene `@AuthScope(...)` o `@NoAuthScope` (caso del D11); si el scope incluye `CLUB`, el método declara además un parámetro `clubId: UUID` (lo que el aspecto del D11 necesita para verificar).
- **No hay `HttpSession` directa**, **no hay acceso al `SecurityContext` fuera del núcleo compartido** (todo principal pasa por el shared kernel).

El test rojo bloquea el merge. Hace **imposible olvidar autorizar** un caso de uso nuevo — el primer olvido es una fuga, no podemos confiarlo a la revisión de PR sola.

<a id="d14"></a>
### D14 — Tests de acceso cruzado obligatorios por caso de uso

Para cada caso de uso que lee o modifica un objeto sujeto a nivel de objeto (D3), existe **al menos un test** que:

- Construye dos principales del mismo rol que no deberían cruzarse (dos alumnos, dos entrenadores con grupos distintos).
- Intenta que uno acceda al objeto del otro.
- Espera `Either.Left(XxxError.Forbidden)` o lista vacía según corresponda.

Estos tests se documentan en la estrategia de tests críticos del módulo correspondiente (mismo patrón que ADR-0003 §"Estrategia de tests críticos"). Sin estos tests, el módulo no se considera implementado.

<a id="d15"></a>
### D15 — Alcance: denegaciones siempre, accesos a salud y perfil personal

El log de auditoría de autorización registra:

- **Toda denegación** de autorización (`Either.Left(XxxError.Forbidden)`), incluido el caso stale (D9). Señal de seguridad: un pico es escaneo, un bug recién desplegado o reorganización del club.
- **Accesos a datos de salud**: lectura o modificación de ficha de alumno con marcas, sesiones reportadas, reportes de entrenamiento, lesiones, observaciones médicas.
- **Accesos a perfil personal de terceros**: lectura por un usuario del email, teléfono o dirección de **otro** usuario (el directorio interno del club). No se audita el acceso al propio perfil.

**No se registra** lectura trivial (listado público del club, taxonomía, plantillas). Solo lo que aporta responsabilidad proactiva del RGPD o señal de seguridad.

Distinción explícita frente a ADR-0003 D15: aquella audita **eventos de identidad** (login, magic link, cambios de contraseña, recuperación por admin); esta audita **accesos a datos y denegaciones**. Viven en módulos distintos (`identidad` vs `auditoria` — D17) y se consultan por separado.

<a id="d16"></a>
### D16 — Emisión asíncrona vía evento + outbox

La capa de autorización emite un evento de dominio (`AccesoDenegado`, `AccesoADatosSensibles`) en la **misma transacción** que la operación de negocio. El outbox de Spring Modulith (ADR-0007 D6) lo entrega al módulo `auditoria` (D17).

- Latencia añadida a la petición: despreciable (un INSERT en `event_publication` ya ocurre por la operación).
- Garantía: at-least-once por el outbox.
- Si Postgres falla al persistir el evento, **la operación de negocio también falla** (misma transacción) — no hay caso "operación sin auditoría".
- El módulo `auditoria` es **idempotente** ante reentregas del mismo evento (ADR-0007 D9).

<a id="d17"></a>
### D17 — Módulo `auditoria` dedicado, esquema propio

Se crea un módulo `auditoria` cuyo único trabajo es consumir los eventos `AccesoDenegado` / `AccesoADatosSensibles` (y otros eventos de auditoría que se decidan en el futuro) y persistirlos en su esquema `auditoria.evento`.

- **No es un módulo central de autorización**: no decide, no es invocado síncronamente, no propaga estado a los demás. Es un consumidor de eventos como cualquier otro (ADR-0007).
- Coherente con ADR-0004 D4 (esquema por módulo).
- Expone una API administrativa para consulta forense: filtros por `userId`, `clubId`, ventana temporal, tipo de evento.
- **RGPD**: cuando un usuario ejerce el derecho al olvido, las filas de `auditoria.evento` que lo mencionan se anonimizan (`actor_id` / `sujeto_id` → null), no se borran. Patrón de borrado mixto del ADR-0014.

<a id="d18"></a>
### D18 — Endpoint `GET /me/permissions` como ayuda de UX, no barrera

Endpoint que devuelve un mapa `{ recurso: [acciones] }` calculado server-side desde el mismo motor de autorización del usuario actual. El frontend lo usa para ocultar botones a los que el usuario no llegaría.

Reglas:

- Es **ayuda visual**, no barrera. La regla de oro (ver más abajo) sigue: cada petición se autoriza en el servidor, independientemente de lo que la UI muestre o esconda.
- La matriz vive en un único sitio (núcleo compartido, D6) y el frontend no la duplica. Cualquier cambio en la matriz se refleja sin tocar el frontend.
- No es secreto: un atacante puede inferir la misma información probando. No se gana ofuscación, se gana coherencia.

<a id="d19"></a>
### D19 — Sin rol de soporte interno en MVP (aplazamiento consciente)

El equipo de Runcriticon (Antonio + futuro equipo técnico) **no tiene rol propio en la aplicación** durante el MVP. La operación, soporte e investigación se realiza fuera de la aplicación (acceso directo a BD por DBA, logs de servidor, dashboard de observabilidad de ADR-0010).

Es una **decisión consciente de aplazamiento**, no un hueco. Razones:

- Introducir un rol `SOPORTE` con lectura transversal sobre todos los clubes requiere modificar las tres capas y la matriz desde el día 1 para un caso de uso que en mono-club es marginal.
- En MVP mono-club, Antonio puede coordinar con el admin del club piloto cuando haga falta intervención visible en la app.

**Disparadores que reabren la decisión**:

- Entra el segundo club piloto.
- Aparece la primera incidencia donde el soporte necesite ver datos de la app y el admin no esté disponible.

Mientras tanto, queda **prohibido** introducir workarounds (cuenta admin "interna" en cada club, *backdoor* en un endpoint, lectura de la BD desde la app con bypass): el día que aparezca el dolor, se diseña en limpio.

## Matriz de visibilidad

| Recurso / operación | admin | entrenador | alumno |
|---------------------|:-----:|:----------:|:------:|
| Gestionar club y taxonomía | ✅ | lectura (para usar tags) | ❌ |
| Alta de entrenadores | ✅ | ❌ | ❌ |
| Alta de alumnos | ✅ | ✅ (los suyos) | ❌ |
| Ver perfil de alumno | todos del club | **solo los de sus grupos** | **solo el suyo** |
| Crear / editar planes | ✅ | **edita los suyos** | ❌ |
| Ver planes | todos del club | **ve todos los del club** | el suyo publicado |
| Reportar una sesión | ❌ | ❌ | ✅ (las suyas) |
| Ver reportes de sesión | todos del club | de sus alumnos | solo los suyos |
| Vista de salud del club | ✅ | su parte (sus grupos) | ❌ |

Reglas de relación que sostienen la matriz:

- Un **alumno** solo accede a objetos cuyo dueño es él mismo.
- Un **entrenador** accede a los alumnos de **sus grupos** y a los reportes de esos alumnos; **ve** todos los planes del club pero **solo edita los que ha creado**.
- Un **admin** accede a todo lo de **su** club.

El detalle fino (p. ej. qué ve exactamente un entrenador en la vista de salud del club) se concreta al implementar cada funcionalidad; este ADR fija la política, no cada permiso.

## Regla de oro

La autorización se comprueba **siempre en el servidor, en cada petición**. Que la interfaz oculte un botón (D18) es comodidad visual, **no** seguridad: la API se puede llamar directamente. La UI nunca es la barrera.

## Consecuencias

### Positivas

- Cierra la vulnerabilidad IDOR — un usuario no puede acceder a objetos de otro, ni sueltos ni en listados.
- Defensa en profundidad real: las tres capas son independientes.
- Las operaciones quedan restringidas por rol de forma declarativa y simple.
- El aislamiento por `club_id` deja preparado el multi-club.
- Modelo proporcional al problema: sin motor de políticas que operar.
- La autorización por módulo respeta la autonomía de events-first; el núcleo compartido evita duplicar el principal y la matriz.
- ArchUnit (D13) hace imposible olvidar autorizar un caso de uso nuevo: el primer olvido no llega a producción.
- El aspecto del D11 hace imposible que un `clubId` equivocado en la firma pase desapercibido — falla cerrado en runtime, no depende solo de la revisión de PR.
- La auditoría asíncrona (D16) y el módulo `auditoria` dedicado (D17) cumplen la responsabilidad proactiva del RGPD sin acoplar.
- El endpoint `/me/permissions` (D18) mantiene la matriz en un único sitio y evita duplicación en el frontend.
- D19 evita meter en MVP un rol que cambiaría las tres capas para un caso de uso marginal.

### Negativas / coste asumido

- El aspecto verificador (D11) solo detecta un `clubId` incorrecto en runtime — si el desarrollador escribe la query sin aplicar el filtro pero pasa el `clubId` correcto como parámetro sin usarlo, el aspecto no lo detecta (no reconstruye la query). Mitigado por revisión de PR + tests de acceso cruzado (D14) + tests de integración que verifican que el filtro real se aplica.
- ArchUnit (D13) requiere mantenimiento: cada vez que aparece un tipo nuevo de caso de uso (`@QueryService`, `@CommandHandler`...), las reglas hay que ampliarlas.
- El módulo `auditoria` añade un módulo más al monolito modular.
- La matriz de visibilidad se concreta dentro de los módulos (cada uno autoriza sus recursos) — hay que mantener la coherencia con revisión.
- D19 implica que cualquier intervención en producción que necesite vista de aplicación pasa por el admin del club piloto; aceptable mientras sea mono-club.

### Riesgos y mitigaciones

- **Comprobación a nivel de objeto olvidada en un caso de uso** → ArchUnit (D13) + servicio de autorización centralizado por módulo (D7) + tests de acceso cruzado por caso de uso (D14).
- **Método `@AuthScope(CLUB)` sin parámetro `clubId` o con un `clubId` que no coincide con el principal** → el aspecto del D11 falla cerrado en runtime (`AuthScopeViolationException`); ArchUnit exige la firma en tiempo de compilación.
- **Datos de relación rancios** (un alumno cambia de grupo) → política de proyección stale (D9): fail-closed por encima de 60 s, alarma operativa.
- **Fuga entre clubes** → filtro por `club_id` sistemático en el acceso a datos (D4), escrito en la query y verificado por el aspecto del D11, como defensa en profundidad además de las capas 1 y 2.
- **Pérdida de auditoría por fallo de Postgres** → la auditoría asíncrona se entrega via outbox con la misma garantía at-least-once del resto (ADR-0007).
- **Workaround del D19 que se infiltra** (cuenta "interna" en cada club) → política explícita: prohibido por contrato; cualquier intento debe abrir un PR que revele la intención y dispare la reapertura.

## Notas

- Las premisas heredadas (especialmente ADR-0003 D2 sobre rol único, ADR-0007 D13 sobre política de fallos y ADR-0008 D11/D12 sobre `Either<XxxError, T>`) son **invariantes de este ADR**: si cambian, este ADR se revisita.
- **MFA y login con Google** (ADR-0003) no afectan a este modelo: la autorización parte del usuario ya autenticado, sea cual sea el método.
- **Cambio de rol del usuario** (alumno → entrenador): caso real en clubes pequeños. El MVP lo aplaza (ADR-0003 D2). Cuando entre, el evento `UsuarioCambioDeRol` invalida sesiones (ADR-0003 D11) y la autorización vuelve a partir del nuevo principal.
- **Revisión periódica**: este ADR se revisa a los **12 meses** de aceptación. Antes si entra el segundo club (D19) o si los disparadores de la nota siguiente se activan.
- **Reapertura → matriz configurable**: si en el futuro la autorización deja de ser fija y pasa a ser **configurable** (cada club define sus propios roles y permisos), se reabre esta decisión con un **módulo central de Autorización** que posea esa configuración y difunda sus cambios al resto de módulos por eventos. Disparadores propuestos: primer cliente que pide un rol propio que no encaja en `admin/entrenador/alumno`, o segundo club piloto con organización suficientemente distinta.
- **Expresiones de `@Authorize`** (D2): la expresión `"RECURSO:ACCION"` es un string sin verificación del compilador. ArchUnit garantiza que ningún handler quede sin anotación (D13) y el motor de evaluación deniega fail-closed las expresiones que no existan en la matriz. La nota original sobre disciplina de SpEL en `@PreAuthorize` quedó obsoleta con la revisión 2026-06-12: ya no hay SpEL.
- **Reorganización del 2026-05-29 (Nivel 1)**: el ADR se reestructura con índice de sub-decisiones (párrafo introductorio + tabla), premisas heredadas, NFRs explícitos, numeración D1-D19 con anchors. Decisiones nuevas o explicitadas: política frente a proyección stale (D9), patrón de listados con aspecto (D11), errores como `Result.Forbidden` (D12), ArchUnit obligatorio (D13), tests de acceso cruzado (D14), alcance concreto de auditoría (D15), módulo `auditoria` dedicado (D17), endpoint `/me/permissions` (D18), aplazamiento consciente del rol de soporte interno (D19), revisión a 12 meses.
- **Revisión del 2026-06-12 (D2)**: el mecanismo RBAC del adaptador de entrada pasa de `@PreAuthorize` de Spring Security a las anotaciones propias `@Authorize`/`@NoAuthRequired` evaluadas contra la `AuthorizationMatrix` (D6) y exigidas por ArchUnit (D13), alineando el ADR con la implementación de H0 (`shared/autorizacion/Authorize.kt`, `backend/CLAUDE.md`, guía operativa de módulo). Sin cambio en el modelo de tres capas (D1) ni en el resto de sub-decisiones. Se corrigen además citas con numeración antigua: ADR-0004 D7 → D4 (esquema por módulo) y "ADR-0011 pendiente" → ADR-0011 (Aceptado desde 2026-05-29).
- **Revisión del 2026-07-05 (D11, D13)**: la revisión multi-agente de arquitectura/seguridad (2026-07-03) detectó que D11 describía un aspecto que **inyectaba** predicados de filtro en la query, pero la implementación real de H0 (`UserRepositoryImpl` y equivalentes) pasa `clubId` como parámetro explícito y filtra en la propia query — no existía ningún `@Aspect`/`@Around` en el backend. En vez de construir el aspecto original (mayor coste, magia oculta sobre Hibernate/queries nativas), se opta por un **aspecto verificador**: el filtro sigue siendo responsabilidad de la firma del método (D4), y el aspecto (`AuthScopeEnforcementAspect`) comprueba en runtime, fail-closed, que el `clubId` recibido coincide con el del principal — cierra la brecha de "un método que no pasa `clubId` no está protegido por nada" sin reescribir el patrón de acceso a datos ya en producción. D13 gana la opción (d) `@AuthenticatedOnly`, que hacía falta para `QueryCurrentSession`/`SessionController.current()` (ni autorizan contra la matriz ni son endpoints públicos) — sin ella, D13 era irrealizable sin mentir sobre esos dos casos. Referencia: LAL-59, LAL-37.
