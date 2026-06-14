# Tipos de base de datos — guía de referencia

> Nota de referencia para el onboarding del equipo técnico. Complementa al
> [ADR-0004](../adr/0004-base-de-datos-postgresql.md), que decide PostgreSQL como
> base de datos de Runcriticon. Aquí se explica **por qué** y **cuándo** tendría
> sentido cada familia de base de datos, con ejemplos del propio proyecto.

## Idea de partida

No hay una base de datos "mejor". Hay una que **encaja con cómo se leen y se escriben los datos**. La elección se hace por el *patrón de acceso* y la *forma del dato*, no por moda.

---

## 1. Relacional / SQL — *PostgreSQL* (la elegida)

**Qué es.** Los datos viven en **tablas** (filas y columnas) enlazadas por claves (`id`). El motor **garantiza la integridad** (no permite un reporte que apunte a una sesión inexistente), las operaciones son **transaccionales** (todo-o-nada) y se consulta con SQL, que permite **cruzar tablas** (`JOIN`).

**Analogía.** Un archivador de fichas perfectamente referenciadas: la ficha del alumno no repite los datos del club, solo apunta a ella; y el archivero no deja meter una ficha que apunte a un club inexistente.

**Cuándo brilla.** Datos con entidades y relaciones claras; la integridad importa; transacciones que tocan varias entidades; consultas variadas e imprevistas que combinan datos.

**Cuándo es mala elección.** Escala de millones de operaciones/segundo de lecturas simplísimas; travesías de relaciones muy profundas; analítica pesada sobre miles de millones de filas.

**Ejemplo en Runcriticon.** Es el núcleo de toda la app. *"Alumnos del grupo Avanzado"* es un `JOIN` entre `grupo_tag_requerido` y `alumno_tag`. *"Publicar el plan de la semana"* es una transacción que escribe plan, sesiones y snapshot todo o nada. El modelo de Runcriticon *es* relacional.

---

## 2. Documental — *MongoDB*

**Qué es.** Guarda **documentos** tipo JSON, cada uno **autocontenido** (lleva dentro todo lo suyo). Sin esquema obligatorio y sin `JOIN` de verdad.

**Analogía.** Una carpeta de dossiers completos: cada dossier trae dentro toda la información de un cliente; no se miran tres archivadores para componerla.

**Cuándo brilla.** Cada registro se lee/escribe entero y es independiente; el esquema varía mucho; casi nunca hay que combinar registros de colecciones distintas.

**Cuándo es mala elección.** Datos muy interrelacionados con integridad entre ellos; algo compartido por muchos registros (obliga a duplicarlo).

**Ejemplo en Runcriticon.**
- *Dónde no encaja:* un `tag` lo comparten muchos alumnos; embeberlo en cada alumno duplica el catálogo. Por eso se descartó en ADR-0004.
- *Dónde sí encajaría:* guardar la respuesta cruda en JSON de una actividad importada de Strava — un bloque de forma variable que nadie cruza con otras tablas.

---

## 3. Clave-valor — *Redis*

**Qué es.** Un diccionario gigante: una clave → un valor, con búsqueda instantánea. Suele vivir en memoria. Solo se busca por la clave exacta.

**Analogía.** El guardarropa de un teatro: das el número de ticket (clave), te devuelven el abrigo (valor). Rapidísimo, pero no se puede pedir "todos los abrigos azules".

**Cuándo brilla.** Caché, sesiones de usuario, contadores, *rate limiting*, datos efímeros. Siempre se conoce la clave.

**Cuándo es mala elección.** Como sistema de registro principal; cuando hay que consultar por algo distinto de la clave, o relaciones.

**Ejemplo en Runcriticon.** Complemento natural (no sustituto) de PostgreSQL: guardar la sesión de usuario, cachear la membresía resuelta de un grupo, limitar intentos de login.

---

## 4. Grafo — *Neo4j*

**Qué es.** Datos como **nodos** conectados por **aristas**, donde la relación es tan importante como el dato. Optimizada para **recorrer conexiones** de profundidad variable.

**Analogía.** El mapa de una red social: lo valioso es preguntar *"¿quién conecta a A con B y por cuántos saltos?"*.

**Cuándo brilla.** Preguntas sobre caminos y cadenas: "amigos de mis amigos", recomendaciones, ruta más corta, detección de fraude, árboles de dependencias.

**Cuándo es mala elección.** Relaciones superficiales de un solo salto — eso es una tabla de enlace relacional.

**Ejemplo en Runcriticon.** *"Alumno tiene tags"* suena a grafo, pero es una relación de **un solo salto**: por eso es la tabla `alumno_tag` y no un grafo. Un grafo se ganaría el sueldo solo con preguntas encadenadas ("cadena de personas que une al alumno A con el B"), que el proyecto no tiene. Un salto → relacional; N saltos encadenados → grafo.

---

## 5. Columnar / analítica — *ClickHouse, BigQuery, Redshift*

**Qué es.** Guarda los datos **por columna** y está pensada para **escanear y agregar volúmenes enormes**. Es el mundo **OLAP** (analizar), frente al **OLTP** (operar) de PostgreSQL.

