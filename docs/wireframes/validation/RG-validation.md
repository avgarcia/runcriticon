# Validación de wireframes RG — captura

> Reporte de validación real. Copiado a `validation/RG-validation.md` tras finalizar la sesión de 75 minutos. Detalle del ejercicio en [`validation.md`](validation.md).

## Metadatos

- **Fecha:** 19 de mayo de 2026
- **Duración:** 74 minutos
- **Facilitador/a:** Diseñador / Product Manager (Runcriticon)
- **Notetaker** (si lo hay): AI Assistant
- **Modalidad:** Videollamada (pantalla compartida mediante Miro/Figma lo-fi)
- **Grabación:** Sí (Audio y video)
- **Iniciales del participante:** RG
- **Rol y contexto:** Coordinadora técnica y Entrenadora principal de los grupos de alto rendimiento. Gestiona de manera directa e indirecta a más de 500 alumnos junto con el equipo de entrenadores de apoyo.

---

## Tiempos cronometrados

| Tarea | Objetivo | Tiempo real | ¿Pasa? |
|---|---|---|---|
| 04 — Crear grupo de 2 condiciones | < 2 min | 1 min 38 s | sí |
| 05 — Construir semana de 6 sesiones (2º intento) | < 10 min | 6 min 12 s | sí |
| 06 — Sabe qué hace hoy desde abrir la app | < 5 s | N/A (Sesión Admin/Coach) | - |
| 07 — Reporta sesión "Hecho" | < 15 s | N/A (Sesión Admin/Coach) | - |

*Nota: Siguiendo el protocolo metodológico, las pantallas de cara al socio final (06 y 07) quedan pendientes de validación para la Sesión 3 con corredores amateur.*

---

## Resultados por pantalla

### 01 — Onboarding wizard

- **Entiende los 5 pasos sin explicación**: sí
- **Pasos que querría saltar / añadir**: El paso 4 (crear grupos iniciales) le generó dudas de si debía crearlos todos en ese instante. Sugiere un botón claro de "Omitir o crear más tarde" para no paralizar el flujo de configuración inicial.
- **Frases relevantes**: *"Es muy limpio. En otros programas corporativos te piden mil datos de configuración antes de ver un menú real. Aquí vas al grano."*
- **Observaciones**: Navegó por los pasos sin atascarse. Validó con entusiasmo la opción A del Wizard secuencial, indicando que frena los errores de configuración de sus entrenadores junior.

### 02 — Editor de tags (R17)

- **Renombra tag sin titubear**: sí
- **Añade valor sin ayuda**: sí
- **Distingue "archivar" de "borrar"**: sí (entendió perfectamente que archivar mantenía el histórico de los alumnos antiguos sin romper sus métricas pasadas)
- **Confunde *tag* con *valor***: no
- **Funcionalidades pedidas y no presentes**: Pidió que el sistema autodetecte la distancia de una carrera popular si se escribe el nombre oficial (ej. que si pone "Media Maratón de Madrid" se asigne automáticamente el metadato de 21.097m).
- **Frases relevantes**: *"Lo de poder archivar un tag es vital. Cada año las carreras cambian de nombre o dejamos de llevar a gente a ciertos objetivos; si los borro, me cargo el registro de entrenamientos de temporadas anteriores."*
- **Veredicto regla de decisión**: pasa

### 03 — Gestión de alumnos

- **Encuentra bulk actions al primer intento**: parcial (tardó un par de segundos en darse cuenta de que debía seleccionar los checkboxes de la izquierda para que apareciera la barra superior de acciones masivas)
- **Entiende chip "+N"**: sí
- **Columnas que echó en falta**: Campo para "Nivel de experiencia en años de running" o si el alumno posee una marca acreditada oficial.
- **Frases relevantes**: *"Cuando entran 150 alumnos nuevos en septiembre, el importador masivo nos ahorra la vida. Si tengo que meterlos a mano uno por uno en una lista desplegable, directamente no usamos la plataforma."*
- **Veredicto regla de decisión**: pasa

