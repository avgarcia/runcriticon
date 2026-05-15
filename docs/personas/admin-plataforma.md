# Admin de plataforma

> Persona **interna y post-MVP**. En el primer release lo mínimo se gestiona directamente en BD o con scripts; no se construye un panel admin completo.

## Perfil

- Rol interno del equipo (soporte, operaciones o el propio fundador en fase temprana).

## Necesidades principales

1. Ver y gestionar usuarios (entrenadores, corredores, clubs).
2. Resolver incidencias (resetear contraseña, desactivar cuenta).
3. Ver métricas globales de uso (DAU/WAU, planes creados, retención).
4. Impersonar un usuario para reproducir un problema (con consentimiento).

## Por qué es post-MVP

- En MVP, con < 50 usuarios beta, basta con acceso a BD + dashboards externos (Metabase, Grafana, o incluso una hoja).
- Construir un panel admin antes de tener tracción es caro y se queda obsoleto.

## Cuándo construirlo

- Cuando el soporte manual supere ~3 horas/semana del equipo.
- Cuando entren los primeros clubs (gestión de grupos requiere herramientas).
