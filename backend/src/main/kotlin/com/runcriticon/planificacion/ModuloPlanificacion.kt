package com.runcriticon.planificacion

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **planificacion**: planes de entrenamiento y su asignación a grupos y alumnos
 * (ADR-0003, ADR-0009). Puede referenciar tipos expuestos de `club_taxonomia` (grupos) e
 * `identidad` (usuarios).
 *
 * El resto de la comunicación es por eventos de integración (ADR-0005, ADR-0011); sin llamadas
 * síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    displayName = "Planificación",
    allowedDependencies = ["club_taxonomia", "identidad"],
)
internal interface ModuloPlanificacion
