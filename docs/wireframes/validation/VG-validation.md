# Validación de wireframes VG — captura

> Reporte de validación real. Copiado a `validation/VG-validation.md` tras finalizar la sesión de 75 minutos. Detalle del ejercicio en [`validation.md`](validation.md).

## Metadatos

- **Fecha:** 19 de mayo de 2026
- **Duración:** 78 minutos
- **Facilitador/a:** Diseñador / Product Manager (Runcriticon)
- **Notetaker** (si lo hay): AI Assistant
- **Modalidad:** Presencial (en las oficinas del club de running)
- **Grabación:** Sí (Audio y pantalla)
- **Iniciales del participante:** VG
- **Rol y contexto:** Dueño del club, Administrador principal y Entrenador en activo. Gestiona un club con más de 500 alumnos y coordina a otros 4 entrenadores.

---

## Tiempos cronometrados

| Tarea | Objetivo | Tiempo real | ¿Pasa? |
|---|---|---|---|
| 04 — Crear grupo de 2 condiciones | < 2 min | 1 min 15 s | sí |
| 05 — Construir semana de 6 sesiones (2º intento) | < 10 min | 4 min 45 s | sí |
| 06 — Sabe qué hace hoy desde abrir la app | < 5 s | N/A (Sesión Admin/Coach) | - |
| 07 — Reporta sesión "Hecho" | < 15 s | N/A (Sesión Admin/Coach) | - |

*Nota: Las pantallas 06 y 07 corresponden al perfil de Alumno y se marcaron como N/A siguiendo las instrucciones del plan de validación, que delega estos flujos a la Sesión 3 con socios finales.*

---

## Resultados por pantalla

### 01 — Onboarding wizard

- **Entiende los 5 pasos sin explicación**: sí
- **Pasos que querría saltar / añadir**: El paso 3 (invitar entrenadores) le pareció bien, pero comentó que en su caso los entrenadores ya están fijos de un año para otro, por lo que querría poder omitirlo rápidamente si no tiene altas nuevas ese mes.
- **Frases relevantes**: *"Me gusta ver que hay un final. Un wizard de 5 pasos se hace en 20 minutos mientras te tomas un café. Lo prefiero mil veces a que me suelten en una pantalla en blanco sin saber por dónde empezar."*
- **Observaciones**: Completó el flujo secuencial con fluidez. Confirmó que la opción A (wizard cerrado) es la idónea porque obliga a estructurar la base antes de meter datos a lo loco.

### 02 — Editor de tags (R17)

- **Renombra tag sin titubear**: sí
- **Añade valor sin ayuda**: sí
- **Distingue "archivar" de "borrar"**: sí
- **Confunde *tag* con *valor***: no
- **Funcionalidades pedidas y no presentes**: Pidió poder clonar un tag con metadatos para las distintas ediciones de carreras anuales (ej: 'Maratón Valencia 2025' y 'Maratón Valencia 2026') sin tener que reescribir los campos adicionales.
- **Frases relevantes**: *"Lo de precargar las carreras populares de la zona (la San Silvestre, el Medio Maratón...) es un puntazo. Si me das la lista vacía me da un perezón increíble ponerme a meter fechas y nombres."*
- **Veredicto regla de decisión**: pasa

### 03 — Gestión de alumnos

- **Encuentra bulk actions al primer intento**: sí
- **Entiende chip "+N"**: sí (dedujo de inmediato que ocultaba el resto de etiquetas secundarias)
- **Columnas que echó en falta**: Campo de "Teléfono de emergencia" o un indicador rápido de si el alumno ha pagado la cuota de la temporada (aunque entiende que esto es para entrenamiento, le simplificaría la vida al limpiar la lista).
- **Frases relevantes**: *"Si el importador inline me resalta en rojo las celdas duplicadas o con errores como el email mal puesto, me ahorras tres viajes de ida y vuelta al Excel de origen. Modificarlo en la propia tabla del navegador es clave."*
- **Veredicto regla de decisión**: pasa

