package com.runcriticon.identidad.domain

/**
 * Email del usuario, normalizado a minúsculas para la unicidad (ADR-0003 D2). En H0 solo se
 * normaliza; la validación de formato y la comprobación contra datos personales llegan en Fase 1.
 */
@JvmInline
value class Email private constructor(
    val valor: String,
) {
    companion object {
        fun de(raw: String): Email = Email(raw.trim().lowercase())
    }
}
