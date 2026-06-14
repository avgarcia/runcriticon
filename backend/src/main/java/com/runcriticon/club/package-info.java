/**
 * Bounded context <strong>club</strong>: el club, sus grupos de entrenamiento y las membresías
 * (ADR-0003, ADR-0009). Puede referenciar tipos expuestos de {@code identidad} (p. ej. el usuario
 * que pertenece a un grupo).
 *
 * <p>El resto de la comunicación es por eventos de integración (ADR-0005, ADR-0011); sin llamadas
 * síncronas cruzadas.
 */
@ApplicationModule(
        displayName = "Club",
        allowedDependencies = {"identidad"})
package com.runcriticon.club;

import org.springframework.modulith.ApplicationModule;
