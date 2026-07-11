# ADR-0008 — Arquitectura hexagonal y DDD (aplicados con criterio)

- **Estado**: Aceptado
- **Fecha**: 2026-05-20 · revisado 2026-05-29 (reorganización Nivel 1 + cierre completo de la disciplina por módulo: índice + premisas heredadas + NFRs + numeración de sub-decisiones D1-D18 con anchors estables; nuevas sub-decisiones D10-D18 sobre librería de mapeo, typed IDs, manejo de errores con `Result`, servicios de dominio, repositorios estrictos, transacciones por AOP, validación, carga eager y exclusión explícita de factories/specifications) · **revisado 2026-06-16** (fase implementación H0 — **D2**: puertos movidos de `domain/` a `application/ports/`; dominio queda sin ninguna referencia a sus propias dependencias. Actualización en cascada de D7, D14, resumen ejecutivo y tabla de tests críticos. Motivación: la implementación del módulo `identidad` confirmó que los puertos son contratos de la capa de aplicación, no del dominio — el dominio puro no sabe nada de cómo se satisfacen sus operaciones) · **revisado 2026-06-19** (regla de idioma de identificadores — refina la premisa de «lenguaje ubicuo en castellano»: el glosario es la lengua ubicua del **negocio** (castellano), pero los **identificadores de código (Kotlin/TS) van en inglés**; SQL, valores de enum persistidos, paquetes raíz de bounded context y textos de UI siguen en castellano. Detalle en D4; enforcement por `NamingConventionArchTest`. Motivación: la contradicción entre esta premisa y el código real generaba retrabajo recurrente de nomenclatura) · **revisado 2026-07-11** (D12 — el ADR describía `Result<T, DomainError>` con un `DomainError` compartido, pero la implementación real del módulo `identidad` usa `arrow.core.Either<XxxError, T>` con el Raise DSL de Arrow-kt y un sealed class de error propio por módulo (`IdentidadError`, sin tipo compartido), tal como exige `CLAUDE.md` raíz. D12 se reescribe con la forma real (`either { }`, `ensure`, `bind()`); D16, la tabla de heurísticas y la tabla de tests críticos se actualizan en cascada. Sin cambio de código — el código ya era correcto, el ADR documentaba un diseño anterior nunca implementado) · **aceptado 2026-05-29**
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack), ADR-0002 (modelo de datos), ADR-0003 (autenticación), ADR-0004 (persistencia, UUID v7, `TIMESTAMPTZ`), ADR-0007 (monolito modular, events-first, distinción domain/integration events), ADR-0010 (CI/CD — ArchUnit), `docs/arquitectura/estructura-de-un-modulo.md` (guía operativa), `docs/glosario.md` (lenguaje ubicuo)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre cómo se construye **el interior de cada módulo** del ADR-0007. Las dieciocho sub-decisiones se agrupan en cinco áreas:

- **Enfoque y estructura (D1-D3)** — qué disciplina aplicamos, cómo se organiza el código y cómo se verifica.
- **DDD táctico (D4-D6, D11-D12)** — el catálogo que aplicamos, qué consideramos *bounded context*, cómo se modela el dominio puro y cómo se expresan IDs y errores.
- **Persistencia y lectura (D7, D8, D10, D14, D17)** — modelo de persistencia aparte, CQRS ligero, librería de mapeo, repositorios estrictos, carga eager.
- **Operativa del caso de uso (D9, D13, D15, D16)** — *hexagonal con criterio*, servicios de dominio, transacciones por AOP, validación de entrada vs invariantes.
- **Lo que NO se hace (D18)** — factories y specifications fuera del MVP.

