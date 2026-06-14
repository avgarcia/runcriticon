/**
 * Bounded context <strong>planificacion</strong>: planes de entrenamiento y su asignación a grupos
 * y alumnos (ADR-0003, ADR-0009). Puede referenciar tipos expuestos de {@code club} (grupos) e
 * {@code identidad} (usuarios).
 *
 * <p>El resto de la comunicación es por eventos de integración (ADR-0005, ADR-0011); sin llamadas
 * síncronas cruzadas.
 */
@ApplicationModule(
        displayName = "Planificación",
        allowedDependencies = {"club", "identidad"})
package com.runcriticon.planificacion;

import org.springframework.modulith.ApplicationModule;
