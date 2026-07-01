package com.runcriticon.shared.autorizacion

import java.util.UUID

/**
 * Puerto **neutro** de revocación de sesiones (ADR-0003 D7/D8/D9/D11). Vive en el núcleo compartido
 * (`shared.autorizacion`, shared kernel) porque revocar sesiones es una preocupación transversal de
 * seguridad que varios bounded contexts necesitan sin acoplarse entre sí ni a Spring Session. Recibe
 * un `UUID` desnudo (no un typed ID de un módulo concreto) precisamente para no depender de ningún
 * bounded context; el módulo llamante traduce su `UserId` a `UUID`.
 *
 * Lo usa el reseteo de contraseña (LAL-12, D8) y lo reutilizarán el cambio de contraseña (D7), el
 * cambio de email (D9) y la revocación por admin (D11, LAL-13). La implementación (borrado de filas en
 * el almacén de sesiones) vive en `shared.autorizacion.spring`, único sitio autorizado a tocar
 * seguridad.
 */
interface SessionRevoker {
    /** Invalida todas las sesiones activas del usuario indicado (borra sus filas de Spring Session). */
    fun revokeAll(userId: UUID)
}
