package com.runcriticon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

/**
 * Punto de entrada del backend de Runcriticon.
 *
 * Monolito modular: los bounded contexts son sub-paquetes directos de com.runcriticon (identidad, club_taxonomia,
 * planificacion, seguimiento, auditoria). Spring Modulith los descubre y verifica sus fronteras en los tests
 * (ModulithFronterasTest).
 */
@Modulithic(systemName = "Runcriticon")
@SpringBootApplication
class RuncriticonApplication

fun main(args: Array<String>) {
    runApplication<RuncriticonApplication>(*args)
}
