# Testing de módulos — guía de referencia

Subdocumento de [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md). Cubre los **detalles de testing por módulo** que la guía principal resume: pirámide, stack, fixtures, tests de acceso cruzado, ArchUnit, tests de contrato de eventos.

> Espejo aplicado de **ADR-0010 D8** (pirámide de tests, Testcontainers), **ADR-0009 D13/D14** (ArchUnit guards + tests de acceso cruzado), **ADR-0008 D6/D14** (dominio puro testeable + reglas arquitectónicas), **ADR-0007 D11** (versionado de eventos con JSON Schema). Si hay conflicto, gana el ADR.

## 1. Pirámide de tests del módulo

Cada módulo cumple una pirámide concreta antes de considerarse listo. Ningún nivel es opcional sin justificación en PR.

```
            ▲
           / \                                Lentos, pocos
          /   \    Acceso cruzado +           — uno por caso de uso con
         /     \   contrato eventos           nivel de objeto (ADR-0009 D14)
        /-------\                             — contrato CI dedicado (ADR-0007 D11)
       /         \
      / Integración \    Testcontainers       — flujo casos de uso end-to-end
     /              \    Postgres real        — listeners, proyecciones,
    /----------------\                          autorización con BD
   /                  \
  /     Unitarios       \  JUnit 5 +          — agregados (require/Either)
 /                       \ Kotest + MockK     — value objects, builders,
/-------------------------\                     pure logic
                                              — muchos, rápidos
                  ArchUnit
       (capas + autorización + Modulith)      — reglas estáticas, parten CI
```

### Distribución típica por módulo

| Nivel | Cantidad esperada | Velocidad | Objetivo |
|---|---|---|---|
| Unitarios | 60-80 % | ms | Lógica del dominio, agregados, value objects, builders |
| Integración | 15-30 % | 100-500 ms | Casos de uso con BD real, listeners, proyecciones |
| Acceso cruzado | 1 por caso de uso con nivel de objeto | similar a integración | Cerrar IDOR por construcción (ADR-0009 D14) |
| ArchUnit | ~10-20 reglas | ms | Capas, imports prohibidos, autorización, Modulith |
| Contrato JSON Schema | 1 por integration event publicado | en CI dedicado | El código y el schema no divergen (ADR-0007 D11) |

## 2. Stack de tests del backend

| Pieza | Elección | Por qué |
|---|---|---|
| **Runner JVM** | **JUnit 5 (Jupiter)** | Integración nativa con Spring Boot Test, más extendido |
| **Assertions** | **Kotest assertions** | Syntax fluida idiomatic Kotlin (`shouldBe`, `shouldBeLeft<XxxError.Forbidden>()`, `shouldBeInstanceOf`) |
| **Mocking** | **MockK** | Idiomatic Kotlin, sin friction con `final` classes, coroutines y `value class` |
| **Integración** | **Testcontainers PostgreSQL** | Postgres real con la misma versión de producción (ADR-0010 D8) |
| **Arquitectura** | **ArchUnit** | Reglas estáticas verificables en CI (ADR-0008 D14, ADR-0009 D13) |
| **Fronteras de Modulith** | **Spring Modulith Test** | Verifica que los módulos no se llaman síncronamente entre sí (ADR-0007 D2) |
| **HTTP** | **MockMvc** o **WebTestClient** | Estándar Spring Boot Test |
| **JSON Schema** | **json-schema-validator** (`networknt/json-schema-validator`) | En CI dedicado para tests de contrato |
| **Property-based** (opcional) | **Kotest Property** | Para invariantes del agregado cuando aporta |

### Dependencias típicas (`build.gradle.kts`)

```kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")  // usamos MockK
    }
    testImplementation("io.kotest:kotest-assertions-core:5.x")
    testImplementation("io.kotest:kotest-assertions-arrow:5.x")  // shouldBeRight, shouldBeLeft
    testImplementation("io.mockk:mockk:1.x")
    testImplementation("org.testcontainers:postgresql:1.x")
    testImplementation("org.testcontainers:junit-jupiter:1.x")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.x")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")

    // Solo para el job CI de contrato de eventos
    testImplementation("com.networknt:json-schema-validator:1.x")
}
```

## 3. Tests unitarios del dominio

**Sin Spring. Sin Testcontainers. Sin BD.** Kotlin puro + Kotest + MockK.

### Patrón: agregado con `Either`

