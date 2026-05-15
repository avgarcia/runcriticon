# User journey — Admin del club (puesta en marcha)

> Journey crítico del MVP mono-club. Cubre el primer mes del admin: del "estamos pensando probarlo" al "el club está funcionando dentro de la herramienta".

## Resumen

Sin admin no hay club operativo en la herramienta: este journey es el cuello de botella inicial. Si el admin tarda más de **una tarde** en tener al club montado y a los entrenadores avisados, lo abandona.

## Etapas

### 1. Acceso inicial

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Recibe credenciales (seed manual del equipo Runcriticon). Entra, ve un club vacío. | "A ver qué tengo que hacer" | Curiosidad + incertidumbre | Onboarding guiado en 3 pasos: 1) crea grupos, 2) da de alta entrenadores, 3) da de alta alumnos |

### 2. Crear los grupos del club

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Crea 3-5 grupos (iniciación, populares, avanzados, maratón, mujeres) | "Esto coincide con cómo los llamamos en el club" | Satisfacción | Permitir renombrar libre; no imponer taxonomía fija |

### 3. Alta de entrenadores

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Da de alta a los 4-5 entrenadores con nombre + email | "Espero que el sistema les avise" | Duda | Email transaccional fiable + link de acceso por 7 días; vista de "pendiente de aceptar" |

### 4. Alta masiva de alumnos

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Tiene 80 socios en un Excel. Los da de alta uno a uno. | "Esto es coñazo" | Fricción alta | **Import CSV con columnas (nombre, email, grupo)** — clave para no abandonar |

### 5. Asignar alumnos a grupos

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Cada alumno entra en uno o varios grupos | "Que pueda mover sin perder histórico" | Atención | Drag & drop o multiselección. Permitir alumno en varios grupos. |

### 6. Verificación y handoff a entrenadores

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Confirma que cada entrenador "tiene su grupo" y avisa por WhatsApp | "Espero que ellos hagan su parte" | Cesión de control | Botón "notificar a los entrenadores que ya pueden empezar"; checklist visible |

### 7. Monitorización mensual

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Una vez al mes mira la vista de salud: % de alumnos activos, grupos sin actividad | "¿Quién no está enchufado?" | Control | Dashboard simple: por grupo, último entreno reportado, % de cumplimiento |

## Momentos críticos

- **Alta masiva de alumnos** — sin import CSV el admin abandona. Es el punto de fricción más alto.
- **Email de invitación a entrenadores** — si va a spam, el admin descarta la herramienta.
- **Primera semana sin actividad** — si los entrenadores no han publicado plan a los 7 días, el admin pierde la fe.
