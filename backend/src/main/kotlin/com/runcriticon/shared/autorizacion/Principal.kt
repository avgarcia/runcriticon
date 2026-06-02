package com.runcriticon.shared.autorizacion

import java.util.UUID

/**
 * El usuario autenticado en curso (ADR-0009 D6). Vive en el núcleo compartido y lo
 * obtiene cada caso de uso a través de [PrincipalProvider].
 */
data class Principal(
    val userId: UUID,
    val clubId: UUID,
    val rol: Rol,
)
