/**
 * Bounded context <strong>identidad</strong>: usuarios, credenciales, alta/baja y el vínculo de
 * identidad con un club (ADR-0003, ADR-0009). Es la raíz de la cadena de dependencias del dominio:
 * no depende de ningún otro módulo de negocio (solo del núcleo compartido, que es OPEN).
 *
 * <p>Se comunica con el resto exclusivamente por eventos de integración (ADR-0005, ADR-0011); no
 * expone llamadas síncronas cruzadas.
 */
@ApplicationModule(displayName = "Identidad")
package com.runcriticon.identidad;

import org.springframework.modulith.ApplicationModule;
