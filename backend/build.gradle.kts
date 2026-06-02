// Build del backend de Runcriticon — Kotlin + Spring Boot 3 + Spring Modulith.
// Versiones en gradle/libs.versions.toml. Cruce: ADR-0001, 0007, 0008, 0010, 0016.

import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

group = "com.runcriticon"
version = "0.0.1-SNAPSHOT"

// GraalVM CE 21 modo JIT (ADR-0016 D1/D2/D7). Foojay descarga el JDK si falta.
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

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.modulith.bom
                .get()
                .toString(),
        )
        mavenBom(
            libs.testcontainers.bom
                .get()
                .toString(),
        )
    }
}

dependencies {
    // --- Kotlin ---
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    // --- Spring Boot ---
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
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
}

tasks.withType<Test> {
    useJUnitPlatform()
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
// detekt embebe el compilador de Kotlin con el que fue compilado (1.23.7 -> 2.0.10). El plugin de
// Kotlin alinearía kotlin-compiler-embeddable a 2.1.0 en el classpath de detekt y este aborta con
// "compiled with Kotlin 2.0.10 but is currently running with 2.1.0". Se fija la versión del
// compilador SOLO en la configuración de detekt (https://detekt.dev/docs/gettingstarted/gradle).
configurations.matching { it.name == "detekt" }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.10")
        }
    }
}

// Tarea de contrato de eventos (ADR-0007 D11). Tests etiquetados @Tag("contract").
tasks.register<Test>("contractTest") {
    description = "Valida que los integration events publicados coinciden con su JSON Schema."
    group = "verification"
    useJUnitPlatform { includeTags("contract") }
    shouldRunAfter(tasks.test)
}
