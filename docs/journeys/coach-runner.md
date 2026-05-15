# User journey — Entrenador ↔ Corredor

> Borrador del flujo principal del MVP. Refinar con los hallazgos de las entrevistas.

## Resumen

Recorre lo que ocurre desde que un entrenador decide trabajar con un nuevo corredor hasta que cierran la primera semana juntos en la plataforma.

## Etapas

### 1. Alta del entrenador

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Crea cuenta como entrenador, completa onboarding (nombre, foto, especialidad) | "A ver si esto me ahorra el Excel" | Curiosidad + escepticismo | Onboarding < 2 min, ejemplo de plan de muestra precargado |

### 2. Invitación al corredor

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Pulsa "Invitar corredor", introduce email y mensaje | "Espero que no me complique al corredor" | Duda | Invitación con texto editable, vista previa del email |

### 3. Alta del corredor

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Recibe email, hace clic, crea cuenta con Google. Ve que ya está vinculado a su entrenador. | "Hostia, qué rápido" | Sorpresa positiva | Mostrar de inmediato "Tu entrenador es X" para reforzar el vínculo |

### 4. Diseño del plan semanal por el entrenador

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Abre calendario del corredor, crea 6 sesiones de la semana | "Esto tiene que ser más rápido que mi Excel" | Tensión: fricción real es alta | Atajos de teclado, duplicar sesión, copiar semana anterior, tipos de sesión predefinidos |

### 5. Ejecución del plan por el corredor

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Cada mañana abre la app, ve el entreno del día, sale a correr, marca como hecho con nota | "Por fin lo tengo claro y rápido" | Satisfacción | Vista "hoy" minimalista; reporte en 1 click + nota opcional |

### 6. Seguimiento por el entrenador

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Al final de la semana, abre vista de seguimiento. Ve quién cumplió y quién no. | "En 1 minuto sé cómo va todo el grupo" | Control + ahorro de tiempo | Heatmap semanal de cumplimiento; alertas suaves para corredores que no reportan |

### 7. Cierre de bucle: comentario y ajuste

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Entrenador comenta una sesión que salió mal, ajusta la siguiente semana | "El corredor sabe que le veo" | Cercanía | Comentario contextual por sesión (no chat suelto) |

## Momentos críticos (donde más se cae el usuario)

- **Momento 1: alta del corredor** — si el email se pierde, todo se rompe. Hay que tener email transaccional fiable y un link alternativo manual.
- **Momento 2: primera semana de plan** — si crear 7 sesiones cuesta más de 5 minutos al entrenador, no volverá. Es la batalla del MVP.
- **Momento 3: tercera semana sin feedback** — si el entrenador no comenta nada en 2-3 semanas, el corredor abandona. El producto debe empujarlo.

## Hipótesis sobre el journey (a validar)

- El entrenador NO usará la app si tiene que duplicar el trabajo que hoy hace en Excel.
- El corredor abre la app solo si recibe valor en < 5 segundos.
- La importación de Strava no es crítica en MVP si el reporte manual es rapidísimo.