### 04 — Constructor de grupos (R18 CRÍTICA)

- **Tiempo crear grupo 2 condiciones**: 98 s
- **Identifica que cada fila es una condición sobre un tag**: sí
- **Pregunta por sintaxis textual ("AND")**: no
- **Encuentra ajustes manuales (M7)**: sí (vio la sección colapsada abajo y la desplegó de forma intuitiva para comprobar el caso de alumnos excepcionales)
- **Vista previa le da confianza**: sí (es lo que más alabó de la pantalla)
- **Fricciones observadas**: Al añadir la segunda condición, buscó un botón de "Ejecutar filtro" o "Buscar". Le costó un instante asimilar que la lista de la derecha se filtraba de manera reactiva al instante. Una animación sutil de carga resolvería este micro-bloqueo.
- **Frases relevantes**: *"Esto es justo lo que necesitábamos. En mi cabeza yo organizo los entrenos pensando: 'A los que preparan maratón y tienen nivel alto les toca series largas'. Ver que la pantalla traduce ese pensamiento exacto sin fórmulas raras es una maravilla."*
- **Veredicto regla de decisión**: pasa

### 05 — Editor del plan semanal (la batalla)

- **Tiempo crear semana desde cero (1º intento)**: 14 min 05 s
- **Tiempo construir semana con "copiar semana anterior" (2º intento)**: 6 min 12 s
- **Reutiliza "copiar semana anterior" espontáneamente**: sí
- **Personaliza una sesión para un alumno sin pedir ayuda**: sí
- **Pide "modo libre" (escribir como frase)**: no
- **Comparado con su Excel actual**: más estructurado y rápido en el segundo intento.
- **Frases relevantes**: *"El botón de 'Copiar semana anterior' reduce el trabajo del domingo a un tercio del tiempo. Mis entrenadores suelen repetir bloques de carga de tres semanas variando solo intensidades; esto les va a encantar."*
- **Veredicto regla de decisión**: pasa

### 06 — Vista "hoy" del alumno (H3) — solo en sesión alumno

- **Tiempo desde abrir hasta "sé qué hago hoy"**: N/A
- **Identifica "Personalizada para ti"**: N/A
- **Lee las notas del entrenador**: N/A
- **Frases relevantes**: N/A
- **Veredicto regla de decisión**: N/A

### 07 — Reporte + reajuste — solo en sesión alumno

- **Tiempo reporte "Hecho"**: N/A
- **Diferencia "Parcial" de "No hecho"**: N/A
- **Encuentra flag de dolor**: N/A
- **Mueve sesión a otro día sin ayuda**: N/A
- **RPE le motiva / le incomoda / indiferente**: N/A
- **Frases relevantes**: N/A
- **Veredicto regla de decisión**: N/A

### 08 — Panel de alertas

- **Distingue 3 prioridades sin explicación**: sí (el código de colores tipo semáforo que ella misma sugirió en el discovery funcionó de inmediato)
- **Sabe a quién atender primero y por qué**: sí (directa a los avisos rojos por molestias físicas)
- **Entiende qué disparó cada alerta**: sí
- **Tipos de alerta que pidió y no aparecen**: Una alerta cuando un corredor de alto rendimiento registra ritmos significativamente inferiores a su media habitual durante tres sesiones seguidas (alerta de posible sobreentrenamiento silbando en segundo plano).
- **Frases relevantes**: *"¡Por fin! Esto es exactamente lo que os pedí. Yo no quiero perder tres horas revisando 500 entrenamientos perfectos de gente que va como un reloj. Muéstrame las excepciones: quién tiene dolor en el tendón o quién lleva una semana desaparecido. Esta pantalla es el corazón de mi día a día."*
- **Veredicto regla de decisión**: pasa

### 09 — Salud del club

