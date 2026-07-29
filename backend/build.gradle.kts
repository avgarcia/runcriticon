// Build del backend de Runcriticon — Kotlin + Spring Boot 4 + Spring Modulith.
// Versiones en gradle/libs.versions.toml. Cruce: ADR-0001, 0007, 0008, 0010, 0016.

import dev.detekt.gradle.Detekt
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.openapi.generator)
}

group = "com.runcriticon"
version = "0.0.1-SNAPSHOT"

// Reproducibilidad de builds (ADR-0010 D19): fija las versiones resueltas transitivas en
// gradle.lockfile, commiteado. Dependabot (ecosistema gradle, directory /backend) NO regenera el
// lockfile por su cuenta al abrir un PR de actualización — a diferencia de npm/yarn, el soporte de
// Gradle de Dependabot solo toca las versiones en build.gradle.kts/libs.versions.toml. Tras
// mergear un PR de Dependabot (o cambiar una dependencia a mano), hay que regenerar el lockfile
// con `./gradlew dependencies --write-locks` en un commit aparte antes de que el build vuelva a
// resolver correctamente (Gradle falla cerrado si la resolución no coincide con el lock).
dependencyLocking {
    lockAllConfigurations()
}

// Toolchain Java 21 per ADR-0016 D7; el runtime es GraalVM CE 25 (Dockerfile). Foojay descarga el JDK si falta.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict") // null-safety con anotaciones de Spring
        // Nombres de parámetro reales en bytecode (java.lang.reflect.Parameter.getName()): los
        // necesitan tanto AuthScopeEnforcementAspect (localizar "clubId" en el join point) como
        // AuthorizationArchTest (exigir el parámetro "clubId" por firma). ADR-0009 D11.
        javaParameters.set(true)
    }
}

// Genera modelos Kotlin a partir de api/openapi.yaml (ADR-0001 D10).
// globalProperties solo incluye "models": el DefaultGenerator no genera apis ni supportingFiles
// si no están presentes en el mapa — evita el conflicto ResponseEntity<T> vs ResponseEntity<*> con Either.fold.
openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$rootDir/../api/openapi.yaml")
    outputDir.set(
        layout.buildDirectory
            .dir("generated-src/openapi")
            .get()
            .asFile
            .path,
    )
    modelPackage.set("com.runcriticon.identidad.infrastructure.rest")
    globalProperties.set(mapOf("models" to ""))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "useSpringBoot3" to "true",
            "useBeanValidation" to "false",
            "enumPropertyNaming" to "UPPERCASE",
            "serializationLibrary" to "jackson",
            "documentationProvider" to "none",
        ),
    )
}

sourceSets.main {
    kotlin.srcDir(layout.buildDirectory.dir("generated-src/openapi/src/main/kotlin"))
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

// KSP (Konvert) y los tasks ktlint del main source set se registran lazily — matching evita que fallen si no existen.
tasks.matching { it.name == "kspKotlin" || it.name == "runKtlintCheckOverMainSourceSet" }.configureEach {
    dependsOn("openApiGenerate")
}

dependencies {
    // --- BOMs (Gradle nativo, sustituye io.spring.dependency-management eliminado en SB4) ---
    // Las configs custom del plugin SB (developmentOnly…) no extienden implementation
    // y no heredan el BOM; hay que declararlo explícitamente en cada una que lo necesite.
    implementation(platform(libs.spring.boot.bom))
    developmentOnly(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    testImplementation(platform(libs.testcontainers.bom))
    // --- Kotlin ---
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310) // JavaTimeModule: TagValueMetadataJsonbConverter

    // --- Spring Boot ---
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.thymeleaf) // plantillas de email en fichero (ADR-0005 D7)
    implementation(libs.spring.boot.starter.aspectj) // AuthScopeEnforcementAspect (ADR-0009 D11)
    implementation(libs.spring.session.jdbc) // sesión por cookie respaldada en Postgres (ADR-0003 D10)
    implementation(libs.bouncycastle) // requerido por Argon2PasswordEncoder (ADR-0003 D13)

    // --- Spring Modulith (ADR-0007) ---
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jpa)
    implementation(libs.spring.modulith.events.jackson)

    // --- Observabilidad (ADR-0011) ---
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.logstash.logback.encoder)

    // --- Persistencia (ADR-0004) ---
    implementation(libs.flyway.core)
    implementation(libs.spring.boot.flyway)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    // --- Funcional / mapping (ADR-0008) ---
    implementation(libs.arrow.core)
    implementation(libs.konvert.api)
    ksp(libs.konvert)

    // --- Typed IDs UUID v7 (ADR-0008) ---
    implementation(libs.uuid.creator)

    // --- Rate limiting (ADR-0003 D12) ---
    implementation(libs.bucket4j.core)
    implementation(libs.caffeine)

    // --- Dev ---
    developmentOnly(libs.spring.boot.devtools)

    // --- Testing (ADR-0010) ---
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.mockito") // usamos MockK (ADR-0010 stack)
    }
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.security.test) // slice @WebMvcTest con csrf() y user() (ADR-0003 D14)
    testImplementation(libs.ognl) // dialecto estándar de Thymeleaf en InvitationEmailRendererTest
    testImplementation(libs.json.schema.validator) // contract test de integration events (ADR-0007 D11)
    testImplementation(libs.swagger.request.validator.core) // contract test REST runtime vs openapi.yaml (ADR-0001 D10)
}

