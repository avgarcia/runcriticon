# Admin de plataforma

> Persona **muy post-MVP**. En el alcance mono-club del MVP **no se construye** este rol: no hay nada que administrar a nivel de plataforma porque solo existe un club. El mantenimiento técnico se hace por BD/scripts.

## Cuándo aparece

Cuando exista un **segundo club** y la plataforma deje de ser mono-tenant. Entonces este rol tendrá sentido para:

1. Crear y desactivar clubs.
2. Soporte transversal (resetear contraseña a un usuario, impersonar con consentimiento).
3. Métricas globales cross-club.
4. Facturación si llegado el caso hay monetización.

## Por qué no se construye ahora

- Con 1 club, todo lo anterior se resuelve con acceso directo a BD o con un script puntual.
- Construir un panel admin antes de tener tracción es caro, se queda obsoleto y compite con esfuerzo del MVP real.
- Es la primera funcionalidad a abordar en la fase "multi-club", no antes.
