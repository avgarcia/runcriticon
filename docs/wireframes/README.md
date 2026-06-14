# Especificaciones de pantallas (wireframes)

Especificaciones detalladas de las pantallas críticas del MVP para que un diseñador produzca wireframes de baja-media fidelidad sin tener que volver a preguntar. Las decisiones de modelo de negocio están cerradas (ver [`vision.md`](../vision.md), [`backlog.md`](../backlog.md)); aquí se concretan estructura, comportamiento y estados de UI.

## Wireframes lo-fi (HTML estático)

**Archivo de entrada**: [`html/index.html`](html/index.html). Abrir en cualquier navegador.

Los 9 wireframes están construidos como HTML estático lo-fi (black & white, sin sistema de marca) compartiendo [`html/_styles.css`](html/_styles.css). Navegables entre sí (cada pantalla enlaza a la anterior/siguiente).

| #  | Pantalla | Archivo |
|----|----------|---------|
| 01 | Onboarding wizard del admin | [`html/01-admin-onboarding.html`](html/01-admin-onboarding.html) |
| 02 | Editor de tags (taxonomía del club) | [`html/02-tag-editor.html`](html/02-tag-editor.html) |
| 03 | Gestión de alumnos | [`html/03-student-management.html`](html/03-student-management.html) |
| 04 | Constructor de grupos (R18 crítico) | [`html/04-group-builder.html`](html/04-group-builder.html) |
| 05 | Editor del plan semanal | [`html/05-coach-week-editor.html`](html/05-coach-week-editor.html) |
| 06 | Vista "hoy" del alumno (móvil + desktop) | [`html/06-student-today.html`](html/06-student-today.html) |
| 07 | Reporte de sesión + reajuste de día | [`html/07-session-report.html`](html/07-session-report.html) |
| 08 | Panel de alertas del entrenador | [`html/08-coach-alerts.html`](html/08-coach-alerts.html) |
| 09 | Vista de salud del club | [`html/09-club-health.html`](html/09-club-health.html) |

### ¿Por qué HTML y no Figma?

Se intentó vía Figma MCP. El plan Starter de Figma limita a **6 llamadas a MCP al mes**, lo que hace inviable iterar wireframes. Como las HTML lo-fi cumplen la misma función (validar estructura y flujo con el club piloto antes de programar) y permiten click-through real en navegador, se ha consolidado todo aquí.

El primer intento dejó una copia parcial de las 6 primeras pantallas en Figma: https://www.figma.com/design/g4Wz1yCZ375qrzhVl4v7I4 — está obsoleta frente a los HTML pero queda como referencia visual histórica.

### Cómo abrir

- macOS: `open docs/wireframes/html/index.html`
- Linux: `xdg-open docs/wireframes/html/index.html`
- Windows: doble clic en el archivo desde el explorador.
- Compartir con RG/VG: subir la carpeta a cualquier static host (GitHub Pages, Netlify, Vercel) o adjuntar como zip.

Las variantes de diseño marcadas como "explorar" en cada spec **no** están dibujadas — son responsabilidad del diseñador final para producir y validar.

## Validación con el club piloto

- [`validation.md`](validation.md) — plan completo de validación: 2 sesiones de entrenador/admin (RG, VG) + 2-3 sesiones de alumno. Guion por pantalla, tareas a proponer, qué observar, **regla de decisión fijada de antemano**, errores típicos a evitar.
- [`validation-template.md`](validation-template.md) — plantilla de captura por sesión.
- `validation/` — las 5 sesiones realizadas: RG, VG, AVG, PM, AM.
- [`findings.md`](findings.md) — **síntesis cruzada de la validación**: las 9 pantallas pasaron, riesgos R2/R17/R18 cerrados, nuevo R19, lista priorizada de cambios. Cambios ALTA ya aplicados a los HTML.

## Objetivo

Cada spec debe permitir a un diseñador:

1. Dibujar la pantalla en wireframe sin dudar de qué elementos pone.
2. Saber qué estados de la pantalla debe dibujar (no solo el "todo OK").
3. Detectar dónde caben **alternativas legítimas** y, en su caso, dibujar las dos para validarlas con el usuario.
4. Conectar la pantalla con los MUST del backlog y con los riesgos/hipótesis que la justifican.

Fidelidad esperada: **baja-media en Figma o equivalente**. Sin sistema visual aún. Geometría limpia, jerarquía clara, sin colores de marca. Los estilos vendrán después.

## Pantallas

| #  | Pantalla | Rol | Cubre MUST | Riesgo/hipótesis crítico |
|----|----------|-----|------------|---------------------------|
| 01 | [Onboarding wizard del admin](01-admin-onboarding.md) | Admin del club | M1-M5, M8 | R17 (admin se atasca al inicio) |
| 02 | [Editor de tags (taxonomía del club)](02-tag-editor.md) | Admin del club | M4 | R17 |
| 03 | [Gestión de alumnos (individual + CSV)](03-student-management.md) | Admin del club | M3, M5, M9 | Fricción del alta masiva |
| 04 | [Constructor de grupos](04-group-builder.md) | Admin / Entrenador | M6, M7, M9b | R18 (constructor demasiado técnico) |
| 05 | [Editor del plan semanal del entrenador](05-coach-week-editor.md) | Entrenador | M10, M11, M12 | R2 (modelo plan-por-grupo) |
| 06 | [Vista "hoy" del alumno](06-student-today.md) | Alumno | M13 | H3 (alumno entiende en <5s) |
| 07 | [Reporte de sesión + reajuste de día](07-student-report.md) | Alumno | M14, M18 | P3 (reajuste rápido) |
| 08 | [Panel de alertas del entrenador](08-coach-alerts.md) | Entrenador | M15, M17 | P2 (feedback por excepción) |
| 09 | [Vista de salud del club](09-admin-club-health.md) | Admin del club | M16 | Adopción institucional del club |
| 10 | [Marcas del alumno (privadas)](10-student-marks.md) | Alumno | M19, M20 | Privacidad del alumno + habilitar ritmos relativos |