```kotlin
// test/kotlin/com/runcriticon/planificacion/domain/PlanSemanalTest.kt
class PlanSemanalTest : BehaviorSpec({

    given("un PlanSemanal en BORRADOR con sesiones") {
        val plan = PlanSemanalBuilder().enBorrador().conTresSesiones().build()

        `when`("se publica") {
            val resultado = plan.publicar()

            then("devuelve Either.Right con el evento PlanPublicado") {
                val evento = resultado.shouldBeRight()
                evento.aggregateId shouldBe plan.id.value
                evento.version shouldBe 1
            }

            then("queda en estado PUBLICADO") {
                plan.publicar()   // segunda llamada
                plan.estado shouldBe EstadoPlan.PUBLICADO
            }
        }
    }

    given("un PlanSemanal ya PUBLICADO") {
        val plan = PlanSemanalBuilder().publicado().conTresSesiones().build()

        `when`("se publica de nuevo") {
            val resultado = plan.publicar()

            then("devuelve Either.Left con PlanYaPublicado") {
                resultado.shouldBeLeft<PlanificacionError.PlanYaPublicado>()
            }
        }
    }

    given("un PlanSemanal en BORRADOR sin sesiones") {
        val plan = PlanSemanalBuilder().enBorrador().sinSesiones().build()

        `when`("se publica") {
            val resultado = plan.publicar()

            then("devuelve Either.Left con SinSesiones") {
                resultado.shouldBeLeft<PlanificacionError.SinSesiones>()
            }
        }
    }
})
```

### Patrón: `require` en precondiciones imposibles

```kotlin
class PlanSemanalRequireTest {

    @Test
    fun `marcarSesionEjecutada con sesionId que no pertenece al plan lanza IllegalArgumentException`() {
        val plan = PlanSemanalBuilder().conTresSesiones().build()
        val sesionAjena = SesionId.nuevo()

        shouldThrow<IllegalArgumentException> {
            plan.marcarSesionEjecutada(sesionAjena)
        }
    }
}
```

Las precondiciones imposibles (`require`) son **bug del caller**. Se prueban con `shouldThrow` (Kotest) y son raras: validan que el agregado **no se deja engañar** si llega un caller defectuoso.

### Patrón: value objects con casos límite

```kotlin
class RitmoTest : FunSpec({

    test("Absoluto rechaza segPorKm <= 0") {
        shouldThrow<IllegalArgumentException> { Ritmo.Absoluto(0) }
        shouldThrow<IllegalArgumentException> { Ritmo.Absoluto(-30) }
    }

    test("Relativo permite delta negativo (más rápido que la marca)") {
        val ritmo = Ritmo.Relativo(referencia = Distancia.MARATON, deltaSegPorKm = -5)
        ritmo.deltaSegPorKm shouldBe -5
    }
})
```

## 4. Tests de integración con Testcontainers

### Configuración base

```kotlin
// test/kotlin/com/runcriticon/IntegrationTestBase.kt
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("runcriticon_test")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url",       postgres::getJdbcUrl)
            registry.add("spring.datasource.username",  postgres::getUsername)
            registry.add("spring.datasource.password",  postgres::getPassword)
        }
    }
}
```

### Patrón: caso de uso con BD real

```kotlin
// test/kotlin/com/runcriticon/planificacion/PublicarPlanIntegrationTest.kt
@Transactional       // rollback al final del test (decisión: aislamiento por rollback)
class PublicarPlanIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var publicarPlan: PublicarPlanService
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun loginComoEntrenador() {
        TestPrincipalContext.set(PrincipalBuilder().entrenador().build())
    }

    @Test
    fun `publicar plan en BORRADOR persiste estado y publica PlanPublicado en el outbox`() {
        // GIVEN
        val plan = PlanSemanalBuilder()
            .enBorrador()
            .conTresSesiones()
            .delEntrenadorActual()
            .build()
        repositorio.guardar(plan)

        // WHEN
        val resultado = publicarPlan.ejecutar(plan.id)

        // THEN
        resultado.shouldBeRight()
        val planActualizado = repositorio.buscar(plan.id)!!
        planActualizado.estado shouldBe EstadoPlan.PUBLICADO

        // El evento PlanPublicado se persiste en el outbox dentro de la misma tx
        val eventoEnOutbox = jdbc.queryForList(
            "SELECT event_type FROM public.event_publication WHERE event_type LIKE '%PlanPublicado'",
        )
        eventoEnOutbox.size shouldBe 1
    }
}
```

