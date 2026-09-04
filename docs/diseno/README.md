# Diseño visual — mockups hi-fi

Mockups en HTML/CSS (Material Design 3 sobrio, primario navy `#1a3e72`) de las pantallas críticas del MVP. Producidos antes del frontend para servir de referencia visual al equipo. Las decisiones de modelo y spec funcional viven en [`docs/wireframes/`](../wireframes/); aquí solo está la materialización visual.

## Cómo abrir

Doble clic en cualquier `.html` desde el explorador de archivos, o servir el directorio con cualquier static server.

## Índice por rol

### Entrenador (desktop)

| Archivo | Cubre | Notas |
|---|---|---|
| [`editor-plan-semanal-combo.html`](editor-plan-semanal-combo.html) | Spec 05 — editor de plan semanal | Calendario de 7 columnas con cards combo (banner tintado + tablero de métricas). Diseño consolidado tras explorar 5 alternativas. |
| [`alternativas-cards-sesion.html`](alternativas-cards-sesion.html) | — | Referencia histórica: las 5 alternativas de card de sesión exploradas. Conservado como bitácora de proceso. |
| [`editor-plan-semanal.html`](editor-plan-semanal.html) | — | Primer borrador del editor de plan. Sustituido por `editor-plan-semanal-combo.html`; se mantiene como referencia. |
| [`editor-sesion.html`](editor-sesion.html) | Spec 05 — side sheet del editor de sesión | Tipo, estructura, **conmutador absoluto/relativo** del ritmo, calentamiento/cool-down, notas, acceso a personalizaciones. |
| [`modal-personalizaciones.html`](modal-personalizaciones.html) | Spec 05 — gestión de personalizaciones (M12) | Modal sobre el side sheet del editor: lista de personalizaciones existentes y form de añadir con mensaje al alumno opcional. |
| [`constructor-grupos.html`](constructor-grupos.html) | Spec 04 — constructor de grupos | Condiciones de tags, ajustes manuales (incluir/excluir/restaurar), detección de conflicto de planes, modal de añadir alumno. |
| [`publicacion-plan.html`](publicacion-plan.html) | Spec 05 — flujo de publicación | Modal que congela el snapshot de membresía: tira semanal, tiles de resumen, lista de alumnos, personalizaciones, aviso de huérfanas, switch de email. |
| [`panel-alertas-entrenador.html`](panel-alertas-entrenador.html) | Spec 08 — panel de alertas (LAL-116, M17) | **Recortado al AC del ticket**: solo 2 secciones (Urgente / Informativo) y 3 tipos de alerta (dolor reportado, sin reportar &gt;7 días, ritmo fuera de objetivo) — sin lesión declarada, sobrecarga, cumplimiento bajo, RPE alto, alumno nuevo ni comentario de la spec 08 original. Panel de **solo lectura**: sin "Descartar" ni histórico de descartadas. Incluye galería de referencia con los estados vacío/cargando/error. |

### Admin (desktop)

| Archivo | Cubre | Notas |
|---|---|---|
| [`editor-taxonomia.html`](editor-taxonomia.html) | Spec 02 — editor de tags del club | Master-detail. Tag `objetivo` con metadata (fecha + distancia), badge de carrera pasada. |
| [`alta-alumnos.html`](alta-alumnos.html) | Spec 03 — gestión de alumnos | Tabla con filtros pill, chips de tags, bulk-bar flotante. Side sheet de alta individual abierto a la derecha con multi-pills para objetivos. |

### Alumno (mobile-first)

| Archivo | Cubre | Notas |
|---|---|---|
| [`vista-hoy-alumno.html`](vista-hoy-alumno.html) | Spec 06 — vista "hoy" | Card combo grande con métricas. Ritmo resuelto con sutil "basado en tu 10K" cuando el origen es relativo. Bloque de reporte con valoración 1-5. |
| [`mis-marcas.html`](mis-marcas.html) | Spec 10 — marcas del alumno | Cards por distancia (5K/10K/21K/42K) con banner verde de privacidad arriba. Modal compacto de edición. |
| [`onboarding-alumno.html`](onboarding-alumno.html) | Spec 10 — onboarding | Dos pantallas: bienvenida con razones y privacidad, primera marca con copy contextual y contador "1 de 4". |

### Identidad y acceso (todos los roles)

| Archivo | Cubre | Notas |
|---|---|---|
| [`identidad-acceso.html`](identidad-acceso.html) | ADR-0003 · ADR-0012 (revisión 2026-07) | **Prototipo interactivo** (único artefacto React empaquetado del directorio; se abre con doble clic igual). Los 10 estados del flujo completo: login con contraseña, pedir/consumir magic link, reseteo, contraseña caducada, activar invitación (con medidor de fortaleza), enlace caducado, home post-login. Rail de simulación de errores y bandeja de email simulada — el diseño de la app es solo el marco de teléfono central. |
| [`activacion-login.html`](activacion-login.html) | — | Primera galería estática (cuatro estados). Sustituida por `identidad-acceso.html`; se conserva como bitácora. |

## Lenguaje visual

Las decisiones globales del sistema visual están en cada archivo (variables CSS coherentes). Lo más relevante:

- **Primario** `#1a3e72` (navy).
- **10 tipos de sesión** con su color propio, usados como banner tintado al 12% (no saturado).
- **Amber** reservado para "metadata / excepción" (objetivo en taxonomía, conflictos en grupos, avatares con personalización).
- **Verde** reservado para mensajes de privacidad y éxito.
- **Neutrales slate** (`#0f172a` texto, `#64748b` secundario, `#e2e8f0` bordes, `#f8fafc` fondo) y **radios 8 px** — consolidados en `identidad-acceso.html` y como tokens CSS del frontend (ADR-0012 D3).
- **Tipografía** de sistema: ui-sans-serif / Segoe UI / Roboto (sin webfont).

## Decisiones de producto materializadas aquí

- **Sin indicador de personalización al alumno**: ver `vista-hoy-alumno.html`. El alumno solo ve el mensaje opcional del entrenador (✉), nunca un texto "personalizada para ti".
- **Marcas privadas del alumno**: ver `mis-marcas.html`, `onboarding-alumno.html` y `editor-sesion.html`. Banner verde explícito + ausencia total de listados o contadores en pantallas del entrenador/admin.
- **Ritmo relativo como delta sobre marca**: ver `editor-sesion.html`. No porcentajes; el entrenador piensa en *"10K + 10 s/km"*.
