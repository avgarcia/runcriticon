package com.runcriticon.identidad

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **identidad**: usuarios, credenciales, alta/baja y el vínculo de identidad con un club. Es la raíz de
 * la cadena de dependencias del dominio: no depende de ningún otro módulo de negocio (solo del núcleo compartido, que
 * es OPEN).
 *
 * Se comunica con el resto exclusivamente por eventos de integración; no expone llamadas síncronas cruzadas.
 *
 * La allowlist solo contiene `shared` y `shared :: events` (donde vive `AccesoDenegado`): declara en código que
 * `identidad` es la raíz del DAG y no consume de ningún otro módulo de negocio, y `ModulithFronterasTest` lo
 * verifica. `shared` va pese a ser `OPEN`: al declarar allowlist, todo destino debe estar listado.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`; ver [ApplicationModule] con
 * `@Target(TYPE)`).
 */
@ApplicationModule(
    displayName = "Identidad",
    allowedDependencies = ["shared", "shared :: events"],
)
internal interface IdentidadModule
