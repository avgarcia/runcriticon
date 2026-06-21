// Build del backend de Runcriticon — Kotlin + Spring Boot 4 + Spring Modulith.
// Versiones en gradle/libs.versions.toml. Cruce: ADR-0001, 0007, 0008, 0010, 0016.

import dev.detekt.gradle.Detekt
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

group = "com.runcriticon"
version = "0.0.1-SNAPSHOT"

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
    }
}

dependencies {
    // --- BOMs (Gradle nativo, sustituye io.spring.dependency-management eliminado en SB4) ---
    // Las configs custom del plugin SB (developmentOnly, kaptTest…) no extienden implementation
    // y no heredan el BOM; hay que declararlo explícitamente en cada una que lo necesite.
    implementation(platform(libs.spring.boot.bom))
    developmentOnly(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    testImplementation(platform(libs.testcontainers.bom))
    kaptTest(platform(libs.testcontainers.bom))
    // --- Kotlin ---
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    // --- Spring Boot ---
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.thymeleaf) // plantillas de email en fichero (ADR-0005 D7)
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
    kapt(libs.konvert)

    // --- Typed IDs UUID v7 (ADR-0008) ---
    implementation(libs.uuid.creator)

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
    testImplementation(libs.ognl) // dialecto estándar de Thymeleaf en InvitationEmailRendererTest
}

tasks.withType<Test> {
    useJUnitPlatform()
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

// Tarea de contrato de eventos (ADR-0007 D11). Tests etiquetados @Tag("contract").
tasks.register<Test>("contractTest") {
    description = "Valida que los integration events publicados coinciden con su JSON Schema."
    group = "verification"
    useJUnitPlatform { includeTags("contract") }
    shouldRunAfter(tasks.test)
}
