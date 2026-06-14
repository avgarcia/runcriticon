# Guía de selección de diagramas

| Tipo de diagrama           | Mejor para...                                     | Audiencia         | Herramienta/Sintaxis       |
|:---------------------------|:--------------------------------------------------|:------------------|:---------------------------|
| **C4 Context**             | Límites del sistema y actores de alto nivel.      | Todos los grupos  | Mermaid `C4Context`        |
| **C4 Container**           | Stack tecnológico y arquitectura de alto nivel.   | Arquitectos, Devs | Mermaid `C4Container`      |
| **SEQUENCE**               | Flujos complejos, llamadas API, condiciones.      | Devs, Arquitectos | Mermaid `sequenceDiagram`  |
| **ERD** (Entity Relation)  | Esquema de base de datos, modelado de datos.      | Devs, DBA         | Mermaid `erDiagram`        |
| **STATE**                  | Ciclo de vida de una entidad (ej. Estado Orden).  | Producto, Devs    | Mermaid `stateDiagram-v2`  |
| **FLOWCHART**              | Árboles de decisión, flujos, lógica de negocio.   | PM, Devs          | Mermaid `graph TD`         |
| **DEPLOYMENT**             | Mapeo de servidores/infraestructura cloud.        | DevOps            | Mermaid `C4Deployment`     |

## Árbol de decisión

1. **¿Mapeando el ecosistema completo?** -> `C4 Context`
2. **¿Mostrando bloques técnicos?** -> `C4 Container`
3. **¿Debuggeando un flujo API específico?** -> `Sequence Diagram`
4. **¿Diseñando una base de datos?** -> `ERD`
5. **¿Siguiendo cambios de estado de una entidad?** -> `State Diagram`
6. **¿Explicando "Si X entonces Y"?** -> `Flowchart`
