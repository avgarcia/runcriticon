# Validación de wireframes con el club piloto

> Ejercicio para validar los 9 wireframes lo-fi con usuarios reales **antes de programar nada**. Mismo enfoque metodológico que el [card-sort](../research/card-sort.md): tareas concretas, observación de pensamiento en voz alta, **regla de decisión fijada de antemano** para no racionalizar resultados a posteriori.

## Objetivo

- Confirmar que las 6 pantallas más arriesgadas se entienden sin formación: **02** (R17), **03**, **04** (R18 crítica), **05** (la batalla del MVP), **06** (H3 <5s), **07** (<15s).
- Detectar variantes alternativas que el diseñador deba dibujar para validación A/B en la siguiente iteración.
- Sacar lista priorizada de cambios concretos antes de pasar a sistema visual y prototipo.

## Sesiones planificadas

Tres tipos de sesión, 2-3 sesiones reales:

| # | Tipo | Participante | Duración | Pantallas a validar |
|---|------|--------------|----------|---------------------|
| 1 | Entrenador/Admin | **RG** (500 alumnos) | 75 min | 01, 02, 03, 04, 05, 08, 09 |
| 2 | Entrenador/Admin | **VG** (180 alumnos) | 75 min | mismas que RG |
| 3 | Alumno × 2-3 | Socios del club (RG/VG pasan contactos) | 30-45 min cada uno | 06, 07 |

Total estimado de campo: 4-5 horas + 2-3 horas de síntesis.

## Logística

- **Modalidad**: videollamada con pantalla compartida. Si es presencial, mejor — observación más rica.
- **Materiales**: `docs/wireframes/html/index.html` servido en GitHub Pages o localmente (`python3 -m http.server` sobre `docs/wireframes/html/`).
- **Roles**: 1 facilitador (Antonio) + 1 notetaker. Si no hay notetaker, grabar con consentimiento.
- **Grabación**: sí, con consentimiento. El oro está en lo que el usuario **dice mientras hace clic**, no en lo que acaba pulsando.
- **Captura**: una sesión por archivo en `docs/wireframes/validation/[INICIALES]-validation.md` usando [`validation-template.md`](validation-template.md).

## Preparación previa (a hacer antes de la sesión)

### Materiales por sesión

1. **Link a los wireframes** servido (no enviar HTML por email — molesta navegar).
2. **Lista de tareas impresa o en una segunda pantalla** (las del apartado "Guion por pantalla").
3. **Plantilla de captura abierta** en otra ventana.
4. **Cronómetro** mental o real para medir 3 tiempos críticos:
   - Crear un grupo de 2 condiciones (objetivo: < 2 min, R18).
   - Construir la semana de 6 sesiones (objetivo: < 10 min en 2º intento, batalla del MVP).
   - Alumno entiende qué tiene que hacer hoy (objetivo: < 5s, H3).

### Antes de empezar

- **Recordar al participante** que validamos *los wireframes*, no a él. No hay respuestas correctas. *"Lo que falle aquí me ayuda más que lo que funcione"*.
- **Pedir consentimiento** para grabar.
- **No defender el diseño**. Si se atasca, anotar la fricción, no explicar la pantalla.

---

## Guion por pantalla (sesión entrenador/admin)

Orden recomendado: 01 → 02 → 03 → 04 → 05 → 08 → 09. Sigue el journey natural del admin/entrenador.

### 01 — Onboarding wizard (5 min)

**Tarea**: *"Esta es la primera vez que entras a Runcriticon como admin de tu club. Cuéntame qué harías."*

**Preguntas**:

1. ¿Entiendes los 5 pasos? ¿Hay alguno que no esperabas?
2. ¿Saltarías alguno hoy? ¿Cuál y por qué?
3. ¿Lo verías como una ayuda o como una imposición?

**Observar**: confusión en el stepper, en el botón "salir y continuar luego", o si pide volver a un paso anterior.

### 02 — Editor de tags (R17, 10 min)

**Tarea libre primero**: *"Imagina que quieres adaptar la taxonomía a tu club. ¿Por dónde empezarías?"*

**Tareas guiadas después**:

1. Renombra el tag `terreno` a "tipo de carrera".
2. Añade el valor "ultra" al tag `distancia`.
3. Archiva el tag `objetivo viejo`. *Después de hacerlo*: "¿qué crees que pasa con los 5 alumnos que lo tenían?"
4. *Pregunta abierta*: "¿qué pasa si no editas nada y sigues con los tags pre-cargados?"

**Observar**:

- ¿Distingue *tag* de *valor*?
- ¿Entiende la diferencia entre archivar y borrar?
- ¿Le sale fácil añadir un valor o sufre?
- ¿Pide alguna funcionalidad que no está? (anotar literal).

### 03 — Gestión de alumnos (10 min)

