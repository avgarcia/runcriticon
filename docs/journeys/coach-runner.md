# User journey — Entrenador ↔ Alumno (a través de grupos)

> Journey principal del MVP mono-club. Cubre el bucle semanal entre un entrenador del club y los alumnos de **un grupo** asignado a él.

## Resumen

Una vez el [admin del club ha montado la estructura](admin-setup.md), el entrenador es quien hace que la herramienta tenga valor todas las semanas. El plan **se publica al grupo**, no a alumnos individuales. La personalización por alumno existe pero es la excepción.

> **Cómo se selecciona el grupo**: los grupos son **consultas nombradas sobre tags libres** definidos por el admin del club (ver [`vision.md`](../vision.md) y el [cierre del card-sort](../research/findings.md#cierre-del-card-sort-con-rg-y-vg)). El entrenador puede crear nuevos grupos como queries o usar los que el admin ya dejó montados; en ambos casos, publica el plan al grupo.

## Etapas

### 1. Primer acceso del entrenador

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Recibe email de invitación del admin del club. Hace clic, pone contraseña. Ve los grupos que el admin le asignó con su nombre propio. | "Vale, ya tengo aquí a mi gente con los nombres que usamos en el club" | Alivio + reconocimiento | Mostrar inmediato "Tus grupos: *Maratón Valencia avanzado* (12 alumnos), *Iniciación CACO* (8 alumnos), *Trail finde* (6 alumnos)" |

### 2. Diseño del plan semanal del grupo

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Abre el calendario del grupo *Maratón Valencia avanzado*, crea 6 sesiones de la semana | "Una sola vez para los 12 alumnos, no 12 veces. Y sé exactamente para qué los preparo." | **Ahorro de tiempo (clave)** | Atajos: duplicar sesión, copiar semana anterior, plantillas por distancia objetivo; tipos predefinidos |

### 3. Crear un grupo nuevo o ajustar pertenencia

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Necesita un subgrupo de los que entrenan series el martes. Crea un grupo nuevo como query (*nivel ∈ {medio-alto, alto} AND día-de-entreno = martes*) y le pone nombre *"Los del martes"*. Alternativa: ajusta manualmente la pertenencia de un alumno concreto si la query no lo cubre. | "El sistema me deja agrupar como yo quiera, no como me obliguen" | Control | Selector con chips para construir la query; vista previa de alumnos. Mover alumno entre grupos sin tocar sus tags (excepción manual) |

### 4. Personalización puntual de una sesión

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Una alumna está con molestias; ajusta la tirada larga solo para ella esta semana | "Sin desordenar el plan del resto" | Control | "Editar para X" sobre la sesión grupal — diff visible con el plan del grupo |

### 5. Publicación

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Pulsa "Publicar semana". Todos los alumnos del grupo reciben sus sesiones. | "Ya no tengo que mandar nada por WhatsApp" | Satisfacción | Confirmación clara + notificación a los alumnos |

### 6. Ejecución del alumno

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Cada mañana abre la app, ve "lo de hoy", entrena, marca como hecho con nota | "Lo tengo claro y rápido" | Satisfacción | Vista "hoy" minimalista; reporte en 1 click + nota opcional |

### 7. Seguimiento del entrenador por grupo

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Al final de la semana, abre la vista de seguimiento del grupo. Ve quién cumplió. | "En 1 minuto sé cómo va todo el grupo" | Control + ahorro | Heatmap semanal por alumno dentro del grupo; alerta suave para quien no reporta |

### 8. Cierre de bucle: comentario y ajuste

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| Comenta una sesión que le salió mal a una alumna; ajusta la siguiente semana | "Sin meterme en el WhatsApp del grupo" | Cercanía 1-a-1 | Comentario contextual por sesión (no chat global) |

### 9. Vista agregada del admin

| Acción | Pensamiento | Emoción | Oportunidad de producto |
|---|---|---|---|
| El admin del club revisa la vista de salud: el grupo de Carlos está al 85% de cumplimiento, el de Ana al 30% | "Tengo que hablar con Ana" | Información accionable | Dashboard club → grupo → alumno; navegación de tres niveles |

## Momentos críticos (donde más se cae el flujo)

- **Momento 1: primer plan semanal** — si publicar la primera semana cuesta más que el Excel actual, el entrenador no vuelve. **Esta es la batalla del MVP.**
- **Momento 2: personalización del grupo** — si no es posible ajustar sin romper el plan del resto, el entrenador no usará la herramienta para alumnos lesionados o irregulares.
- **Momento 3: tercera semana sin feedback** — si el entrenador no comenta nada al alumno en 2-3 semanas, el alumno deja de reportar.

## Hipótesis sobre el journey (a validar)

- Los entrenadores NO usarán la app si tienen que duplicar el plan por alumno (por eso el modelo es **plan por grupo**, no plan por alumno).
- El alumno abre la app solo si recibe valor en < 5 segundos.
- La importación de Strava no es crítica en MVP si el reporte manual es rapidísimo.
- La vista del admin del club es lo que justifica que el **club** adopte la herramienta institucionalmente.
