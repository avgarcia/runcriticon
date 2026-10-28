package com.runcriticon.shared.events

import kotlin.reflect.KClass

/**
 * Retraso de una proyección local medido sobre el outbox (`event_publication`): cuánto lleva pendiente la entrega
 * más antigua a los listeners que la alimentan. Es lo que publica cada gauge `{modulo}.projection_lag_seconds`, con
 * alarma por encima de 60 s (ADR-0009 D9: lag entre lo último publicado por el módulo origen y lo último procesado).
 *
 * No se mide como `now() - MAX(last_processed_event_ts)` de la proyección: esa fórmula crece en cuanto nadie publica
 * eventos, aunque la proyección esté al día, y la alarma saltaría tras cualquier fin de semana tranquilo. Una entrega
 * pendiente desde hace más de 60 s sí es retraso real.
 */
interface PendingDeliveryLag {
    /**
     * Segundos desde la entrega pendiente más antigua a cualquiera de [listeners]; 0 si no hay ninguna. Cuenta también
     * las `FAILED`: no convergen solas (ADR-0007 D13) y el gauge tiene que seguir en rojo hasta que se reprocesen.
     *
     * [listeners] son las clases con los `@ApplicationModuleListener` que escriben en la proyección; no puede estar
     * vacío, porque sin filtro se mediría el outbox entero.
     */
    fun oldestPendingSeconds(listeners: Set<KClass<*>>): Long
}
