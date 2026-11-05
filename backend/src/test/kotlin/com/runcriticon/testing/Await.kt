package com.runcriticon.testing

import org.awaitility.Awaitility
import java.time.Duration

/**
 * Esperas de los tests de integración que dependen de un listener asíncrono (`@ApplicationModuleListener` entrega
 * tras el commit). Sustituyen a los bucles `while (deadline) { …; Thread.sleep(25) }` escritos a mano en cada test y a
 * los `Thread.sleep` de tiempo fijo, con Awaitility por debajo.
 *
 * Tres formas, según lo que se afirme:
 * - [until] y [value]: algo **debe** ocurrir. Se vuelve a comprobar cada [POLL] y falla a los [DEFAULT_TIMEOUT]
 *   con el mensaje indicado, así que una CI lenta no produce un falso fallo.
 * - [holds]: algo **no** debe cambiar (una reentrega descartada, un evento fuera de orden). No basta con esperar un
 *   tiempo fijo y mirar una vez: se comprueba de forma continua durante [during].
 */
object Await {
    val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(5)
    val POLL: Duration = Duration.ofMillis(25)

    /** Espera a que [probe] devuelva `true`. */
    fun until(
        failure: String,
        atMost: Duration = DEFAULT_TIMEOUT,
        probe: () -> Boolean,
    ) {
        Awaitility
            .await(failure)
            .atMost(atMost)
            .pollInterval(POLL)
            .until { probe() }
    }

    /** Espera a que [probe] devuelva algo distinto de `null` y lo devuelve. */
    fun <T : Any> value(
        failure: String,
        atMost: Duration = DEFAULT_TIMEOUT,
        probe: () -> T?,
    ): T {
        var found: T? = null
        until(failure, atMost) {
            found = probe()
            found != null
        }
        return found!!
    }

    /**
     * Comprueba que [assertion] se cumple de forma continua durante [during]. Si no se cumple de entrada, sigue
     * intentándolo hasta [atMost] antes de fallar con el error de la propia aserción.
     */
    fun holds(
        during: Duration,
        atMost: Duration = during.plus(DEFAULT_TIMEOUT),
        assertion: () -> Unit,
    ) {
        Awaitility
            .await()
            .during(during)
            .atMost(atMost)
            .pollInterval(POLL)
            .untilAsserted { assertion() }
    }
}