| #   | Sub-decisión                                                                              | Capa         |
|-----|-------------------------------------------------------------------------------------------|--------------|
| D1  | [Hexagonal + DDD táctico + DDD estratégico ligero](#d1)                                    | Estratégica  |
| D2  | [Estructura interna por módulo: `domain` / `application` / `infrastructure`](#d2)          | Estratégica  |
| D3  | [Regla de dependencias hacia el dominio, verificada por ArchUnit](#d3)                     | Operativa    |
| D4  | [Catálogo del DDD táctico que se aplica](#d4)                                              | Estratégica  |
| D5  | [Bounded contexts = módulos del ADR-0007 (sin event storming formal)](#d5)                 | Estratégica  |
| D6  | [Dominio puro: sin framework, sin JPA, separado del modelo de persistencia](#d6)           | Estratégica  |
| D7  | [Modelo de persistencia aparte con mapeador](#d7)                                          | Operativa    |
| D8  | [CQRS ligero: agregados para escritura, proyecciones para lectura](#d8)                   | Estratégica  |
| D9  | [Hexagonal con criterio: puertos solo para lo que de verdad cruza](#d9)                   | Estratégica  |
| D10 | [Librería de mapeo: Konvert (KSP)](#d10)                                                   | Operativa    |
| D11 | [Typed IDs con `value class` envolviendo UUID v7](#d11)                                    | Operativa    |
| D12 | [Manejo de errores: `Either<XxxError, T>` con Raise DSL de Arrow-kt](#d12)                | Estratégica |
| D13 | [Servicios de dominio: regla estricta — solo entre varios agregados raíz](#d13)            | Operativa    |
| D14 | [Repositorios estrictos: solo cargar y guardar por ID](#d14)                               | Operativa    |
| D15 | [Transacciones por AOP con meta-anotación `@ApplicationService` a nivel de clase](#d15)    | Operativa    |
| D16 | [Validación: Bean Validation en controlador + invariantes en agregado](#d16)               | Operativa    |
| D17 | [Carga eager de agregados (incluidas sus entidades hijas)](#d17)                           | Estratégica  |
| D18 | [Factories y Specifications fuera del MVP](#d18)                                           | Operativa    |

## Contexto y problema

ADR-0007 fija un monolito modular con cuatro módulos que se comunican *events-first*. Falta decidir **cómo se estructura el código dentro de cada módulo** y **qué enfoque de diseño** se sigue para modelar el dominio.

El dominio de Runcriticon tiene complejidad real que merece modelarse bien: los tags como entidad de primera clase, los grupos como consulta sobre tags, la publicación de plan con *snapshot* de membresía, los ritmos como `Absoluto | Relativo(referencia, delta)`, las personalizaciones como entidades hijas de `PlanSemanal`, las marcas privadas del corredor (todo ello en ADR-0002). Pero el MVP son 21 funcionalidades y lo construye un equipo interno pequeño: pasarse de ceremonia es un riesgo tan real como quedarse corto.

Además, este ADR es el patrón que se replicará en **cada módulo**: cualquier decisión imprecisa se multiplica por cuatro. Por eso bajamos a tierra las decisiones operativas que normalmente se dejan al equipo y que, sin guía, terminan resolviéndose de cuatro formas distintas.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR** — se asumen y condicionan toda la decisión que sigue. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Monolito modular con cuatro bounded contexts** `identidad`, `club_taxonomia`, `planificacion`, `seguimiento` (ADR-0007). Este ADR decide la estructura interna de cada uno, no qué módulos hay.
- **Stack Kotlin sobre Spring Boot 3.x** (ADR-0001). Habilita el uso de `value class`, `data class`, *sealed classes* y librerías KSP como Konvert (D10).
- **PostgreSQL con un schema por módulo** (ADR-0004). El modelo de persistencia mapea a JPA + Hibernate (D7); el repositorio vive en `infrastructure` y solo el módulo dueño accede a su schema.
- **UUID v7 como formato de IDs** (ADR-0004 D8). Todos los IDs tipados de este ADR (D11) envuelven `UUID` v7, no `String`.
- **`TIMESTAMPTZ` para fechas** (ADR-0004 D8). En el dominio se usa `java.time.Instant`, no `LocalDateTime`.
- **Events-first como comunicación entre módulos** (ADR-0007 D4). Los eventos del dominio (D4 de este ADR) son la materia prima de los integration events que el módulo emite (ADR-0007 D10-D12).
- **ArchUnit disponible en CI** (ADR-0010). Es el mecanismo de enforcement de las reglas de este ADR (D3, D11, D13, D14, D18).
- **Lenguaje ubicuo del negocio en castellano** fijado por discovery y consolidado en `docs/glosario.md`: es el idioma de la comunicación, del discovery, de los textos de UI y de la **frontera de persistencia** (identificadores SQL y valores de enum persistidos). Los **identificadores de código (Kotlin/TypeScript) van en inglés** — la regla completa, con sus excepciones y el enforcement, está en **D4**. (Premisa refinada en la revisión del 2026-06-19; antes decía «es el lenguaje del código sin traducción», lo que contradecía el código real.)

## Requisitos no funcionales y criterios de éxito

Este ADR no establece NFRs de runtime (la latencia y la carga las fijan ADR-0001 y ADR-0007). Los criterios que sí aplican son los del **proceso de desarrollo**, que son lo que justifica el coste del doble modelo y la disciplina de dominio puro:

| Dimensión | Valor objetivo |
|---|---|
| **Tiempo de la suite unitaria del dominio** (por módulo) | **< 1 s** para toda la suite del módulo. El dominio puro debe ser ejecutable sin BD ni Spring. |
| **Cobertura de tests del dominio** | **≥ 90 %** de la lógica del dominio. Es el corazón testable; debería estar cubierto. |
| **Onboarding de un perfil JVM/Spring nuevo** | **< 1 semana** desde primer día hasta PR full-stack mergeable. La afinidad estructural (ADR-0001) y la guía `estructura-de-un-modulo.md` la sostienen. |
| **Líneas del mapeador / líneas del módulo** | **< 15 %**. Indicador de que el coste del doble modelo no se ha salido de madre; si crece más, revisar el patrón o la librería (D10). |
| **Tiempo total CI por módulo** (unitarios + integración con Testcontainers) | **< 5 min** (orientativo; lo afina ADR-0010). |

Estos no son NFRs de cliente sino **del propio proceso de construcción**. Si fallan, la apuesta del ADR (hexagonal con criterio + dominio puro) deja de pagar su coste.

## Drivers de la decisión

- El dominio tiene **reglas de negocio reales** (resolución de grupos, *snapshot* de plan, tipos de ritmo, personalizaciones) que conviene tener centralizadas y bien modeladas, no dispersas.
- La **lógica de dominio debe ser testable** sin levantar base de datos ni framework.
- El producto es **longevo**: el código debe envejecer bien.
- Equipo interno pequeño + velocidad de MVP → **evitar ceremonia que no pague**.
- Coherencia con el monolito modular: las fronteras de módulo de ADR-0007 deben corresponderse con fronteras de dominio.
- **Este patrón se replica en cuatro módulos**: cualquier ambigüedad se multiplica por cuatro.

## Opciones consideradas

- **Opción A** — Arquitectura hexagonal + DDD táctico, con DDD estratégico ligero.
- **Opción B** — Capas tradicionales (controller → service → repository).
- **Opción C** — Arquitectura hexagonal + DDD completo (estratégico con ceremonia formal).

### Opción A — Hexagonal + DDD táctico, estratégico ligero

Cada módulo se estructura en **dominio / aplicación / infraestructura**, con el dominio aislado de la infraestructura mediante **puertos y adaptadores**. Se aplica el **DDD táctico** (agregados, *value objects*, eventos de dominio, lenguaje ubicuo, repositorios como puertos) y el **DDD estratégico de forma ligera**: los *bounded contexts* son los módulos de ADR-0007, identificados de forma pragmática a partir del discovery, sin talleres formales.

- 👍 El dominio queda aislado y **testable sin infraestructura** — tests rápidos.
- 👍 Las reglas de negocio viven en un sitio (el dominio), no filtradas en *services* anémicos.
- 👍 El lenguaje ubicuo alinea código y discovery — menos malentendidos.
- 👍 Encaja con el monolito modular: *bounded context* = módulo.
- 👎 Más estructura inicial que las capas tradicionales; el equipo debe entender hexagonal + DDD táctico (coste de onboarding).

### Opción B — Capas tradicionales

`controller → service → repository`, sin dominio rico ni puertos.

- 👍 Familiar, cero ceremonia, rápido de arrancar.
- 👎 Tiende al **dominio anémico**: la lógica de negocio se desparrama por los *services* y, con reglas como las de este dominio, deriva en *services* enormes difíciles de testear y mantener.
- 👎 El dominio queda acoplado a JPA/infraestructura — tests lentos y frágiles.

### Opción C — Hexagonal + DDD completo

Como la A, pero con **DDD estratégico formal**: *event storming*, *context mapping* elaborado, etc.

- 👍 Modelado de dominio muy riguroso.
- 👎 La ceremonia estratégica es **overhead que un MVP de 21 MUSTs con equipo pequeño no puede justificar**. Riesgo real de *parálisis por análisis*: meses de talleres antes de entregar.

## Decisión

**Opción A: arquitectura hexagonal + DDD táctico, con DDD estratégico aplicado de forma ligera.** Da lo que el dominio necesita —aislamiento, testabilidad, reglas centralizadas, lenguaje compartido— sin la ceremonia estratégica que ahogaría al equipo antes de entregar el MVP. Las capas tradicionales envejecerían mal con este dominio; el DDD completo es prematuro.

Las dieciocho sub-decisiones desarrolladas a continuación. Nueve son **estratégicas** (D1, D2, D4, D5, D6, D8, D9, D12, D17 — enfoque, estructura, DDD táctico aplicado, dominio puro, CQRS ligero, criterio hexagonal, manejo de errores funcional, carga eager); el resto son **operativas** (D3, D7, D10, D11, D13, D14, D15, D16, D18) y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — Hexagonal + DDD táctico + DDD estratégico ligero

Cada módulo se construye con tres elementos a la vez:

- **Arquitectura hexagonal**: el dominio aislado en el centro; comunicación con el exterior mediante puertos (interfaces en el dominio) y adaptadores (implementaciones en infraestructura).
- **DDD táctico**: agregados, value objects, eventos de dominio, repositorios como puertos, lenguaje ubicuo (D4).
- **DDD estratégico ligero**: los bounded contexts son los módulos del ADR-0007 (D5); **no se hace event storming formal**.

La opción se elige frente a B (capas tradicionales — dominio anémico inevitable con este dominio) y frente a C (DDD completo — ceremonia que ahoga al MVP). El equilibrio se sostiene en una frase que el resto del ADR convierte en regla: *"aislar lo que de verdad puede cambiar o necesita test aislado; no abstraer por abstraer"* (D9).

<a id="d2"></a>
### D2 — Estructura interna por módulo: `domain` / `application` / `infrastructure`

Cada módulo de ADR-0007 se organiza en tres capas:

- **`domain`** — entidades, agregados, *value objects*, servicios de dominio (D13) y **eventos de dominio**. **Sin ninguna dependencia ni anotación de framework** (ni Spring ni JPA): son clases puras (D6). El dominio no contiene puertos: no sabe nada de cómo se satisfacen sus operaciones ni de qué adapatadores lo implementan.
- **`application`** — casos de uso / servicios de aplicación que orquestan el dominio, **más los puertos** (interfaces que definen las dependencias del módulo hacia infraestructura: repositorios, adaptadores de salida como `EnviadorDeEmail` y `PublicadorDeEventos`, `AutorizacionService` del módulo). Los puertos viven en el sub-paquete `application/ports/`. Depende de `domain`. **Publica los eventos de dominio** y **consume los eventos entrantes** de otros módulos, actualizando las proyecciones locales. Las clases de casos de uso llevan `@ApplicationService` (D15); los puertos son interfaces planas sin anotación.
- **`infrastructure`** — los **adaptadores**: controladores REST (adaptadores de entrada), el modelo de persistencia y los repositorios, el cliente de email, el adaptador de publicación de eventos. Implementan los puertos definidos en `application/ports/`.

Estructura de paquetes que cierra D2 + D12 del ADR-0007:

```
com.runcriticon.<modulo>/
  ├── domain/
  │     ├── events/             ← domain events internos
  │     └── ...                  ← agregados, value objects, errores
  ├── application/
  │     ├── ports/              ← interfaces: repositorio, adaptadores de salida, AutorizacionService
  │     └── ...                 ← @ApplicationService (casos de uso)
  ├── infrastructure/           ← adaptadores
  └── api/
        └── events/             ← integration events públicos (ADR-0007 D12)
```

<a id="d3"></a>
### D3 — Regla de dependencias hacia el dominio, verificada por ArchUnit

La regla de dependencias apunta siempre **hacia el dominio**: `infrastructure` depende de `application`, `application` depende de `domain`, y el `domain` **no depende de nadie**. El dominio no conoce framework alguno.

Tests ArchUnit obligatorios en CI (cruce con ADR-0010):

- **Sin Spring en `domain`**: ningún import de `org.springframework.*` en clases del paquete `…domain.*`.
- **Sin JPA en `domain`**: ningún import de `jakarta.persistence.*` ni `org.hibernate.*` en `…domain.*`.
- **Sin Jackson en `domain`**: ningún import de `com.fasterxml.jackson.*` en `…domain.*`.
- **Direccionalidad**: `…application.*` no importa de `…infrastructure.*`; `…domain.*` no importa de ninguna de las otras dos.
- **Eventos por capa**: clases en `…domain.events.*` no implementan `IntegrationEvent` (ADR-0007 D12); clases en `…api.events.*` sí.

Si CI falla por una de estas reglas, el PR no se mergea. La disciplina es **enforced**, no documentada.

<a id="d4"></a>
### D4 — Catálogo del DDD táctico que se aplica

Los elementos del catálogo táctico que se usan en Runcriticon, con su rol explícito:

- **Agregados** con una raíz que protege sus invariantes — ej. `PlanSemanal` (raíz del agregado con `Sesion` y `Personalizacion` como entidades hijas, ver D17), `Grupo`, `Alumno`.
- **Value objects** para conceptos sin identidad propia, inmutables — ej. `Ritmo` (`Absoluto(segPorKm: Int)` o `Relativo(referencia: Distancia, deltaSegPorKm: Int)` de ADR-0002 D6), `TagKey`, `TagValue`, `Distancia`.
- **Eventos de dominio** — un hecho relevante que ya ha ocurrido (`PlanPublicado`, `AlumnoAsignadoAGrupo`). Definidos en `domain.events` (para los internos) o materializados en `api.events` (para los públicos — ADR-0007 D12). Ver *Aclaración sobre eventos de dominio e integration events* abajo.
- **Repositorios como puertos**: interfaz en `domain`, implementación en `infrastructure`. **Estrictos**: solo cargan/guardan por ID (D14).
- **Servicios de dominio**: lógica que orquesta **varios agregados raíz** y no encaja en ninguno. Regla estricta en D13.
- **Lenguaje ubicuo**: los conceptos del discovery (alumno, entrenador, grupo, plan, sesión, reporte, tag, marca, personalización) son el **vocabulario compartido** negocio↔código, recogidos en [`docs/glosario.md`](../glosario.md). El glosario es la lengua ubicua del **negocio**, en castellano; **no impone castellano a los identificadores de código**. La regla de idioma —única y sin ambigüedad, verificada por `NamingConventionArchTest` (ADR-0010)— es:
  - **Inglés**: todos los identificadores de código Kotlin/TS — clases, interfaces, objetos, funciones, propiedades y sub-paquetes técnicos (`persistence`, `security`, `model`, `annotations`, …). Ej.: `User`, `WeeklyPlan`, `PublishPlan`, `AuthorizationMatrix`.
  - **Castellano (frontera deliberada)**: paquetes raíz de bounded context (`identidad`, `clubtaxonomia`, `planificacion`, `seguimiento`, `auditoria`, `shared.autorizacion`); identificadores SQL (esquemas, tablas, columnas) y valores de enum persistidos (`ENTRENADOR`, `ALUMNO`, `ACTIVO`, …); textos de UI (i18n, ADR-0012 D9). La frontera de persistencia traduce con `@Table(name=…)` / `@Column(name=…)`.

  > Los ejemplos de código de este ADR y de las guías de arquitectura que aún muestran nombres en castellano (`PlanSemanal`, `Sesion`, `PlanificacionError`…) son **previos a esta regla**; ilustran conceptos DDD, no la convención de idioma. Se migran de forma oportunista (LAL-52). La norma vigente es la de arriba.

#### Aclaración sobre eventos de dominio e integration events (cruce con ADR-0007 D12)

Hay **dos categorías** de evento que conviene no mezclar:

- **Domain event interno** — emitido por un agregado o servicio de dominio para comunicar internamente dentro del módulo (otros agregados, listeners del mismo módulo). Vive en `domain.events.*`. **No** se versiona con JSON Schema. Forma libre — al menos `eventId`, `occurredAt` y nombre en pasado para mantener el estilo.
- **Integration event público** — la **vista publicada al exterior** de un domain event (o de varios), traducida al lenguaje del contrato externo. Vive en `api.events.*`. **Cumple los seis campos obligatorios** de ADR-0007 D10 (`eventId`, `aggregateId`, `occurredAt`, `version`, `clubId`, `actorId`). Versionado con JSON Schema (ADR-0007 D11).

El patrón canónico es **domain event → integration event**: el agregado emite un domain event, el caso de uso del mismo módulo lo recoge y emite el integration event correspondiente al outbox. Ambos son "eventos de dominio" en sentido DDD; lo que cambia es la **visibilidad** y por tanto las garantías de contrato.

<a id="d5"></a>
### D5 — Bounded contexts = módulos del ADR-0007 (sin event storming formal)

Los bounded contexts son los **cuatro módulos** que el ADR-0007 D2 ya identificó a partir del discovery, las specs y los wireframes:

- Identidad y acceso.
- Club y taxonomía.
- Planificación.
- Seguimiento.

**No se hacen talleres de event storming ni mapas de contexto formales para el MVP**. La descomposición se valida en uso: si al añadir una funcionalidad se ve que no encaja en ninguno de los cuatro módulos (señal de que falta un quinto o de que dos están solapados), se revisa.

<a id="d6"></a>
### D6 — Dominio puro: sin framework, sin JPA, separado del modelo de persistencia

El dominio **no tiene ninguna anotación de persistencia ni framework**. Para lograrlo:

- El `domain` define los agregados, entidades, value objects, eventos y puertos como **clases puras Kotlin** (`data class`, `value class`, `sealed class`, `class` con constructor privado).
- La `infrastructure` tiene un **modelo de persistencia propio**: entidades JPA (`@Entity`) que reflejan las tablas, **separadas** de los agregados de dominio (D7).
- Un **mapeador** convierte agregado de dominio ↔ entidad de persistencia (D10).
- Spring Data JPA / Hibernate es el ORM (ADR-0001, ADR-0004). Su carácter invasivo queda **contenido en el modelo de persistencia** y **nunca toca el dominio** — por eso esta opción no obliga a cambiar ADR-0004.

Razón: el dominio se puede testar sin BD, sin Spring y sin red. La suite unitaria del dominio corre en milisegundos (NFR < 1 s por módulo). Y el motor de persistencia o el framework se pueden cambiar sin tocar el dominio.

<a id="d7"></a>
### D7 — Modelo de persistencia aparte con mapeador

Una entidad JPA por agregado raíz (más sus entidades hijas si las tiene), distinta del agregado del dominio. Un **mapeador** (D10) traduce entre ambos.

- La interfaz del repositorio vive en `application/ports/`; la implementación en `infrastructure` usa las entidades JPA y el mapeador.
- **Coste asumido**: el doble modelo y el *boilerplate* de mapeo. Mitigado con Konvert (D10), tests de roundtrip y tests de propiedades.

<a id="d8"></a>
### D8 — CQRS ligero: agregados para escritura, proyecciones para lectura

- Los **agregados** protegen la **escritura** (sus invariantes). El repositorio es estricto (D14): solo carga/guarda por ID.
- Las **proyecciones / read models** —locales, alimentadas por eventos de dominio (ADR-0007 D9)— sirven la **lectura cross-context**: se consultan directamente, sin pasar por los agregados.
- No se fuerza la ceremonia de agregado sobre las consultas dentro del propio módulo: una lectura interna del módulo puede ser una *query method* sobre la entidad JPA si no necesita reglas de negocio.

<a id="d9"></a>
### D9 — Hexagonal con criterio: puertos solo para lo que de verdad cruza

Un puerto existe para lo que **habla con infraestructura externa** (BD, email, broker de eventos) o para los **adaptadores de entrada** (REST, listeners de eventos). La comunicación con **otros módulos no es un puerto**: es por eventos (ADR-0007).

**No se crea un puerto ni una abstracción para cada operación CRUD trivial.** La regla operativa: **aislar lo que de verdad puede cambiar o necesita test aislado; no abstraer por abstraer**.

Indicadores prácticos de cuándo un puerto es legítimo (cruce con las heurísticas más abajo):

- Hay un componente externo (BD, email, cliente HTTP) que puede cambiarse por otra implementación o stubear en tests.
- Hay un adaptador de entrada (REST, listener) que dispara al caso de uso.
- Existe una dependencia que el dominio quiere expresar pero no implementar (clock, generador de IDs).

<a id="d10"></a>
### D10 — Librería de mapeo: Konvert (KSP)

El mapeo entre agregado de dominio y entidad de persistencia (D7) se hace con **Konvert** — librería KSP (Kotlin Symbol Processing) específica para Kotlin que **genera el código en compile-time**.

Forma del mapeador:

```kotlin
@Konverter
interface PlanSemanalMapper {
    fun toEntity(domain: PlanSemanal): PlanSemanalEntity
    fun toDomain(entity: PlanSemanalEntity): PlanSemanal
}
```

Konvert genera la implementación en compile-time. Si los campos no coinciden o un tipo no es mapeable, **error de compilación**.

Razones de la elección frente a alternativas:

- **Específico para Kotlin**: soporta nativamente `data class`, `sealed class`, `value class` (que vamos a usar para los typed IDs de D11) y nullability.
- **Compile-time, no reflexión**: cero coste en runtime; los errores se detectan en build.
- **Type-safe**: si el agregado evoluciona y el mapeador no se actualiza, el build rompe.
- **Sintaxis declarativa**: anotación sobre interface, sin código boilerplate.

Se descarta **MapStruct** (port de Java, fricción con `data class` y null safety de Kotlin) y **ModelMapper / mappers reflexivos** (runtime, sin garantías de compilación).

Coste razonable: añadir Konvert + plugin KSP en Gradle. Sin coste de runtime, sin curva pronunciada (la anotación es directa).

<a id="d11"></a>
### D11 — Typed IDs con `value class` envolviendo UUID v7

Cada agregado raíz tiene su **identificador tipado**, envolviendo `UUID` de Java (que en BD es UUID v7 según ADR-0004 D8):

```kotlin
@JvmInline
value class PlanId(val value: UUID)

@JvmInline
value class AlumnoId(val value: UUID)

@JvmInline
value class GrupoId(val value: UUID)
```

Razones:

- **Seguridad de tipos**: imposible pasar un `AlumnoId` donde se espera un `PlanId` (el compilador lo rechaza). Elimina toda una clase de bugs en runtime.
- **`@JvmInline value class`** tiene **cero coste de runtime**: el compilador desenrolla a `UUID` puro. No hay sobrecarga.
- **Coherente con UUID v7** (ADR-0004): el `value` interno es siempre `UUID`, generado en aplicación con la librería del ADR-0004 hasta que PG 18 traiga `uuidv7()` nativo.
- **Legibilidad**: el tipo en una firma de método dice qué ID es, no solo "es un identificador".

Regla: **nunca usar `UUID` o `String` raw como id en firmas del dominio**. Convertir entre `UUID` y `XId` solo ocurre en los bordes (controlador, mapeador). Test ArchUnit que detecta `UUID` y `String` como parámetros de métodos en `…domain.*` (deben ser typed IDs).

<a id="d12"></a>
### D12 — Manejo de errores: `Either<XxxError, T>` con Raise DSL de Arrow-kt

Los fallos cruzan capas como `arrow.core.Either<XxxError, T>` (estilo *Railway-Oriented Programming*, Raise DSL de Arrow-kt), no como excepciones lanzadas en mitad del flujo. Es la elección que más encaja con el espíritu funcional que queremos en el proyecto.

**No hay un `DomainError` compartido entre módulos.** Cada módulo define su propio sealed class de error (`XxxError` — `IdentidadError`, `PlanificacionError`, …, CLAUDE.md), sin tipo base común: un `Either<PlanificacionError, T>` y un `Either<IdentidadError, T>` no comparten jerarquía. Las variantes se repiten por convención entre módulos (`Forbidden`, `NotFound`, `InvalidInput(field, reason)`, `Conflict(reason)`), no por herencia.

Forma (verificada contra el módulo `identidad`, `IdentidadError.kt` y los casos de uso reales):

```kotlin
// domain — sealed class propia del módulo, sin tipo compartido
sealed class PlanificacionError {
    data class PlanYaPublicado(val planId: PlanId) : PlanificacionError()
    data class AlumnoNoEnSnapshot(val alumnoId: AlumnoId) : PlanificacionError()
    data object Forbidden : PlanificacionError()
    data object NotFound : PlanificacionError()
    data class InvalidInput(val field: String, val reason: String) : PlanificacionError()
}

// domain — el agregado devuelve Either en vez de lanzar
class PlanSemanal private constructor(/* ... */) {
    fun publish(actor: UserId): Either<PlanificacionError, PlanPublished> =
        either {
            ensure(status == PlanStatus.DRAFT) { PlanificacionError.PlanYaPublicado(id) }
            status = PlanStatus.PUBLISHED
            PlanPublished(id, clubId, actor)
        }
}

// application — el caso de uso compone con el Raise DSL (either { }, ensure, bind())
@ApplicationService
class PublishPlan(private val repository: PlanRepository) {
    fun execute(planId: PlanId, actor: UserId): Either<PlanificacionError, PlanPublished> =
        either {
            val plan = repository.findById(planId)
            ensureNotNull(plan) { PlanificacionError.NotFound }
            plan.publish(actor).bind()
        }
}
```

Razones para elegir Arrow-kt/`Either` sobre excepciones o `kotlin.Result`:

- **Errores como parte del contrato**: la firma del método declara los fallos posibles. El consumidor está obligado a tratarlos.
- **Sin excepciones que interrumpen el control flow** del dominio puro.
- **Raise DSL** (`either { }`, `ensure`, `ensureNotNull`, `bind()`) compone sin `try/catch` anidados ni el boilerplate manual de encadenar `flatMap`.
- **`kotlin.Result` se descarta**: su canal de error es `Throwable`, no un tipo sellado propio — pierde la exhaustividad de `when` que exige D12.

**Lo que NO desaparece**: las excepciones se permiten en `infrastructure` (rutas externas pueden lanzar, hay que captarlas) y en violaciones de invariantes irrecuperables (`require()`/`check()` en constructores con argumentos imposibles). Pero el flujo normal del dominio usa `Either`.

<a id="d13"></a>
### D13 — Servicios de dominio: regla estricta — solo entre varios agregados raíz

Un **servicio de dominio** es legítimo **solo cuando** la operación cumple las tres condiciones siguientes:

1. Involucra **dos o más agregados raíz** distintos.
2. La lógica no encaja naturalmente en ninguno de ellos (no es "el plan se publica solo" — eso es del agregado `PlanSemanal`).
3. La operación expresa una **regla de negocio**, no una orquestación técnica.

Si la operación no cumple las tres, **no es un servicio de dominio**:

- Operación sobre un único agregado → método del agregado.
- Operación que orquesta agregados pero no añade reglas → caso de uso de aplicación (`@ApplicationService`).
- Operación que necesita una proyección o evento externo → caso de uso, no servicio de dominio.

Ejemplo claro de la regla: *"al publicar un plan, resolver el snapshot consultando la proyección de grupos"* **NO es servicio de dominio** — es caso de uso porque resuelve por proyección, no por agregados ajenos.

Sin esta disciplina, el equipo termina creando `PlanSemanalService` con toda la lógica y un `PlanSemanal` anémico — exactamente lo que la Opción B descartada produciría. Este es el principal riesgo de degradación del ADR y por eso D13 es regla, no recomendación.

<a id="d14"></a>
### D14 — Repositorios estrictos: solo cargar y guardar por ID

Los repositorios del dominio tienen una superficie **deliberadamente limitada**:

```kotlin
// application/ports
interface PlanSemanalRepository {
    fun guardar(plan: PlanSemanal)
    fun buscar(id: PlanId): PlanSemanal?
    fun existe(id: PlanId): Boolean
}
```

**No se permiten** finders complejos (`buscarPorClubYNivel`, `listarPorEntrenadorYSemana`, etc.). Cualquier consulta no trivial va a una **proyección / read model** (D8). Razones:

- El repositorio no es un *query repository*; su función es la **persistencia del agregado**.
- Las consultas complejas en el repositorio rompen CQRS ligero (D8): la misma información se sirve por dos caminos.
- Sin la regla, el repositorio crece sin freno: el primer PR añade `buscarPorEntrenador`, el siguiente `buscarPorEntrenadorYClub`, el tercero `buscarConFiltrosArbitrarios` — y termina con SQL ad-hoc disperso.

Test ArchUnit que verifica el contrato: los métodos de las interfaces que extienden `Repository` en `…application.ports.*` solo pueden ser `guardar`, `buscar(id)`, `existe(id)`, `borrar(id)` y variantes con typed IDs.

<a id="d15"></a>
### D15 — Transacciones por AOP con meta-anotación `@ApplicationService` a nivel de clase

Las transacciones se abren en el **caso de uso** (capa `application`), pero **sin anotar cada método** con `@Transactional`. Se usa una **meta-anotación** a nivel de clase:

```kotlin
// shared/infrastructure
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
@Transactional
annotation class ApplicationService
```

Cualquier caso de uso se declara así:

```kotlin
// application
@ApplicationService
class PublicarPlanService(
    private val repositorio: PlanSemanalRepository,
    private val publicador: PublicadorDeEventos,
) {
    fun ejecutar(planId: PlanId): Either<PlanificacionError, Unit> { /* ... */ }
}
```

Ventajas:

- **Sin anotaciones en métodos**: cumple el requisito de la decisión.
- **Identifica los casos de uso a primera vista**: `@ApplicationService` es marker semántico, no solo declarativo.
- **Bean Spring + transacción**: una sola anotación cubre las dos cosas.
- **Test ArchUnit posible**: cualquier clase en `…application.*` con métodos públicos debe estar marcada con `@ApplicationService`. Cualquier clase fuera de `…application.*` no debe usarla.

Read-only transactions: para casos de uso solo de lectura se puede definir una segunda anotación `@ApplicationQueryService` con `@Transactional(readOnly = true)`. Se introduce solo si se observa beneficio medible.

<a id="d16"></a>
### D16 — Validación: Bean Validation en controlador + invariantes en agregado

Hay **dos niveles de validación** que se aplican en sitios distintos y son **complementarios**, no redundantes:

- **Validación de forma** (estructura, tipos, formatos): **en el controlador REST** con Bean Validation (`@Valid`, `@NotNull`, `@Email`, `@Min`, etc.). Garantiza que los datos que llegan al caso de uso son sintácticamente válidos.
- **Validación de negocio** (invariantes del dominio): **en el agregado** mediante el constructor privado + factory method, y en los métodos de comportamiento mediante `Either<XxxError, T>` (D12) o `require()` cuando el caso es irrecuperable.

Ejemplo del split:

```kotlin
// infrastructure — controlador
data class PublicarPlanRequest(
    @field:NotNull val planId: UUID,
)

@RestController
class PlanController(private val publicarPlan: PublicarPlanService) {
    @PostMapping("/api/planes/publicar")
    fun publicar(@Valid @RequestBody req: PublicarPlanRequest): ResponseEntity<*> {
        // Bean Validation ya rechazó request inválidos
        return when (val result = publicarPlan.ejecutar(PlanId(req.planId))) {
            is Either.Right -> ResponseEntity.ok().build<Unit>()
            is Either.Left -> ResponseEntity.unprocessableEntity().body(result.value)
        }
    }
}

// domain — agregado
class PlanSemanal private constructor(/* ... */) {
    fun publicar(actor: UsuarioId): Either<PlanificacionError, PlanPublicado> =
        either {
            ensure(estado == EstadoPlan.BORRADOR) { PlanificacionError.PlanYaPublicado(id) }  // invariante de negocio
            // ...
        }
}
```

Ambas son obligatorias; no se duplican.

<a id="d17"></a>
### D17 — Carga eager de agregados (incluidas sus entidades hijas)

Cuando se carga un agregado raíz desde su repositorio, se cargan **todas sus entidades hijas** en la misma operación. No hay lazy loading.

Razón: el agregado se carga **completo** porque su invariante depende del estado completo. Si un agregado fuera tan grande que la carga eager rinde mal, sería **señal de que el bounded context o el agregado están mal modelados**, no de que haga falta lazy loading.

En Runcriticon concretamente:

- `PlanSemanal` carga sus `Sesion` (típicamente 7) y sus `Personalizacion` (típicamente < 10 por plan). Volumen bajo.
- `Grupo` no tiene entidades hijas (los `alumno_tag` y `grupo_alumno_override` son colecciones externas resueltas por SQL, no parte del agregado).
- `Alumno` no tiene entidades hijas.

Implementación: la entidad JPA usa `FetchType.EAGER` o `@EntityGraph` en el método del repositorio. El mapeador (D10) reconstruye el agregado completo. Para evitar N+1, los `JOIN FETCH` se aplican explícitamente al cargar.

Si en el futuro un agregado se vuelve pesado en carga, la opción NO es lazy loading; es **rediseñar el agregado** (separar en dos agregados con sus límites claros, o mover una colección a un read model).

<a id="d18"></a>
### D18 — Factories y Specifications fuera del MVP

Dos patrones del DDD táctico que **no se aplican** en el MVP, con razón explícita:

- **Factories**: para la creación compleja de agregados. En Runcriticon, la creación se hace con **constructores estáticos del agregado** (`PlanSemanal.crearBorrador(...)`, `Alumno.invitar(...)`). No hay creación lo suficientemente compleja como para justificar un objeto factory separado. Si emerge un caso (creación que depende de varios agregados o de servicios externos), se introduce entonces.
- **Specifications**: para reglas de negocio reutilizables (ej. *"el alumno cumple los requisitos del grupo"*). El modelo de Runcriticon resuelve estas consultas con **tags + proyecciones** (ADR-0002 D3, SQL canónico): la regla vive en SQL, no en un `Specification` de DDD. Las specs como pattern formal **no se usan en MVP**.

Esto cierra la pregunta del equipo *"¿usamos también factories y specifications?"* con un **NO explícito y justificado**.

## Estructura interna de cada módulo (resumen ejecutivo)

Cada módulo de ADR-0007 sigue la misma forma. Para el detalle paso a paso con ejemplos en Kotlin, ver [`docs/arquitectura/estructura-de-un-modulo.md`](../arquitectura/estructura-de-un-modulo.md).

Resumen:

```
com.runcriticon.<modulo>/
  ├── domain/
  │     ├── events/             ← domain events internos
  │     ├── <Agregado>.kt       ← class con constructor privado + factory
  │     ├── <ValueObject>.kt    ← data class / value class / sealed class
  │     └── <Modulo>Error.kt    ← sealed class con casos del módulo (D12, sin tipo de error compartido)
  ├── application/
  │     ├── ports/
  │     │     ├── <Repository>.kt          ← interface (puerto estricto D14)
  │     │     ├── PublicadorDeEventos.kt   ← interface adaptador de salida
  │     │     └── <Modulo>AutorizacionService.kt  ← interface (D15 cruce ADR-0009)
  │     └── <UseCase>Service.kt ← @ApplicationService (D15)
  ├── infrastructure/
  │     ├── persistence/
  │     │     ├── <Aggregate>Entity.kt
  │     │     ├── <Aggregate>JpaRepository.kt
  │     │     └── <Aggregate>Mapper.kt   ← @Konverter (D10)
  │     ├── rest/
  │     │     └── <Aggregate>Controller.kt  ← @Valid (D16)
  │     └── events/
  │           └── PublicadorDeEventosImpl.kt    ← outbox Spring Modulith
  └── api/
        └── events/                          ← integration events públicos
```

## Heurísticas operativas — cuándo crear X

Tabla para resolver decisiones recurrentes del equipo sin discusión repetida. Se basa en las sub-decisiones de este ADR.

| Pregunta | Respuesta | Razón |
|---|---|---|
| **¿Crear un nuevo agregado o añadir al existente?** | Agregado nuevo **solo si** tiene su propio ciclo de vida + sus propias invariantes que no comparten con el existente. Si comparten ciclo o invariantes, **entidad hija** del existente. | Un agregado por límite de consistencia transaccional. |
| **¿Servicio de dominio o caso de uso?** | Servicio de dominio **solo si** involucra **varios agregados raíz** + expresa **regla de negocio** + no encaja en ninguno (D13). Si no, **caso de uso** `@ApplicationService`. | Evita la DDD anémica encubierta. |
| **¿Value object o tipo primitivo?** | Value object **siempre que** el concepto tenga reglas propias (validación, formato) o aparezca en varios sitios. `Ritmo`, `Distancia`, `TagValue` lo son; `nombreLibre: String` no. | Reglas en el value object, no dispersas. |
| **¿Proyección nueva o ampliar existente?** | Proyección nueva **si** sirve un caso de uso de lectura distinto. Ampliar existente **si** es enriquecer la misma consulta. | Una proyección, una vista de lectura. |
| **¿Puerto o llamada directa?** | Puerto **si** hay infraestructura externa (BD, email) o adaptador de entrada (REST, listener) (D9). Llamada directa **si** es lógica interna del dominio o utilidad sin externalidad. | Hexagonal con criterio, no por reflejo. |
| **¿Excepción o `Either<XxxError, T>`?** | `Either` para fallos esperados de la lógica (D12). Excepción solo para casos irrecuperables (precondiciones imposibles) o errores de infraestructura. | Errores como contrato. |
| **¿Eager o lazy?** | **Siempre eager** (D17). Si el agregado es demasiado pesado, **rediseñar**, no lazy. | Carga completa preserva invariantes. |

## Mapeo de entidades hijas (caso `PlanSemanal` con `Sesion` y `Personalizacion`)

El caso simple (un agregado = una entidad JPA) es trivial con Konvert. El caso real más complejo de Runcriticon es **`PlanSemanal` con dos colecciones de entidades hijas** (`Sesion` y `Personalizacion`). Patrón concreto:

```kotlin
// domain
class PlanSemanal private constructor(
    val id: PlanId,
    val clubId: ClubId,
    val entrenadorId: UsuarioId,
    val sesiones: List<Sesion>,
    val personalizaciones: List<Personalizacion>,
    private var estado: EstadoPlan,
) { /* ... */ }

// infrastructure/persistence — modelo JPA
@Entity @Table(name = "plan_semanal")
class PlanSemanalEntity(
    @Id val id: UUID,
    val clubId: UUID,
    val entrenadorId: UUID,
    @OneToMany(mappedBy = "plan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val sesiones: MutableList<SesionEntity>,
    @OneToMany(mappedBy = "plan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val personalizaciones: MutableList<PersonalizacionEntity>,
    val estado: String,
)

// infrastructure/persistence — mapeador
@Konverter
interface PlanSemanalMapper {
    fun toEntity(domain: PlanSemanal): PlanSemanalEntity
    fun toDomain(entity: PlanSemanalEntity): PlanSemanal
}

// infrastructure/persistence — repositorio
@Repository
class PlanSemanalJpaRepository(
    private val jpa: JpaPlanSemanalRepository,
    private val mapper: PlanSemanalMapper,
) : PlanSemanalRepository {
    override fun buscar(id: PlanId): PlanSemanal? =
        jpa.findById(id.value).orElse(null)?.let(mapper::toDomain)

    override fun guardar(plan: PlanSemanal) {
        jpa.save(mapper.toEntity(plan))
    }
}

// infrastructure/persistence — Spring Data JPA con @EntityGraph para evitar N+1
interface JpaPlanSemanalRepository : JpaRepository<PlanSemanalEntity, UUID> {
    @EntityGraph(attributePaths = ["sesiones", "personalizaciones"])
    override fun findById(id: UUID): Optional<PlanSemanalEntity>
}
```

Tres puntos clave:

- **`FetchType.EAGER` + `@EntityGraph`** garantizan que el plan se carga con sus colecciones en una sola query SQL (`JOIN FETCH`), evitando N+1.
- **Konvert** genera `toEntity` y `toDomain` que recorren las colecciones recursivamente. Sin código manual.
- El agregado del dominio recibe las colecciones como `List<...>` (inmutables); la entidad JPA usa `MutableList<...>` por requisito de Hibernate. El mapeador hace la conversión.

## Detección de DDD anémica encubierta

El riesgo principal del ADR es que la estructura hexagonal se mantenga pero la lógica termine en `@ApplicationService` gordos, dejando los agregados como simples portadores de datos. Tres reglas operativas que el equipo aplica en revisión de PR:

1. **Si el agregado solo expone `data class` con propiedades públicas y sin métodos de comportamiento**, probablemente es anémico. Un agregado de Runcriticon (`PlanSemanal`, `Grupo`, `Alumno`) tiene **al menos 2-3 métodos de comportamiento** que ejecutan reglas.
2. **Si un `@ApplicationService` tiene más de ~20 líneas de lógica condicional**, probablemente esa lógica debería estar en el agregado. Caso típico: `if (plan.estado == BORRADOR && plan.sesiones.isNotEmpty()) { ... }` — eso es responsabilidad del agregado, no del caso de uso.
3. **Si el agregado tiene más de 5 dependencias externas inyectadas**, no es un agregado: es un service disfrazado. Un agregado de DDD se construye desde su estado, no se inyecta con clientes externos.

Estas reglas se aplican en revisión humana de PR; no son tests automatizables fácilmente, pero su violación es señal de alarma.

## Estrategia de tests críticos

Los tipos de test los fija **ADR-0010**. Esta sección señala los **casos críticos** del modelo de cada módulo que duelen si fallan en producción.

| Ámbito | Caso crítico | Tipo de test | Por qué duele |
|---|---|---|---|
| **D3 — dependencias** | ArchUnit: `…domain.*` sin imports de Spring, JPA, Jackson. `…application.*` no importa de `…infrastructure.*`. | ArchUnit | Sin esto, el dominio puro deja de ser puro silenciosamente. |
| **D4 — eventos** | Test ArchUnit: clases en `…domain.events.*` no implementan `IntegrationEvent`; clases en `…api.events.*` sí. | ArchUnit | Distinción domain/integration roto (ADR-0007 D12). |
| **D6 — dominio puro** | Test unitario del agregado: rechaza estados inválidos en el constructor; cada método de comportamiento devuelve `Either.Left` en violación de invariante. | Unitario | Agregado anémico = reglas de negocio dispersas. |
| **D10 — mapeo** | **Test de roundtrip**: `mapper.toDomain(mapper.toEntity(plan)) == plan` con datos sintéticos representativos. Property-based testing con `kotest`. | Unitario | Un mapeador roto corrompe datos en producción de forma sutil. |
| **D11 — typed IDs** | Test ArchUnit: parámetros de métodos en `…domain.*` que sean IDs **no** son `UUID` ni `String` raw. | ArchUnit | Confusión de IDs en runtime = bugs caros. |
| **D12 — errores** | Cada caso del `sealed class XxxError` del módulo tiene al menos un test que produce ese error. | Unitario | Errores no testados = comportamiento desconocido. |
| **D13 — servicios de dominio** | Test ArchUnit (cuando existan): los servicios de dominio en `…domain.*` solo tienen métodos que toman al menos dos agregados raíz como parámetros. | ArchUnit | DDD anémica encubierta. |
| **D14 — repositorios** | Test ArchUnit: las interfaces de repositorio en `…application.ports.*` solo declaran métodos `guardar`, `buscar`, `existe`, `borrar` con typed IDs. | ArchUnit | Repositorio se convierte en query repository, rompe CQRS ligero. |
| **D15 — transacciones** | Test ArchUnit: toda clase pública en `…application.*` con métodos públicos lleva `@ApplicationService`. Ninguna clase fuera la usa. | ArchUnit | Casos de uso sin transacción → fallos sutiles de consistencia. |
| **D17 — carga eager** | Test de integración con Testcontainers: cargar un `PlanSemanal` ejecuta **una sola query SQL** (verificable con `@SqlMergeMode` o contador de queries de Hibernate). | Integración | N+1 en producción degrada toda la app. |

Los tests **ArchUnit** son los más baratos y los que más errores detectan en build. Los de **mapeo roundtrip** son obligatorios — sin ellos, el doble modelo es un riesgo no mitigado.

## Consecuencias

### Positivas

- Dominio testable sin base de datos ni Spring → suite de tests rápida (< 1 s por módulo).
- Reglas de negocio centralizadas y protegidas por los agregados.
- Código alineado con el lenguaje del discovery — menos malentendidos negocio↔técnico.
- *Bounded context* = módulo → ADR-0007 y ADR-0008 se refuerzan.
- Dominio **literalmente puro**: cambiar el motor de persistencia, o un adaptador, no toca el dominio.
- **Typed IDs eliminan una clase entera de bugs en runtime** (confundir IDs).
- **`Either<XxxError, T>`** como tipo de retorno orienta el código a programación funcional explícita.
- **Konvert** elimina el grueso del *boilerplate* del doble modelo.
- **`@ApplicationService`** identifica casos de uso a primera vista y acopla transaccionalidad sin contaminar firmas.

### Negativas / coste asumido

- **Doble modelo** (agregado de dominio + entidad de persistencia) y *boilerplate* mitigado pero no eliminado.
- Más estructura inicial que las capas tradicionales.
- El equipo debe conocer hexagonal, DDD táctico, programación funcional con Arrow-kt (`Either`, Raise DSL), y `value class` de Kotlin — coste de onboarding; la guía `estructura-de-un-modulo.md` y este ADR como referencia.
- Konvert es una dependencia más; si abandonan el proyecto, hay que migrar (riesgo bajo: el código generado es Kotlin estándar, se puede portar).

### Riesgos y mitigaciones

- **Sobre-ingeniería** (puertos por todo, abstracción gratuita) → la regla de "hexagonal con criterio" (D9); revisión de código; un módulo de ejemplo bien hecho como referencia.
- **Bugs en el mapeo dominio ↔ persistencia** → tests de roundtrip obligatorios (D10); Konvert detecta incompatibilidades en compile-time.
- **Ceremonia estratégica que se cuela** → el *event storming* y el *context mapping* formal están **explícitamente fuera** del MVP (D5).
- **Dominio anémico camuflado** (estructura hexagonal pero la lógica sigue en *services*) → reglas de D13 + heurísticas de detección en revisión humana de PR.
- **Servicios de dominio mal usados** → regla estricta de D13; test ArchUnit que verifica la firma.
- **Repositorios que crecen sin freno** → regla estricta de D14; test ArchUnit que verifica la superficie.

## Notas

- La estructura `domain/application/infrastructure`, un agregado bien modelado, el modelo de persistencia con su mapeador y una proyección están detallados en la guía de referencia [`docs/arquitectura/estructura-de-un-modulo.md`](../arquitectura/estructura-de-un-modulo.md), para acelerar el onboarding del equipo.
- La revisión de los *bounded contexts* mediante técnicas estratégicas formales se reabre solo si el crecimiento del producto lo justifica.
- **Criterios de revisión de "hexagonal con criterio" a 6 meses**: cuando el primer módulo lleve seis meses en desarrollo, se audita la disciplina con tres preguntas concretas — (1) ¿están los agregados protegiendo invariantes de verdad o son `data class` con getters?; (2) ¿hay `@ApplicationService` con > 20 líneas de lógica condicional?; (3) ¿el mapeador se ha vuelto un monstruo (> 15 % del módulo)? Si alguna respuesta es preocupante, se reabre la disciplina (no necesariamente el ADR) con un *refactor* focalizado.
- **Revisión del 2026-07-11 (D12 — auditoría de deriva doc↔código)**: el ADR describía `Result<T, DomainError>` con un `DomainError` compartido; la implementación real usa `Either<XxxError, T>` de Arrow-kt (Raise DSL) con un sealed class de error propio por módulo, sin tipo base compartido — así lo exige `CLAUDE.md` raíz y así lo usan los 18 ficheros del backend que manejan errores. D12 se reescribe con la forma real; D16, la tabla de heurísticas y la tabla de tests críticos se actualizan en cascada. Sin cambio de código.
- **Revisión del 2026-06-16 (fase implementación H0 — D2)**: la implementación del módulo `identidad` confirmó que los puertos pertenecen a `application/ports/`, no a `domain/`. El dominio puro no debería saber nada de cómo se satisfacen sus operaciones (qué repositorio lo persiste, qué adaptador de email usa); ese contrato lo define la capa de aplicación. Cambios: D2 redefine las tres capas con puertos en `application`; D7 actualiza la ubicación de la interfaz del repositorio; D14 ajusta el test ArchUnit a `…application.ports.*`; resumen ejecutivo y tabla de tests actualizados. Alineado con `docs/arquitectura/estructura-de-un-modulo.md` y `backend/CLAUDE.md`.
- **Revisión del 2026-05-29 (Nivel 1 + cierre de la disciplina por módulo)**: el ADR se reestructura con índice, premisas heredadas y criterios de éxito del proceso, y se numeran las sub-decisiones D1-D18 con anchors. Se incorporan nueve sub-decisiones nuevas que cierran las decisiones implícitas que la revisión profunda identificó: **D10 — Konvert** como librería de mapeo (KSP, Kotlin-first, compile-time); **D11 — typed IDs** con `@JvmInline value class` envolviendo `UUID` v7 (coherente con ADR-0004 D8); **D12 — `Result<T, DomainError>`** como manejo de errores en el dominio (orientado a programación funcional); **D13 — regla estricta de servicios de dominio** (solo entre varios agregados raíz); **D14 — repositorios estrictos** (solo `guardar` / `buscar` por ID); **D15 — transacciones por AOP** con meta-anotación `@ApplicationService` a nivel de clase (sin anotaciones en métodos); **D16 — validación de forma en controller + invariantes en agregado**; **D17 — carga eager** de agregados con sus entidades hijas; **D18 — factories y specifications fuera del MVP** con razón explícita. Se añaden además: aclaración sobre la distinción de eventos en cruce con ADR-0007 D12, coherencia explícita con UUID v7 y `TIMESTAMPTZ` de ADR-0004, tabla de heurísticas operativas, patrón de mapeo de entidades hijas con `PlanSemanal`, tres reglas para detectar DDD anémica encubierta, tabla de tests críticos cruzando con ADR-0010 y criterios de revisión a 6 meses. Alineado con ADR-0001, ADR-0002, ADR-0003, ADR-0004 y ADR-0007 ya aceptados.
