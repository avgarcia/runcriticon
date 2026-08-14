package com.runcriticon.planificacion.infrastructure.rest

import com.runcriticon.planificacion.application.usecases.plans.CreateDraftPlanCommand
import com.runcriticon.planificacion.application.usecases.plans.GetPlanQuery
import com.runcriticon.planificacion.application.usecases.plans.ListDraftPlansQuery
import com.runcriticon.planificacion.application.usecases.plans.PublishPlanCommand
import com.runcriticon.planificacion.application.usecases.sessions.AddSessionCommand
import com.runcriticon.planificacion.application.usecases.sessions.DeleteSessionCommand
import com.runcriticon.planificacion.application.usecases.sessions.UpdateSessionCommand
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.infrastructure.rest.mappers.toDetailResponse
import com.runcriticon.planificacion.infrastructure.rest.mappers.toDomain
import com.runcriticon.planificacion.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.planificacion.infrastructure.rest.mappers.toPublicacionResponse
import com.runcriticon.planificacion.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.CreatePlanRequest
import com.runcriticon.shared.api.rest.TrainingSessionRequest
import com.runcriticon.shared.api.rest.TrainingSessionUpdateRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Planes semanales en borrador y sus sesiones (LAL-24). Solo ENTRENADOR — ver comentario en `AuthorizationMatrix`. */
@RestController
@RequestMapping("/api/planes")
class PlanController(
    private val listDraftPlans: ListDraftPlansQuery,
    private val createDraftPlan: CreateDraftPlanCommand,
    private val getPlan: GetPlanQuery,
    private val addSession: AddSessionCommand,
    private val updateSession: UpdateSessionCommand,
    private val deleteSession: DeleteSessionCommand,
    private val publishPlan: PublishPlanCommand,
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

    /** GET /api/planes/{planId} — el plan completo, con sus sesiones. */
    @GetMapping("/{planId}")
    @Authorize("PLAN:LIST")
    fun getById(
        @PathVariable planId: UUID,
    ): ResponseEntity<*> =
        getPlan.execute(principalProvider.current(), PlanId.of(planId)).fold(
            { error -> error.toErrorResponse() },
            { plan -> ResponseEntity.ok(plan.toDetailResponse()) },
        )

    /** POST /api/planes/{planId}/sesiones — añade una sesión al plan. */
    @PostMapping("/{planId}/sesiones")
    @Authorize("PLAN:UPDATE")
    fun addSession(
        @PathVariable planId: UUID,
        @RequestBody req: TrainingSessionRequest,
    ): ResponseEntity<*> =
        addSession
            .execute(
                actor = principalProvider.current(),
                planId = PlanId.of(planId),
                day = req.dia,
                type = req.tipo.toDomain(),
                volume = req.volumen.toDomain(),
                pace = req.ritmo.toDomain(),
                notes = req.notas,
            ).fold(
                { error -> error.toErrorResponse() },
                { session -> ResponseEntity.status(HttpStatus.CREATED).body(session.toResponse()) },
            )

    /** PUT /api/planes/{planId}/sesiones/{sesionId} — edita tipo, volumen, ritmo y notas de la sesión. */
    @PutMapping("/{planId}/sesiones/{sesionId}")
    @Authorize("PLAN:UPDATE")
    fun updateSession(
        @PathVariable planId: UUID,
        @PathVariable sesionId: UUID,
        @RequestBody req: TrainingSessionUpdateRequest,
    ): ResponseEntity<*> =
        updateSession
            .execute(
                actor = principalProvider.current(),
                planId = PlanId.of(planId),
                sessionId = SessionId.of(sesionId),
                type = req.tipo.toDomain(),
                volume = req.volumen.toDomain(),
                pace = req.ritmo.toDomain(),
                notes = req.notas,
            ).fold(
                { error -> error.toErrorResponse() },
                { session -> ResponseEntity.ok(session.toResponse()) },
            )

    /** DELETE /api/planes/{planId}/sesiones/{sesionId} — elimina la sesión. */
    @DeleteMapping("/{planId}/sesiones/{sesionId}")
    @Authorize("PLAN:UPDATE")
    fun deleteSession(
        @PathVariable planId: UUID,
        @PathVariable sesionId: UUID,
    ): ResponseEntity<*> =
        deleteSession.execute(principalProvider.current(), PlanId.of(planId), SessionId.of(sesionId)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Unit>() },
        )

    /** POST /api/planes/{planId}/publicacion — publica el plan al grupo, congelando el snapshot de alumnos. */
    @PostMapping("/{planId}/publicacion")
    @Authorize("PLAN:PUBLISH")
    fun publish(
        @PathVariable planId: UUID,
    ): ResponseEntity<*> =
        publishPlan.execute(principalProvider.current(), PlanId.of(planId)).fold(
            { error -> error.toErrorResponse() },
            { result -> ResponseEntity.ok(result.toPublicacionResponse()) },
        )
}
