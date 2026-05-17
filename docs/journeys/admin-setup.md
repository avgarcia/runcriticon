# User journey — Admin del club (puesta en marcha)

> Journey crítico del MVP mono-club. Cubre el primer mes del admin: del "estamos pensando probarlo" al "el club está funcionando dentro de la herramienta".

## Resumen

Sin admin no hay club operativo en la herramienta: este journey es el cuello de botella inicial. Si el admin tarda más de **una tarde** en tener al club montado y a los entrenadores avisados, lo abandona.

> **Nota sobre grupos**: tras el [card-sort con RG y VG](../research/findings.md#cierre-del-card-sort-con-rg-y-vg), los grupos del MVP no son una taxonomía rígida sino **consultas nombradas sobre tags libres** definidos por el club. El sistema pre-carga un set sensato (nivel, distancia, objetivo / catálogo de carreras, terreno, estado) y el admin lo adapta. Detalle en [`vision.md`](../vision.md).

## Etapas

### 1. Acceso inicial

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Recibe credenciales (seed manual del equipo Runcriticon). Entra y ve un club vacío con una taxonomía pre-cargada visible. | "A ver qué tengo que hacer" | Curiosidad + incertidumbre | Onboarding guiado en 5 pasos: 1) revisar taxonomía, 2) catálogo de carreras, 3) entrenadores, 4) alumnos con tags, 5) crear grupos. Cada paso con checklist |

### 2. Revisar y adaptar la taxonomía del club

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Ve los tags pre-cargados (nivel, distancia, objetivo, terreno, estado). Renombra alguno ("terreno" → "tipo de carrera"), añade un tag propio ("día de entreno") y borra los que no aplican a su club. | "Bien, esto se parece a cómo hablamos en el club" | Reconocimiento + apropiación | Drag-and-drop para reordenar tags, valores editables in-line, no obligar a confirmar guardado tras cada edit |

### 3. Mantener el catálogo de carreras

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Edita los valores del tag `objetivo` (= el catálogo de carreras). Mete las 5-10 carreras de la temporada con su fecha y distancia. Mantiene "sin carrera" como valor para los de mantenimiento. | "Sin esto los alumnos no tienen objetivo asignable" | Atención | Plantilla con carreras populares precargadas (MMM, San Silvestre, Maratón Valencia, etc.) editables |

### 4. Alta de entrenadores

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Da de alta a los 4-5 entrenadores con nombre + email. | "Espero que el sistema les avise" | Duda | Email transaccional fiable + link de acceso por 7 días; vista de "pendiente de aceptar" |

### 5. Alta masiva de alumnos con tags

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Tiene 80 socios en un Excel. Necesita darlos de alta asignando tags a cada uno. | "Esto sí que es coñazo" | Fricción muy alta | **Import CSV con columnas dinámicas según la taxonomía del club** (nombre, email, nivel, distancia, objetivo, terreno, estado, …). Validación previa con preview y reporte de errores línea a línea. Un alumno puede tener varios valores del mismo tag separados por `;` |

### 6. Crear los grupos del club

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| El admin (o un entrenador) crea grupos como consultas sobre tags. Ej.: *"Maratón Valencia avanzado"* = objetivo: Maratón Valencia AND nivel ∈ {medio-alto, alto}. Le pone nombre, ve la lista de alumnos que entran y lo guarda. | "Esto es exactamente lo que tengo en la cabeza" | Satisfacción | UI tipo **selector con chips**: el admin elige tags y valores con clic, no escribe. Vista previa instantánea de alumnos que caen. Plantillas de grupos comunes para arrancar |

### 7. Detección de micro-grupos

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| El sistema avisa: *"el grupo X tiene 1 alumno · el grupo Y tiene 2 alumnos · los grupos Z y W comparten el 90% de membresía"*. Sugiere generalizar o fusionar. | "Mejor un grupo de 8 que cuatro grupos de 2" | Alivio | Mitiga R16. Sugerencia accionable con un clic ("generalizar filtro: quitar la restricción de carrera"). |

### 8. Asignar entrenadores a grupos

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Pone 1-2 entrenadores en cada grupo. | "Cada grupo tiene que tener responsable" | Atención | Vista "grupos sin entrenador" como alerta hasta cubrirlos todos |

### 9. Verificación y handoff a entrenadores

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Confirma que cada entrenador "tiene su grupo" y avisa por WhatsApp. | "Espero que ellos hagan su parte" | Cesión de control | Botón "notificar a los entrenadores que ya pueden empezar"; checklist visible |

### 10. Monitorización mensual

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Una vez al mes mira la vista de salud: % de alumnos activos, grupos sin actividad, grupos sin entrenador, micro-grupos pendientes de fusionar. | "¿Quién no está enchufado?" | Control | Dashboard simple: por grupo, último entreno reportado, % de cumplimiento, alertas de grupos huérfanos o micro-grupos |

## Momentos críticos

- **Revisar la taxonomía pre-cargada (paso 2)** — si no le encaja nada, abandona. Por eso los tags pre-cargados deben ser sensatos y editables a la primera. Riesgo R17.
- **Crear el primer grupo (paso 6)** — si el constructor de filtros le parece técnico, no completa el alta. Necesita ser visual (chips, plantillas). Riesgo R18.
- **Alta masiva de alumnos con tags (paso 5)** — sin import CSV bien diseñado el admin abandona. Las columnas del CSV deben adaptarse a su taxonomía, no al revés.
- **Email de invitación a entrenadores** — si va a spam, el admin descarta la herramienta.
- **Primera semana sin actividad** — si los entrenadores no han publicado plan a los 7 días, el admin pierde la fe.
