package com.runcriticon.identidad.infrastructure.ratelimit

import io.github.bucket4j.TimeMeter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Cableado del rate-limiting. Habilita [RateLimitProperties] y publica las fuentes de tiempo por defecto de los
 * adaptadores. Ambos beans son `@ConditionalOnMissingBean`: los tests de tiempo controlado inyectan un [TimeMeter] /
 * [Clock] falso para verificar ventanas y backoff sin esperas reales.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitConfig {
    @Bean
    @ConditionalOnMissingBean(TimeMeter::class)
    fun rateLimitTimeMeter(): TimeMeter = TimeMeter.SYSTEM_MILLISECONDS

    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun rateLimitClock(): Clock = Clock.systemUTC()
}
