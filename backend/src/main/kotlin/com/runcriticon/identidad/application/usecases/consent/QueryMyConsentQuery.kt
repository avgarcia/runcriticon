package com.runcriticon.identidad.application.usecases.consent

import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId

/**
 * `GET /me/consentimiento`: el propio estado de consentimiento del alumno. `null` significa
 * `PENDIENTE` — nunca ha concedido, típicamente porque activó su cuenta antes de que existiera este
 * mecanismo (LAL-128).
 */
@ApplicationService
@AuthenticatedOnly(
    "Devuelve el propio estado de consentimiento del alumno; no hay recurso de terceros que autorizar",
)
class QueryMyConsentQuery(
    private val consentRepository: ConsentRepository,
) {
    fun execute(actor: Principal): Consent? =
        consentRepository.findLatestByUserId(ClubId.of(actor.clubId), UserId.of(actor.userId))
}
