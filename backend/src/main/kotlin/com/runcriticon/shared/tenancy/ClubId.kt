package com.runcriticon.shared.tenancy

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado del club (ADR-0008: typed IDs como value class sobre UUID v7). Vive en el
 * núcleo compartido porque es el eje de multi-tenancy y del scope CLUB (ADR-0009): ningún módulo
 * es su dueño y el futuro módulo Club no debe importar `identidad` para usarlo.
 *
 * Como `@JvmInline value class` se borra a `UUID` en bytecode: [com.runcriticon.shared.autorizacion.spring.AuthScopeEnforcementAspect]
 * y el ArchUnit de autorización ven un `UUID` y siguen funcionando sin cambios.
 */
@JvmInline
value class ClubId(
    val value: UUID,
) {
    companion object {
        fun new(): ClubId = ClubId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): ClubId = ClubId(value)
    }
}