**Tareas**:

1. Busca al alumno "Marta".
2. Filtra solo los de nivel medio + objetivo MMM.
3. Selecciona 3 alumnos y cámbiales el estado a "lesión".
4. *Conceptual*: "Tienes 80 socios en un Excel. Cuéntame cómo darías de alta a todos a la vez". (No tenemos pantalla del CSV importer dibujada; ver la conversación, no la ejecución).

**Observar**:

- ¿Encuentra los bulk actions tras seleccionar?
- ¿Entiende el chip "+N" en la columna de tags?
- ¿Espera alguna columna que no está?

### 04 — Constructor de grupos (R18 CRÍTICO, 15 min)

⏱ **Cronometrar**: tiempo desde "crea el grupo X" hasta "guardado". Objetivo: **< 2 min**.

**Tarea principal** (sin más instrucciones): *"Necesitas crear el grupo 'Maratón Valencia avanzado': alumnos que preparan la Maratón de Valencia y son de nivel medio-alto o alto. Hazlo."*

**Tareas adicionales**:

1. Quita a un alumno concreto del grupo aunque cumpla el filtro (ajuste manual M7).
2. *Pregunta abierta*: "¿Qué pasa si dejas el filtro vacío?"
3. *Hipotético*: "¿Cómo crearías un grupo de 'Los del martes que entrenan en pista'?"

**Observar (lista de chequeo)**:

- [ ] ¿Identifica que cada fila es una condición sobre un tag?
- [ ] ¿Necesita explicación del operador `=` vs `∈`?
- [ ] ¿Pregunta por sintaxis textual ("¿cómo pongo AND?")? → la UI debe esconder mejor el AND implícito.
- [ ] ¿Encuentra el acordeón de "Ajustes avanzados"?
- [ ] ¿La vista previa de alumnos le aporta confianza o le desconcierta?

### 05 — Editor del plan semanal (la batalla, 20 min)

⏱ **Cronometrar dos veces**:

1. Primer intento ("desde cero, construye la semana"): objetivo informativo.
2. Segundo intento ("ahora copia la semana anterior y ajusta"): objetivo **< 10 min**.

**Tareas**:

1. Crea las 6 sesiones de la semana del grupo "Maratón Valencia avanzado" (las definimos antes con el participante para que sean realistas).
2. Marta está volviendo de lesión: personaliza su sesión del miércoles para que sea más suave.
3. Mueve la sesión del sábado al domingo.
4. Publica la semana.

**Observar**:

- ¿Pide "modo libre" (escribir la sesión como una frase)? → señal para evaluar la Opción C híbrida del editor.
- ¿La personalización por alumno fluye o le confunde?
- ¿Qué hace antes de pulsar "Publicar"? ¿Lee el resumen?

**Pregunta clave al final**: *"¿Esto te ahorra tiempo respecto a lo que haces hoy? ¿Cuánto?"*

### 08 — Panel de alertas (10 min)

**Tarea**: *"Entras por la mañana. ¿A quién atiendes primero? ¿Por qué?"*

**Tareas adicionales**:

1. Resuelve la alerta de Marta (la del dolor reportado).
2. Descarta dos alertas sin acción.
3. *Pregunta abierta*: "Si tienes 50 alertas activas, ¿esta pantalla te sirve?"

**Observar**:

- ¿Distingue urgente / atención / informativo sin leer la documentación?
- ¿Entiende qué disparó cada alerta?
- ¿Pide algún tipo de alerta que no aparece?

### 09 — Salud del club (10 min)

**Tarea**: *"¿Cómo va tu club esta semana? Cuéntamelo solo mirando esto."*

**Tareas adicionales**:

1. Identifica el grupo con peor cumplimiento.
2. Hay sugerencias de fusión pendientes. ¿Qué decides?
3. Cambia el periodo a "Este mes".

**Observar**:

- ¿Entiende el cálculo de cumplimiento? (atención al tooltip).
- ¿Los 4 KPIs son los relevantes para él? ¿Falta alguno?
- ¿La sugerencia de fusión le tiene sentido?

---

## Guion por pantalla (sesión alumno)

Duración total: 30-45 min. Participantes: 2-3 alumnos del club piloto.

### 06 — Vista "hoy" (H3 <5s, 15 min)

⏱ **Cronometrar**: tiempo desde "abre la app" hasta "sabe qué hace hoy".

**Tarea**: *"Acabas de abrir la app. ¿Qué tienes que hacer hoy?"*

**Tareas adicionales**:

1. *Pregunta dirigida*: "¿Cómo sabes si esto es solo para ti o para todos del grupo?" (validar el indicador "Personalizada para ti").
2. *Pregunta dirigida*: "¿Dónde marcarías que ya hiciste el entreno?".
3. Mira la semana al fondo: "¿qué hiciste el lunes? ¿qué te toca el domingo?"