// Falla rápido y con mensaje claro si Docker no responde, en vez de dejar que cada una de las ~20
// clases @SpringBootTest (× maxParallelForks) agote por su cuenta el timeout de sondeo de Testcontainers.
val checkDockerAvailable =
    tasks.register("checkDockerAvailable") {
        description = "Comprueba que el daemon de Docker responde antes de arrancar tests con Testcontainers."
        doFirst {
            // La salida se descarta en vez de redirigirse a una pipe: solo interesa el código de salida, y nadie
            // consume el stream. Con `redirectErrorStream(true)` la salida iba a una pipe de 4 KB que `docker info`
            // llena hoy (~3,7 KB y creciendo con cada warning nuevo del daemon); al llenarse, el proceso se bloquea
            // escribiendo, el waitFor expira y la tarea reportaba "Docker no está disponible" con Docker perfectamente
            // arrancado. `Redirect.DISCARD` escribe al dispositivo nulo, así que no hay buffer que llenar.
            val process =
                ProcessBuilder("docker", "info")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            val finishedInTime = process.waitFor(10, TimeUnit.SECONDS)
            if (!finishedInTime) process.destroyForcibly()
            if (!finishedInTime || process.exitValue() != 0) {
                throw GradleException(
                    "Docker no está disponible: 'docker info' " +
                        (if (!finishedInTime) "no respondió en 10 s" else "devolvió código ${process.exitValue()}") +
                        ". Los tests de integración usan Testcontainers (ADR-0010 D8) — arranca Docker y reintenta.",
                )
            }
        }
    }

tasks.named<Test>("test") {
    dependsOn(checkDockerAvailable)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Paraleliza JVMs de test (no threads dentro de una JVM); cada fork sigue arrancando su propio
    // contenedor Postgres (ADR-0010 D8, "contenedor por test class"), así que el aislamiento no cambia.
    // Tope conservador (no processors/2): AuthenticateUserTimingIntegrationTest mide tiempos en
    // nanosegundos y falla con ratios de CPU altos si hay demasiada contención entre forks.
    maxParallelForks = 2
    // El contenedor Postgres de cada test class (ADR-0010 D21) se detiene en el afterAll de JUnit,
    // pero el ApplicationContext de Spring se cierra un instante después (destroy de
    // eventPublicationRegistry de Modulith, scheduler de limpieza de Spring Session) y esos beans
    // intentan abrir una conexión JDBC contra un Postgres que ya no existe. Con el connection-timeout
    // por defecto de Hikari (30 s) cada cierre de contexto se bloquea 30 s sin ningún valor — con
    // ~20 clases de test es la mayor parte del tiempo total del build. Como system property (no
    // fichero de config) llega a todos los forks sin depender del perfil activo de cada test class.
    systemProperty("spring.datasource.hikari.connection-timeout", "2000")
    systemProperty("spring.session.jdbc.cleanup-cron", "-") // ningún test depende de que se ejecute
    testLogging {
        events(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL // muestra expected/actual de los asserts en el log de CI
        showStandardStreams = false
    }
}

// ktlint (ADR-0010 D7 / backend CLAUDE.md)
ktlint {
    version.set("1.4.1")
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

// detekt (ADR-0010 D7)
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}
tasks.withType<Detekt> {
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}

// Tarea de contrato de eventos (ADR-0007 D11). Tests etiquetados @Tag("contract") en identidad.contracts.
tasks.register<Test>("contractTest") {
    description = "Valida que los integration events publicados coinciden con su JSON Schema."
    group = "verification"
    // Un Test registrado a mano no hereda el classpath del source set "test" por defecto:
    // sin esto, useJUnitPlatform no encuentra ninguna clase que filtrar y la tarea sale NO-SOURCE
    // (verde sin ejecutar nada).
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath =
        sourceSets.test
            .get()
            .runtimeClasspath
    // Filtro por nombre de clase, no por @Tag: los specs Kotest (la mayoría de la suite) no
    // propagan sus tags al TagFilter de JUnit Platform que usa Gradle, así que
    // useJUnitPlatform { includeTags("contract") } no excluye nada — verificado empíricamente
    // (con un tag inexistente seguían ejecutándose los 35 tests Kotest/ArchUnit de la suite).
    // El filtro por FQN de clase sí es agnóstico al motor de test.
    filter {
        includeTestsMatching("com.runcriticon.identidad.contracts.*")
    }
    shouldRunAfter(tasks.test)
}
