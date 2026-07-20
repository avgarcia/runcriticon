package com.runcriticon.identidad.domain.user

/**
 * Email del usuario, normalizado a minúsculas para la unicidad.
 */
@JvmInline
value class Email private constructor(
    val value: String,
) {
    companion object {
        fun of(raw: String): Email = Email(raw.trim().lowercase())
    }
}