### Aislamiento por rollback con `@Transactional`

**Patrón estándar**: `@Transactional` envuelve cada test en una transacción que **rollback** al final. Vuelve la BD al estado limpio sin truncates manuales.

**Limitaciones conocidas** (importantes):

1. **Listeners de Spring Modulith en `@ApplicationModuleListener`** corren en la **transacción del publisher** por defecto. El rollback los cubre. ✓
2. **Listeners explícitamente asíncronos** (`@Async`, `@TransactionalEventListener(phase = AFTER_COMMIT)`) corren en otra transacción y **no se hacen rollback**. Para esos casos:
   - Marcar el test con `@Commit` y limpiar manualmente con `@AfterEach`.
   - O usar `TestTransaction.flagForCommit(); TestTransaction.end()` para forzar commit y consumir el listener.
3. **Tests que validan la creación efectiva de filas en outbox** funcionan con rollback porque el INSERT en `event_publication` está en la misma transacción (Spring Modulith lo garantiza).

### Patrón: listener de un evento de otro módulo

```kotlin
class AlumnoAsignadoAGrupoListenerIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var listener: AlumnoAsignadoAGrupoListener
    @Autowired lateinit var proyeccion: MiembrosGrupoProjection
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    @Transactional
    fun `consume AlumnoAsignadoAGrupo actualiza proyeccion miembros_grupo`() {
        // GIVEN
        val evento = AlumnoAsignadoAGrupoBuilder().build()

        // WHEN
        listener.on(evento)

        // THEN
        val miembros = proyeccion.buscar(evento.grupoId)
        miembros.alumnos shouldContain evento.alumnoId
        miembros.lastProcessedEventId shouldBe evento.eventId
        miembros.lastProcessedEventTs shouldBe evento.occurredAt
    }

    @Test
    @Transactional
    fun `consume el mismo evento dos veces es idempotente`() {
        val evento = AlumnoAsignadoAGrupoBuilder().build()

        listener.on(evento)
        listener.on(evento)   // misma idempotencia tabla evento_procesado

        val miembros = proyeccion.buscar(evento.grupoId)
        miembros.alumnos.count { it == evento.alumnoId } shouldBe 1   // no duplicado
    }
}
```

## 5. Tests de acceso cruzado por caso de uso (ADR-0009 D14)

**Obligatorios** por cada `@ApplicationService` que carga o modifica objetos sujetos a nivel de objeto. Cierran IDOR por construcción.

### Patrón canónico

```kotlin
// test/kotlin/com/runcriticon/planificacion/PublicarPlanAccesoTest.kt
@Transactional
class PublicarPlanAccesoTest : IntegrationTestBase() {

    @Autowired lateinit var publicarPlan: PublicarPlanService
    @Autowired lateinit var repositorio: PlanSemanalRepository

    @Test
    fun `entrenador A no puede publicar plan creado por entrenador B`() {
        // GIVEN dos entrenadores del MISMO club con grupos distintos
        val (entrenadorA, entrenadorB) = TestPrincipals.dosEntrenadoresMismoClub()
        val planDeB = PlanSemanalBuilder()
            .enBorrador()
            .delEntrenador(entrenadorB)
            .build()
        repositorio.guardar(planDeB)

        // WHEN entrenador A intenta publicar el plan de B
        TestPrincipalContext.set(entrenadorA)
        val resultado = publicarPlan.ejecutar(planDeB.id)

        // THEN denegación con Forbidden, no NotFound (no se filtra existencia)
        resultado.shouldBeLeft<PlanificacionError.Forbidden>()

        // Y el plan sigue en BORRADOR
        repositorio.buscar(planDeB.id)!!.estado shouldBe EstadoPlan.BORRADOR
    }

    @Test
    fun `entrenador de club X no ve planes de club Y`() {
        // GIVEN un entrenador de cada club
        val (clubX, clubY) = TestClubs.dosClubes()
        val entrenadorX = PrincipalBuilder().entrenador().enClub(clubX).build()
        val entrenadorY = PrincipalBuilder().entrenador().enClub(clubY).build()
        val planDeY = PlanSemanalBuilder().delEntrenador(entrenadorY).enClub(clubY).build()
        repositorio.guardar(planDeY)

        // WHEN entrenador X busca el plan de Y
        TestPrincipalContext.set(entrenadorX)
        val planEncontrado = repositorio.buscar(planDeY.id)

        // THEN repositorio devuelve null (aspecto @AuthScope filtra por club_id)
        planEncontrado shouldBe null
    }
}
```

