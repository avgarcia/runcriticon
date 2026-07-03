package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.domain.user.UserStatus
import java.util.UUID

/**
 * Vista de lectura de un entrenador para el listado admin (LAL-13). DTO de la capa `application`:
 * expone lo justo que la UI necesita (id, nombre, email, estado) sin filtrar el agregado completo
 * (nunca el hash de contraseña). El controlador lo mapea al cuerpo del contrato (`CoachSummary`).
 */
data class CoachSummary(
    val id: UUID,
    val name: String,
    val email: String,
    val status: UserStatus,
)
