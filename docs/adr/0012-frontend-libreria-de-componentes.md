# ADR-0012 — Frontend: librería de componentes y estrategia de UI

- **Estado**: Propuesto
- **Fecha**: 2026-05-22
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack — Angular), ADR-0010 (CI/CD — lint y tests de frontend)

## Contexto y problema

ADR-0001 fija **Angular** como framework del frontend, pero no decide con qué se construye la **interfaz**: ¿una librería de componentes? ¿cuál? ¿qué estrategia de estilos?

El MVP tiene pantallas **interactivas** —el constructor de grupos con vista previa en vivo, el editor de plan semanal (con *drag-drop*), la vista "hoy" del alumno— que necesitan componentes ricos: formularios, tablas, diálogos, *datepickers*. Construir todo eso desde cero, o elegir mal, cuesta caro.

## Drivers de la decisión

- Equipo interno de 4 personas → **evitar construir componentes de UI desde cero**.
- Pantallas interactivas que necesitan componentes ricos y *drag-drop*.
- **Accesibilidad**: el discovery puso cuidado en la UX y la app maneja datos de salud — la UI debe ser accesible.
- Coherencia con la lógica de ADR-0001 (se eligió Angular por ser "oficial y con baterías incluidas").
- Velocidad de MVP.

## Opciones consideradas

- **Opción A** — Angular Material.
- **Opción B** — PrimeNG u otra librería de terceros.
- **Opción C** — Componentes propios sobre Angular CDK.

### Opción A — Angular Material

Librería **oficial del equipo de Angular**, implementa Material Design.

- 👍 Oficial — la misma lógica de "baterías incluidas y afinidad" que llevó a Angular en ADR-0001.
- 👍 **Accesibilidad de serie** vía Angular CDK.
- 👍 Conjunto completo: formularios, tablas, diálogos, *datepickers*; el CDK aporta el **drag-drop** del editor semanal.
- 👍 Gran comunidad y mantenimiento.
- 👎 Estética Material reconocible — personalizable, pero "se nota".

### Opción B — PrimeNG u otra librería de terceros

- 👍 Catálogo enorme de componentes, *widgets* "enterprise".
- 👎 De terceros, con calidad desigual entre componentes; sin la afinidad oficial con Angular.

### Opción C — Componentes propios sobre Angular CDK

El CDK da primitivas de comportamiento sin estilo; el equipo construye los componentes estilados encima.

- 👍 Control total del aspecto; sin dependencia de una librería de componentes.
- 👎 Se construye **todo** —diálogos accesibles, *datepickers*, desplegables, tablas—; muchísimo trabajo, la ceremonia que no paga en un MVP con equipo de 4.

## Decisión

**Opción A: Angular Material**, con el theming de Material y SCSS con ámbito de componente como estrategia de estilos.

Es la misma lógica que eligió Angular en ADR-0001: oficial, baterías incluidas, afinidad. Trae **accesibilidad de fábrica** —relevante con el cuidado de UX del discovery y los datos de salud— y el Angular CDK incluye el *drag-drop* que necesita el editor semanal. La Opción C es un coste enorme para un MVP; PrimeNG (B) no aporta ninguna ventaja decisiva frente a la librería oficial.

### Estrategia de estilos

- Se define un **tema de Angular Material** (paletas de color) como base.
- Los estilos propios van en **SCSS con ámbito de componente** — Angular ya los aísla por defecto.
- **Un solo paradigma de estilos**: no se añade Tailwind ni otro sistema de utilidades — convivir con el theming de Material metería un segundo paradigma redundante y fuente de inconsistencia.

### Lo que este ADR no decide

Este ADR fija la **base técnica** de la UI (librería + theming). El **diseño visual** —paleta de marca, *UI kit*, prototipo— es una **tarea de diseño aparte**, ya prevista en el plan de discovery; los wireframes actuales son lo-fi.

## Consecuencias

### Positivas

- El equipo no construye componentes de UI desde cero — más velocidad de MVP.
- Accesibilidad de serie.
- Componentes ricos y *drag-drop* disponibles para las pantallas interactivas.
- Un solo sistema de estilos, coherente.
- Coherencia con la afinidad Angular oficial de ADR-0001.

### Negativas / coste asumido

- La estética Material es reconocible; diferenciar visualmente el producto exige trabajo de theming.
- Acoplamiento a Angular Material como librería — aceptable: es la oficial y la más estable del ecosistema.

### Riesgos y mitigaciones

- **UI "genérica" de Material** → trabajo de theming con la paleta de marca cuando exista el *UI kit*.
- **Sobre-uso de componentes pesados** → usar el componente adecuado a cada caso; no forzar *widgets* complejos donde basta uno simple.

## Notas

- El *UI kit* y el diseño visual se abordan como tarea de diseño separada (plan de discovery).
- Si en el futuro se necesita un componente muy específico que Material no cubre, se construye a medida sobre el Angular CDK — sin cambiar esta decisión.
