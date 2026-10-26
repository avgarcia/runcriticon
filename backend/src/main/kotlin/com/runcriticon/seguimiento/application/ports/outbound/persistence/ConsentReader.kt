package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Lectura de la proyección `consentimiento_alumno` desde dentro de una petición con `Principal`
 * (consentimiento explícito Art. 9.2.a): guarda de las escrituras de datos de salud (`SubmitSessionReportCommand`
 * y compañía) y filtro de las lecturas que un tercero hace de ellos (`ListCoachAlertsQuery`). Puerto aparte de
 * [ConsentProjection] — ver su KDoc.
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

    /**
     * Los de [students] con consentimiento vigente, mismo criterio fail-closed que [isGranted]: un alumno sin
     * fila en la proyección no aparece. En lote para filtrar un listado sin consultar alumno a alumno.
     */
    fun grantedAmong(
        clubId: ClubId,
        students: Set<StudentId>,
    ): Set<StudentId>
}
