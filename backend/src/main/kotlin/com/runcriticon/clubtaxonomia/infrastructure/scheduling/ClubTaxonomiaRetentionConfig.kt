package com.runcriticon.clubtaxonomia.infrastructure.scheduling

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Cableado de [ClubTaxonomiaRetentionProperties] (ADR-0017 D8). */
@Configuration
@EnableConfigurationProperties(ClubTaxonomiaRetentionProperties::class)
class ClubTaxonomiaRetentionConfig