### Helpers obligatorios

```kotlin
// test/kotlin/com/runcriticon/test/TestPrincipals.kt
object TestPrincipals {
    /** Dos entrenadores del mismo club con IDs distintos. */
    fun dosEntrenadoresMismoClub(): Pair<Principal, Principal> {
        val clubId = UUID.randomUUID()
        return Pair(
            PrincipalBuilder().entrenador().enClub(clubId).build(),
            PrincipalBuilder().entrenador().enClub(clubId).build(),
        )
    }
    fun dosAlumnosMismoClub(): Pair<Principal, Principal> = /* ... */
    fun adminYEntrenador(): Pair<Principal, Principal> = /* ... */
}
```

### Reglas

- **Un test mínimo por caso de uso que carga o modifica un objeto con nivel de objeto**. Si no aplica nivel de objeto (operaciones globales del admin), se documenta con `@NoAuthRequired` en el caso de uso y se anota explícitamente en el test.
- **Tests usan principal real**, no se saltan el aspecto `@AuthScope` con `@NoAuthScope`.
- **El test verifica `Result.Forbidden`, no `NotFound`**, cuando el principal no debería ver el objeto. Excepción: listados filtrados, donde la respuesta correcta es lista vacía.

## 6. Tests de arquitectura (ArchUnit)

Reglas estáticas que se ejecutan en CI. Cualquier violación parte el build.

### Capas y dependencias

```kotlin
// test/kotlin/com/runcriticon/architecture/CapasArchTest.kt
@AnalyzeClasses(packages = ["com.runcriticon"])
class CapasArchTest {

    @ArchTest
    val `domain no depende de application ni infrastructure` =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application..",
                "..infrastructure..",
            )

    @ArchTest
    val `application no depende de infrastructure` =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")

    @ArchTest
    val `domain no importa Spring ni JPA ni Jackson ni SDK AWS` =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "javax.persistence..",
                "com.fasterxml.jackson..",
                "software.amazon.awssdk..",
            )

    @ArchTest
    val `api no depende de domain` =
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..domain..")
}
```

### Autorización

```kotlin
// test/kotlin/com/runcriticon/architecture/AuthorizationArchTest.kt
@AnalyzeClasses(packages = ["com.runcriticon"])
class AuthorizationArchTest {

    // Todo @ApplicationService consulta AuthorizationMatrix (en la propia clase o en una clase
    // anidada/anónima) o se declara exento con @NoAuthRequired/@AuthenticatedOnly de clase.
    @ArchTest
    val `todo @ApplicationService consulta la matriz de autorizacion o se declara exento` =
        classes()
            .that().areAnnotatedWith(ApplicationService::class.java)
            .should(consultaLaMatrizOSeDeclaraExento())   // ArchCondition<JavaClass> custom

    // Todo handler público de un @RestController lleva @Authorize, @NoAuthRequired o @AuthenticatedOnly.
    @ArchTest
    val `todo handler publico de un @RestController declara su decision de autorizacion` =
        methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController::class.java)
            .and().arePublic().and().areMetaAnnotatedWith(RequestMapping::class.java)
            .should().beAnnotatedWith(Authorize::class.java)
            .orShould().beAnnotatedWith(NoAuthRequired::class.java)
            .orShould().beAnnotatedWith(AuthenticatedOnly::class.java)

    @ArchTest
    val `cada @Repository declara @AuthScope o @NoAuthScope` =
        methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Repository::class.java)
            .and().arePublic()
            .should().beAnnotatedWith(AuthScope::class.java)
            .orShould().beAnnotatedWith(NoAuthScope::class.java)

    // Todo @AuthScope(CLUB) declara el parámetro clubId: UUID que AuthScopeEnforcementAspect verifica.
    @ArchTest
    val `todo metodo @AuthScope(CLUB) declara un parametro clubId de tipo UUID` =
        methods()
            .that().areAnnotatedWith(AuthScope::class.java)
            .should(declaraParametroClubIdSiEsScopeClub())   // ArchCondition<JavaMethod> custom

    @ArchTest
    val `no se accede a SecurityContext fuera del nucleo compartido` =
        noClasses().that().resideOutsideOfPackage("..shared.autorizacion..")
            .should().dependOnClassesThat().haveSimpleName("SecurityContextHolder")

    @ArchTest
    val `no se usa HttpSession directa` =
        noClasses().should().dependOnClassesThat().haveSimpleName("HttpSession")
}
```

