package com.runcriticon.identidad.domain.user

/**
 * Email del usuario, normalizado a minúsculas para la unicidad (ADR-0003 D2). En H0 solo se
 * normaliza; la validación de formato y la comprobación contra datos personales llegan en Fase 1.
 */
@JvmInline
value class Email private constructor(
    val value: String,
) {
    companion object {
        fun of(raw: String): Email = Email(raw.trim().lowercase())
    }
}
