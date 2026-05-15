# Personas

Borrador de los perfiles de usuario que Runcriticon debe soportar en el alcance **mono-club** del MVP. Se refinan tras las entrevistas de la semana 1.

## Personas del MVP (todas son MVP en el alcance mono-club)

- [Admin del club](admin-club.md) — gestiona altas, grupos y la asignación entrenador ↔ grupo.
- [Carlos — Entrenador del club](carlos-entrenador.md) — diseña y publica planes a sus grupos.
- [Marta — Alumna del club](marta-alumna.md) — sigue el plan de su grupo y reporta.

## Personas post-MVP (visibles pero no se construyen ahora)

- [Admin de plataforma](admin-plataforma.md) — solo aplica cuando haya un segundo club. En MVP, mantenimiento técnico por BD/scripts.

## Resumen del modelo de roles en MVP

```
Club (único)
├── Admin del club              (1 persona)
│   └── puede gestionar entrenadores, alumnos y grupos
├── Entrenadores                (N)
│   └── publican planes a sus grupos
├── Grupos                      (N: "iniciación", "avanzados", "maratón"…)
│   ├── tienen entrenador(es) asignado(s)
│   └── tienen alumnos miembros
└── Alumnos                     (M)
    └── pertenecen a 1+ grupos
```