**Observar**:

- ¿Lee las notas del entrenador o las ignora?
- ¿Confunde "Reajustar día" con "Marcar como hecho"?
- ¿El week-strip le ayuda o le distrae?

### 07 — Reporte + reajuste (<15s, 15 min)

⏱ **Cronometrar**: tiempo desde "marcar como hecho" hasta "enviar".

**Tareas reporte**:

1. Marca la sesión como hecha sin más detalle. (Mide el "happy path" rápido.)
2. Has hecho solo 4 de las 8 series. Reporta.
3. Tienes un pinchazo. ¿Cómo se lo dices al entrenador?

**Tareas reajuste**:

4. Hoy no puedes entrenar. Mueve la sesión a mañana.
5. Estás molesto, no quieres entrenar tampoco mañana. Marca el día como descansado.

**Observar**:

- ¿Entiende la diferencia entre "Parcial" y "No hecho"?
- ¿Encuentra la flag de dolor?
- ¿La RPE (emojis) le incomoda o le motiva?

---

## Regla de decisión (fijar ANTES de la sesión)

Es crítico fijarla **antes** para no racionalizar después.

| Pantalla | Criterio de éxito | Si se cumple → | Si NO se cumple → acción |
|----------|-------------------|-----------------|--------------------------|
| **02 R17** | El admin renombra un tag, añade un valor y entiende el archivado sin titubear | Mantener Opción A | Diseñar variante B (acordeón) y revalidar |
| **03** | Encuentra los bulk actions al primer intento; entiende los chips "+N" | Mantener | Aumentar visibilidad de bulk actions; quizá chips compactos con todos |
| **04 R18** | Crea grupo de 2 condiciones en **< 2 min** sin preguntar por sintaxis | Mantener Opción A | Diseñar Opción C (chatbot guiado) o introducir plantillas de grupos comunes |
| **05** | Construye semana en **< 10 min** en segundo intento + reutiliza "copiar semana anterior" | Mantener Opción A | Evaluar editor de sesión "modo libre" (Opción C híbrida) |
| **06 H3** | Sabe qué hace hoy en **< 5s** + identifica "Personalizada para ti" | Mantener Opción A | Recortar contenido secundario; revisar jerarquía |
| **07** | Reporta sesión "Hecho" en **< 15s** + encuentra flag de dolor | Mantener Opción A | Validar Opción C (reporte inline en card de Hoy) |
| **08** | Distingue 3 prioridades sin documentación + sabe a quién atender primero | Mantener Opción A | Diseñar Opción B (tabla densa) para clubes de volumen alto |
| **09** | Identifica grupo con peor cumplimiento en **< 30s** + entiende sugerencia de fusión | Mantener | Revisar métricas + sugerencias |

## Errores típicos a evitar (facilitador)

- **Defender la pantalla** cuando el participante se atasca. Anotar, no explicar.
- **Saltar a la siguiente pantalla** sin esperar a que termine de pensar en voz alta.
- **Hacer preguntas dirigidas** del tipo "¿no te parece útil que…?". Mejor abiertas: "¿qué piensas?".
- **No cronometrar** las 3 tareas clave. Sin tiempos, no hay datos para H3 y R18.
- **Olvidarse de los alumnos**. La sesión de entrenador/admin no sustituye la del alumno (06 y 07 solo se validan con el usuario final).
- **Validar todas las variantes en la primera sesión**. No las hay dibujadas todavía — solo la Opción A. Las B/C se diseñan **si el resultado lo justifica**.

## Después de la sesión (mismo día)

1. **30-60 min de síntesis** mientras está fresco.
2. Rellenar `validation/[INICIALES]-validation.md` con la plantilla.
3. Capturar **3 quotes literales destacadas**.
4. Aplicar la regla de decisión para cada pantalla.
5. Documentar **hallazgos transversales** (cosas que aparecen en varias pantallas).

## Después de las 2-3 sesiones

1. **Síntesis cruzada** en un nuevo `docs/wireframes/findings.md` similar al de discovery.
2. Lista priorizada de **cambios a aplicar a los wireframes** antes del siguiente paso.
3. Decisión: ¿hay que diseñar variantes B/C de alguna pantalla y revalidar? ¿Pasamos ya a sistema visual?
4. Actualizar `backlog.md` y `risks.md` si emergen riesgos nuevos o se cierran los pendientes (R17, R18).

## Próximo paso tras validación

Solo cuando las pantallas críticas (02, 04, 05, 06) hayan superado su regla de decisión → arrancar:

- **Sistema visual** (color, tipografía, iconografía).
- **Prototipo navegable** en Figma con los flujos completos.
- **ADR de stack técnico** (web responsive, auth invite-only, etc.).
- **Plan de implementación del MVP** con las 19 MUSTs.