### Fronteras de Modulith

```kotlin
// test/kotlin/com/runcriticon/architecture/ModulithFronterasTest.kt
class ModulithFronterasTest {

    private val modulos = ApplicationModules.of(RuncriticonApplication::class.java)

    @Test
    fun `los modulos respetan sus fronteras (no llamadas sincronas cruzadas)`() {
        modulos.verify()
    }

    @Test
    fun `documenta los modulos y sus eventos`() {
        Documenter(modulos).writeDocumentation()   // genera PUML / Asciidoc en build/spring-modulith/
    }
}
```

## 7. Tests de contrato del JSON Schema (CI dedicado)

**Decisión**: los tests de contrato del JSON Schema **no corren en cada PR del módulo**. Corren en un **job dedicado** del CI que valida que cada integration event publicado por algún módulo tiene su JSON Schema en `schemas/` y que ambos coinciden.

### Job en CI

```yaml
# .github/workflows/contratos-eventos.yml
name: Contratos de eventos
on:
  pull_request:
    paths:
      - 'backend/src/main/kotlin/com/runcriticon/**/api/events/**'
      - 'schemas/**'
  push:
    branches: [main]

jobs:
  contratos:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      # Acción oficial de GraalVM: setup-java no soporta GraalVM CE (ADR-0016 D6)
      - uses: graalvm/setup-graalvm@v1
        with:
          distribution: graalvm-community
          java-version: '21'
          github-token: ${{ secrets.GITHUB_TOKEN }}
      - name: Ejecutar tests de contrato
        run: ./gradlew :backend:contractTest
```

### Tarea Gradle dedicada

```kotlin
// backend/build.gradle.kts
tasks.register<Test>("contractTest") {
    description = "Valida que los integration events publicados coinciden con su JSON Schema"
    group = "verification"
    useJUnitPlatform {
        includeTags("contract")
    }
}
```

### Patrón de test de contrato

```kotlin
// test/kotlin/com/runcriticon/planificacion/contracts/PlanPublicadoContractTest.kt
@Tag("contract")
class PlanPublicadoContractTest {

    private val schema = JsonSchemaFactory
        .getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(Path.of("../schemas/planificacion/plan-publicado-v1.json").toUri())

    @Test
    fun `PlanPublicado serializado cumple el JSON Schema v1`() {
        val evento = PlanPublicadoBuilder().build()
        val json = objectMapper.valueToTree<JsonNode>(evento)

        val errores = schema.validate(json)

        errores.shouldBeEmpty()
    }

    @Test
    fun `Los 6 campos obligatorios estan presentes en cualquier instancia`() {
        val evento = PlanPublicadoBuilder().build()
        val json = objectMapper.writeValueAsString(evento)

        json shouldContainJsonKey "eventId"
        json shouldContainJsonKey "aggregateId"
        json shouldContainJsonKey "occurredAt"
        json shouldContainJsonKey "version"
        json shouldContainJsonKey "clubId"
        json shouldContainJsonKey "actorId"
    }
}
```

### Versionado de schema y dual-publishing

Cuando un evento cambia de forma rompiente (ADR-0007 D11), durante la ventana de migración de 4 semanas **existen dos schemas válidos**: `plan-publicado-v1.json` y `plan-publicado-v2.json`. Los tests de contrato deben cubrir ambas versiones.

## 8. Fixtures con builders

**Patrón**: cada entidad/agregado/principal tiene un builder con **valores defaults razonables** que satisfacen invariantes. Cada test sobreescribe **sólo lo que le interesa**.

### Builder canónico

