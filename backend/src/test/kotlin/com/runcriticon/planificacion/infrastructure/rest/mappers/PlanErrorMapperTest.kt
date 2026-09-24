package com.runcriticon.planificacion.infrastructure.rest.mappers

import com.runcriticon.planificacion.domain.PlanificacionError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

/**
 * `ProjectionStale` no tenía test del mapeo HTTP (P0-7, auditoría de testing 2026-09): 503, no 409/500 — es
 * indisponibilidad temporal de la proyección (ADR-0009 D9), no un conflicto del cliente ni un fallo del servidor.
 */
class PlanErrorMapperTest :
    FunSpec({
        test("ProjectionStale mapea a 503 con el código PROJECTION_STALE") {
            val response = PlanificacionError.ProjectionStale(lagSeconds = 60L).toErrorResponse()

            response.statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
            response.body?.code shouldBe "PROJECTION_STALE"
        }
    })
