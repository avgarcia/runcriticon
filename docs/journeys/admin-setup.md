# User journey — Admin del club (puesta en marcha)

> Journey crítico del MVP mono-club. Cubre el primer mes del admin: del "estamos pensando probarlo" al "el club está funcionando dentro de la herramienta".

## Resumen

Sin admin no hay club operativo en la herramienta: este journey es el cuello de botella inicial. Si el admin tarda más de **una tarde** en tener al club montado y a los entrenadores avisados, lo abandona.

> **Nota sobre grupos**: en este MVP los grupos no se "crean" como nombres libres — se **derivan automáticamente** del cruce nivel × distancia × carrera objetivo de los alumnos. El trabajo del admin es mantener el catálogo de carreras, clasificar a los alumnos y asignar entrenadores a los grupos resultantes.

## Etapas

### 1. Acceso inicial

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Recibe credenciales (seed manual del equipo Runcriticon). Entra, ve un club vacío. | "A ver qué tengo que hacer" | Curiosidad + incertidumbre | Onboarding guiado en 4 pasos: 1) catálogo de carreras, 2) entrenadores, 3) alumnos clasificados, 4) entrenadores a grupos |

### 2. Mantener el catálogo de carreras

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Mete las 5-10 carreras objetivo de la temporada (nombre, fecha, distancia) | "Sin esto los alumnos no podrán apuntar carrera" | Atención | Plantilla con carreras populares precargadas y editables (MMM, San Silvestre, etc.) |

### 3. Alta de entrenadores

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Da de alta a los 4-5 entrenadores con nombre + email | "Espero que el sistema les avise" | Duda | Email transaccional fiable + link de acceso por 7 días; vista de "pendiente de aceptar" |

### 4. Alta masiva de alumnos (con clasificación)

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Tiene 80 socios en un Excel. Los da de alta uno a uno asignando nivel + distancia + carrera. | "Esto sí que es coñazo" | Fricción muy alta | **Import CSV con columnas (nombre, email, nivel, distancia, carrera)** — clave para no abandonar; validación previa con preview |

### 5. Revisar los grupos sugeridos

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| El sistema ha generado N grupos por la combinatoria de las 3 etiquetas; el admin los revisa | "Algunos tienen 1 solo alumno, otros 25" | Sorpresa | Marcar grupos demasiado pequeños o grandes; sugerir fusiones (ej. "1500m y 5k iniciación pueden compartir grupo") |

### 6. Ajustes manuales puntuales

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Mueve a 2-3 alumnos a un grupo distinto del que la taxonomía sugiere (porque conoce su caso) | "La regla general no aplica a todos" | Control | Excepción explícita por alumno; marcar visualmente "asignado manualmente, no recoloca al cambiar clasificación" |

### 7. Asignar entrenadores a grupos

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Pone 1-2 entrenadores en cada grupo | "Cada grupo tiene que tener responsable" | Atención | Vista "grupos sin entrenador" como alerta hasta cubrirlos todos |

### 8. Verificación y handoff a entrenadores

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Confirma que cada entrenador "tiene su grupo" y avisa por WhatsApp | "Espero que ellos hagan su parte" | Cesión de control | Botón "notificar a los entrenadores que ya pueden empezar"; checklist visible |

### 9. Monitorización mensual

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Una vez al mes mira la vista de salud: % de alumnos activos, grupos sin actividad, grupos sin entrenador | "¿Quién no está enchufado?" | Control | Dashboard simple: por grupo, último entreno reportado, % de cumplimiento, alertas de grupos huérfanos |

## Momentos críticos

- **Mantener el catálogo de carreras al día** — si caducan o faltan, los alumnos no se pueden clasificar.
- **Alta masiva de alumnos con clasificación** — sin import CSV bien diseñado el admin abandona. La columna "carrera objetivo" debe aceptar el código de la carrera del catálogo (o "ninguna").
- **Grupos huérfanos o demasiado pequeños** — la taxonomía puede generar muchos micro-grupos. Hay que ayudar al admin a detectarlos.
- **Email de invitación a entrenadores** — si va a spam, el admin descarta la herramienta.
- **Primera semana sin actividad** — si los entrenadores no han publicado plan a los 7 días, el admin pierde la fe.
