/**
 * Bounded context <strong>salud</strong>: datos de salud del alumno (categoría especial RGPD
 * art. 9) ligados a su planificación (ADR-0003, ADR-0013, ADR-0014). Puede referenciar tipos
 * expuestos de {@code planificacion}, {@code club} e {@code identidad}.
 *
 * <p>Todo acceso a datos de salud se audita (ADR-0013). El resto de la comunicación es por eventos
 * de integración (ADR-0005, ADR-0011); sin llamadas síncronas cruzadas.
 */
@ApplicationModule(
        displayName = "Salud",
        allowedDependencies = {"planificacion", "club", "identidad"})
package com.runcriticon.salud;

import org.springframework.modulith.ApplicationModule;
