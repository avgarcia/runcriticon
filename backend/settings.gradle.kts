// Proyecto Gradle del backend de Runcriticon.
// Un único módulo desplegable; los bounded contexts de ADR-0007 son PAQUETES
// (com.runcriticon.{modulo}), no subproyectos Gradle — Spring Modulith opera
// dentro de un solo deployable.

rootProject.name = "runcriticon-backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Descarga automática del JDK declarado en la toolchain (GraalVM CE 21, ADR-0016 D7).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