```kotlin
// test/kotlin/com/runcriticon/planificacion/fixtures/PlanSemanalBuilder.kt
class PlanSemanalBuilder {
    private var id            = PlanId.nuevo()
    private var clubId        = ClubId(UUID.randomUUID())
    private var entrenadorId  = EntrenadorId(UUID.randomUUID())
    private var estado        = EstadoPlan.BORRADOR
    private var semanaInicio  = LocalDate.of(2026, 6, 1)
    private var semanaFin     = LocalDate.of(2026, 6, 7)
    private var sesiones      = mutableListOf<Sesion>()

    fun enBorrador()                    = apply { estado = EstadoPlan.BORRADOR }
    fun publicado()                     = apply { estado = EstadoPlan.PUBLICADO }
    fun delEntrenador(p: Principal)     = apply { entrenadorId = EntrenadorId(p.userId); clubId = ClubId(p.clubId) }
    fun enClub(clubId: UUID)            = apply { this.clubId = ClubId(clubId) }
    fun conTresSesiones()               = apply { sesiones = mutableListOf(
        SesionBuilder().dia(0).rodaje().build(),
        SesionBuilder().dia(2).series().build(),
        SesionBuilder().dia(5).tiradaLarga().build(),
    ) }
    fun sinSesiones()                   = apply { sesiones.clear() }

    fun build(): PlanSemanal = PlanSemanal.reconstruir(
        id = id, clubId = clubId, entrenadorId = entrenadorId,
        sesiones = sesiones, estado = estado,
        semanaInicio = semanaInicio, semanaFin = semanaFin,
    )
}
```

### Reglas para los builders

- **Defaults satisfacen invariantes**: cualquier `build()` sin overrides produce un objeto válido del dominio.
- **Métodos verbosos**: `enBorrador()` no `setEstado(BORRADOR)`. Lenguaje ubicuo en los tests también.
- **`fluent interface`** con `apply { ... }` para encadenar.
- **Ningún builder devuelve un objeto medio construido**. Si la invariante exige algo, el builder lo rellena en `build()`.

### Object Mother para escenarios completos

Cuando varios builders deben coordinarse, usar Object Mother:

```kotlin
// test/kotlin/com/runcriticon/planificacion/fixtures/EscenarioClubPequeno.kt
object EscenarioClubPequeno {
    /**
     * Un club con: 1 admin, 2 entrenadores, 10 alumnos, 3 grupos.
     * Devuelve las semillas listas para insertar en la BD.
     */
    fun semillas(): EscenarioSemillas {
        val club = ClubBuilder().build()
        val admin = PrincipalBuilder().admin().enClub(club.id.value).build()
        val (entrenador1, entrenador2) = TestPrincipals.dosEntrenadoresMismoClub()
        val alumnos = (1..10).map { PrincipalBuilder().alumno().enClub(club.id.value).build() }
        // ...
        return EscenarioSemillas(club, admin, listOf(entrenador1, entrenador2), alumnos)
    }
}
```

## 9. Dataset sintético en `staging`

Cruce con ADR-0006 D21: `staging` arranca con BD vacía y un script de seed genera datos sintéticos. **Prohibida la copia de producción** (ADR-0014 D6).

### Estructura del seed

```
backend/src/main/resources/seed/
├── staging/
│   ├── 01_club_piloto.sql
│   ├── 02_taxonomia.sql
│   ├── 03_entrenadores.sql
│   ├── 04_alumnos.sql
│   ├── 05_grupos.sql
│   └── 06_planes_semana_anterior.sql
└── local/
    └── ...
```

### Tarea Gradle de seed

```kotlin
// backend/build.gradle.kts
tasks.register("seedStaging") {
    description = "Ejecuta el script de seed sintético sobre la BD de staging"
    group = "database"
    doLast {
        // Llama a Flyway con location adicional db/migration/seed/staging
        // O ejecuta los .sql directamente via JdbcTemplate
    }
}
```

### Convenciones del dataset

- **Volumen mínimo representativo**: 1 club, 2-3 entrenadores, 10-20 alumnos, 3-5 grupos, 2-3 semanas de plan, ~30 reportes de sesión.
- **Nombres sintéticos visibles**: `Entrenador Sintético 01`, `alumno-test-01@example.com`. **Nunca** nombres realistas que pudieran parecer PII real.
- **Fechas relativas** a `now()`: `semana_inicio = current_date - INTERVAL '7 days'`. Evita datasets con fechas fijas que envejecen.
- **`club_id` único** generado al ejecutar el seed (no hardcodeado).

## 10. Catálogo de tests críticos del módulo

Patrón inspirado en la *"Estrategia de tests críticos"* del ADR-0003. Cada módulo declara, en su README de tests (`backend/src/test/kotlin/com/runcriticon/{modulo}/README.md`), su catálogo de casos críticos:

