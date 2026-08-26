package com.runcriticon.identidad.domain.consent

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de una fila de consentimiento. Cada concesión es una fila nueva (ver [Consent]);
 * este id la identifica de forma estable para poder actualizar su `revocado_en` sin tocar `concedido_en`.
 */
@JvmInline
value class ConsentId(
    val value: UUID,
) {
    companion object {
        fun new(): ConsentId = ConsentId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): ConsentId = ConsentId(value)
    }
}