### 04 — Constructor de grupos (R18 CRÍTICA)

- **Tiempo crear grupo 2 condiciones**: 75 s
- **Identifica que cada fila es una condición sobre un tag**: sí
- **Pregunta por sintaxis textual ("AND")**: no (el AND implícito visual funcionó a la perfección)
- **Encuentra ajustes manuales (M7)**: parcial (tardó unos segundos en desplegar el acordeón inferior, pero entendió el concepto al instante)
- **Vista previa le da confianza**: sí
- **Fricciones observadas**: Al principio intentó buscar un botón de "Guardar condición" fila por fila antes de darse cuenta de que el filtrado de la derecha se actualizaba en tiempo real (reactivo). Una vez captado el dinamismo, avanzó muy rápido.
- **Frases relevantes**: *"Tenía miedo de que esto fuera como programar una base de datos. Pero al ver los chips de los tags y el contador de alumnos bajando a la derecha a tiempo real mientras añado filtros, me da la seguridad de que no la estoy liando."*
- **Veredicto regla de decisión**: pasa

### 05 — Editor del plan semanal (la batalla)

- **Tiempo crear semana desde cero (1º intento)**: 11 min 20 s
- **Tiempo construir semana con "copiar semana anterior" (2º intento)**: 4 min 45 s
- **Reutiliza "copiar semana anterior" espontáneamente**: sí
- **Personaliza una sesión para un alumno sin pedir ayuda**: sí
- **Pide "modo libre" (escribir como frase)**: no (le gustó la segmentación de series, ritmos y recuperaciones porque lo conecta con la base de datos para análisis posteriores)
- **Comparado con su Excel actual**: más rápido
- **Frases relevantes**: *"Mi gran miedo con Runcriticon era tener que escribir el mismo plan 40 veces para subgrupos que solo cambian un detalle. Con el botón de 'copiar semana' del grupo hermano y la personalización en modal por alumno me habéis solucionado la vida. Esto me ahorra horas los domingos por la noche."*
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

- **Distingue 3 prioridades sin explicación**: sí
- **Sabe a quién atender primero y por qué**: sí (se fue directo al bloque rojo de alumnos con dolor reportado)
- **Entiende qué disparó cada alerta**: sí (apreció enormemente la cita literal del alumno acompañando a la alerta)
- **Tipos de alerta que pidió y no aparecen**: Pidió una alerta específica si un alumno acumula 3 sesiones seguidas en estado "Parcial", no solo cuando pasa a "No hecho", para pillar a tiempo la sobrecarga antes de que se lesione del todo.
- **Frases relevantes**: *"Para un club de más de 500 personas como el nuestro, esto es el corazón del sistema. Yo no puedo abrir el perfil de todos cada lunes. Que la app me tire arriba la lista de la gente que se queja de dolor de metatarso o rodilla me permite dar un servicio premium sin volverme loco."*
- **Veredicto regla de decisión**: pasa

### 09 — Salud del club

- **Identifica grupo con peor cumplimiento < 30s**: sí (detectó el grupo de iniciación en color ámbar de inmediato)
- **Entiende los 4 KPIs**: sí
- **KPIs que pidió y no aparecen**: Le gustaría ver el "Ratio de abandono" o "Alumnos durmientes" (aquellos que pagan pero llevan más de 21 días sin abrir la aplicación ni reportar nada).
- **Sugerencia de fusión le tiene sentido**: sí (afirmó que es algo que él hace a mano a mitad de temporada y que el sistema lo automatice es brutal)
- **Frases relevantes**: *"Esta es la pantalla que yo como dueño necesito mirar los viernes por la tarde. Me sirve para tirar de las orejas a los entrenadores que no están publicando los planes a tiempo o para reorganizar grupos que se han quedado desiertos."*
- **Veredicto regla de decisión**: pasa

---

## 3-5 citas literales destacadas

