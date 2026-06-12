# Buenas Prácticas en Diagramación de Arquitectura

Síntesis de directrices de expertos de la industria (AWS, C4 Model, InfoQ, Mural).

## 1. Principios Fundamentales

### "Un Diagrama, Una Historia"

- **No intentes modelar todo el sistema en un único diagrama.** Conduce a resultados "feos" e ilegibles.
- **Usa Capas de Abstracción:** Sigue el modelo C4 (Context -> Container -> Component) para separar responsabilidades. Cada diagrama debe responder un conjunto específico de preguntas para una audiencia concreta.

### Diseño Centrado en la Audiencia

- **Conoce a tu observador:**
    - _Ejecutivos/Producto_: Diagramas Context de alto nivel (límites del sistema, interacciones con usuarios).
    - _Arquitectos/Líderes técnicos_: Diagramas de Container/Cloud Architecture (decisiones tecnológicas, protocolos).
    - _Desarrolladores_: Diagramas de Component/ERD (estructura del código, esquema de base de datos).
- **Evita jerga:** Si debes usar siglas (p. ej., "RBAC", "OCR"), defínelas en una leyenda o nota.

## 2. Gobernanza Visual (La Regla "Sin Diagramas Feos")

### La Consistencia es Clave

- **Formas:** Usa la misma forma para el mismo concepto en todos los diagramas (p. ej., Cilindro = Base de Datos, Forma de persona = Usuario).
- **Colores:** Usa colores semánticamente, no decorativamente.
    - _Ejemplo:_ Azul = Sistema Interno, Gris = Sistema Externo, Verde = Usuario.
    - _Anti-patrón:_ Usar colores aleatorios solo para que "resalte".
- **Tamaño:** Mantén cajas relativamente uniformes a menos que el tamaño transmita significado (p. ej., anidamiento).

### La Leyenda es Obligatoria

- **Nunca asumas que el lector conoce tu notación.**
- **Todo diagrama debe tener una Leyenda** que defina:
    - Formas de cajas (Container vs Sistema).
    - Estilos de líneas (Sólido = Síncrono, Punteado = Asíncrono/Message Bus).
    - Significado de flechas (Flujo de datos vs Dependencia).
    - Significados de colores.

### Diseño y Flujo

- **Dirección:** Estandariza en **Left-to-Right (LR - De Izquierda a Derecha)** o **Top-Down (TD - De Arriba a Abajo)**.
    - _LR_ suele ser mejor para flujos de datos y diagramas de infraestructura amplios.
    - _TD_ es mejor para jerarquías y desgloses de componentes.
- **Espacio en blanco:** Deja espacio respirable. Los diagramas abarrotados implican falta de claridad en el diseño del sistema mismo.

## 3. Semántica y Notación

### Explicar Líneas y Flechas

- **Etiqueta cada arista.** Una flecha sin etiqueta es ambigua.
- **Sé específico:**
    - _Malo:_ "Se comunica con"
    - _Bueno:_ "HTTPS/JSON", "gRPC", "Pub/Sub"
- **Dirección:**
    - _Dependencia:_ "A depende de B" (usualmente apunta a la dependencia).
    - _Flujo de datos:_ "A envía datos a B" (apunta a B).
    - _Aclara esto en la leyenda._

### Manejo de Metadatos

Todo diagrama (o el documento que lo contiene) debe indicar:

- **Alcance:** ¿Qué se muestra?
- **Estado:** ¿Borrador, Propuesto o Implementado?
- **Fecha/Versión:** ¿Cuándo fue esto preciso?

## 4. Anti-Patrones a Evitar

- **La "Caja Huérfana":** Todo nodo debe estar conectado a algo. Si está aislado, ¿por qué está ahí?
- **El Diagrama de "Todo":** Mezclar detalles de servidor físico (RAM/CPU) con flujos de usuario de alto nivel.
- **La "Sigla Misteriosa":** Usar "PIMS" o "DWH" sin definición.
- **Abstracción Inconsistente:** Mostrar una caja "Base de Datos" junto a una caja "Clase". Mantén niveles de abstracción consistentes.
