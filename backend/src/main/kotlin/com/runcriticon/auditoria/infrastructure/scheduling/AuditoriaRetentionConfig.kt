package com.runcriticon.auditoria.infrastructure.scheduling

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Cableado de [AuditoriaRetentionProperties] (ADR-0017 D8). */
@Configuration
@EnableConfigurationProperties(AuditoriaRetentionProperties::class)
class AuditoriaRetentionConfig
