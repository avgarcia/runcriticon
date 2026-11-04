package com.runcriticon.planificacion.infrastructure.scheduling

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Cableado de [PlanificacionRetentionProperties] (ADR-0017 D8). */
@Configuration
@EnableConfigurationProperties(PlanificacionRetentionProperties::class)
class PlanificacionRetentionConfig
