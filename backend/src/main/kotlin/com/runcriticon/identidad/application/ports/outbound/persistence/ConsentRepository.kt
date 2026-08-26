package com.runcriticon.identidad.application.ports.outbound.persistence

import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Puerto de persistencia de [Consent]. La malla anti-IDOR exige que cada método público del adaptador
 * declare `@AuthScope` o `@NoAuthScope`.
 */
interface ConsentRepository {
    /**
     * Persiste una concesión nueva o la actualización de una existente (identificadas por [Consent.id]):
     * conceder siempre pasa un [Consent] con un id nuevo; revocar pasa el mismo id con `revokedAt` ya
     * relleno. Nunca borra ni sustituye una fila por otra.
     */
    fun save(consent: Consent)

    /** La fila más reciente del usuario (por `grantedAt`), o `null` si nunca ha concedido consentimiento. */
    fun findLatestByUserId(
        clubId: ClubId,
        userId: UserId,
    ): Consent?

    /** Borra físicamente todas las filas del usuario (derecho de supresión, ADR-0014 D6). */
    fun deleteByUserId(
        clubId: ClubId,
        userId: UserId,
    ): Int
}
