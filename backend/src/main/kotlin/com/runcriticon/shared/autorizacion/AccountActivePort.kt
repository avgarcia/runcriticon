package com.runcriticon.shared.autorizacion

import java.util.UUID

/**
 * Puerto **neutro** de consulta del estado de una cuenta. Vive en el núcleo compartido (`shared.autorizacion`, shared
 * kernel) porque el *gate-check* de estado —el [com.runcriticon.shared.autorizacion.spring.AccountStatusFilter]— es una
 * preocupación transversal de seguridad que no debe acoplarse a ningún bounded context concreto. Recibe un `UUID` (no
 * un typed ID de un módulo) precisamente para no depender de `identidad`; el módulo que expone la proyección de estado
 * (identidad) implementa el adaptador.
 *
 * Lo consume el filtro de estado de cuenta: si una sesión sobrevive a la desactivación, la siguiente petición se
 * rechaza (fail-closed) al ver que la cuenta ya no está `ACTIVO`.
 */
interface AccountActivePort {
    /** Indica si la cuenta del usuario indicado está activa. Ausente o no-`ACTIVO` devuelve `false`. */
    fun isActive(userId: UUID): Boolean
}
