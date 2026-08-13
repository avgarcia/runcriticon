package com.runcriticon.planificacion.infrastructure.rest

import com.runcriticon.planificacion.application.usecases.plans.CreateDraftPlanCommand
import com.runcriticon.planificacion.application.usecases.plans.ListDraftPlansQuery
import com.runcriticon.planificacion.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.planificacion.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.CreatePlanRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Planes semanales en borrador. Solo ENTRENADOR — ver comentario en `AuthorizationMatrix`. */
@RestController
@RequestMapping("/api/planes")
class PlanController(
    private val listDraftPlans: ListDraftPlansQuery,
    private val createDraftPlan: CreateDraftPlanCommand,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/planes?grupoId= — planes en borrador del grupo. */
    @GetMapping
    @Authorize("PLAN:LIST")
    fun listDrafts(
        @RequestParam grupoId: UUID,
    ): ResponseEntity<*> =
        listDraftPlans.execute(principalProvider.current(), grupoId).fold(
            { error -> error.toErrorResponse() },
            { plans -> ResponseEntity.ok(plans.toResponse()) },
        )

    /** POST /api/planes — crea el plan en borrador. */
    @PostMapping
    @Authorize("PLAN:CREATE")
    fun create(
        @RequestBody req: CreatePlanRequest,
    ): ResponseEntity<*> =
        createDraftPlan.execute(principalProvider.current(), req.grupoId, req.semana).fold(
            { error -> error.toErrorResponse() },
            { plan -> ResponseEntity.status(HttpStatus.CREATED).body(plan.toResponse()) },
        )
}