> La spec 10 nació tras la ronda 1 de validación (consolidada en la ronda 2 informal — ver [findings.md](findings.md)) y no tiene contrapartida en los HTML lo-fi de la tabla siguiente; sus mockups hi-fi viven en [`docs/diseno/`](../diseno/).

## Convenciones globales (vinculantes para todas las specs)

### Estructura común

- **Web responsive**. Diseñar primero para escritorio (1280-1440px) y verificar móvil (360-414px). Los puntos críticos de móvil se señalan en cada spec.
- **Layout base** (admin y entrenador):
  - Header superior fijo: logo a la izquierda, nombre del club centrado, menú de usuario a la derecha.
  - Navegación lateral izquierda (colapsable): secciones del rol.
  - Contenido principal scrollable.
  - Sin footer visible (deja altura útil).
- **Layout base alumno**: header simplificado + contenido a pantalla completa (móvil-first).

### Idioma y tono

- Castellano neutro.
- Tuteo (*"asigna tags a tus alumnos"*, no *"asigne usted"*).
- Verbos en imperativo en CTAs (*"Crear grupo"*, no *"Crear un grupo"*).
- Mensajes de error en lenguaje humano, sin "Error 422".

### Estados que toda pantalla debe contemplar

Toda spec debe definir explícitamente al menos:

1. **Vacío inicial** — pantalla sin datos (primer uso).
2. **Cargando** — datos en camino.
3. **Lleno** — caso de uso típico.
4. **Error** — fallo de carga o validación.
5. **Sin permisos** — si el rol no debería entrar.

Si la pantalla tiene más estados relevantes (filtrado vacío, búsqueda sin resultados, etc.), se listan en su sección.

### Patrones reutilizables

Las specs los referencian por nombre; aquí su descripción canónica.

- **Toast** — feedback no bloqueante. Aparece abajo-derecha 4s, descartable. Tres tipos: éxito (verde), aviso (amarillo), error (rojo). Acción opcional ("Deshacer").
- **Banner** — feedback persistente arriba del contenido. Para avisos de estado del sistema (mantenimiento, cuenta no verificada).
- **Modal** — confirmación destructiva o creación de entidad simple. Bloquea fondo, cerrable con ESC.
- **Side sheet** — panel lateral derecho deslizante para edición de detalle sin perder contexto de la lista. Más ancho que un modal, no bloquea fondo.
- **Chip** — etiqueta con valor visible, opcional botón X para quitar. Usado para tags asignados y filtros activos.
- **Empty state** — ilustración (puede ser pictograma) + título + descripción 1-2 líneas + CTA principal.
- **Inline editing** — clic en un texto lo convierte en input. Enter guarda, Esc cancela, click fuera guarda.

### Accesibilidad mínima

Todas las pantallas deben:

- Navegarse con teclado completo (Tab, Shift+Tab, Enter, Esc, flechas en listas).
- Tener orden lógico de foco.
- Contar con `aria-label` en iconos sin texto.
- Cumplir contraste AA (WCAG 2.1) en el sistema visual final.
- No depender solo del color para transmitir información (estados con icono además).

Se llaman fuera estos detalles solo cuando la pantalla añade interacciones particulares.

### Convención de identificadores de elementos

Para que el diseño y la implementación hablen el mismo idioma:

- Cada CTA primario en una spec lleva un identificador entre paréntesis: *"Crear grupo (cta:create-group)"*.
- Cada zona principal lleva un identificador de sección: `region:filters`, `region:main`, `region:preview`.

El diseñador no necesita inventar nombres; los reutiliza tal cual.

### Cómo leer cada spec

Estructura común de cada archivo:

1. **Propósito** (1 frase).
2. **Contexto** — rol, journey, MUSTs, riesgos.
3. **Inputs** — qué información debe estar disponible al entrar.
4. **Layout** — descripción + ASCII opcional para clarificar.
5. **Componentes** — uno a uno con estados.
6. **Acciones** — primarias y secundarias.
7. **Estados de la pantalla** — todos los relevantes, no solo el feliz.
8. **Interacciones clave** — paso a paso lo no obvio.
9. **Validaciones y errores** — reglas que afectan UI.
10. **Responsive (móvil)** — qué cambia.
11. **Opciones de diseño a explorar** — alternativas legítimas para validar con usuario.
12. **Criterios de validación** — cómo sabremos en test que funciona.

## Qué NO incluyen estas specs

- **Sistema visual** (color, tipografía, iconografía concreta). Vendrá después.
- **Implementación técnica** (componente React X, librería Y).
- **Microcopy final**. Las strings que aparecen son indicativas; un copy final puede iterar.
- **Decisiones de modelo de datos**. Están en [`../vision.md`](../vision.md) y [`../backlog.md`](../backlog.md).

## Qué hacer después de los wireframes

1. Validar wireframes con el admin del club piloto (perfil [`admin-club.md`](../personas/admin-club.md)) — especialmente las pantallas 02 y 04 (R17 y R18).
2. Validar con un entrenador (RG o VG) — pantallas 04, 05, 08.
3. Validar con un alumno (RG/VG nos pasa contacto) — pantallas 06 y 07.
4. Ajustar specs según feedback.
5. Pasar a sistema visual y prototipo navegable solo si las wireframes superan estas validaciones.
