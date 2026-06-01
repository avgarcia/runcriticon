package com.runcriticon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

/**
 * Punto de entrada del backend de Runcriticon.
 *
 * Monolito modular (ADR-0007): los bounded contexts son sub-paquetes directos de
 * com.runcriticon (identidad, club, planificacion, salud, auditoria). Spring Modulith
 * los descubre y verifica sus fronteras en los tests (ModulithFronterasTest).
 *
 * En H0 Bloque 2A todavía no hay módulos: esta clase solo arranca la app para que el
 * build produzca un JAR ejecutable. Los módulos llegan en el Bloque 3.
 */
@Modulithic(systemName = "Runcriticon")
@SpringBootApplication
class RuncriticonApplication

fun main(args: Array<String>) {
    runApplication<RuncriticonApplication>(*args)
}
