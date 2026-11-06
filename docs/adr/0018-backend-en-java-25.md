# ADR-0018 — Backend en Java 25 LTS (reemplaza Kotlin)

- **Estado**: Propuesto
- **Fecha**: 2026-11-06
- **Decisores**: Negocio (Antonio) · Claude
- **Relacionado con**: ADR-0001 D2 (lenguaje Kotlin, reemplazado por D1), ADR-0001 D12 (política LTS), ADR-0007 (outbox de Spring Modulith), ADR-0008 D10/D11/D12 (Konvert, `value class`, `Either`, reemplazados por D6/D5/D4), ADR-0009 D9/D11 (*staleness* fail-closed, `@AuthScope`), ADR-0010 D4/D7 (trunk-based y quality gates, excepción acotada en D10 y cambio de linters en D7), ADR-0016 D3/D4 (target 21 y build stage `:21`, reemplazados por D2)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta**: el backend deja Kotlin y pasa a Java 25 LTS. Las 11 sub-decisiones se agrupan en cuatro áreas:

- **Lenguaje y toolchain (D1-D3)** — Java 25 LTS, compilación a 25 y null-safety por anotaciones.
- **Modelado (D4-D6)** — errores como excepciones checked, records y MapStruct.
- **Calidad y tests (D7-D9)** — linters de Java, Spock y principios de diseño en el port.
- **Migración (D10-D11)** — rama larga de integración e invariantes de despliegue.

