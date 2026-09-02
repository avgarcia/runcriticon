package com.runcriticon.seguimiento.domain

/**
 * Cómo resolver que el día destino de un `MOVER` ya tenga una sesión efectiva (LAL-33, wireframe 07 §Flujo B:
 * *"Ese día tiene [Series]. ¿Reemplazar / Intercambiar / Cancelar?"*). `Cancelar` no tiene representación
 * aquí: el alumno cerrando el diálogo nunca llega a `RescheduleDayCommand`.
 */
enum class ConflictResolution {
    /** La sesión que ocupaba el destino se marca [AdjustmentAction.SALTADA] con el mismo motivo. */
    REEMPLAZAR,

    /** Las dos sesiones se intercambian: cada una queda [AdjustmentAction.MOVIDA] al día de la otra. */
    INTERCAMBIAR,
}
