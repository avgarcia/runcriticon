package com.runcriticon.identidad.domain.magiclink

/**
 * Propósito de un magic link. Discrimina para qué se emitió el token de modo que un enlace de login no valga como
 * reseteo de contraseña ni al revés (aislamiento de propósito): cada consumo exige el propósito esperado. Los valores
 * en mayúsculas son los que se persisten en la columna `proposito` de `identidad.magic_link`.
 */
enum class MagicLinkPurpose { LOGIN, RESETEO }
