# 01 — Onboarding wizard del admin

> Orquestador de los 5 primeros pasos del admin del club al entrar por primera vez. Su éxito determina si el club arranca en la herramienta o la abandona.

## Contexto

- **Rol**: admin del club ([`personas/admin-club.md`](../personas/admin-club.md)).
- **Cuándo se accede**: la primera vez que el admin entra con sus credenciales (creadas por el equipo Runcriticon mediante seed). Se puede reabrir desde "Ajustes del club" si queda inacabado.
- **Frecuencia**: una vez por club. Puntualmente, si el admin lo retoma a la semana siguiente porque no terminó.
- **Punto del journey**: etapa 1 de [`admin-setup.md`](../journeys/admin-setup.md).
- **MUSTs cubiertos**: M1 (login), M2 (alta entrenador), M3 (alta alumno), M4 (taxonomía), M5 (tags al alumno), M8 (asignar entrenadores a grupos).
- **Riesgo principal**: **R17** (sin tags pre-cargados sensatos, el admin se atasca).

## Objetivo del usuario

> "Dejar el club listo en una tarde, sin tener que adivinar qué hacer en cada paso."

## Inputs (lo que debe estar disponible al entrar)

- Cuenta del admin ya creada (seed).
- Catálogo de tags pre-cargado (ver spec 02).
- Plantilla de carreras populares precargada (MMM, San Silvestre, Maratón Valencia, etc.).
- Nombre del club ya configurado (lo introduce el equipo Runcriticon en el seed).

## Layout

Layout dedicado, **distinto al layout base de admin**. Ocupa toda la pantalla. No hay nav lateral mientras dura el wizard.

```
┌────────────────────────────────────────────────────────────────┐
│ [Logo] Bienvenido a Runcriticon — Club Atletismo XYZ           │ region:header
├────────────────────────────────────────────────────────────────┤
│                                                                │
│ Paso 2 de 5: Revisar tu taxonomía                              │ region:step-title
│ ──────────────────────────────────────────────────             │
│                                                                │
│ ① ━━━ ② ━━━ ③ ━━━ ④ ━━━ ⑤                                       │ region:stepper
│                                                                │
│                                                                │
│ [contenido del paso actual — varía según el paso]              │ region:step-content
│                                                                │
│                                                                │
│                                                                │
├────────────────────────────────────────────────────────────────┤
│ [← Anterior]              [Saltar este paso]   [Siguiente →]   │ region:footer-nav
└────────────────────────────────────────────────────────────────┘
```

## Componentes

### `region:header`

- Logo Runcriticon a la izquierda.
- Título "Bienvenido a Runcriticon — [nombre del club]".
- Sin menú de usuario (no desviar atención).
- Botón "Salir y continuar luego (cta:exit-resume)" arriba a la derecha. Guarda progreso del wizard.

### `region:stepper`

Indicador visual de 5 pasos. Cada paso muestra:

- Número.
- Nombre corto.
- Estado: pendiente / activo / completado / saltado.
- Es **clickable** solo en pasos ya completados (para revisar). En pasos pendientes posteriores no se puede saltar adelante.

Los 5 pasos son:

1. **Te damos la bienvenida** (welcome, no requiere input).
2. **Revisa tu taxonomía** (tags pre-cargados, ver spec 02 — embebida).
3. **Mantén el catálogo de carreras** (valores del tag `objetivo`, embebida en spec 02).
4. **Da de alta a tus entrenadores**.
5. **Da de alta a tus alumnos**.

> Nota: la creación de grupos NO entra en el onboarding. Es opcional y se hace después con la spec 04. Razón: para crear grupos hace falta alumnos con tags, y si forzamos a crear grupos en el primer día el admin abandona.

### `region:step-content` — desglose por paso

#### Paso 1 — Bienvenida

- Título grande: "Vamos a dejar tu club listo en 4 pasos."
- Texto corto (~3 líneas) explicando que los siguientes pasos son: taxonomía, carreras, entrenadores, alumnos.
- Una imagen / ilustración opcional (placeholder en wireframe).
- CTA principal: "Empezar (cta:start)".
- Línea inferior: "¿Prefieres explorar primero? Salta a la pantalla principal".

#### Paso 2 — Revisa tu taxonomía

- Encabezado: "Estos son los tags que vienen pre-cargados. Adáptalos al lenguaje de tu club."
- Embebido el editor de tags (ver spec 02), en versión inline (no como pantalla independiente).
- Aviso inferior: "Puedes editar esto en cualquier momento desde Ajustes > Taxonomía del club".

#### Paso 3 — Catálogo de carreras

- Encabezado: "Estas son las carreras de la temporada. Edítalas, añade las tuyas."
- Embebida la edición de los valores del tag `objetivo` (ver spec 02, sección "Valores con metadata").
- Aviso: "Si no incluyes una carrera ahora, tus alumnos podrán elegir 'sin carrera'. Puedes añadir más después."

#### Paso 4 — Entrenadores

- Encabezado: "Da de alta a los entrenadores de tu club. Recibirán un email con un enlace para entrar."
- Tabla simple con columnas: Nombre, Email, [Eliminar].
- Botón "Añadir entrenador (cta:add-coach)" añade una fila nueva.
- Validación: nombre y email obligatorios. Email con formato válido. No duplicados de email.
- Aviso inferior cuando hay al menos 1 entrenador: "X entrenador(es) recibirán email cuando completes el onboarding".
- Permite continuar incluso con 0 entrenadores (admin podría querer dar alta solo a alumnos primero).

#### Paso 5 — Alumnos

