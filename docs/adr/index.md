# Decisiones de arquitectura de Runcriticon

**Runcriticon** es una aplicación web para gestionar entrenos de running: los entrenadores crean planes de entrenamiento y los publican a grupos de alumnos, que ejecutan sus sesiones y las reportan.

Este sitio recoge los **Architecture Decision Records (ADR)** del proyecto. Cada ADR documenta **una** decisión —su contexto, las opciones consideradas, la elegida y sus consecuencias— en formato **MADR**, ligero y versionado junto al código.

## Estado

Los **16 ADR** del corpus inicial están en estado **Aceptado** tras la revisión Nivel 1 (índice de sub-decisiones con tabla, premisas heredadas, NFRs propios, sub-decisiones numeradas con anchors) completada entre el 2026-05-27 y el 2026-05-30. Los disparadores de reapertura quedan consolidados en el **índice maestro del [ADR-0015](0015-temas-aplazados-fuera-del-mvp.md)**.

## Por dónde empezar

- **ADR-0001** — el *stack* de la aplicación: Spring Boot (Kotlin) + Angular.
- **ADR-0007** — monolito modular con comunicación *events-first*.
- **ADR-0008** — arquitectura hexagonal y DDD aplicados con criterio.
- **ADR-0015** — índice maestro de aplazamientos: *"¿qué queda fuera del MVP y cuándo se reabre?"*.

Usa la navegación de la izquierda para recorrer todos los ADR, ordenados y con su estado.
