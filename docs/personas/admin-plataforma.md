# Admin de plataforma

> Persona **descartada** (2026-11-12). El producto es mono-club permanente: este rol **no se construye** porque no hay nada que administrar a nivel de plataforma. El mantenimiento técnico se hace por BD/scripts. Se conserva el documento como registro de por qué no existe.

## Cuándo aparece

Habría tenido sentido si existiera un **segundo club** y la plataforma dejara de ser mono-tenant, para:

1. Crear y desactivar clubs.
2. Soporte transversal (resetear contraseña a un usuario, impersonar con consentimiento).
3. Métricas globales cross-club.
4. Facturación si llegado el caso hay monetización.

## Por qué no se construye ahora

- Con 1 club, todo lo anterior se resuelve con acceso directo a BD o con un script puntual.
- Construir un panel admin antes de tener tracción es caro, se queda obsoleto y compite con esfuerzo del MVP real.
- La fase "multi-club" que la haría necesaria no existe: el mono-club es permanente.