- Encabezado: "¿Cómo prefieres dar de alta a tus alumnos?"
- Dos opciones grandes (cards):
  - **Importar desde CSV (recomendado)**: lleva al flujo de spec 03 (bulk import).
  - **Añadir uno a uno**: lleva al flujo individual de spec 03.
- Tercera opción inferior: "Lo hago más tarde".
- Si elige CSV: muestra spec 03 inline; el wizard continúa con un resumen tras el import.
- Si elige individual: muestra spec 03 inline; cuando el admin pulsa "Hecho", se vuelve al wizard.

### `region:footer-nav`

- Botón "← Anterior (cta:prev)" gris, deshabilitado en paso 1.
- Botón centrado "Saltar este paso (cta:skip)" en gris pequeño. Visible solo en pasos 2-5. Texto distinto según el paso:
  - Paso 2: "Usar la taxonomía por defecto sin cambios".
  - Paso 3: "Sin carreras de momento".
  - Paso 4: "Sin entrenadores todavía".
  - Paso 5: "Más tarde".
- Botón "Siguiente → (cta:next)" primario a la derecha. En el paso 5 cambia a "Finalizar (cta:finish)".

## Acciones

| Acción | Trigger | Resultado |
|---|---|---|
| Empezar | CTA paso 1 | Avanza a paso 2 |
| Siguiente | CTA `next` | Avanza al siguiente paso. Si hay cambios sin guardar en el paso, los guarda. |
| Anterior | CTA `prev` | Vuelve al paso anterior. Cambios del paso actual se guardan. |
| Saltar paso | CTA `skip` | Marca el paso como saltado, avanza al siguiente. |
| Salir y continuar luego | CTA `exit-resume` | Guarda progreso, cierra wizard, lleva al dashboard del club. Banner persistente en dashboard: "Tu onboarding está al X%. Continuar →". |
| Finalizar | CTA `finish` (solo paso 5) | Cierra wizard. Lanza el envío de emails a entrenadores. Lleva al dashboard. Toast: "¡Listo! Hemos enviado X invitaciones por email." |

## Estados de la pantalla

1. **Primer acceso** — paso 1 visible.
2. **Retomar** — abre directamente el último paso no completado. Banner: "Continuamos donde lo dejaste".
3. **Onboarding completado, vuelve por curiosidad** — se accede desde Ajustes; muestra los 5 pasos en modo lectura, con opción de re-ejecutar cada uno.
4. **Cargando datos del paso** — skeleton del contenido.
5. **Error al guardar un paso** — toast rojo "No se pudo guardar. Reintentar".

## Interacciones clave

### Interacción A — Cierre y retomar

1. Admin entra por primera vez, pasa al paso 3, no termina y cierra.
2. Próxima vez que entra, ve banner en dashboard: "Te quedan 3 pasos del onboarding. Continuar".
3. Pulsa el banner → vuelve al wizard en el paso 3 con los datos guardados intactos.

### Interacción B — Cambio de idea en taxonomía

1. Admin pasa los 5 pasos pero saltó el paso 2.
2. Una semana después, va a Ajustes > Taxonomía del club → entra a spec 02 standalone.
3. El editor es el mismo, solo que sin contexto de wizard.

## Validaciones y errores

- No se puede avanzar si el paso tiene errores **bloqueantes** (ej. en paso 4, email mal formado).
- Los errores se muestran inline al lado del campo afectado, además de un banner amarillo arriba: "Revisa los campos marcados".
- Se permite saltar pasos pero NO se permite avanzar con datos parcialmente válidos en el paso actual (es saltar o completar bien).

## Responsive (móvil)

- Stepper colapsa a "Paso 2 de 5" + barra de progreso lineal.
- Footer de navegación queda sticky abajo.
- El contenido del paso ocupa pantalla completa con padding mínimo.
- En el paso 5 (alta de alumnos), el CSV import queda restringido (subida desde móvil es coñazo) — sugerir cambiar a escritorio con un aviso.

## Opciones de diseño a explorar

### Opción A — Wizard secuencial obligatorio (recomendada como base)

Lo descrito arriba: 5 pasos lineales con stepper. El admin no ve el dashboard hasta finalizar (o salir explícitamente).

**Pros**: máxima focalización. El admin sabe exactamente qué hacer y en qué orden.
**Contras**: puede sentirse forzado. Si el admin es exigente con la herramienta, puede irritarse.

### Opción B — Checklist lateral sobre dashboard

El admin entra directamente al dashboard. En el lateral derecho aparece un panel persistente con los 5 pasos como checklist; cada uno abre un side sheet con el contenido del paso. El admin puede tocar cualquier paso en cualquier orden y trabajar también con el dashboard.

**Pros**: más libertad, sensación de control. Funciona mejor para admins más técnicos que ya entienden el modelo.
**Contras**: dispersa la atención; el admin puede no completar los pasos si no hay un hilo claro.

**Recomendación**: diseñar las dos y validar con el admin del club piloto. La hipótesis es que A funciona mejor para admins no técnicos (el target real), B para usuarios power.

## Criterios de validación con usuario

- ✅ El admin del club piloto completa los 5 pasos en una sesión de menos de 45 minutos.
- ✅ No abandona en el paso 2 (riesgo R17).
- ✅ Sabe explicar para qué sirvió cada paso al terminar.
- ✅ Cuando se le pregunta *"¿qué harías si quisieras cambiar X después?"*, identifica correctamente Ajustes.
- ❌ Si tarda > 1 hora o si abandona antes del paso 4 → revisar enfoque.