- **Identifica grupo con peor cumplimiento < 30s**: sí
- **Entiende los 4 KPIs**: sí
- **KPIs que pidió y no aparecen**: Un índice agregado de "Felicidad o Bienestar del Club" basado en los comentarios de los reportes.
- **Sugerencia de fusión le tiene sentido**: sí (confirmó que gestionan demasiados microgrupos residuales que saturan a sus entrenadores)
- **Frases relevantes**: *"Como coordinadora técnica, esta pantalla me da la foto real del rendimiento de mis entrenadores. Si veo un grupo con un 40% de cumplimiento, sé que el entrenador está poniendo planes demasiado duros o no está motivando a la gente."*
- **Veredicto regla de decisión**: pasa

---

## 3-5 citas literales destacadas

> *"No me muestres los 500 entrenamientos que van bien, muéstrame las alertas. El semáforo rojo para dolores reportados en carrera nos evita perder atletas por lesiones graves."*

> *"El constructor de filtros dinámico es súper visual. Tenía pánico a tener que enseñar lógica booleana a los nuevos entrenadores del staff, pero con este sistema de tarjetas se aprende a usar en dos minutos."*

> *"La vista de salud global es la que me va a permitir controlar si los grupos piloto están funcionando o si la gente se está descolgando de los planes antes de que decidan darse de baja del club."*

## Hallazgos transversales (cosas que aparecen en varias pantallas)

1. **Gestión por Excepción (El mantra de RG):** El valor real de la aplicación para ella no radica en el registro de datos, sino en el filtrado inteligente de problemas. Valora drásticamente que el sistema esconda la normalidad y resalte la anomalía.
2. **Claridad terminológica:** El uso de conceptos propios del running (series, recuperaciones, objetivos, ritmos) integrados de forma nativa en la UI minimiza cualquier resistencia técnica al software.
3. **Control del equipo de entrenadores:** A diferencia de un entrenador independiente, RG evalúa la herramienta pensando en cómo afectará al flujo de trabajo del resto del staff técnico que tiene a su cargo.

## Funcionalidades pedidas explícitamente (no presentes)

- **Alerta de Sobreentrenamiento:** Algoritmo básico que compare los ritmos de los últimos 3 reportes con el histórico del usuario para detectar bajadas drásticas de rendimiento no justificadas.
- **Botón de omisión en el Wizard:** Capacidad de saltarse la asignación de grupos en el onboarding para resolverlo a posteriori desde el panel standalone.

## Funcionalidades que descartó o consideró irrelevantes

- **Introducción de comentarios en formato prosa libre dentro del plan general:** Defiende que la estructura estricta ayuda a mantener un estándar de calidad unificado entre todos los entrenadores del club.

## Próximos pasos

- **¿Acepta nueva sesión cuando haya cambios?** Sí, solicita ser avisada con prioridad para revisar el prototipo funcional e interactivo de la pantalla de Alertas (08).
- **¿Está dispuesto a probar el beta?** Sí, está dispuesta a liderar la migración de todo el sector de corredores de fondo avanzados al sistema a partir del próximo mes.

---

## Lista priorizada de cambios derivados de esta sesión

| Prioridad | Pantalla | Cambio propuesto | Por qué |
|---|---|---|---|
| **Alta** | 04 — Constructor | Añadir un micro-indicador visual (loader animado) en la vista previa derecha para hacer explícito que el filtrado es reactivo y en tiempo real. | Elimina la confusión inicial del usuario al buscar un botón manual de "Buscar/Aplicar". |
| **Media** | 01 — Onboarding | Añadir un botón claro de "Configurar más tarde" en el paso de creación de grupos iniciales. | Evita el efecto embudo si el administrador no tiene claro el esquema organizativo completo el primer día. |
| **Media** | 08 — Alertas | Diseñar una alerta amarilla por "Bajo rendimiento continuado" (caída de ritmos durante 3 sesiones). | Permite detectar fatiga crónica o sobreentrenamiento antes de que el alumno sufra una rotura física. |
| **Baja** | 03 — Gestión Alumnos | Habilitar un feedback visual más evidente cuando se activan las 'bulk actions' al marcar los checkboxes. | Aumenta la descubribilidad de las herramientas de edición en lote. |
