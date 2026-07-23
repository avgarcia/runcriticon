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
 * Se declara [ApplicationModule.Type.OPEN] para que sus sub-paquetes sean accesibles sin exponerlos uno a uno y para
 * quedar fuera de la detección de ciclos. **`OPEN` no exime del allowlist**: un módulo que declara
 * `allowedDependencies` debe incluir `"shared"` en la lista igual que a cualquier otro destino, o
 * `ApplicationModules.verify()` falla. Los tres módulos con allowlist lo listan.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    type = ApplicationModule.Type.OPEN,
    displayName = "Núcleo compartido",
)
internal interface SharedModule
