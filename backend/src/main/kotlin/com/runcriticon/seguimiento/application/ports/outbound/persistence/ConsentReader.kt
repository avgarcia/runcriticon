package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Lectura de la proyección `consentimiento_alumno` desde dentro de una petición con `Principal`
 * (`SubmitSessionReportCommand`, LAL-128 PR2). Puerto aparte de [ConsentProjection] — ver su KDoc.
 */
interface ConsentReader {
    /**
     * `false` tanto si la última fila es una revocación como si no hay ninguna fila — **fail-closed** ante la
     * ausencia de proyección, mismo criterio que ADR-0009 D9 para proyecciones obsoletas: sin evidencia de
     * consentimiento vigente, no se trata el dato de salud. Consecuencia asumida: todo alumno activado antes
     * de que existiera este mecanismo queda sin poder reportar hasta que pase por `/mi-cuenta`.
     */
    fun isGranted(
        clubId: ClubId,
        studentId: StudentId,
    ): Boolean
}
