/**
 * Núcleo compartido (shared kernel) de Runcriticon: contratos transversales que cualquier módulo
 * puede usar sin crear acoplamiento de dominio (ADR-0009, guía operativa de módulos).
 *
 * <p>Contiene exclusivamente <em>contratos</em>: la interfaz {@code IntegrationEvent} y los seis
 * campos obligatorios (ADR-0005, ADR-0011), las anotaciones y enums de autorización (ADR-0009),
 * los contratos de observabilidad (ADR-0012) y la clasificación RGPD (ADR-0013, ADR-0014). No
 * contiene reglas de negocio de ningún bounded context.
 *
 * <p>Se declara {@link org.springframework.modulith.ApplicationModule.Type#OPEN OPEN} para que el
 * resto de módulos pueda depender de él sin listarlo explícitamente en {@code allowedDependencies}.
 */
@ApplicationModule(
        type = ApplicationModule.Type.OPEN,
        displayName = "Núcleo compartido")
package com.runcriticon.shared;

import org.springframework.modulith.ApplicationModule;
