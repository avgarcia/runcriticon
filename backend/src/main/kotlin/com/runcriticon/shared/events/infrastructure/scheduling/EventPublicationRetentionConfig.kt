package com.runcriticon.shared.events.infrastructure.scheduling

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Cableado de [EventPublicationRetentionProperties] (ADR-0017 D8). */
@Configuration
@EnableConfigurationProperties(EventPublicationRetentionProperties::class)
class EventPublicationRetentionConfig
