package com.runcriticon.testing

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper

/**
 * El mapper de los tests de contrato de eventos (ADR-0007 D11). Se construye como el que usa la aplicación: registra
 * los módulos que haya en el classpath (`findAndAddModules`, que incluye el de Kotlin y el de fechas), igual que el
 * autoconfigurado de Spring Boot con el que el outbox serializa cada evento, y escribe las fechas como texto ISO.
 *
 * Antes cada test levantaba su propio mapper solo con el módulo de fechas, sin el de Kotlin: un evento con un
 * constructor con valores por defecto o con un tipo `value class` se habría serializado distinto en el test que en
 * producción, y el contrato validaría un JSON que el outbox no escribe.
 */
object ContractTestMapper {
    fun create(): JsonMapper =
        JsonMapper
            .builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()
}
