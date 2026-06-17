package com.runcriticon.architecture

import com.runcriticon.RuncriticonApplication
import com.tngtech.archunit.core.domain.JavaClass
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Fronteras de Spring Modulith (ADR-0007, ADR-0005 D3): los bounded contexts solo se comunican por
 * eventos de integración, sin llamadas síncronas cruzadas, y cada uno respeta las
 * `allowedDependencies` declaradas en su descriptor de módulo Kotlin (`ModuloXxx.kt`).
 *
 * `verify()` es análisis estático: NO arranca el contexto de Spring ni necesita base de datos. Se
 * excluye del modelo el paquete de tests `..architecture..` para que sus clases no se confundan con
 * un módulo de la aplicación.
 */
class ModulithFronterasTest {
    private val modulos =
        ApplicationModules.of(
            RuncriticonApplication::class.java,
            JavaClass.Predicates.resideInAnyPackage("com.runcriticon.architecture.."),
        )

    @Test
    fun `los modulos respetan sus fronteras y dependencias declaradas`() {
        modulos.verify()
    }

    @Test
    fun `se detectan los cinco bounded contexts del sistema`() {
        val nombres = modulos.map { it.identifier.toString() }.toSet()
        listOf("identidad", "club_taxonomia", "planificacion", "seguimiento", "auditoria").forEach { modulo ->
            assertTrue(modulo in nombres, "Falta el módulo '$modulo'. Detectados: $nombres")
        }
    }
}
