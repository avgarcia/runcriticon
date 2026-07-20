package com.runcriticon.identidad

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **identidad**: usuarios, credenciales, alta/baja y el vínculo de identidad con un club. Es la raíz de
 * la cadena de dependencias del dominio: no depende de ningún otro módulo de negocio (solo del núcleo compartido, que
 * es OPEN).
 *
 * Se comunica con el resto exclusivamente por eventos de integración; no expone llamadas síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`; ver [ApplicationModule] con
 * `@Target(TYPE)`).
 */
@ApplicationModule(displayName = "Identidad")
internal interface IdentidadModule
