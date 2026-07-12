package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.QueryMyPermissions
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Endpoints sobre el propio usuario autenticado, fuera de la gestión de sesión (`SessionController`). */
@RestController
@RequestMapping("/api/me")
class MeController(
    private val queryMyPermissions: QueryMyPermissions,
) {
    /**
     * `GET /me/permissions` (ADR-0009 D18): ayuda de UX para que el frontend oculte botones a los
     * que el usuario no llegaría. Nunca es una barrera — cada petición real se autoriza en el
     * servidor con independencia de lo que este endpoint devuelva.
     */
    @GetMapping("/permissions")
    @AuthenticatedOnly(
        "Devuelve los propios permisos del rol autenticado; no hay recurso de terceros que autorizar (ADR-0009 D18)",
    )
    fun permissions(): Map<Resource, Set<Action>> = queryMyPermissions.execute()
}