| #   | Sub-decisión                                                                                       | Capa        |
|-----|----------------------------------------------------------------------------------------------------|-------------|
| D1  | [Lenguaje del backend: Java 25 LTS](#d1)                                                           | Estratégica |
| D2  | [Toolchain: compilación, build stage y CI en Java 25](#d2)                                         | Operativa   |
| D3  | [Null-safety: JSpecify `@NullMarked` + NullAway como error de compilación](#d3)                    | Estratégica |
| D4  | [Modelo de errores: excepciones checked selladas por módulo](#d4)                                  | Estratégica |
| D5  | [Value objects y typed IDs como `record`](#d5)                                                     | Operativa   |
| D6  | [Mapeo dominio ↔ persistencia: MapStruct](#d6)                                                     | Operativa   |
| D7  | [Calidad estática: Spotless + Error Prone + NullAway](#d7)                                         | Operativa   |
| D8  | [Tests: Spock 2.4 sustituye a Kotest + MockK; ArchUnit sigue en JUnit 5](#d8)                      | Estratégica |
| D9  | [Principios de diseño en el port: Effective Java y SOLID, patrones solo con un problema concreto](#d9) | Operativa   |
| D10 | [Estrategia: rama larga de integración, excepción acotada a ADR-0010 D4](#d10)                     | Estratégica |
| D11 | [Invariantes de despliegue: outbox, sesiones y contrato externo](#d11)                             | Operativa   |

## Contexto y problema

ADR-0001 D2 eligió Kotlin para el backend por dos razones: la seguridad frente a nulos en el sistema de tipos y la sinergia con una futura app Android. Negocio decide ahora estandarizar el backend en Java.

Motivación de negocio: _pendiente de completar por Antonio en la revisión de esta PR_.

El backend tiene a fecha de este ADR 866 ficheros `.kt`: unas 29,5 k líneas de producción y 45,6 k de tests. Usa construcciones que no existen en Java:

- El Raise DSL de Arrow-kt en 123 ficheros: 109 bloques `either { }`, 190 `ensure` y 78 `ensureNotNull`.
- 27 `value class` y 138 `data class`.
- Konvert (KSP) para los mappers.
- Kotest y MockK en todos los tests.

Hay que decidir a qué Java se migra, qué sustituye a cada una de esas construcciones y cómo se migra sin parar el trabajo de H1.

## Premisas heredadas (no se revisan en este ADR)

- **Spring Boot 4.x + Spring Modulith 2.x** (ADR-0001 D12, ADR-0007). Solo cambia el lenguaje, no el framework.
- **Hexagonal + DDD táctico** (ADR-0008 D1-D9, D13-D18). Capas `domain` / `application` / `infrastructure` / `api`, dominio puro, puertos en `application/ports/`, sin Factories ni Specifications (D18).
- **Regla de idioma de los identificadores** (ADR-0008 D4). Inglés en el código, castellano en paquetes raíz, SQL y valores de enum persistidos.
- **Runtime GraalVM CE 25 en modo JIT** (ADR-0016). No cambia: este ADR solo alinea la compilación con él.
- **Events-first con outbox** (ADR-0007). Sin llamadas síncronas entre módulos.
- **Autorización en tres capas** (ADR-0009). `AuthorizationMatrix`, comprobación a nivel de objeto y `@AuthScope` con fail-closed.
- **Contrato OpenAPI primero** (ADR-0001 D10). La API REST no cambia con la migración.
- **Política LTS** (ADR-0001 D12). *"Java: nos quedamos en la LTS vigente"*.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| Comportamiento observable | Sin cambios: API REST, esquemas SQL y JSON Schemas de eventos idénticos |
| Quality gates (ADR-0010 D7) | Todos en verde en cada sub-PR, con los linters de D7 en lugar de detekt/ktlint |
| Eventos pendientes en `event_publication` al desplegar | 100 % deserializables por las clases Java |
| Sesiones de usuario | Como máximo un cierre de sesión forzado, en el despliegue del merge final |
| Desfase de la rama de integración con `main` | ≤ 7 días |

## Drivers de la decisión

- Conservar la arquitectura vigente (hexagonal, events-first, autorización en tres capas) sin reabrir sus ADRs.
- Que la migración sea invisible hacia fuera: ni la API, ni la base de datos, ni el outbox cambian.
- Recuperar la protección frente a nulos que justificaba ADR-0001 D2, con herramientas de Java.
- Cumplir la política LTS de ADR-0001 D12 y mantener el runtime GraalVM CE de ADR-0016.
- Que cada paso sea revisable: el port no se mezcla con rediseños.

## Opciones consideradas

- **Opción A** — Mantener Kotlin.
- **Opción B** — Java 25 LTS.
- **Opción C** — Java 27, la última GA a fecha de este ADR.

### Opción A — Mantener Kotlin

- 👍 Coste cero. El camino crítico de H1 no se retrasa.
- 👍 Conserva la null-safety en el sistema de tipos, el Raise DSL, `copy()` y las `value class` sin asignación.
- 👎 No responde a la decisión de negocio de estandarizar en Java.
- 👎 Mantiene la toolchain propia de Kotlin: KSP, plugins `kotlin-spring` y `kotlin-jpa`, detekt en versión alpha.

### Opción B — Java 25 LTS

- 👍 Es la LTS vigente (ADR-0001 D12) y la versión del runtime de producción (ADR-0016). El target de compilación se alinea con el runtime sin cambiar de JDK en producción.
- 👍 El lenguaje cubre ya lo esencial del modelado actual: records, jerarquías `sealed`, `switch` exhaustivo con *pattern matching*.
- 👍 La toolchain se simplifica: sin KSP, sin plugins de Kotlin y sin detekt alpha.
- 👎 La null-safety pasa del sistema de tipos a un *checker* (D3).
- 👎 El código es más verboso: *withers* en lugar de `copy()`, sobrecargas o *parameter objects* en lugar de argumentos por defecto, sin funciones de extensión.
- 👎 Coste alto: reescribir unas 75 k líneas en mitad de H1.

### Opción C — Java 27

- 👍 Es la última GA (15-sep-2026).
- 👎 No es LTS: soporte solo hasta marzo de 2027, lo que obliga a migrar a Java 28 en seis meses.
- 👎 GraalVM no publicará versiones para JDK 26, 27 ni 28. Obliga a cambiar el runtime y reabrir ADR-0016 entero.
- 👎 Incumple la política LTS de ADR-0001 D12.

## Decisión

**Opción B — Java 25 LTS.** Es la única que responde a la decisión de negocio sin romper la política LTS ni el runtime de ADR-0016.

Las 11 sub-decisiones se desarrollan a continuación. Cinco son **estratégicas** (D1, D3, D4, D8 y D10 fijan el lenguaje, el modelo de errores, la null-safety, los tests y la estrategia de migración); el resto son **operativas** y derivan de ellas.

<a id="d1"></a>
### D1 — Lenguaje del backend: Java 25 LTS

Todo el código de producción de `backend/src/main`, incluido el generado desde OpenAPI, pasa a Java 25. **Reemplaza ADR-0001 D2.**

- Sin `--enable-preview`: ninguna característica en *preview* entra en el código (ADR-0001 D12, estabilidad sobre novedad).
- La regla de idioma de ADR-0008 D4 se aplica igual a los identificadores Java.
- La siguiente LTS (Java 29, prevista para septiembre de 2027) dispara la revisión ordinaria de ADR-0001 D12.

<a id="d2"></a>
### D2 — Toolchain: compilación, build stage y CI en Java 25

**Reemplaza ADR-0016 D3 y D4** en lo relativo al target de compilación y al build stage. El runtime GraalVM CE 25 en modo JIT no cambia.

- Toolchain de Gradle con `languageVersion = 25` y `options.release = 25`.
- Flag `-parameters` explícito en `JavaCompile`. `AuthScopeEnforcementAspect` localiza el parámetro `clubId` por nombre.
- Durante la fase mixta, `jvmTarget` de Kotlin también a 25: Gradle falla si los targets de `compileJava` y `compileKotlin` difieren.
- Build stage del Dockerfile en `ghcr.io/graalvm/jdk-community:25`, igual que el runtime stage.
- CI con `graalvm/setup-graalvm@v1` y `java-version: '25'`.
- En local, la toolchain nueva es una descarga de JDK nueva: el certificado corporativo se importa con el `keytool` de esa toolchain.

<a id="d3"></a>
### D3 — Null-safety: JSpecify `@NullMarked` + NullAway como error de compilación

Sustituye la null-safety que daba el sistema de tipos de Kotlin, el argumento central de ADR-0001 D2.

- Cada paquete declara `@NullMarked` en su `package-info.java`: lo no anotado es no nulo.
- Lo que puede ser nulo se anota `@Nullable` (JSpecify). En retornos de consulta se prefiere `Optional` (D9).
- NullAway corre sobre Error Prone con severidad `ERROR`: un posible NPE rompe la compilación.
- Kotlin 2.x interpreta JSpecify, así que la frontera Kotlin ↔ Java sigue chequeada durante la fase mixta.
- Se excluye el código generado (`build/generated`: OpenAPI, MapStruct).

<a id="d4"></a>
### D4 — Modelo de errores: excepciones checked selladas por módulo

**Reemplaza ADR-0008 D12.** Los fallos de negocio dejan de ser valores `Either<XxxError, T>` y pasan a ser excepciones checked (Effective Java, ítem 70: excepciones checked para condiciones recuperables).

- **Una jerarquía sellada por módulo**, sin núcleo compartido, igual que hoy: `public abstract sealed class SeguimientoException extends Exception permits …`, con subclases `final`. Cada variante actual de `XxxError` pasa a ser una subclase con su `code` y su `field` opcional.
- **El dominio declara y lanza** la excepción de su módulo. Sigue siendo dominio puro: `java.lang.Exception` no es framework.
- **Sin traza de pila** en las excepciones de negocio esperadas (`super(message, null, false, false)`). Son flujo de control previsto, no fallos de programación.
- **Precondiciones imposibles** con excepciones *unchecked* (`IllegalArgumentException`, `IllegalStateException`, `Objects.requireNonNull`). Sustituyen a `require`/`check`.
- **Traducción en la frontera** (ítem 73). Un `@RestControllerAdvice` por módulo traduce la excepción a `{ code, field?, message }` con el mismo status HTTP que hoy (ADR-0012 D19).
- **Listeners.** Cada `@ApplicationModuleListener` captura la excepción de su módulo exactamente donde hoy trata el `Left` (por ejemplo, el `onLeft { log.debug }` de `HealthConsentListener`). Un listener que hoy no propaga un fallo no empieza a propagarlo: dejaría la publicación incompleta en `event_publication`, `PendingDeliveryLag` crecería y la *staleness* fail-closed de ADR-0009 D9 bloquearía operaciones a los 60 s.
- **Transacciones.** Spring no hace *rollback* ante excepciones checked por defecto. Es la misma semántica que hoy tiene un `Left`, que también hace *commit* de lo ya escrito. El port la conserva. Cambiar `rollbackFor` es una decisión aparte y explícita.
- **Lambdas.** Las excepciones checked no atraviesan `java.util.function`. Donde un *stream* tendría que lanzarlas se usa un bucle. Nunca se envuelven en una *unchecked* para esquivar al compilador (ítem 77: no ignorar excepciones).
- **Error de contrato.** El test que comprueba que cada `code` emitido tiene mensaje en el catálogo del frontend se adapta a las rutas Java y sigue exigiendo que encuentre al menos un código.

Alternativas descartadas en esta sub-decisión:

- Un `Result<E, T>` sellado propio. Conserva los errores como valores, pero sin Raise DSL cada `ensure` se vuelve una *guard clause* con `instanceof Err`, y el compilador no obliga a tratar el error.
- Vavr `Either`. Añade una dependencia sin la ergonomía del Raise DSL.

<a id="d5"></a>
### D5 — Value objects y typed IDs como `record`

**Reemplaza ADR-0008 D11.**

- Typed IDs como `record PlanId(UUID value)`. La validación va en el constructor compacto; la factoría estática `newId()` genera el UUID v7. A diferencia de la `value class`, el record es un objeto real en el heap: se acepta el coste.
- `data class` pasa a `record`. `copy()` pasa a *withers* explícitos solo donde se usen.
- `sealed class` / `sealed interface` pasan a `sealed interface` con records, consumidos con `switch` exhaustivo.
- Las colecciones que guarda un record se copian en el constructor (`List.copyOf`, ítem 50).
- Las entidades JPA siguen siendo clases: un record no puede ser una entidad.
- Converters JPA y Jackson explícitos por typed ID, porque ya no se borran a `UUID` en bytecode.
- `AuthScopeEnforcementAspect` y su regla ArchUnit aceptan `ClubId` además de `UUID`, sin perder el fail-closed. Antes de portar `shared` se escribe el test de club no coincidente contra el record.

<a id="d6"></a>
### D6 — Mapeo dominio ↔ persistencia: MapStruct

**Reemplaza ADR-0008 D10.** Konvert depende de KSP y no existe para Java. MapStruct es el equivalente en tiempo de compilación.

- `unmappedTargetPolicy = ERROR`: un campo sin mapear rompe la compilación.
- Los mappers siguen en `infrastructure`; el modelo de persistencia sigue separado del dominio (ADR-0008 D7).

<a id="d7"></a>
### D7 — Calidad estática: Spotless + Error Prone + NullAway

**Reemplaza detekt + ktlint** en los quality gates de ADR-0010 D7.

- Formato con Spotless y `palantir-java-format` (4 espacios, 120 columnas, como el estilo actual).
- Error Prone con NullAway (D3) como bloqueantes en cada PR.
- detekt y ktlint conviven mientras quede un `.kt` y se retiran en la limpieza final (D10).

<a id="d8"></a>
### D8 — Tests: Spock 2.4 sustituye a Kotest + MockK; ArchUnit sigue en JUnit 5

- **Spock 2.4** sobre la plataforma JUnit, en la variante Groovy 5.0: es la línea de Groovy que gestiona el BOM de Spring Boot 4.1 (5.0.x), así que no hay dos Groovy en conflicto. Se descarta Spock 2.5, publicado en octubre de 2026, por la política de estabilidad de ADR-0001 D12.
- Mocks con los de Spock; en los *slices* de Spring, `spock-spring` con `@SpringBean`.
- Testcontainers con PostgreSQL real, como hoy.
- Los tests de contrato llevan `@Tag("contract")` de Spock y el filtro de `contractTest` debe recogerlos.
- **ArchUnit sigue en JUnit 5, escrito en Java**: el motor `archunit-junit5` es específico de JUnit 5.
- Groovy entra como lenguaje de tests. Durante la transición el backend tiene tres lenguajes (Kotlin, Java, Groovy).
- **Gate previo.** El PR de toolchain demuestra cuatro cosas antes de portar ningún test: una *spec* contra una clase compilada a Java 25, un *slice* `@WebMvcTest` con `@SpringBean`, una *spec* `@Tag("contract")` recogida por `contractTest`, y PITest ejecutando una *spec*. Si alguna falla, se reabre esta sub-decisión antes de seguir.

<a id="d9"></a>
### D9 — Principios de diseño en el port: Effective Java y SOLID, patrones solo con un problema concreto

**El port no es un rediseño.** Cada PR de port traduce el comportamiento existente; si además cambiara el diseño, nadie podría revisar que el comportamiento se conserva.

Lo que el port aplica por defecto (Effective Java, 3.ª ed., aplicado a Java 25):

- Factorías estáticas con nombre (ítem 1): `Alumno.invitar(...)`, `PlanId.newId()`.
- Inmutabilidad (ítem 17): records y campos `final`. Copias defensivas (ítem 50).
- Mínima accesibilidad (ítem 15): visibilidad de paquete para lo que no es API del paquete.
- Validación de parámetros (ítem 49) en constructores compactos.
- `Optional` solo como retorno (ítem 55), nunca en campos ni parámetros.
- *Parameter objects* (record) en lugar de listas largas de argumentos, porque Java no tiene argumentos por defecto.
- Inyección por constructor, dependencias por interfaz (ítem 64).

SOLID ya está en la arquitectura y el port lo conserva:

- Responsabilidad única: un caso de uso por clase `@ApplicationService`.
- Segregación de interfaces e inversión de dependencias: puertos estrechos en `application/ports/`, adaptadores en `infrastructure`.

Patrones:

- Los presentes se conservan: Repository, Adapter (puertos y adaptadores), Observer vía eventos, y estados de agregado como jerarquías selladas.
- Un patrón nuevo solo entra en un **PR de diseño aparte** y con un problema concreto que resolver.
- Siguen vigentes ADR-0008 D9 (puertos solo para lo que cruza de verdad) y D18 (sin objetos Factory ni Specifications).

<a id="d10"></a>
### D10 — Estrategia: rama larga de integración, excepción acotada a ADR-0010 D4

ADR-0010 D4 prohíbe código *long-lived* fuera de `main`. La migración es una **excepción acotada**: vive en la rama `feature/migracion-backend-java` hasta su merge final.

- **Sub-PRs contra la rama de integración**, con la misma CI que `main`: el disparador `pull_request` de `ci.yml` no filtra por rama base.
- **Sincronización semanal** con `origin/main` mediante merge. En cada sincronización se portan a Java los cambios de `backend/` hechos en `main` desde el último punto de merge (`git log $(git merge-base …)..origin/main -- backend/`).
- **Orden**:
  1. Toolchain, con el gate de D8.
  2. Spike de una rebanada vertical completa (dominio, caso de uso, repositorio `@AuthScope`, mapper, listener, evento, controlador) con ArchUnit, Modulith y contract tests en verde.
  3. Un módulo tras otro en el orden del grafo de eventos: `shared` → `identidad` → `clubtaxonomia` → `planificacion` → `seguimiento` → `auditoria`.
  4. Generador OpenAPI de `kotlin-spring` a `spring`.
  5. Barrido de documentación y limpieza: fuera los plugins de Kotlin, KSP, Konvert, detekt, ktlint, Kotest y MockK.
- **Dentro de cada módulo, dos PRs.** El primero porta la producción y adapta de forma mecánica las aserciones de los tests Kotlin (`shouldBeLeft(X)` pasa a `shouldThrow<XException>`), sin tocar nada más: así el revisor ve que el oráculo no se ha debilitado. El segundo porta esos tests a Spock.
- **Condiciones del merge final a `main`**: este ADR aceptado, ningún `.kt` en `backend/src` y todos los quality gates en verde.

<a id="d11"></a>
### D11 — Invariantes de despliegue: outbox, sesiones y contrato externo

- **Outbox.** `event_publication` guarda el nombre de la clase y el JSON de cada evento. Los integration events conservan paquete, nombre (incluido el anidamiento `Outer$Inner`) y forma JSON. Antes de portar cada evento se capturan sus *payloads* Kotlin actuales como *fixtures* y un test comprueba que el record Java los deserializa. Hay que vigilar los booleanos con prefijo `is` y los valores por defecto que hoy pone Kotlin.
- **Sesiones.** `Principal` y `SessionAuthenticationDetails` son `Serializable` dentro de Spring Session JDBC. Su forma serializada cambia con el port, así que las sesiones vivas no se podrán leer: el despliegue del merge final cierra la sesión de todos los usuarios una vez. Se anuncia antes del despliegue.
- **Contrato externo.** La API REST, los esquemas SQL y los JSON Schemas de `schemas/` no cambian. Cualquier cambio de contrato va en un PR propio fuera de la migración.

## Lo que este ADR no decide

- Cambiar la semántica transaccional ante errores de negocio (`rollbackFor`). Se decide aparte, si se quiere.
- Rediseños de módulos o patrones nuevos. Van en PRs de diseño propios (D9).
- La app Android post-MVP. Pierde la sinergia de lenguaje de ADR-0001 D2; su lenguaje se decide cuando se abra.

## Consecuencias

### Positivas

- El target de compilación se alinea con el runtime de producción (Java 25) sin el apaño del target 21 de ADR-0016.
- La toolchain se simplifica: sin KSP, sin plugins `kotlin-spring` / `kotlin-jpa` y sin detekt alpha.
- Los errores de negocio quedan en la firma de los métodos (`throws`) y el compilador obliga a tratarlos.
- MapStruct es una dependencia madura y muy usada.

### Negativas / coste asumido

- La null-safety deja de ser del sistema de tipos. NullAway es un *checker*: no cubre las librerías sin anotar.
- Más verbosidad: *withers*, *parameter objects*, sin funciones de extensión.
- Las excepciones checked no atraviesan lambdas: hay *streams* que pasan a bucles.
- Groovy es un lenguaje dinámico: los tests pierden parte de la seguridad de refactor del IDE.
- La rama larga contradice el trunk-based de ADR-0010 D4: conflictos con `main` y un único despliegue grande al final.
- El camino crítico de H1 se retrasa. El cuello de botella es revisar unas 75 k líneas.
- Se pierde la sinergia con una futura app Android en Kotlin.

### Riesgos y mitigaciones

- **Eventos del outbox no deserializables tras el despliegue** → *fixtures* de los *payloads* Kotlin actuales y test de deserialización por evento (D11).
- **Un listener que empieza a propagar errores** → regla de captura en el mismo punto que hoy trata el `Left` (D4).
- **`AuthScopeEnforcementAspect` con `ClubId` como record** → test de club no coincidente antes de portar `shared`; el aspecto sigue fallando cerrado (D5).
- **Spock o PITest incompatibles con Java 25** → gate del PR de toolchain; si falla, se reabre D8.
- **Deriva entre `main` y la rama de integración** → sincronización semanal y lista de cambios de `backend/` a portar (D10).
- **Errores de TLS al descargar dependencias en local con el JDK nuevo** → importar el certificado corporativo con el `keytool` de la toolchain 25 (D2).

## Criterios de éxito

| # | Métrica | Umbral verde | Atribuible a | Cómo medirla |
|---|---------|--------------|--------------|--------------|
| M1 | Ficheros `.kt` en `backend/src` al merge final | 0 | D10 | `git ls-files 'backend/src/**/*.kt' \| wc -l` |
| M2 | Publicaciones de eventos fallidas por deserialización tras el despliegue final | 0 | D11 | `event_publication` incompletas con error de deserialización en los logs |
| M3 | Desfase de la rama de integración con `main` | ≤ 7 días | D10 | Fecha del último merge de `origin/main` en la rama |
| M4 | *Mutation score* de `domain` en el nightly | ≥ el último nightly en Kotlin | D8 | Informe de PITest |

Una métrica en rojo al merge final lo bloquea. Dos semanas seguidas con M3 en rojo reabren D10 (pasar a PRs por módulo directos a `main`).

## Notas

- Las premisas heredadas son **invariantes de este ADR**: si cambian, este ADR se revisita.
- Al aceptarse, cada ADR afectado se marca en su propia PR de revisión (`feature/revision-adr-NNNN`): ADR-0001 D2, ADR-0008 D10/D11/D12, ADR-0010 D4/D7 y ADR-0016 D3/D4.
- Antes del merge final, el agente `adr-impact-analyzer` dimensiona el barrido de documentación: los dos `CLAUDE.md`, `docs/arquitectura/` (con el ejemplo Kotlin de `estructura-de-un-modulo.md`), las skills `module-scaffold` y `backend-feature-dev`, y los agentes `module-code-reviewer` y `event-contract-reviewer`.
- **Disparadores de reapertura**: un gate de D8 en rojo reabre D8; dos semanas con M3 en rojo reabren D10.
