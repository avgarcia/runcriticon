package com.runcriticon.identidad.domain.magiclink

/**
 * Propósito de un magic link (ADR-0003 D5/D8). Discrimina para qué se emitió el token de modo que un
 * enlace de login no valga como reseteo de contraseña ni al revés (aislamiento de propósito): cada
 * consumo exige el propósito esperado. Los valores en mayúsculas son los que se persisten en la
 * columna `proposito` de `identidad.magic_link` (ADR-0008 D4).
 */
enum class MagicLinkPurpose { LOGIN, RESETEO }
