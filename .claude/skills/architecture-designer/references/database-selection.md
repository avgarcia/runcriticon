# Selección de Bases de Datos

## Tipos de Bases de Datos

| Tipo                  | Ejemplos                   | Ideal para                                          |
|-----------------------|----------------------------|-----------------------------------------------------|
| **Relacional**        | PostgreSQL, MySQL          | Transacciones, consultas complejas, relaciones      |
| **Documental**        | MongoDB, Firestore         | Esquemas flexibles, iteración rápida                |
| **Clave-Valor**       | Redis, DynamoDB            | Almacenamiento en caché, sesiones, alto rendimiento |
| **Series Temporales** | TimescaleDB, InfluxDB      | Métricas, IoT, análisis                             |
| **Grafos**            | Neo4j, Neptune             | Relaciones, redes sociales                          |
| **Búsqueda**          | Elasticsearch, Meilisearch | Búsqueda de texto completo, registros               |

## Relacional (PostgreSQL, MySQL)

```
Ideal para:
- Transacciones financieras (cumplimiento ACID)
- Consultas complejas con uniones
- Requisitos de integridad de datos
- Esquemas estructurados y predecibles

Cuándo evitarlo:
- Esquemas altamente variables
- Necesidades de escalabilidad horizontal masiva
- Patrones de acceso clave-valor simples
```

| Funcionalidad              | PostgreSQL        | MySQL                      |
|----------------------------|-------------------|----------------------------|
| Compatibilidad con JSON    | Excelente (JSONB) | Buena (JSON)               |
| Búsqueda de texto completo | Integrada         | Básica                     |
| Extensiones                | Ecosistema amplio | Limitada                   |
| Replicación                | Streaming, lógica | Basada en sentencias, fila |

## Documento (MongoDB, Firestore)

```
Ideal para:
- Esquemas flexibles y evolutivos
- Datos jerárquicos (documentos anidados)
- Prototipado rápido
- Gestión de contenido

Cuándo evitar:
- Transacciones complejas entre documentos
- Consultas relacionales pesadas
- Requisitos de esquema estrictos
```

## Clave-Valor (Redis, DynamoDB)

```
Ideal para:
- Almacenamiento de sesiones
- Capa de caché
- Clasificaciones en tiempo real
- Contadores de limitación de velocidad

Cuándo evitar:
- Consultas complejas
- Datos relacionales
- Valores de gran tamaño (>1 MB)
```

## Series temporales (TimescaleDB, InfluxDB)

```
Ideal para:
- Métricas y monitorización
- Datos de sensores IoT
- Datos financieros en tiempo real
- Registro de eventos con marcas de tiempo

Cuándo evitar:
- Actualizaciones frecuentes de registros existentes
- Consultas relacionales complejas
- Datos no basados en el tiempo Patrones de acceso
```

## Matriz de decisión

| Requisito                                 | Recomendado           |
|-------------------------------------------|-----------------------|
| Transacciones ACID                        | PostgreSQL, MySQL     |
| Esquema flexible                          | MongoDB, Firestore    |
| Almacenamiento en caché de alta velocidad | Redis                 |
| Datos de series temporales                | TimescaleDB, InfluxDB |
| Relaciones sociales                       | Neo4j                 |
| Búsqueda de texto completo                | Elasticsearch         |
| Escalabilidad sin servidor                | DynamoDB, Firestore   |

## Referencia rápida

| Pregunta                                    | Si la respuesta es sí → |
|---------------------------------------------|-------------------------|
| ¿Necesita transacciones ACID?               | Relacional (PostgreSQL) |
| ¿Cambia de esquema con frecuencia?          | Documento (MongoDB)     |
| ¿Lecturas en submilisegundos?               | Clave-valor (Redis)     |
| ¿Consultas basadas en el tiempo?            | Series temporales       |
| ¿Recorrer relaciones?                       | Grafo (Neo4j)           |
| ¿Búsqueda de texto completo como principal? | Elasticsearch           |