> *"Tenía pánico a que el constructor de grupos fuera tan técnico que mis entrenadores pasaran de usarlo y volvieran al Excel de toda la vida. Al ver que se añaden condiciones con dos clics y se previsualiza la lista al momento, respiro tranquilo."*

> *"El panel de alertas con la frase literal que pone el corredor en el móvil es oro puro. No pierdo el tiempo buscando quién va mal; voy directo a escribirle un WhatsApp al que le duele el sóleo."*

> *"Lo de sugerir fusiones de microgrupos es una genialidad. A veces creas grupos específicos para una carrera y se te quedan colgados con 2 o 3 personas. Juntarlos automáticamente ahorra tiempo de planificación."*

## Hallazgos transversales (cosas que aparecen en varias pantallas)

1. **Obsesión por el ahorro de tiempo en volumen alto:** Con más de 500 alumnos, VG filtra cada pantalla bajo el prisma de la eficiencia masiva. Acciones que en un club de 30 personas son tolerables (como corregir datos de uno en uno), para él son bloqueantes. Todo lo que sea edición masiva inline o automatización por lotes genera un efecto "wow" inmediato.
2. **Confianza absoluta en el sistema reactivo:** La actualización de datos en tiempo real (como la vista previa de alumnos al añadir tags o los cambios de color de los KPIs en base al periodo) reduce drásticamente la carga cognitiva y elimina la necesidad de botones redundantes de "Aceptar/Guardar".
3. **El dolor como métrica reina:** Tanto en el editor de planes, en la gestión de alumnos, como en el panel de alertas, el foco del entrenador veterano siempre se desvía hacia la prevención de lesiones y la gestión de molestias (tags de estado físico).

## Funcionalidades pedidas explícitamente (no presentes)

- **Alertas por acumulación de 'Parciales':** Disparar alerta amarilla si un corredor encadena 3 entrenamientos incompletos seguidos.
- **Métrica de Alumnos Durmientes:** En la pantalla de salud global, identificar usuarios inactivos digitalmente (que no abren la app en semanas).
- **Clonación de etiquetas con metadatos:** Poder duplicar la estructura de un tag de carrera popular cambiando únicamente el año/fecha del evento.

## Funcionalidades que descartó o consideró irrelevantes

- **El 'Modo Libre' de redacción de texto en planificación:** Prefiere mil veces el formulario estructurado actual porque entiende que si los entrenadores escriben en prosa, luego el sistema no podrá cruzar datos ni generar estadísticas de ritmos fiables en la salud del club.

## Próximos pasos

- **¿Acepta nueva sesión cuando haya cambios?** Sí, está totalmente disponible para la fase de diseño de alta fidelidad y prototipado interactivo.
- **¿Está dispuesto a probar el beta?** Sí, se ofrece como el primer club piloto para migrar 2 de sus grupos de maratón (aproximadamente 60 alumnos) en la fase beta cerrada.

---

## Lista priorizada de cambios derivados de esta sesión

| Prioridad | Pantalla | Cambio propuesto | Por qué |
|---|---|---|---|
| **Alta** | 08 — Alertas | Añadir regla de alerta por acumulación de 3 estados "Parciales" consecutivos. | Evita que un alumno con fatiga crónica o molestias leves pase desapercibido antes del parón total. |
| **Media** | 09 — Salud del club | Incorporar tarjeta de KPI para "Alumnos inactivos / durmientes" (>21 días sin loguearse). | Permite al negocio hacer retención proactiva antes de que el socio se dé de baja del club. |
| **Media** | 04 — Constructor | Añadir texto de ayuda o tooltips breves aclarando que la lista de la derecha es reactiva y se guarda sola. | Minimiza la duda inicial del usuario al buscar un botón explícito de "Guardar condición". |
| **Baja** | 02 — Editor Tags | Opción de "Duplicar Tag" heredando la configuración de tipos y metadatos. | Ahorra tiempo administrativo en la gestión anual de competiciones recurrentes. |
