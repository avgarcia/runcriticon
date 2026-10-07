package com.runcriticon.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Valida [eventNameMatchesSchemaFile] contra los 22 pares evento↔schema reales del repo, incluidas las
 * dos convenciones distintas que coexisten para las palabras de una sola letra, y comprueba que detecta
 * una violación sintética (evento sin schema correspondiente).
 */
class EventSchemaMatchingTest :
    FunSpec({
        test("los 22 eventos reales casan con su schema real") {
            val pares =
                listOf(
                    "EntrenadorAsignadoAGrupo" to "entrenador-asignado-a-grupo-v1.json",
                    "EntrenadorEliminadoDeGrupo" to "entrenador-eliminado-de-grupo-v1.json",
                    "MembresiaDeGrupoCambiada" to "membresia-de-grupo-cambiada-v1.json",
                    "AdminEliminado" to "admin-eliminado-v1.json",
                    "AlumnoActivado" to "alumno-activado-v1.json",
                    "AlumnoEliminado" to "alumno-eliminado-v1.json",
                    "AlumnoInvitado" to "alumno-invitado-v1.json",
                    "ConsentimientoConcedido" to "consentimiento-concedido-v1.json",
                    "ConsentimientoRevocado" to "consentimiento-revocado-v1.json",
                    "EntrenadorActivado" to "entrenador-activado-v1.json",
                    "EntrenadorEliminado" to "entrenador-eliminado-v1.json",
                    "EntrenadorInvitado" to "entrenador-invitado-v1.json",
                    "PersonalizacionAplicada" to "personalizacion-aplicada-v1.json",
                    "PersonalizacionRetirada" to "personalizacion-retirada-v1.json",
                    "PlanPublicado" to "plan-publicado-v1.json",
                    "DiaReajustado" to "dia-reajustado-v1.json",
                    "MarcaActualizada" to "marca-actualizada-v1.json",
                    "MarcaRetirada" to "marca-retirada-v1.json",
                    "ReporteRegistrado" to "reporte-registrado-v1.json",
                    // La "A" (preposición "a") se omite en el nombre de fichero — a diferencia del caso de arriba.
                    "AccesoADatosSensibles" to "acceso-datos-sensibles-v1.json",
                    "AccesoDenegado" to "acceso-denegado-v1.json",
                    "LesionDeclarada" to "lesion-declarada-v1.json",
                )

            pares.forEach { (eventClassName, schemaFileName) ->
                withClue("$eventClassName vs $schemaFileName") {
                    eventNameMatchesSchemaFile(eventClassName, schemaFileName) shouldBe true
                }
            }
        }

        test("un evento sin schema correspondiente no casa con un schema de otro evento") {
            eventNameMatchesSchemaFile("PlanRetirado", "plan-publicado-v1.json") shouldBe false
        }
    })
