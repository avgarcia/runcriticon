package com.runcriticon.identidad.domain.invitation

/**
 * Token de invitación en claro (≥256 bits de entropía, ADR-0003 D13). Se genera al emitir la
 * invitación y **solo** viaja al email del destinatario (ADR-0005); nunca se persiste ni se loguea.
 * En la base de datos vive únicamente su [TokenHash].
 */
@JvmInline
value class RawToken(
    val value: String,
)