### Plantilla

```markdown
# Tests críticos — módulo Planificación

| Caso | Tipo de test | Por qué duele si falla en producción |
|---|---|---|
| Plan en BORRADOR se puede publicar; ya PUBLICADO devuelve PlanYaPublicado | Unitario `PlanSemanalTest` | Sin esto, un entrenador podría publicar dos veces el mismo plan, generando eventos duplicados |
| Plan publicado emite PlanPublicado en el outbox dentro de la misma transacción | Integración `PublicarPlanIntegrationTest` | Sin esto, la coherencia entre estado y evento se rompe |
| Entrenador A no puede publicar plan de entrenador B | Acceso cruzado `PublicarPlanAccesoTest` | IDOR (OWASP API #1) |
| Listener AlumnoAsignadoAGrupo es idempotente | Integración `AlumnoAsignadoAGrupoListenerIntegrationTest` | Sin esto, una entrega duplicada del outbox introduce alumnos duplicados en proyección |
| PlanPublicado serializado cumple plan-publicado-v1.json | Contrato `PlanPublicadoContractTest` | Sin esto, el código y el contrato divergen silenciosamente, rompiendo a los consumidores |
| ArchUnit: domain no depende de Spring | Arquitectura `CapasArchTest` | Sin esto, el dominio pierde su pureza y la testabilidad |
| ArchUnit: cada @ApplicationService autoriza | Arquitectura `AuthorizationArchTest` | Sin esto, un caso de uso nuevo sin autorización pasa a producción |
```

Si una PR introduce un caso crítico nuevo, **actualiza esta tabla en el mismo commit**.

## 11. Checklist de testing al crear un módulo

- [ ] Stack del módulo configurado: JUnit 5 + Kotest + MockK + Testcontainers `(ADR-0010 D8)`
- [ ] Tests unitarios del dominio sin Spring, con Kotest assertions y `shouldBeLeft<XxxError>` para casos Either `(ADR-0008 D6, D11)`
- [ ] Builders fluent por entidad/agregado/principal con defaults razonables; Object Mother para escenarios compuestos
- [ ] `IntegrationTestBase` con Testcontainers PostgreSQL configurado `(ADR-0010 D8)`
- [ ] Tests de integración usan `@Transactional` para rollback automático; tests con listeners async marcados explícitamente
- [ ] Al menos **un test de acceso cruzado** por cada `@ApplicationService` que carga o modifica objetos con nivel de objeto `(ADR-0009 D14)`
- [ ] Helpers de tests: `TestPrincipals`, `TestClubs`, `TestPrincipalContext` usados consistentemente
- [ ] ArchUnit: reglas de capas, autorización (`@ApplicationService` autoriza, `@Repository` con `@AuthScope`), imports prohibidos en `domain`, fronteras de Modulith `(ADR-0008 D14, ADR-0009 D13)`
- [ ] Tests de contrato del JSON Schema con `@Tag("contract")` para cada integration event publicado por el módulo `(ADR-0007 D11)`
- [ ] Catálogo de tests críticos en `README.md` del paquete de tests del módulo
- [ ] Dataset sintético del módulo añadido al seed de `staging` si introduce datos nuevos `(ADR-0006 D21, ADR-0014 D6)`
- [ ] Coverage en lógica de dominio y aplicación cubre los casos del catálogo crítico (sin objetivo numérico ciego)

## Referencias

- **ADR-0007 D2, D11** — fronteras de Modulith, versionado de eventos con JSON Schema.
- **ADR-0008 D6, D11, D14** — dominio puro testeable, `Either`, ArchUnit obligatorio.
- **ADR-0009 D13, D14** — ArchUnit guard de autorización + tests de acceso cruzado obligatorios.
- **ADR-0010 D8** — pirámide de tests con Testcontainers Postgres real.
- **ADR-0006 D21** — datos sintéticos generados en `staging`, prohibida copia de producción.
- **ADR-0014 D6** — borrado mixto cubierto por tests de integración con consumo de `AlumnoEliminado`.
- [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md) — guía principal.
- [`persistencia.md`](persistencia.md) — Testcontainers PostgreSQL, esquema por módulo en tests, builders con UUID v7.
- [`rgpd-en-modulos.md`](rgpd-en-modulos.md) — tests del consumo de `AlumnoEliminado` aplicando borrado mixto.