**Analogía.** PostgreSQL es la caja registradora (apunta cada venta al instante); una base columnar es el departamento de análisis que a fin de año suma millones de tickets para sacar tendencias.

**Cuándo brilla.** Cuadros de mando y analítica sobre millones/miles de millones de filas; "suma/media/cuenta agrupando por…" sobre todo el histórico.

**Cuándo es mala elección.** Trabajo transaccional, actualizaciones fila a fila, lecturas puntuales de baja latencia.

**Ejemplo en Runcriticon.** El MVP no la necesita. En un futuro multi-club, la analítica pesada ("evolución del volumen de entrenamiento de todos los clubes en 3 años") se copiaría a un almacén columnar **separado** del PostgreSQL operacional. Regla de oro: no se hace analítica pesada sobre la base de datos operacional.

---

## 6. Búsqueda de texto — *Elasticsearch / OpenSearch*

**Qué es.** Un motor de búsqueda full-text: índice invertido, tolerancia a erratas, ranking por relevancia, búsqueda "mientras escribes", filtros por facetas.

**Analogía.** Un Google privado: no se le pide "la fila con id 42", sino "lo que hable de *fascitis*", ordenado por relevancia aunque haya erratas.

**Cuándo brilla.** Cajas de búsqueda sobre mucho texto libre, con erratas y relevancia.

**Cuándo es mala elección.** Como sistema de registro o para integridad transaccional.

**Ejemplo en Runcriticon.** El MVP no la necesita: PostgreSQL trae búsqueda de texto suficiente a esta escala. Si más adelante se acumula mucho texto (notas de sesión, comentarios) y se quiere búsqueda con erratas y ranking, se añadiría un motor de búsqueda **junto a** Postgres.

---

## 7. Series temporales — *TimescaleDB / InfluxDB*

**Qué es.** Optimizada para datos con marca de tiempo que solo se añaden (nunca se editan): ingesta masiva, consultas por ventanas de tiempo, retención automática.

**Analogía.** El registro de un sensor: una medición tras otra, ordenadas por instante, que solo crecen.

**Cuándo brilla.** Métricas, monitorización, sensores IoT — "una medida en un instante", millones de veces.

**Cuándo es mala elección.** Entidades relacionales que se actualizan.

**Ejemplo en Runcriticon.** El MVP no la necesita. Si se integra Garmin/Strava e se importa el rastro segundo a segundo de GPS y pulsaciones, esos millones de puntos irían a una base de series temporales; el **resumen** de la sesión seguiría en PostgreSQL.

> Mención aparte: las **bases vectoriales** (Pinecone, `pgvector`) sirven para búsqueda semántica e IA. Solo serían relevantes si se aborda la "IA generadora de planes" de la lista WON'T del backlog.

---

## Modelo primario + satélites (persistencia políglota)

Las aplicaciones reales no eligen una sola base para siempre. Tienen una **principal** —la dueña de la verdad, en Runcriticon PostgreSQL— y, **cuando aparece una necesidad concreta**, añaden un almacén especializado *al lado*. Eso es la *persistencia políglota*. Lo importante: se añaden **de una en una, cuando hay señal real**, nunca todas "por si acaso".

Orden realista de adopción para Runcriticon:

| Cuándo | Qué se añadiría | Para qué |
|--------|-----------------|----------|
| Pronto, al crecer | **Redis** | Caché y sesiones — acelerar |
| Si crece el texto | **Buscador** | Búsqueda con erratas y relevancia |
| Si se integran wearables | **Series temporales** | Rastro GPS/pulso segundo a segundo |
| Si llega multi-club | **Columnar/analítica** | Cuadros de mando sobre todo el histórico |

## Tabla resumen

| Tipo | Para qué sirve | Ejemplo en Runcriticon | ¿En el MVP? |
|------|----------------|------------------------|-------------|
| **Relacional** (PostgreSQL) | Entidades relacionadas, integridad, transacciones | Todo el núcleo: club, alumnos, tags, planes, reportes | ✅ Sí — la principal |
| **Documental** (MongoDB) | Registros autocontenidos, esquema variable | JSON crudo de una actividad de Strava | ❌ No |
| **Clave-valor** (Redis) | Caché, sesiones, contadores | Sesión de usuario, caché de membresía de grupos | ❌ No (pronto sí) |
| **Grafo** (Neo4j) | Travesías de relaciones profundas | No aplica — las relaciones son de un salto | ❌ No |
| **Columnar** (ClickHouse) | Analítica sobre volúmenes enormes | Cuadros de mando multi-club a 3 años | ❌ No |
| **Búsqueda** (Elasticsearch) | Texto libre, erratas, relevancia | Buscar en notas de sesión | ❌ No |
| **Series temporales** (TimescaleDB) | Datos con marca de tiempo, solo añadir | Rastro GPS/pulso de wearables | ❌ No |

## Conclusión

Para todo el MVP, **PostgreSQL es la dueña de la verdad de todo**. Las demás familias no son "mejores ni peores" — son herramientas que se incorporan *al lado* el día que una necesidad concreta lo pida, y no antes.
