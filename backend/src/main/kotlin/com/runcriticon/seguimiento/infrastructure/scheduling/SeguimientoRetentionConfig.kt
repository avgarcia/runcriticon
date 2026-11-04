package com.runcriticon.seguimiento.infrastructure.scheduling

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Cableado de [SeguimientoRetentionProperties] (ADR-0017 D8). */
@Configuration
@EnableConfigurationProperties(SeguimientoRetentionProperties::class)
class SeguimientoRetentionConfig
