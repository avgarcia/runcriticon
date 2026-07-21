package com.runcriticon.identidad.domain.club

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.tenancy.ClubId

/**
 * Agregado del club (ADR-0006 D30). En el MVP mono-club solo su [name] es editable; [slug] es de solo
 * lectura (aún no se usa para enrutar por subdominio, eso es multi-club). Zona horaria e inicio de
 * semana son columnas latentes con default en la tabla: no se modelan aquí todavía (ADR-0015 A2).
 */
data class Club(
    val id: ClubId,
    val name: String,
    val slug: String?,
) {
    /**
     * Cambia el nombre del club. Invariantes: no vacío tras `trim`, máximo 200 caracteres (coherente con
     * el schema `Nombre` del contrato OpenAPI).
     */
    fun rename(newName: String): Either<IdentidadError, Club> =
        either {
            val trimmed = newName.trim()
            ensure(trimmed.isNotEmpty()) { IdentidadError.InvalidInput("nombre", "blank") }
            ensure(trimmed.length <= MAX_NAME_LENGTH) { IdentidadError.InvalidInput("nombre", "too_long") }
            copy(name = trimmed)
        }

    companion object {
        const val MAX_NAME_LENGTH: Int = 200
    }
}
