package com.runcriticon.shared

import org.springframework.modulith.ApplicationModule

/**
 * Núcleo compartido (shared kernel) de Runcriticon: contratos transversales que cualquier módulo puede usar sin crear
 * acoplamiento de dominio.
 *
 * Contiene exclusivamente *contratos*: la interfaz `IntegrationEvent` y los seis campos obligatorios, las anotaciones y
 * enums de autorización, los contratos de observabilidad y la clasificación RGPD. No contiene reglas de negocio de
 * ningún bounded context.
 *
 * Se declara [ApplicationModule.Type.OPEN] para que el resto de módulos pueda depender de él sin listarlo
 * explícitamente en `allowedDependencies`.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    type = ApplicationModule.Type.OPEN,
    displayName = "Núcleo compartido",
)
internal interface SharedModule
